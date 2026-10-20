# Verification and limits

The authoritative evidence is the Actions run for the downloaded build, its reports and BUILD-RESULTS.json inside the ZIP.
This file describes coverage, not a claim that every future run has passed.

## Automated gates

1. Android Lint debug, with abortOnError enabled.
2. JVM tests: saved order merge, removal/duplicate handling, moving an app, row boundaries, partial rows, header skipping and RTL.
3. Compile debug, optimized preview, instrumentation tests and the separate install/uninstall fixture.
4. apksigner signature verification and aapt manifest/package inspection.
5. Android 10 / API 29 x86_64 emulator at a 1920×1080 logical display:
   - install and uninstall a real fixture APK while the launcher is visible;
   - open app options through MENU, add a favorite, recreate the Activity and check persistence;
   - send actual D-pad key events and verify retained app focus, then BACK to the toolbar;
   - switch to Arabic, verify RTL configuration and open the bundled remote keyboard.
6. Publish the APK and complete ZIP only after both build and device-test jobs pass.

The emulator is generic Android in landscape, not X96Q Pro firmware. App repository parsing and real framework broadcasts are tested, but manufacturer HOME behavior is not established by it.
The fixture is test-only and is never bundled into the launcher or installed on the user device.

## Manual acceptance on the actual X96Q Pro

- Read Android version and API from Settings/About before installing.
- Confirm discovery and opening of the actual TV and mobile-style apps.
- Check all arrow keys, held OK, MENU, BACK and selection recovery after returning from apps.
- Reorder both sections, cancel another reorder, hide/restore an app and search in both languages.
- Reboot and confirm preferences, favorites, order and wallpaper remain.
- Install/update/disable/remove an app and verify cards update.
- Check Wi-Fi, Ethernet, local-only links and lost network.
- Test local photo selection, a large JPEG and the absence of a file picker.
- Select NOIR TV as HOME if the firmware offers it; verify return to the previous launcher.
- Check margins and density on 720p, 1080p and 4K displays.
- Observe responsiveness and memory on 1 GB and 2 GB devices; no hardware performance guarantee is claimed.

## Not tested by the authoring environment

No physical X96Q Pro, vendor remote, vendor HOME policy, overnight stability, actual 4K GPU performance or 1 GB hardware benchmark is available here.
The app cannot make another streaming app run faster; it only replaces the home interface where permitted.
