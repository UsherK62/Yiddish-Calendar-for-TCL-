# Yiddish Calendar

A Hebrew/Yiddish calendar app for keypad flip phones such as the TCL Flip 2 (240×320 dp, D-pad and soft keys, no touch).

It shows Hebrew dates (letters, not numbers), month names, and Shabbos parsha names, and it can store calendar events. There are no ads and the app does not use the internet. It only asks for calendar permission so it can read and write events.

Light theme is the default. The layout is right-to-left, with Sunday on the right.

This is version 0.2.1 (package `com.yiddishcalendar.app`). It is meant for Android 8 and up, and it targets Android 11, which is what that phone runs.

## Build

You need JDK 17 and the Android SDK. Create a `local.properties` file (gitignored) with your Android SDK path:

```
sdk.dir=/path/to/android-sdk
```

Then:

```
./gradlew assembleDebug
```

The debug APK is written under `app/build/outputs/apk/debug/`.

`./gradlew assembleRelease` makes a release APK. If you do not set up your own signing key, that release build is signed with the debug key. To sign a release yourself, add a gitignored `keystore.properties` in the project root:

```
storeFile=/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Or set the environment variables `YIDDISH_CALENDAR_STORE_FILE`, `YIDDISH_CALENDAR_STORE_PASSWORD`, `YIDDISH_CALENDAR_KEY_ALIAS`, and `YIDDISH_CALENDAR_KEY_PASSWORD`. Keep your key and passwords private.

## Credits

- [kizitonwose/Calendar](https://github.com/kizitonwose/Calendar) (MIT), vendored with a small fork in `Calendar/`
- [KosherJava Zmanim](https://github.com/KosherJava/zmanim) (LGPL 2.1)
- [Noto Sans Hebrew](https://github.com/notofonts/hebrew) (SIL Open Font License 1.1)

See `LICENSE` for this app's MIT license and `THIRD_PARTY_LICENSES.md` for third-party terms.
