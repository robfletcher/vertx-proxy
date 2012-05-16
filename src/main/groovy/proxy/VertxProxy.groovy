package proxy

import java.util.concurrent.*
import org.vertx.groovy.core.*
import static org.vertx.groovy.core.streams.Pump.createPump

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
			if (request.method == 'CONNECT') {
				proxyConnect request
			} else {
				proxyRequest request
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

	private void proxyRequest(request) {
		def host = request.headers['Host']
		println "Proxying request: $request.method $host $request.path"
		for (header in request.headers) println " - $header.key = $header.value"

		def client = vertx.createHttpClient(host: host)
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

	private void proxyConnect(request) {
		def host = request.headers['Host']
		println "Proxying $request.method to $host..."
		for (header in request.headers) println " - $header.key = $header.value"

		request.response.statusCode = 200
		request.response.headers['Proxy-agent'] = 'Vertx Proxy/1.0'

		def client = vertx.createNetClient(host: host, SSL: true, trustAll: true) { socket ->
			println 'Socket established, pumping data...'
			createPump socket, request.response
		}

		request.endHandler {
			println 'Client closed HTTPS tunnel, closing socket...'
			client.close()
			request.response.end()
		}
	}

}