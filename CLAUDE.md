# Listen Quran — Agent Guidelines & Project Rules

## 1. Project Overview & Architecture
**Listen Quran** is a native Android offline-first Quran audio streaming and study application inspired by Spotify and Quran Audio.

### Core Stack
- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3 (Material You, version 1.3+)
- **Audio Engine**: Jetpack Media3 (`ExoPlayer` + `MediaLibraryService`). **STRICT RULE**: Never use deprecated `MediaBrowserServiceCompat` or legacy `android.media.MediaPlayer`.
- **Platform Features**: 
  - **Android Auto**: Built strictly via Jetpack Media3 `MediaLibraryService`.
  - **Home Screen Widget**: Jetpack Glance (M3 styled, 2x1 and 4x2 sizes).
- **Database & Persistence**: Room with SQLite FTS4 / FTS5 support for full-text search. Pre-packaged / local database located at `app/src/main/assets/listen_quran.db`.
- **Preferences & State**: Jetpack DataStore (Preferences & Proto DataStore).
- **Serialization**: `kotlinx.serialization` (JSON parsing).
- **Architecture Pattern**: Unidirectional Data Flow (UDF), MVVM / Clean Architecture with Repository pattern, Kotlin Coroutines & Flows.
- **Privacy & Storage**: Local-only on-device storage. No user accounts, no tracking, no cloud lock-in.

---

## 2. Mandatory MCP Tool Usage Rules
Whenever working on this codebase, always utilize the configured Model Context Protocol (MCP) servers:

1. **`context7` (Docs & Modern API Lookup)**:
   - **MANDATORY**: Always query `context7` before writing or updating:
     - Jetpack Compose `SharedTransitionLayout` / shared element transitions.
     - Material 3 1.3+ components (`NavigationBar`, `ModalBottomSheet`, `SearchBar`, `FilterChip`, `Slider`).
     - Jetpack Glance app widgets.
     - Jetpack Media3 (`MediaSession`, `MediaLibraryService`, `Player`, `ExoPlayer`).
   - Purpose: Prevent hallucinating deprecated Android APIs or outdated Compose constructs.

2. **`fetch` (Quran.com API Verification)**:
   - **MANDATORY**: Always use `fetch` to inspect live JSON responses from `https://api.quran.com/api/v4` (e.g. `/chapters`, `/verses/by_chapter/{id}`, `/reciters`, `/words`) before generating or modifying `@Serializable` Kotlin DTOs, verse models, or word-by-word timestamp parsers.
   - Purpose: Ensure zero runtime deserialization crashes due to missing or renamed schema fields.

3. **`sqlite-quran` (Database Verification & Testing)**:
   - **MANDATORY**: Always use `sqlite-quran` to inspect, seed, test, and validate SQLite / Room schemas and FTS queries for:
     - Surahs & Ayahs tables and indices.
     - Full-text search (FTS4/FTS5) for Arabic & English queries.
     - Playlists, Bookmarks (with personal notes), and Khatma progress tracking.
   - Target database: `app/src/main/assets/listen_quran.db`.

4. **`android-adb` (Device & UI Validation)**:
   - **MANDATORY**: Always use `android-adb` to:
     - Inspect UI layouts and verify Compose composables on connected devices/emulators.
     - Verify Material 3 tonal color adherence (Teal `#006C4C` primary seed, Quranic Gold `#7D5700` tertiary seed).
     - Check for text clipping in Uthmani Arabic typography (proper font padding and 2.0+ line-height for diacritics).
     - Capture `logcat` stack traces immediately when debugging runtime crashes or audio session errors.

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
   - FTS4/FTS5 Room-backed instant search.

3. **Library Screen**:
   - Pinned Liked items at the top.
   - Custom user playlists (create, reorder, export/import JSON).
   - Bookmarks with personal notes/tadabbur reflections.
   - **Hifz Repetition Mode**: Ayah range selection (From Ayah X to Y), repetition counter (`1x`, `3x`, `5x`, `10x`, `∞`), delay interval between ayahs (`0s`, `3s`, `5s`).
   - **Timed Listening / Sleep Timer**: Presets (15m, 30m, 1h, End of Surah) + exact stop time.

4. **Player Experience**:
   - **M3 Floating Mini-Player**: Floats 8dp above bottom nav, 16dp rounded, linear progress bar, shared element expansion to Full Player.
   - **Full Player**: Hero artwork (28dp shape), thick M3 slider (6dp), 64dp Play/Pause FAB, speed control (`0.5x` to `1.5x`), gapless playback engine.
   - **Synced Ayahs Display**: Lyrics-style synchronized verses with active ayah card highlighted in teal border. Word-by-word mode with transliteration and translation.
   - **Share Ayah as Image**: Export verse on curated backgrounds (nature, architecture, patterns) with attribution watermark.

5. **Settings & Extras**:
   - Theme mode (System, Dark, Light, AMOLED Pitch Black).
   - Offline storage manager (download quality MP3 128k/192k/Lossless, delete cached audio).
   - Data backup: JSON export/import for playlists and listening stats.
