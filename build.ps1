$ErrorActionPreference = "Stop"
# Point these at your own JDK 17 and Android SDK (build-tools 34, platform 34).
$jdk = if ($env:JDK) { $env:JDK } else { "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin" }
$sdk = if ($env:ANDROID_SDK) { $env:ANDROID_SDK } else { "$env:USERPROFILE\tools\android-sdk" }
$bt = "$sdk\build-tools\34.0.0"
$jar = "$sdk\platforms\android-34\android.jar"
$env:JAVA_HOME = Split-Path $jdk
Set-Location $PSScriptRoot
foreach ($d in "out\classes","out\dex") {
  $target = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot $d))
  if (-not $target.StartsWith($PSScriptRoot + [IO.Path]::DirectorySeparatorChar)) { throw "Invalid build path" }
  if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
}
New-Item -ItemType Directory -Force out\classes, out\dex | Out-Null
$cert = [Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPem([IO.File]::ReadAllText("$PSScriptRoot\keys\platform.x509.pem"))
if ($cert.GetCertHashString([Security.Cryptography.HashAlgorithmName]::SHA256) -ne "C8A2E9BCCF597C2FB6DC66BEE293FC13F2FC47EC77BC6B2B0D52C11F51192AB8") { throw "Platform certificate mismatch" }

& "$bt\aapt2.exe" compile --dir res -o out\res.zip
if ($LASTEXITCODE) { throw "aapt2 compile failed" }
& "$bt\aapt2.exe" link -I $jar --manifest AndroidManifest.xml -o out\base.apk out\res.zip
if ($LASTEXITCODE) { throw "aapt2 failed" }
$src = Get-ChildItem -Recurse src -Filter *.java | ForEach-Object FullName
& "$jdk\javac.exe" -encoding UTF-8 --release 11 -classpath $jar -d out\classes $src
if ($LASTEXITCODE) { throw "javac failed" }
& "$jdk\jar.exe" cf out\classes.jar -C out\classes .
& "$bt\d8.bat" --release --min-api 31 --lib $jar --output out\dex out\classes.jar
if ($LASTEXITCODE) { throw "d8 failed" }
& "$jdk\jar.exe" uf out\base.apk -C out\dex classes.dex
& "$bt\zipalign.exe" -f 4 out\base.apk out\aligned.apk
if ($LASTEXITCODE) { throw "zipalign failed" }
& "$bt\apksigner.bat" sign --key keys\platform.pk8 --cert keys\platform.x509.pem --out carhome.apk out\aligned.apk
if ($LASTEXITCODE) { throw "apksigner failed" }
& "$bt\apksigner.bat" verify carhome.apk
if ($LASTEXITCODE) { throw "APK verification failed" }
"BUILD OK " + (Get-Item carhome.apk).Length
