package com.vectras.vm.utils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.AssetManager;

import androidx.appcompat.app.AlertDialog;

import com.vectras.vm.AppConfig;
import com.vectras.vm.R;
import com.vectras.vterm.Terminal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LibraryChecker {
    private Context context;

    // Offline Alpine apk repository bundled in assets, extracted into the
    // proot rootfs (files/distro/apks) and installed without any network access.
    private static final String ASSET_REPO_DIR = "apks/aarch64";
    private static final String INSTALL_CMD =
            "apk add --no-network --allow-untrusted /apks/aarch64/*.apk";

    public LibraryChecker(Context context) {
        this.context = context;
    }

    public void checkMissingLibraries(Activity activity) {
        queryInstalled(activity, installed -> {
            String[] requiredLibraries = AppConfig.neededPkgs.split(" ");

            StringBuilder missingLibraries = new StringBuilder();
            for (String lib : requiredLibraries) {
                String name = lib.trim();
                if (!name.isEmpty() && !installed.contains(name)) {
                    missingLibraries.append(name).append("\n");
                }
            }

            if (missingLibraries.length() == 0) {
                return;
            }
            autoInstallOffline(activity, missingLibraries.toString());
        });
    }

    /**
     * Silently installs the missing packages from the offline repository
     * bundled in assets. No user confirmation is required; only an error
     * dialog is shown if the automatic installation cannot complete.
     */
    private void autoInstallOffline(Activity activity, String missingLibraries) {
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setMessage("正在安装运行时组件…\nInstalling runtime components…");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            String copyError = ensureOfflineRepo();
            if (copyError != null) {
                String err = copyError;
                activity.runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showInstallErrorDialog(activity,
                            "无法释放离线安装包:\n" + err, missingLibraries);
                });
                return;
            }

            activity.runOnUiThread(() ->
                    new Terminal(context).executeShellCommand(INSTALL_CMD, activity, (output, errors) -> {
                        progressDialog.dismiss();
                        verifyInstallation(activity, missingLibraries);
                    }));
        }).start();
    }

    private void verifyInstallation(Activity activity, String previousMissing) {
        queryInstalled(activity, installed -> {
            StringBuilder stillMissing = new StringBuilder();
            for (String lib : AppConfig.neededPkgs.split(" ")) {
                String name = lib.trim();
                if (!name.isEmpty() && !installed.contains(name)) {
                    stillMissing.append(name).append("\n");
                }
            }
            if (stillMissing.length() > 0) {
                showInstallErrorDialog(activity,
                        "部分运行时组件安装失败:\n" + stillMissing, stillMissing.toString());
            }
        });
    }

    private void showInstallErrorDialog(Activity activity, String message, String missingForRetry) {
        new AlertDialog.Builder(activity, R.style.MainDialogTheme)
                .setTitle("Runtime Setup")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Retry", (dialog, which) ->
                        autoInstallOffline(activity, missingForRetry))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Copies the bundled apk repository from assets into the proot rootfs.
     * Files already present are skipped. Returns null on success or an error message.
     */
    private String ensureOfflineRepo() {
        try {
            File repoDir = new File(context.getFilesDir(), "distro/apks/aarch64");
            if (!repoDir.exists() && !repoDir.mkdirs()) {
                return "cannot create " + repoDir.getAbsolutePath();
            }

            AssetManager assets = context.getAssets();
            String[] names = assets.list(ASSET_REPO_DIR);
            if (names == null || names.length == 0) {
                return "offline repository not found in assets";
            }

            Set<String> assetNames = new HashSet<>(Arrays.asList(names));

            // remove stale files from older app versions
            File[] existing = repoDir.listFiles();
            if (existing != null) {
                for (File f : existing) {
                    if (f.isFile() && !assetNames.contains(f.getName())) {
                        f.delete();
                    }
                }
            }

            byte[] buffer = new byte[64 * 1024];
            for (String name : names) {
                if (!name.endsWith(".apk")) {
                    continue;
                }
                File outFile = new File(repoDir, name);
                if (outFile.exists() && outFile.length() > 0) {
                    continue;
                }
                try (InputStream in = assets.open(ASSET_REPO_DIR + "/" + name);
                     OutputStream out = new FileOutputStream(outFile)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    private interface InstalledSetCallback {
        void onResult(Set<String> installed);
    }

    private void queryInstalled(Activity activity, InstalledSetCallback callback) {
        new Terminal(context).executeShellCommand("apk info", activity, (output, errors) -> {
            Set<String> installedPackages = new HashSet<>();
            if (output != null) {
                for (String installedPackage : output.split("\n")) {
                    String name = installedPackage.trim();
                    if (!name.isEmpty()) {
                        installedPackages.add(name);
                    }
                }
            }
            callback.onResult(installedPackages);
        });
    }

    // Method to check if the package is installed
    public void isPackageInstalled(String packageName, Terminal.CommandCallback callback) {
        String command = "apk info";

        Terminal terminal = new Terminal(context);
        terminal.executeShellCommand(command, (Activity) context, (output, errors) -> {
            if (callback != null) {
                callback.onCommandCompleted(output, errors);
            }
        });
    }

    // Method to check if the package is installed
    public static void isPackageInstalled2(Activity activity, String packageName, Terminal.CommandCallback callback) {
        String command = "apk info";

        Terminal terminal = new Terminal(activity);
        terminal.executeShellCommand(command, activity, (output, errors) -> {
            if (callback != null) {
                callback.onCommandCompleted(output, errors);
            }
        });
    }

    public void checkAndInstallXFCE4(Activity activity) {
        // XFCE4 meta-package
        String xfce4Package = "xfce4";

        // Check if XFCE4 is installed
        isPackageInstalled(xfce4Package, (output, errors) -> {
            boolean isInstalled = false;

            // Check if the package exists in the installed packages output
            if (output != null) {
                Set<String> installedPackages = new HashSet<>();
                for (String installedPackage : output.split("\n")) {
                    installedPackages.add(installedPackage.trim());
                }

                isInstalled = installedPackages.contains(xfce4Package.trim());
            }

            // If not installed, show a dialog to install it
            if (!isInstalled) {
                showInstallDialog(activity, xfce4Package);
            } else {
                showAlreadyInstalledDialog(activity);
            }
        });
    }

    private void showInstallDialog(Activity activity, String packageName) {
        new AlertDialog.Builder(activity, R.style.MainDialogTheme)
                .setTitle("Install XFCE4")
                .setMessage("XFCE4 is not installed. Would you like to install it?")
                .setCancelable(false)
                .setPositiveButton("Install", (dialog, which) -> {
                    String installCommand = "apk add " + packageName;
                    new Terminal(context).executeShellCommand(installCommand, true, activity);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showAlreadyInstalledDialog(Activity activity) {
        new AlertDialog.Builder(activity, R.style.MainDialogTheme)
                .setTitle("XFCE4 Installed")
                .setMessage("XFCE4 is already installed on this system.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
