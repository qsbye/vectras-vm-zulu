package com.qsbye.alpinedesktop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 负责：首次释放 rootfs → 引导 proot 工具链（termux/proot 套件 + 外置 loader）
 * → 启动 XFCE（proot-distro 风格的命令行）→ 等待 noVNC 端口就绪。
 * 全程不需要联网。
 */
public class LinuxService extends Service {

    private static final String CHANNEL_ID = "alpine_desktop";
    private static final int NOTIF_ID = 1;
    private static final int VNC_WEB_PORT = 6080;

    /** busybox applet，仅用于宿主侧备用/调试 */
    private static final String[] BUSYBOX_APPLETS = {
            "sh", "tar", "cat", "cp", "rm", "mkdir", "ln", "grep", "sed",
            "awk", "head", "tail", "chmod", "stat", "realpath", "id", "uname"
    };

    private Process process;
    private PowerManager.WakeLock wakeLock;
    private File logFile;
    private volatile boolean shuttingDown;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIF_ID, buildNotification("Alpine XFCE 准备中…"));

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlpineDesktop:session");
        wakeLock.acquire(24 * 60 * 60 * 1000L);

        new Thread(this::boot, "alpine-boot").start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void boot() {
        Status.reset();
        try {
            File base = getFilesDir();
            File rootfs = new File(base, "rootfs");
            File tmpDir = new File(base, "tmp");
            File logDir = new File(base, "logs");
            mkdir(rootfs, tmpDir, logDir);
            tmpDir.setWritable(true, false);
            tmpDir.setReadable(true, false);
            tmpDir.setExecutable(true, false);
            logFile = new File(logDir, "desktop.log");

            File ready = new File(rootfs, ".ready");
            if (!ready.exists()) {
                Status.stage = Status.Stage.EXTRACTING;
                Status.message = "正在准备 Alpine 桌面环境（首次启动需要几分钟）…";
                Status.log("extracting rootfs.bin ...");
                TarExtractor.extract(getAssets(), "rootfs.bin", rootfs,
                        (read, total, name) -> {
                            int pct = total > 0 ? (int) (read * 100 / total) : -1;
                            Status.progress = pct;
                            Status.message = "正在释放系统文件 " + pct + "%";
                            if (read == 0 || read % 5_000_000 < 65536) {
                                Status.log("[" + pct + "%] " + name);
                            }
                        });
                prepareMountPoints(rootfs);
                Files.write(ready.toPath(), new byte[0]);
                Status.progress = -1;
            }

            Status.stage = Status.Stage.STARTING;
            Status.message = "正在启动 XFCE 桌面…";
            Status.log("starting proot ...");

            // termux/proot 套件以 jniLibs 形式随 APK 释放到 nativeLibraryDir，
            // 在 files/bin 下建立符号链接（loader 必须外置并通过 PROOT_LOADER 指定；
            // libtalloc 的 SONAME 是 libtalloc.so.2，与 APK 内文件名不一致，也需链接）。
            File binDir = new File(base, "bin");
            mkdir(binDir);
            String libDir = getApplicationInfo().nativeLibraryDir;
            setupToolBin(binDir, libDir);

            setupFakeSysData(rootfs);

            // 共享目录：宿主机 Documents/VectrasVM/home/qsbye/share ↔ guest /home/qsbye/share
            // 没有则自动创建；仅在存储权限已授予时挂载（权限由 MainActivity 经 XXPermissions 申请）。
            // 注意：proot --bind 是路径翻译，guest 进程以 app uid 直接访问 /sdcard，
            // sdcard FUSE 上 chown/chmod 会失败（EPERM），属预期行为。
            File shareDir = null;
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                File dir = new File(Environment.getExternalStorageDirectory(),
                        "Documents/VectrasVM/home/qsbye/share");
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    Status.log("share dir create failed: " + dir);
                } else {
                    shareDir = dir;
                    mkdir(new File(rootfs, "home/qsbye/share"));
                }
            } else {
                Status.log("storage permission not granted, share dir disabled");
            }

            File proot = new File(binDir, "proot");
            String geometry = screenGeometry();

            List<String> cmd = new ArrayList<>();
            cmd.add(proot.getAbsolutePath());
            // 参数对齐 proot-distro login（termux/proot 验证组合）
            cmd.add("--sysvipc");                       // System V IPC 仿真
            cmd.add("-L");                              // 修复 lstat（symlink 大小）
            cmd.add("--link2symlink");                  // 用 symlink 模拟 hardlink
            cmd.add("--root-id");                       // guest 内 uid/gid 恒为 0
            cmd.add("--kill-on-exit");
            cmd.add("--rootfs=" + rootfs.getAbsolutePath());
            cmd.add("--cwd=/root");
            cmd.add("--bind=/dev");
            cmd.add("--bind=/dev/urandom:/dev/random");
            cmd.add("--bind=/proc");
            cmd.add("--bind=/proc/self/fd:/dev/fd");
            // Android 上这些 /proc、/sys 节点不可读/不存在，用 rootfs 内假文件覆盖
            cmd.add("--bind=" + new File(rootfs, "proc/.loadavg") + ":/proc/loadavg");
            cmd.add("--bind=" + new File(rootfs, "proc/.stat") + ":/proc/stat");
            cmd.add("--bind=" + new File(rootfs, "proc/.uptime") + ":/proc/uptime");
            cmd.add("--bind=" + new File(rootfs, "proc/.version") + ":/proc/version");
            cmd.add("--bind=" + new File(rootfs, "sys/.empty") + ":/sys/fs/selinux");
            cmd.add("--bind=" + tmpDir.getAbsolutePath() + ":/tmp");
            if (shareDir != null) {
                cmd.add("--bind=" + shareDir.getAbsolutePath() + ":/home/qsbye/share");
                Status.log("share mounted: " + shareDir.getAbsolutePath());
            }
            // 干净环境启动，杜绝 Android 宿主机变量泄漏进 guest
            cmd.add("/usr/bin/env");
            cmd.add("-i");
            cmd.add("HOME=/home/qsbye");
            cmd.add("LANG=C.UTF-8");
            cmd.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
            cmd.add("TERM=xterm-256color");
            cmd.add("TMPDIR=/tmp");
            cmd.add("GEOMETRY=" + geometry);
            cmd.add("/usr/local/bin/desktop-start.sh");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.environment().clear();
            // ---- 宿主机侧（Android）环境 ----
            pb.environment().put("PATH", binDir.getAbsolutePath() + ":/system/bin");
            pb.environment().put("PROOT_LOADER", new File(binDir, "loader").getAbsolutePath());
            pb.environment().put("LD_LIBRARY_PATH", binDir.getAbsolutePath());
            pb.environment().put("TMPDIR", tmpDir.getAbsolutePath());
            pb.environment().put("PROOT_TMP_DIR", tmpDir.getAbsolutePath());
            // 注意：不要设置 PROOT_NO_SECCOMP。termux/proot 在纯 ptrace 模式下改写
            // getcwd/getgroups 返回值会异常（getcwd 返回 -1 导致 musl/python 报 ENOSYS）；
            // seccomp 加速路径（默认自动探测启用）才是其在 Android app 进程内的验证组合。

            process = pb.start();
            pumpLogs(process.getInputStream());

            if (waitForPort(VNC_WEB_PORT, 150)) {
                Status.stage = Status.Stage.RUNNING;
                Status.progress = 100;
                Status.message = "桌面已就绪";
                updateNotification("Alpine XFCE 运行中");
                int exit = process.waitFor();
                Status.log("proot exited with " + exit);
                Status.stage = shuttingDown ? Status.Stage.STOPPED : Status.Stage.ERROR;
                Status.message = "桌面会话已结束（code " + exit + "）";
            } else {
                int exit = process.isAlive() ? -99 : process.exitValue();
                Status.stage = Status.Stage.ERROR;
                Status.message = "桌面启动超时，请查看日志";
                Status.log("noVNC port not reachable, proot exit=" + exit);
            }
        } catch (Exception e) {
            Status.stage = Status.Stage.ERROR;
            Status.message = String.valueOf(e.getMessage());
            Status.log("ERROR: " + e);
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE);
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            stopSelf();
        }
    }

    /** 补出客户机运行时目录（/dev 整体绑定宿主机，只需保证目录存在） */
    private void prepareMountPoints(File rootfs) throws Exception {
        File dev = new File(rootfs, "dev");
        mkdir(dev);
        for (String d : new String[]{"pts", "shm", "fd"}) {
            mkdir(new File(dev, d));
        }
        File tmp = new File(rootfs, "tmp");
        mkdir(tmp);
        chmod(tmp, 0777, true);
    }

    /**
     * 在 files/bin 下建立指向 nativeLibraryDir 的符号链接。
     * jniLibs 释放出来的文件名带 lib 前缀/.so 后缀且直接执行受 targetSdk 限制，
     * 符号链接到 nativeLibraryDir（始终可执行）是 termux/proot-distro 生态的标准引导方式。
     */
    private void setupToolBin(File binDir, String libDir) throws Exception {
        linkTool(binDir, "proot", libDir + "/libproot.so");
        linkTool(binDir, "loader", libDir + "/libloader.so");
        linkTool(binDir, "libtalloc.so.2", libDir + "/liblibtalloc.so.2.so");
        linkTool(binDir, "busybox", libDir + "/libbusybox.so");
        linkTool(binDir, "bash", libDir + "/libbash.so");
        File busybox = new File(binDir, "busybox");
        for (String applet : BUSYBOX_APPLETS) {
            linkTool(binDir, applet, busybox.getAbsolutePath());
        }
    }

    private void linkTool(File binDir, String name, String target) throws Exception {
        File link = new File(binDir, name);
        // 无条件重建（断链符号链接 exists()=false 但仍占着路径）
        Files.deleteIfExists(link.toPath());
        Files.createSymbolicLink(link.toPath(), new File(target).toPath());
    }

    /**
     * 写入 proot 覆盖绑定用的假 /proc、/sys 数据。
     * Android 上 /proc/loadavg、/proc/stat 等对应用 uid 不可读，
     * 桌面组件（面板系统监控等）读取失败会报错；做法与 proot-distro 一致。
     */
    private void setupFakeSysData(File rootfs) throws Exception {
        File proc = new File(rootfs, "proc");
        File sys = new File(rootfs, "sys");
        File sysEmpty = new File(sys, ".empty");
        mkdir(proc, sys, sysEmpty);

        writeIfMissing(new File(proc, ".loadavg"), "0.12 0.07 0.02 2/165 765\n");
        writeIfMissing(new File(proc, ".uptime"), "124.08 932.80\n");
        writeIfMissing(new File(proc, ".version"),
                "Linux version 5.10.0 (proot@android) (aarch64-linux-gnu-gcc) #1 SMP\n");
        writeIfMissing(new File(proc, ".stat"),
                "cpu  1957 0 2877 93280 262 342 254 87 0 0\n"
              + "intr 127541 38 290 25329\nctxt 140223\nbtime 1680020856\n"
              + "processes 772\nprocs_running 2\nprocs_blocked 0\n");
    }

    private void writeIfMissing(File f, String content) throws Exception {
        if (!f.exists()) {
            Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String screenGeometry() {
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            Point p = new Point();
            wm.getDefaultDisplay().getRealSize(p);
            return p.x + "x" + p.y;
        } catch (Exception e) {
            return "1280x800";
        }
    }

    private boolean waitForPort(int port, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                return false;
            }
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", port), 800);
                return true;
            } catch (Exception ignore) {
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    return false;
                }
            }
        }
        return false;
    }

    private void pumpLogs(InputStream in) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
                 FileWriter fw = new FileWriter(logFile, true)) {
                String line;
                while ((line = br.readLine()) != null) {
                    Status.log(line);
                    fw.write(line);
                    fw.write('\n');
                    fw.flush();
                }
            } catch (Exception ignore) {
            }
        }, "proot-log");
        t.setDaemon(true);
        t.start();
    }

    private static void mkdir(File... dirs) {
        for (File d : dirs) {
            if (!d.exists() && !d.mkdirs()) {
                throw new RuntimeException("无法创建目录: " + d);
            }
        }
    }

    private static void chmod(File f, int mode, boolean sticky) throws Exception {
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString(
                sticky ? "rwxrwxrwx" : "rwxr-xr-x");
        Files.setPosixFilePermissions(f.toPath(), perms);
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Alpine XFCE", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Linux 桌面会话");
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Alpine XFCE")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        shuttingDown = true;
        if (process != null) {
            process.destroy();
            try {
                process.waitFor();
            } catch (InterruptedException ignore) {
            }
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }
}
