---
name: android-release-sign-install
description: 在 Windows（Git Bash）上构建 Vectras VM（vectras-vm-zulu-main）release APK 并归档。涉及 gradlew 构建、JDK 17、Android SDK、嵌入式资源（WePE ISO、bootstrap tar）、中文路径陷阱、adb 安装验证时使用。不用于生成新 keystore 或修改签名配置。
---

# Vectras VM Release 构建 + 归档 + 真机验证（Windows / Git Bash）

本项目（vectras-vm-zulu-main，AGP 8.1.2 + Gradle 8.4）在 Windows 上构建有一条已验证的固定链路。直接照做，不要重新摸索环境。

## 本机固定环境

- OS：Windows，shell 用 Git Bash（`bash build-with-timestamp.sh`），脚本内全部是 Unix 语法。
- Android SDK：`C:\Users\Lenovo\AppData\Local\Android\Sdk`（已写入项目根 `local.properties` 的 `sdk.dir`，缺失的 Platform/Build-Tools 由 Gradle 首次构建时自动安装，需联网）。
- JDK：必须用 **JDK 17**，本机已装在 `C:\Users\Lenovo\.jdks\jdk-17.0.20.1+1`（Temurin）。
  - 系统默认 JDK 是 Zulu 25，Gradle 8.4 不支持（报 `Unsupported class file major version 69`）。构建前必须 `export JAVA_HOME="$HOME/.jdks/jdk-17.0.20.1+1"; export PATH="$JAVA_HOME/bin:$PATH"`。
- Gradle wrapper：distributionUrl 已改为 `gradle-8.4-bin.zip`（本机 `~/.gradle/wrapper/dists` 有完整缓存）。**不要改回 `-all`**——本机访问 services.gradle.org 会超时，且 `-all` 只有一个残缺的 .part 文件。
- 签名：release 由 Gradle 内联签名（`app/build.gradle` 的 signingConfigs 用项目根 `vectras.jks`，别名 `vectras`），产物 `app-release.apk` 直接是签名包，**不需要 zipalign/apksigner 后签**。证书 DN: `CN=Noureldeen Elsayed, OU=VectrasVM, O=vectras-team`。
- 归档：`build-with-timestamp.sh`（项目根）= `gradlew clean` + `assembleRelease` + 复制 APK 到项目根并加时间戳后缀（`VectrasVM-release-YYYYMMDD_HHMMSS.apk`）。用法：`bash build-with-timestamp.sh [debug|release]`。

## 关键陷阱（已踩过，按此预防）

1. **中文路径必炸**：项目若位于含中文/非 ASCII 字符的路径（如 `D:\JustStupid\vectras安卓运行虚拟机\...`），`:app:compileReleaseAidl` 报 `java.nio.charset.MalformedInputException: Input length = 1`。加 `-Dfile.encoding=UTF-8` 无效。
   - 解法：把项目同步到纯英文路径再构建，例如：
     ```bash
     mkdir -p /d/vectras-build
     tar --exclude=.git --exclude=.gradle --exclude='*/build' --exclude=.idea -cf - . | tar -xf - -C /d/vectras-build
     cd /d/vectras-build && bash build-with-timestamp.sh
     ```
   - 交付后把 APK 复制回原目录。日常建议在英文路径副本上开发/构建。
2. **Gradle daemon 复用错 JDK**：换 JAVA_HOME 后先 `./gradlew --stop`，否则 daemon 可能仍跑在 JDK 25 上。
3. **资源变更后必须重启构建**：assets 在构建中途改动不会被增量任务可靠拾取，改完 `app/src/main/assets/**` 后停掉当前构建重跑。

## 嵌入式资源（打进 APK 的，构建前确认在位）

- `app/src/main/assets/roms/WePE_64_V2.3.iso`（约 218M）：首次启动由 SplashActivity 注册为离线 ROM。源文件在 `D:\JustStupid\vectras安卓运行虚拟机\WePE_64_V2.3.iso`。
- `app/src/main/assets/bootstrap/arm64-v8a.tar`（解压后约 595M）：setup 时解包 QEMU 工具链，替代原 3.9M 占位 tar。由 `vectras-vm-arm64-v8a.tar.gz` 解压而来：`gunzip -c xxx.tar.gz > app/src/main/assets/bootstrap/arm64-v8a.tar`。
- APK 因此约 500M+，属正常。

## 执行步骤

### 1. 构建

```bash
cd /d/vectras-build   # 或当前使用的英文路径副本
export JAVA_HOME="$HOME/.jdks/jdk-17.0.20.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --stop 2>/dev/null
bash build-with-timestamp.sh release
```

必须看到 `BUILD SUCCESSFUL` 和 `Build completed successfully!`。

### 2. 验证签名（免后签，仅核验）

```bash
"$LOCALAPPDATA/Android/Sdk/build-tools/34.0.0/apksigner" verify --print-certs VectrasVM-release-*.apk
```

证书 DN 应为 `CN=Noureldeen Elsayed, OU=VectrasVM, O=vectras-team`。

### 3. adb 安装验证

```bash
adb devices -l
adb install -r "VectrasVM-release-<时间戳>.apk"
```

成功回执关键行 `Success`。覆盖安装若报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，说明设备上旧包签名不同：`adb uninstall com.vectras.vm` 后重装（清数据，先告知用户）。

### 4. 汇报

最终 APK 绝对路径、大小、adb 回执。提醒：正式分发的即是此签名包（vectras 团队证书），无 debug 后签环节。

## 不做的事

- 不改 `gradle-wrapper.properties` 回 `-all`；不改 signingConfigs；不生成新 keystore。
- 不在中文路径下直接构建。
- 不用系统默认 JDK 25 跑 Gradle。
- 不清理 `local.properties`（sdk.dir 是本机构建的前提）。
