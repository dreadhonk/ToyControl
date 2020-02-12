package eu.dreadhonk.apps.toycontrol.control

import org.junit.Test
import org.junit.Before
import org.junit.Assert.*

class QuantizerNodeTest {
    private lateinit var node: QuantizerNode

    @Before
    fun setUp() {
        node = QuantizerNode()
    }

    @Test
    fun initState() {
        assertEquals(2, node.stepCount)
        assertEquals(1, node.inputs.size)
        assertEquals(1, node.outputs.size)
        assertEquals(0.0f, node.deadZone)
    }

    @Test(expected = RuntimeException::class)
    fun validateStepCountNotZero() {
        node.stepCount = 0
    }

    @Test(expected = RuntimeException::class)
    fun validateStepCountNotNegative() {
        node.stepCount = -2
    }

    @Test
    fun quantisation() {
        node.stepCount = 5

        node.inputs[0] = 0.0f
        node.update()
        assertEquals(node.outputs[0], 0.0f)

        node.inputs[0] = 0.12499f
        node.update()
        assertEquals(node.outputs[0], 0.0f)

        node.inputs[0] = 0.125f
        node.update()
        assertEquals(node.outputs[0], 0.25f)

        node.inputs[0] = 0.37499f
        node.update()
        assertEquals(node.outputs[0], 0.25f)

        node.inputs[0] = 0.375f
        node.update()
        assertEquals(node.outputs[0], 0.5f)

        node.inputs[0] = 0.62499f
        node.update()
        assertEquals(node.outputs[0], 0.5f)

        node.inputs[0] = 0.625f
        node.update()
        assertEquals(node.outputs[0], 0.75f)

        node.inputs[0] = 0.87499f
        node.update()
        assertEquals(node.outputs[0], 0.75f)

        node.inputs[0] = 0.875f
        node.update()
        assertEquals(node.outputs[0], 1.0f)

        node.inputs[0] = 1.0f
        node.update()
        assertEquals(node.outputs[0], 1.0f)
    }

    @Test
    fun updateReturnValue() {
        node.stepCount = 5

        node.inputs[0] = 0.0f
        node.update()
        assertEquals(node.outputs[0], 0.0f)

        node.inputs[0] = 0.12f
        assertEquals(node.update(), ToyController.UPDATE_ON_INPUT_CHANGE)
        assertEquals(node.outputs[0], 0.0f)

        node.inputs[0] = 0.4f
        assertEquals(node.update(), ToyController.UPDATE_IMMEDIATELY)
        assertEquals(node.outputs[0], 0.5f)

        node.inputs[0] = 0.2f
        assertEquals(node.update(), ToyController.UPDATE_IMMEDIATELY)
        assertEquals(node.outputs[0], 0.25f)

        node.inputs[0] = 0.9f
        assertEquals(node.update(), ToyController.UPDATE_IMMEDIATELY)
        assertEquals(node.outputs[0], 1.0f)

        node.inputs[0] = 0.875f
        assertEquals(node.update(), ToyController.UPDATE_ON_INPUT_CHANGE)
        assertEquals(node.outputs[0], 1.0f)
    }

    @Test
    fun firstUpdateReturnsTrueEvenIfUnchanged() {
        node.stepCount = 2
        node.inputs[0] = 0.0f
        val oldOutput = node.outputs[0]
        assertEquals(node.update(), ToyController.UPDATE_IMMEDIATELY)
        assertEquals(node.outputs[0], oldOutput)
    }

    @Test
    fun secondUpdateReturnsFalseIfUnchanged() {
        node.stepCount = 2
        node.inputs[0] = 0.0f
        val oldOutput = node.outputs[0]
        assertEquals(node.update(), ToyController.UPDATE_IMMEDIATELY)
        assertEquals(node.outputs[0], oldOutput)

        assertEquals(node.update(), ToyController.UPDATE_ON_INPUT_CHANGE)
        assertEquals(node.outputs[0], oldOutput)
    }

    @Test
    fun deadZoneTestUpwards() {
        node.stepCount = 11
        node.deadZone = 0.1f // 10% dead zone

        node.inputs[0] = 0.1f
        node.update()
        assertEquals(node.outputs[0], 0.1f)

        node.inputs[0] = 0.041f
        assertEquals(node.update(), ToyController.UPDATE_ON_INPUT_CHANGE)
        assertEquals(node.outputs[0], 0.1f)

        node.inputs[0] = 0.039f
        assertEquals(node.update(), ToyController.UPDATE_IMMEDIATELY)
        assertEquals(node.outputs[0], 0.0f)
    }

    @Test
    fun deadZoneTestDownwards() {
        node.stepCount = 11
        node.deadZone = 0.1f // 10% dead zone

        node.inputs[0] = 0.3f
        node.update()
        assertEquals(node.outputs[0], 0.3f)

        node.inputs[0] = 0.359f
        assertEquals(node.update(), ToyController.UPDATE_ON_INPUT_CHANGE)
        assertEquals(node.outputs[0], 0.3f)

        node.inputs[0] = 0.361f
        assertEquals(node.update(), ToyController.UPDATE_IMMEDIATELY)
        assertEquals(node.outputs[0], 0.4f)
    }
}