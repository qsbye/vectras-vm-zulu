#!/bin/sh
# 在 arm64 Alpine 容器内执行：静态编译 proot 5.4.0（Alpine 官方同款补丁，含 clone3 适配）
set -e

apk add --no-cache build-base bsd-compat-headers libarchive-dev linux-headers \
    talloc talloc-dev talloc-static uthash-dev pkgconf curl

WORK=/tmp/proot-build
OUT=/out
rm -rf "$WORK"
mkdir -p "$WORK" "$OUT"
cd "$WORK"

curl -fsSL https://github.com/proot-me/proot/archive/refs/tags/v5.4.0.tar.gz -o proot.tar.gz
mkdir src && tar xzf proot.tar.gz -C src --strip-components=1
cd src

base="https://gitlab.alpinelinux.org/alpine/aports/-/raw/master/community/proot"
for p in fix-basename.patch tests-musl-compat.patch clone3.patch; do
    curl -fsSL "$base/$p" -o "/tmp/$p"
    patch -p1 -i "/tmp/$p"
done

# loader 以 objcopy 内嵌进二进制，最终只需要单个 proot
make -C src proot CC=cc V=1 VERSION=5.4.0 LDFLAGS="-static -ltalloc"
strip src/proot
cp src/proot "$OUT/proot"
echo "BUILD-OK -> $OUT/proot"
