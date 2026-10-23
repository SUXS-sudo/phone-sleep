param(
    [switch]$AndroidOnly,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$KeystorePath = ''
)
$ErrorActionPreference='Stop'
if (-not $JdkHome) {
    $installed = Get-ChildItem -Path (Join-Path $env:ProgramFiles 'Java') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($installed) { $JdkHome = $installed.FullName }
}
if (-not $JdkHome -or -not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\javac.exe'))) { throw '请安装 JDK 17，并设置 JAVA_HOME 或传入 -JdkHome。' }
$jdk = $JdkHome
$env:JAVA_HOME=$jdk
$taskDir=$PSScriptRoot
$android=Join-Path $taskDir 'android'
$build=Join-Path $taskDir 'build'
$out=Join-Path $taskDir 'dist'
if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_SDK_ROOT }
if (-not $AndroidSdk) { $AndroidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$sdk = $AndroidSdk
if (-not (Test-Path -LiteralPath (Join-Path $sdk 'platforms\android-34\android.jar'))) { throw '请安装 Android SDK Platform 34，并设置 ANDROID_HOME 或传入 -AndroidSdk。' }
$tools=Join-Path $sdk 'build-tools\34.0.0'
$platform=Join-Path $sdk 'platforms\android-34\android.jar'
$classes=Join-Path $build ('classes-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $build,$classes,(Join-Path $build 'dex'),$out | Out-Null
& "$tools\aapt.exe" package -f -m -J $build -M "$android\AndroidManifest.xml" -S "$android\res" -I $platform -F "$build\resources.apk"
if($LASTEXITCODE){throw 'aapt failed'}
$sources=Get-ChildItem "$android\src" -Filter *.java -Recurse | ForEach-Object FullName
& "$jdk\bin\javac.exe" -encoding UTF-8 --release 8 -classpath $platform -d $classes @sources
if($LASTEXITCODE){throw 'javac failed'}
& "$jdk\bin\jar.exe" --create --file "$build\classes.jar" -C $classes .
if($LASTEXITCODE){throw 'jar failed'}
& "$tools\d8.bat" --lib $platform --min-api 28 --output "$build\dex" "$build\classes.jar"
if($LASTEXITCODE){throw 'd8 failed'}
Copy-Item "$build\resources.apk" "$build\unsigned.apk" -Force
& "$jdk\bin\jar.exe" --update --file "$build\unsigned.apk" -C "$build\dex" classes.dex
if($LASTEXITCODE){throw 'apk assembly failed'}
& "$tools\zipalign.exe" -f 4 "$build\unsigned.apk" "$build\aligned.apk"
if($LASTEXITCODE){throw 'zipalign failed'}
if(-not $KeystorePath){$KeystorePath=Join-Path $build 'signing.jks'}
if(-not(Test-Path -LiteralPath $KeystorePath)){
 & "$jdk\bin\keytool.exe" -genkeypair -keystore $KeystorePath -storepass local-build-only -keypass local-build-only -alias phonesleep -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Phone Sleep Local Build' -storetype JKS
 if($LASTEXITCODE){throw 'keytool failed'}
}
& "$tools\apksigner.bat" sign --ks $KeystorePath --ks-pass pass:local-build-only --key-pass pass:local-build-only --ks-key-alias phonesleep --out "$out\一键息屏.apk" "$build\aligned.apk"
if($LASTEXITCODE){throw 'signing failed'}
& "$tools\apksigner.bat" verify "$out\一键息屏.apk"
if($LASTEXITCODE){throw 'signature verification failed'}
if(-not $AndroidOnly){
& 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe' /nologo /target:exe /out:"$build\IconMaker.exe" /r:System.Drawing.dll "$taskDir\IconMaker.cs"
if($LASTEXITCODE){throw 'icon build failed'}
& "$build\IconMaker.exe" "$build\app.ico"
& 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe' /nologo /utf8output /target:winexe /optimize+ /win32manifest:"$taskDir\app.manifest" /win32icon:"$build\app.ico" /out:"$out\一键息屏.exe" /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.Security.dll "$taskDir\PhoneSleep.cs" "$taskDir\DesktopUi.cs"
if($LASTEXITCODE){throw 'desktop build failed'}
}
Get-ChildItem $out | Select-Object Name,Length
