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
			println "Proxying request: ${request}"

		    request.response.chunked = true
		    request.response.statusCode = 200
		    request.response << 'O HAI!'
		    request.response.end()

		}
		server.listen(port)

		System.properties.'http.proxyHost' = InetAddress.localHost.hostAddress
		System.properties.'http.proxyPort' = port.toString()
	}

	void stop() {
		def latch = new CountDownLatch(1)
		server.close {
			latch.countDown()
		}
		latch.await()

		System.clearProperty 'http.proxyHost'
		System.clearProperty 'http.proxyPort'
	}

}