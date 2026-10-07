package com.qsbye.alpinedesktop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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

import java.util.List;

public class MainActivity extends Activity {

    private static final String DESKTOP_URL =
            "http://127.0.0.1:6080/vnc_lite.html?autoconnect=1&resize=remote"
            + "&reconnect=1&reconnect_delay=2000&show_dot=true";

    private ProgressBar progressBar;
    private TextView statusView;
    private TextView logView;
    private ScrollView logScroll;
    private View splash;
    private WebView web;
    private Button retryButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loadedDesktop;
    private long lastBackPress;

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

        retryButton.setOnClickListener(v -> startDesktop());
        setupWebView();
        requestStorageThenStart();
    }

    /** 先经 XXPermissions 申请存储权限（用于共享目录挂载），无论结果如何都继续启动 */
    private void requestStorageThenStart() {
        XXPermissions.with(this)
                .permission(Permission.WRITE_EXTERNAL_STORAGE, Permission.READ_EXTERNAL_STORAGE)
                .request(new OnPermissionCallback() {
                    @Override
                    public void onGranted(List<String> permissions, boolean allGranted) {
                        startDesktop();
                    }

                    @Override
                    public void onDenied(List<String> permissions, boolean doNotAskAgain) {
                        Toast.makeText(MainActivity.this,
                                "未授予存储权限，共享目录 /home/qsbye/share 不可用",
                                Toast.LENGTH_LONG).show();
                        startDesktop();
                    }
                });
    }

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
