package proxy

import spock.lang.*
import spock.util.concurrent.*
import org.vertx.groovy.core.*

class VertxProxySpec extends Specification {

	@Shared Vertx vertx = Vertx.newVertx()
	def proxy = new VertxProxy(vertx)
	def responseBody = new BlockingVariable<String>(5)
	def responseHeaders = new BlockingVariable<Map>(5)
	def responseHandler = { resp ->
		println "Got response ${resp.statusCode}..."
		responseHeaders.set(resp.headers)
		def responseBuffer = new StringBuilder()
		resp.bodyHandler { body -> 
			responseBuffer << body.toString()
		}
		resp.endHandler {
			responseBody.set(responseBuffer.toString()) 
		}
	}

	void setup() {
		proxy.start()
	}

	void cleanup() {
		proxy.stop()
	}
	
	void 'can proxy an http request'() {
		when:
		def request = vertx.createHttpClient(port: VertxProxy.HTTP_PORT).get('/betamax/', responseHandler)
		request.headers['Host'] = 'freeside.co'
		request.end()

		then:
		responseBody.get().startsWith('<!DOCTYPE html>')

		and:
		responseHeaders.get()['Via'] == 'Vertx Proxy'
	}

	void 'can proxy an https request'() {
		when:
		def request = vertx.createHttpClient(port: VertxProxy.HTTPS_PORT, SSL: true, trustAll: true).get('/robfletcher/betamax/master/readme.md', responseHandler)
		request.headers['Host'] = 'raw.github.com'
		request.end()

		then:
		responseHeaders.get()['Via'] == 'Vertx Proxy'

		and:
		responseBody.get().startsWith('# Betamax')
	}

}