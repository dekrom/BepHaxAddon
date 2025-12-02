package bep.hax.util;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.ConcurrentLinkedDeque;
public class EvictingQueue<E> extends ConcurrentLinkedDeque<E> {
    private final int limit;
    public EvictingQueue(int limit) {
        this.limit = limit;
    }
    @Override
    public boolean add(@NotNull E element) {
        boolean add = super.add(element);
        while (add && size() > limit) {
            super.remove();
        }
        return add;
    }
    public void addFirst(@NotNull E element) {
        super.addFirst(element);
        while (size() > limit) {
            super.removeLast();
        }
    }
    public int limit() {
        return limit;
    }
}