package stack;

import kotlinx.atomicfu.AtomicRef;

import java.util.ArrayList;

public class StackImpl implements Stack {
    private static class Node {
        final AtomicRef<Node> next;
        final int x;

        Node(int x, Node next) {
            this.next = new AtomicRef<>(next);
            this.x = x;
        }
    }

    private static class HelperArray {
        private final ArrayList<AtomicRef<Integer>> helper;
        private final int length = 128;
        private final int shift = 8;
        private final int waitIter = 100;

        private HelperArray() {
            this.helper = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                this.helper.add(new AtomicRef<Integer>(null));
            }
        }

        private int getRandomPos() {
            return (int) System.currentTimeMillis() % length;
        }

        private boolean waitForKill(int pos, Integer x) {
            for (int i = 0; i < waitIter; i++) {
                if (helper.get(pos) == null) {
                    return true;
                }
            }
            return !helper.get(pos).compareAndSet(x, null);
        }

        public boolean push(int x) {
            Integer toPush = x;
            int pos = getRandomPos();
            int l = Math.max(0, pos - shift);
            int r = Math.min(length, pos + shift);
            for (int i = l; i < r; i++) {
                if (helper.get(i).compareAndSet(null, toPush)) {
                    return waitForKill(i, toPush);
                }
            }
            return false;
        }

        public Integer pop() {
            int pos = getRandomPos();
            int l = Math.max(0, pos - shift);
            int r = Math.min(length, pos + shift);
            for (int i = l; i < r; i++) {
                Integer val = helper.get(i).getValue();
                if (val != null && helper.get(i).compareAndSet(val, null)) {
                    return val;
                }
            }
            return null;
        }
    }

    // head pointer
    private final AtomicRef<Node> head = new AtomicRef<>(null);

    private final HelperArray helperArray = new HelperArray();

    @Override
    public void push(int x) {
        if (helperArray.push(x)) return;
        while (true) {
            Node currentHead = head.getValue();
            Node newHead = new Node(x, currentHead);
            if (head.compareAndSet(currentHead, newHead)) {
                return;
            }
        }
    }

    @Override
    public int pop() {
        Integer helperRes = helperArray.pop();
        if (helperRes != null) return helperRes;
        while (true) {
            Node currentHead = head.getValue();
            if (head.compareAndSet(currentHead, currentHead == null ? null : currentHead.next.getValue())) {
                return currentHead == null ? Integer.MIN_VALUE : currentHead.x;
            }
        }
    }
}
