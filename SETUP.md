# Local setup (macOS, Apple Silicon)

This folder is a fork of [tzebrowski/ObdGraphs](https://github.com/tzebrowski/ObdGraphs)
(remote `upstream`). See [RESEARCH.md](RESEARCH.md) for why.

## 1. Install (once) — last done on a fresh Mac 2026-11-23

A command-line toolchain lives under `~/Library/Android`, with no Android Studio and no
Homebrew:

| What | Where |
|------|-------|
| Temurin JDK 17 (app build) | `~/Library/Android/jdk/jdk-17.0.21.1+2/Contents/Home` |
| Temurin JDK 11 (ObdMetrics build) | `~/Library/Android/jdk/jdk-11.0.33.1+1/Contents/Home` |
| Maven 3.9.9 (ObdMetrics build) | `~/Library/Android/maven/apache-maven-3.9.9` |
| Android command-line tools | `~/Library/Android/sdk/cmdline-tools/latest` |
| SDK Platform 36, Build-Tools 35.0.0, Platform-Tools | `~/Library/Android/sdk` |
| Debug signing key | `~/.android/debug.keystore` |
| ObdMetrics fork, branch `feat/bmw-extended-addressing` | `../ObdMetrics`, installed into `~/.m2` |

The JDK folder names change with each Temurin release; use whatever `ls ~/Library/Android/jdk`
shows in the paths below.

### From scratch

JDKs, command-line tools and Maven:

```bash
mkdir -p ~/Library/Android/jdk ~/Library/Android/sdk/cmdline-tools ~/Library/Android/maven
cd ~/Library/Android/jdk
curl -fsSL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jdk/hotspot/normal/eclipse"
curl -fsSL -o jdk11.tar.gz "https://api.adoptium.net/v3/binary/latest/11/ga/mac/aarch64/jdk/hotspot/normal/eclipse"
tar xzf jdk17.tar.gz && tar xzf jdk11.tar.gz && rm jdk17.tar.gz jdk11.tar.gz
cd ~/Library/Android
curl -fsSL -o clt.zip "https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip"
unzip -q clt.zip -d sdk/tmp && mv sdk/tmp/cmdline-tools sdk/cmdline-tools/latest && rm -r sdk/tmp clt.zip
curl -fsSL "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz" | tar xz -C maven
```

SDK packages (with `JAVA_HOME` set to JDK 17, see the `~/.zshrc` lines below):

```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
```

Debug signing key. Without it the build fails at `validateSigningGiuliaAADebug`
("Keystore file ... debug.keystore not found"):

```bash
mkdir -p ~/.android && keytool -genkeypair -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

A new key signs differently from an APK built on another machine: `adb install` then fails
with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the old app must be uninstalled first (its
settings go with it).

ObdMetrics fork (the app depends on `obd-metrics:11.26.1-SNAPSHOT` from `~/.m2`). Build it
with JDK 11: on JDK 17 delombok and the formula tests fail.

```bash
cd .. && git clone -b feat/bmw-extended-addressing https://github.com/p0nyque/ObdMetrics.git
cd ObdMetrics && JAVA_HOME=~/Library/Android/jdk/jdk-11.0.33.1+1/Contents/Home ~/Library/Android/maven/apache-maven-3.9.9/bin/mvn -B install -DskipTests -Dlombok.delombok.skip=true -Dgpg.skip -Dmaven.javadoc.skip=true
```

### Shell

Add this to `~/.zshrc` so `./gradlew` and `adb` work in any new terminal:

```bash
export JAVA_HOME="$HOME/Library/Android/jdk/jdk-17.0.21.1+2/Contents/Home"
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
path. Firebase was removed from the build, so no `google-services.json` is needed.

## 3. Build the Android Auto variant

```bash
./gradlew :app:assembleGiuliaAADebug
```

The APK lands in `app/build/outputs/apk/giuliaAA/debug/` (about 14 MB). The first build
downloads Gradle and the dependencies and takes about 3 to 7 minutes.

## 4. Run it on the phone and in Android Auto

1. On the phone: **Settings → About phone → tap Build number 7×**, then
   **Developer options → USB debugging** on. Connect it by USB, tick "Always allow from this
   computer" and tap **Allow** on the prompt. `adb devices` must list the phone as `device`.
   On OnePlus / OxygenOS: Build number is under **About device → Version**, Developer options
   under **Additional settings** (**System settings** on OxygenOS 15); the Settings search
   finds both. With USB debugging off, the Mac sees the phone on USB but `adb devices` stays
   empty.
2. Install the APK:

   ```bash
   adb install -r app/build/outputs/apk/giuliaAA/debug/*.apk
   ```

   The first start asks "Let app always run in background?". Allow it for the car, so data
   keeps flowing with the phone screen off.
3. Smoke test without the car (debug build only): **OBD2 Adapter Connection → Settings →
   OBD adapter connection type → mock**, then the round button bottom right. The toolbar
   subtitle turns to `mock · BMW G31 520d MHEV · connected`; `adb logcat` shows
   `Collecting process started. Strategy: CLASSIC_QUERY` and no
   `ConnectorResponseDecoder ... Unexpected error`. Press the button again and switch the type
   back to **Bluetooth Classic** and pick the adapter under "Available Bluetooth Classic
   devices" (a fresh install shows "Not set"). There are no instrumented tests; the unit
   tests run on the Mac:

   ```bash
   ./gradlew :app:testGiuliaAADebugUnitTest :datalogger:testDebugUnitTest :screen_renderer:testDebugUnitTest :profile:testDebugUnitTest :automotive:testDebugUnitTest
   ```

4. Android Auto only lists apps from the Play Store unless you allow others: open
   **Android Auto settings → tap Version 10× → ⋮ → Developer settings → Unknown sources** on.
5. Optional, without the car: in Android Auto's ⋮ menu choose **Start head unit server**,
   then on the Mac run

   ```bash
   adb forward tcp:5277 tcp:5277
   ```

   followed by `$ANDROID_HOME/extras/google/auto/desktop-head-unit`.

## Notes

- The only flavor is upstream's `giuliaAA` (app id `org.obd.graphs.my.giulia.aa`), kept so an
  installed app keeps its settings. If you also install the store version of ObdGraphs AA, the
  two will clash.
- `local.properties` is marked `skip-worktree`, so your SDK path never shows up in
  commits.
