#!/usr/bin/env bash
# HugoTV 构建脚本（Linux / macOS / CI）
# 用法: bash build.sh
# 产物: dist/HugoTV-0.1.0.apk（已签名）
set -euo pipefail

VERSION="${VERSION:-0.1.0}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$ROOT/tools"
OBJ="$ROOT/obj"
DIST="$ROOT/dist"
mkdir -p "$TOOLS" "$OBJ" "$DIST"

# ---------- 1. 定位 JDK（CI 用 setup-java，本地需 JAVA_HOME） ----------
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/javac" ]]; then
  echo "请设置 JAVA_HOME（JDK 17）"; exit 1
fi
JAVA_BIN="$JAVA_HOME/bin"

# ---------- 2. 定位 / 下载 Android 构建工具 ----------
ANDROID_JAR="$(find "$TOOLS" -maxdepth 2 -name android.jar -type f | head -1)"
if [[ -z "$ANDROID_JAR" ]]; then
  echo "[download] platform-33 ..."
  curl -fsSL --connect-timeout 20 --max-time 900 -o "$TOOLS/platform33.zip" \
    "https://dl.google.com/android/repository/platform-33_r02.zip"
  unzip -q -o "$TOOLS/platform33.zip" -d "$TOOLS"
  ANDROID_JAR="$(find "$TOOLS" -maxdepth 2 -name android.jar -type f | head -1)"
fi

BT="$TOOLS/build-tools"
if [[ ! -x "$BT/aapt2" ]]; then
  echo "[download] build-tools r34 (linux) ..."
  curl -fsSL --connect-timeout 20 --max-time 900 -o "$TOOLS/bt34.zip" \
    "https://dl.google.com/android/repository/build-tools_r34-linux.zip"
  unzip -q -o "$TOOLS/bt34.zip" -d "$TOOLS"
  BT_SRC="$(dirname "$(find "$TOOLS" -maxdepth 2 -name aapt2 -type f | head -1)")"
  mv "$BT_SRC" "$BT"
fi
chmod +x "$BT"/aapt2 "$BT"/zipalign 2>/dev/null || true

# ---------- 3. 资源 ----------
"$BT/aapt2" compile --dir "$ROOT/res" -o "$OBJ/res.flata"
"$BT/aapt2" link -I "$ANDROID_JAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --java "$OBJ/java" \
  --auto-add-overlay \
  -o "$OBJ/base.apk" "$OBJ/res.flata"

# ---------- 4. Java ----------
"$JAVA_BIN/javac" -encoding UTF-8 -source 8 -target 8 \
  -cp "$ANDROID_JAR" -d "$OBJ/classes" \
  "$OBJ/java"/cn/hugotv/launcher/R.java "$ROOT"/src/cn/hugotv/launcher/*.java
mkdir -p "$OBJ/dex"
mapfile -d '' CLASSES < <(find "$OBJ/classes" -name '*.class' -print0)
"$JAVA_BIN/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --release --lib "$ANDROID_JAR" --min-api 21 --output "$OBJ/dex" "${CLASSES[@]}"

# ---------- 5. 打包 ----------
python3 - "$OBJ/base.apk" "$OBJ/dex/classes.dex" "$OBJ/withdex.apk" <<'PY'
import sys, zipfile
src, dex, dst = sys.argv[1:4]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
    for i in zin.infolist():
        zout.writestr(i, zin.read(i.filename))
    zout.write(dex, "classes.dex")
PY

"$BT/zipalign" -f 4 "$OBJ/withdex.apk" "$OBJ/aligned.apk"

# ---------- 6. 签名（CI 每次生成临时密钥，未入库） ----------
KS="$TOOLS/hugotv.keystore"
if [[ ! -f "$KS" ]]; then
  "$JAVA_BIN/keytool" -genkeypair -keystore "$KS" -alias hugotv \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass hugotv123 -keypass hugotv123 \
    -dname "CN=HugoTV,O=hugotv,C=CN" -noprompt
fi
"$JAVA_BIN/java" -jar "$BT/lib/apksigner.jar" sign \
  --ks "$KS" --ks-pass pass:hugotv123 --key-pass pass:hugotv123 \
  --out "$DIST/HugoTV-$VERSION.apk" "$OBJ/aligned.apk"

echo "构建完成: $DIST/HugoTV-$VERSION.apk"
