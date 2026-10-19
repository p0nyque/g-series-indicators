# Local setup (macOS, Apple Silicon)

This folder is a fork of [tzebrowski/ObdGraphs](https://github.com/tzebrowski/ObdGraphs)
(remote `upstream`). See [RESEARCH.md](RESEARCH.md) for why.

## 1. Install (once) — done on this Mac 2026-10-19

A command-line toolchain lives under `~/Library/Android`, with no Android Studio:

| What | Where |
|------|-------|
| Temurin JDK 17.0.20.1 | `~/Library/Android/jdk/jdk-17.0.20.1+1/Contents/Home` |
| Android command-line tools 23.0 | `~/Library/Android/sdk/cmdline-tools/latest` |
| SDK Platform 36, Build-Tools 35.0.0, Platform-Tools 37.0.1 | `~/Library/Android/sdk` |
| Debug signing key | `~/.android/debug.keystore` |

Add this to `~/.zshrc` so `./gradlew` and `adb` work in any new terminal:

```bash
export JAVA_HOME="$HOME/Library/Android/jdk/jdk-17.0.20.1+1/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Optional: the Android Auto Desktop Head Unit, to see the car screen on the Mac:

```bash
sdkmanager "extras;google;auto"
```

Android Studio can be added later for editing and debugging. Point it at the same SDK
folder.

## 2. Prepare the checkout (once)

```bash
./scripts/setup-local.sh
```

The script points `local.properties` at your SDK, because upstream committed its own Linux
path. It also writes a placeholder `app/google-services.json`, so Firebase is effectively
off and no crash data goes to the original author's project.

## 3. Build the Android Auto variant

```bash
./gradlew :app:assembleGiuliaAADebug
```

The APK lands in `app/build/outputs/apk/giuliaAA/debug/` (about 18 MB). The first build
downloads Gradle and the dependencies and took about 6.5 minutes here.

## 4. Run it on the phone and in Android Auto

1. On the phone: **Settings → About phone → tap Build number 7×**, then
   **Developer options → USB debugging** on. Connect it by USB.
2. Install the APK:

   ```bash
   adb install -r app/build/outputs/apk/giuliaAA/debug/*.apk
   ```

3. Android Auto only lists apps from the Play Store unless you allow others: open
   **Android Auto settings → tap Version 10× → ⋮ → Developer settings → Unknown sources** on.
4. Optional, without the car: in Android Auto's ⋮ menu choose **Start head unit server**,
   then on the Mac run

   ```bash
   adb forward tcp:5277 tcp:5277
   ```

   followed by `$ANDROID_HOME/extras/google/auto/desktop-head-unit`.

## Notes

- The flavor is still upstream's `giuliaAA` (app id `org.obd.graphs.my.giulia.aa`). If
  you also install the store version of ObdGraphs AA, the two will clash. We'll rename it
  when the BMW profile is added.
- `local.properties` is marked `skip-worktree`, so your SDK path never shows up in
  commits.
