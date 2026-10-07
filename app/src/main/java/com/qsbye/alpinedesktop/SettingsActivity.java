package com.qsbye.alpinedesktop;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.io.File;
import java.util.regex.Pattern;

/**
 * 软件设置界面：设置启动屏幕方向（横/竖屏）与桌面分辨率（auto / WxH）。
 * 仅能从启动时的权限检查界面进入，保存即写入
 * Documents/VectrasVM/config/config.toml。
 */
public class SettingsActivity extends Activity {

    private static final Pattern RESOLUTION =
            Pattern.compile("\\s*(\\d+)x(\\d+)\\s*", Pattern.CASE_INSENSITIVE);

    private RadioGroup orientationGroup;
    private RadioGroup resolutionGroup;
    private EditText resolutionInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        orientationGroup = findViewById(R.id.rg_orientation);
        resolutionGroup = findViewById(R.id.rg_resolution);
        resolutionInput = findViewById(R.id.et_resolution);
        Button save = findViewById(R.id.btn_save);

        AppConfig cfg = AppConfig.load();
        orientationGroup.check(cfg.isLandscape()
                ? R.id.rb_landscape : R.id.rb_portrait);

        String explicit = cfg.explicitGeometry();
        if (explicit != null) {
            resolutionGroup.check(R.id.rb_custom);
            resolutionInput.setText(explicit);
            resolutionInput.setEnabled(true);
        } else {
            resolutionGroup.check(R.id.rb_auto);
        }

        resolutionGroup.setOnCheckedChangeListener((g, checkedId) ->
                resolutionInput.setEnabled(checkedId == R.id.rb_custom));

        save.setOnClickListener(v -> saveSettings());
        findViewById(R.id.btn_open_share).setOnClickListener(v -> openShareDir());
    }

    /**
     * 用系统文件管理器打开宿主机共享目录
     * Documents/VectrasVM/home/qsbye/share（与 guest /home/qsbye/share
     * 为避免任意应用（如 QPython）抢占
     * 隐式 Intent，按优先级显式选择真正的文件管理器：
     * 1) AOSP DocumentsUI：ExternalStorageProvider 文档 URI，精确定位子目录
     * 2) 其他文件管理器：resolveActivity 后按包名白名单过滤
     * 3) 华为文件管理器：仅打开内部存储根目录（无法定位子目录）
     */
    private void openShareDir() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请先在权限检查界面授予存储权限",
                    Toast.LENGTH_LONG).show();
            return;
        }
        File dir = new File(Environment.getExternalStorageDirectory(),
                "Documents/VectrasVM/home/qsbye/share");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Toast.makeText(this, "共享目录创建失败", Toast.LENGTH_LONG).show();
            return;
        }

        Uri providerUri = null;
        try {
            providerUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", dir);
        } catch (Exception ignored) {
        }

        // 1) AOSP DocumentsUI：直接定位 ExternalStorageProvider 中的目录
        //    （面包屑精确到 share，是唯一能稳定定位子目录的系统入口）
        Uri docUri = android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Documents/VectrasVM/home/qsbye/share");
        if (launchExplicit("com.android.documentsui",
                "com.android.documentsui.files.FilesActivity",
                docUri, "vnd.android.document/directory")) {
            return;
        }

        // 2) 其他已安装的知名文件管理器（隐式查询 + 白名单过滤）
        if (providerUri != null) {
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            if (launchKnownFileManager(providerUri, flags, "resource/folder")
                    || launchKnownFileManager(providerUri, flags,
                            "vnd.android.document/directory")) {
                return;
            }

            // 3) 华为文件管理器：无法定位子目录，仅打开内部存储根目录兜底
            //    （新包优先，旧 hidisk 兜底），总比只提示手动寻找好
            if (launchExplicit("com.huawei.filemanager",
                    "com.huawei.hidisk.view.activity.category.StorageActivity",
                    providerUri, "filemanager.dir")
                    || launchExplicit("com.huawei.hidisk",
                    "com.huawei.hidisk.view.activity.category.StorageActivity",
                    providerUri, "filemanager.dir")) {
                return;
            }
        }

        Toast.makeText(this,
                "未找到文件管理器，请手动进入 Documents/VectrasVM/home/qsbye/share",
                Toast.LENGTH_LONG).show();
    }

    private boolean launchExplicit(String pkg, String cls, Uri uri, String mime) {
        // 包不存在/被禁用时 getPackageInfo 直接抛异常
        try {
            getPackageManager().getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClassName(pkg, cls);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            // ActivityNotFoundException / SecurityException 均尝试下一方案
            return false;
        }
    }

    private boolean launchKnownFileManager(Uri uri, int flags, String mime) {
        Intent probe = new Intent(Intent.ACTION_VIEW);
        probe.setDataAndType(uri, mime);
        for (android.content.pm.ResolveInfo ri :
                getPackageManager().queryIntentActivities(probe, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (isKnownFileManager(pkg)) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setClassName(pkg, ri.activityInfo.name);
                intent.setDataAndType(uri, mime);
                intent.addFlags(flags);
                try {
                    startActivity(intent);
                    return true;
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    /** 各厂商系统文件管理器包名白名单；防止编辑器等无关应用抢占目录 Intent。 */
    private static boolean isKnownFileManager(String pkg) {
        if (pkg == null) {
            return false;
        }
        String[] exact = {
                "com.android.documentsui", "com.android.files",
                "com.google.android.files", "com.huawei.hidisk",
                "com.huawei.filemanager", "com.mi.android.globalFileexplorer",
                "com.android.fileexplorer", "com.sec.android.app.myfiles",
                "com.coloros.filemanager", "com.oplus.filemanager",
                "com.vivo.filemanager", "com.lbe.filemanager"
        };
        for (String p : exact) {
            if (p.equals(pkg)) {
                return true;
            }
        }
        return pkg.contains("filemanager") || pkg.contains("fileexplorer");
    }

    private void saveSettings() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "保存失败：请先在权限检查界面授予存储权限",
                    Toast.LENGTH_LONG).show();
            return;
        }

        AppConfig cfg = new AppConfig();
        cfg.orientation = orientationGroup.getCheckedRadioButtonId() == R.id.rb_portrait
                ? AppConfig.ORIENTATION_PORTRAIT
                : AppConfig.ORIENTATION_LANDSCAPE;

        if (resolutionGroup.getCheckedRadioButtonId() == R.id.rb_custom) {
            String text = resolutionInput.getText().toString();
            if (!RESOLUTION.matcher(text).matches()) {
                Toast.makeText(this, "分辨率格式不正确，请输入如 1280x720",
                        Toast.LENGTH_LONG).show();
                return;
            }
            cfg.resolution = text.trim().toLowerCase();
        } else {
            cfg.resolution = "auto";
        }

        if (!cfg.save()) {
            Toast.makeText(this, "保存失败：无法写入配置文件", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "已保存，下次进入 Alpine 时生效", Toast.LENGTH_SHORT).show();
        finish();
    }
}
