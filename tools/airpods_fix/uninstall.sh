#!/bin/bash
# Reverts install.sh: restores the device's original, untouched
# /system/lib/libbluetoothdrv.so from the backup install.sh made.

set -e

REMOTE_LIB_DIR="/system/lib"

echo "== Checking device + root =="
adb shell su -c "id" || { echo "Root (su) not available over adb — aborting."; exit 1; }

echo "== Checking backup exists =="
adb shell su -c "[ -f $REMOTE_LIB_DIR/libbluetoothdrv_stock_backup.so ]" || {
    echo "No backup found at $REMOTE_LIB_DIR/libbluetoothdrv_stock_backup.so — nothing to restore (was the fix ever installed on this device?)." >&2
    exit 1
}

echo "== Remounting /system read-write =="
adb shell su -c "mount -o rw,remount /system" || echo "Remount may already be rw, continuing."

echo "== Restoring original driver =="
adb shell su -c "cp $REMOTE_LIB_DIR/libbluetoothdrv_stock_backup.so $REMOTE_LIB_DIR/libbluetoothdrv.so"
adb shell su -c "chmod 644 $REMOTE_LIB_DIR/libbluetoothdrv.so"
adb shell su -c "rm -f $REMOTE_LIB_DIR/libbluetoothdrv_real.so"

echo "== Done. Reboot the device (or toggle Bluetooth off/on) to go back to stock behavior. =="
