#!/bin/bash
# Installs the AirPods RTP-timestamp fix onto a connected, rooted Y1 over adb.
# EXPERIMENTAL / OPT-IN — see README.md in this folder before running this.
#
# What it does, in order:
#   1. Confirms root is available (su -c).
#   2. Backs up the device's real /system/lib/libbluetoothdrv.so to
#      /system/lib/libbluetoothdrv_stock_backup.so (kept forever, untouched,
#      so uninstall.sh can always restore the exact original).
#   3. Renames the real driver to /system/lib/libbluetoothdrv_real.so (this
#      is the name the proxy dlopen()s at runtime to forward calls to).
#   4. Pushes the compiled proxy (libbluetoothdrv_proxy.so) in as the new
#      /system/lib/libbluetoothdrv.so, with the same permissions/ownership
#      as the file it replaces.
#
# Safe to re-run: if the backup/real files already exist, it won't overwrite
# them again, so running this twice doesn't lose the original.

set -e

REMOTE_LIB_DIR="/system/lib"
PROXY_SO="$(dirname "$0")/libbluetoothdrv_proxy.so"

if [ ! -f "$PROXY_SO" ]; then
    echo "libbluetoothdrv_proxy.so not found next to this script." >&2
    exit 1
fi

echo "== Checking device + root =="
adb shell su -c "id" || { echo "Root (su) not available over adb — aborting."; exit 1; }

echo "== Remounting /system read-write =="
adb shell su -c "mount -o rw,remount /system" || echo "Remount may already be rw, continuing."

echo "== Backing up the real driver (only if not already done) =="
adb shell su -c "[ -f $REMOTE_LIB_DIR/libbluetoothdrv_stock_backup.so ] || cp $REMOTE_LIB_DIR/libbluetoothdrv.so $REMOTE_LIB_DIR/libbluetoothdrv_stock_backup.so"

echo "== Renaming real driver -> libbluetoothdrv_real.so (only if not already done) =="
adb shell su -c "[ -f $REMOTE_LIB_DIR/libbluetoothdrv_real.so ] || cp $REMOTE_LIB_DIR/libbluetoothdrv.so $REMOTE_LIB_DIR/libbluetoothdrv_real.so"

echo "== Pushing proxy driver =="
adb push "$PROXY_SO" /data/local/tmp/libbluetoothdrv_proxy.so
adb shell su -c "cp /data/local/tmp/libbluetoothdrv_proxy.so $REMOTE_LIB_DIR/libbluetoothdrv.so"
adb shell su -c "chmod 644 $REMOTE_LIB_DIR/libbluetoothdrv.so"
adb shell rm /data/local/tmp/libbluetoothdrv_proxy.so

echo "== Done. Reboot the device (or at least toggle Bluetooth off/on) for it to take effect. =="
echo "== If anything with Bluetooth audio seems wrong afterwards, run uninstall.sh. =="
