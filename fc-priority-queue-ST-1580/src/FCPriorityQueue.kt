import kotlinx.atomicfu.atomic
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val ARRAY_SIZE = 8
    private val helperCombiner = CombinerArray<E>(ARRAY_SIZE)

    enum class State {
        NONE, PEEK, POOL, ADD, DONE
    }

    private class Node<E> {
        val state = atomic(State.NONE)
        var elem: E? = null
    }

    private class CombinerArray<E> (arraySize: Int) {
        private val lock = atomic(false)
        val array: Array<Node<E>> = Array(arraySize) { Node() }

        fun tryLock(): Boolean {
            return lock.compareAndSet(expect = false, true)
        }

        fun unlock() {
           lock.value = false
        }
    }

    fun waitInCombiner(state: State, elem: E? = null): E? {
        var currOperationIndex = Random().nextInt(ARRAY_SIZE)
        while (true) {
            if (helperCombiner.array[currOperationIndex].state.compareAndSet(State.NONE, State.DONE)) {
                helperCombiner.array[currOperationIndex].elem = elem
                helperCombiner.array[currOperationIndex].state.value = state
                break
            }
            currOperationIndex = (currOperationIndex + 1) % ARRAY_SIZE
        }

        while (!helperCombiner.array[currOperationIndex].state.compareAndSet(State.DONE, State.DONE)) {
            if (helperCombiner.tryLock()) {
                for (i in 0 until ARRAY_SIZE) {
                    val currNode = helperCombiner.array[i]
                    when (currNode.state.value) {
                        State.PEEK -> { currNode.elem = q.peek() }
                        State.POOL -> { currNode.elem = q.poll() }
                        State.ADD -> { q.add(currNode.elem) }
                        else -> continue
                    }
                    currNode.state.value = State.DONE
                }

                helperCombiner.unlock()
            }
        }

        val res = helperCombiner.array[currOperationIndex].elem
        helperCombiner.array[currOperationIndex].state.compareAndSet(State.DONE, State.NONE)
        return res
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if (helperCombiner.tryLock()) {
            val res = q.poll();
            helperCombiner.unlock()
            return res
        }
        return waitInCombiner(State.POOL)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if (helperCombiner.tryLock()) {
            val res = q.peek();
            helperCombiner.unlock()
            return res
        }
        return waitInCombiner(State.PEEK)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (helperCombiner.tryLock()) {
            q.add(element);
            helperCombiner.unlock()
            return
        }
        waitInCombiner(State.ADD, element)
    }
}
