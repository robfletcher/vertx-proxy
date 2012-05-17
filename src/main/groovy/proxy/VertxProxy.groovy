package proxy

import java.util.concurrent.*
import org.vertx.groovy.core.*
import static org.vertx.groovy.core.streams.Pump.createPump

class VertxProxy {

	static final HTTP_PORT = 5555
	static final HTTPS_PORT = 5443

	private final Vertx vertx
	private final int port
	private httpServer, httpsServer

	VertxProxy(Vertx vertx) {
		this.vertx = vertx
	}

	void start() {
		httpServer = vertx.createHttpServer().requestHandler { request ->
			proxyRequest request, false
		}
		httpServer.listen(HTTP_PORT)

		httpsServer = vertx.createHttpServer(SSL: true, keyStorePath: 'server-keystore.jks', keyStorePassword: 'wibble').requestHandler { request ->
			proxyRequest request, true
		}
		httpsServer.listen(HTTPS_PORT)

		System.properties.'http.proxyHost' = InetAddress.localHost.hostAddress
		System.properties.'http.proxyPort' = HTTP_PORT.toString()
		System.properties.'https.proxyHost' = InetAddress.localHost.hostAddress
		System.properties.'https.proxyPort' = HTTPS_PORT.toString()
		System.properties.'sun.security.ssl.allowUnsafeRenegotiation' = 'true'
	}

	void stop() {
		def latch = new CountDownLatch(2)
		httpServer.close {latch.countDown() }
		httpsServer.close {latch.countDown() }
		latch.await()

		System.clearProperty 'http.proxyHost'
		System.clearProperty 'http.proxyPort'
		System.clearProperty 'https.proxyHost'
		System.clearProperty 'https.proxyPort'
		System.clearProperty 'sun.security.ssl.allowUnsafeRenegotiation'
	}

	private void proxyRequest(request, boolean ssl) {
		def host = request.headers['Host']
		println "Proxying request: method=$request.method host=$host path=$request.path"
		for (header in request.headers) println " - $header.key = $header.value"

		def client = vertx.createHttpClient(SSL: ssl, trustAll: true, host: host, port: ssl ? 443 : 80)
		def onwardRequest = client.request(request.method, request.path) { response ->
			println "Proxying response $response.statusCode"
			for (header in response.headers) println " - $header.key = $header.value"
			request.response.chunked = true
			request.response.statusCode = response.statusCode
			request.response.headers << response.headers
			request.response.headers['Via'] = 'Vertx Proxy'
			response.dataHandler { data ->
				println "Proxying response body... $data"
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

}