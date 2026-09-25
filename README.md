<p align="center">
  <img src="docs/zinc_music_logo.png" width="140" alt="Zinc Music Logo" />
</p>

<h1 align="center">Zinc Music</h1>

<p align="center">
  A modern, unified Android music player built with Jetpack Compose, Material Design 3 Expressive and advanced audio processing for local libraries and online streaming.
</p>

<p align="center">
  <b>Hybrid Streaming | Expressive UI | Dynamic Theming | Volume Normalization | Open Source</b>
</p>

<p align="center">
  <a href="https://github.com/EpsilonMusicApp/ZincMusic/releases"><img src="https://img.shields.io/github/release/EpsilonMusicApp/ZincMusic.svg?theme=dark" alt="Release" /></a>
  <a href="https://github.com/EpsilonMusicApp/ZincMusic/actions"><img src="https://img.shields.io/github/actions/workflow/status/EpsilonMusicApp/ZincMusic/build.yml?theme=dark" alt="Build Status" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/EpsilonMusicApp/ZincMusic.svg?theme=dark" alt="License" /></a>
</p>

---

## About Zinc Music

**Zinc Music** is a next-generation Android audio experience that unifies local music collections and online streaming into a single, cohesive application. Built natively with Kotlin and Jetpack Compose, it features an adaptive user interface that dynamically extracts colors from album artwork in real time to generate custom Material Design 3 Expressive themes.

Whether playing high-fidelity offline files from internal storage or streaming from YouTube Music, Zinc Music delivers high-performance playback through AndroidX Media3 (ExoPlayer), with studio-grade volume normalization, custom crossfading, multi-provider lyrics, synchronized karaoke rendering and responsive system haptics.

## Features

### Hybrid Playback System
- **Local Storage Library:** High-speed storage scanner supporting MP3, FLAC, AAC, WAV and standard Android audio formats.
- **Online Streaming:** Built-in InnerTube API integration to search, browse and stream audio without requiring accounts.
- **Unified Queue Management:** Combine local audio files and online streams into a single playback queue.

### Advanced Audio Engineering
- **Real-Time Volume Normalization (LUFS):** On-the-fly gain analysis ensuring uniform loudness across offline and online tracks.
- **Custom Crossfade:** Smooth, configurable transitions between tracks.
- **Built-in Equalizer and Effects:** Multi-band equalizer with presets, bass boost, loudness enhancer and reverb.

### Synchronized Lyrics and Player Canvas
- **Multi-Provider Lyrics Engine:** Automatic fetching across multiple sources.
- **Word-by-Word Karaoke:** High-precision TTML and LRC syllable sync rendering.
- **Interactive Player Canvas:** Dynamic background visualizer synced to audio playback.

### Premium Material Design 3 Expressive UI
- **Dynamic Color Engine:** Real-time palette generation derived from track artwork.
- **Smooth Motion:** Spring-physics animations, fluid shimmer effects and responsive layouts.
- **Appearance Customization:** Pure dark (AMOLED) mode, custom accent themes and adaptive widgets.

## Tech Stack

- **Language:** Kotlin + Java interop
- **UI:** Jetpack Compose, Material 3 Expressive, ViewBinding
- **Audio:** AndroidX Media3 (ExoPlayer), FFmpeg-kit, Loudness normalization
- **Data:** Room, Retrofit, Gson
- **DI:** Hilt (KSP)
- **Min SDK:** 25 (Android 7.1) / **Target SDK:** 37

## Installation

Download the latest signed APK from [Releases](https://github.com/EpsilonMusicApp/ZincMusic/releases), or grab a fresh build from the [Actions](https://github.com/EpsilonMusicApp/ZincMusic/actions) tab (debug and release APKs are attached to every run).

## Building from Source

```bash
git clone https://github.com/EpsilonMusicApp/ZincMusic.git
cd ZincMusic
./gradlew assembleDebug
```

Release builds can be signed by setting `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_ALIAS_PASSWORD` environment variables.

## License

Zinc Music is licensed under the [Apache License 2.0](LICENSE).
