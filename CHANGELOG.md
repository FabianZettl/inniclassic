# Changelog

All notable changes to InniClassic (formerly "JJ Launcher Classic Version") are documented here. This project is based on JJ Launcher `0.11`; this changelog covers only what changed on top of that base.

## [1.3.0] - 2026-07-29

A Music Quiz overhaul, a Now Playing polish pass, and another batch of Reddit-reported bugs — mostly sleep/wake and audio.

### Added
- **Music Quiz: five new question types** — alongside "What song is playing?", it can now ask which artist released a track, which album it's from, what year it came out, and what genre it is (each only offered when your library actually has enough distinct values to make a fair question). Also new: a visual question type showing 3 album covers side by side to pick from instead of text answers.
- **Music Quiz: all 4 answers now fit on screen at once**, no more scrolling to see the other options — the album cover was removed from the question card and chrome (badges, progress bar, score row) was shrunk to make room.

### Changed
- **Now Playing**: the album cover sits slightly lower, spacing between Title/Artist/Album is now perfectly consistent, the star rating moved between Album and the track counter, and the "X of Y" track counter is now bold at the same size as the rest of the info block.
- Documented the existing (but easy-to-miss) **synced lyrics** support in the README: drop a `.lrc` file next to a song (same filename) or embed lyrics in ID3/FLAC/ALAC tags, then long-press Center on Now Playing → Toggle Visualizer to see them scroll in sync with playback.

### Fixed
- **Screen never came back on after the backlight timer put it to sleep** — two separate bugs, both now fixed:
  - The "virtual" sleep used while FM Radio or the web server is running (so the CPU stays alive) set a flag to fake the screen off but never had any code path that turned it back off. Once it triggered, the screen stayed black forever (vibration/click feedback still worked, since the app itself never stopped running) until a full reboot.
  - The "real" sleep used during ordinary music playback simulates a power-button press to turn the display off, but nothing ever simulated a second press to turn it back on. The wheel/buttons aren't registered as Android "wake keys" on this hardware, so nothing brought the display back short of the actual hardware power button.
- **Full device lockup (required a paperclip reset) when playing FM Radio through wired headphones for a few minutes** — the backlight timer's "virtual sleep" path was also toggling Wi-Fi off, even while the FM tuner was actively running. On this chipset, FM/Wi-Fi/Bluetooth share the same combo radio hardware; killing Wi-Fi out from under a live FM session could wedge the shared chip badly enough to need a hard reset. Wi-Fi is now left alone whenever the radio is on, the same way it was already left alone whenever the web server is running.
- **Screen dimming during active use** — the out-of-the-box Backlight Timer default was 10 seconds, which is aggressive enough to feel like the screen "won't stay on" during normal browsing/listening. Default is now 1 minute (still adjustable down to 10 seconds in Settings if you want it).
- **Bluetooth headphones (reported with AirPods) noticeably quiet** — a known AVRCP "absolute volume" quirk where the phone-side volume slider becomes the headset's actual volume, and the negotiated level can end up low. Absolute volume is now disabled on connect, so headphones fall back to using their own physical volume control.
- **Some FLAC files failed to play with "Legacy Player Error: 262"** — the native decoder used for FLAC (to avoid a separate ExoPlayer FLAC hang bug) rejects a handful of files that ExoPlayer's own FLAC extension can actually decode. Failing files now automatically retry once through ExoPlayer's FLAC extension instead of just giving up; the error message also now includes the decoder's `extra` code for easier triage if a file still fails both engines.

## [1.2.0] - 2026-07-27

A big visual consistency pass across every screen, plus two new features and a round of performance/battery work.

### Added
- **Video playback** — a new "Videos" entry in the Main Menu, its own folder on the SD card (`/storage/sdcard0/Videos`), and a full-screen player with wheel-driven volume/seek (long-press Center to switch between the two, short-press to play/pause). Now powered by **libVLC** instead of ExoPlayer: ExoPlayer's precise frame-timing API only exists from Android API 21 onward, and its fallback for older devices turned out to be broken on the Y1's API 17 — audio and the position clock stayed correct, but picture played back many times faster than real time, decoupled from the audio entirely. libVLC brings its own native, battle-tested AV-sync engine that isn't tied to that Android API, giving stable, correctly-synced playback. Audio-only playback is untouched and still runs on ExoPlayer. Thumbnails in the video list and resume-position are still unaddressed for now.
- **Gapless playback** — automatically kicks in for albums and playlists of 15 tracks or fewer, as long as shuffle is off and no FLAC files are in the queue (FLAC uses a separate playback engine that can't join ExoPlayer's native queue, and large/shuffled queues intentionally keep the older, proven per-track loading path). Track transitions inside a gapless queue no longer stop and reload — ExoPlayer switches internally with no gap.
- **Favorites are now visible at a glance** — Now Playing shows a hollow heart normally and a filled red heart when the current track is favorited, instead of showing nothing at all unless it happened to already be a favorite.

### Changed
- **Consistent look across every menu** — Main Menu, Music menu, Artists, Albums, Songs, and Settings/Bluetooth/Wi-Fi lists were all using slightly different font sizes, left margins, and row padding. Everything now shares the same left alignment (flush with the status bar title), the same row spacing, and one font-size scale.
- **Right-arrow indicator** now only shows on the focused (blue) row everywhere, instead of being visible (just dimmer) on every row all the time.
- **Album rows** got a larger cover thumbnail and slightly taller rows for better legibility, at the cost of one or two fewer rows fitting on screen.
- **Status bar** redesigned to match the real iPod's proportions (thinner, smaller icons) and now consistently narrows to the left column on both the Main Menu and Music menu, letting the album cover on the right bleed all the way to the top behind it.
- **Main Menu and Music menu covers are now visually identical**, including the slow panning ("Ken Burns") animation — previously only the Main Menu had it, the Music menu's cover was a static crop.
- **Now Playing**: bigger album cover, "X of X" track counter in bold, more breathing room between the album name and track counter, and titles too long to fit now scroll (marquee) instead of just being cut off.
- **Progress/volume bar** now has a subtle glass-like highlight through the middle instead of a flat fill.

### Fixed
- **Two real bugs found while reworking the Artist/Album row styling**: the focus arrow's color was tied to the theme instead of being fixed white like the rest of the app, and — more importantly — a recycled (scrolled-past-and-back) row could have its blue focused background incorrectly reset to the normal background on every re-render regardless of whether it was actually focused.
- **Now Playing's progress ticker kept doing full work every 0.5 seconds even when Now Playing wasn't the visible screen** (e.g. browsing the library while music played in the background) — updating position, remaining time, and lyric auto-scroll on views nobody could see. Now skipped entirely unless Now Playing is actually on screen.
- **The clock/widget refresh loop ran every second forever, including during "screen off" playback** (the screen-off state is a virtual black overlay, not a real display power-off, so the app keeps running underneath) — rebuilding a date formatter and refreshing Main Menu widgets every second regardless. Now drops to one no-op tick every 15 seconds while the screen is virtually off, and skips widget work entirely outside the Main Menu.

### Optional: reduce background bloat
A number of stock Android/MediaTek system apps that a dedicated music player never uses (Exchange mail sync, the phone/telephony stack, SIM toolkit, text-to-speech, live-wallpaper demos, calendar, an OEM RAM-cleaner utility, and others) can be safely disabled to free up RAM and stop unnecessary background activity. Two of them — the stock FM Radio app and the Download Manager provider — are **not** safe to remove, since InniClassic's own Radio and PC Upload features depend on them under the hood. This isn't baked into the ROM yet; ask if you want the exact list of packages.

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
