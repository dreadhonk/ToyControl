package eu.dreadhonk.apps.toycontrol.control

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RateLimitNode_ConstantTest: RateLimitNodeTest() {
    @Before
    fun setUpNode() {
        node = RateLimitNode(
            minPeriod=250,
            nIO=1,
            init=0.0f,
            constantRate=true,
            clock=::time::get
        )
    }

    @Test
    fun gateMultipleUpdatesTest() {
        node.inputs[0] = 1.0f
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(1.0f, node.outputs[0])
        assertTrue(node.invalidated)

        advanceTime(1)
        node.inputs[0] = 0.5f
        assertEquals(249, node.update())
        assertEquals(1.0f, node.outputs[0])
        assertTrue(node.invalidated)

        advanceTime(248)
        node.inputs[0] = 0.5f
        assertEquals(1, node.update())
        assertEquals(1.0f, node.outputs[0])
        assertTrue(node.invalidated)

        advanceTime(1)
        node.inputs[0] = 0.5f
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(0.5f, node.outputs[0])
        assertTrue(node.invalidated)
    }

    @Test
    fun passUpdatesWithEnoughDistanceTest() {
        node.inputs[0] = 1.0f
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(1.0f, node.outputs[0])
        assertTrue(node.invalidated)

        advanceTime(500)
        node.inputs[0] = 0.5f
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(0.5f, node.outputs[0])
        assertTrue(node.invalidated)
    }

    @Test
    fun nonStandardGateFunctionTest() {
        node.gateFunction = {
                old, new -> old + new
        }

        node.inputs[0] = 0.0f
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(0.0f, node.outputs[0])

        advanceTime(1)
        node.inputs[0] = 1.0f
        assertEquals(249, node.update())
        assertEquals(0.0f, node.outputs[0])

        advanceTime(1)
        node.inputs[0] = 2.0f
        assertEquals(248, node.update())
        assertEquals(0.0f, node.outputs[0])

        advanceTime(1)
        node.inputs[0] = 3.0f
        assertEquals(247, node.update())
        assertEquals(0.0f, node.outputs[0])

        advanceTime(250)
        node.inputs[0] = 4.0f
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(10.0f, node.outputs[0])
    }

    @Test
    fun gateFunctionResetsTest() {
        node.gateFunction = {
                old, new -> old + new
        }

        node.inputs[0] = 0.0f
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(0.0f, node.outputs[0])

        advanceTime(1)
        node.inputs[0] = 1.0f
        assertEquals(249, node.update())
        assertEquals(0.0f, node.outputs[0])

        advanceTime(250)
        node.inputs[0] = 4.0f
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(5.0f, node.outputs[0])

        advanceTime(1)
        node.inputs[0] = 2.0f
        assertEquals(249, node.update())
        assertEquals(5.0f, node.outputs[0])

        advanceTime(250)
        node.inputs[0] = 1.0f
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(3.0f, node.outputs[0])
    }
}