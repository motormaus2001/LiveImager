# Live Imager — real-time multiband stereo imager for Android

Inspired by the Ozone Imager workflow: polar-sample / Lissajous vectorscope, correlation meter,
4-band stereo width with draggable crossovers, and Stereoize — all running in real time.

## Get the APK (no Android Studio needed)
1. Create a new repository on github.com (can be private).
2. Upload the contents of this folder (keep the `.github` folder!) — "Add file → Upload files".
3. Open the **Actions** tab → "Build APK" runs automatically (~3–4 min).
4. Open the finished run → **Artifacts** → download `LiveImager-apk` → unzip → `LiveImager.apk`.
5. Copy to the phone, open it, allow "install unknown apps".

## Or build in Android Studio
Open the folder, let it sync, then Build → Build APK(s).

## Modes
| Mode   | What happens |
|--------|--------------|
| File   | Decodes any audio file (mp3, m4a, flac, wav, ogg…), processes it live, loops. Bypass for A/B. |
| Mic    | Live input (phone mics, USB interface) → imager → headphones, low latency. |
| System | Scope + meters on whatever other apps play (Android 10+). Analysis only: Android doesn't allow modifying other apps' audio. Some apps (e.g. Spotify) block capture. |

Requirements: Android 8.0+ (System mode: Android 10+).
