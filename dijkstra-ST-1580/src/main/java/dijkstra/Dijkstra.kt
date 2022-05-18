package dijkstra

import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.Comparator
import kotlin.concurrent.thread
import kotlin.random.Random

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> Integer.compare(o1!!.distance, o2!!.distance) }

private class MultiQueue(private val workers: Int) {
    private class CustomQueue() {
        val lock: ReentrantLock = ReentrantLock()
        val queue = PriorityQueue(NODE_DISTANCE_COMPARATOR)
    }

    private val currElems = AtomicInteger(0)
    private val queuesList: Array<CustomQueue> = Array(workers) { CustomQueue() }

    fun isEmpty(): Boolean {
        return currElems.get() == 0
    }

    fun update(isInc: Boolean) {
        if (isInc) {
            currElems.incrementAndGet()
        } else {
            currElems.decrementAndGet()
        }
    }

    fun customPoll(): Node? {
        while (true) {
            val pos1 = Random.nextInt(0, workers)
            var pos2 = Random.nextInt(0, workers)
            while (pos1 == pos2) {
                pos2 = Random.nextInt(0, workers)
            }

            var ans: Node? = null
            var ok: Boolean = false
            if (queuesList[pos1].lock.tryLock()) {
                if (queuesList[pos2].lock.tryLock()) {
                    ok = true

                    val first = queuesList[pos1].queue.peek()
                    val second = queuesList[pos2].queue.peek()

                    ans = if ((first != null && second != null && first.distance < second.distance)) {
                        queuesList[pos1].queue.poll()
                    } else {
                        queuesList[pos2].queue.poll()
                    }
                    queuesList[pos2].lock.unlock()

                }
                queuesList[pos1].lock.unlock()
                if (ok) {
                    return ans
                }
            }
        }
    }

    fun customPush(node: Node) {
        while (true) {
            val pos = Random.nextInt(0, workers)
            if (queuesList[pos].lock.tryLock()) {
                queuesList[pos].queue.add(node)
                queuesList[pos].lock.unlock()
                break
            }
        }
    }
}

// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    // The distance to the start node is `0`
    start.distance = 0
    // Create a priority (by distance) queue and add the start node into it
    val q = MultiQueue(2 * workers)
    q.customPush(start)
    q.update(true)
    // Run worker threads and wait until the total work is done
    val onFinish = Phaser(workers + 1) // `arrive()` should be invoked at the end by each worker
    repeat(workers) {
        thread {
            while (true) {
                val cur: Node? = q.customPoll()
                if (cur == null) {
                    if (q.isEmpty()) {
                        break
                    }
                    continue
                }
                for (e in cur.outgoingEdges) {
                    while (true) {
                        val curDist = e.to.distance
                        val newDist = cur.distance + e.weight
                        if (curDist > newDist) {
                            if (e.to.casDistance(curDist, newDist)) {
                                q.customPush(e.to)
                                q.update(true)
                                break
                            }
                        } else {
                            break
                        }
                    }
                }
                q.update(false)
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}