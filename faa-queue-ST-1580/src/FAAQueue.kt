import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
class FAAQueue<T> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: T) {
        while (true) {
            val currTail = tail.value;
            val enqIdx = currTail.enqIdx.getAndIncrement()
            if (enqIdx >= SEGMENT_SIZE) {
                val newTail = Segment(x)
                if (currTail.next.compareAndSet(null, newTail)) {
                    tail.compareAndSet(currTail, newTail)
                    return
                } else {
                    val nextTail = currTail.next.value
                    if (nextTail != null) {
                        tail.compareAndSet(currTail, nextTail)
                    }
                }
            } else if (currTail.elements[enqIdx].compareAndSet(null, x)) {
                return;
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): T? {
        while (true) {
            val currHead = head.value
            val deqIdx = currHead.deqIdx.getAndIncrement();
            if (deqIdx >= SEGMENT_SIZE) {
                val nextHead = currHead.next.value
                if (nextHead != null) {
                    head.compareAndSet(currHead, nextHead)
                } else {
                    return null
                }
            } else {
                val res = currHead.elements[deqIdx].getAndSet(DONE) ?: continue
                return res as T?
            }            
        }
    }

    /**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     */
    val isEmpty: Boolean get() {
        while (true) {
            val currHead = head.value;
            if (currHead.isEmpty) {
                if (currHead.next.compareAndSet(null, null)) {
                    return true;
                }
                val nextSegment = head.value.next.value
                if (nextSegment != null) {
                    head.compareAndSet(currHead, nextSegment)
                }
            } else {
                return false
            }
        }
    }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val enqIdx = atomic(0) // index for the next enqueue operation
    val deqIdx = atomic(0) // index for the next dequeue operation
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor() // for the first segment creation

    constructor(x: Any?) { // each next new segment should be constructed with an element
        enqIdx.value = 1
        elements[0].value = x
    }

    val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE

}

private val DONE = Any() // Marker for the "DONE" slot state; to avoid memory leaks
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

