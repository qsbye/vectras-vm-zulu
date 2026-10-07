FROM alpine:3.20

ENV LANG=C.UTF-8

# XFCE4 + TigerVNC(免密) + noVNC/websockify + 中文字体
RUN apk add --no-cache \
      xfce4 xfce4-terminal xfce4-screenshooter mousepad \
      dbus dbus-x11 sudo coreutils procps \
      tigervnc novnc websockify \
      xkeyboard-config xkbcomp xauth \
      ttf-dejavu font-noto font-noto-cjk adwaita-icon-theme \
      gcompat \
 && rm -rf /var/cache/apk/* /tmp/* /usr/share/man/* /usr/share/doc/* /var/log/*

# 默认账户 qsbye / qsbye，sudo 免密
RUN adduser -D -s /bin/sh qsbye \
 && echo 'qsbye:qsbye' | chpasswd \
 && echo 'qsbye ALL=(ALL) NOPASSWD: ALL' > /etc/sudoers.d/qsbye \
 && mkdir -p /home/qsbye/.vnc /home/qsbye/Desktop \
 && chown -R qsbye:qsbye /home/qsbye

COPY desktop-start.sh /usr/local/bin/desktop-start.sh
RUN chmod 755 /usr/local/bin/desktop-start.sh

# LocalSend CLI（glibc 动态链接，依赖上面的 gcompat）
COPY localsend-cli /home/qsbye/localsend-cli
RUN chmod 755 /home/qsbye/localsend-cli \
 && chown qsbye:qsbye /home/qsbye/localsend-cli

CMD ["/bin/sh"]
