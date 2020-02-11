package eu.dreadhonk.apps.toycontrol.control

import android.util.Log
import java.lang.IllegalArgumentException

class ControlGraph {
    private val nodes = ArrayList<Node>();
    private var topologyInvalidated = true
    private var sortedNodes = ArrayList<Node>();

    private data class NodeSlot(public val node: Node, public val ioIndex: Int) {
    }

    private data class HalfEdge(public val outIndex: Int, public val dest: NodeSlot) {

    }

    /**
     * Map the edges which *start* at Node and end somewhere else.
     *
     * keys are nodes which output values, value is an array of the destinations
     */
    private val outEdges = HashMap<Node, ArrayList<HalfEdge> >();

    /**
     * Map the edges which terminate at a node.
     *
     * keys are the inputs, values are the outputs.
     */
    private val inEdges = HashMap<NodeSlot, NodeSlot>();

    private val dirtyFlags = HashMap<Node, Boolean>();

    // handling of dirty bits (hehe)

    private fun nodeHasInternalInputs(node: Node, inEdges: HashMap<NodeSlot, NodeSlot>? = null): Boolean {
        val inEdges = if (inEdges == null) {
            this.inEdges
        } else {
            inEdges
        }

        for (inIndex in 0 until node.inputs.size) {
            val inSlot = NodeSlot(node, inIndex)
            val outSlot = inEdges.get(inSlot)
            if (outSlot == null) {
                // input unassigned
                continue;
            }
            // valid input, abort
            return true
        }

        return false
    }

    private fun updateTopology() {
        topologyInvalidated = false
        Log.v("ControlGraph", "updateTopology()")
        val noincoming = ArrayList<Node>();
        val tmpOutEdges = (outEdges.clone() as HashMap<Node, ArrayList<HalfEdge>>)
        val tmpInEdges = HashMap<NodeSlot, NodeSlot>();

        for (k in inEdges.keys) {
            val v = inEdges.get(k)!!
            tmpInEdges.put(k, v)
        }

        sortedNodes.clear()
        sortedNodes.ensureCapacity(nodes.size)

        for (inNode in nodes) {
            if (!nodeHasInternalInputs(inNode, tmpInEdges)) {
                Log.v("ControlGraph", String.format("updateTopology: adding %s to initial set", inNode.javaClass.simpleName))
                noincoming.add(inNode)
            }
        }

        while (noincoming.isNotEmpty()) {
            val currNode = noincoming.removeAt(noincoming.size - 1)
            Log.v("ControlGraph", String.format("updateTopology: processing: %s", currNode.javaClass.simpleName))
            sortedNodes.add(currNode)
            val dests = tmpOutEdges.get(currNode)
            if (dests == null) {
                continue
            }

            for (edge in dests) {
                tmpInEdges.remove(edge.dest)
                if (!nodeHasInternalInputs(edge.dest.node, tmpInEdges)) {
                    if (noincoming.contains(edge.dest.node)) {
                        throw RuntimeException(String.format("while detaching edges of %s node, %s was added to the noincoming list twice",
                            currNode.javaClass.simpleName,
                            edge.dest.node.javaClass.simpleName
                        ))
                    }
                    noincoming.add(edge.dest.node)
                }
            }
        }

        if (tmpInEdges.isNotEmpty()) {
            throw IllegalArgumentException("graph has loops: that's bad")
        }

        if (sortedNodes.size != nodes.size) {
            throw RuntimeException("something went terribly wrong")
        }
    }


    public fun addNode(node: Node) {
        nodes.add(node)
        topologyInvalidated = true
    }

    public fun unlinkInput(inputNode: Node, inputIndex: Int) {
        val inSlot = NodeSlot(inputNode, inputIndex)
        val source = inEdges.get(inSlot)
        if (source == null) {
            return
        }

        val sourceNode = source.node
        outEdges.get(sourceNode)?.remove(HalfEdge(source.ioIndex, inSlot))
        // this cannot cause a loop, so no need to force a full update
        topologyInvalidated = true
    }

    public fun link(outputNode: Node, outputIndex: Int,
                    inputNode: Node, inputIndex: Int)
    {
        if (outputNode.outputs.size <= outputIndex || outputIndex < 0) {
            throw IllegalArgumentException("attempt to use non-existent output")
        }

        if (inputNode.inputs.size <= inputIndex || inputIndex < 0) {
            throw IllegalArgumentException("attempt to use non-existent input")
        }

        val inSlot = NodeSlot(inputNode, inputIndex)
        val outSlot = NodeSlot(outputNode, outputIndex)
        if (inEdges.containsKey(inSlot)) {
            unlinkInput(inputNode, inputIndex)
        }

        val outEdgeArray = if (!outEdges.containsKey(outputNode)) {
            val newArray = ArrayList<HalfEdge>();
            outEdges.put(outputNode, newArray)
            newArray
        } else {
            outEdges.get(outputNode)!!
        }

        val newHalfEdge = HalfEdge(outputIndex, inSlot)
        inEdges.put(inSlot, outSlot)
        outEdgeArray.add(newHalfEdge)
        try {
            updateTopology()
        } catch (e: IllegalArgumentException) {
            // reverse the changes which broke the thing
            outEdgeArray.remove(newHalfEdge)
            inEdges.remove(inSlot)
            throw e;
        }
    }

    public fun update(): Long {
        Log.v("ControlGraph", "new run")
        var minDelay = ToyController.UPDATE_ON_INPUT_CHANGE

        if (topologyInvalidated) {
            updateTopology()
        }

        dirtyFlags.clear()

        for (inNode in sortedNodes) {
            var updated = false
            for (inIndex in 0 until inNode.inputs.size) {
                val outEdge = inEdges.get(NodeSlot(inNode, inIndex))
                if (outEdge == null) {
                    continue
                }

                val dirtyFlag = dirtyFlags.get(outEdge.node)
                if (dirtyFlag != null && dirtyFlag != false) {
                    inNode.inputs[inIndex] = outEdge.node.outputs[outEdge.ioIndex]
                    updated = true
                }
            }

            Log.v("ControlGraph", String.format("%s node: has updated inputs: %s", inNode.javaClass.simpleName, updated))

            if (updated || inNode.invalidated) {
                val delay = inNode.update()
                Log.v("ControlGraph", String.format("%s node: returned %d", inNode.javaClass.simpleName, delay))
                if (delay == ToyController.UPDATE_IMMEDIATELY) {
                    dirtyFlags.put(inNode, true)
                } else if (delay > ToyController.UPDATE_IMMEDIATELY) {
                    if (minDelay == ToyController.UPDATE_ON_INPUT_CHANGE || minDelay > delay) {
                        minDelay = delay
                    }
                }
            }
        }

        return minDelay
    }
}