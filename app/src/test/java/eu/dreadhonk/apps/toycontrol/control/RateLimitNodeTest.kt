package eu.dreadhonk.apps.toycontrol.control

import org.junit.Before

open class RateLimitNodeTest {
    protected lateinit var node: RateLimitNode
    protected var time: Long = 100000

    protected fun advanceTime(by: Long) {
        time += by
    }

    @Before
    fun setUp() {
        time = 100000
    }
}