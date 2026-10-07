FROM alpine:3.20

ENV LANG=C.UTF-8

# XFCE4 + TigerVNC(免密) + noVNC/websockify + 中文字体
RUN apk add --no-cache \
      xfce4 xfce4-terminal xfce4-screenshooter mousepad \
      dbus dbus-x11 sudo coreutils procps \
      tigervnc novnc websockify \
      xkeyboard-config xkbcomp xauth \
      ttf-dejavu font-noto font-noto-cjk adwaita-icon-theme \
      gcompat openssh \
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

# LocalSend CLI（glibc 动态链接，依赖上面的 gcompat）
COPY localsend-cli /home/qsbye/localsend-cli
RUN chmod 755 /home/qsbye/localsend-cli \
 && chown qsbye:qsbye /home/qsbye/localsend-cli

# musl 缺 glibc 私有的 __res_init 符号（glibc≥2.34 将其并入 libc.so.6），
# 该二进制在加载期引用它；用 LD_PRELOAD 空实现桩补齐。
# 包装器放 /usr/bin（sshd 默认 PATH 可达），任何会话都能直接跑 localsend-cli
COPY libres_init_stub.so /usr/local/lib/libres_init_stub.so
RUN chmod 755 /usr/local/lib/libres_init_stub.so \
 && printf '#!/bin/sh\nexport LD_PRELOAD=/usr/local/lib/libres_init_stub.so\nexec /home/qsbye/localsend-cli "$@"\n' \
      > /usr/bin/localsend-cli \
 && chmod 755 /usr/bin/localsend-cli

CMD ["/bin/sh"]
