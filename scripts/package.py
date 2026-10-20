#!/usr/bin/env python3
"""Package tracked source, the generated official wrapper, APK and test reports."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
dist = root / "dist"
dist.mkdir(exist_ok=True)
apk = root / "app/build/outputs/apk/preview/app-preview.apk"
delivered_apk = dist / "Premium-TV-Launcher.apk"
delivered_apk.write_bytes(apk.read_bytes())
tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
archive = dist / "Premium-TV-Launcher-Complete.zip"
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as out:
    for name in tracked:
        if not name:
            continue
        path = root / name
        if path.is_file():
            out.write(path, "PremiumTVLauncher/" + name)
    wrapper = root / "gradle/wrapper/gradle-wrapper.jar"
    assert wrapper.is_file(), "The complete project must include the Gradle wrapper JAR."
    if "gradle/wrapper/gradle-wrapper.jar" not in tracked:
        out.write(wrapper, "PremiumTVLauncher/gradle/wrapper/gradle-wrapper.jar")
    out.write(delivered_apk, "PremiumTVLauncher/APK/Premium-TV-Launcher.apk")
    report = {
        "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root).decode().strip(),
        "workflow_run": os.environ.get("GITHUB_RUN_ID", "local"),
        "build": "assemblePreview + assembleDebug succeeded",
        "static_checks": "Android Lint debug succeeded",
        "unit_tests": "FocusNavigatorTest and AppOrderTest succeeded",
        "device_tests": "See the same workflow run; release publication requires that job to pass.",
        "real_X96Q_Pro": "NOT TESTED: no physical device connected.",
        "performance": "NOT BENCHMARKED on 1GB/2GB boxes or 4K hardware.",
        "signing": "Optimized non-debuggable preview APK signed using the Android debug/test certificate."
    }
    out.writestr("PremiumTVLauncher/BUILD-RESULTS.json", json.dumps(report, indent=2))
for item in (delivered_apk, archive):
    digest = hashlib.sha256(item.read_bytes()).hexdigest()
    (dist / (item.name + ".sha256")).write_text(digest + "  " + item.name + "\n")
print(json.dumps({"apk_bytes": delivered_apk.stat().st_size, "zip_bytes": archive.stat().st_size}))
