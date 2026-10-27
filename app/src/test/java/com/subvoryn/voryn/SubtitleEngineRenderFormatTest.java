package com.subvoryn.voryn;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression tests for SRT/VTT cues used by the hardsub renderer. */
public class SubtitleEngineRenderFormatTest {
    @Test public void srtCueRetainsTextAndTimestampsWhenExportedForRendering() {
        String input = "1\n00:00:01,000 --> 00:00:02,250\nHello, world!\n\n";
        List<SubtitleCue> cues = SubtitleEngine.parse(input, SubtitleEngine.FORMAT_SRT);
        assertEquals(1, cues.size());
        assertEquals(1000L, cues.get(0).startMs);
        assertEquals(2250L, cues.get(0).endMs);
        String ass = SubtitleEngine.export(cues, SubtitleEngine.FORMAT_ASS, SubtitleEngine.POSITION_ORIGINAL);
        assertTrue(ass.contains("Dialogue: 0,0:00:01.00,0:00:02.25,Default,,0,0,25,,Hello, world!"));
    }

    @Test public void vttCueRetainsMillisecondTimingWhenExportedForRendering() {
        String input = "WEBVTT\n\n00:00:00.500 --> 00:00:02.000\nHello VTT!\n";
        List<SubtitleCue> cues = SubtitleEngine.parse(input, SubtitleEngine.FORMAT_VTT);
        assertEquals(1, cues.size());
        assertEquals(500L, cues.get(0).startMs);
        assertEquals(2000L, cues.get(0).endMs);
        String ass = SubtitleEngine.export(cues, SubtitleEngine.FORMAT_ASS, SubtitleEngine.POSITION_ORIGINAL);
        assertTrue(ass.contains("Dialogue: 0,0:00:00.50,0:00:02.00,Default,,0,0,25,,Hello VTT!"));
    }
}
