package linked_list_set;

import kotlinx.atomicfu.AtomicRef;

public class SetImpl implements Set {
    private interface NodeState {

    }

    private static class Removed implements NodeState {
        final Node next;

        private Removed(Node next) {
            this.next = next;
        }
    }


    private static class Node implements NodeState {
        AtomicRef<NodeState> next;
        int x;

        Node(int x, Node next) {
            this.next = new AtomicRef<NodeState>(next);
            this.x = x;
        }
    }

    private class Window {
        Node cur, next;
    }

    private final Node head = new Node(Integer.MIN_VALUE, new Node(Integer.MAX_VALUE, null));

    /**
     * Returns the {@link Window}, where cur.x < x <= next.x
     */
    private Window findWindow(int x) {
        while (true) {
            Node cur = head;
            NodeState next = cur.next.getValue();
            boolean removed = false;
            while (next instanceof Node && ((Node) next).x < x) {
                NodeState node = ((Node) next).next.getValue();
                if (node instanceof Removed) {
                    Node nextNode = ((Removed) node).next;
                    if (!cur.next.compareAndSet(next, nextNode)) {
                        removed = true;
                        break;
                    }

                } else {
                    cur = (Node) next;
                    next = cur.next.getValue();
                }
            }
            if (removed) {
                continue;
            }
            if (next instanceof Node) {
                Window w = new Window();
                w.cur = cur;
                w.next = (Node) next;
                return w;
            }
        }
    }

    @Override
    public boolean add(int x) {
        while (true) {
            Window w = findWindow(x);

            if (w.next.next.getValue() instanceof Node && w.next.x == x) {
                return false;
            }
            Node node = new Node(x, w.next);
            if (w.cur.next.compareAndSet(w.next, node)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(int x) {
        while (true) {
            Window w = findWindow(x);
            if (w.next.next.getValue() instanceof Node && w.next.x != x) {
                return false;
            }
            NodeState state = w.next.next.getValue();
            if (state instanceof Node) {
                Removed node = new Removed((Node) state);
                if (w.next.next.compareAndSet(state, node)) {
                    w.cur.next.compareAndSet(w.next, state);
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean contains(int x) {
        Node node = findWindow(x).next;

        return node.next.getValue() instanceof Node && node.x == x;
    }
}