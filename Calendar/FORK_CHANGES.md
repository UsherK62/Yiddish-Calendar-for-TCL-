# Yiddish Calendar fork changes (upstream kizitonwose/Calendar c2711b3, MIT, see LICENSE.md)
1. data/.../MonthPageSource.kt (new): optional hook so an app can supply its own month pages (Hebrew months).
2. view/.../monthcalendar/MonthCalendarAdapter.kt: dataStore uses MonthPageSource.provider when set, else upstream behaviour.
The Yiddish app builds core/data/view sources through ../calendarlib (its own simple build file), not through this repo's Gradle build.
