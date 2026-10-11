---
name: android-release-sign-install
description: 构建 Android Release APK 并用 debug 密钥后签、带时间戳归档到项目根目录、adb 覆盖安装验证。用户提到 release 打包、apksigner 签名、归档 APK、安装到真机时使用。不用于生成正式发布证书或平台签名。
---

# Android Release 构建 + Debug 后签 + 归档 + 真机验证

把"release 构建 → zipalign/apksigner 后签 → 带时间戳归档 → adb 覆盖安装验证"作为一条固定闭环执行。目标是交付一个**已对齐、已用 debug 密钥签名、文件名带时间戳、放在项目根目录、且已在真机验证可安装**的 APK。不要修改项目的 Gradle 签名配置，不改 applicationId，不生成新 keystore。

## 本机固定环境（工作区 macOS）

- Android SDK：`/Users/workspace/Library/Android/sdk`
- 签名/对齐工具：`~/Library/Android/sdk/build-tools/<最高版本>/apksigner`、`.../zipalign`（当前 35.0.0）
- debug 密钥：`~/.android/debug.keystore`，别名 `androiddebugkey`。store/key 口令是 AGP 对 debug keystore 的公开默认值，执行时由操作者在 shell 中以环境变量提供（见第 4 步），不要把口令字面量写进任何项目文件或技能文件。
- JDK：
  - JDK 8（Corretto）`/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home` —— 用于 Gradle 4.x / AGP 3.x 老工程（如 AudioLouder-master，Gradle 4.1 + AGP 3.0.1）
  - JDK 17（JBR）`/Users/workspace/Library/Java/JavaVirtualMachines/jbr-17.0.8.1/Contents/Home` —— 用于 AGP 8.x / Gradle 8.x 工程（如 AudioSuitZulu，Gradle 8.7 + AGP 8.5.1）
  - 判定依据：读 `gradle/wrapper/gradle-wrapper.properties` 和 `gradle/libs.versions.toml`（或根 build.gradle 的 AGP 版本）。JDK 选错是最常见失败原因，必须先选对再构建。
- adb：`/opt/homebrew/Homebrew/bin/adb`；真机华为 TAS-AN00 序列号 `XPL0219C06017003`。有多设备时 adb 命令一律加 `-s <serial>`；offline 的 emulator 忽略。

## 执行步骤

### 1. 冻结交付目标

一句话确认：debug 签名 release 包、允许覆盖安装、不做正式签名。后续动作只服务该目标。

### 2. 构建 Release

```bash
cd "<项目根目录>"
export JAVA_HOME=<按上面规则选定的 JDK 路径>
./gradlew :app:assembleRelease
```

必须看到 `BUILD SUCCESSFUL`。老工程若仓库用 `jcenter()` 且依赖拉取失败，可把两处 `jcenter()` 替换为 `mavenCentral()`（保留 `google()`），这是本工作区已验证的兼容改法；缺少 `local.properties` 时补 `sdk.dir=/Users/workspace/Library/Android/sdk`。

### 3. 定位唯一权威产物并核对时间戳

```bash
ls -lT app/build/outputs/apk/release/
```

- release 未配 signingConfig 时产物名是 `app-release-unsigned.apk`，这是唯一输入件；不要引用 intermediates 或其他目录的中间包。
- 核对文件时间是本次构建刚生成的（与 `date` 当前时间一致），防止拿到旧包。

### 4. zipalign 对齐 + apksigner 后签

先在当前 shell 会话中导出 debug keystore 的默认口令到环境变量（AGP 公开默认值，仅存于会话内存，不落盘、不写入文件），再执行：

```bash
export DEBUG_STOREPASS='<debug keystore 默认口令，执行时由操作者填入>'
export DEBUG_KEYPASS="$DEBUG_STOREPASS"
BT=~/Library/Android/sdk/build-tools/35.0.0
TS=$(date +%Y%m%d_%H%M%S)
ALIGNED="/tmp/<项目名>-aligned_${TS}.apk"
SIGNED="<项目名>-release-signed_${TS}.apk"
"$BT/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk "$ALIGNED"
"$BT/apksigner" sign \
  --ks ~/.android/debug.keystore \
  --ks-pass "pass:$DEBUG_STOREPASS" --key-pass "pass:$DEBUG_KEYPASS" \
  --ks-key-alias androiddebugkey --out "$SIGNED" "$ALIGNED"
```

也可不传口令参数，让 apksigner 交互式提示输入。签名结束后 `unset DEBUG_STOREPASS DEBUG_KEYPASS`。

### 5. 带时间戳归档到项目根目录

- 第 4 步的 `--out` 直接输出到**项目根目录**，命名固定为 `<项目名>-release-signed_YYYYMMDD_HHMMSS.apk`。
- 未签名包如也需归档，命名 `<项目名>-release-unsigned_<时间戳>.apk`，与签名包区分清楚。
- 每次构建生成新时间戳文件，不覆盖历史归档。

### 6. 验证签名

```bash
"$BT/apksigner" verify --verbose --print-certs "$SIGNED"
```

要求输出 `Verifies`，v2/v3 scheme 为 true（targetSdk 30+ 不要求 v1），证书 DN 含 `Android Debug`。验证通过后删除 `/tmp` 下的 aligned 中间文件。

### 7. adb 覆盖安装验证（闭环必须有回执）

```bash
adb devices -l
adb -s <serial> install -r "<SIGNED 的绝对路径>"
```

- 成功回执关键行：`Success`。
- 华为 ROM 打印 `Incremental installation not allowed` 异常后自动回退 `Performing Streamed Install` 并 Success，属正常，不是失败。
- 按失败码最短修复：
  - `signatures do not match` / `INSTALL_FAILED_UPDATE_INCOMPATIBLE`：同包名旧包签名不同 → `adb -s <serial> uninstall <包名>` 后重装（会清数据，先告知）。debug 包与 debug 后签的 release 包证书一致，可直接 `-r` 覆盖。
  - `INSTALL_FAILED_OLDER_SDK`：设备系统低于 minSdk，停止并报告，不要改 minSdk 硬闯。
- 安装后用 `adb -s <serial> shell dumpsys package <包名> | grep -E 'versionName|lastUpdateTime'` 确认 lastUpdateTime 为刚刚。
- 可选启动验证：`adb shell am start -n <包名>/.MainActivity`，再 `dumpsys activity activities | grep ResumedActivity` 确认在前台、logcat crash buffer 无崩溃。

### 8. 汇报三元信息

每轮交付只报：adb 安装入口（含设备序列号）、最终签名 APK 的**绝对路径**、安装回执关键行（Success/失败码）。附上构建任务结果和签名方案（v2/v3）。提醒：debug 签名包仅用于自测，不能上架。

## 不做的事

- 不往 app/build.gradle(.kts) 加 signingConfigs、不把 release 绑 debug 签名配置（后签链路不需要，且会扩大故障面）。
- 不创建正式 keystore（用户明确要求正式证书时另走流程）。
- 不把任何口令字面量写入技能、脚本或项目文件；口令只在执行时通过环境变量或交互输入进入内存。
- 不安装多份 APK 到不同目录造成"装了哪个包"的歧义；只认项目根目录带时间戳的 signed 文件为交付件。
