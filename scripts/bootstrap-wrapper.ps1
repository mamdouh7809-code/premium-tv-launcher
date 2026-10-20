$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Force 'gradle/wrapper' | Out-Null
$TaskWrapperFile = Join-Path (Get-Location) 'gradle/wrapper/gradle-wrapper.jar.tmp'
Invoke-WebRequest -UseBasicParsing 'https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar' -OutFile $TaskWrapperFile
$TaskWrapperHash = (Get-FileHash $TaskWrapperFile -Algorithm SHA256).Hash.ToLowerInvariant()
if ($TaskWrapperHash -ne '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046') {
    Remove-Item $TaskWrapperFile
    throw 'Gradle wrapper checksum mismatch.'
}
Move-Item -Force $TaskWrapperFile 'gradle/wrapper/gradle-wrapper.jar'
