param(
    [Parameter(Mandatory=$true)][string]$DeviceAddress,
    [string]$ApkPath = (Join-Path $PSScriptRoot '../APK/Premium-TV-Launcher.apk')
)
$ErrorActionPreference = 'Stop'
$TaskAdbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($TaskAdbCommand) { $TaskAdb = $TaskAdbCommand.Source }
elseif (Test-Path (Join-Path $PSScriptRoot '../adb.exe')) { $TaskAdb = (Resolve-Path (Join-Path $PSScriptRoot '../adb.exe')).Path }
else { throw 'Install Android Platform Tools and put adb in PATH, or place adb.exe and its DLLs in the project root.' }
if (!(Test-Path $ApkPath)) { throw 'APK not found. Pass -ApkPath with the downloaded APK path.' }
& $TaskAdb connect $DeviceAddress
& $TaskAdb -s $DeviceAddress wait-for-device
$TaskDeviceSdk = (& $TaskAdb -s $DeviceAddress shell getprop ro.build.version.sdk).Trim()
if ($TaskDeviceSdk -notmatch '^\d+$' -or [int]$TaskDeviceSdk -lt 23) { throw 'Unable to confirm Android API 23 or newer.' }
& $TaskAdb -s $DeviceAddress shell getprop ro.product.model
& $TaskAdb -s $DeviceAddress shell getprop ro.build.version.release
& $TaskAdb -s $DeviceAddress install -r (Resolve-Path $ApkPath).Path
if ($LASTEXITCODE -ne 0) { throw 'Installation failed. Check the TV authorization prompt or signing compatibility.' }
& $TaskAdb -s $DeviceAddress shell am start -n com.premiumtv.launcher/.MainActivity
