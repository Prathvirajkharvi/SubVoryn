package com.subvoryn.voryn;

import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class SubtitleStyleEngineTest {
    private static final String ASS = "[Script Info]\r\nTitle: Preserve me\r\nScriptType: v4.00+\r\n\r\n"
            + "[V4+ Styles]\r\n"
            + "Format: Name, Fontsize, Fontname, Bold, Italic, PrimaryColour, SecondaryColour, OutlineColour, BackColour, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Spacing, StrikeOut, Underline, Encoding\r\n"
            + "Style: Main,44,Arial,0,0,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,1,2,1,2,20,21,24,0,0,0,1\r\n"
            + "Style: Signs,30,Montserrat,-1,0,&H0000FFFF,&H000000FF,&H00000000,&H80000000,1,3,1,8,10,10,12,0,0,0,1\r\n\r\n"
            + "[Events]\r\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\r\n"
            + "Dialogue: 1,0:00:01.00,0:00:02.00,Main,Actor,20,21,24,,{\\an8\\pos(300,60)\\bord4}Keep, this!\\NSecond line\r\n"
            + "; a comment to keep\r\n";

    @Test public void stylesAreListedAndFormatOrderIsDynamic() {
        List<String> names = SubtitleStyleEngine.styleNames(ASS);
        assertEquals(2, names.size()); assertEquals("Main", names.get(0)); assertEquals("Signs", names.get(1));
        assertEquals("Arial", SubtitleStyleEngine.styleValue(ASS, "Main", "Fontname"));
        assertEquals("44", SubtitleStyleEngine.styleValue(ASS, "Main", "Fontsize"));
    }

    @Test public void editsOnlySelectedStyleAndPreservesCrLfDialogueMetadataAndOverrides() {
        Map<String,String> changes = new LinkedHashMap<String,String>();
        changes.put("Fontname", "Roboto Bk"); changes.put("Outline", "2.5"); changes.put("Bold", "-1");
        String out = SubtitleStyleEngine.editAssStyle(ASS, "Main", changes);
        assertTrue(out.contains("Style: Main,44,Roboto Bk,-1,0,&H00FFFFFF"));
        assertTrue(out.contains("Style: Signs,30,Montserrat,-1,0,&H0000FFFF"));
        assertTrue(out.contains("Dialogue: 1,0:00:01.00,0:00:02.00,Main,Actor,20,21,24,,{\\an8\\pos(300,60)\\bord4}Keep, this!\\NSecond line"));
        assertTrue(out.contains("Title: Preserve me")); assertTrue(out.contains("; a comment to keep"));
        assertTrue(out.contains("\r\n"));
    }

    @Test public void supportsLegacySsaV4StyleSection() {
        String ssa = "[Script Info]\nScriptType: v4.00\n\n[V4 Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, TertiaryColour, BackColour, Bold, Italic, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, AlphaLevel, Encoding\nStyle: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,1,1,0,2,10,10,15,0,1\n\n[Events]\nFormat: Marked, Start, End, Style, Name, MarginL, MarginR, Effect, Text\nDialogue: Marked=0,0:00:00.00,0:00:01.00,Default,,10,10,,Hello\n";
        Map<String,String> c=new LinkedHashMap<String,String>();c.put("Fontname","Roboto");c.put("Outline","3");
        String out=SubtitleStyleEngine.editAssStyle(ssa,"Default",c);
        assertTrue(out.contains("[V4 Styles]"));assertTrue(out.contains("Style: Default,Roboto,20"));assertTrue(out.contains("Dialogue: Marked=0,0:00:00.00,0:00:01.00,Default"));
    }

    @Test public void assColorConversionKeepsBgrAndInvertedAlphaCorrect() {
        assertEquals(0xFFFF0000, SubtitleStyleEngine.assColorToArgb("&H000000FF"));
        assertEquals("&H000000FF", SubtitleStyleEngine.argbToAssColor(0xFFFF0000));
        assertEquals(0x80FF0000, SubtitleStyleEngine.assColorToArgb("&H7F0000FF"));
        assertEquals("&H7F0000FF", SubtitleStyleEngine.argbToAssColor(0x80FF0000));
    }

    @Test public void editsVttByAddingOneMarkedStyleBlockAndPreservingCueMetadata() {
        String vtt="WEBVTT\nKind: captions\nLanguage: en\n\nSTYLE\n::cue { color: lime; }\n\nintro-1\n00:00:01.000 --> 00:00:03.000 line:90% position:50% align:middle size:80%\nHello <i>world</i>\n\nNOTE keep this note\nline two\n";
        Map<String,String> m=SubtitleStyleEngine.defaultStyle();m.put("Fontname","Noto Sans");m.put("Fontsize","38");m.put("PrimaryColour","&H00FFFFFF");
        String out=SubtitleStyleEngine.editVttStyle(vtt,m);
        assertTrue(out.contains("WEBVTT\nKind: captions\nLanguage: en"));assertTrue(out.contains("intro-1\n00:00:01.000 --> 00:00:03.000 line:90% position:50% align:middle size:80%"));
        assertTrue(out.contains("Hello <i>world</i>"));assertTrue(out.contains("NOTE keep this note\nline two"));assertTrue(out.contains("font-family: 'Noto Sans'"));assertTrue(out.contains("::cue { color: lime; }"));
    }

    @Test public void vttStyleEditsUpdateOwnedBlockInsteadOfDuplicatingIt() {
        Map<String,String> m=SubtitleStyleEngine.defaultStyle();
        String once=SubtitleStyleEngine.editVttStyle("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nHi\n",m);
        m.put("Fontsize","54");String twice=SubtitleStyleEngine.editVttStyle(once,m);
        assertEquals(twice.indexOf(SubtitleStyleEngine.MARKER_START),twice.lastIndexOf(SubtitleStyleEngine.MARKER_START));assertTrue(twice.contains("font-size: 54px"));assertTrue(twice.contains("Hi\n"));
    }

    @Test public void vttHardsubStyleContainsRendererStrokeAndShadowFields() {
        Map<String,String> m=SubtitleStyleEngine.defaultStyle();m.put("Outline","2.5");m.put("Shadow","1.5");m.put("Alignment","8");
        String force=SubtitleStyleEngine.buildVttForceStyle(m);
        assertTrue(force.contains("Outline=2.5"));assertTrue(force.contains("Shadow=1.5"));assertTrue(force.contains("Alignment=8"));assertTrue(force.contains("FontName=Roboto"));
    }

    @Test public void malformedOrMissingNamedStyleFailsWithoutReturningDamagedContent() {
        try { SubtitleStyleEngine.editAssStyle("[Script Info]\n[Events]\n", "Default", SubtitleStyleEngine.defaultStyle()); fail("Expected clear validation failure"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Styles")); }
    }

    @Test public void builtInPresetsReturnFormatAwareStyleValuesAndUnknownPresetIsRejected() {
        assertEquals("48", SubtitleStyleEngine.builtinPreset("Anime Bold").get("Fontsize"));
        assertEquals("3", SubtitleStyleEngine.builtinPreset("High Contrast").get("BorderStyle"));
        assertEquals("Roboto", SubtitleStyleEngine.builtinPreset("Clean White").get("Fontname"));
        assertNull(SubtitleStyleEngine.builtinPreset("Not a preset"));
    }

    @Test public void inlineAppearanceOverridesAreDetectedWithoutModifyingThem() {
        assertTrue(SubtitleStyleEngine.hasInlineAppearanceOverrides("Dialogue: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,{\\an8\\bord4\\pos(20,30)}Hello"));
    }

    // SUBVORYN_STYLE_FIX_V1: targeted regression tests
    @Test public void styleEditorActionRequiresSupportedInputAndIdleEncoding() {
        assertFalse(SubtitleStyleEngine.canEditStyleButton(false, "episode.ass", false));
        assertFalse(SubtitleStyleEngine.canEditStyleButton(true, "episode.srt", false));
        assertFalse(SubtitleStyleEngine.canEditStyleButton(true, "episode.vtt", true));
        assertTrue(SubtitleStyleEngine.canEditStyleButton(true, "episode.ass", false));
        assertTrue(SubtitleStyleEngine.canEditStyleButton(true, "episode.ssa", false));
        assertTrue(SubtitleStyleEngine.canEditStyleButton(true, "episode.vtt", false));
    }

    @Test public void vttCssCombinesUnderlineAndStrikeOut() {
        Map<String,String> style = SubtitleStyleEngine.defaultStyle();
        style.put("Underline", "-1"); style.put("StrikeOut", "-1");
        String input = "WEBVTT\n\ncue-1\n00:00:00.000 --> 00:00:01.000 line:90%\nKeep me\n\nNOTE preserve this\n";
        String output = SubtitleStyleEngine.editVttStyle(input, style);
        assertTrue(output.contains("text-decoration: underline line-through"));
        assertTrue(output.contains("cue-1\n00:00:00.000 --> 00:00:01.000 line:90%"));
        assertTrue(output.contains("NOTE preserve this"));
    }

    @Test public void assColorRoundTripKeepsRgbChannelsAndFullyTransparentAlpha() {
        int transparentColor = 0x00010203;
        assertEquals("&HFF030201", SubtitleStyleEngine.argbToAssColor(transparentColor));
        assertEquals(transparentColor, SubtitleStyleEngine.assColorToArgb("&HFF030201"));
        assertEquals(0xFF010203, SubtitleStyleEngine.assColorToArgb("&H00030201"));
    }

    @Test public void assStyleRejectsIntegerOverflowInsteadOfWrappingMargins() {
        Map<String,String> changes = new LinkedHashMap<String,String>();
        changes.put("MarginL", "4294967296");
        try {
            SubtitleStyleEngine.editAssStyle(ASS, "Main", changes);
            fail("Overflowing margin must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("range") || expected.getMessage().contains("integer"));
        }
    }

    // SUBVORYN_STYLE_LAB_V2_0_2: prove \\fn extraction and source-aware native preview creation.
    @Test public void inlineFontOverridesAreExtractedDistinctlyFromDialogueTags() {
        List<String> fonts = SubtitleStyleEngine.inlineFontOverrides(
                "Dialogue: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,{\\fnRoboto Bk\\fs60}Hello {\\fnArial}World {\\fnRoboto Bk}Again");
        assertEquals(2, fonts.size());
        assertEquals("Roboto Bk", fonts.get(0));
        assertEquals("Arial", fonts.get(1));
    }

    @Test public void ffmpegAssPreviewUsesSelectedNativeStyleAndKeepsOtherStyles() {
        String preview = SubtitleStyleEngine.buildAssPreviewSource(ASS, "Signs", "Main tumhe kabhi chhodunga nahi.");
        assertTrue(preview.contains("Title: Preserve me"));
        assertTrue(preview.contains("Style: Main,44,Arial"));
        assertTrue(preview.contains("Style: Signs,30,Montserrat"));
        assertTrue(preview.contains("Format: Layer, Start, End, Style, Name"));
        assertTrue(preview.contains("Signs"));
        assertTrue(preview.contains("Main tumhe kabhi chhodunga nahi."));
        assertFalse(preview.contains("Keep, this!"));
    }

    @Test public void styleUsageCountsParseActualEventsFormatAndKeepUnusedStyles() {
        String source = "[V4+ Styles]\\n"
                + "Format: Name, Fontname, Fontsize, Alignment\\n"
                + "Style: Default,Arial,44,2\\n"
                + "Style: 4-normal,Noto Sans,65,4\\n"
                + "Style: Unused,Roboto,20,2\\n"
                + "[Events]\\n"
                + "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\\n"
                + "Dialogue: 0,0:00:00.00,0:00:01.00,4-normal,,0,0,0,,Hello, world\\n"
                + "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hi\\n"
                + "Dialogue: 0,0:00:02.00,0:00:03.00,4-normal,,0,0,0,,Again\\n";
        Map<String, Integer> counts = SubtitleStyleEngine.styleUsageCounts(source);
        assertEquals(Integer.valueOf(2), counts.get("4-normal"));
        assertEquals(Integer.valueOf(1), counts.get("Default"));
        assertEquals(Integer.valueOf(0), counts.get("Unused"));
    }

    @Test public void formatDetectionHonorsExtensionAndVttHeader() {
        assertEquals(SubtitleStyleEngine.SSA,SubtitleStyleEngine.detectFormat("captions.ssa","not parsed"));
        assertEquals(SubtitleStyleEngine.VTT,SubtitleStyleEngine.detectFormat("data.txt","WEBVTT\n\n"));
        assertEquals("",SubtitleStyleEngine.detectFormat("captions.srt","1\n00:00:00,000 --> 00:00:01,000\nHi"));
    }
}
