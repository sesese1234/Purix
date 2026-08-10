# Yomi — יומי

**Design your day. Score your day.**

Yomi is a daily agenda app built around one idea: a day is something you *design*
in advance and then *score* against what actually happened. Every task carries a
weight, a window and a rulebook; every evening ends with a number between 0 and
100 and a grade, and the whole point is to chase the maximum.

Built with **Kotlin Multiplatform + Compose Multiplatform**, following
kdroidFilter's MVVM/clean-module architecture, with a Material 3 Expressive
design language inspired by ArchiveTune's playful, colour-driven interface.

**Runs on Android and desktop** (Windows, macOS, Linux) from one shared
codebase — the same Compose tree, the same engines, the same tests.

---

## What it does

### The score
The heart of the app. Each day is scored out of 100 from five weighted
components, then adjusted by bonuses and penalties:

| Component | What it measures |
|---|---|
| **Completion** | How much of the planned weight you actually did |
| **Punctuality** | How close to their windows your timed tasks landed |
| **Sub-missions** | How thoroughly you finished multi-step tasks |
| **Routine** | Wake-up, bedtime and the daily check-in |
| **Reliability** | How much you carried out rather than skipped, blended with your streak |

A component the day cannot exercise — no timed tasks, no check-in — is marked
*not applicable* and its weight is redistributed, so a day of untimed work is
never silently punished for lacking a clock.

Bonuses: perfect day, streak (capped), every sub-mission ticked, early riser,
goal pace. Penalties: missed, skipped and deferred tasks, with a total cap. A
skip with a stated reason can be free. Every threshold is editable, and the
settings screen re-scores today live as you drag the sliders.

### Planning
- **Tasks** with fixed times, flexible windows, deadlines, or no clock at all
- **Recurrence**: daily, every N days, weekly (any weekdays, every N-th week),
  monthly by date or by weekday ("2nd Tuesday", "last Friday"), one-off, manual —
  plus per-date exceptions and additions
- **Sub-missions** with weights, optional flags, and four roll-up rules
  (all-required / N-of-M / weighted / informational)
- **Measurable tasks** — "read 20 pages" — that score by quantity
- Priorities, energy levels, categories with their own colours and weight
  multipliers

### Copying a day
Deliberately a first-class feature, not an afterthought:
- Copy to the next N days, to chosen weekdays over a chosen number of weeks, or
  to an explicit date range
- Merge (add only what is missing), replace, or append
- Filter what travels: recurring vs one-off, unfinished, specific categories,
  wake/bedtime targets, day note
- Save any day as a **template** and stamp it anywhere later
- Duplicate a single task onto several days

### Missed-task detection
A task is declared **missed** once `due + grace` has passed; work that was
genuinely started becomes **partial** instead of being thrown away. Untimed
tasks are only missed when the day itself is over. Closing a day freezes its
score so history never shifts under you, and unfinished work can carry over to
tomorrow.

### Goals
Goals fill themselves in from the tasks and categories wired into them — there
is no separate bookkeeping. Count, minutes, custom quantity, average score, or
streak; measured daily, weekly, monthly, or over the whole run. Each shows both
progress *and* pace, plus what is needed per remaining day.

### Insights
Weekly score with its own grade, score trend, per-weekday and per-category
breakdowns, completion by time of day, wake-up average and consistency spread,
perfect-day and streak counters, and a calendar heatmap.

### Notifications
Task reminders with per-task lead times, overdue alerts, a morning summary, an
evening review, a weekly report, streak-at-risk warnings and goal-pace nudges —
all filtered through quiet hours. The planner is pure and fully unit-tested; the
platform layer only knows how to put a message on screen.

### Customisation
Theme mode, seed colour, nine palette styles, four corner styles, three
densities, three motion levels, text scale, AMOLED black, high contrast,
category tinting, emoji toggle, score-ring toggle, timeline grouping (time of
day / category / priority / status / none), which cards appear on Today, week
start, 12/24-hour clock, a configurable **day boundary hour** for night owls,
and the whole scoring rulebook.

**Full Hebrew and English**, with genuine right-to-left layout — not translated
English in a left-to-right shell.

---

## Screenshots

Rendered off-screen by the test suite (`app/build/screenshots`):

| | |
|---|---|
| `today-light.png` / `today-dark.png` | The Today screen, both themes |
| `today-hebrew-rtl.png` | Hebrew, right-to-left |
| `planner-calendar.png` | Month view with the score heatmap |
| `planner-library.png` / `planner-templates.png` | Task library and templates |
| `goals.png` | Goal cards with pace |
| `insights.png` | The full statistics screen |
| `settings-scoring.png` | The scoring editor with live preview |
| `palette-*.png` | The palette styles side by side |
| `phone-today.png` / `phone-planner-dark.png` | The Android phone layout |
| `phone-hebrew.png` | Phone layout in Hebrew, right-to-left |

---

## Architecture

```
:core:model          Serialisable domain entities. Pure Kotlin, no dependencies.
:core:domain         The engines: recurrence, materialisation, scoring, streaks,
                     missed detection, copying, goals, notifications, statistics.
                     Pure, deterministic, and where most of the tests live.
:core:data           Okio + JSON persistence with atomic writes and monthly
                     shards; YomiRepository, the single door to everything.
:core:designsystem   Theme, tokens, components, formatters, and both languages.
:core:notification   Notifier abstraction + the desktop and Android backends.
:core:ui             Navigator and the ViewModel base.
:feature:today       Today screen and its model.
:feature:planner     Calendar, copy dialog, templates, task editor.
:feature:goals       Goal board and editor.
:feature:insights    Statistics and charts.
:feature:settings    Every preference, including the scoring editor.
:app                 Shell, navigation, DI graph, desktop and Android entries.
```

**Rules the layering enforces**

- The domain has no knowledge of files, platforms, or user-facing text. Scoring
  emits machine-readable `ScoreInsight` values that the UI renders into
  sentences, so the same verdict reads naturally in every language.
- Day plans store *snapshots* of task blueprints. Editing or deleting a task
  never rewrites a score that has already been recorded.
- Nothing reads the system clock directly. Everything takes a `YomiClock`, which
  is what makes the scoring, streak and missed-task logic testable to the minute.
- Derived state — the score of every day — is recomputed synchronously after
  each write, so a caller that reads a score right after a mutation always sees
  the result of that mutation.

**Stack**: Kotlin 2.4.10 · Compose Multiplatform 1.11.1 · AGP 8.13.2 ·
Koin 4.2.2 · kotlinx-coroutines / serialization / datetime · Okio ·
MaterialKolor · kdroidFilter compose-native-notification (desktop) · JVM 21 on
desktop, JVM 17 bytecode on Android.

Every module carries both an `androidTarget()` and a `jvm()` target. Platform
differences live in exactly three `expect`/`actual` pairs — the data directory,
the file system, and the notifier — so `commonMain` holds the entire app.

---

## Running it

```bash
./gradlew :app:run              # launch the desktop app
./gradlew build                 # compile everything and run all 140 tests
./gradlew :app:jvmTest          # re-render the screenshots
./gradlew :app:packageDeb       # desktop installers: also packageMsi / packageDmg

./gradlew :app:assembleDebug    # Android APK, installable as-is
./gradlew :app:assembleRelease  # Android APK, signed if a keystore is present
```

### Android

`minSdk 26` (Android 8.0) · `targetSdk 36` · one Activity hosting the same
Compose tree the desktop build runs, so the two platforms cannot drift apart.
Android supplies its private files directory and notification context to the
shared layer at startup; notifications go through the framework's channel API
directly, with `POST_NOTIFICATIONS` requested on first launch.

The debug APK installs without any setup. For a signed release build, generate
a key first — the keystore is deliberately **not** in the repository:

```bash
keytool -genkeypair -v -keystore keystore/yomi-release.jks -alias yomi \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass <password> -keypass <password> -dname "CN=Yomi"
```

then point `signingConfigs["release"]` in `app/build.gradle.kts` at your own
credentials. Without a keystore the release task still runs and simply leaves
the APK unsigned.

Building Android needs an SDK; put its location in `local.properties`
(`sdk.dir=/path/to/android-sdk`) or set `ANDROID_HOME`.

Data lives in the platform's conventional location — `%APPDATA%\Yomi` on
Windows, `~/Library/Application Support/Yomi` on macOS, `$XDG_DATA_HOME/yomi`
elsewhere — as readable JSON: `settings.json`, `catalog.json`, and one file per
month under `days/`. Writes go through a temporary file and an atomic move, and
a corrupt file degrades to defaults rather than taking your history with it.

---

## Tests

140 tests, all green.

- **Scoring** (29) — weights, priority and category multipliers, all four
  sub-mission rules, quantity credit, punctuality and grace overrides, penalty
  caps, free skips, perfect-day detection, bedtime past midnight, component
  redistribution, frozen scores, clamping
- **Recurrence** (14) — every pattern, interval alignment, short months,
  "last Friday", exceptions and additions
- **Copying** (14) — merge/replace/append, filters, templates, finalised targets
- **Repository** (22) — persistence round-trips, corrupt files, day finalisation,
  carry-over, deferral, blueprint deletion, monthly sharding, the night-owl
  boundary
- **Goals, notifications, streaks, materialisation** (37)
- **Wiring** (3) — the Koin graph assembles, notifications never repeat, backups
  round-trip
- **Screenshots** (10) — every screen renders in light, dark, RTL, all three
  densities, and the Android phone layout

The screenshot tests earn their place: they caught a `Strings` catalogue that
compiled cleanly but blew past the JVM's 255-argument limit at class-load time,
and a progress bar drawn with raw canvas coordinates that never mirrored itself
in Hebrew. Android Lint caught a third: notification channels need API 26, so
`minSdk` moved from 24 rather than shipping a path that would crash on 24 and 25.
