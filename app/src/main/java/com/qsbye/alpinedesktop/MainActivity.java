package com.qsbye.alpinedesktop;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.io.File;
import java.util.List;

public class MainActivity extends Activity {

    private static final String DESKTOP_URL =
            "http://127.0.0.1:6080/vnc_lite.html?autoconnect=1&resize=remote"
            + "&reconnect=1&reconnect_delay=2000&show_dot=true";

    private static final int COLOR_GRANTED = Color.parseColor("#4CAF50");
    private static final int COLOR_DENIED = Color.parseColor("#EF5350");
    private static final int COLOR_NA = Color.parseColor("#90A4AE");

    private ProgressBar progressBar;
    private TextView statusView;
    private TextView logView;
    private ScrollView logScroll;
    private View splash;
    private WebView web;
    private Button retryButton;

    private View permissionScreen;
    private TextView storageStatus;
    private Button storageButton;
    private TextView notificationStatus;
    private Button notificationButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loadedDesktop;
    private long lastBackPress;

    /** 进入 Alpine 前等待旋转布局时使用的配置与单次测量标志 */
    private AppConfig pendingConfig;
    private boolean sizeCaptured;

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 600);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        splash = findViewById(R.id.splash);
        progressBar = findViewById(R.id.progress);
        statusView = findViewById(R.id.status);
        logView = findViewById(R.id.log);
        logScroll = findViewById(R.id.log_scroll);
        retryButton = findViewById(R.id.retry);
        web = findViewById(R.id.web);

        permissionScreen = findViewById(R.id.permission_screen);
        storageStatus = findViewById(R.id.perm_storage_status);
        storageButton = findViewById(R.id.perm_storage_btn);
        notificationStatus = findViewById(R.id.perm_notification_status);
        notificationButton = findViewById(R.id.perm_notification_btn);

        retryButton.setOnClickListener(v -> startDesktop());
        storageButton.setOnClickListener(v -> requestStorage());
        notificationButton.setOnClickListener(v -> requestNotification());
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_enter).setOnClickListener(v -> enterAlpine());

        setupWebView();
        // 每次启动停留权限检查界面，逐项确认后再由用户主动进入 Alpine
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从系统授权对话框/系统设置/软件设置返回都实时刷新，避免显示过期状态
        refreshPermissionStatuses();
    }

    // ==================== 权限检查界面 ====================

    private void refreshPermissionStatuses() {
        // 读、写权限分开判定：部分机型（含 EMUI）只授予 WRITE 不自动授予
        // READ，会导致能写不能读（设置保存后界面仍显示旧值）
        boolean storage = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        setRowState(storageStatus, storageButton, storage);

        // 不按 SDK 版本一刀切：部分 Android 12 OEM（如 EMUI）回填了
        // POST_NOTIFICATIONS 运行时权限，默认待审核；一律以系统实测状态为准
        if (notificationsEnabled()) {
            setRowState(notificationStatus, notificationButton, true);
        } else {
            notificationStatus.setText("未授权");
            notificationStatus.setTextColor(COLOR_DENIED);
            notificationButton.setEnabled(true);
            // 运行时权限机型弹授权框；否则只能跳系统通知设置页开启
            notificationButton.setText(isRuntimePermission(Permission.POST_NOTIFICATIONS)
                    ? "授予权限" : "前往开启");
        }
    }

    /** 应用当前是否允许发送通知（API24+；低版本恒为允许） */
    private boolean notificationsEnabled() {
        if (Build.VERSION.SDK_INT < 24) {
            return true;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    /**
     * 该权限在当前系统上是否为真正的运行时（危险）权限。
     * 依据：manifest 已声明该权限，且系统框架将其保护级别定义为 dangerous。
     * EMUI 回填的 POST_NOTIFICATIONS 在 Android 12 上即满足此条件。
     */
    private boolean isRuntimePermission(String permission) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(
                    getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] names = pi.requestedPermissions;
            if (names == null) {
                return false;
            }
            for (int i = 0; i < names.length; i++) {
                if (permission.equals(names[i])) {
                    PermissionInfo info = getPackageManager()
                            .getPermissionInfo(permission, 0);
                    return (info.protectionLevel
                            & PermissionInfo.PROTECTION_MASK_BASE)
                            == PermissionInfo.PROTECTION_DANGEROUS;
                }
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    private void setRowState(TextView status, Button button, boolean granted) {
        if (granted) {
            status.setText("已授权");
            status.setTextColor(COLOR_GRANTED);
            button.setEnabled(false);
            button.setText("已授权");
        } else {
            status.setText("未授权");
            status.setTextColor(COLOR_DENIED);
            button.setEnabled(true);
            button.setText("授予权限");
        }
    }

    /** 存储权限：读写权限同属一个权限组，一并申请 */
    private void requestStorage() {
        XXPermissions.with(this)
                .permission(Permission.WRITE_EXTERNAL_STORAGE, Permission.READ_EXTERNAL_STORAGE)
                .request(new SimplePermissionCallback());
    }

    private static final int REQ_NOTIFICATION = 100;

    /**
     * 通知权限：系统框架支持运行时申请时（Android 13+，以及回填该权限的
     * EMUI 等 ROM）直接弹授权框——在权限界面提前完成，避免启动服务
     * startForeground 时才被动弹窗；否则跳转系统通知设置页由用户手动开启。
     */
    private void requestNotification() {
        if (isRuntimePermission(Permission.POST_NOTIFICATIONS)
                && checkSelfPermission(Permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATION);
        } else {
            openNotificationSettings();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATION) {
            refreshPermissionStatuses();
            // 用户已勾选不再询问仍未授予时，引导到系统设置页
            if (!notificationsEnabled()
                    && !shouldShowRequestPermissionRationale(Permission.POST_NOTIFICATIONS)
                    && isRuntimePermission(Permission.POST_NOTIFICATIONS)) {
                openNotificationSettings();
            }
        }
    }

    /** 打开本应用的系统通知设置页（各版本写法不同，逐级兜底） */
    private void openNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= 26) {
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
        } else {
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("app_package", getPackageName());
            intent.putExtra("app_uid", getApplicationInfo().uid);
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            fallback.setData(android.net.Uri.fromParts("package",
                    getPackageName(), null));
            startActivity(fallback);
        }
    }

    private class SimplePermissionCallback implements OnPermissionCallback {
        @Override
        public void onGranted(List<String> permissions, boolean allGranted) {
            refreshPermissionStatuses();
        }

        @Override
        public void onDenied(List<String> permissions, boolean doNotAskAgain) {
            refreshPermissionStatuses();
            if (doNotAskAgain) {
                Toast.makeText(MainActivity.this,
                        "已被系统拒绝，请在系统设置中手动授予权限",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /** 点击“进入 Alpine”：存在未授权项时二次确认（功能降级），全部已授权直接进入 */
    private void enterAlpine() {
        boolean storageMissing = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED;
        boolean notificationMissing = !notificationsEnabled();

        if (storageMissing || notificationMissing) {
            new AlertDialog.Builder(this)
                    .setTitle("存在未授予的权限")
                    .setMessage("未授予的权限对应功能（配置文件、共享目录或前台通知）将不可用，"
                            + "仍要继续进入 Alpine 系统吗？")
                    .setNegativeButton("返回授权", null)
                    .setPositiveButton("继续进入", (d, w) -> proceedToAlpine())
                    .show();
            return;
        }
        proceedToAlpine();
    }

    /** 离开权限检查界面，进入 Alpine 初始化流程 */
    private void proceedToAlpine() {
        // 此时存储权限可能已授予：确保默认 config.toml 存在（已被设置页保存过则保留）
        File cfgFile = AppConfig.ensureDefault(this);
        AppConfig cfg = AppConfig.load();
        Status.log(cfgFile != null
                ? "config: " + cfgFile.getAbsolutePath()
                : "config unavailable, using defaults");
        applyOrientation(cfg);
        pendingConfig = cfg;
        sizeCaptured = false;
        permissionScreen.setVisibility(View.GONE);
        // 先让启动屏占住目标窗口，旋转完成后实测内容区，再启动服务
        splash.setVisibility(View.VISIBLE);
        measureWindowThenStart();
    }

    /**
     * 旋转动画期间窗口会经过若干瞬态布局（边栏/挖孔内边距分步生效）：
     * 监听全局布局，只接受“连续两次回调尺寸一致”且朝向正确（横屏 w≥h /
     * 竖屏 h≥w）的稳定布局；实测像素 ÷ density 得 DIP，写入 Status 供
     * LinuxService 作为 auto 分辨率。2.5 秒兜底，避免任何机型不回调时
     * 卡死启动。
     */
    private void measureWindowThenStart() {
        final View content = findViewById(android.R.id.content);
        final int[] last = {0, 0};
        final Runnable[] verify = new Runnable[1];
        final ViewTreeObserver.OnGlobalLayoutListener listener =
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        final int w = content.getWidth();
                        final int h = content.getHeight();
                        boolean oriented = pendingConfig.isLandscape()
                                ? w >= h : h >= w;
                        if (sizeCaptured || w <= 0 || h <= 0 || !oriented) {
                            return;
                        }
                        // 延迟 250ms 复核：尺寸不变才算稳定，过滤瞬态布局
                        handler.removeCallbacks(verify[0]);
                        verify[0] = () -> {
                            if (!sizeCaptured
                                    && content.getWidth() == w
                                    && content.getHeight() == h) {
                                content.getViewTreeObserver()
                                        .removeOnGlobalLayoutListener(this);
                                captureSize(w, h);
                            }
                        };
                        handler.postDelayed(verify[0], 250);
                        last[0] = w;
                        last[1] = h;
                    }
                };
        content.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        content.requestLayout();

        handler.postDelayed(() -> {
            if (!sizeCaptured) {
                int w = content.getWidth();
                int h = content.getHeight();
                if (w > 0 && h > 0) {
                    captureSize(w, h);
                } else if (last[0] > 0) {
                    captureSize(last[0], last[1]);
                } else {
                    sizeCaptured = true;
                    Status.log("window measure timeout, service will use config dp");
                    startDesktop();
                }
            }
        }, 2500);
    }

    /**
     * 物理像素 ÷ density = DIP（与 vnc_lite 页面 viewport 的 CSS 像素一致）。
     * 高度再减去 noVNC 顶部控制栏约 26 DIP：页面 #screen 只占剩余高度，
     * 这样即使 resize=remote 未被服务器执行，桌面底部也不会被控制栏遮住。
     */
    private void captureSize(int widthPx, int heightPx) {
        sizeCaptured = true;
        float density = Math.max(getResources().getDisplayMetrics().density, 1f);
        int w = Math.round(widthPx / density);
        int h = Math.max(200, Math.round(heightPx / density) - 26);
        Status.desiredWidthDip = w;
        Status.desiredHeightDip = h;
        Status.log("auto resolution: " + w + "x" + h + " dip (density " + density + ")");
        startDesktop();
    }

    /** 按配置强制横屏（默认）/竖屏，Xvnc 分辨率由 LinuxService 按同一配置计算 */
    private void applyOrientation(AppConfig cfg) {
        setRequestedOrientation(cfg.isLandscape()
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    // ==================== Alpine 启动/运行 ====================

    private void startDesktop() {
        loadedDesktop = false;
        web.setVisibility(View.GONE);
        splash.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        Intent svc = new Intent(this, LinuxService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        handler.removeCallbacks(poller);
        handler.post(poller);
    }

    private void render() {
        switch (Status.stage) {
            case EXTRACTING:
                progressBar.setIndeterminate(false);
                if (Status.progress >= 0) {
                    progressBar.setProgress(Status.progress);
                }
                statusView.setText(Status.message);
                break;
            case STARTING:
                progressBar.setIndeterminate(true);
                statusView.setText(Status.message);
                break;
            case RUNNING:
                if (!loadedDesktop) {
                    loadedDesktop = true;
                    enterDesktop();
                }
                return;
            case ERROR:
                progressBar.setIndeterminate(false);
                statusView.setText("启动失败：" + Status.message);
                retryButton.setVisibility(View.VISIBLE);
                break;
            case STOPPED:
                statusView.setText("桌面已停止，点击重试");
                retryButton.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
        logView.setText(Status.logTail(40));
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void enterDesktop() {
        splash.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        if (web.getUrl() == null) {
            web.loadUrl(DESKTOP_URL);
        }
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        web.setBackgroundColor(Color.BLACK);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this,
                            "桌面连接中断，正在重连…", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (permissionScreen.getVisibility() == View.VISIBLE) {
            // 权限检查界面：返回即退出应用，下次启动重新检查
            super.onBackPressed();
            return;
        }
        if (web.getVisibility() == View.VISIBLE) {
            long now = System.currentTimeMillis();
            if (now - lastBackPress < 2000) {
                moveTaskToBack(true);
            } else {
                lastBackPress = now;
                Toast.makeText(this, "再按一次返回键退出桌面", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(poller);
        if (isFinishing()) {
            web.destroy();
            stopService(new Intent(this, LinuxService.class));
        }
        super.onDestroy();
    }
}
