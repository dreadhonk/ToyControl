package eu.dreadhonk.apps.toycontrol.control

import android.util.Log
import kotlin.IllegalArgumentException

class ControlGraph {
    private val nodes = ArrayList<Node>();
    private var topologyInvalidated = true
    private var sortedNodes = ArrayList<Node>();

    private data class NodeInSlot(public val node: Node, public val inIndex: Int) {
        init {
            if (node.inputs.size <= inIndex || inIndex < 0) {
                throw IllegalArgumentException("attempt to reference non-existent input")
            }
        }
    }

    private data class NodeOutSlot(public val node: Node, public val outIndex: Int) {
        init {
            if (node.inputs.size <= outIndex || outIndex < 0) {
                throw IllegalArgumentException("attempt to reference non-existent output")
            }
        }
    }

    private data class HalfEdge(public val outIndex: Int, public val dest: NodeInSlot)

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
    private val inEdges = HashMap<NodeInSlot, NodeOutSlot>();

    private val dirtyFlags = HashMap<Node, Boolean>();

    // handling of dirty bits (hehe)

    private fun nodeHasInternalInputs(node: Node, inEdges: Map<NodeInSlot, NodeOutSlot>? = null): Boolean {
        val inEdges = if (inEdges == null) {
            this.inEdges
        } else {
            inEdges
        }

        for (inIndex in 0 until node.inputs.size) {
            val inSlot = NodeInSlot(node, inIndex)
            val outSlot = inEdges.get(inSlot)
            if (outSlot != null) {
                // valid input, abort
                return true
            }
        }

        return false
    }

    private fun checkEdgeConsistency(
        inEdges: Map<NodeInSlot, NodeOutSlot>,
        outEdges: Map<Node, List<HalfEdge>>)
    {
        // TODO: disable this outside of debug builds
        //Log.v("ControlGraph", "checkEdgeConsistency()")
        // check out edge -> in edge consistency
        for (nodeEntry in outEdges.entries) {
            val outNode = nodeEntry.key
            val nodeHalfEdges = nodeEntry.value
            for (halfEdge in nodeHalfEdges) {
                val outSlot = NodeOutSlot(outNode, halfEdge.outIndex)
                val inSlot = halfEdge.dest
                val existingOutSlot = inEdges.get(inSlot)
                /*Log.v("ControlGraph", String.format("checkEdgeConsistency: outEdges: %s:%d -> %s:%d",
                    outSlot.node,
                    outSlot.outIndex,
                    inSlot.node,
                    inSlot.inIndex
                ))*/
                if (existingOutSlot == null) {
                    throw IllegalArgumentException(String.format(
                        "graph invariant violated: edge %s:%d -> %s:%d in outEdges, but not in inEdges",
                        outSlot.node,
                        outSlot.outIndex,
                        inSlot.node,
                        inSlot.inIndex
                    ))
                }
                if (inEdges.get(inSlot) != outSlot) {
                    throw IllegalArgumentException(String.format(
                        "graph invariant violated: input %s:%d maps to %s:%d via outEdges and to %s:%d via inEdges",
                        inSlot.node,
                        inSlot.inIndex,
                        outSlot.node,
                        outSlot.outIndex,
                        existingOutSlot.node,
                        existingOutSlot.outIndex
                    ))
                }
            }
        }

        // check in edge -> out edge consistency
        for (edgeEntry in inEdges.entries) {
            val inSlot = edgeEntry.key
            val outSlot = edgeEntry.value
            val halfEdge = HalfEdge(outSlot.outIndex, inSlot)
            val outEdges = outEdges.get(outSlot.node)
            /*Log.v("ControlGraph", String.format("checkEdgeConsistency: inEdges: %s:%d -> %s:%d",
                outSlot.node,
                outSlot.outIndex,
                inSlot.node,
                inSlot.inIndex
            ))*/
            if (outEdges == null || !outEdges.contains(halfEdge)) {
                throw IllegalArgumentException(String.format(
                    "graph invariant violated: edge %s:%d -> %s:%d in inEdges, but not in outEdges",
                    outSlot.node,
                    outSlot.outIndex,
                    inSlot.node,
                    inSlot.inIndex
                ))
            }
        }
    }

    private fun cloneOutEdges(input: HashMap<Node, ArrayList<HalfEdge>>): HashMap<Node, ArrayList<HalfEdge>> {
        val result = HashMap<Node, ArrayList<HalfEdge>>()
        for (entry in input.entries) {
            result[entry.key] = (entry.value.clone() as ArrayList<HalfEdge>)
        }
        return result
    }

    private fun updateTopology() {
        //Log.v("ControlGraph", "updateTopology()")
        checkEdgeConsistency(inEdges, outEdges)
        topologyInvalidated = false
        val noincoming = ArrayList<Node>();
        val tmpOutEdges = cloneOutEdges(outEdges)
        val tmpInEdges = (inEdges.clone() as HashMap<NodeInSlot, NodeOutSlot>);

        // iteratively prune all nodes which have no side effects and no outputs from the copied
        // graph
        val tmpNodes = (nodes.clone() as ArrayList<Node>)
        var changed = true
        while (changed) {
            changed = false
            for (nodeIndex in tmpNodes.indices.reversed()) {
                val node = tmpNodes[nodeIndex]
                if (node.hasSideEffects) {
                    continue
                }
                if (tmpOutEdges.containsKey(node)) {
                    continue
                }
                // prune node. remove the node and then all out edges it has
                tmpNodes.removeAt(nodeIndex)
                for (inIndex in 0 until node.inputs.size) {
                    val inSlot = NodeInSlot(node, inIndex)
                    val existingOutSlot = tmpInEdges.remove(inSlot)
                    if (null != existingOutSlot) {
                        changed = true
                        val sourceOutEdges = tmpOutEdges.get(existingOutSlot.node)!!
                        sourceOutEdges.remove(HalfEdge(existingOutSlot.outIndex, inSlot))
                        if (sourceOutEdges.isEmpty()) {
                            tmpOutEdges.remove(existingOutSlot.node)
                        }
                    }
                }
                Log.d(
                    "ControlGraph",
                    String.format("updateTopology: pruned unused node %s", node)
                )
            }
        }

        checkEdgeConsistency(tmpInEdges, tmpOutEdges)

        Log.d("ControlGraph", String.format("updateTopology: node prune done. removed %d nodes and %d/%d edges",
            nodes.size - tmpNodes.size,
            inEdges.size - tmpInEdges.size,
            outEdges.size - tmpOutEdges.size))

        sortedNodes.clear()
        sortedNodes.ensureCapacity(tmpNodes.size)

        for (inNode in tmpNodes) {
            if (!nodeHasInternalInputs(inNode, tmpInEdges)) {
                //Log.v("ControlGraph", String.format("updateTopology: adding %s to initial set", inNode.javaClass.simpleName))
                noincoming.add(inNode)
            }
        }

        while (noincoming.isNotEmpty()) {
            val currNode = noincoming.removeAt(noincoming.size - 1)
            //Log.v("ControlGraph", String.format("updateTopology: processing: %s", currNode.javaClass.simpleName))
            sortedNodes.add(currNode)
            val dests = tmpOutEdges.get(currNode)
            if (dests == null || dests.isEmpty()) {
                //Log.v("ControlGraph", String.format("updateTopology: no outbound edges"))
                continue
            }

            for (edge in dests) {
                /*Log.v("ControlGraph", String.format("updateTopology: processing edge %s:%d -> %s:%d",
                    currNode,
                    edge.outIndex,
                    edge.dest.node,
                    edge.dest.inIndex))*/
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
            for (edge in tmpInEdges) {
                Log.d("ControlGraph",
                    String.format("remaining edge: %s:%d <- %s:%d",
                        edge.key.node, edge.key.inIndex,
                        edge.value.node, edge.value.outIndex)
                )
            }
            throw IllegalArgumentException("graph has loops: that's bad")
        }

        if (sortedNodes.size != tmpNodes.size) {
            throw RuntimeException("something went terribly wrong")
        }
    }


    public fun addNode(node: Node) {
        nodes.add(node)
        topologyInvalidated = true
    }

    public fun unlinkInput(inputNode: Node, inputIndex: Int) {
        val inSlot = NodeInSlot(inputNode, inputIndex)
        val source = inEdges.remove(inSlot)
        if (source == null) {
            return
        }

        val sourceNode = source.node
        outEdges.get(sourceNode)?.remove(HalfEdge(source.outIndex, inSlot))
        // this cannot cause a loop, so no need to force a full update
        topologyInvalidated = true
        checkEdgeConsistency(inEdges, outEdges)
    }

    public fun link(outputNode: Node, outputIndex: Int,
                    inputNode: Node, inputIndex: Int)
    {
        /*Log.v("ControlGraph", String.format("link(%s, %d, %s, %d)",
            outputNode, outputIndex,
            inputNode, inputIndex))*/
        checkEdgeConsistency(inEdges, outEdges)
        val inSlot = NodeInSlot(inputNode, inputIndex)
        val outSlot = NodeOutSlot(outputNode, outputIndex)
        if (inEdges.containsKey(inSlot)) {
            unlinkInput(inputNode, inputIndex)
        }

        val outEdgeArray = if (!outEdges.containsKey(outputNode)) {
            val newArray = ArrayList<HalfEdge>();
            outEdges[outputNode] = newArray
            newArray
        } else {
            outEdges[outputNode]!!
        }

        val newHalfEdge = HalfEdge(outputIndex, inSlot)
        inEdges[inSlot] = outSlot
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
        //Log.v("ControlGraph", "new run")
        var minDelay = ToyController.REQUIRES_INPUT_CHANGE

        if (topologyInvalidated) {
            updateTopology()
        }

        dirtyFlags.clear()

        for (inNode in sortedNodes) {
            var updated = false
            for (inIndex in 0 until inNode.inputs.size) {
                val outEdge = inEdges.get(NodeInSlot(inNode, inIndex))
                if (outEdge == null) {
                    continue
                }

                val dirtyFlag = dirtyFlags.get(outEdge.node)
                if (dirtyFlag != null && dirtyFlag != false) {
                    inNode.inputs[inIndex] = outEdge.node.outputs[outEdge.outIndex]
                    updated = true
                }
            }

            //Log.v("ControlGraph", String.format("%s node: has updated inputs: %s", inNode.javaClass.simpleName, updated))

            if (updated || inNode.invalidated) {
                val delay = inNode.update()
                // Log.v("ControlGraph", String.format("%s node: returned %d", inNode.javaClass.simpleName, delay))
                if (delay == ToyController.RESULT_UPDATED) {
                    dirtyFlags.put(inNode, true)
                } else if (delay > ToyController.RESULT_UPDATED) {
                    if (minDelay == ToyController.REQUIRES_INPUT_CHANGE || minDelay > delay) {
                        minDelay = delay
                    }
                }
            }
        }

        return minDelay
    }

    public fun isNodeUsed(node: Node): Boolean {
        return sortedNodes.contains(node)
    }
}
