# Architecture and resource policy

## Native application

MainActivity uses framework Activity, FrameLayout, LinearLayout, TextView, AlertDialog and one RecyclerView/GridLayoutManager.
There is no browser engine, Compose runtime, video background, analytics, advertising SDK, account or remote API.
All visual artwork is drawn from bundled vectors or static native gradients. The NOIR identity is original.

InstalledApps queries MAIN + LEANBACK_LAUNCHER and MAIN + LAUNCHER on one worker, preferring the TV entry and displaying one entry per package.
An explicit component intent opens the real activity. Query visibility is declared by matching intents; QUERY_ALL_PACKAGES is unnecessary.
Labels and icons are supplied by Android. Apps without a launchable activity are not presented as openable tiles.
The host app excludes itself.

## Focus and input

FocusNavigator models grid rows and full-width headers. Physical left/right are reversed for RTL.
Headers never consume focus. The top row leads back to the toolbar; bottom and horizontal boundaries retain focus.
Off-screen targets are scrolled into view before focus is requested. A generation token discards obsolete requests.
The selected app key is restored across scans and Activity recreation, including duplicates between Favorites and All.
Reorder uses a working copy; OK commits and BACK cancels, with separate orders for each section.

## State and lifecycle

SharedPreferences stores ordered JSON arrays, a hidden set, accent, wallpaper mode, animation preference, margin and language.
Hidden favorites remain saved so restoring an app can restore its favorite status.
Package replacement removal events are ignored until the replacement arrives. Full discovery runs again when the activity becomes visible.
Package and time receivers and ConnectivityManager callbacks exist only while the activity is started.
There are no manifest boot receivers, services, jobs, alarms or wake locks.
The system starts the selected HOME activity; the app cannot intercept or override a firmware-owned HOME key.

## Memory and drawing

- RecyclerView creates visible cards, with a small recycled pool and no item animations.
- Icons are rasterized at 64–192 px on a worker and cached in a 2 MiB or 4 MiB LruCache.
- A content/version-based disk cache is capped at 12 MiB. Trim callbacks discard the memory cache.
- No synchronous icon decoding or package enumeration runs in the main draw path.
- Time is updated at minute boundaries and in response to clock changes; network state is event-driven.
- Built-in wallpaper paths/shaders are prepared when size or theme changes and have no frame loop.
- Imported image input is bounded to 32 MiB, sampled before decode, normalized for EXIF rotation, and saved atomically.
- Image dimensions are sampled against 960×540 on low-memory devices or 1280×720 otherwise. A portrait image may swap dimensions after EXIF rotation while retaining the same pixel budget.
- Focus movement is 90 ms; it is disabled by default on devices reported as low RAM or below about 1.6 GB total memory.
- All threads belong to the Activity and are shut down on destruction. Idle executors do not run persistent services.

These are implementation limits, not measured FPS, startup-time or RSS guarantees. Vendor firmware, installed applications, TV density and GPU load need real hardware validation.

## Permission and storage

ACCESS_NETWORK_STATE only reads connectivity capabilities for the status indicator.
There is no INTERNET, Wi-Fi location, broad storage, overlay, accessibility, usage access, package installation or root permission.
Photo selection delegates to ACTION_OPEN_DOCUMENT. A normalized private copy is retained; a long-term URI grant is unnecessary.
App metadata stays on the device. Backups are disabled for this initial build.

## Build

Kotlin 2.0.21, Android Gradle Plugin 8.9.2, Gradle 8.11.1, JDK 17.
compile/target SDK 35, min SDK 23. Native newer APIs use SDK guards.
The preview variant has release optimizations and is not debuggable, but uses a test/debug signing certificate.
Use a private, stable release keystore for long-term distributions and seamless updates.

## Sources checked

- X96 published Android 10 specification: https://x96mini.com/products/android-10-x96q-pro-smart-tv-box
- Android package visibility: https://developer.android.com/training/package-visibility/declaring
- HOME intent and RoleManager: https://developer.android.com/reference/android/app/role/RoleManager
- TV hardware and D-pad: https://developer.android.com/training/tv/get-started/hardware
- AGP tool compatibility: https://developer.android.com/build/releases/agp-8-9-0-release-notes
- Official Gradle distribution / wrapper checksums: https://gradle.org/release-checksums/
