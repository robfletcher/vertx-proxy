package proxy

import spock.lang.*
import org.vertx.groovy.core.*

class VertxProxySpec extends Specification {

	@Shared Vertx vertx = Vertx.newVertx()
	def proxy = new VertxProxy(vertx)

	void setup() {
		proxy.start()
	}

	void cleanup() {
		proxy.stop()
	}
	
	void 'can proxy an http request'() {
		given:
		def connection = new URL('http://freeside.co/betamax').openConnection()
		connection.readTimeout = 5000
		connection.connectTimeout = 5000

		expect:
		connection.inputStream.text.startsWith('<!DOCTYPE html>')

		and:
		connection.getHeaderField('Via') == 'Vertx Proxy'
	}

	void 'can proxy an https request'() {
		given:
		def connection = new URL('https://raw.github.com/robfletcher/betamax/master/readme.md').openConnection()
		connection.readTimeout = 5000
		connection.connectTimeout = 5000

		expect:
		connection.inputStream.text.startsWith('# Betamax')

		and:
		connection.getHeaderField('Via') == 'Vertx Proxy'
	}

}