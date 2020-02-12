package eu.dreadhonk.apps.toycontrol.control

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EnvelopeNodeTest {
    private lateinit var node: EnvelopeNode

    @Before
    fun setUp() {
        node = EnvelopeNode(
            attack=0.9f,
            decay=0.5f,
            nIO=1,
            init=0.5f
        )
    }

    @Test
    fun attackTest() {
        floatArrayOf(1.0f).copyInto(node.inputs)
        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.95f, node.outputs[0])

        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.995f, node.outputs[0])

        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.9995f, node.outputs[0])
    }

    @Test
    fun decayTest() {
        floatArrayOf(0.0f).copyInto(node.inputs)
        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.25f, node.outputs[0])

        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.125f, node.outputs[0])

        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.0625f, node.outputs[0])
    }

    @Test
    fun envelopeTest() {
        // undocumented hack \o/
        node.outputs[0] = 0.0f

        floatArrayOf(1.0f).copyInto(node.inputs)
        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.9f, node.outputs[0])

        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.99f, node.outputs[0])

        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.999f, node.outputs[0])

        floatArrayOf(0.0f).copyInto(node.inputs)
        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.4995f, node.outputs[0])

        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.24975f, node.outputs[0])

        assertEquals(ToyController.UPDATE_IMMEDIATELY, node.update())
        assertEquals(0.124875f, node.outputs[0])
    }
}