#!/bin/sh
# 一键：构建 release → zipalign → apksigner(debug 证书后签) → 时间戳命名归档到项目根目录
# 用法: ./build-with-timestamp.sh
set -e
cd "$(dirname "$0")"

TS=$(date +%Y%m%d_%H%M%S)
RAW=app/build/outputs/apk/release/app-release.apk
ALIGNED="app-release-aligned_${TS}.apk"
OUT="alpine-desktop-release-signed_${TS}.apk"
BT="$HOME/Library/Android/sdk/build-tools/35.0.0"
export JAVA_HOME=/Users/workspace/Library/Java/JavaVirtualMachines/jbr-17.0.8.1/Contents/Home

echo "[1/4] 构建 release APK ..."
./gradlew :app:assembleRelease

echo "[2/4] zipalign 对齐 ..."
"$BT/zipalign" -f -p 4 "$RAW" "$ALIGNED"

echo "[3/4] apksigner 签名（debug keystore，v2/v3）..."
"$BT/apksigner" sign --ks ~/.android/debug.keystore \
  --ks-pass pass:android --key-pass pass:android \
  --out "$OUT" "$ALIGNED"
rm -f "$ALIGNED"

echo "[4/4] 校验签名并输出摘要 ..."
"$BT/apksigner" verify --print-certs "$OUT" | head -3
ls -l "$OUT"
shasum -a 256 "$OUT"
echo "==> 完成: $OUT"
