# Listen Quran — Agent Guidelines & Project Rules

## 1. Project Overview & Architecture
**Listen Quran** is a native Android offline-first Quran audio streaming and study application inspired by Spotify and Quran Audio.

### Core Stack
- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3 (Material You, version 1.3+)
- **Audio Engine**: Jetpack Media3 (`ExoPlayer` + `AudioPlayerManager` + `MediaLibraryService` + `SimpleCache`). Audio playback, sleep timers, and Hifz repetition loops are driven via timestamp polling and `seekToAyah()` in `AudioPlayerManager`. **STRICT RULE**: Never use deprecated `MediaBrowserServiceCompat` or legacy `android.media.MediaPlayer`.
- **Platform Features**: 
  - **Android Auto**: Built strictly via Jetpack Media3 `MediaLibraryService`.
  - **Home Screen Widget**: Jetpack Glance (M3 styled, 2x1 and 4x2 sizes).
  - **Notifications**: Jetpack WorkManager (`PeriodicWorkRequest`) for reminders.
  - **Drag & Drop**: `sh.calvin.reorderable` for drag-and-drop queues and playlists.
- **Database & Persistence**: Room Database for Playlists, Liked collection (`isLikedCollection = true`), Bookmarks with notes, Listening Stats (`ListeningStatEntity`), and Khatma progress (`KhatmaEntity`). Standard SQL `LIKE` queries & in-memory filtering for Surah/Reciter search. Pre-packaged SQLite database located at `app/src/main/assets/listen_quran.db`.
- **Preferences & Settings**: Jetpack DataStore Preferences strictly for user settings/preferences (Theme mode, audio quality, notification switches).
- **Networking & Serialization**: Retrofit 2.11 + Moshi 1.15 (`moshi-kotlin` + KSP codegen) for Quran.com API v4. Storage Access Framework (SAF) (`CreateDocument` / `OpenDocument`) + Moshi for JSON backup/restore.
- **Image Loading**: Coil 2.7.0 (`coil-compose`).
- **Architecture Pattern**: Unidirectional Data Flow (UDF), MVVM / Clean Architecture with Repository pattern, Kotlin Coroutines & Flows.
- **Privacy & Storage**: Local-only on-device storage. No user accounts, no tracking, no cloud lock-in.

---

## 2. Mandatory MCP Tool Usage Rules (Use MCP Whenever Possible & Needed)
**STRICT RULE**: Always use the configured Model Context Protocol (MCP) servers whenever possible and needed across all tasks. Never default to unassisted inspection or generic fallbacks when an MCP server is configured for the domain:

1. **`context7` (Docs & Modern API Lookup)**:
   - **MANDATORY**: Query `context7` before writing or updating:
     - Jetpack Compose `SharedTransitionLayout` / shared element transitions.
     - Material 3 1.3+ components (`NavigationBar`, `ModalBottomSheet`, `SearchBar`, `FilterChip`, `Slider`).
     - Jetpack Glance app widgets.
     - Jetpack Media3 (`MediaSession`, `MediaLibraryService`, `Player`, `ExoPlayer`, `SimpleCache`).
     - `sh.calvin.reorderable` drag-and-drop interactions.
   - Purpose: Prevent hallucinating deprecated Android APIs or outdated Compose constructs. Always provide both `libraryName` and specific `query` to receive concise documentation.

2. **`serena` (Semantic Code Navigation & Symbol Intelligence)**:
   - **MANDATORY**: Use Serena for symbol lookups (`find_symbol`), reference tracing (`find_referencing_symbols`), class hierarchy exploration, and surgical refactoring before reading entire files or running broad string searches across `app/`.
   - Purpose: Minimize token overhead and navigate Kotlin definitions cleanly via Kotlin LSP.

3. **`sqlite-quran` (Database Verification & Testing)**:
   - **MANDATORY**: Use `sqlite-quran` (with `mcp<2` pin) to inspect table structures, columns, types, indexes, and test SQL queries in `app/src/main/assets/listen_quran.db`.
   - Purpose: Validate Room entity schemas, foreign keys, and indexes against the shipped pre-packaged SQLite database without dumping entire tables into context. Always operate in read-only mode; never modify the shipped asset database.

4. **`android-adb` (Device & UI Validation)**:
   - **MANDATORY**: Use `android-adb` (`mobile-mcp`) to:
     - Inspect UI layouts and verify Compose composables on connected devices/emulators (`mobile_list_elements_on_screen`).
     - Verify Material 3 tonal color adherence (Teal `#006C4C` primary seed, Quranic Gold `#7D5700` tertiary seed).
     - Check for text clipping in Uthmani Arabic typography (proper font padding and 2.0+ line-height for diacritics).
     - Capture `logcat` stack traces (`mobile_get_device_logs` / `adb logcat`) immediately when debugging runtime crashes or audio session errors.

5. **`fetch` (Quran.com API Verification)**:
   - **MANDATORY**: Always use `fetch` to inspect live JSON responses from `https://api.quran.com/api/v4` (e.g. `/chapters`, `/verses/by_chapter/{id}`, `/reciters`, `/words`) before generating or modifying `@JsonClass(generateAdapter = true)` Moshi DTOs, verse models, or word-by-word timestamp parsers.
   - Purpose: Ensure zero runtime deserialization crashes due to missing or renamed schema fields.

6. **`figma` (Design System & Canvas Context)**:
   - Use the remote Figma MCP server (`https://mcp.figma.com/mcp`) when implementing new screens or updating player layouts to extract exact component styling, padding, tokens, and Uthmani typography scales.
   - Purpose: Ensure UI implementations accurately match approved designs.

7. **`github` (Repository & Review Context)**:
   - Use `github-mcp-server` (read-only mode) to review issues, inspect PR discussions, read commit history, and examine releases.
   - Purpose: Keep work aligned with repository issues and pull requests without manual context copy-pasting.

8. **`playwright` (Browser Automation & External Docs)**:
   - Use headless Playwright for interactive web exploration, documentation retrieval, and API verification when static fetch or Context7 is insufficient.

---

## 3. Material Design 3 (M3) Design System

### 3.1 Color Palette Seeds
- **Primary Seed**: `#006C4C` (Islamic Teal / Emerald)
- **Tertiary Seed**: `#7D5700` (Quranic Amber / Gold)
- **Dark Theme (Default)**:
  - `surface`: `#111413`, `surface-dim`: `#111413`, `surface-bright`: `#373A38`
  - `surface-container-lowest`: `#0C0F0E`, `surface-container-low`: `#191C1B`, `surface-container`: `#1D211F`
  - `surface-container-high`: `#282B29` (Mini-player, elevated dialogs), `surface-container-highest`: `#323634` (Bottom sheets)
  - `primary`: `#6CDAA2`, `on-primary`: `#003824`, `primary-container`: `#005237`, `on-primary-container`: `#89F8BD`
  - `secondary-container`: `#334B3F`, `on-secondary-container`: `#CDE8D7`
  - `tertiary`: `#F8BD48` (Verse medallions, Bismillah accents, Khatma badges)
- **Light Theme**:
  - `surface`: `#FBFDF9`, `surface-container`: `#F0F4EF`, `surface-container-high`: `#EAEFE9`
  - `primary`: `#006C4C`, `primary-container`: `#89F8BD`
  - `tertiary`: `#7D5700`, `tertiary-container`: `#FFDEA2`

### 3.2 Typography & Arabic Scripture
- Use standard M3 typography scale for UI (`Display Large`, `Headline Medium`, `Title Medium`, `Body Large`, `Label Medium`).
- **Quranic Scripture**: King Fahd Complex Uthmani Calligraphy (`Hafs`) or `Amiri Quran`.
  - Size: `26sp` to `32sp`.
  - Line height: Minimum `2.0` to `2.2` multiplier to avoid clipping tashkeel / harakat diacritics.

### 3.3 Motion & Transitions
- **Mini-Player to Full Player**: Shared element container transform using Compose `SharedTransitionLayout` (`500ms`, emphasized easing).
- **Navigation**: Forward/backward shared axis transitions (`300ms`).
- **Sheets**: Modal bottom sheet slide-up (`400ms`, emphasized decelerate).

---

## 4. Feature Specifications & Requirements
Refer to [internal/design.md](file:///home/tokit/Projects/listen-quran/internal/design.md) and [internal/plan.md](file:///home/tokit/Projects/listen-quran/internal/plan.md) for full specs:

1. **Home Screen**:
   - Liked Surahs (quick access) & Custom Playlists.
   - Trending from Quran.com (Daily & Weekly Mixes).
   - Recently Listened with instant resume.
   - Radio from Quran.com (live streaming recitations).
   - Reciter Discovery & Recommendations.
   - Khatma Tracking Card (circular progress ring, juz/page metrics, resume FAB).

2. **Search Screen**:
   - M3 Pill Search Bar (by Surah name, number, Ayah e.g. `2:255`, or Reciter).
   - Popular Ayahs Quick Assist Chips (Ayatul Kursi, Al-Mulk, Yasin, Ar-Rahman, 4 Quls).
   - 2-column Category Browse (Ruqyah, Sleep, Juz/Hizb, Tahajjud, Duas).
   - Instant search backed by Room SQL `LIKE` queries and in-memory filtering.

3. **Library Screen**:
   - Pinned Liked items at the top (`isLikedCollection = true`).
   - Custom user playlists (create, reorder via `sh.calvin.reorderable`, SAF + Moshi JSON export/import).
   - Bookmarks with personal notes/tadabbur reflections.
   - **Hifz Repetition Mode**: Ayah range selection (From Ayah X to Y), repetition counter (`1x`, `3x`, `5x`, `10x`, `∞`), delay interval between ayahs (`0s`, `3s`, `5s`) driven by `AudioPlayerManager` timestamp polling and `seekToAyah()`.
   - **Timed Listening / Sleep Timer**: Presets (15m, 30m, 1h, End of Surah) + exact stop time managed by `AudioPlayerManager`.

4. **Player Experience**:
   - **M3 Floating Mini-Player**: Floats 8dp above bottom nav, 16dp rounded, linear progress bar, `SharedTransitionLayout` expansion to Full Player.
   - **Full Player**: Hero artwork (28dp shape), thick M3 slider (6dp), 64dp Play/Pause FAB, speed control (`0.5x` to `1.5x`), gapless playback engine.
   - **Synced Ayahs Display**: Lyrics-style synchronized verses with active ayah card highlighted in teal border. Word-by-word mode with transliteration and translation.
   - **Share Ayah as Image**: Export verse on curated backgrounds (nature, architecture, patterns) with attribution watermark via `rememberGraphicsLayer()` and `FileProvider`.

5. **Settings & Extras**:
   - Theme mode (System, Dark, Light, AMOLED Pitch Black) stored in DataStore Preferences.
   - Offline storage manager (`MediaLibraryService` + `SimpleCache`, download quality MP3 128k/192k/Lossless, delete cached audio).
   - Data backup: SAF + Moshi JSON export/import for playlists and listening stats (`ListeningStatEntity` + `KhatmaEntity`).

---

## 6. Token Management & Context Optimization Guidelines
Token efficiency is a core engineering requirement for all agent interactions:
1. **Targeted Symbol Navigation**: Use `serena` (`find_symbol`, `get_symbols_overview`) and targeted line-range file reads (`view_file` with `StartLine`/`EndLine`) instead of opening full files or reading the entire repository.
2. **Minimal Call-Chain Tracing**: Trace only direct callers and callees relevant to the bug or feature; do not pull entire architectural call graphs into context.
3. **Restricted Documentation Queries**: Scrape only relevant API methods via Context7 using focused queries; avoid fetching multi-page API references or dumping whole doc sites.
4. **Focused SQLite Inspection**: Use `describe_table` and targeted `SELECT ... LIMIT 5` queries rather than `SELECT *` on large tables.
5. **Selective Context Loading**: Avoid reading `README.md`, `design.md`, `plan.md`, `techstack.md`, and rule files repeatedly. Reference only the specific file needed for the immediate subtask.
6. **Tool Selection by Relevance**: Never call tools indiscriminately. Select the minimal reliable set of tools matching the task domain (e.g., Serena for Kotlin code navigation, Context7 for API docs, fetch for Quran.com responses).

---

## 7. Engineering Safety & Verification Guidelines
1. **Repository Reconnaissance Before Edits**: Always inspect `git status`, current branch, and existing diffs before touching files.
2. **Never Overwrite Unrelated Work**: Preserve uncommitted user changes and work-in-progress code at all times.
3. **Version-Aware Documentation**: Respect project dependency versions (Kotlin 2.1.0, Media3 1.5.1, Compose M3 1.3+, Room 2.6.1, Retrofit 2.11, Moshi 1.15). Do not propose deprecated APIs.
4. **Media3 Lifecycle & Audio Session Rigor**: Always verify foreground service state (`MediaLibraryService`), notification controls, lockscreen player, and MediaSession lifecycle callbacks.
5. **Arabic Scripture & Typography Validation**: Always ensure Uthmani calligraphy has minimum 2.0 to 2.2 line-height and adequate padding to avoid clipping tashkeel and harakat diacritics. Verify RTL alignment on Arabic UI elements.
6. **Privacy & Credential Protection**: Never commit, log, or hardcode API keys, personal access tokens, or sensitive user data. Local-only on-device storage must remain completely private.
7. **Verification Before Claiming Success**: Always test and compile changes using Gradle or unit tests (`./gradlew testDebugUnitTest`). Never claim physical or emulator device validation unless an active device is verified via `adb devices`.

