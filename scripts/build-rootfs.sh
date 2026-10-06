#!/bin/bash
# 构建 arm64 Alpine(XFCE) rootfs，打包为 app/src/main/assets/rootfs.bin（即 tar.gz）
# 用 .bin 扩展名避开 aapt 对 .gz 资产的改名/解压特殊处理
set -e
cd "$(dirname "$0")/.."

ASSETS="app/src/main/assets"
mkdir -p "$ASSETS"

docker build -t alpine-xfce-rootfs:latest rootfs-build

# 用 docker export 导出合并后的完整根文件系统。
# 不能在容器内用 busybox tar：它处理 Alpine 的 /bin -> usr/bin 合并目录有 bug，
# 会丢失大量 busybox 小工具（rm/ls/mkdir 等）。docker export 保留正确的符号链接。
cid=$(docker create alpine-xfce-rootfs:latest)
trap 'docker rm -f "$cid" >/dev/null 2>&1' EXIT
docker export "$cid" | gzip -1 > "$ASSETS/rootfs.bin"

echo "OK: $ASSETS/rootfs.bin ($(du -h "$ASSETS/rootfs.bin" | cut -f1))"
