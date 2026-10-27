package com.subvoryn.voryn;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression checks that ASS export does not flatten script styles or cue placement metadata. */
public class SubtitleEngineAssRoundTripTest {
    private static final String ASS = "[Script Info]\n"
            + "Title: Style Test\n"
            + "ScriptType: v4.00+\n"
            + "WrapStyle: 0\n\n"
            + "[V4+ Styles]\n"
            + "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
            + "Style: Default,Montserrat,47,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,3,1,8,34,56,61,1\n\n"
            + "[Events]\n"
            + "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
            + "Dialogue: 2,0:00:01.10,0:00:03.25,Default,Actor,34,56,61,Banner,{\\an8\\pos(640,70)\\b1}Hello, world!\\NSecond line\n";

    @Test public void preservesScriptStylesAndPositioningOnOriginalAssExport() {
        List<SubtitleCue> cues = SubtitleEngine.parse(ASS, SubtitleEngine.FORMAT_ASS);
        assertEquals(1, cues.size());
        SubtitleCue cue = cues.get(0);
        assertEquals("Default", cue.style);
        assertEquals("Actor", cue.assName);
        assertEquals("Banner", cue.assEffect);
        assertEquals(34, cue.marginL);
        assertEquals(56, cue.marginR);
        assertEquals(61, cue.marginV);
        assertEquals(2, cue.layer);
        assertTrue(cue.assHeader.contains("Style: Default,Montserrat,47"));

        String exported = SubtitleEngine.export(cues, SubtitleEngine.FORMAT_ASS, SubtitleEngine.POSITION_ORIGINAL);
        assertTrue(exported.contains("Style: Default,Montserrat,47,&H00FFFFFF"));
        assertTrue(exported.contains("Dialogue: 2,0:00:01.10,0:00:03.25,Default,Actor,34,56,61,Banner,{\\an8\\pos(640,70)\\b1}Hello, world!\\NSecond line"));
    }

    @Test public void newOcrAssExportStillProducesAUsableDefaultStyleHeader() {
        SubtitleCue cue = new SubtitleCue(1000L, 2000L, "Hello!");
        String exported = SubtitleEngine.export(java.util.Collections.singletonList(cue),
                SubtitleEngine.FORMAT_ASS, SubtitleEngine.POSITION_ORIGINAL);
        assertTrue(exported.contains("[V4+ Styles]"));
        assertTrue(exported.contains("Style: Default,Arial,42"));
        assertTrue(exported.contains("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,25,,Hello!"));
    }
}
