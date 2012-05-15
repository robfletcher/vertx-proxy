package proxy

import spock.lang.*
import org.vertx.groovy.core.*

class VertxProxySpec extends Specification {

	@Shared Vertx vertx = Vertx.newVertx()
	
	void 'can proxy an http request'() {
		given:
		def proxy = new VertxProxy(vertx)
		proxy.start()

		expect:
		new URL('http://freeside.co/betamax').text == 'O HAI!'

		cleanup:
		proxy.stop()
	}

}