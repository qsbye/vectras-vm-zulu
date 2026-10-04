#!/bin/bash

# Build signed APK and copy to project root with timestamp suffix
# Usage: ./build-with-timestamp.sh [debug|release]

set -e

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="VectrasVM"
BUILD_TYPE="${1:-release}"

# Validate build type
if [[ "$BUILD_TYPE" != "debug" && "$BUILD_TYPE" != "release" ]]; then
    echo "Error: Build type must be 'debug' or 'release'"
    echo "Usage: $0 [debug|release]"
    exit 1
fi

echo "=========================================="
echo "Building $APP_NAME ($BUILD_TYPE)"
echo "=========================================="

# Navigate to project root
cd "$PROJECT_ROOT"

# Clean and build
echo "Cleaning previous builds..."
./gradlew clean

echo "Building APK..."
if [[ "$BUILD_TYPE" == "release" ]]; then
    ./gradlew assembleRelease
else
    ./gradlew assembleDebug
fi

# Find the built APK
APK_SOURCE=$(find "$PROJECT_ROOT/app/build/outputs/apk" -name "*.apk" -type f | head -n 1)

if [[ ! -f "$APK_SOURCE" ]]; then
    echo "Error: APK not found after build!"
    exit 1
fi

echo "Found APK: $APK_SOURCE"

# Generate timestamp suffix
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Create destination filename
APK_FILENAME="${APP_NAME}-${BUILD_TYPE}-${TIMESTAMP}.apk"
APK_DEST="$PROJECT_ROOT/$APK_FILENAME"

# Copy APK to project root with timestamp
echo "Copying APK to project root..."
cp "$APK_SOURCE" "$APK_DEST"

# Verify copy
if [[ -f "$APK_DEST" ]]; then
    APK_SIZE=$(du -h "$APK_DEST" | cut -f1)
    echo "=========================================="
    echo "Build completed successfully!"
    echo "APK: $APK_DEST"
    echo "Size: $APK_SIZE"
    echo "=========================================="
else
    echo "Error: Failed to copy APK to project root!"
    exit 1
fi
