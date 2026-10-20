<div align="center">
  <img src="./assets/logo.png" alt="Listen Quran Logo" width="150" height="150" style="border-radius: 28px;" />
  <h1>Listen Quran — استمع للقرآن</h1>
  <p><em>A modern, native Android Quran audio streaming and study application inspired by Spotify and Quran Audio.</em></p>

  <p>
    <a href="https://github.com/tokitauhid/listen-quran/releases/latest"><img src="https://img.shields.io/badge/Release-v1.0.0-006C4C?style=for-the-badge&logo=github&logoColor=white" alt="Version" /></a>
    <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
    <img src="https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Compose-Material%203-006C4C?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose M3" />
    <img src="https://img.shields.io/badge/Media3-ExoPlayer-00838F?style=for-the-badge&logo=google&logoColor=white" alt="Media3" />
    <img src="https://img.shields.io/badge/Room-SQLite%20Local-2E7D32?style=for-the-badge&logo=sqlite&logoColor=white" alt="Room SQLite" />
    <img src="https://img.shields.io/badge/License-MIT-F8BD48?style=for-the-badge&logo=open-source-initiative&logoColor=black" alt="License" />
  </p>
</div>

---

**Listen Quran** is a modern Quran recitation and study application for Android inspired by Spotify and Quran Audio. Built with Jetpack Compose and Material Design 3, it combines smooth audio streaming, beautiful Arabic typography, live radio stations, synchronized verse highlighting, memorization tools, and complete personal data privacy.

## 📱 App Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center" width="20%">
        <img src="./assets/screenshots/01_home_khatam.png" alt="Home & Khatma Tracker" width="100%" /><br />
        <sub><b>Home & Khatma Tracker</b></sub>
      </td>
      <td align="center" width="20%">
        <img src="./assets/screenshots/02_radio_streams.png" alt="Continuous Radio" width="100%" /><br />
        <sub><b>Continuous Radio Streams</b></sub>
      </td>
      <td align="center" width="20%">
        <img src="./assets/screenshots/03_browse_categories.png" alt="Browse Categories" width="100%" /><br />
        <sub><b>Curated Categories</b></sub>
      </td>
      <td align="center" width="20%">
        <img src="./assets/screenshots/04_reciters_discovery.png" alt="255+ Reciters Discovery" width="100%" /><br />
        <sub><b>255+ Verified Reciters</b></sub>
      </td>
      <td align="center" width="20%">
        <img src="./assets/screenshots/05_library_hifz.png" alt="Library & Sleep Timer" width="100%" /><br />
        <sub><b>Library & Sleep Timer</b></sub>
      </td>
    </tr>
  </table>
</div>

---

## ✨ Features

- 📖 **Khatam Progress Tracker** — Track your Quran reading and completion across all 30 Juz. Automatically records completed verses as you listen, with a visual progress grid and reading statistics.
- 📻 **Live Quran Radio** — Continuous 24/7 recitation broadcasts from Quran.com. Automatically reconnects if your connection drops, and preserves your place so you can resume your personal recitation anytime.
- 🎙️ **250+ Renowned Reciters** — Stream high-quality audio recitations from over 250 verified reciters across multiple recitation styles (Hafs, Warsh, Qalun, Mujawwad, Murattal), with support for downloading surahs for offline listening.
- 📜 **Synchronized Verse & Word Highlighting** — Follow along in real-time with verse and word-by-word highlights, English translations, and tap any verse to jump directly to it.
- 🔁 **Memorization (Hifz) Mode** — Focus on memorization by looping specific verse ranges, choosing repetition counts (1x, 3x, 5x, 10x, or continuous), and setting pause intervals between verses.
- 🔍 **Quick Search & Verse Jump** — Search instantly by Surah name, reciter, or jump straight to any verse by typing `Surah:Ayah` (such as `2:255` for Ayatul Kursi).
- 🛡️ **100% Private & Ad-Free** — No account required, no ads, and no tracking. All your playlists, bookmarks, and listening history stay safely on your device, with full backup and restore support.


---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **UI Framework** | Jetpack Compose + Material Design 3 (`1.3.1` via BOM `2024.12.01`) |
| **Language** | 100% Kotlin `2.1.0` (Coroutines, StateFlow, Unidirectional Data Flow) |
| **Audio Architecture** | Jetpack Media3 `1.5.1` (`ExoPlayer` + `MediaLibraryService` + `MediaSession`) |
| **Local Database** | Room Database `2.6.1` (`listen_quran.db` pre-pack + `khatam.db` isolated) |
| **Preferences** | Jetpack DataStore Preferences `1.1.1` |
| **Dependency Injection** | Dagger Hilt `2.53.1` |
| **Networking & Serialization** | Retrofit `2.11.0` + OkHttp `4.12.0` + Moshi `1.15.1` (KSP Codegen) |
| **Image Loading** | Coil Compose `2.7.0` |
| **Lists & Drag-and-Drop** | `sh.calvin.reorderable` `2.4.1` + Paging 3 |
| **Background Processing** | Jetpack WorkManager `2.10.0` |
| **Typography** | King Fahd Complex / Amiri Quran Uthmani Calligraphy (`.ttf`) |

---

## 🚀 Getting Started

### Prerequisites
- **JDK**: Java Development Kit 17 or higher
- **Android SDK**: Compile SDK `35`, Min SDK `26`
- **IDE**: Android Studio Ladybug (2024.2.1+) or command-line build tools

### 1. Clone the Repository
```bash
git clone git@github.com:tokitauhid/listen-quran.git
cd listen-quran
```

### 2. Build the Application

**Debug Build**:
```bash
./gradlew assembleDebug
```
*Output: `app/build/outputs/apk/debug/app-debug.apk`*

**Release Build (Minified with R8 & Shrink Resources)**:
```bash
./gradlew assembleRelease
```
*Output: `app/build/outputs/apk/release/app-release.apk`*

### 3. Run Unit & Durability Tests
```bash
./gradlew testDebugUnitTest
```

### 4. Install onto Connected Device / Emulator
```bash
# Debug:
./gradlew installDebug

# Release:
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 📱 Building for Mobile

### Option 1: Automated Release Build & ADB Deployment (CLI)
Build an optimized production release APK with full R8 optimization, resource shrinking, and baseline profiles:
```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Option 2: Android Studio Workflow
1. Open the repository root directory in Android Studio.
2. Allow Gradle sync to complete and download dependencies.
3. Select the `app` run configuration and choose your connected physical device or emulator.
4. Click **Run** (`Shift + F10`) or **Build > Generate Signed Bundle / APK**.

---

## 🙏 Credits & Attribution

- **[Quran.com / Quran Foundation](https://quran.com)** — Open data APIs, chapter metadata, translations, and high-fidelity recitation audio CDN via QuranicAudio.
- **[Amiri Font Project](https://github.com/aliftype/amiri)** — Authentic Uthmani Quranic Arabic typography designed by Dr. Khaled Hosny and Dr. Nezar Al-Rawi.
- **[Quran Audio & Batoul Apps](https://github.com/batoulapps)** — Inspiration for clean Islamic software engineering.

---

## 📄 Licence

MIT — see [LICENSE](./LICENSE) for details.
