package eu.dreadhonk.apps.toycontrol.control

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MagnitudeNodeTest {
    private lateinit var node: MagnitudeNode

    @Before
    fun setUp() {
        node = MagnitudeNode(
            nIO=4
        )
    }

    @Test
    fun initialState() {
        assertTrue(node.invalidated)
    }

    @Test
    fun updateClearsInvalidated() {
        node.invalidated = true
        node.update()
        assertFalse(node.invalidated)
    }

    @Test
    fun oneTest() {
        floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f).copyInto(node.inputs)
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(1.0f, node.outputs[0])

        floatArrayOf(0.0f, 0.7071067811865476f, 0.7071067811865476f, 0.0f).copyInto(node.inputs)
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(1.0f, node.outputs[0], 0.000001f)

        floatArrayOf(0.5773502691896257f, 0.5773502691896257f, 0.5773502691896257f, 0.0f).copyInto(node.inputs)
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(1.0f, node.outputs[0], 0.000001f)

        floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f).copyInto(node.inputs)
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(1.0f, node.outputs[0], 0.000001f)
    }

    @Test
    fun zeroTest() {
        floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f).copyInto(node.inputs)
        assertEquals(ToyController.RESULT_UPDATED, node.update())
        assertEquals(0.0f, node.outputs[0])
    }
}