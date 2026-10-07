# AlpineDesktop（Alpine XFCE）

**中文** | [English](#english)

在 Android 设备上**完全离线**运行的 Alpine Linux XFCE 桌面。

应用把一个打包好的 Alpine rootfs（约 384 MB 的 `rootfs.bin`，实为 tar.gz）和
**Termux 生态的 proot 套件**（proot + 外置 loader + busybox + bash）一起塞进 APK：
首次启动时把 rootfs 释放到应用私有目录，在 `files/bin` 下建立指向
`nativeLibraryDir` 的符号链接引导 proot，再以 proot-distro 同款命令行启动
Xvnc + XFCE + websockify/noVNC，最后由内置 WebView 连接本机
`127.0.0.1:6080` 显示桌面。全程不需要 root、不需要联网（安装好 APK 之后）。

附加能力：

- **VNC/noVNC 对局域网开放**：其他设备浏览器直接打开
  `http://<手机IP>:6080/vnc_lite.html?autoconnect=1&resize=remote`
  即可看到同一个桌面；任何 VNC 客户端也可直连 `<手机IP>:5900`
  （无密码，仅限可信网络）
- **OpenSSH**：guest 内 sshd 监听 `0.0.0.0:8022`，`ssh qsbye@<手机IP> -p 8022`
  （密码 `qsbye`，Android 应用无权绑定 22 端口故用 8022）
- **共享目录**：宿主机 `Documents/VectrasVM/home/qsbye/share` ↔ guest
  `/home/qsbye/share` 双向互通（目录不存在自动创建；存储权限由
  XXPermissions 在首启时申请，拒绝仅禁用该功能）。
  - 任一侧放入或删除文件，另一侧**立即可见**；可在 Alpine 桌面文件
    管理器（Thunar）、终端（`cp`/`mv`）或 SSH 会话（端口 8022）中操作
  - `/sdcard` 的 FUSE 挂载带 `noexec` 且不支持 `chown`/`chmod`：要在
    guest 内运行的程序请先 `cp` 到 home 目录再执行
  - 其他设备可用 LocalSend 发到本机，再移入共享目录
  - 软件设置页内有“**使用文件管理器打开共享目录**”按钮（经
    DocumentsUI 直接定位到该目录）及完整使用说明
- **LocalSend CLI**：内置官方 LocalSend CLI 1.18.2（arm64 glibc 版 +
  musl 兼容垫片），与各平台官方 LocalSend App 互通，任意 guest 会话
  （含 ssh）里直接运行：

  ```sh
  localsend-cli -f 文件1 -f 文件2                     # 发送，按编号 1-9 选设备
  localsend-cli --destination /home/qsbye/share      # 接收并存进共享目录，Y/N/P 应答
  ```
- **OpenCode**：内置 [opencode](https://opencode.ai)（AI 终端编程助手，
  官方安装器自动选取 linux-arm64-musl 构建），任意 guest 会话里直接运行 `opencode`
- **启动权限检查**：每次启动首先进入权限检查界面，**逐项确认**存储权限
  （READ+WRITE 分开校验，避免“能写不能读”）与通知权限（以系统实测
  `areNotificationsEnabled()` 为准；Android 13+ 及回填该权限的 EMUI
  Android 12 等机型需授权），全部确认（或放弃部分权限）后才进入
  Alpine 初始化流程——通知授权前置，避免启动服务时系统被动弹窗
- **软件设置（仅启动时可进入）**：权限检查界面提供“软件设置”入口，可设置
  启动时**横屏（默认）/竖屏**与桌面**分辨率**（`auto` 自动适配屏幕物理
  分辨率，也可手动指定 `1280x720`），保存写入宿主机
  `Documents/VectrasVM/config/config.toml`；页内同时提供共享目录使用说明
  与“使用文件管理器打开共享目录”按钮，以及**局域网连接说明**（浏览器
  noVNC、VNC 客户端、SSH、LocalSend 收发的完整步骤）；桌面运行期间
  没有设置入口

> proot 引导方式复用自 [code_lfa](../../../code_lfa-main)（Flutter 封装
> proot-distro + Ubuntu + code-server 的项目），桌面侧由 code-server 替换为
> XFCE + TigerVNC + noVNC。

```
MainActivity (WebView)
      │  http://127.0.0.1:6080/vnc_lite.html
      ▼
noVNC / websockify :6080  ──►  Xvnc :5900 (:0)  ──►  XFCE4 桌面
      ▲                             ▲
      └──  termux/proot (+外置loader) ─┘   ← 容器根 = assets/rootfs.bin 解出的 rootfs
```

## 环境要求 / Requirements

**中文**

| 项目 | 要求 |
| --- | --- |
| 架构 | arm64-v8a（`abiFilters` 只保留 arm64） |
| 系统 | Android 8.0+（`minSdk 26`，`targetSdk 28`，`compileSdk 34`） |
| 构建 | JDK 17、Android SDK、Gradle 8.10.2（wrapper 自带） |
| 重新构建 rootfs | Docker（arm64 原生或 buildx） |

`targetSdk` 故意保持 28：targetSdk ≥ 29 后应用数据目录内的二进制不可执行，
必须让 proot 套件以 `lib*.so` 之名释放到 `nativeLibraryDir`（该目录始终可执行），
再从应用目录 symlink 过去——与 Termux 采用同一方案。APK 离线直装，不上架
Google Play。

**English**

| Item | Requirement |
| --- | --- |
| Architecture | arm64-v8a only (`abiFilters` keeps arm64 only) |
| System | Android 8.0+ (`minSdk 26`, `targetSdk 28`, `compileSdk 34`) |
| Build | JDK 17, Android SDK, Gradle 8.10.2 (bundled wrapper) |
| Rebuild rootfs | Docker (native arm64 or buildx) |

`targetSdk` is intentionally kept at 28: with targetSdk ≥ 29 binaries inside the
app data directory are not executable, so the proot toolchain must be extracted
to `nativeLibraryDir` under `lib*.so` names (that directory is always executable)
and symlinked from the app directory — the same approach Termux uses. The APK is
installed offline directly and is not published on Google Play.

## 目录结构 / Project Structure

```
alpine-desktop/
├── app/
│   ├── build.gradle                  # 包名、SDK、签名、noCompress/useLegacyPackaging
│   └── src/main/
│       ├── AndroidManifest.xml       # INTERNET / FOREGROUND_SERVICE / WAKE_LOCK
│       │                             # READ/WRITE_EXTERNAL_STORAGE（共享目录 / shared dir）
│       │                             # POST_NOTIFICATIONS（前台通知 / notifications）
│       │                             # CHANGE_WIFI_MULTICAST_STATE（LocalSend 组播 / multicast）
│       ├── assets/rootfs.bin         # Alpine XFCE 根文件系统（tar.gz，改扩展名避开 aapt）
│       ├── jniLibs/arm64-v8a/        # termux/proot 套件（.so 名 → 释放为可执行文件）
│       │   ├── libproot.so           #   termux 版 proot（含 SCM_CREDENTIALS 等补丁）
│       │   ├── libloader.so          #   proot 外置 loader（必须经 PROOT_LOADER 指定）
│       │   ├── liblibtalloc.so.2.so  #   proot 依赖的 talloc（SONAME=libtalloc.so.2）
│       │   ├── libbusybox.so         #   宿主侧工具
│       │   └── libbash.so
│       ├── java/com/qsbye/alpinedesktop/
│       │   ├── MainActivity.java     # 权限检查界面（首屏）→ 启动屏 → noVNC WebView
│       │   ├── SettingsActivity.java # 软件设置（横竖屏/分辨率 + 共享目录说明/打开），仅权限检查界面可进入
│       │   ├── LinuxService.java     # 前台服务：引导 symlink → 释放 rootfs → 起 proot
│       │   ├── AppConfig.java        # 启动配置 TOML 读写（朝向/分辨率），首启创建默认文件
│       │   ├── TarExtractor.java     # 零依赖 tar.gz 解包（ustar/GNU long/pax，防目录穿越）
│       │   └── Status.java           # 服务与 UI 之间共享的状态/日志（进程级单例）
│       └── res/                      # 权限/启动/设置布局、卡片背景、图标、通知小图标
├── rootfs-build/
│   ├── Dockerfile                    # alpine:3.20 + xfce4 + tigervnc + novnc + 中文字体
│   │                                 #   + gcompat/openssh/curl/bash + localsend-cli
│   │                                 #   + opencode（官方脚本安装）
│   ├── desktop-start.sh              # 客户机入口：dbus(nofork) → Xvnc → startxfce4
│   │                                 #   → websockify → sshd(-D)
│   ├── localsend-cli                 # LocalSend CLI（arm64 glibc 动态链接版）
│   ├── localsend_compat.c            # musl 兼容垫片源码（见"关键技术注意点"）
│   └── localsend_compat.so           # 垫片编译产物（docker 内 alpine build-base 编译）
├── scripts/
│   ├── build-rootfs.sh               # Docker 构建 rootfs 并导出为 assets/rootfs.bin
│   ├── build-proot.sh                # 【历史】旧的静态 proot 构建入口
│   └── proot-build/build.sh          # 【历史】proot 5.4.0 + Alpine 补丁（已被 termux 套件取代）
├── build-with-timestamp.sh           # 一键：assembleRelease → zipalign → apksigner
│                                     #   → alpine-desktop-release-signed_<时间戳>.apk
├── build.gradle / settings.gradle    # AGP 8.5.1，仓库走阿里云镜像 + jitpack + google/mavenCentral
└── gradlew                           # ./gradlew assembleDebug
```

## 构建步骤 / Build Steps

### 1. 生成 rootfs（Docker） / Generate rootfs (Docker)

**中文**

```bash
./scripts/build-rootfs.sh
# → app/src/main/assets/rootfs.bin  (docker create + docker export | gzip -1)
```

用 `docker export` 而不是容器内 `busybox tar`：后者处理 Alpine `/bin -> usr/bin`
合并目录有 bug，会丢掉大量 busybox 小工具。

**English**

```bash
./scripts/build-rootfs.sh
# → app/src/main/assets/rootfs.bin  (docker create + docker export | gzip -1)
```

`docker export` is used instead of in-container `busybox tar`: the latter has a
bug handling Alpine's merged `/bin -> usr/bin` directories and drops many busybox
applets.

### 2. proot 套件（已随仓库提供，无需构建） / proot toolchain (bundled, no build needed)

**中文**

`jniLibs/arm64-v8a/` 下的 5 个文件取自 code_lfa 预编译的 termux/proot 套件，
直接随 APK 打包。文件名不能改（Android 只按 `lib*.so` 识别并释放）。

> `scripts/proot-build/` 是更早一版自研静态 proot 5.4.0 的构建脚本，仅作历史
> 保留。上游 proot 缺少 termux 的 `sendmsg`/`SCM_CREDENTIALS` 补丁，会导致
> xfsettingsd 报 "Unable to contact settings server"，**不要再切回去**。

**English**

The 5 files under `jniLibs/arm64-v8a/` are taken from the prebuilt termux/proot
toolchain in code_lfa and are packaged directly into the APK. Their filenames
must not be changed (Android only recognizes and extracts them as `lib*.so`).

> `scripts/proot-build/` contains the build scripts for an older self-built
> static proot 5.4.0, kept for history only. Upstream proot lacks Termux's
> `sendmsg`/`SCM_CREDENTIALS` patches, which makes xfsettingsd fail with
> "Unable to contact settings server" — **do not switch back to it**.

### 3. 打包 APK / Build the APK

**中文**

```bash
# macOS 上请显式指定 JDK 17
JAVA_HOME=/path/to/jbr-17 ./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk   （约 666 MB）

# 或一键出签名 release 包（时间戳命名，归档到项目根目录）
./build-with-timestamp.sh
# → alpine-desktop-release-signed_YYYYMMDD_HHMMSS.apk
```

`rootfs.bin` 通过 `androidResources { noCompress 'bin' }` 在 APK 内原样保留
（用 `.bin` 扩展名避开 aapt 对 `.gz` 的特殊处理），运行时由 `TarExtractor`
自行 gunzip；jniLibs 经 `useLegacyPackaging true` 解压到 `nativeLibraryDir`。

安装：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.qsbye.alpinedesktop/.MainActivity
```

首次启动解压 384 MB rootfs（解出约 900 MB），视闪存速度约需 1–3 分钟。

**English**

```bash
# On macOS specify JDK 17 explicitly
JAVA_HOME=/path/to/jbr-17 ./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk   (~666 MB)

# Or produce a signed release in one step (timestamped name, copied to project root)
./build-with-timestamp.sh
# → alpine-desktop-release-signed_YYYYMMDD_HHMMSS.apk
```

`rootfs.bin` is stored uncompressed inside the APK via
`androidResources { noCompress 'bin' }` (the `.bin` extension avoids aapt's
special handling of `.gz`); at runtime `TarExtractor` gunzips it itself. jniLibs
are extracted to `nativeLibraryDir` with `useLegacyPackaging true`.

Install:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.qsbye.alpinedesktop/.MainActivity
```

The first launch extracts the 384 MB rootfs (~900 MB uncompressed), taking
about 1–3 minutes depending on flash storage speed.

## 运行流程 / Runtime Flow

**中文**

1. **权限检查（每次启动首屏）**：`MainActivity` 首先显示权限检查界面（不自动
   弹窗、不启动任何服务），逐项列出存储权限（READ+WRITE 均授予才算通过）、
   通知权限（以 `NotificationManager.areNotificationsEnabled()` 实测为准），
   每项显示实时状态并可单独点击“授予权限”（存储经 XXPermissions 一并申请；
   通知在支持运行时授权的机型直接申请，含 EMUI 回填的 POST_NOTIFICATIONS，
   否则跳转系统通知设置页）；`onResume` 统一刷新状态（应对权限被系统回收、
   从系统设置返回等情况）。
   - 点“**软件设置**”进入 `SettingsActivity`：单选横屏/竖屏、分辨率自动/
     手动（`1280x720` 格式校验），保存覆盖
     `Documents/VectrasVM/config/config.toml`；该界面**仅此入口**，桌面运行
     期间无法进入。页内另有“**共享目录**”区块：列出宿主机/guest 两侧路径
     （`Documents/VectrasVM/home/qsbye/share` ↔ `/home/qsbye/share`）与
     使用说明（同目录双向实时可见、Thunar/终端/SSH 操作、noexec 注意点），
     “使用文件管理器打开共享目录”按钮先确保目录存在，再优先通过
     DocumentsUI 精确定位；其他文件管理器按包名白名单兜底（华为文件管理器
     仅能打开内部存储根目录）。页内还有“**局域网连接（其他设备）**”区块：
     同 WiFi 前提与手机 IP 查看方法，以及 4 种接入方式的完整步骤——
     浏览器 `http://手机IP:6080/vnc_lite.html?autoconnect=1&resize=remote`、
     VNC 客户端 `手机IP:5900`（无密码）、SSH `ssh qsbye@手机IP -p 8022`
     （密码 `qsbye`）、LocalSend CLI 收发（`-f` 发送按 1-9 选设备，
     `--destination` 接收按 Y/N/P 应答）
   - 点“**进入 Alpine**”：若有未授予权限先弹确认（功能降级，仍可继续）；
     随后确保默认 config.toml 存在（存储权限可用时）、读取配置并
     `setRequestedOrientation` 锁定朝向，再进入下方第 2 步的初始化流程
2. `LinuxService` 首启检测 `files/rootfs/.ready`，不存在则解包 `assets/rootfs.bin`
   （带百分比进度），补齐 `dev/{pts,shm,fd}`、`tmp`（1777）等目录。
3. **引导工具链**（`setupToolBin`）：在 `files/bin` 下建符号链接指向
   `nativeLibraryDir`：

   | 链接名 | 目标 |
   | --- | --- |
   | `proot` | `libproot.so` |
   | `loader` | `libloader.so` |
   | `libtalloc.so.2` | `liblibtalloc.so.2.so` |
   | `busybox` / `bash` | `libbusybox.so` / `libbash.so` |
   | `sh tar cat cp rm mkdir ln grep sed …` | busybox applet |

4. **组装 proot 命令**（对齐 `proot-distro login`）：
   - `--sysvipc -L --link2symlink --root-id --kill-on-exit`
   - `--rootfs=<files>/rootfs --cwd=/root`
   - `--bind=/dev`、`/dev/urandom:/dev/random`、`/proc`、`/proc/self/fd:/dev/fd`
   - 假系统数据覆盖绑定：rootfs 内 `proc/.loadavg .stat .uptime .version`
     分别盖到 `/proc/*`，`sys/.empty` 盖到 `/sys/fs/selinux`
   - `--bind=<files>/tmp:/tmp`
   - 存储权限已授予时追加 `--bind=<宿主机 Documents/VectrasVM/home/qsbye/share>:/home/qsbye/share`
     （目录不存在先自动创建；proot bind 是路径翻译不是真 mount，
     `mount`/`df` 里看不到，属预期）
   - guest 入口经 `/usr/bin/env -i` 以干净环境启动（`HOME=/home/qsbye`、
     固定 guest `PATH`、`GEOMETRY` 按配置计算：`auto` 时取 MainActivity
     旋转后实测的应用窗口内容区 **DIP（CSS 像素）** 尺寸（高度扣除
     noVNC 顶部控制栏），手动分辨率则原样使用），执行
     `/usr/local/bin/desktop-start.sh`
   - 宿主侧环境仅设：`PATH=<files>/bin:/system/bin`、
     **`PROOT_LOADER=<files>/bin/loader`**、`LD_LIBRARY_PATH=<files>/bin`、
     `TMPDIR`/`PROOT_TMP_DIR=<files>/tmp`
5. `desktop-start.sh` 在客户机内：建 X11/IPC 运行时目录 →
   `dbus-daemon --system/--session --nofork`（见下方注意点）→
   `Xvnc :0 -SecurityTypes None`（RFB 5900，监听 0.0.0.0、无密码）→
   `startxfce4` → `websockify --web=/usr/share/novnc 6080 127.0.0.1:5900` →
   `sshd -D -p 8022`（局域网 ssh），
   最后 `exec sleep infinity` 保持会话存活。
6. 服务轮询 `127.0.0.1:6080`（150 s 超时），就绪后置 `Status.RUNNING`；
   UI 加载 `vnc_lite.html?autoconnect=1&resize=remote` 进入桌面。
7. 日志同时进入内存环形缓冲（最近 200 行，启动屏滚动显示）
   和 `files/logs/desktop.log`；guest 内各组件日志在 `/tmp/*.log`。

**English**

1. **Permission check (first screen on every launch)**: `MainActivity` first
   shows the permission-check screen (no automatic dialogs, no services
   started), listing storage permission (both READ and WRITE must be granted)
   and notification permission (checked live via
   `NotificationManager.areNotificationsEnabled()`) one by one; each row shows
   live status and has its own "Grant" button (storage is requested together
   via XXPermissions; notifications are requested directly on ROMs supporting
   runtime grants, including EMUI's backported POST_NOTIFICATIONS, otherwise
   the button opens the system notification settings page). `onResume`
   refreshes all statuses (handling permissions being revoked by the system,
   returning from system settings, etc.).
   - Tap "**Settings**" to open `SettingsActivity`: radio choices for
     landscape/portrait and auto/custom resolution (validated against
     `1280x720`); saving overwrites
     `Documents/VectrasVM/config/config.toml`. This is the **only entry** to
     the settings screen; it cannot be opened while the desktop is running.
     The screen also contains a "**Shared folder**" section: it lists both
     paths (`Documents/VectrasVM/home/qsbye/share` ↔ `/home/qsbye/share`) and
     usage notes (the same folder, visible on both sides in real time; usable
     from Thunar/terminal/SSH; noexec caveat). The "Open shared folder in
     file manager" button creates the folder if needed and opens it precisely
     via DocumentsUI first; other known file managers are used as fallbacks
     by package allowlist (Huawei's file manager can only open the storage
     root). A "**LAN access (other devices)**" section adds the same-WiFi
     prerequisite, how to find the phone's IP, and step-by-step instructions
     for all four access methods: browser
     `http://PHONE_IP:6080/vnc_lite.html?autoconnect=1&resize=remote`,
     VNC client `PHONE_IP:5900` (no password), SSH
     `ssh qsbye@PHONE_IP -p 8022` (password `qsbye`), and LocalSend CLI
     (`-f` to send, pick device by number 1-9; `--destination` to receive,
     answer Y/N/P).
   - Tap "**Enter Alpine**": if any permission is missing a confirmation dialog
     appears first (features degrade, you may still continue); then the default
     config.toml is created if missing (when storage is available), the config
     is read, the orientation is locked with `setRequestedOrientation`, and the
     initialization in step 2 begins.
2. On first launch `LinuxService` checks `files/rootfs/.ready`; if absent it
   extracts `assets/rootfs.bin` (with percentage progress) and creates
   `dev/{pts,shm,fd}`, `tmp` (1777), etc.
3. **Bootstrap the toolchain** (`setupToolBin`): create symlinks under
   `files/bin` pointing to `nativeLibraryDir`:

   | Link name | Target |
   | --- | --- |
   | `proot` | `libproot.so` |
   | `loader` | `libloader.so` |
   | `libtalloc.so.2` | `liblibtalloc.so.2.so` |
   | `busybox` / `bash` | `libbusybox.so` / `libbash.so` |
   | `sh tar cat cp rm mkdir ln grep sed …` | busybox applet |

4. **Assemble the proot command** (aligned with `proot-distro login`):
   - `--sysvipc -L --link2symlink --root-id --kill-on-exit`
   - `--rootfs=<files>/rootfs --cwd=/root`
   - `--bind=/dev`, `/dev/urandom:/dev/random`, `/proc`, `/proc/self/fd:/dev/fd`
   - Fake system-data override binds: rootfs files
     `proc/.loadavg .stat .uptime .version` over `/proc/*`, and
     `sys/.empty` over `/sys/fs/selinux`
   - `--bind=<files>/tmp:/tmp`
   - When storage permission is granted, additionally
     `--bind=<host Documents/VectrasVM/home/qsbye/share>:/home/qsbye/share`
     (created automatically if missing; proot bind is path translation, not a
     real mount — it does not appear in `mount`/`df`, which is expected)
   - The guest entry starts through `/usr/bin/env -i` with a clean environment
     (`HOME=/home/qsbye`, fixed guest `PATH`, `GEOMETRY` computed from config:
     for `auto` the activity measures the actual window content size in
     **DIP (CSS pixels)** after rotation (height reduced by the noVNC top
     control bar); a custom resolution is used as-is), running
     `/usr/local/bin/desktop-start.sh`
   - Host-side environment only sets: `PATH=<files>/bin:/system/bin`,
     **`PROOT_LOADER=<files>/bin/loader`**, `LD_LIBRARY_PATH=<files>/bin`,
     `TMPDIR`/`PROOT_TMP_DIR=<files>/tmp`
5. Inside the guest, `desktop-start.sh`: creates X11/IPC runtime dirs →
   `dbus-daemon --system/--session --nofork` (see notes below) →
   `Xvnc :0 -SecurityTypes None` (RFB 5900, listening on 0.0.0.0, no password) →
   `startxfce4` → `websockify --web=/usr/share/novnc 6080 127.0.0.1:5900` →
   `sshd -D -p 8022` (LAN ssh access),
   finally `exec sleep infinity` to keep the session alive.
6. The service polls `127.0.0.1:6080` (150 s timeout); once reachable it sets
   `Status.RUNNING`, and the UI loads
   `vnc_lite.html?autoconnect=1&resize=remote` to enter the desktop.
7. Logs go both into an in-memory ring buffer (last 200 lines, shown on the
   launch screen) and `files/logs/desktop.log`; per-component guest logs are in
   `/tmp/*.log`.

## 关键技术注意点（踩坑结论） / Key Technical Notes (lessons learned)

**中文**

- **外置 loader 必须给**：`PROOT_LOADER` 指向从 `libloader.so` 链出的
  `files/bin/loader`，且 `LD_LIBRARY_PATH` 包含工具目录。proot 自身是动态链接，
  缺 loader/talloc 时直接 exec 失败。
- **绝不能设 `PROOT_NO_SECCOMP`**：termux/proot 在 Android app 进程（zygote
  派生）里走纯 ptrace 路径时，会出现 `getcwd()` 返回值被改写为 -1、
  musl/python 报 `OSError: [Errno 38] Function not implemented`、
  `getgroups()` 泄漏 Android 真实组等问题；保持默认（自动探测 seccomp 加速）
  才是验证过的组合。
- **避免双重 fork 的守护进程**：app 进程域内 proot 无法可靠跟踪 daemon 双重
  fork 出的孙进程（chdir/getgroups 等直通 Android 宿主而失败）。因此
  dbus 不用 `dbus-launch`/`--fork`，改用
  `dbus-daemon --nofork --print-address ... &` 后从临时文件读取会话地址；
  websockify 也不用 `-D`，一律普通 `&` 后台。
- Xvnc 以 root 身份跑（`--root-id` 下恒为 uid 0）并带 `-nolock -ac`：
  绕开 proot 下 lock 文件 `link()` 失败和 X 授权问题。
- dbus 日志里 `Failed to set fd limit to 65536: Operation not permitted`
  是 setrlimit 被 proot 拒绝的无害提示，保持默认 fd 上限即可。
- **glibc 二进制在 musl 上跑（localsend-cli）**：三个坑三种解法——
  ① 装 `gcompat` 提供 `ld-linux-aarch64.so.1` 等基础符号；
  ② 该二进制引用了 glibc≥2.34 并入 libc.so.6 的私有符号 `__res_init`，
  musl 没有，用 `localsend_compat.so`（LD_PRELOAD）给空实现；
  ③ Android 禁止 app 域 bind netlink 路由套接字（`ip addr` 都报
  `bind: Permission denied`），musl 的 `getifaddrs()` 走 netlink 必然失败，
  垫片里用 ioctl（`SIOCGIFCONF`）重写 `getifaddrs/freeifaddrs`。
  垫片只经 `/usr/bin/localsend-cli` 包装器注入该进程，不影响系统其它程序。
- **局域网组播 EACCES**：guest 内组播 sendto 返回 `Permission denied`，
  需要 manifest 声明 `CHANGE_WIFI_MULTICAST_STATE` 且 App 侧持有
  `WifiManager.MulticastLock`（LinuxService.onCreate 获取、onDestroy 释放）。
- **共享目录在 /sdcard**：sdcard FUSE 挂载带 `noexec` 且不支持 chown/chmod，
  共享目录里的程序要先 `cp` 到 guest 内（如 /tmp）再执行；
  guest 写入的文件宿主侧属主显示为 media_rw，属正常。

**English**

- **The external loader is mandatory**: `PROOT_LOADER` points to
  `files/bin/loader` symlinked from `libloader.so`, and `LD_LIBRARY_PATH`
  includes the tools directory. proot itself is dynamically linked and fails to
  exec without loader/talloc.
- **Never set `PROOT_NO_SECCOMP`**: when termux/proot takes the pure-ptrace
  path inside an Android app process (forked from zygote), `getcwd()` gets
  rewritten to -1, musl/python fails with
  `OSError: [Errno 38] Function not implemented`, and `getgroups()` leaks real
  Android groups. Keeping the default (auto-detected seccomp acceleration) is
  the verified configuration.
- **Avoid double-forking daemons**: within the app process domain proot cannot
  reliably trace grandchild processes produced by a daemon's double fork
  (chdir/getgroups pass straight through to the Android host and fail). So
  dbus does not use `dbus-launch`/`--fork`; instead
  `dbus-daemon --nofork --print-address ... &` is used and the session address
  is read from a temp file. websockify likewise avoids `-D`; everything is
  backgrounded with plain `&`.
- Xvnc runs as root (uid is always 0 under `--root-id`) with `-nolock -ac`:
  this works around the lock-file `link()` failure under proot and X
  authorization issues.
- The dbus log message
  `Failed to set fd limit to 65536: Operation not permitted` is a harmless
  notice that setrlimit was denied by proot; the default fd limit is fine.
- **Running a glibc binary on musl (localsend-cli)**: three problems, three
  fixes —
  ① install `gcompat` to provide `ld-linux-aarch64.so.1` and other base
  symbols;
  ② the binary references the private symbol `__res_init` which glibc ≥ 2.34
  merged into libc.so.6; musl lacks it, so `localsend_compat.so` (LD_PRELOAD)
  provides an empty implementation;
  ③ Android forbids app domains from binding netlink routing sockets (even
  `ip addr` fails with `bind: Permission denied`), and musl's `getifaddrs()`
  necessarily uses netlink; the shim rewrites `getifaddrs/freeifaddrs` using
  ioctl (`SIOCGIFCONF`).
  The shim is injected only via the `/usr/bin/localsend-cli` wrapper, so other
  programs are unaffected.
- **LAN multicast EACCES**: guest multicast sendto returns
  `Permission denied`; the manifest must declare
  `CHANGE_WIFI_MULTICAST_STATE` and the app must hold a
  `WifiManager.MulticastLock` (acquired in LinuxService.onCreate, released in
  onDestroy).
- **Shared dir on /sdcard**: the sdcard FUSE mount is `noexec` and does not
  support chown/chmod; programs in the shared dir must first be `cp`'d into the
  guest (e.g. /tmp) before execution. Files written by the guest appear owned
  by media_rw on the host, which is normal.

## 常用信息 / Reference

**中文**

- **账号**：`qsbye` / `qsbye`，sudo 免密（桌面会话实际以 fake root 运行，
  `HOME=/home/qsbye`；在 XFCE 终端里可用 `login qsbye` 切到该账户）
- **端口**：5900 = Xvnc RFB（0.0.0.0、无密码），6080 = noVNC WebSocket/Web
  （websockify 默认绑 0.0.0.0），8022 = OpenSSH（qsbye/qsbye，密码或公钥）
- **共享目录**：宿主 `Documents/VectrasVM/home/qsbye/share` ↔ guest
  `/home/qsbye/share`，两侧实时互通；软件设置页内可看使用说明并一键用
  文件管理器打开
- **启动配置**：宿主 `Documents/VectrasVM/config/config.toml`
  （进入 Alpine 时自动创建，或由软件设置界面生成）：

  ```toml
  orientation = "landscape"   # landscape 横屏（默认）/ portrait 竖屏
  resolution  = "auto"        # auto 适配屏幕物理分辨率，或手动 "1280x720"
  ```

  推荐在启动权限检查界面点“软件设置”图形化修改；手动改文件后完全退出应用
  再进入即生效
- **分辨率（auto 适合屏幕）**：MainActivity 在旋转布局稳定后实测应用窗口
  内容区（物理像素 ÷ density = DIP，高度扣除 noVNC 顶部控制栏），
  经 `Status` 传给 LinuxService 作为 Xvnc `GEOMETRY`；未实测到时服务用
  `Configuration.screenWidthDp/HeightDp` 兜底。页面 viewport 同为 DIP，
  画布 1:1 完整显示；noVNC `resize=remote` 连上后还会按页面容器微调
  （rootfs 内 vnc_lite.html 已打补丁支持该参数，官方精简版默认不识别）
- **Guest 内置工具**：`localsend-cli`（局域网传文件）、`opencode`
  （AI 终端编程助手），桌面终端或 ssh 会话中直接运行
- **退出/重置**：清除应用数据即重新走首次释放；`onDestroy` 会终止 proot
  （`--kill-on-exit` 连带结束整个 guest）

**English**

- **Account**: `qsbye` / `qsbye`, passwordless sudo (the desktop session
  actually runs as fake root with `HOME=/home/qsbye`; in an XFCE terminal you
  can switch with `login qsbye`)
- **Ports**: 5900 = Xvnc RFB (0.0.0.0, no password), 6080 = noVNC
  WebSocket/web (websockify binds 0.0.0.0 by default), 8022 = OpenSSH
  (qsbye/qsbye, password or public key)
- **Shared dir**: host `Documents/VectrasVM/home/qsbye/share` ↔ guest
  `/home/qsbye/share`, synced in real time; the Settings screen shows usage
  notes and opens it in a file manager with one tap
- **Launch config**: host `Documents/VectrasVM/config/config.toml`
  (auto-created when entering Alpine, or generated by the Settings screen):

  ```toml
  orientation = "landscape"   # landscape (default) / portrait
  resolution  = "auto"        # auto fits the physical screen, or custom "1280x720"
  ```

  Prefer the "Settings" button on the launch permission screen for a graphical
  editor; after editing the file manually, fully quit and reopen the app for it
  to take effect.
- **Resolution (auto fits screen)**: after the rotated layout settles, the
  activity measures the actual window content size (physical pixels ÷ density
  = DIP; height reduced by the noVNC top control bar) and passes it through
  `Status` to LinuxService as the Xvnc `GEOMETRY`; if unavailable, the
  service falls back to `Configuration.screenWidthDp/HeightDp`. The page
  viewport is also DIP, so the canvas shows the full desktop 1:1; noVNC
  `resize=remote` fine-tunes to the page container on connect (the in-rootfs
  vnc_lite.html is patched to support this — the stock lite page ignores it).
- **Bundled guest tools**: `localsend-cli` (LAN file transfer) and `opencode`
  (AI terminal coding assistant), runnable directly in a desktop terminal or
  ssh session.
- **Exit/Reset**: clearing app data reruns the first-time extraction;
  `onDestroy` terminates proot (`--kill-on-exit` tears down the whole guest).

## 排障 / Troubleshooting

| 现象（中文） / Symptom (English) | 查看 / 处理（中文） / Action |
| --- | --- |
| 启动卡在“正在启动 XFCE 桌面” / Stuck at "Starting XFCE desktop" | 启动屏滚动日志；`files/logs/desktop.log` / Scrolling launch-screen logs; `files/logs/desktop.log` |
| guest 内大量 `Function not implemented` / Many `Function not implemented` in guest | 检查是否误设了 `PROOT_NO_SECCOMP`（删掉它） / Check for an erroneous `PROOT_NO_SECCOMP` (remove it) |
| dbus 报 `Memory allocation failure` / chdir 失败 / dbus `Memory allocation failure` or chdir failure | 确认 desktop-start.sh 用的是 `--nofork` 而不是 `dbus-launch` / Ensure desktop-start.sh uses `--nofork`, not `dbus-launch` |
| 6080 不通 / Port 6080 unreachable | guest 内 `/tmp/vnc.log`、`/tmp/xfce.log`、`/tmp/websockify.log` / Guest `/tmp/vnc.log`, `/tmp/xfce.log`, `/tmp/websockify.log` |
| proot 启动即退出 / proot exits immediately | 确认 `PROOT_LOADER` 指向、`files/bin` 符号链接未断、仅 arm64 设备 / Verify `PROOT_LOADER`, unbroken `files/bin` symlinks, arm64-only device |
| 配置改错导致朝向/分辨率异常 / Bad config breaks orientation or resolution | 编辑或删除 `Documents/VectrasVM/config/config.toml` 后重进应用 / Edit or delete `Documents/VectrasVM/config/config.toml` and reopen |
| 手动调试 / Manual debugging | `adb shell run-as com.qsbye.alpinedesktop` 可进入应用私有目录 / `adb shell run-as com.qsbye.alpinedesktop` enters the app-private dir |
| 桌面顶部面板消失（看似被遮挡） / Top panel missing (looks obscured) | libwnck 的 pager 插件启动竞态崩溃；APK 内置无 pager 的面板配置，每次启动由 LinuxService 自动覆盖修复，升级新版即可 / The libwnck pager plugin crashes at startup; the APK ships a pager-free panel config that LinuxService applies on every boot — update to the new build |

## 已知取舍 / Known Trade-offs

**中文**

- 仅 arm64；`targetSdk 28` 意味着无法上架 Play（离线直装分发）
- VNC 无密码且监听 0.0.0.0，同网段任何设备可连入并操控桌面，仅限可信网络；
  sshd 同理（密码 qsbye 为弱口令，用后建议改密或换公钥）
- 屏幕保持常亮（`FLAG_KEEP_SCREEN_ON` + `PARTIAL_WAKE_LOCK`，24 h 上限）
- rootfs 打进 APK，安装包约 666 MB；更新 guest 系统需重新构建 rootfs 并打包

**English**

- arm64 only; `targetSdk 28` means it cannot be listed on Play (distributed via
  offline direct install)
- VNC is passwordless and listens on 0.0.0.0, so any device on the same network
  can connect and control the desktop — trusted networks only. The same applies
  to sshd (qsbye is a weak password; change it or switch to public keys)
- The screen stays on (`FLAG_KEEP_SCREEN_ON` + `PARTIAL_WAKE_LOCK`, 24 h cap)
- The rootfs is bundled in the APK, making it ~666 MB; updating the guest system
  requires rebuilding the rootfs and repackaging

---

<a id="english"></a>

## English Overview

**AlpineDesktop** is a fully **offline** Alpine Linux XFCE desktop running on
Android devices.

The app packages an Alpine rootfs (`rootfs.bin`, ~384 MB, actually a tar.gz)
together with the **Termux-ecosystem proot toolchain** (proot + external loader
+ busybox + bash) inside the APK. On first launch the rootfs is extracted into
the app-private directory; symlinks under `files/bin` pointing to
`nativeLibraryDir` bootstrap proot; then Xvnc + XFCE + websockify/noVNC are
started with the same command line as proot-distro, and the built-in WebView
connects to local `127.0.0.1:6080` to display the desktop. No root and no
network connection are required after the APK is installed.

Every section of this document is bilingual — scroll up to find, after each
**中文** block, the matching **English** translation for:

1. [Requirements](#环境要求--requirements)
2. [Project Structure](#目录结构--project-structure)
3. [Build Steps](#构建步骤--build-steps)
4. [Runtime Flow](#运行流程--runtime-flow)
5. [Key Technical Notes](#关键技术注意点踩坑结论--key-technical-notes-lessons-learned)
6. [Reference](#常用信息--reference)
7. [Troubleshooting](#排障--troubleshooting)
8. [Known Trade-offs](#已知取舍--known-trade-offs)

> The proot bootstrap is reused from [code_lfa](../../../code_lfa-main) (a
> Flutter project wrapping proot-distro + Ubuntu + code-server); the desktop
> side replaces code-server with XFCE + TigerVNC + noVNC.
