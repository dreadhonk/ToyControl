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
        node.push(arrayListOf(0.000001f, 0.000002f, 0.000003f))
        assertEquals(node.outputs[0], 0.0f)
        assertEquals(node.outputs[1], 0.0f)
        assertEquals(node.outputs[2], 0.0f)
    }

    @Test
    fun outputNormalisedVector() {
        node.push(arrayListOf(1.0f, 0.0f, 0.0f))
        assertEquals(node.outputs[0], 1.0f)
        assertEquals(node.outputs[1], 0.0f)
        assertEquals(node.outputs[2], 0.0f)

        node.push(arrayListOf(9.0f, 0.0f, 0.0f))
        assertEquals(node.outputs[0], 1.0f)
        assertEquals(node.outputs[1], 0.0f)
        assertEquals(node.outputs[2], 0.0f)

        node.push(arrayListOf(1.0f, 1.0f, 1.0f))
        assertEquals(node.outputs[0], 0.5773502691896258f)
        assertEquals(node.outputs[1], 0.5773502691896258f)
        assertEquals(node.outputs[2], 0.5773502691896258f)

        node.push(arrayListOf(0.0f, 1.0f, 1.0f))
        assertEquals(node.outputs[0], 0.7071067811865475f)
        assertEquals(node.outputs[1], 0.7071067811865475f)
        assertEquals(node.outputs[2], 0.7071067811865475f)
    }
}