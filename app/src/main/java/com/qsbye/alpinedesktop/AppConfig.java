package com.qsbye.alpinedesktop;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 桌面配置：宿主机 {@code Documents/VectrasVM/config/config.toml}。
 * App 初始化且存储权限授予后，文件不存在则自动写出带注释的默认 TOML：
 * <pre>
 * orientation = "landscape"   # landscape 横屏（默认）/ portrait 竖屏
 * resolution  = "auto"        # auto 跟随屏幕物理分辨率（默认），或手动 "1280x720"
 * </pre>
 * 读取/解析任何环节失败都回退默认值（横屏 + auto），不阻断启动。
 */
public final class AppConfig {

    public static final String ORIENTATION_LANDSCAPE = "landscape";
    public static final String ORIENTATION_PORTRAIT = "portrait";

    private static final Pattern RESOLUTION_PATTERN =
            Pattern.compile("(\\d+)x(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_VALUE =
            Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*=\\s*(.*)$");

    public String orientation = ORIENTATION_LANDSCAPE;
    public String resolution = "auto";

    public static File configFile() {
        File dir = new File(Environment.getExternalStorageDirectory(),
                "Documents/VectrasVM/config");
        return new File(dir, "config.toml");
    }

    /** 竖屏仅在显式配置 portrait 时成立，其它一切取值（含拼写错误）按横屏处理 */
    public boolean isLandscape() {
        return !ORIENTATION_PORTRAIT.equalsIgnoreCase(orientation);
    }

    /** 手动指定的分辨率（WxH）；auto/空/非法时返回 null */
    public String explicitGeometry() {
        if (resolution == null || resolution.isEmpty()
                || "auto".equalsIgnoreCase(resolution)) {
            return null;
        }
        Matcher m = RESOLUTION_PATTERN.matcher(resolution.trim());
        return m.matches() ? m.group(1) + "x" + m.group(2) : null;
    }

    /**
     * 把当前字段写入 config.toml（覆盖）。需要存储权限；
     * 目录/文件创建或写入失败返回 false。
     */
    public boolean save() {
        File f = configFile();
        try {
            File dir = f.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return false;
            }
            Files.write(f.toPath(), toToml().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 序列化为与默认模板一致的带注释 TOML */
    private String toToml() {
        String res = explicitGeometry() != null ? explicitGeometry()
                : (resolution == null || resolution.isEmpty() ? "auto" : resolution);
        return "# AlpineDesktop 桌面配置\n"
             + "# 由软件设置界面生成，修改后完全退出应用再重新进入即生效。\n"
             + "\n"
             + "# 启动屏幕方向：\n"
             + "#   landscape = 横屏（默认）\n"
             + "#   portrait  = 竖屏\n"
             + "orientation = \"" + orientation + "\"\n"
             + "\n"
             + "# 桌面分辨率：\n"
             + "#   auto    = 自动适配屏幕物理分辨率（默认）\n"
             + "#   手动指定 = \"1280x720\" 形式的 宽x高\n"
             + "resolution = \"" + res + "\"\n";
    }

    /**
     * 配置文件不存在时写出默认 TOML。需要存储权限；
     * 无权限或创建失败返回 null（调用方按默认配置继续）。
     */
    public static File ensureDefault(Context ctx) {
        File f = configFile();
        if (f.exists()) {
            return f;
        }
        if (ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            File dir = f.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            Files.write(f.toPath(), DEFAULT_TOML.getBytes(StandardCharsets.UTF_8));
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取配置；文件不存在/不可读/解析失败均回退默认（横屏 + auto） */
    public static AppConfig load() {
        AppConfig cfg = new AppConfig();
        File f = configFile();
        if (!f.exists()) {
            return cfg;
        }
        try {
            for (String raw : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) {
                    continue;
                }
                Matcher m = KEY_VALUE.matcher(line);
                if (!m.matches()) {
                    continue;
                }
                String key = m.group(1);
                String val = stripValue(m.group(2));
                switch (key) {
                    case "orientation":
                        if (ORIENTATION_LANDSCAPE.equalsIgnoreCase(val)
                                || ORIENTATION_PORTRAIT.equalsIgnoreCase(val)) {
                            cfg.orientation = val.toLowerCase();
                        }
                        break;
                    case "resolution":
                        cfg.resolution = val;
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception ignore) {
            // 保持默认值
        }
        return cfg;
    }

    /**
     * 从等号右侧取值：双/单引号包裹则取引号内内容（忽略其后的行内注释）；
     * 裸值则截到第一个 '#' 之前。
     */
    private static String stripValue(String raw) {
        String v = raw.trim();
        if (v.startsWith("\"") || v.startsWith("'")) {
            int end = v.indexOf(v.charAt(0), 1);
            return end > 0 ? v.substring(1, end) : v;
        }
        int hash = v.indexOf('#');
        return hash >= 0 ? v.substring(0, hash).trim() : v;
    }

    private static final String DEFAULT_TOML =
            "# AlpineDesktop 桌面配置\n"
          + "# 修改后完全退出应用再重新进入即生效。\n"
          + "\n"
          + "# 启动屏幕方向：\n"
          + "#   landscape = 横屏（默认）\n"
          + "#   portrait  = 竖屏\n"
          + "orientation = \"landscape\"\n"
          + "\n"
          + "# 桌面分辨率：\n"
          + "#   auto    = 自动适配屏幕物理分辨率（默认）\n"
          + "#   手动指定 = \"1280x720\" 形式的 宽x高\n"
          + "resolution = \"auto\"\n";
}
