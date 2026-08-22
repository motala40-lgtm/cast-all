# Easy Cast — Complete Project Brief (for design collaboration)

## What this app is
Easy Cast is a native Android app (Java) that lets a user cast local video/photo/music files, or a direct video URL, from their phone to a TV — supporting both **Google Cast (Chromecast)** and **DLNA** (for smart TVs, e.g. many Samsung models, that don't have Chromecast built in). It's a real, working, in-development app being prepared for a Google Play Store launch.

- **App name:** Easy Cast
- **Package name:** com.app.castall.scanner
- **Platform:** Android only (native Java, no Kotlin, no cross-platform framework)
- **minSdk 24 / targetSdk 36 / compileSdk 36**
- **Repo:** https://github.com/motala40-lgtm/cast-all (public)

## Who's building it & how
The developer works **entirely from an Android phone** (Samsung Galaxy Z Fold5), no computer. Code is written by an AI assistant (Claude), packaged as a zip, unzipped and pushed to GitHub via Termux (a terminal app), and GitHub Actions builds the APK/AAB automatically. This matters for design feedback: **any design change has to be expressible as code** (no Figma/Sketch file exists — the XML layouts and this document are the closest things to a design source of truth).

## Current feature set (all shipped and working)
1. **Cast local media**: video, photo, or music, picked via the system Photo Picker (for video/photo) or a file picker (for music, single or multi-select).
2. **Cast from a direct URL** — tucked into a collapsible "Advanced" section, since it's used far less than local casting.
3. **Playlist**: multi-select photos/videos from the gallery grid, optionally add music tracks too, and they play through automatically — photos advance on a timer, video/music advance when playback actually finishes.
4. **Playback controls**: play/pause, ±10s skip, a real seek bar, volume control synced to the TV.
5. **Settings** (accordion/collapsible sheet, not a separate screen):
   - Theme: Light / Dark / System / **Custom** (pick your own photo as the app's background)
   - Accent color: a palette of ~10 pastel swatches + white, affects the background gradient (light theme only) and progress bars
   - Sleep timer (15/30/60 min), with a live countdown banner on the main screen while active
   - Support (email + privacy policy links)
6. **Device connection**: a bottom sheet lists discovered Cast + DLNA devices; tapping one shows an inline spinner while it connects. There's also the official system Cast icon in the header (Google's own picker, Chromecast-only).
7. **11 languages**: English, Persian (RTL), Arabic (RTL), German, Spanish, French, Swedish, Turkish, Russian, Chinese — fully translated (72 strings × 10 non-default locales).
8. Google Play–ready: adaptive app icon, signed release build (APK + AAB) via a checked-in fixed debug key + GitHub Secrets for the release key, privacy policy hosted on GitHub Pages, Feature Graphic, and full store-listing copy already written.

## Current visual design system

### Color palette (light theme; a parallel dark theme exists with adjusted values)
| Role | Hex | Notes |
|---|---|---|
| Background | `#F3F5FF` → gradient | Page background is a vertical gradient, sky-blue at the bottom fading to near-white at top; re-tinted by the chosen accent color |
| Primary blue | `#4F8EF7` (dark `#3D72D4`) | Used for the "Select Device" and "Cast Video" buttons, and the header |
| Text primary | `#1A1D29` | |
| Text secondary | `#666E80` | |
| Header bar | Blue gradient (`#4F8EF7` → `#2D6FE0`) | Rounded rect, sits at the very top |
| 4 source-button accents (fixed, don't follow the accent-color picker) | Video = mint/teal `#1E9E85`, Photo = peach/orange `#E8763B`, Music = lavender/purple `#7C5CFC`, Playlist = sky blue `#3D72D4` | Each button has a soft tinted background + a bold icon in its own hue |

### Layout structure (single main screen, top to bottom)
1. Rounded navy→blue header: app logo (small) + "EASY CAST" wordmark + Settings icon (gear) + language icon (globe) + official Cast icon
2. "Only devices on this Wi-Fi are shown" hint text
3. Sleep-timer banner (only visible when a timer is running)
4. 2×2 grid of big, colorful, rounded pill buttons: Local Video / Playlist / Photo / Music
5. Collapsible "Cast from a link" row (closed by default)
6. Big logo (192dp) centered, as a brand moment in otherwise-empty space
7. Fixed footer at the bottom: the big primary-blue "Select Device" button (label changes to "Connected to: X" once linked)

### Iconography
Custom-drawn vector icons (not a stock icon pack) for: video camera, photo, music note, playlist, link, settings gear, globe, moon (sleep timer), chevron. All are simple, single-color, filled glyphs, ~20-24dp.

### Typography
No custom font — system default (Roboto). Headline-ish text is bold + slightly larger; body/secondary text is smaller and a muted gray.

## Design goals for this next round (why you're being asked to help)
The developer wants a fresh, more experienced design perspective on:
1. Whether the current color/layout approach reads as polished/professional or "AI-generated template"-ish, and concretely how to elevate it.
2. Whether the 2×2 colorful button grid is the best pattern for the primary actions, or if something else would feel more premium.
3. General visual hierarchy, spacing, and whether the page feels cluttered or well-organized on a real phone screen (this matters a lot — it's tested exclusively on a Samsung Z Fold5's cover/main screen).
4. Any specific, concrete redesign proposals are welcome — but they need to be translatable into Android XML layouts + drawables, since that's the only way they can actually be implemented here.

## Known constraints to respect in any proposal
- Must stay a **native Android XML layout** (no Compose migration planned at this time — the whole app is View-based).
- Must keep RTL support working (Persian/Arabic are primary-ish languages for this developer).
- No ads yet, but a slot for a future ad banner was considered and removed in favor of the big logo — that decision could be revisited.
- The developer is non-technical and works only from a phone; any suggestion should come with enough detail that it can be handed to a coding AI and implemented without back-and-forth about ambiguous vagueness.
