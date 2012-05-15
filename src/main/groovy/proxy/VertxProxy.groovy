package proxy

import java.util.concurrent.*
import org.vertx.groovy.core.*

class VertxProxy {

	private final Vertx vertx
	private final int port
	private server

	VertxProxy(Vertx vertx, int port) {
		this.vertx = vertx
		this.port = port
	}

	VertxProxy(Vertx vertx) {
		this(vertx, 5555)
	}

	void start() {
		server = vertx.createHttpServer().requestHandler { request ->
			println "Proxying request: ${request.headers['Host']} $request.path"
			for (header in request.headers) println " - $header.key = $header.value"

			def client = vertx.createHttpClient(host: request.headers['Host'])
			def onwardRequest = client.request(request.method, request.path) { response ->
				println "Proxying response $response.statusCode"
				request.response.chunked = true
				request.response.statusCode = response.statusCode
				request.response.headers << response.headers
				request.response.headers['Via'] = 'Vertx Proxy'
				response.dataHandler { data ->
					println "Proxying response body..."
					request.response << data
				}
				response.endHandler {
					request.response.end()
				}
			}

			//onwardRequest.chunked = true
			onwardRequest.headers << request.headers
			onwardRequest.headers['Via'] = 'Vertx Proxy'
			request.dataHandler { data ->
				onwardRequest << data
			}
			request.endHandler {
				onwardRequest.end()
			}

		}
		server.listen(port)

		System.properties.'http.proxyHost' = InetAddress.localHost.hostAddress
		System.properties.'http.proxyPort' = port.toString()
		System.properties.'https.proxyHost' = InetAddress.localHost.hostAddress
		System.properties.'https.proxyPort' = port.toString()
	}

	void stop() {
		def latch = new CountDownLatch(1)
		server.close {
			latch.countDown()
		}
		latch.await()

		System.clearProperty 'http.proxyHost'
		System.clearProperty 'http.proxyPort'
		System.clearProperty 'https.proxyHost'
		System.clearProperty 'https.proxyPort'
	}

}