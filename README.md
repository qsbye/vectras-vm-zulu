# AlpineDesktop（Alpine XFCE）

在 Android 设备上**完全离线**运行的 Alpine Linux XFCE 桌面。

应用把一个打包好的 Alpine rootfs（约 280 MB 的 `rootfs.bin`，实为 tar.gz）和
**Termux 生态的 proot 套件**（proot + 外置 loader + busybox + bash）一起塞进 APK：
首次启动时把 rootfs 释放到应用私有目录，在 `files/bin` 下建立指向
`nativeLibraryDir` 的符号链接引导 proot，再以 proot-distro 同款命令行启动
Xvnc + XFCE + websockify/noVNC，最后由内置 WebView 连接本机
`127.0.0.1:6080` 显示桌面。全程不需要 root、不需要联网（安装好 APK 之后）。

附加能力：

- **VNC 监听 0.0.0.0**：局域网内任何 VNC 客户端可直接连 `<手机IP>:5900`
  （无密码，仅限可信网络）
- **OpenSSH**：guest 内 sshd 监听 `0.0.0.0:8022`，`ssh qsbye@<手机IP> -p 8022`
  （密码 `qsbye`，Android 应用无权绑定 22 端口故用 8022）
- **共享目录**：宿主机 `Documents/VectrasVM/home/qsbye/share` ↔ guest
  `/home/qsbye/share` 双向互通（目录不存在自动创建；存储权限由
  XXPermissions 在首启时申请，拒绝仅禁用该功能）
- **LocalSend CLI**：内置 `localsend-cli`（arm64 glibc 版 + musl 兼容垫片），
  任意 guest 会话（含 ssh）里直接运行，局域网互传文件

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

## 环境要求

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

## 目录结构

```
alpine-desktop/
├── app/
│   ├── build.gradle                  # 包名、SDK、签名、noCompress/useLegacyPackaging
│   └── src/main/
│       ├── AndroidManifest.xml       # INTERNET / FOREGROUND_SERVICE / WAKE_LOCK
│       │                             # READ/WRITE_EXTERNAL_STORAGE（共享目录）
│       │                             # CHANGE_WIFI_MULTICAST_STATE（LocalSend 组播）
│       ├── assets/rootfs.bin         # Alpine XFCE 根文件系统（tar.gz，改扩展名避开 aapt）
│       ├── jniLibs/arm64-v8a/        # termux/proot 套件（.so 名 → 释放为可执行文件）
│       │   ├── libproot.so           #   termux 版 proot（含 SCM_CREDENTIALS 等补丁）
│       │   ├── libloader.so          #   proot 外置 loader（必须经 PROOT_LOADER 指定）
│   │   ├── liblibtalloc.so.2.so  #   proot 依赖的 talloc（SONAME=libtalloc.so.2）
│       │   ├── libbusybox.so         #   宿主侧工具
│       │   └── libbash.so
│       ├── java/com/qsbye/alpinedesktop/
│       │   ├── MainActivity.java     # 启动屏 + 进度/日志 + noVNC WebView
│       │   ├── LinuxService.java     # 前台服务：引导 symlink → 释放 rootfs → 起 proot
│       │   ├── TarExtractor.java     # 零依赖 tar.gz 解包（ustar/GNU long/pax，防目录穿越）
│       │   └── Status.java           # 服务与 UI 之间共享的状态/日志（进程级单例）
│       └── res/                      # 启动屏布局、图标、通知小图标
├── rootfs-build/
│   ├── Dockerfile                    # alpine:3.20 + xfce4 + tigervnc + novnc + 中文字体
│   │                                 #   + gcompat/openssh + localsend-cli
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

## 构建步骤

### 1. 生成 rootfs（Docker）

```bash
./scripts/build-rootfs.sh
# → app/src/main/assets/rootfs.bin  (docker create + docker export | gzip -1)
```

用 `docker export` 而不是容器内 `busybox tar`：后者处理 Alpine `/bin -> usr/bin`
合并目录有 bug，会丢掉大量 busybox 小工具。

### 2. proot 套件（已随仓库提供，无需构建）

`jniLibs/arm64-v8a/` 下的 5 个文件取自 code_lfa 预编译的 termux/proot 套件，
直接随 APK 打包。文件名不能改（Android 只按 `lib*.so` 识别并释放）。

> `scripts/proot-build/` 是更早一版自研静态 proot 5.4.0 的构建脚本，仅作历史
> 保留。上游 proot 缺少 termux 的 `sendmsg`/`SCM_CREDENTIALS` 补丁，会导致
> xfsettingsd 报 "Unable to contact settings server"，**不要再切回去**。

### 3. 打包 APK

```bash
# macOS 上请显式指定 JDK 17
JAVA_HOME=/path/to/jbr-17 ./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk   （约 571 MB）

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

首次启动解压 280 MB rootfs（解出约 670 MB），视闪存速度约需 1–3 分钟。

## 运行流程

1. `MainActivity` 展示启动屏，启动前台服务 `LinuxService` 并每 600 ms 轮询 `Status`。
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
     固定 guest `PATH`、`GEOMETRY=<屏幕分辨率>`），执行
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

## 关键技术注意点（踩坑结论）

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

## 常用信息

- **账号**：`qsbye` / `qsbye`，sudo 免密（桌面会话实际以 fake root 运行，
  `HOME=/home/qsbye`；在 XFCE 终端里可用 `login qsbye` 切到该账户）
- **端口**：5900 = Xvnc RFB（0.0.0.0、无密码），6080 = noVNC WebSocket/Web
  （websockify 默认绑 0.0.0.0），8022 = OpenSSH（qsbye/qsbye，密码或公钥）
- **共享目录**：宿主 `Documents/VectrasVM/home/qsbye/share` ↔ guest
  `/home/qsbye/share`
- **分辨率**：`GEOMETRY` 由 App 按 `Display.getRealSize` 传入，
  noVNC `resize=remote` 跟随窗口
- **退出/重置**：清除应用数据即重新走首次释放；`onDestroy` 会终止 proot
  （`--kill-on-exit` 连带结束整个 guest）

## 排障

| 现象 | 查看 / 处理 |
| --- | --- |
| 启动卡在“正在启动 XFCE 桌面” | 启动屏滚动日志；`files/logs/desktop.log` |
| guest 内大量 `Function not implemented` | 检查是否误设了 `PROOT_NO_SECCOMP`（删掉它） |
| dbus 报 `Memory allocation failure` / chdir 失败 | 确认 desktop-start.sh 用的是 `--nofork` 而不是 `dbus-launch` |
| 6080 不通 | guest 内 `/tmp/vnc.log`、`/tmp/xfce.log`、`/tmp/websockify.log` |
| proot 启动即退出 | 确认 `PROOT_LOADER` 指向、`files/bin` 符号链接未断、仅 arm64 设备 |
| 手动调试 | `adb shell run-as com.qsbye.alpinedesktop` 可进入应用私有目录 |

## 已知取舍

- 仅 arm64；`targetSdk 28` 意味着无法上架 Play（离线直装分发）
- VNC 无密码且监听 0.0.0.0，同网段任何设备可连入并操控桌面，仅限可信网络；
  sshd 同理（密码 qsbye 为弱口令，用后建议改密或换公钥）
- 屏幕保持常亮（`FLAG_KEEP_SCREEN_ON` + `PARTIAL_WAKE_LOCK`，24 h 上限）
- rootfs 打进 APK，安装包约 571 MB；更新 guest 系统需重新构建 rootfs 并打包
