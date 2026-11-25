# Tea Token System (Android)

Two screens: a branded splash screen, then a full-screen WebView that behaves like a native app.

## Change the website
Edit `app/src/main/java/com/teatoken/system/AppConfig.kt`:

```kotlin
const val WEB_URL = "https://www.google.com"   // put your site here
const val SPLASH_DURATION_MS = 2000L            // splash time
```
If your site is plain `http://` (not https), set `android:usesCleartextTraffic="true"` in `AndroidManifest.xml`.

## Open in Android Studio
1. Unzip, then in Android Studio choose **File > Open** and pick the `TeaTokenSystem` folder.
2. Let Gradle sync (Android Studio downloads the SDK 35 platform if missing).
3. Press **Run** on a device or emulator.

## WebView behaviour
- No pinch zoom or zoom buttons (also forced via the page viewport after each load)
- No text selection, long-press menu or tap highlight (form fields still editable)
- All http/https links, including `target="_blank"`, open inside the app; `tel:`, `mailto:`, `whatsapp:`, UPI and `intent:` links open the matching app
- Back button goes back in WebView history, exits on the first page
- JavaScript, DOM storage and cookies enabled; file upload and downloads supported
- No overscroll glow or scrollbars; font size ignores system scaling
- No internet: friendly offline screen with a Retry button
- Rotation does not reload the page

Package: `com.teatoken.system` · minSdk 26 · targetSdk 35 · Kotlin 2.0 · AGP 8.7 · Gradle 8.10
