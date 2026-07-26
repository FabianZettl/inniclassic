# Changelog

All notable changes to InniClassic (formerly "JJ Launcher Classic Version") are documented here. This project is based on JJ Launcher `0.11`; this changelog covers only what changed on top of that base.

## [1.1.2] - 2026-07-27

Another batch of Reddit-reported bugs, this time from multiple users.

### Added
- **Shuffle**: a "Shuffle" row now appears at the top of All Songs, matching the stock Y1 firmware — picks a random play order and starts playback immediately.
- **FM Radio** is back in the Main Menu (both the light and dark iPod Classic theme) — it had quietly gone missing from the theme config.

### Fixed
- **Crash when browsing Artists, Album Artists, Albums, Songs, or Genres** — for some libraries, a song with a missing artist or album tag could be stored with a `null` value; anything that later compared against it (e.g. building the Albums list, matching songs to an artist) threw a crash instead of just treating it as "Unknown". Every song now always gets a safe "Unknown Artist"/"Unknown Album" fallback, closing this off for good.
- **FLAC files stuck at 0:00** — FLAC playback was supposed to fall back to a dedicated engine on devices where ExoPlayer's FLAC support hangs, but the switch that was supposed to enable it was never actually turned on. It's wired up correctly now.
- **Screen turning off during active use** — spinning the wheel didn't reset the auto-lock timer (the code that did lived in a method that never actually runs for wheel input), so the screen could sleep mid-session unless you pressed the center button. Any input now resets the timer correctly.
- **Charging indicator** — the battery icon was supposed to show a small lightning bolt while charging, but the drawing code for it had been left disabled, so charging only caused a faint color shift. It now draws a proper bolt icon.
- Long album/track names could visually run into the battery/Bluetooth icons in the status bar. Long titles are now truncated with an ellipsis instead of overlapping.

## [1.1.1] - 2026-07-23

Two bug fixes reported by u/withclay on Reddit. Also: the project is now named **InniClassic**, and installable as a full ROM.

### Added
- **Flashable ROM**: `rom.zip`, installable directly via the [Innioasis Updater](https://www.innioasis.com/pages/download) — no ADB required (the Updater expects this exact filename). Ships the latest Y1 Firmware 3.1.2 with InniClassic pre-installed as the system launcher.

### Changed
- Project renamed to **InniClassic** (still based on and credited to JJ Launcher — see README). No functional/version change, same 1.1.1 build.

### Fixed
- Wireless PC Upload (web server) status text was hardcoded white, making it invisible against the iPod Classic theme's white background once the server was started. Also fixed the same hardcoded-near-white issue on the Bluetooth/Wi-Fi/Brightness/Storage/Settings screen titles, which had the identical problem.
- The wheel stayed active while the device was locked (screen off) — turning it could still change the volume. The guard against this already existed in code, but it lived in a method that never actually runs on this device (`dispatchKeyEvent` intercepts and consumes these keys before it would ever be reached); moved the guard to where it's actually effective.

## [1.1.0] - 2026-07-23

Focused on getting closer to the real iPod Classic experience — especially a proper depth to the Now Playing screen, plus a cleanup pass on the Main Menu and some visual consistency fixes.

### Added
- **Now Playing sub-menus**: a center-click on the Now Playing screen now cycles through four states, exactly like a real iPod Classic — Progress bar → Seek (scrub bar with a diamond thumb, wheel jumps the track ±5s) → Shuffle & Repeat (quick toggle, wheel cycles each) → Rating (wheel sets 1-5 stars) → back to Progress. The wheel reverts to volume control outside of these states, same as on a real device.
- **Star ratings**: rate any track 1-5 stars from the Now Playing Rating state above; shown as ★★★☆☆ under the track counter.
- **On-The-Go playlist**: the classic always-available instant playlist. Reachable as a one-tap "Add to On-The-Go" action from the Now Playing hold-menu, or as a pinned entry in the full Add to Playlist dialog.
- **Now Playing hold-menu**: long-press Center on Now Playing for Add to On-The-Go, Browse Album, Browse Artist, and Cancel — matching the real iPod's menu, plus a Toggle Visualizer entry for this fork's bonus spectrum/lyrics view.
- **Composers** grouping in the Music menu, alongside the existing Artists/Albums/Genres/Years grouping.
- **Fast-scroll letter jump**: spin the wheel quickly through an alphabetized list (Artists/Albums/Songs/Genres/Composers/Search) to jump straight to the next first-letter group, with an on-screen letter overlay while jumping — tuned so it triggers reliably without being overly twitchy on a quick spin.
- Status bar now shows the current screen's name (Music/Now Playing/Settings/etc.) instead of a clock, matching the real iPod's title bar.

### Changed
- **Main Menu cleanup**: Cover Flow, Audiobooks, Folders, Years, Recently Added, and My Favorites have moved out of the Main Menu and into the Music menu where they belong on a real iPod. Main Menu is back down to Now Playing, Music, Music Quiz, Podcasts, Bluetooth, Wi-Fi, Settings, and Web Server.
- The Main Menu's split-view album cover now genuinely fills the entire remaining screen edge-to-edge (computed from the real screen size) instead of a fixed-size box, so it bleeds above the status bar the same way the Music menu's cover panel already did.

### Fixed
- Menu text size was inconsistent between screens: Music-menu-style rows (Artists/Albums/Composers/Songs/Settings) were sized in `sp` while the Main Menu's buttons were sized in raw pixels — same nominal number, different unit, so they never quite matched. Everything now renders at the exact same size.

## [1.0.0] - 2026-07-21

First public release.

### Added
- Bundled "iPod Classic" theme:
  - Two-pane Main Menu with a slowly panning, full-bleed album cover on the right that extends up behind the status bar
  - Two-pane Music menu (Cover Flow, Playlists, Artists, Albums, Songs, Genres, Search) with the same cover panel
  - Redesigned Now Playing screen: angled cover with reflection, centered track info, thick square progress bar
  - Redesigned Artists/Albums lists: icon-free artist rows, large-cover two-line album rows (bold name + song count), "All Songs" shortcut per artist
  - New text search screen (title/artist/album)
  - System-wide bold Nimbus Sans typography, tightened list indents, consistent status bar color, gradient battery icon
- Last.fm scrobbling:
  - Local Rockbox-style `.scrobbler.log` (Audioscrobbler 1.1 format)
  - Live scrobbling via the Last.fm API with browser-based login (no on-device typing required)
- Music Quiz: a first version of the classic iPod Music Quiz mini-game built from your own library (10s clips, 5-answer rounds, lives, score), styled after the original's 2000s "Fruitiger Aero" look
- OGG Vorbis playback and library scanning, with automatic detection of Opus-encoded files mislabeled with an `.ogg` extension
- Main Menu shortcuts for Music Quiz, Podcasts, Audiobooks, Folders, Years, Recently Added, and My Favorites

### Fixed
- Duplicate songs appearing in album track lists after interrupted library scans
- Missing album art in Artist → Albums lists (now falls back to folder `cover.jpg`/`folder.jpg`)
- OGG files not appearing in the library at all
- Out-of-memory crash loop while scanning very large libraries on-device
- Album art forced to a true 1:1 crop regardless of source image aspect ratio

### Changed
- Last.fm API credentials are no longer hardcoded; they're read from a local, gitignored `local.properties` file (see README)
