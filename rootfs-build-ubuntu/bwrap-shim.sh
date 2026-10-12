#!/bin/sh
# bwrap 直通垫片（Android proot 专用）
#
# 背景：gtk3 ≥ 3.24.50 的 SVG 图标加载改走 glycin，glycin 固定用 bwrap
# 沙箱拉起 loader 进程；Android/proot 无 user namespace（SELinux 禁止），
# bwrap --unshare-all 必败 → SVG 图标加载失败 → gtkiconhelper 断言崩溃
# （xfce4-panel 启动即 Aborted）。
#
# proot 环境本无沙箱需求，故把 bwrap 换成此直通垫片：
# 解析 bwrap 命令行，导出 --setenv 变量，忽略全部隔离选项，exec 真正的命令。
# --dbus-fd 等 fd 不带 CLOEXEC，直接继承可用。
#
# 真身保留在 /usr/bin/bwrap.real。
while [ $# -gt 0 ]; do
  case "$1" in
    --setenv) export "$2=$3"; shift 3;;
    # 双值选项
    --ro-bind|--ro-bind-try|--bind-try|--bind|--dev-bind|--dev-bind-try|--symlink|--remount-ro|--ro-bind-data|--bind-data) shift 3;;
    # 单值选项
    --tmpfs|--chdir|--seccomp|--dev|--proc|--dir|--perms|--size|--mode|--uid|--gid|--hostname|--unshare|--sync-fd|--info-fd|--json-status-fd|--block-fd|--userns-fd|--level-prefix|--cap-add|--cap-drop) shift 2;;
    # 无值选项及未知选项
    --*) shift;;
    # 第一个非选项参数 = 命令起点
    *) break;;
  esac
done
exec "$@"
