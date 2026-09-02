# AirPods Pro 2 silent-audio fix (experimental, opt-in)

Fixes AirPods Pro 2 (and reportedly other AirPods models) connecting over Bluetooth but staying completely silent on the Y1. **Not bundled into `rom.zip` or the APK** — this replaces a system-level native library, not app code, and it's opt-in on purpose. Read this whole file before running `install.sh`.

## Credit

All of the actual reverse-engineering and the fix itself is [Semy0nBu's work](https://github.com/Semy0nBu/y1-airpods-rtpfix), originally built for Rockbox on the Y1. `libbluetoothdrv_proxy.c` here is their source, unmodified, mirrored so it can be rebuilt without depending on that repo staying up. We only compiled it and wrote the install/uninstall scripts below.

## What it actually does

It replaces `/system/lib/libbluetoothdrv.so` (MediaTek's closed-source Bluetooth driver — not something InniClassic ships or controls) with a small proxy that intercepts every call, applies two rewrites, then forwards to the real driver (renamed alongside it to `libbluetoothdrv_real.so`, never deleted):

1. Rewrites the SBC codec's negotiated `max_bitpool` from `0x35` down to `0x23` during setup.
2. Replaces the RTP timestamp on every outgoing A2DP/SBC audio packet with an internally-tracked, perfectly regular counter, instead of whatever the stock MediaTek stack computed. AirPods are reported to silently drop the audio stream when they don't like the stock stack's timestamps; this fix is what unblocks them.

**Important: both rewrites apply to every Bluetooth device you connect, not just AirPods** — there's no per-device detection. It's built from the same byte-pattern matching Semy0nBu reverse-engineered on their own hardware, not derived from or tested against our exact Y1 firmware build.

## What we verified before packaging this

- The real `/system/lib/libbluetoothdrv.so` shipped in our own `system.img` exports exactly the 5 functions the proxy expects (`mtk_bt_enable`, `mtk_bt_disable`, `mtk_bt_write`, `mtk_bt_read`, `mtk_bt_op`) with matching signatures — so this is the right driver architecture for our device, not a guess.
- `libbluetoothdrv_proxy.so` in this folder was cross-compiled clean (no errors or warnings) against Android API 17 / armeabi-v7a using the project's existing NDK (r10e), matching the Y1's actual OS version and CPU ABI.

## What we could NOT verify

Nobody on the InniClassic side has run this on a real Y1 yet. We can't confirm:
- That the packet-format assumptions (RTP header layout, SBC framing byte offsets) hold on our exact firmware build vs. the one Semy0nBu tested on.
- Whether the two unconditional rewrites cause any regression for Bluetooth devices other than AirPods (regular Bluetooth headphones/speakers, in particular).
- General runtime stability of the proxy under real playback load.

If it goes wrong, the realistic failure mode is Bluetooth audio breaking (silence, stutter, or a Bluetooth service crash) for whatever's connected — not a bricked device, since `/system` stays otherwise untouched and `install.sh` always keeps an unmodified backup of the original driver.

## Requirements

- A rooted Y1 connected over `adb` (the InniClassic app itself already requires root for several features, so if that's working, this will too).
- `adb` on your PC, in your `PATH`.

## Install

```bash
cd tools/airpods_fix
./install.sh
```

Then reboot the device (or toggle Bluetooth off and back on) and reconnect your AirPods.

## Uninstall / revert to stock

If anything with Bluetooth seems off afterwards — for AirPods or any other device — revert immediately:

```bash
cd tools/airpods_fix
./uninstall.sh
```

This restores the exact original `libbluetoothdrv.so` from the backup `install.sh` made and removes the renamed/proxy files. Reboot (or toggle Bluetooth) afterwards.

## Rebuilding from source

If you'd rather not run a binary someone else built:

```bash
NDK=/path/to/android-ndk-r10e
GCC="$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/bin/arm-linux-androideabi-gcc"
"$GCC" --sysroot="$NDK/platforms/android-17/arch-arm" -fPIC -shared -Wall \
  -march=armv7-a -mfloat-abi=softfp -mfpu=neon \
  -o libbluetoothdrv_proxy.so libbluetoothdrv_proxy.c -ldl -llog
```

Needs an old NDK (r10e used here) that still supports `android-17` as a platform target — modern NDKs dropped support for API levels this old.
