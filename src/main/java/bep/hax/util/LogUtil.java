package bep.hax.util;
import bep.hax.Bep;
public class LogUtil {
    public static void info(String msg) {
        Bep.LOG.info("{} {}", MsgUtil.getRawPrefix(), msg);
    }
    public static void info(String msg, String module) {
        Bep.LOG.info("{}{} {}", MsgUtil.getRawPrefix(), MsgUtil.getRawPrefix(module), msg);
    }
    public static void warn(String msg) {
        Bep.LOG.warn("{} {}", MsgUtil.getRawPrefix(), msg);
    }
    public static void warn(String msg, String module) {
        Bep.LOG.warn("{}{} {}", MsgUtil.getRawPrefix(), MsgUtil.getRawPrefix(module), msg);
    }
    public static void error(String msg) {
        Bep.LOG.error("{} {}", MsgUtil.getRawPrefix(), msg);
    }
    public static void error(String msg, String module) {
        Bep.LOG.error("{}{} {}", MsgUtil.getRawPrefix(), MsgUtil.getRawPrefix(module), msg);
    }
    public static void debug(String msg) {
        Bep.LOG.debug("{} {}", MsgUtil.getRawPrefix(), msg);
    }
    public static void debug(String msg, String module) {
        Bep.LOG.debug("{}{} {}", MsgUtil.getRawPrefix(), MsgUtil.getRawPrefix(module), msg);
    }
}