package eu.dreadhonk.apps.toycontrol.control

import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NormalisedGravityNodeTest {
    private lateinit var node: NormalisedGravityNode

    @Before
    fun setUp() {
        node = NormalisedGravityNode()
    }

    @Test
    fun treatSmallInputsAsNull() {
        floatArrayOf(0.000001f, 0.000002f, 0.000003f).copyInto(node.inputs)
        node.update()
        assertEquals(0.0f, node.outputs[0])
        assertEquals(0.0f, node.outputs[1])
        assertEquals(0.0f, node.outputs[2])
    }

    @Test
    fun outputNormalisedVector() {
        floatArrayOf(1.0f, 0.0f, 0.0f).copyInto(node.inputs)
        node.update()
        assertEquals(1.0f, node.outputs[0])
        assertEquals(0.0f, node.outputs[1])
        assertEquals(0.0f, node.outputs[2])

        floatArrayOf(9.0f, 0.0f, 0.0f).copyInto(node.inputs)
        node.update()
        assertEquals(1.0f, node.outputs[0])
        assertEquals(0.0f, node.outputs[1])
        assertEquals(0.0f, node.outputs[2])

        floatArrayOf(1.0f, 1.0f, 1.0f).copyInto(node.inputs)
        node.update()
        assertEquals(0.5773502691896258f, node.outputs[0])
        assertEquals(0.5773502691896258f, node.outputs[1])
        assertEquals(0.5773502691896258f, node.outputs[2])

        floatArrayOf(0.0f, 1.0f, 1.0f).copyInto(node.inputs)
        node.update()
        assertEquals(0.0f, node.outputs[0])
        assertEquals(0.7071067811865475f, node.outputs[1])
        assertEquals(0.7071067811865475f, node.outputs[2])
    }
}