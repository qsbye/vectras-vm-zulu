FROM alpine:3.20

ENV LANG=C.UTF-8

# XFCE4 + TigerVNC(免密) + noVNC/websockify + 中文字体
RUN apk add --no-cache \
      xfce4 xfce4-terminal xfce4-screenshooter mousepad \
      dbus dbus-x11 sudo coreutils procps \
      tigervnc novnc websockify \
      xkeyboard-config xkbcomp xauth \
      ttf-dejavu font-noto font-noto-cjk adwaita-icon-theme \
      gcompat openssh curl bash \
 && rm -rf /var/cache/apk/* /tmp/* /usr/share/man/* /usr/share/doc/* /var/log/*

# 默认账户 qsbye / qsbye，sudo 免密；预生成 SSH host keys 避免首启 keygen 耗时
RUN adduser -D -s /bin/sh qsbye \
 && echo 'qsbye:qsbye' | chpasswd \
 && echo 'qsbye ALL=(ALL) NOPASSWD: ALL' > /etc/sudoers.d/qsbye \
 && mkdir -p /home/qsbye/.vnc /home/qsbye/Desktop \
 && chown -R qsbye:qsbye /home/qsbye \
 && ssh-keygen -A

COPY desktop-start.sh /usr/local/bin/desktop-start.sh
RUN chmod 755 /usr/local/bin/desktop-start.sh

# 覆盖官方 vnc_lite.html：补 viewport meta（WebView 以 DIP/CSS 像素布局）
# 并支持 resize=remote（帧缓冲跟随页面容器实际大小），修复桌面只显示上半部分
COPY vnc_lite.html /usr/share/novnc/vnc_lite.html

# LocalSend CLI（glibc 动态链接，依赖上面的 gcompat）
COPY localsend-cli /home/qsbye/localsend-cli
RUN chmod 755 /home/qsbye/localsend-cli \
 && chown qsbye:qsbye /home/qsbye/localsend-cli

# musl 缺 glibc 私有的 __res_init 符号（glibc≥2.34 将其并入 libc.so.6），
# 且 Android 禁止 app 域 bind netlink 路由套接字（getifaddrs 走 netlink 必失败），
# 用 LD_PRELOAD 垫片补齐：__res_init 空实现 + ioctl 版 getifaddrs。
# 包装器放 /usr/bin（sshd 默认 PATH 可达），任何会话都能直接跑 localsend-cli
COPY localsend_compat.so /usr/local/lib/localsend_compat.so
RUN chmod 755 /usr/local/lib/localsend_compat.so \
 && printf '#!/bin/sh\nexport LD_PRELOAD=/usr/local/lib/localsend_compat.so\nexec /home/qsbye/localsend-cli "$@"\n' \
      > /usr/bin/localsend-cli \
 && chmod 755 /usr/bin/localsend-cli

# OpenCode（终端内运行的 AI 编程助手，https://opencode.ai）
# 官方安装器能识别 Alpine（/etc/alpine-release），自动下载 linux-arm64-musl 包，
# 装到 $HOME/.opencode/bin。以 qsbye 家目录为 HOME 执行；--no-modify-path 避免改 rc 文件。
# 再在 /usr/bin 放符号链接，保证桌面会话与 ssh 非登录命令（默认 PATH 可能只有 /usr/bin）都能直接运行。
RUN curl -fsSL https://opencode.ai/v2/install \
      | HOME=/home/qsbye bash -s -- --no-modify-path \
 && ln -sf /home/qsbye/.opencode/bin/opencode /usr/bin/opencode \
 && chown -R qsbye:qsbye /home/qsbye/.opencode

CMD ["/bin/sh"]
