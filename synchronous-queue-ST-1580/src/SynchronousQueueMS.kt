import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SynchronousQueueMS<E> : SynchronousQueue<E> {
    object RETRY
    private enum class NodeTypes {
        SENDER, RECEIVER
    }

    private abstract class Node<E>(
        val type: NodeTypes,
        val next: AtomicRef<Node<E>?> = atomic(null)
    )
    private class Sender<E> (
        val elem: E,
        notAtomicCoroutine: Continuation<Unit>
        ) : Node<E>(NodeTypes.SENDER) {
            val coroutine: AtomicRef<Continuation<Unit>> = atomic(notAtomicCoroutine)
        }

    private class Receiver<E> (
        notAtomicCoroutine: Continuation<E>?
        ) : Node<E>(NodeTypes.RECEIVER) {
            val coroutine: AtomicRef<Continuation<E>?> = atomic(notAtomicCoroutine)
        }

    private val startNode: Node<E?> = Receiver(null)
    private val head: AtomicRef<Node<E?>> = atomic(startNode)
    private val tail: AtomicRef<Node<E?>> = atomic(startNode)

    @Suppress("UNCHECKED_CAST")
    override suspend fun send(element: E) {
        while (true) {
            val currHead = head.value
            val currTail = tail.value
            if (currHead == currTail || currTail.type == NodeTypes.SENDER) {
                val res = suspendCoroutine<Any> sc@ { cont ->
                    val newTail = Sender(element, cont)
                    if (!currTail.next.compareAndSet(null, newTail as Node<E?>)) {
                        cont.resume(RETRY)
                        return@sc
                    } else {
                        val possibleNextNode = currTail.next.value
                        if (possibleNextNode != null) {
                            tail.compareAndSet(currTail, possibleNextNode)
                        }
                    }
                }
                if (res != RETRY) {
                    return
                }
            } else {
                val currWorkingNode = currHead.next.value
                if (currTail != tail.value || currHead != head.value || currWorkingNode == null ) {
                    continue
                }
                if (currWorkingNode.type == NodeTypes.RECEIVER && head.compareAndSet(currHead, currWorkingNode)) {
                    val currCoroutine = (currWorkingNode as Receiver<E>).coroutine.value
                    currCoroutine?.resume(element)
                    return
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun receive(): E {
        while (true) {
            val currHead = head.value
            val currTail = tail.value
            if (currHead == currTail || currTail.type == NodeTypes.RECEIVER) {
                val res = suspendCoroutine<E?> sc@ { cont ->
                    val newTail = Receiver<E>(cont)
                    if (!currTail.next.compareAndSet(null, newTail as Node<E?>)) {
                        cont.resume(null)
                        return@sc
                    } else {
                        val possibleNextNode = currTail.next.value
                        if (possibleNextNode != null) {
                            tail.compareAndSet(currTail, possibleNextNode)
                        }
                    }
                }
                if (res != null) {
                    return res
                }
            } else {
                val currWorkingNode = currHead.next.value
                if (currTail != tail.value || currHead != head.value || currWorkingNode == null ) {
                    continue
                }
                if (currWorkingNode.type == NodeTypes.SENDER && head.compareAndSet(currHead, currWorkingNode)) {
                    val currCoroutine = (currWorkingNode as Sender<E>).coroutine.value
                    currCoroutine.resume(Unit)
                    return (currWorkingNode as Sender<E>).elem
                }
            }
        }
    }
}
