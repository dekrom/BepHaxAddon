package bep.hax.util;
public class CacheTimer {
    private long time = -1L;
    public boolean passed(long ms) {
        return System.currentTimeMillis() - time >= ms;
    }
    public void reset() {
        time = System.currentTimeMillis();
    }
    public long getElapsed() {
        return System.currentTimeMillis() - time;
    }
    public long getTime() {
        return time;
    }
    public void setTime(long time) {
        this.time = time;
    }
}