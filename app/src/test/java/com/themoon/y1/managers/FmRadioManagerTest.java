package com.themoon.y1.managers;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.PowerManager;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.ShadowPowerManager;
import org.robolectric.shadows.util.DataSource;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = {FmRadioManagerTest.WakeAwarePlayer.class,
        FmRadioManagerTest.RecordingPowerManager.class})
public class FmRadioManagerTest {
    private static final String SOURCE = "MEDIATEK://MEDIAPLAYER_PLAYERTYPE_FM";
    private FmRadioManager fm;
    private PowerManager pm;

    // Robolectric's player shadows start() rather than native _start(), bypassing
    // framework stayAwake(). Restore that step so the real setWakeMode/release
    // code owns the lock; no MediaTek hardware is simulated here.
    @Implements(MediaPlayer.class)
    public static class WakeAwarePlayer extends ShadowMediaPlayer {
        @RealObject private MediaPlayer player;
        static boolean failStart;
        static boolean failStop;
        @Implementation protected void start() {
            super.start();
            ReflectionHelpers.callInstanceMethod(player, "stayAwake",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));
            if (failStart) throw new IllegalStateException("start failed");
        }
        @Implementation protected void _stop() {
            if (failStop) throw new IllegalStateException("stop failed");
            super._stop();
        }
    }

    @Implements(PowerManager.class)
    public static class RecordingPowerManager extends ShadowPowerManager {
        static int flags;
        @Implementation protected PowerManager.WakeLock newWakeLock(int value, String tag) {
            flags = value;
            return super.newWakeLock(value, tag);
        }
    }

    public static class Driver {
        public static boolean setmute(boolean mute) { return true; }
        public static boolean powerdown(int type) { return true; }
        public static boolean closedev() { return true; }
    }

    @Before public void setup() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Constructor<FmRadioManager> constructor = FmRadioManager.class.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        fm = constructor.newInstance(context);
        field("fmNativeClass").set(fm, Driver.class);
        fm.lastError = "";
        WakeAwarePlayer.failStart = false;
        WakeAwarePlayer.failStop = false;
        pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(SOURCE), new ShadowMediaPlayer.MediaInfo());
    }

    private Field field(String name) throws Exception {
        Field f = FmRadioManager.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    private void start() throws Exception {
        Method m = FmRadioManager.class.getDeclaredMethod("startFmAudio");
        m.setAccessible(true);
        m.invoke(fm);
        assertEquals("", fm.lastError);
        fm.isPowerUp = true;
    }

    @Test public void physicalDisplayOffKeepsFmAwakeUntilPowerDown() throws Exception {
        start();
        PowerManager.WakeLock lock = ShadowPowerManager.getLatestWakeLock();
        assertNotNull(lock);
        assertTrue(lock.isHeld());
        assertEquals(PowerManager.PARTIAL_WAKE_LOCK, RecordingPowerManager.flags & 0xffff);
        shadowOf(pm).setIsInteractive(false);
        assertFalse(pm.isInteractive());
        assertTrue(lock.isHeld());
        assertTrue(((MediaPlayer) field("fmPlayer").get(fm)).isPlaying());
        fm.powerDown();
        assertFalse(lock.isHeld());
        assertNull(field("fmPlayer").get(fm));
        assertFalse(fm.isPowerUp);
    }

    @Test public void restartingReleasesPreviousPlaybackLock() throws Exception {
        start();
        PowerManager.WakeLock old = ShadowPowerManager.getLatestWakeLock();
        start();
        assertFalse(old.isHeld());
        PowerManager.WakeLock current = ShadowPowerManager.getLatestWakeLock();
        assertTrue(current.isHeld());
        fm.powerDown();
        fm.powerDown();
        assertFalse(current.isHeld());
    }

    @Test public void failedAudioSetupReleasesPlayerAndLock() throws Exception {
        ShadowMediaPlayer.addException(DataSource.toDataSource(SOURCE), new IOException("routing failed"));
        Method m = FmRadioManager.class.getDeclaredMethod("startFmAudio");
        m.setAccessible(true);
        m.invoke(fm);
        assertTrue(fm.lastError.contains("routing failed"));
        assertNull(field("fmPlayer").get(fm));
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
    }
    @Test public void stopFailureStillReleasesPlaybackLock() throws Exception {
        start();
        PowerManager.WakeLock lock = ShadowPowerManager.getLatestWakeLock();
        WakeAwarePlayer.failStop = true;
        fm.powerDown();
        assertFalse(lock.isHeld());
        assertNull(field("fmPlayer").get(fm));
    }

    @Test public void startFailureAfterAcquiringLockReleasesIt() throws Exception {
        WakeAwarePlayer.failStart = true;
        Method m = FmRadioManager.class.getDeclaredMethod("startFmAudio");
        m.setAccessible(true);
        m.invoke(fm);
        assertTrue(fm.lastError.contains("start failed"));
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
        assertNull(field("fmPlayer").get(fm));
    }

}
