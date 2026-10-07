#!/bin/sh
# proot 入口：由 Android 端调用
# Android 的 proot 无真实多用户，会话统一以"root"身份运行（fake_id0），
# 但 HOME 指向 qsbye 的家目录；qsbye 账户仍可在终端内登录（密码 qsbye）。
# 环境变量 GEOMETRY=WxH 由 App 按屏幕物理尺寸与配置朝向（横/竖屏）计算传入
GEOMETRY="${GEOMETRY:-1280x800}"
# 固定客户机 PATH，避免 Android 宿主机 PATH 经 proot 泄漏导致找不到命令
# 末尾追加 /home/qsbye，方便直接运行 localsend-cli 等放在家目录的程序
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/home/qsbye
export LANG=C.UTF-8
export HOME=/home/qsbye
export USER=qsbye
export LOGNAME=qsbye
export XDG_RUNTIME_DIR=/tmp/runtime-qsbye
export XDG_CACHE_HOME=/home/qsbye/.cache
export XDG_CONFIG_HOME=/home/qsbye/.config

# ---- X11 / IPC / 运行时目录 ----
mkdir -p /tmp/.X11-unix /tmp/.ICE-unix /run/dbus "$XDG_RUNTIME_DIR"
chmod 1777 /tmp/.X11-unix /tmp/.ICE-unix
chmod 0700 "$XDG_RUNTIME_DIR"
chown -R qsbye:qsbye /home/qsbye 2>/dev/null || true

# ---- D-Bus ----
# 注意：在 Android app 进程内，proot 无法可靠跟踪守护进程双重 fork 出的孙进程，
# 因此系统/会话总线一律 --nofork 前台运行再由 shell 放到后台，
# 不使用 dbus-launch / --fork。
rm -f /tmp/.dbus-session-addr /tmp/dbus-system.log /tmp/dbus-session.log
dbus-daemon --system --nofork --nopidfile \
    >/tmp/dbus-system.log 2>&1 &
dbus-daemon --session --nofork --nopidfile --print-address=1 \
    >/tmp/.dbus-session-addr 2>/tmp/dbus-session.log &
i=0
while [ ! -s /tmp/.dbus-session-addr ] && [ $i -lt 100 ]; do
    i=$((i + 1)); sleep 0.1
done
export DBUS_SESSION_BUS_ADDRESS="$(cat /tmp/.dbus-session-addr 2>/dev/null)"
export DBUS_SYSTEM_BUS_ADDRESS=unix:path=/run/dbus/system_bus_socket
export DISPLAY=:0

# ---- 启动 TigerVNC：root 运行（-nolock 要求 uid 0），RFB 无密码、监听 0.0.0.0 ----
# -ac 关闭 X 协议访问控制；-nolock 绕开 proot 下 lock 文件 link() 失败
# 注意：不带 -localhost，5900 对局域网可见且无任何认证，仅建议在可信网络使用
rm -f /tmp/.X0-lock /tmp/.X11-unix/X0
Xvnc :0 -geometry "${GEOMETRY}" -depth 24 -rfbport 5900 -SecurityTypes None \
  -AlwaysShared -nolock -ac -desktop Alpine-XFCE \
  -AcceptKeyEvents -AcceptPointerEvents -AcceptCutText -SendCutText \
  > /tmp/vnc.log 2>&1 &

# 等待 X socket
i=0
while [ ! -e /tmp/.X11-unix/X0 ] && [ $i -lt 100 ]; do
    i=$((i + 1)); sleep 0.1
done
if [ ! -e /tmp/.X11-unix/X0 ]; then
    echo "Xvnc failed to start:"
    cat /tmp/vnc.log
    exit 1
fi

# ---- 启动 XFCE 桌面会话 ----
startxfce4 > /tmp/xfce.log 2>&1 &

# ---- noVNC：把 5900 的 VNC 转成 6080 的 WebSocket ----
# 不用 -D（双重 fork+chdir 的孙进程在 proot 下会异常），普通后台运行，
# sleep infinity 会保持整个会话存活
websockify --web=/usr/share/novnc 6080 127.0.0.1:5900 > /tmp/websockify.log 2>&1 &

# ---- OpenSSH：局域网 ssh 接入（端口 8022，Android 应用无权绑定 <1024 端口）----
# -D 前台运行（守护进程双重 fork 的孙进程会脱离 proot 跟踪），由 shell 放后台
mkdir -p /run/sshd
/usr/sbin/sshd -D -p 8022 > /tmp/sshd.log 2>&1 &

echo "=== Alpine XFCE desktop ready on 127.0.0.1:6080 ==="

# 保持 proot 会话存活
exec sleep infinity
