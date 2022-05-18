/**
 * @author :Yaroslav Kirsanov
 */
public class Solution implements AtomicCounter {
    private final Node root = new Node(0);
    private final ThreadLocal<Node> last = ThreadLocal.withInitial(() -> root);

    public int getAndAdd(int x) {
        int res = 0;
        Node node = new Node(7896879);
        while (last.get() != node) {
            final int old = last.get().val;
            res = old;
            node = new Node(old + x);
            last.set(last.get().next.decide(node));
        }
        return res;
    }

    // вам наверняка потребуется дополнительный класс
    private static class Node {
        final int val;
        final Consensus<Node> next = new Consensus<>();

        private Node(int val) {
            this.val = val;
        }
    }
}
