package com.qsbye.alpinedesktop;

import java.util.ArrayDeque;

/** 服务与界面之间共享的运行状态（进程级单例） */
public final class Status {

    public enum Stage {
        IDLE,           // 未启动
        EXTRACTING,     // 首次启动：释放 rootfs
        STARTING,       // proot + VNC + XFCE 启动中
        RUNNING,        // noVNC 端口就绪
        STOPPED,        // 会话已结束
        ERROR           // 启动失败
    }

    public static volatile Stage stage = Stage.IDLE;
    /** 0~100，-1 表示不确定进度 */
    public static volatile int progress = -1;
    public static volatile String message = "";

    private static final ArrayDeque<String> LOG = new ArrayDeque<>();

    public static void log(String line) {
        synchronized (LOG) {
            LOG.addLast(line);
            while (LOG.size() > 200) {
                LOG.removeFirst();
            }
        }
    }

    public static String logTail(int n) {
        synchronized (LOG) {
            Object[] all = LOG.toArray();
            int from = Math.max(0, all.length - n);
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < all.length; i++) {
                sb.append(all[i]).append('\n');
            }
            return sb.toString();
        }
    }

    public static void reset() {
        synchronized (LOG) {
            LOG.clear();
        }
        progress = -1;
        message = "";
    }

    private Status() { }
}
