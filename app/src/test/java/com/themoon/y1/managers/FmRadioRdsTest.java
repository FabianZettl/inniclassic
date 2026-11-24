package com.themoon.y1.managers;

import android.content.Context;
import android.media.MediaPlayer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.After;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FmRadioRdsTest {
    private static final String SOURCE = "MEDIATEK://MEDIAPLAYER_PLAYERTYPE_FM";
    private FmRadioManager fm;

    public static class Driver {
        public static boolean setmute(boolean mute) { return true; }
        public static boolean powerdown(int type) { return true; }
        public static boolean closedev() { return true; }
    }

    public static class RdsDriver extends Driver {
        static int support;
        static int enableResult;
        static boolean enabled;
        static boolean failRead;
        static short events;
        static CountDownLatch readLatch;
        public static int isRDSsupport() { return support; }
        public static int rdsset(boolean on) { enabled = on; return enableResult; }
        public static short readrds() {
            readLatch.countDown();
            if (failRead) throw new IllegalStateException("read failed");
            return events;
        }
        public static byte[] getPS() { return " STATION ".getBytes(java.nio.charset.StandardCharsets.US_ASCII); }
        public static byte[] getLRText() { return "Artist - Song\rpadding".getBytes(java.nio.charset.StandardCharsets.US_ASCII); }
        public static boolean tune(float freq) { events = 0; return true; }
        public static short[] autoscan() { events = 0; return new short[] { 995 }; }
    }

    @After public void cleanup() { fm.powerDown(); }

    @Before public void setup() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Constructor<FmRadioManager> constructor = FmRadioManager.class.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        fm = constructor.newInstance(context);
        field("fmNativeClass").set(fm, Driver.class);
        fm.lastError = "";
        fm.setInfoFields(false, false);
        RdsDriver.support = 1;
        RdsDriver.enableResult = 1;
        RdsDriver.enabled = false;
        RdsDriver.failRead = false;
        RdsDriver.events = 0x0048;
        RdsDriver.readLatch = new CountDownLatch(1);
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

    private void startMetadata() throws Exception {
        start();
        field("fmNativeClass").set(fm, RdsDriver.class);
        fm.setInfoFields(true, true);
        assertTrue(RdsDriver.readLatch.await(3, TimeUnit.SECONDS));
    }

    @Test public void extendedInfoIsOptInAndPersists() throws Exception {
        assertFalse(fm.isExtendedInfoEnabled());
        fm.setInfoFields(true, true);
        assertTrue(RuntimeEnvironment.getApplication().getSharedPreferences("Y1_SETTINGS", 0)
                .getBoolean("radio_show_station_name", false));
        assertNull(field("rdsReader").get(fm)); // No reader while FM is off.
    }

    @Test public void metadataReadsBothFieldsAndToggleOffStopsReader() throws Exception {
        startMetadata();
        assertEquals("STATION\nArtist - Song", fm.getExtendedInfoText());
        assertTrue(RdsDriver.enabled);
        assertEquals("STATION", fm.getExtendedInfoText(true, false));
        assertEquals("Artist - Song", fm.getExtendedInfoText(false, true));
        assertEquals("", fm.getExtendedInfoText(false, false));
        fm.setInfoFields(false, false);
        assertEquals("", fm.getExtendedInfoText());
        assertFalse(RdsDriver.enabled);
        assertNull(field("rdsReader").get(fm));
        assertTrue(fm.isPowerUp);
        assertTrue(((MediaPlayer) field("fmPlayer").get(fm)).isPlaying());
    }

    @Test public void retuneAndScanClearPreviousStationInformation() throws Exception {
        startMetadata();
        assertEquals("STATION\nArtist - Song", fm.getExtendedInfoText());
        assertTrue(fm.tune(99.5f));
        assertFalse(fm.getExtendedInfoText().contains("STATION"));
        RdsDriver.events = 0x0048;
        RdsDriver.readLatch = new CountDownLatch(1);
        assertTrue(RdsDriver.readLatch.await(3, TimeUnit.SECONDS));
        assertTrue(fm.getExtendedInfoText().contains("STATION"));
        assertArrayEquals(new float[] {99.5f}, fm.autoScan(), 0.01f);
        assertFalse(fm.getExtendedInfoText().contains("STATION"));
    }

    @Test public void powerDownDisablesRdsAndReleasesPlayer() throws Exception {
        startMetadata();
        fm.powerDown();
        assertFalse(RdsDriver.enabled);
        assertNull(field("rdsReader").get(fm));
        assertEquals("", fm.getExtendedInfoText());
        assertNull(field("fmPlayer").get(fm));
    }

    @Test public void unavailableDriverDoesNotInterruptAudio() throws Exception {
        start();
        fm.setInfoFields(true, true); // Driver lacks optional RDS methods.
        assertEquals("RDS unavailable", fm.getExtendedInfoText());
        assertTrue(fm.isPowerUp);
        assertTrue(((MediaPlayer) field("fmPlayer").get(fm)).isPlaying());
    }

    @Test public void failedEnableAndReadDisableRdsWithoutStoppingAudio() throws Exception {
        start();
        field("fmNativeClass").set(fm, RdsDriver.class);
        RdsDriver.enableResult = 0;
        fm.setInfoFields(true, true);
        assertEquals("RDS unavailable", fm.getExtendedInfoText());
        assertFalse(RdsDriver.enabled);
        RdsDriver.enableResult = 1;
        RdsDriver.failRead = true;
        fm.setInfoFields(true, true);
        assertTrue(RdsDriver.readLatch.await(3, TimeUnit.SECONDS));
        assertEquals("RDS unavailable", fm.getExtendedInfoText());
        assertFalse(RdsDriver.enabled);
        assertTrue(fm.isPowerUp);
    }

    @Test public void decoderHandlesPaddingTerminatorsAndNull() {
        assertEquals("", FmRadioManager.decodeRdsText(null));
        assertEquals("ABC", FmRadioManager.decodeRdsText(new byte[] {32,65,66,67,0,88}));
        assertEquals("ABC", FmRadioManager.decodeRdsText(new byte[] {65,66,67,13,88}));
    }

    @Test public void eitherFieldEnablesRdsAndBothOffStopIt() throws Exception {
        startMetadata();
        fm.setInfoFields(false, true);
        assertTrue(fm.isExtendedInfoEnabled());
        assertTrue(RdsDriver.enabled);
        fm.setInfoFields(true, false);
        assertTrue(fm.isExtendedInfoEnabled());
        assertTrue(RdsDriver.enabled);
        fm.setInfoFields(false, false);
        assertFalse(fm.isExtendedInfoEnabled());
        assertFalse(RdsDriver.enabled);
        assertNull(field("rdsReader").get(fm));
        assertTrue(fm.isPowerUp);
    }

    @Test public void previousMasterOffMigratesToBothFieldsOff() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("Y1_SETTINGS", 0).edit()
                .putBoolean("radio_extended_info", false)
                .putBoolean("radio_show_station_name", true)
                .putBoolean("radio_show_radio_text", true).commit();
        Constructor<FmRadioManager> constructor = FmRadioManager.class.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        FmRadioManager migrated = constructor.newInstance(context);
        assertFalse(migrated.isExtendedInfoEnabled());
        assertFalse(context.getSharedPreferences("Y1_SETTINGS", 0).getBoolean("radio_show_station_name", true));
        assertFalse(context.getSharedPreferences("Y1_SETTINGS", 0).getBoolean("radio_show_radio_text", true));
        assertFalse(context.getSharedPreferences("Y1_SETTINGS", 0).contains("radio_extended_info"));
    }

}
