#!/bin/bash
# 构建 arm64 Ubuntu(XFCE) rootfs，打包为 tar.gz（.bin 扩展名，与 Alpine 版一致）。
#
# 默认产物：rootfs-build-ubuntu/rootfs-ubuntu.bin（不进 assets/，避免 APK 同时打进两套 rootfs）。
# 想做 Ubuntu 版 APK 时，再用产物覆盖 app/src/main/assets/rootfs.bin 即可：
#   ./scripts/build-rootfs-ubuntu.sh app/src/main/assets/rootfs.bin
#
# 基础镜像默认本地 ubuntu:resolute-20260927，可用环境变量覆盖：
#   BASE_IMAGE=ubuntu:26.04 ./scripts/build-rootfs-ubuntu.sh
set -e
cd "$(dirname "$0")/.."

OUT="${1:-rootfs-build-ubuntu/rootfs-ubuntu.bin}"
BASE_IMAGE="${BASE_IMAGE:-ubuntu:resolute-20260927}"
mkdir -p "$(dirname "$OUT")"

# 权威校验：镜像必须真实存在于本地（退出码/截断输出都不可信）
if ! docker image inspect "$BASE_IMAGE" >/dev/null 2>&1; then
    echo "ERROR: 本地不存在基础镜像 $BASE_IMAGE，先 docker pull 或用 BASE_IMAGE=... 指定" >&2
    docker images --format '  {{.Repository}}:{{.Tag}}' | grep -i ubuntu >&2 || true
    exit 1
fi
echo ">> 使用基础镜像: $BASE_IMAGE ($(docker image inspect "$BASE_IMAGE" --format '{{.Architecture}}'))"

docker build --build-arg "BASE_IMAGE=$BASE_IMAGE" \
       -t ubuntu-xfce-rootfs:latest rootfs-build-ubuntu

# 用 docker export 导出合并后的完整根文件系统（与 Alpine 版同一方案）。
cid=$(docker create ubuntu-xfce-rootfs:latest)
trap 'docker rm -f "$cid" >/dev/null 2>&1' EXIT
docker export "$cid" | gzip -1 > "$OUT"

echo "OK: $OUT ($(du -h "$OUT" | cut -f1))"
