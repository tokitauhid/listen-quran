# Listen Quran 🎧📖

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose BOM](https://img.shields.io/badge/Compose_BOM-2024.12.01-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material_Design-3_(Material_You)-006C4C.svg)](https://m3.material.io/)
[![Jetpack Media3](https://img.shields.io/badge/Jetpack_Media3-1.5.1-00838F.svg)](https://developer.android.com/media/media3)
[![Room](https://img.shields.io/badge/Room_SQLite-FTS4%2FFTS5-2E7D32.svg)](https://developer.android.com/training/data-storage/room)
[![Android Min SDK](https://img.shields.io/badge/Min_SDK-26_(Android_8.0+)-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-35_(Android_15)-green.svg)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Listen Quran** is a modern, native Android offline-first Quran audio streaming and study application inspired by Spotify and Quran Audio. Built from the ground up with 100% Kotlin, Jetpack Compose, Material Design 3 (Material You), and Jetpack Media3.

---

## 🌟 Highlights & Philosophy

- **Zero Accounts, Zero Tracking**: 100% local, on-device data sovereignty. Your playlists, bookmarks, listening statistics, and Khatma milestones never leave your device.
- **Authentic Material Design 3**: Bespoke tonal palette anchored by Islamic Teal (`#006C4C`) and Quranic Amber/Gold (`#7D5700`), with full Dark, Light, and AMOLED Pitch Black themes.
- **Modern Audio Architecture**: Built strictly on Jetpack Media3 (`ExoPlayer` + `MediaLibraryService`). First-class lockscreen controls, notification shade integration, Android Auto readiness, and gapless audio streaming.
- **255+ Verified CDN Reciters**: Stream or download high-fidelity recitations from [Quran.com](https://quran.com) CDN across diverse Riwayahs (Hafs, Warsh, Qalun, and more).
- **Lyrics-Style Synchronized Ayahs**: Karaoke-style real-time verse tracking with English translation, word-by-word highlight mode, and instant tap-to-seek navigation.
- **Hifz Repetition Engine**: Targeted memorization loops with custom verse ranges ($A \to B$), repetition counts ($1\times, 3\times, 5\times, \infty$), and inter-verse delay intervals.

---

## 📱 Features

### 🏠 1. Home & Exploration
- **Pinned Playlists Carousel**: Immediate 1-tap access to your Liked Surahs and custom playlists right at the top.
- **Trending Mixes**: Daily & Weekly curated mixes streamed directly from Quran.com.
- **Recently Listened Grid**: Rapid resume tiles with memory of the last active Surah, Ayah, and reciter.
- **Radio Streams**: Continuous non-stop live recitation streaming.
- **Reciter Discovery**: Recommendation shelves to discover new reciters and diverse Riwayahs.
- **Khatma Progress Card**: Circular progress visualizer tracking total Quran completion across all 30 Juz and 604 pages, with quick-resume action.

### 🔍 2. Instant Search & Quick Assist
- **Direct Verse Parser**: Type any `Surah:Ayah` shortcut (e.g. `2:255` for *Ayatul Kursi*, `36:1` for *Yasin*) to get an instant 1-tap playback card.
- **Full Catalog Search**: Lightning-fast instant search across all 114 Surahs, 6,236 Ayahs, and 250+ reciters powered by pre-packaged Room SQLite FTS.
- **Quick Assist Chips**: Instant 1-tap shortcuts for popular surahs and verses (*Ayatul Kursi*, *Al-Mulk*, *Yasin*, *Ar-Rahman*, *4 Quls*).
- **Thematic Categories**: 2-column curated browsing (Ruqyah, Sleep & Peace, Juz & Hizb, Tahajjud, Quranic Duas).

### 📚 3. Library & Hifz (Memorization)
- **Custom Playlists**: Create, rename, reorder, and export custom playlists.
- **Verse Bookmarks**: Bookmark individual Ayahs with personal tadabbur (reflection) notes.
- **Hifz Repetition Engine**:
  - Configure precise verse ranges (e.g. Ayah 1 to 10).
  - Configurable repetition counter ($1\times, 3\times, 5\times, 10\times, \infty$).
  - Configurable pause intervals between ayahs ($0\text{s}, 3\text{s}, 5\text{s}$).
- **Timed Listening (Sleep Timer)**:
  - Quick duration presets (15m, 30m, 45m, 1h, 2h, 3h).
  - Exact stop-time picker (e.g. stop at 11:30 PM or Fajr).

### 🎵 4. Player Experience
- **Floating M3 Mini-Player**: Floats 8dp above the navigation bar with rounded 16dp corners, reactive linear progress indicator, and smooth shared-element expansion.
- **Full Player Screen**:
  - 28dp rounded hero artwork card with Surah metadata, Arabic script title, and reciter label.
  - 6dp thick Material 3 scrubber with elapsed and total time indicators.
  - Prominent 64dp Play/Pause FAB, shuffle, repeat, and playback speed controller ($0.5\times$ to $1.5\times$).
- **Queue Management**: Up Next sheet with item removal, manual track selection, and clear queue actions.
- **Lyrics-Style Synchronized Ayahs**:
  - High-contrast Arabic Uthmani script (`Amiri Quran` font) with 2.2 line-height multiplier to prevent diacritic clipping.
  - Active Ayah card highlighted with primary teal border.
  - Tap any verse card to immediately jump audio to that exact timestamp.
- **Share Ayah as Image**: Export verses onto curated backgrounds with customizable typography and share via Android system share sheet.

### ⚙️ 5. Settings & Data Sovereignty
- **Theme Customization**: System Default, Dark Theme, Light Theme, and pure AMOLED Pitch Black.
- **Offline Storage Manager**: View cached audio storage usage, configure download quality (MP3 128kbps, 192kbps, lossless), or clear cache in one tap.
- **Complete Data Backup**: Export and import your playlists, bookmarks, and listening stats as clean JSON files.

---

## 🏗️ Architecture & Tech Stack

The application adheres to Clean Architecture, Unidirectional Data Flow (UDF), and MVVM patterns:

```
┌────────────────────────────────────────────────────────┐
│                      UI Layer                          │
│  Jetpack Compose + Material 3 (1.3+) + Navigation BOM  │
│         MainViewModel (StateFlow & Compose UDF)        │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│                    Domain Layer                        │
│          Domain Models (Surah, Ayah, Reciter)          │
│          Repository Interfaces & Business Rules        │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│                     Data Layer                         │
│  Room Database (FTS4/5) ── DataStore ── QuranApiService│
│  (listen_quran.db pre-pack) (Preferences) (Retrofit)   │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│                 Media Playback Engine                  │
│       Jetpack Media3 (ExoPlayer + MediaLibraryService)  │
│        QuranForwardingPlayer + AudioPlayerManager      │
└────────────────────────────────────────────────────────┘
```

### Core Libraries & Versions

| Component | Library | Version |
| :--- | :--- | :--- |
| **Language** | Kotlin | `2.1.0` |
| **UI Framework** | Jetpack Compose (BOM) | `2024.12.01` |
| **Design System** | Material Design 3 | `1.3.1` (via BOM) |
| **Audio Engine** | Jetpack Media3 (ExoPlayer, Session, UI) | `1.5.1` |
| **Dependency Injection** | Dagger Hilt | `2.53.1` |
| **Local Database** | Room (with SQLite FTS4 / FTS5) | `2.6.1` |
| **Preferences** | Jetpack DataStore Preferences | `1.1.1` |
| **Networking** | Retrofit + OkHttp + Moshi (Codegen) | `2.11.0` / `4.12.0` |
| **Image Loading** | Coil Compose | `2.7.0` |
| **Compiler Plugins** | KSP + Compose Compiler Plugin | `2.1.0-1.0.29` |

---

## 🎨 Material Design 3 Palette

| Role | Dark Theme (Default) | Light Theme | AMOLED Mode |
| :--- | :--- | :--- | :--- |
| **Primary** | `#6CDAA2` (Teal) | `#006C4C` | `#6CDAA2` |
| **Primary Container** | `#005237` | `#89F8BD` | `#005237` |
| **Tertiary (Accents)** | `#F8BD48` (Gold) | `#7D5700` | `#F8BD48` |
| **Surface** | `#111413` | `#FBFDF9` | `#000000` |
| **Surface Container** | `#1D211F` | `#F0F4EF` | `#0D0D0D` |
| **Surface High** | `#282B29` | `#EAEFE9` | `#161616` |

---

## 📂 Repository Structure

```
listen-quran/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   ├── chapters.json           # All 114 Surahs metadata
│   │   │   │   ├── reciters.json           # 255 verified CDN reciters
│   │   │   │   └── listen_quran.db         # Pre-packaged Room SQLite FTS database
│   │   │   ├── java/com/listenquran/
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/              # Room database, DAOs, Entities, DataStore
│   │   │   │   │   ├── remote/             # Retrofit Quran.com API service & DTOs
│   │   │   │   │   └── repository/         # Quran, Reciter, Playlist & User repositories
│   │   │   │   ├── di/                     # Hilt DI modules (Database, Media, Network)
│   │   │   │   ├── domain/model/           # Domain models (Surah, Ayah, Reciter, Playlist)
│   │   │   │   ├── player/                 # Media3 service, AudioPlayerManager, ForwardingPlayer
│   │   │   │   └── ui/
│   │   │   │       ├── components/         # MiniPlayer and shared widgets
│   │   │   │       ├── navigation/         # Navigation graph and destination routes
│   │   │   │       ├── screens/            # Home, Search, Library, Player, Playlist, Reciter, Settings
│   │   │   │       └── theme/              # Color, Type, Theme tokens
│   │   │   └── res/
│   │   │       ├── font/amiri_quran.ttf    # Arabic Uthmani font asset
│   │   │       └── values/                 # Themes, colors, strings
│   │   └── test/                           # Unit tests (CDN URLs, Queue logic)
│   ├── compose_compiler_config.conf        # Compose compiler stability configuration
│   └── build.gradle.kts                    # App-level build configuration
├── gradle/
│   ├── libs.versions.toml                  # Version Catalog for dependencies
│   └── wrapper/                            # Gradle wrapper binaries & properties
├── AGENTS.md                               # Agent guidelines & strict architectural rules
├── CLAUDE.md                               # Guidelines for Anthropic Claude
├── .cursorrules                            # Rules for Cursor AI
├── .mcp.json                               # Model Context Protocol server registrations
├── LICENSE                                 # MIT License
└── README.md                               # Project documentation
```

---

## 🚀 Getting Started

### Prerequisites
- **JDK**: Java Development Kit 17 or higher
- **Android SDK**: Compile SDK `35`, Min SDK `26`
- **IDE**: Android Studio Ladybug (2024.2.1+) or command line build tools

### 1. Clone the Repository
```bash
git clone https://github.com/tokitauhid/listen-quran.git
cd listen-quran
```

### 2. Build the Debug APK
```bash
./gradlew assembleDebug
```
The output APK will be placed in `app/build/outputs/apk/debug/app-debug.apk`.

### 3. Run Unit Tests
```bash
./gradlew test
```

### 4. Install on Connected Device / Emulator
```bash
./gradlew installDebug
```

---

## 🧪 Testing & Verification

The project includes unit test coverage for critical playback workflows:
- **Audio CDN URL Verification**: Validates Quran.com CDN audio stream URL patterns across reciters.
- **Queue Logic Engine**: Tests queue insertions, Play Next, Up Next reordering, and loop/shuffle states.

Run tests directly:
```bash
./gradlew testDebugUnitTest --info
```

---

## 🛠️ Developer & Agent Tooling

This repository is pre-configured with Model Context Protocol (MCP) servers defined in `.mcp.json` and `.cursor/mcp.json`:

- **`context7`**: Instant documentation and API verification for modern Jetpack Compose & Media3 APIs.
- **`sqlite-quran`**: Direct database inspection and verification of the pre-packaged SQLite database (`app/src/main/assets/listen_quran.db`).
- **`fetch`**: Live verification of Quran.com API v4 schemas.
- **`android-adb`**: Automated UI inspection, screenshot capture, and logcat diagnostics on connected devices.

Refer to [`AGENTS.md`](AGENTS.md) for architectural rules and development guidelines.

---

## 📄 License

This project is open-source and released under the [MIT License](LICENSE).
