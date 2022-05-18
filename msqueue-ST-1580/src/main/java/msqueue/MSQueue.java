package msqueue;

import kotlinx.atomicfu.AtomicRef;

public class MSQueue implements Queue {
    private AtomicRef<Node> head;
    private AtomicRef<Node> tail;

    public MSQueue() {
        Node dummy = new Node(0);
        this.head = new AtomicRef<>(dummy);
        this.tail = new AtomicRef<>(dummy);
    }

    @Override
    public void enqueue(int x) {
        Node newTail = new Node(x);
        while (true) {
            Node currTail = tail.getValue();
            if (currTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(currTail, newTail);
                return;
            } else {
                tail.compareAndSet(currTail, tail.getValue().next.getValue());
            }
        }
    }

    @Override
    public int dequeue() {
        while (true) {
            Node currTail = tail.getValue();
            Node currHead = head.getValue();
            Node next = currHead.next.getValue();
            if (currHead == currTail) {
                if (next == null) {
                    return Integer.MIN_VALUE;
                }
                tail.compareAndSet(currTail, tail.getValue().next.getValue());
            } else if (next != null && head.compareAndSet(currHead, next)) {
                return next.x;
            }
        }
    }

    @Override
    public int peek() {
        Node currHead = head.getValue();
        Node next = currHead.next.getValue();
        if (next == null) return Integer.MIN_VALUE;
        return next.x;
    }

    private class Node {
        final int x;
        AtomicRef<Node> next = new AtomicRef<>(null);

        Node(int x) {
            this.x = x;
        }
    }
}