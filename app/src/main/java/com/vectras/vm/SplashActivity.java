package com.vectras.vm;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION.SDK_INT;

import android.app.ProgressDialog;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.vectras.qemu.MainSettingsManager;
import com.vectras.vm.utils.FileUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Locale;

public class SplashActivity extends AppCompatActivity implements Runnable {
    public static SplashActivity activity;
    private final String TAG = "SplashActivity";

    private static final int REQ_RUNTIME_PERMS = 101;
    private static final int REQ_ALL_FILES = 102;

    private View permissionPanel;
    private View preparingPanel;
    private TextView permStorageStatus;
    private TextView permNotifStatus;
    private MaterialButton btnGrant;

    private boolean proceeded = false;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        activity = this;
        setContentView(R.layout.activity_splash);

        permissionPanel = findViewById(R.id.permissionPanel);
        preparingPanel = findViewById(R.id.preparingPanel);
        permStorageStatus = findViewById(R.id.permStorageStatus);
        permNotifStatus = findViewById(R.id.permNotifStatus);
        btnGrant = findViewById(R.id.btnGrant);
        btnGrant.setOnClickListener(v -> requestMissingPermissions());

        VectrasApp.prepareDataForAppConfig(activity);
        setupFolders();
        MainSettingsManager.setOrientationSetting(activity, 1);
        updateLocale();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionUi();
        tryProceed();
    }

    private void updateLocale() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String languageCode = sharedPreferences.getString("language", "zh");

        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    // ------------------------------------------------------------------
    // Permission gate (single chokepoint: tryProceed)
    // ------------------------------------------------------------------

    private boolean hasStoragePermission() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean hasNotificationPermission() {
        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void refreshPermissionUi() {
        boolean storage = hasStoragePermission();
        boolean notif = hasNotificationPermission();

        permStorageStatus.setText(storage
                ? "● 所有文件访问权限（已授权）\nManage all files: granted"
                : "○ 所有文件访问权限（未授权）\nManage all files: required");
        permStorageStatus.setTextColor(storage ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));

        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permNotifStatus.setVisibility(View.VISIBLE);
            permNotifStatus.setText(notif
                    ? "● 通知权限（已授权）\nNotifications: granted"
                    : "○ 通知权限（未授权）\nNotifications: required");
            permNotifStatus.setTextColor(notif ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
        } else {
            // Notification permission is implicitly granted before Android 13.
            permNotifStatus.setText("● 通知权限（系统默认允许）\nNotifications: granted by system");
            permNotifStatus.setTextColor(Color.parseColor("#2E7D32"));
        }

        permissionPanel.setVisibility((storage && notif) ? View.GONE : View.VISIBLE);
    }

    private void requestMissingPermissions() {
        ArrayList<String> runtimePerms = new ArrayList<>();

        if (SDK_INT < Build.VERSION_CODES.R
                && ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            runtimePerms.add(READ_EXTERNAL_STORAGE);
            runtimePerms.add(WRITE_EXTERNAL_STORAGE);
        }

        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            runtimePerms.add(POST_NOTIFICATIONS);
        }

        if (!runtimePerms.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    runtimePerms.toArray(new String[0]), REQ_RUNTIME_PERMS);
        } else {
            openAllFilesSettingsIfNeeded();
        }
    }

    private void openAllFilesSettingsIfNeeded() {
        if (SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_ALL_FILES);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, REQ_ALL_FILES);
                } catch (Exception ex) {
                    Toast.makeText(this, "请在系统设置中授予所有文件访问权限", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            tryProceed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshPermissionUi();
        if (requestCode == REQ_RUNTIME_PERMS) {
            boolean allGranted = grantResults.length > 0;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "请授予所需权限以继续", Toast.LENGTH_SHORT).show();
            }
            openAllFilesSettingsIfNeeded();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ALL_FILES) {
            refreshPermissionUi();
            tryProceed();
        }
    }

    /**
     * Single chokepoint: both the settings callback path and the synchronous
     * "already granted" path converge here.
     */
    private void tryProceed() {
        if (proceeded) {
            return;
        }
        if (!hasStoragePermission() || !hasNotificationPermission()) {
            return;
        }

        proceeded = true;
        permissionPanel.setVisibility(View.GONE);
        preparingPanel.setVisibility(View.VISIBLE);

        // External storage writes (config files, bundled ROM/ISO registration)
        // happen only after all required permissions have been granted.
        new Thread(() -> {
            try {
                setupFiles();
            } catch (Exception e) {
                Log.e(TAG, "setupFiles failed", e);
            }
            runOnUiThread(this::routeToNextScreen);
        }).start();
    }

    private void routeToNextScreen() {
        String filesDir = activity.getFilesDir().getAbsolutePath();
        if ((new File(filesDir, "/distro/usr/local/bin/qemu-system-x86_64").exists())
                || (new File(filesDir, "/distro/usr/bin/qemu-system-x86_64").exists())) {
            startActivity(new Intent(this, MainActivity.class));
        } else {
            startActivity(new Intent(this, SetupQemuActivity.class));
            if (Build.VERSION.SDK_INT >= 34) {
                MainSettingsManager.setVmUi(this, "VNC");
            }
        }
        finish();
    }

    public void setupFiles() {
        String filesDir = activity.getFilesDir().getAbsolutePath();
        String nativeLibDir = activity.getApplicationInfo().nativeLibraryDir;

        File tmpDir = new File(activity.getFilesDir(), "usr/tmp");
        if (!tmpDir.isDirectory()) {
            tmpDir.mkdirs();
            FileUtils.chmod(tmpDir, 0771);
        }

        File vDir = new File(com.vectras.vm.AppConfig.maindirpath);
        if (!vDir.exists()) {
            vDir.mkdirs();
        }

        File distroDir = new File(filesDir + "/distro");
        if (!distroDir.exists()) {
            distroDir.mkdirs();
        }

        File cvbiDir = new File(FileUtils.getExternalFilesDirectory(activity).getPath() + "/cvbi");
        if (!cvbiDir.exists()) {
            cvbiDir.mkdirs();
        }

        File sharedDir = new File(AppConfig.sharedFolder);
        if (!sharedDir.exists()) {
            sharedDir.mkdirs();
        }

        File downloadsDir = new File(AppConfig.downloadsFolder);
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }

        File jsonFile = new File(AppConfig.maindirpath
                + "roms-data.json");
        if (!jsonFile.exists())
            try {

                if (!jsonFile.exists()) {
                    jsonFile.createNewFile();
                }

                FileWriter writer = new FileWriter(jsonFile);
                writer.write("[]");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        com.vectras.qemu.utils.FileInstaller.installFiles(activity, true);

        // Register embedded WePE ISO as a ROM entry for offline use
        registerEmbeddedWePERom();
    }

    /**
     * Copies WePE ISO from assets to vmFolder and registers it in roms-data.json
     * so it appears in the ROM list for offline use.
     */
    private void registerEmbeddedWePERom() {
        try {
            // Check if WePE ISO exists in assets
            String[] romAssets = getAssets().list("roms");
            boolean hasWePE = false;
            if (romAssets != null) {
                for (String f : romAssets) {
                    if (f.startsWith("WePE")) {
                        hasWePE = true;
                        break;
                    }
                }
            }
            if (!hasWePE) return;

            // Check if already registered
            String jsonPath = AppConfig.maindirpath + "roms-data.json";
            String existing = VectrasApp.readFile(jsonPath);
            if (existing != null && existing.contains("WePE")) return;

            // Copy ISO from assets to vmFolder
            String isoName = "WePE_64_V2.3.iso";
            String destIso = AppConfig.vmFolder + isoName;
            File destFile = new File(destIso);
            if (!destFile.exists()) {
                if (destFile.getParentFile() != null && !destFile.getParentFile().exists()) {
                    destFile.getParentFile().mkdirs();
                }
                InputStream is = getAssets().open("roms/" + isoName);
                OutputStream os = new FileOutputStream(destFile);
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
                os.close();
                is.close();
            }

            // Read current roms-data.json and add WePE entry
            String jsonContent = VectrasApp.readFile(jsonPath);
            if (jsonContent == null || jsonContent.isEmpty()) jsonContent = "[]";

            // Parse and append new entry
            String newEntry = String.format(
                "{\"imgName\":\"WePE 64 V2.3\",\"imgIcon\":\"\",\"imgArch\":\"X86_64\",\"imgPath\":\"\",\"imgCdrom\":\"%s\",\"imgDrv1\":\"\",\"imgExtra\":\"-M pc -accel tcg,thread=multi -cpu qemu64 -smp 4 -m 4096 -vga std -net nic,model=e1000 -net user -usb -device usb-tablet\",\"vmID\":\"wepe_official\"}",
                destIso.replace("\\", "\\\\")
            );

            // Insert into JSON array
            StringBuilder sb = new StringBuilder(jsonContent.trim());
            if (sb.toString().equals("[]")) {
                sb = new StringBuilder("[" + newEntry + "]");
            } else {
                // Remove trailing ] and append
                int lastBracket = sb.lastIndexOf("]");
                if (lastBracket > 0) {
                    sb.deleteCharAt(lastBracket);
                    // Add comma if there are existing entries
                    if (!sb.toString().trim().endsWith("[")) {
                        sb.append(",");
                    }
                    sb.append(newEntry).append("]");
                }
            }

            FileWriter writer = new FileWriter(jsonPath);
            writer.write(sb.toString());
            writer.flush();
            writer.close();

        } catch (Exception e) {
            Log.e(TAG, "Failed to register WePE ROM", e);
        }
    }


    public void onStart() {
        super.onStart();
        if (MainSettingsManager.getModeNight(activity)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            VectrasApp.getApp().setTheme(R.style.AppTheme);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            VectrasApp.getApp().setTheme(R.style.AppTheme);
        }
    }

    public static String[] storage_permissions = {
            WRITE_EXTERNAL_STORAGE,
            READ_EXTERNAL_STORAGE
    };

    public String getPath(Uri uri) {
        return com.vectras.vm.utils.FileUtils.getPath(this, uri);
    }

    /**
     * CHECK WHETHER INTERNET CONNECTION IS AVAILABLE OR NOT
     */
    public boolean checkConnection(Context context) {
        final ConnectivityManager connMgr = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connMgr != null) {
            NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();

            if (activeNetworkInfo != null) { // connected to the internet
                // connected to wifi
                if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    // connected to wifi
                    return true;
                } else
                    return activeNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE;
            }
        }
        return false;
    }

    class DownloadFileAsync extends AsyncTask<String, String, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... aurl) {
            int count;

            try {
                URL url = new URL(aurl[0]);
                URLConnection conexion = url.openConnection();
                conexion.connect();

                int lenghtOfFile = conexion.getContentLength();
                Log.d(TAG, "Lenght of file: " + lenghtOfFile);
                String fileName = "roms-" + MainSettingsManager.getArch(activity) + ".json";
                InputStream input = new BufferedInputStream(url.openStream());
                OutputStream output = new FileOutputStream(getExternalFilesDir("data") + "/" + fileName);

                byte data[] = new byte[1024];

                long total = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    publishProgress("" + (int) ((total * 100) / lenghtOfFile));
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();
            } catch (Exception e) {
            }
            return null;

        }

        protected void onProgressUpdate(String... progress) {
            Log.d(TAG, progress[0]);
        }

        @Override
        protected void onPostExecute(String unused) {
            tryProceed();
        }
    }

    private void copyAssetFile(String assetFileName, String destinationDirectory) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AssetManager assetManager = getAssets();
                InputStream in = null;
                OutputStream out = null;
                try {
                    in = assetManager.open(assetFileName);
                    File outFile = new File(destinationDirectory);
                    out = new FileOutputStream(outFile);
                    copyFile(in, out);
                } catch (IOException e) {
                    Log.e("tag", "Failed to copy asset file: " + assetFileName, e);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            // NOOP
                        }
                    }
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e) {
                            // NOOP
                        }
                    }
                    activity.runOnUiThread(SplashActivity.this::tryProceed);
                }
            }
        }).start();
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public static void setupFolders() {
        try {
            StartVM.cache = activity.getCacheDir().getAbsolutePath();
        } catch (Exception ignored) {

        }
    }

    public static final String CREDENTIAL_SHARED_PREF = "settings_prefs";

    @Override
    public void run() {
        // Legacy entry point (Handler callbacks): converge on the permission gate.
        tryProceed();
    }
}
