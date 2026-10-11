# Ubuntu XFCE rootfs（Android proot 用，arm64）
# 用法：默认基于本地已拉取的 ubuntu:resolute-20260927；
#       如需换源可用 --build-arg BASE_IMAGE=<镜像> 覆盖。
ARG BASE_IMAGE=ubuntu:resolute-20260927

# ---- 阶段 1：编译 localsend 的 getifaddrs 垫片（最终镜像不带 gcc）----
FROM ${BASE_IMAGE} AS shim-builder
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
 && apt-get install -y --no-install-recommends gcc libc6-dev \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY localsend_compat.c /src/localsend_compat.c
RUN gcc -shared -fPIC -O2 -Wall -o /localsend_compat.so localsend_compat.c

# ---- 阶段 2：桌面 rootfs ----
FROM ${BASE_IMAGE}

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8

# XFCE4 + TigerVNC(免密) + noVNC/websockify + 中文字体
# --no-install-recommends 控制体积；xfce4 元包的硬依赖已含 thunar/panel/session/wm4 等，
# 显式补回需要的 recommends（desktop-base 主题、xfce4-notifyd 通知、xfonts 字体、mesa dri）
RUN sed -i 's/^hosts:.*/hosts: files dns/' /etc/nsswitch.conf \
 && apt-get update \
 && apt-get install -y --no-install-recommends \
      xfce4 xfce4-terminal xfce4-screenshooter mousepad \
      desktop-base xfce4-notifyd \
      dbus dbus-x11 sudo coreutils procps \
      tigervnc-standalone-server tigervnc-common \
      xfonts-base libgl1-mesa-dri \
      novnc websockify \
      xauth x11-xkb-utils \
      fonts-dejavu fonts-noto fonts-noto-cjk adwaita-icon-theme \
      openssh-server curl ca-certificates unzip bash \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/* /var/cache/apt/* /tmp/* /var/tmp/* \
           /usr/share/doc/* /usr/share/man/* /var/log/*.log /var/log/apt/*

# D-Bus 系统总线需要非空 machine-id（容器里通常是空的）
RUN rm -f /etc/machine-id /var/lib/dbus/machine-id \
 && dbus-uuidgen --ensure=/etc/machine-id \
 && ln -sf /etc/machine-id /var/lib/dbus/machine-id

# 默认账户 qsbye / qsbye，sudo 免密；预生成 SSH host keys 避免首启 keygen 耗时
RUN useradd -m -s /bin/bash -G sudo qsbye \
 && echo 'qsbye:qsbye' | chpasswd \
 && echo 'qsbye ALL=(ALL) NOPASSWD: ALL' > /etc/sudoers.d/qsbye \
 && mkdir -p /home/qsbye/.vnc /home/qsbye/Desktop \
 && ssh-keygen -A

# proot 下 sshd：密码认证开启、关闭 PAM（无 systemd/loginuid，PAM 会话会报错）；
# 监听端口由启动参数 -p 8022 指定（Android 应用无权绑定 22）
RUN printf 'PasswordAuthentication yes\nUsePAM no\n' \
      > /etc/ssh/sshd_config.d/99-proot.conf

# ---- DNS 固化 ----
# Docker 构建期 /etc/resolv.conf 是被守护进程屏蔽的特殊路径（COPY/RUN 写入都不进镜像层，
# docker export 拿到的仍是基础镜像里的 0 字节空文件，进 Android proot 后必然 bad address）。
# 因此预置配置放在非屏蔽路径，由 desktop-start.sh 在 proot 入口首启时装入 /etc/resolv.conf。
COPY resolv.conf /usr/local/share/resolv.conf.default
COPY fix-dns.sh /usr/local/bin/fix-dns.sh
RUN chmod 644 /usr/local/share/resolv.conf.default \
 && chmod 755 /usr/local/bin/fix-dns.sh

COPY desktop-start.sh /usr/local/bin/desktop-start.sh
RUN chmod 755 /usr/local/bin/desktop-start.sh

# 覆盖官方 vnc_lite.html：补 viewport meta（WebView 以 DIP/CSS 像素布局）
# 并支持 resize=remote（帧缓冲跟随页面容器实际大小），修复桌面只显示上半部分
COPY vnc_lite.html /usr/share/novnc/vnc_lite.html

# 与 Alpine 版一致的无 pager 面板配置（规避 libwnck pager 插件启动竞态崩溃）：
# 系统级默认 + qsbye 家目录各放一份
COPY xfce4-panel.xml /etc/xdg/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml
RUN mkdir -p /home/qsbye/.config/xfce4/xfconf/xfce-perchannel-xml \
 && cp /etc/xdg/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml \
       /home/qsbye/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml

# xfdesktop 默认背景路径 /usr/share/xfce4/backdrops/xubuntu-wallpaper.png 在
# 非 xubuntu 系统不存在，会刷 "Failed to load image" 并退回纯色；链到自带壁纸
RUN mkdir -p /usr/share/xfce4/backdrops \
 && ln -sf /usr/share/backgrounds/xfce/xfce-blue.jpg \
           /usr/share/xfce4/backdrops/xubuntu-wallpaper.png

# DNS 一键修复桌面快捷方式（幂等，可反复运行）
COPY fix-dns.desktop /home/qsbye/Desktop/fix-dns.desktop
RUN chmod 755 /home/qsbye/Desktop/fix-dns.desktop

# LocalSend CLI（arm64 glibc，Ubuntu 原生可跑；Android proot 仍禁 netlink，
# 需 LD_PRELOAD 垫片把 getifaddrs 改为 ioctl 实现。glibc 自带 __res_init，
# 垫片只覆盖 getifaddrs，与 musl 版不同）
COPY localsend-cli /home/qsbye/localsend-cli
COPY --from=shim-builder /localsend_compat.so /usr/local/lib/localsend_compat.so
RUN chmod 755 /home/qsbye/localsend-cli /usr/local/lib/localsend_compat.so \
 && printf '#!/bin/sh\nexport LD_PRELOAD=/usr/local/lib/localsend_compat.so${LD_PRELOAD:+:$LD_PRELOAD}\nexec /home/qsbye/localsend-cli "$@"\n' \
      > /usr/bin/localsend-cli \
 && chmod 755 /usr/bin/localsend-cli

# OpenCode（终端内运行的 AI 编程助手，https://opencode.ai）
# 官方安装器识别 Ubuntu（/etc/os-release），自动下载 linux-arm64-gnu 包，
# 装到 $HOME/.opencode/bin。以 qsbye 家目录为 HOME 执行；--no-modify-path 避免改 rc 文件。
# 再在 /usr/bin 放符号链接，保证桌面会话与 ssh 非登录命令（默认 PATH 可能只有 /usr/bin）都能直接运行。
RUN curl -fsSL https://opencode.ai/v2/install \
      | HOME=/home/qsbye bash -s -- --no-modify-path \
 && ln -sf /home/qsbye/.opencode/bin/opencode /usr/bin/opencode \
 && chown -R qsbye:qsbye /home/qsbye

CMD ["/bin/bash"]
