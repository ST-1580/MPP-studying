import kotlinx.atomicfu.*

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        if (index < core.value.firstAvailable.value) {
            val currValue = core.value.getArray()[index].value;
            if (currValue != null) {
                return currValue.elem
            }
        }
        throw IllegalArgumentException()
    }

    override fun put(index: Int, element: E) {
        if (index < core.value.firstAvailable.value) {
            while (true) {
                val currCore = core.value
                val currNode = currCore.getArray()[index].value
                if (currNode is Active && currCore.getArray()[index].compareAndSet(currNode, Active(element))) {
                    return
                }
            }
        }
        throw IllegalArgumentException()
    }

    override fun pushBack(element: E) {
        while (true) {
            val currCore = core.value;
            val firstAvailable = currCore.firstAvailable.value
            if (firstAvailable >= currCore.capacity) {
                val nextCore = currCore.next.value
                if (nextCore != null) {
                    var i = 0
                    while (i < currCore.capacity) {
                        val currNode = currCore.getArray()[i].value
                        if (currNode != null) {
                            if (currNode is Active) {
                                if (currCore.getArray()[i].compareAndSet(currNode, Moving(currNode.elem))) {
                                    if (nextCore.getArray()[i].compareAndSet(null, Active(currNode.elem))) {
                                        val currMovingValue = currCore.getArray()[i].value
                                        if (currMovingValue is Moving) {
                                            currCore.getArray()[i].compareAndSet(currMovingValue, Moved(currNode.elem))
                                            i++
                                        }
                                    }
                                }
                            } else if (currNode is Moving) {
                                nextCore.getArray()[i].compareAndSet(null, Active(currNode.elem))
                                currCore.getArray()[i].compareAndSet(currNode, Moved(currNode.elem))
                                i++
                            } else {
                                i++
                            }
                        }
                    }
                    core.compareAndSet(currCore, nextCore)
                } else {
                    val newCore = Core<E>(currCore.capacity * 2)
                    newCore.firstAvailable.compareAndSet(0, firstAvailable)
                    currCore.next.compareAndSet(null, newCore)
                }
            } else if (currCore.getArray()[firstAvailable].compareAndSet(null, Active(element))
                && currCore.firstAvailable.compareAndSet(firstAvailable, firstAvailable + 1)) {
                return;
            }
        }
    }

    override val size: Int get() {
        return core.value.firstAvailable.value
    }
}

private abstract class Node<E>(
    val elem: E
)

private class Active<E> (elem: E) : Node<E>(elem)
private class Moved<E> (elem: E) : Node<E>(elem)
private class Moving<E> (elem: E) : Node<E>(elem)

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<Node<E>>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
    val firstAvailable = atomic(0)
    val capacity = capacity

    fun getArray(): AtomicArray<Node<E>?> {
        return array
    }
}

private const val INITIAL_CAPACITY = 1