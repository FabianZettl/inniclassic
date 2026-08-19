# iPod Classic (6th/7th Gen) Parity Plan

Goal: turn JJ Launcher Classic Version into as close to a 1:1 iPod Classic (2007/2009 "iPod classic") clone as makes sense for a music-focused device. Based on research against Apple's official iPod classic User Guide (see chat history / commit for sources).

Ordered by "quick + clean to implement" first, working up to the bigger structural features. Check items off as they land; each phase should be shippable/testable on its own before moving to the next.

## Already done (confirmed in code, no action needed)

- [x] Music menu: Cover Flow, Playlists, Artists, Albums, Songs, Genres, Search
- [x] Now Playing: cover art, title/artist/album, "X of Y", progress bar
- [x] Cover Flow
- [x] Fast-scroll letter jump + on-screen letter overlay
- [x] Shuffle Mode toggle
- [x] Repeat Mode toggle (Off/One/All)
- [x] Button Sound (= Clicker) toggle
- [x] Equalizer & Audio Effects (EQ presets)
- [x] Playback speed setting (audiobook-style)
- [x] Brightness setting
- [x] Date & Time settings screen
- [x] Language selection
- [x] Main Menu item visibility editor (Settings → Main Menu Items) — matches Settings → Main Menu on real iPod
- [x] Storage screen (pie chart: total/used/free) — partial "About" equivalent
- [x] Music Quiz (older-style "Music Quiz" game, not the later "iQuiz")

## Phase 1 — Settings quick wins (low risk, mostly UI + prefs)

Highest fidelity-per-effort. Do these first.

- [x] **Music Menu item visibility editor** — "Music Menu Items" row in Settings, same `hide_btn_<id>` pattern as Main Menu editor
- [x] **About screen** — Settings → About, song/album/artist counts + version + device model
- [x] **Backlight Timer** setting — also tuned this since (default lowered from 10s to 1 minute after user reports of the screen feeling too aggressive)
- [x] **24-Hour Clock** toggle + **Time in Title Bar** toggle — both live as `is24HourFormat`/`isTimeInTitleBar`
- [x] **Volume Limit** setting — `volumeLimitMax`, Settings → Volume Limit
- [x] **Sound Check** toggle — `isSoundCheckEnabled`, Settings → Sound Check
- [x] **Legal** screen — Settings → Legal
- [x] **Reset All Settings** — Settings → Reset All Settings
- [ ] Search results: small type indicator per row (Song/Artist/Album/Podcast) — search still dumps everything into the same flat song-list view (`buildVirtualSongs()` with `virtualQueryType = "SEARCH"`), no per-row badge yet. Last real gap in this phase.

## Phase 2 — Now Playing & library depth (medium effort, high "iPod feel")

- [x] **Star rating** (1–5 stars) — persisted via path-keyed `SharedPreferences` (`rating_<path>`), shown on Now Playing as ★★★☆☆ under the track counter. Editable two ways, matching the real iPod: (1) the Now Playing bottom-bar Rating state (see below), wheel turn = ±1 star; (2) no longer a separate dialog — the old hold-menu "Rate Song"/"Clear Rating" dialog was removed once the inline widget shipped.
- [x] **Lyrics** — already fully implemented pre-existing (embedded USLT/`©lyr`/m4b-chapter + external `.lrc`), confirmed working, no changes needed
- [x] **Composers** grouping in Music menu — new `COMPOSER` tag extracted in all 4 custom metadata parsers (Opus/Vorbis/FLAC/ALAC) + stock `MediaMetadataRetriever`, grouped exactly like Genres/Years, new "Composers" entry between Genres and Search
- [ ] **Compilations** grouping — explicitly skipped: doesn't fit the existing string-grouping pattern well and is low value for personal (non-various-artists-heavy) libraries. Revisit only if requested.
- [x] **On-The-Go playlist** — fixed filename `On-The-Go.m3u8`, auto-created empty on first use, reusing the existing M3U append engine. Reachable two ways: pinned first entry in the full Add-to-Playlist dialog, and as a one-tap "Add to On-The-Go" action directly in the Now Playing hold-menu (matches real iPod, which doesn't route this through a picker).
- [x] **Now Playing bottom-bar 4-state cycle** — Center-click on Now Playing now cycles Progress bar → Seek (scrub bar with diamond thumb, wheel jumps ±5s) → Shuffle & Repeat (quick toggle, wheel cycles each) → Rating (wheel ±1 star) → back to Progress, exactly like real iPod Classic. Wheel reverts to volume control outside of these sub-states.
- [x] Now Playing hold-menu — rebuilt to match the real iPod's actual menu order/wording: Add to On-The-Go, Browse Album, Browse Artist, Cancel. Plus one non-canonical extra ("Toggle Visualizer", for our bonus spectrum/lyrics-view feature since it lost its old click-to-toggle trigger when Center click got reassigned to the state cycle above).
- [ ] **Edit Song** (tag editor) — deferred by request: real ID3/Vorbis-comment/MP4-atom tag writing is meaningfully more effort/risk (can corrupt files if done wrong) than the rest of this phase. Not started.

All Phase 2 items above (except Edit Song, explicitly deferred) compiled clean and were verified on-device. The On-The-Go *write* itself could not be end-to-end confirmed on one earlier test pass because that device's SD card was mounted read-only mid-session (`errors=remount-ro`, pre-existing filesystem corruption unrelated to this change); the write path is shared with the already-working "add to existing playlist" feature, so no further app-side fix is expected to be needed.

### Also landed this pass (polish, not tracked as their own phase items)
- Top status bar now shows the current screen's name (Music/Now Playing/Settings/etc.) instead of a live clock, matching the reference look; the opt-in "Time in Title Bar" clock inside Browser mode is unaffected/separate.
- Fixed a real font-size bug: Music-menu-style rows (Artists/Albums/Composers/Songs/Settings rows) were sized in `sp` while Main Menu's theme-driven buttons were sized in raw `px`— same nominal number, different unit. Unified everything to the Main Menu's px-based sizing.
- Main Menu's split-view cover now fills the entire remaining screen area (computed from real screen pixels) instead of a fixed 238×325dp box from the theme config, so it bleeds above the status bar edge-to-edge like the Music Menu's cover pane does.

## Phase 3 — Extras section (new area, larger but self-contained)

Each of these is independent — can ship one at a time without touching the others.

- [ ] New **Extras** top-level menu container
- [ ] **Stopwatch** (start/pause/lap, session log with date/time + lap stats)
- [ ] **Clocks** (add/remove cities, multi-timezone display)
- [ ] **Alarms** + **Sleep Timer** (needs AlarmManager for real background firing)
- [ ] **Screen Lock** (combination code, lock/unlock screen)
- [ ] **Notes** (simple `.txt` file reader from a Notes folder)
- [ ] Games: **Klondike** (Solitaire), **Vortex** — full mini-games, tackle only after everything above is solid
- [ ] **Voice Memos** — only if the Y1 actually has a mic; verify hardware first

Lowest priority / probably skip unless there's real demand: **Contacts**, **Calendar**, **To-Do lists** — these need a real PC-side sync pipeline (iTunes/Outlook/iCal equivalent) that doesn't exist here, so the value is low relative to effort.

## Phase 4 — Big structural features (large effort, questionable value on an MP3 player)

Revisit only after Phases 1–3 are done and if there's still appetite for it.

- [x] **Videos** top-level playback feature — shipped since 1.2.0 (own "Videos" folder on SD card, full-screen wheel-driven playback, powered by libVLC after ExoPlayer's frame-timing turned out broken on this device's API 17; thumbnails/resume-position still not done)
- [ ] **Photos** + slideshow (+ optional TV-out, if the hardware even supports it)
- [ ] **Genius** / Genius Mixes / Genius Playlists — a real recommendation engine is a lot of work; a pragmatic fake version (cluster by genre + artist + similar tempo/decade) could get 80% of the feel for far less effort, worth considering as a scoped-down substitute rather than the full thing

## Notes

- Last.fm scrobbling and OGG Vorbis support are intentional additions beyond the original iPod — keep them, they're not "gaps" to fix.
- Our Music Quiz is modeled on the earlier (pre-iPod-classic) standalone "Music Quiz" game rather than the later "iQuiz" — that's fine, it's what was actually researched and asked for.
