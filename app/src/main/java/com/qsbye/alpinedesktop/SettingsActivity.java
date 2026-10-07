package com.qsbye.alpinedesktop;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

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
