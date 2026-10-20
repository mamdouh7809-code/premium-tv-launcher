#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p gradle/wrapper
curl --fail --location --retry 3 https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar.tmp
printf '%s  %s\n' '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046' 'gradle/wrapper/gradle-wrapper.jar.tmp' | sha256sum -c -
mv gradle/wrapper/gradle-wrapper.jar.tmp gradle/wrapper/gradle-wrapper.jar
chmod +x gradlew
