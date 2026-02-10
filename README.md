# Vibe Reader

An Android reading companion that captures vocabulary and quotes by voice during reading sessions, then generates AI-powered weekly insights about your reading patterns.

## What It Does

While you read, Vibe Reader runs a background session tied to the book you're reading. Speak a word to instantly look it up, or say a full phrase to save it as a quote. At the end of each week, Gemini AI analyzes your captures and produces a "Weekly Vibe": a short, personalized summary of your reading themes, interests, and standout discoveries.

## Features

- **Voice Capture** with three modes:
  - *Smart Capture* auto-detects words vs. quotes based on input length
  - *Define Word* looks up definitions via Dictionary API (Wikipedia fallback)
  - *Save Quote* captures phrases with TTS read-back for verification
- **Quick Settings Tile** for capture access from the lock screen
- **Reading Sessions** tracked per-book with foreground service notification
- **Library** organizes words and quotes by book and session, with favorites, swipe-to-delete, and word/quote remapping
- **Weekly Vibe** generates Spotify Wrapped-style reading insights via Gemini 2.0 Flash

## Tech Stack

| Layer | Tech |
|-------|------|
| UI | Jetpack Compose, Material 3 |
| State | ViewModel + Kotlin Coroutines/Flow |
| Database | Room (SQLite) |
| Network | Retrofit + Gson |
| AI | Google Generative AI (Gemini 2.0 Flash) |
| APIs | Free Dictionary API, Wikipedia REST API |
| Platform | Speech Recognition, TTS, Quick Settings Tiles, Foreground Services |

## Requirements

- Min SDK 26 (Android 8.0)
- Target SDK 36
- Java 11+

## Building

Open in Android Studio and run, or from the command line:

```
./gradlew assembleDebug
```

Requires `JAVA_HOME` to point to a JDK 11+ installation (Android Studio bundles one at `<studio>/jbr`).

## Project Structure

```
app/src/main/java/com/vibereader/
  ReadingSessionService.kt      # Foreground service managing active sessions
  QuickCaptureTileService.kt    # Quick Settings tile for lock-screen capture
  ui/
    SpeechCaptureActivity.kt    # Translucent overlay for voice capture
    ReviewScreen.kt             # Weekly Vibe review screen
    SessionViewModel.kt         # Central state management
    session/SessionComponents.kt # Compose UI components
  data/
    db/                         # Room database, DAO, entities (Book, Session, Word, Quote)
    models/                     # Data models (WeeklyVibe)
    network/                    # Retrofit clients (Dictionary, Wikipedia, Gemini)
```
