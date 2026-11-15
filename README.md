# Yiddish Calendar

A Hebrew/Yiddish calendar app for keypad flip phones such as the TCL Flip 2 (240×320 dp, D-pad and soft keys). It also works by touch.

It shows Hebrew dates (letters, not numbers), month names, and Shabbos parsha names, and it can store calendar events. There are no ads and the app does not use the internet. It only asks for calendar permission so it can read and write events.

This is version 0.2.4 (package `com.yiddishcalendar.app`). It is meant for Android 5 and up, and it targets Android 11, which is what the TCL Flip 2 runs.

## What's new in 0.2.4

- New app icon
- Touch support: tap rows, pop-up items, soft-key labels and days; swipe to change month
- Only the month grid slides when the month changes
- Events can last several days
- No arrows in New event or Settings; choices open small pop-ups
- Pop-ups fit their contents
- Menu item 5 is Help (About is in Settings)

## What's new in 0.2.3

- Android 5 and up, including the LG Exalt
- The Add soft key works on more phones
- The Language row is bilingual, and 0 resets to English
- About has a key test

## What's new in 0.2.2

- Holiday names always show in the day boxes
- The current month is clearer
- Gregorian mode is fixed
- Soft-key labels sit at the far corners
- English starts on the left and Hebrew on the right
- Settings is cleaner

## Credits

- [kizitonwose/Calendar](https://github.com/kizitonwose/Calendar) (MIT), vendored with a small fork in `Calendar/`
- [KosherJava Zmanim](https://github.com/KosherJava/zmanim) (LGPL 2.1)
- [Noto Sans Hebrew](https://github.com/notofonts/hebrew) (SIL Open Font License 1.1)

See `LICENSE` for this app's MIT license and `THIRD_PARTY_LICENSES.md` for third-party terms.
