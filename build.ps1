# HugoTV build script (Windows PowerShell)
# Usage: powershell -ExecutionPolicy Bypass -File build.ps1
# Output: dist\HugoTV-0.1.0.apk (signed, installable)

param(
    [string]$Version = "0.1.0"
)
$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$Tools = Join-Path $Root "tools"
$Obj   = Join-Path $Root "obj"
$Dist  = Join-Path $Root "dist"
New-Item -ItemType Directory -Force -Path $Tools, $Obj, $Dist | Out-Null

# ---------- 1. locate / download JDK ----------
function Get-Jdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
        return $env:JAVA_HOME
    }
    $cand = Join-Path $Tools "jdk"
    if (Test-Path (Join-Path $cand "bin\javac.exe")) { return $cand }
    Write-Host "[1/6] prepare JDK 17 ..."
    $zip = Join-Path $Tools "jdk17.zip"
    if (-not (Test-Path $zip)) {
        curl.exe -fL --connect-timeout 20 --max-time 900 -o $zip "https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip"
        if ($LASTEXITCODE -ne 0) { throw "JDK download failed" }
    }
    Expand-Archive -Force $zip $Tools
    $inner = Get-ChildItem $Tools -Directory | Where-Object { $_.Name -like "jdk-17*" } | Select-Object -First 1
    if (-not $inner) { throw "JDK extract failed" }
    Move-Item -Force $inner.FullName $cand
    return $cand
}

# ---------- 2. locate / download Android platform + build-tools ----------
function Get-AndroidJar {
    $found = Get-ChildItem $Tools -Directory | Where-Object { Test-Path (Join-Path $_.FullName "android.jar") } | Select-Object -First 1
    if ($found) { return Join-Path $found.FullName "android.jar" }
    Write-Host "[2/6] prepare platform-33 ..."
    $zip = Join-Path $Tools "platform33.zip"
    if (-not (Test-Path $zip)) {
        curl.exe -fL --connect-timeout 20 --max-time 900 -o $zip "https://dl.google.com/android/repository/platform-33_r02.zip"
        if ($LASTEXITCODE -ne 0) { throw "platform download failed" }
    }
    Expand-Archive -Force $zip $Tools
    $found = Get-ChildItem $Tools -Directory | Where-Object { Test-Path (Join-Path $_.FullName "android.jar") } | Select-Object -First 1
    if (-not $found) { throw "platform extract failed" }
    return Join-Path $found.FullName "android.jar"
}

function Get-BuildTools {
    $cand = Join-Path $Tools "build-tools"
    if (Test-Path (Join-Path $cand "aapt2.exe")) { return $cand }
    Write-Host "[3/6] prepare build-tools 34 ..."
    $zip = Join-Path $Tools "buildtools34.zip"
    if (-not (Test-Path $zip)) {
        curl.exe -fL --connect-timeout 20 --max-time 900 -o $zip "https://dl.google.com/android/repository/build-tools_r34-windows.zip"
        if ($LASTEXITCODE -ne 0) { throw "build-tools download failed" }
    }
    Expand-Archive -Force $zip $Tools
    $inner = Get-ChildItem $Tools -Directory | Where-Object { Test-Path (Join-Path $_.FullName "aapt2.exe") } | Select-Object -First 1
    if (-not $inner) { throw "build-tools extract failed" }
    Move-Item -Force $inner.FullName $cand
    return $cand
}

$Jdk        = Get-Jdk
$AndroidJar = Get-AndroidJar
$Bt         = Get-BuildTools
$JavaBin    = Join-Path $Jdk "bin"
$java       = Join-Path $JavaBin "java.exe"
$javac      = Join-Path $JavaBin "javac.exe"
$keytool    = Join-Path $JavaBin "keytool.exe"
$aapt2      = Join-Path $Bt "aapt2.exe"
$zipalign   = Join-Path $Bt "zipalign.exe"
$d8jar      = Join-Path $Bt "lib\d8.jar"
$apksignerjar = Join-Path $Bt "lib\apksigner.jar"

# ---------- 3. resources ----------
Write-Host "[4/6] aapt2 ..."
& $aapt2 compile --dir (Join-Path $Root "res") -o (Join-Path $Obj "res.flata")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

& $aapt2 link -I $AndroidJar `
    --manifest (Join-Path $Root "AndroidManifest.xml") `
    --java (Join-Path $Obj "java") `
    --auto-add-overlay `
    -o (Join-Path $Obj "base.apk") (Join-Path $Obj "res.flata")
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# ---------- 4. java + dex ----------
Write-Host "[5/6] javac + d8 ..."
$Rjava = Get-ChildItem (Join-Path $Obj "java") -Recurse -Filter "*.java"
$Src   = Get-ChildItem (Join-Path $Root "src") -Recurse -Filter "*.java"
$All   = @($Rjava) + @($Src)
& $javac -encoding UTF-8 -source 8 -target 8 `
    -cp $AndroidJar -d (Join-Path $Obj "classes") $All.FullName
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$dexDir = Join-Path $Obj "dex"
New-Item -ItemType Directory -Force -Path $dexDir | Out-Null
$classes = Get-ChildItem (Join-Path $Obj "classes") -Recurse -Filter "*.class" | Select-Object -ExpandProperty FullName
& $java -cp $d8jar com.android.tools.r8.D8 `
    --release --lib $AndroidJar --min-api 21 `
    --output $dexDir $classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

# ---------- 5. package ----------
$dex = Join-Path $dexDir "classes.dex"
if (-not (Test-Path $dex)) { throw "classes.dex not generated" }
python -c "import zipfile,sys; src,dex,dst=sys.argv[1:4]; zin=zipfile.ZipFile(src); zout=zipfile.ZipFile(dst,'w'); [zout.writestr(i, zin.read(i.filename)) for i in zin.infolist()]; zout.write(dex,'classes.dex'); zout.close(); zin.close()" `
    (Join-Path $Obj "base.apk") $dex (Join-Path $Obj "withdex.apk")
if ($LASTEXITCODE -ne 0) { throw "merge dex failed" }

& $zipalign -f 4 (Join-Path $Obj "withdex.apk") (Join-Path $Obj "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# ---------- 6. sign ----------
$ks = Join-Path $Tools "hugotv.keystore"
if (-not (Test-Path $ks)) {
    & $keytool -genkeypair -keystore $ks -alias hugotv `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass hugotv123 -keypass hugotv123 `
        -dname "CN=HugoTV,O=hugotv,C=CN" -noprompt
}

$out = Join-Path $Dist "HugoTV-$Version.apk"
& $java -jar $apksignerjar sign --ks $ks --ks-pass pass:hugotv123 --key-pass pass:hugotv123 --out $out (Join-Path $Obj "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "sign failed" }

& $java -jar $apksignerjar verify --print-certs $out
Write-Host ""
Write-Host "DONE: $out"
