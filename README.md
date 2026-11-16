# Yiddish Calendar

A Hebrew calendar for Yiddish-speaking users, made for keypad flip phones such as the TCL Flip 2. It also works by touch. The month grid is [kizitonwose/Calendar](https://github.com/kizitonwose/Calendar). Hebrew dates, the weekly parsha, and holidays come from [KosherJava Zmanim](https://github.com/KosherJava/zmanim).

There are no ads, and the app does not use the internet. It only asks for calendar permission, so it can read and write events.

This is version 0.2.5 (package `com.yiddishcalendar.app`). It runs on Android 5.0 and up, and it targets Android 11.

## Features

- The month grid reads right to left, with Sunday on the right. Settings can switch the grid to left to right.
- Hebrew dates are written in Hebrew letters. Hebrew months are the usual view: a large Hebrew day and a small Gregorian day. Gregorian months put the Gregorian day first. Switch between them with # or with Settings → Hebrew months.
- On Shabbos the screen shows the weekly parsha (שבת פרשת) when Show parsha is on. Saturday is labeled שבת, and an ordinary Friday is called ערב שבת.
- Yom Tov, Chol Hamoed, fasts, Chanukah, Purim, and Rosh Chodesh have a short Hebrew label on the grid, such as פסח, ר״ה, or ר״ח. Open the day to see the full Yom Tov, Rosh Chodesh, or special Shabbos name. Shabbos, Yom Tov, and Rosh Chodesh days are tinted.
- The day screen also shows Daf Yomi (Bavli) and the Omer count when they apply.
- **In Israel** follows the calendar used in Israel. Leave it off for the diaspora calendar.
- **Date at nightfall** is on by default. After nightfall, “today” becomes the next Hebrew date. Nightfall is figured for Jerusalem when In Israel is on, and for Brooklyn otherwise.
- Light and dark themes. Light is the default; Dark theme switches to dark.
- Days with an event show a dot. Open the day to see the events. A new event can last one day or several (up to 30), and it can repeat every day, every week, every month, or every year. Save it to a calendar on the phone, or keep it on this phone only. A reminder is set when the event is saved to the phone calendar.
- Menu → Help explains the keys. Settings → About, then the left soft key (Keys), opens a key test that shows the code of each key the phone sends.

## Phones and screens

Screens stay in portrait and are laid out for a small keypad phone, about 240×320 dp (a TCL Flip 2). The same screens also fit a taller phone such as the LG Exalt (about 240×400 dp).

Move with the D-pad. The left soft key is the menu: the phone may send it as the left soft key, Menu, or F1, including on TCL phones and some Kyocera and LG phones. The right soft key is Add on the month screen and Back on other screens (right soft key or F2, including TCL and Sonim XP3).

A touchscreen is optional. Tap a day to select it, and tap it again to open it. Swipe up for the next month and down for the previous month. Tap the words on the bottom bar to use them like the soft keys.

## Keys

On the month screen:

- **OK** or **5** opens the selected day.
- The **D-pad** moves to another day. In the right-to-left grid, the next day is to the left.
- **4** and **6** change the month, in that same direction.
- **1** and **3** change the year.
- **0** jumps to today.
- **\*** goes to a date. Type it as MMDDYYYY.
- **#** switches between Hebrew months and Gregorian months.
- The **left soft key** opens the menu in the bottom-left corner: Today, Go to date, Add event, Settings, Help. Press a number, or move to an item and press OK, or tap it.
- The **right soft key** starts a new event.
- **Back** leaves the screen.

In Settings, **0** switches the language back to English.

On New event, press OK or tap a row to change it. Days sets how long the event lasts. The left soft key saves.

## Languages

Settings opens on Language · שפראך. That row is always labeled in English and Yiddish.

- **English.** Hebrew dates, and Shabbos and parsha names. Menus stay in English.
- **English + Hebrew names.** Holiday names are in Hebrew too.
- **Yiddish.** Menus, settings, and soft-key labels are in Yiddish, and weekday names are in Hebrew. Help is shown in Yiddish, then in English.

Hebrew dates, Hebrew month names, parsha names, and the short holiday labels stay in Hebrew letters in every language. Gregorian dates stay in English.

## Credits

- [kizitonwose/Calendar](https://github.com/kizitonwose/Calendar) (MIT), vendored with a small fork in `Calendar/`
- [KosherJava Zmanim](https://github.com/KosherJava/zmanim) (LGPL 2.1)
- [Noto Sans Hebrew](https://github.com/notofonts/hebrew) (SIL Open Font License 1.1)

## License

Copyright (C) 2026 Yidtech-101.

This app's own code is GPL-3.0. The full text is in `LICENSE`.

The bundled libraries keep their own licenses. kizitonwose/Calendar stays under the MIT license in `Calendar/`. KosherJava stays under the LGPL, as listed in `THIRD_PARTY_LICENSES.md` and `licenses/`.
