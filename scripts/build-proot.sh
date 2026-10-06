#!/bin/bash
# 编译静态 proot，输出到 app/src/main/jniLibs/arm64-v8a/libproot.so
# 以 .so 命名是为了让系统把可执行文件释放到 nativeLibraryDir（可执行、应用可写目录外）
set -e
cd "$(dirname "$0")/.."

OUT="app/src/main/jniLibs/arm64-v8a"
mkdir -p "$OUT"

docker run --rm \
    -v "$PWD/scripts/proot-build:/buildctx:ro" \
    -v "$PWD/$OUT:/out" \
    alpine:3.20 sh /buildctx/build.sh

mv "$OUT/proot" "$OUT/libproot.so"
echo "OK: $OUT/libproot.so ($(du -h "$OUT/libproot.so" | cut -f1))"
