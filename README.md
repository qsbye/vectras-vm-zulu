<p align="center">
  <img src="resources/vectrasvm.png" style="width: 30%;" />
</p>

# Vectras VM

[![Ceasefire Now](https://badge.techforpalestine.org/default)](https://techforpalestine.org/learn-more)

[![Telegram Channel][ico-telegram]][link-telegram]
[![Latest Version][ico-version]][link-releases]
[![Software License][ico-license]](LICENSE)

Welcome to Vectras VM! A virtual machine app for Android based on QEMU that lets you emulate various OSes including Windows, macOS, Linux, and Android.

> **欢迎体验 Vectras VM！** 这是一款基于 QEMU 的 Android 虚拟机应用，可模拟多种操作系统，包括 Windows、macOS、Linux 以及 Android。

If you need help, check out [our documentation](https://vectras.vercel.app/how.html). For quick answers, join the [Vectras Telegram group](http://t.me/vectras_vm_discussion).

> 如需帮助，请参阅[官方文档](https://vectras.vercel.app/how.html)；如需快速交流，欢迎加入 [Telegram 群组](http://t.me/vectras_vm_discussion)。

[![Video Thumbnail](https://img.youtube.com/vi/AlNbverd0xE/0.jpg)](https://www.youtube.com/watch?v=AlNbverd0xE)

---

## Device Compatibility / 设备兼容性

### Not Supported / 不支持
The following devices do not support this project on Android 14+:

> 以下设备在 Android 14 及以上系统中**不支持**运行本项目：

- **Oppo**
- **Realme**
- **Huawei**
- **Honor**
- **Vivo**

### Supported Devices / 支持的设备
The following devices are supported:

> 以下设备已确认可以正常运行：

- **Samsung**
- **Google Pixel**
- **Xiaomi (HyperOS)**
- **RedMagic**

You can try running Vectras VM on unsupported devices, but we cannot guarantee stability or support.

> 你也可以在不支持的设备上尝试运行，但我们无法保证其稳定性，也不提供相关技术支持。

---

## Installation / 安装

You can download Vectras VM from the [releases](https://github.com/xoureldeen/Vectras-VM-Android/releases) page or the [official website](https://vectras.vercel.app/download.html).

> 你可以通过 [Releases](https://github.com/xoureldeen/Vectras-VM-Android/releases) 页面或[官方网站](https://vectras.vercel.app/download.html)下载 Vectras VM。

or

> 或通过以下平台获取：

[![OpenAPK](https://www.openapk.net/images/openapk-badge.png)](https://www.openapk.net/vectras-vm/com.vectras.vm/)

---

## Offline Resources / 离线资源

This build includes embedded offline resources for out-of-box use without internet connection:

> 本构建内嵌离线资源，无需网络连接即可开箱即用：

### Embedded Files / 内嵌文件

| Resource / 资源 | Size / 大小 | Description / 说明 |
|---|---|---|
| `bootstrap/{abi}.tar` | ~4 MB | Minimal Alpine Linux rootfs with busybox / 最小化 Alpine Linux 根文件系统（含 busybox） |
| `setup/vectras-vm-arm64-v8a.tar.gz` | 153 MB | QEMU runtime binaries (arm64) / QEMU 运行时二进制文件 (arm64) |
| `roms/WePE_64_V2.3.iso` | 217 MB | Windows PE system image / Windows PE 系统镜像 |
| `roms/QEMU_EFI.img` | 64 MB | UEFI firmware variables / UEFI 固件变量 |
| `roms/QEMU_VARS.img` | 64 MB | UEFI variables store / UEFI 变量存储 |
| `roms/bios-vectras.bin` | 0.2 MB | BIOS firmware / BIOS 固件 |
| `apks/aarch64/` | ~97 MB (168 packages / 168 个包) | Offline Alpine package repository (v3.19 closure + edge testing libiscsi) for auto-installing QEMU runtime libraries / 离线 Alpine 软件包仓库，用于自动补装 QEMU 运行时依赖库 |

### Behavior Changes in This Fork / 本分支交互调整

- **Permission gate on launch / 启动权限检查页**：the app first enters a permission screen requesting **All-files access (MANAGE_EXTERNAL_STORAGE)** on Android 11+ and **notification permission** on Android 13+; the main screen only opens after both are granted.
  > 应用启动先进入权限页，依次请求**所有文件访问权限**（Android 11+）与**通知权限**（Android 13+），授权后才进入主界面。
- **Removed Telegram prompt & ads / 已移除 Telegram 弹窗与广告**：the "JOIN US ON TELEGRAM" dialog and the AdMob banner/interstitial modules are completely removed.
  > 已彻底移除 "JOIN US ON TELEGRAM" 弹窗以及 AdMob 广告模块。
- **Silent offline dependency installation / 缺失依赖自动离线安装**：missing Alpine runtime libraries are installed from the bundled repository automatically (`apk add --no-network --allow-untrusted /apks/aarch64/*.apk`), with no extra button to tap.
  > 检测到缺失的 Alpine 运行库时，直接使用内置仓库静默自动安装，无需用户再次点击。

### Offline Setup / 离线安装

On first launch, the app **automatically installs QEMU from embedded assets** — no network connection and no manual bootstrap selection required. The default UI language is **Chinese (中文)**.

> 首次启动时，应用会**自动从内嵌资源安装 QEMU**——无需网络连接，也无需手动选择 bootstrap。默认界面语言为**中文**。

### Pre-configured ROM / 预配置 ROM

The WePE ISO is automatically registered as a ROM entry in the VM list. No manual configuration needed.

> WePE ISO 会自动注册为 VM 列表中的 ROM 条目，无需手动配置。

**Note**: The offline QEMU runtime targets **arm64** devices (the vast majority of Android phones). x86_64 devices will need to use Auto Setup or Manual Setup.

> **注意**：离线 QEMU 运行时面向 **arm64** 设备（绝大多数安卓手机）。x86_64 设备需要使用自动安装或手动安装。

### Replicating This Build / 复刻本构建

The large binary resources are **not** stored in the git repository. Download them from the [GitHub Release](https://github.com/qsbye/vectras-vm-zulu/releases) and place them at the following paths before building:

> 大体积二进制资源**不**存储在 git 仓库中。请从 [GitHub Release](https://github.com/qsbye/vectras-vm-zulu/releases) 下载，并在构建前放入以下路径：

- `vectras-vm-arm64-v8a.tar.gz` → `app/src/main/assets/setup/`
- `WePE_64_V2.3.iso` → `app/src/main/assets/roms/`

The offline Alpine package repository **is tracked in git** at `app/src/main/assets/apks/aarch64/` (168 `.apk` files). A packaged archive (`alpine-apks-aarch64.tar.gz`) is also published on the dedicated [offline-deps release](https://github.com/qsbye/vectras-vm-zulu/releases) in case you prefer downloading a single archive; extract it so that the files land in the path above.

> 离线 Alpine 软件包仓库**已纳入 git**（`app/src/main/assets/apks/aarch64/`，共 168 个 `.apk`）。同时在独立的 [offline-deps Release](https://github.com/qsbye/vectras-vm-zulu/releases) 中提供打包好的单文件归档 `alpine-apks-aarch64.tar.gz`，下载后解压到上述目录即可。

---

## Known Issues / 已知问题

- **Automatic runtime-library installation may fail on some devices / 部分设备自动补装运行库可能失败**：on certain devices/ROMs the proot login shell used to run `apk add` starts with an incomplete `PATH` (errors such as `/bin/sh: no such command 'apk'`, or the login flag being rejected). As a result QEMU libraries (libX11, GLib, pixman, SDL2, GTK, etc.) may remain missing and `qemu-system-x86_64` cannot start. **Workaround / 临时解决办法**：open the in-app terminal and run:
  > 某些设备/系统上，执行 `apk add` 的 proot 登录 shell 可能以不完整的 `PATH` 启动（报 `/bin/sh: no such command 'apk'` 或登录参数被拒绝），导致 QEMU 依赖库（libX11、GLib、pixman、SDL2、GTK 等）未安装、`qemu-system-x86_64` 无法运行。可在应用内终端手动执行：

  ```sh
  export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
  apk add --no-network --allow-untrusted /apks/aarch64/*.apk
  ```

- **Restricted vendors / 厂商限制**：Huawei, Honor, Oppo, Realme and Vivo devices are not supported on Android 14+ (see [Device Compatibility](#device-compatibility--设备兼容性)); proot may also be restricted by vendor kernels on older Android versions.
  > 华为、荣耀、OPPO、realme、vivo 在 Android 14+ 不受支持；部分旧版本系统上厂商内核也可能限制 proot。
- **WePE VNC display / WePE VNC 显示**：if the VNC view stays black after boot, first verify that all runtime libraries above are installed — most observed black-screen cases were caused by missing QEMU dependencies.
  > 若启动 WePE 后 VNC 黑屏，请先确认上述运行库已全部安装；已观测到的黑屏多数由 QEMU 依赖缺失引起。
- **Offline only / 纯离线环境**：this fork never downloads dependencies at runtime; installing extra Alpine packages beyond the bundled closure requires adding them to `assets/apks/aarch64/` and rebuilding.
  > 本分支运行期不联网下载依赖；如需安装仓库闭包之外的 Alpine 包，需自行放入 `assets/apks/aarch64/` 后重新构建。

---

### Minimum System Requirements / 最低系统要求
- Android 5.0 and up. / Android 5.0 及以上
- 3GB RAM (1GB of free RAM). / 3GB 内存（至少 1GB 可用内存）
- A good processor. / 性能良好的处理器

### Recommended System Requirements / 推荐系统要求
- Android 8.1 and up. / Android 8.1 及以上
- 8GB RAM (3GB of free RAM). / 8GB 内存（至少 3GB 可用内存）
- CPU and Android OS support 64-bit. / CPU 与 Android 系统均支持 64 位
- Snapdragon 855 CPU or better. / 骁龙 855 或更高性能处理器
- Integrated or removable cooling system (if running operating systems from 2010 to present). / 集成或可拆卸散热系统（如运行 2010 年至今的操作系统）

> [!TIP]
> If the OS you are trying to emulate crashes, try using an older version.
>
> **提示**：如果你尝试模拟的操作系统出现崩溃，请尝试使用更早的版本。

---

## Donate / 捐赠
Help support the project by contributing!

> 通过捐赠支持本项目的发展！

[![Buy Me A Coffee][ico-buymeacoffee]][link-buymeacoffee]
[![Buy Me a Coffee at ko-fi.com][ico-ko-fi]][link-ko-fi]
[![Support me on Patreon](https://img.shields.io/endpoint.svg?url=https%3A%2F%2Fshieldsio-patreon.vercel.app%2Fapi%3Fusername%3Dendel%26type%3Dpatrons&style=flat)](https://patreon.com/VectrasTeam)

---

## Version Comparison / 版本对比

This project is based on **Vectras VM v2.9.5-3dfx** (`versionCode 21`).

> 本项目基于 **Vectras VM v2.9.5-3dfx**（`versionCode 21`）。

Compared to the newer **v4.4.x** branch (`versionCode 156`), the following differences exist:

> 与较新的 **v4.4.x** 分支（`versionCode 156`）相比，存在以下差异：

| Feature / 特性 | v2.9.5 (This Branch) | v4.4.x |
|---|---|---|
| **Minimum Android / 最低 Android 版本** | Android 5.0 | Android 6.0 |
| **Compile SDK / 编译 SDK** | 34 | 37 |
| **Java / JDK** | 11 | 21 |
| **NDK & CMake / 原生构建** | Not included / 不包含 | Included (`cpu-info.cpp`, `gpu_info.cpp`, `termux.c`) / 包含 |
| **Native CPU/GPU info / 原生 CPU/GPU 信息** | Not available / 不可用 | JNI via Vulkan, detect Adreno / 通过 Vulkan 枚举，识别 Adreno |
| **X11 Display modes / X11 显示模式** | Basic X11 via Termux-X11 / 基础 X11 | X11 / SDL / OpenGL / Bubble selectable / 多显示模式可选 |
| **QEMU Params Editor / QEMU 参数编辑器** | Not available / 不可用 | `QemuParamsEditorActivity` / 可编辑保存启动参数 |
| **VM File Manager / 虚拟机文件管理** | Not available / 不可用 | `VmFileManager` (ROM, snapshot, log, etc.) / 支持 ROM、快照、日志等 |
| **3DFX Wrappers / 3DFX 包装器** | v2.9.5 ISO only / 仅 v2.9.5 ISO | Added `4.1.1+` ISO / 新增 4.1.1+ ISO |
| **Dependency management / 依赖管理** | Direct declarations / 直接声明 | Version catalog (`libs.`) / 版本目录 |
| **Firebase Messaging / 消息推送** | Not included / 不包含 | Included / 包含 |
| **OSS Licenses / 开源许可页** | Not included / 不包含 | Included / 包含 |
| **Play Store Native QEMU / Play Store 原生 QEMU** | Proot-based / 基于 Proot | Native QEMU 10, no Proot required / QEMU 10 原生运行 |
| **Build Tools / 构建工具** | AGP 8.1.2 | AGP with foojay-resolver, NDK 27 / 含工具链解析与 NDK 27 |

**Recommendation / 建议**
- Use **this branch (v2.9.5)** if you need a lighter build or are targeting older Android devices (Android 5.0+).
- Use the **v4.4.x** branch if you need X11 multi-display modes, native CPU/GPU detection, QEMU parameter editing, or Play Store native QEMU 10 features.

> **建议**
> - 如果你需要一个更轻量的构建，或者需要兼容较旧的 Android 设备（Android 5.0+），请使用 **本分支（v2.9.5）**。
> - 如果你需要 X11 多显示模式、原生 CPU/GPU 检测、QEMU 参数编辑或 Play Store 原生 QEMU 10 功能，请使用 **v4.4.x** 分支。

---

## Thanks to / 致谢
- [QEMU](https://github.com/qemu/qemu)
- [3DFX QEMU PATCH](https://github.com/kjliew/qemu-3dfx)
- [PROOT](https://proot-me.github.io/)
- [Alpine Linux](https://www.alpinelinux.org/)

---

[ico-telegram]: https://img.shields.io/badge/Telegram-2CA5E0?style=flat-square&logo=telegram&logoColor=white
[ico-version]: https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white
[ico-license]: https://img.shields.io/badge/License-GPL_v2-blue.svg
[ico-buymeacoffee]: https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?&logo=buy-me-a-coffee&logoColor=black
[ico-ko-fi]: https://img.shields.io/badge/Ko--fi-FF5E5B?logo=ko-fi&logoColor=white

[link-telegram]: https://t.me/vectras_os
[link-repo]: https://github.com/xoureldeen/Vectras-VM-Android/
[link-releases]: https://github.com/xoureldeen/Vectras-VM-Android/releases/
[link-buymeacoffee]: https://www.buymeacoffee.com/vectrasvm
[link-ko-fi]: https://ko-fi.com/vectrasvm
