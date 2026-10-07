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

    /**
     * MainActivity 在旋转布局完成后实测的应用窗口内容区尺寸（DIP/CSS 像素），
     * LinuxService 用作 auto 分辨率；0 表示未提供（服务走 Configuration DP 兜底）。
     * 注意：reset() 不清这两个字段——它们在服务启动前由 UI 写入。
     */
    public static volatile int desiredWidthDip;
    public static volatile int desiredHeightDip;

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
