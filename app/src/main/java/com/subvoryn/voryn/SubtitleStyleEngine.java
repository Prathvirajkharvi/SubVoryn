package com.subvoryn.voryn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Preservation-first ASS/SSA and WebVTT styling helpers. No Android dependencies. */
public final class SubtitleStyleEngine {
    public static final String ASS = "ass";
    public static final String SSA = "ssa";
    public static final String VTT = "vtt";
    public static final String MARKER_START = "/* SUBVORYN_STYLE_BEGIN */";
    public static final String MARKER_END = "/* SUBVORYN_STYLE_END */";
    private static final Pattern VTT_CUE = Pattern.compile("(?m)^\\s*\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+-->\\s+");

    private SubtitleStyleEngine() { }

    public static String detectFormat(String fileName, String content) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".ass")) return ASS;
        if (name.endsWith(".ssa")) return SSA;
        if (name.endsWith(".vtt")) return VTT;
        String body = content == null ? "" : content.replace("\uFEFF", "").trim();
        if (body.regionMatches(true, 0, "WEBVTT", 0, 6)) return VTT;
        if (body.toLowerCase(Locale.ROOT).contains("[v4+ styles]") || body.toLowerCase(Locale.ROOT).contains("[v4 styles]")) return ASS;
        return "";
    }

    // SUBVORYN_STYLE_FIX_V1: format-aware editor action gate
    /** True only for formats with a native style editor; SRT remains valid for encoding. */
    public static boolean isStyleEditorSupported(String fileName) {
        String format = detectFormat(fileName, "");
        return ASS.equals(format) || SSA.equals(format) || VTT.equals(format);
    }

    /** Shared, testable gate for the visible Edit Subtitle Style action. */
    public static boolean canEditStyleButton(boolean hasSubtitle, String fileName, boolean encoding) {
        return hasSubtitle && !encoding && isStyleEditorSupported(fileName);
    }

    public static List<String> styleNames(String content) {
        if (content == null) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        String section = "";
        List<String> format = null;
        for (String line : content.split("\\r\\n|\\n|\\r", -1)) {
            String trim = line.trim();
            if (trim.startsWith("[") && trim.endsWith("]")) {
                section = trim.toLowerCase(Locale.ROOT);
                format = null;
                continue;
            }
            if (!("[v4+ styles]".equals(section) || "[v4 styles]".equals(section))) continue;
            if (startsIgnoreCase(trim, "Format:")) {
                format = csv(trim.substring(trim.indexOf(':') + 1));
            } else if (startsIgnoreCase(trim, "Style:") && format != null) {
                List<String> values = csv(trim.substring(trim.indexOf(':') + 1));
                int nameIndex = indexOfIgnoreCase(format, "Name");
                if (nameIndex >= 0 && nameIndex < values.size()) {
                    String name = values.get(nameIndex).trim();
                    if (name.length() > 0 && !result.contains(name)) result.add(name);
                }
            }
        }
        return result;
    }

    public static String styleValue(String content, String styleName, String fieldName) {
        if (content == null) return "";
        String section = "";
        List<String> format = null;
        for (String line : content.split("\\r\\n|\\n|\\r", -1)) {
            String trim = line.trim();
            if (trim.startsWith("[") && trim.endsWith("]")) { section = trim.toLowerCase(Locale.ROOT); format = null; continue; }
            if (!("[v4+ styles]".equals(section) || "[v4 styles]".equals(section))) continue;
            if (startsIgnoreCase(trim, "Format:")) format = csv(trim.substring(trim.indexOf(':') + 1));
            else if (startsIgnoreCase(trim, "Style:") && format != null) {
                List<String> values = csv(trim.substring(trim.indexOf(':') + 1));
                int n = indexOfIgnoreCase(format, "Name"), f = indexOfIgnoreCase(format, fieldName);
                if (n >= 0 && f >= 0 && n < values.size() && values.get(n).trim().equalsIgnoreCase(styleName) && f < values.size()) return values.get(f).trim();
            }
        }
        return "";
    }

    /** Rewrites only selected fields of one named Style record; every other line and field is retained. */
    public static String editAssStyle(String source, String styleName, Map<String, String> changes) {
        if (source == null) throw new IllegalArgumentException("Subtitle content is empty.");
        if (styleName == null || styleName.trim().length() == 0) throw new IllegalArgumentException("Select a named ASS/SSA style first.");
        if (changes == null || changes.isEmpty()) return source;
        List<Line> lines = splitLines(source);
        String section = "";
        List<String> format = null;
        boolean foundSection = false, foundTarget = false;
        StringBuilder out = new StringBuilder(source.length() + 128);
        for (Line item : lines) {
            String raw = item.text;
            String trim = raw.trim();
            if (trim.startsWith("[") && trim.endsWith("]")) {
                section = trim.toLowerCase(Locale.ROOT);
                format = null;
                if ("[v4+ styles]".equals(section) || "[v4 styles]".equals(section)) foundSection = true;
                out.append(raw).append(item.ending);
                continue;
            }
            if (("[v4+ styles]".equals(section) || "[v4 styles]".equals(section)) && startsIgnoreCase(trim, "Format:")) {
                format = csv(trim.substring(trim.indexOf(':') + 1));
                out.append(raw).append(item.ending);
                continue;
            }
            if (("[v4+ styles]".equals(section) || "[v4 styles]".equals(section)) && startsIgnoreCase(trim, "Style:") && format != null) {
                int colon = raw.indexOf(':');
                String prefix = raw.substring(0, colon + 1);
                String body = raw.substring(colon + 1);
                List<String> values = csv(body);
                int nameIdx = indexOfIgnoreCase(format, "Name");
                if (nameIdx >= 0 && nameIdx < values.size() && values.get(nameIdx).trim().equalsIgnoreCase(styleName)) {
                    for (Map.Entry<String, String> change : changes.entrySet()) {
                        int idx = indexOfIgnoreCase(format, change.getKey());
                        if (idx >= 0 && idx < values.size() && isAllowedStyleField(change.getKey())) values.set(idx, normalizeField(change.getKey(), change.getValue()));
                    }
                    String modified = prefix + joinCsv(values);
                    raw = modified;
                    foundTarget = true;
                }
            }
            out.append(raw).append(item.ending);
        }
        if (!foundSection) throw new IllegalArgumentException("No [V4+ Styles] or [V4 Styles] section was found. The source was not changed.");
        if (!foundTarget) throw new IllegalArgumentException("Style '" + styleName + "' was not found. The source was not changed.");
        return out.toString();
    }

    private static boolean isAllowedStyleField(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return Arrays.asList("fontname", "fontsize", "primarycolour", "secondarycolour", "outlinecolour", "backcolour",
                "bold", "italic", "underline", "strikeout", "scalex", "scaley", "spacing", "angle", "borderstyle",
                "outline", "shadow", "alignment", "marginl", "marginr", "marginv", "encoding").contains(n);
    }

    private static String normalizeField(String name, String value) {
        if (value == null) return "";
        String n = name.toLowerCase(Locale.ROOT);
        String v = value.trim();
        if (Arrays.asList("fontsize", "outline", "shadow", "spacing", "angle", "scalex", "scaley").contains(n)) {
            double d;
            try { d = Double.parseDouble(v); } catch (Exception e) { throw new IllegalArgumentException("Invalid number for " + name + "."); }
            if (Double.isNaN(d) || Double.isInfinite(d) || d < 0 || d > 1000) throw new IllegalArgumentException(name + " must be between 0 and 1000.");
            return trimNumber(d);
        }
        if (Arrays.asList("bold", "italic", "underline", "strikeout").contains(n)) {
            if ("-1".equals(v) || "1".equals(v) || "true".equalsIgnoreCase(v)) return "-1";
            if ("0".equals(v) || "false".equalsIgnoreCase(v)) return "0";
            throw new IllegalArgumentException("Invalid toggle value for " + name + ".");
        }
        // SUBVORYN_STYLE_FIX_V1: overflow-safe numeric validation
        if (Arrays.asList("borderstyle", "alignment", "marginl", "marginr", "marginv", "encoding").contains(n)) {
            long parsed; try { parsed = Long.parseLong(v); } catch (Exception e) { throw new IllegalArgumentException("Invalid integer for " + name + "."); }
            if (parsed < 0L || parsed > 100000L) throw new IllegalArgumentException(name + " is out of range.");
            int i = (int) parsed;
            if ("borderstyle".equals(n) && i != 1 && i != 3) throw new IllegalArgumentException("BorderStyle must be 1 or 3.");
            if ("alignment".equals(n) && (i < 1 || i > 9)) throw new IllegalArgumentException("Alignment must be 1–9.");
            return String.valueOf(i);
        }
        if (n.endsWith("colour")) {
            if (!v.matches("(?i)&H[0-9A-F]{6,8}|[0-9A-F]{6,8}")) throw new IllegalArgumentException("Invalid ASS colour for " + name + ".");
            return v.startsWith("&H") || v.startsWith("&h") ? "&H" + v.substring(2).toUpperCase(Locale.ROOT) : "&H" + v.toUpperCase(Locale.ROOT);
        }
        if ("fontname".equals(n) && (v.indexOf(',') >= 0 || v.indexOf('\r') >= 0 || v.indexOf('\n') >= 0))
            throw new IllegalArgumentException("Font family cannot contain commas or line breaks in ASS/SSA style records.");
        return v;
    }

    /** Inserts/updates only SubVoryn's marked CSS rule; all cue blocks and existing metadata remain verbatim. */
    public static String editVttStyle(String source, Map<String, String> style) {
        if (source == null || !source.replace("\uFEFF", "").trim().regionMatches(true, 0, "WEBVTT", 0, 6))
            throw new IllegalArgumentException("This is not a valid WebVTT file.");
        String css = buildVttCss(style);
        String block = MARKER_START + "\n::cue { " + css + " }\n" + MARKER_END;
        int start = source.indexOf(MARKER_START), end = source.indexOf(MARKER_END);
        if (start >= 0 && end >= start) {
            end += MARKER_END.length();
            return source.substring(0, start) + block + source.substring(end);
        }
        String nl = source.contains("\r\n") ? "\r\n" : "\n";
        Matcher existingStyle = Pattern.compile("(?m)^STYLE[ \t]*\r?$", Pattern.CASE_INSENSITIVE).matcher(source);
        if (existingStyle.find()) {
            int contentStart = existingStyle.end();
            Matcher separator = Pattern.compile("\r?\n[ \t]*\r?\n").matcher(source);
            separator.region(contentStart, source.length());
            if (separator.find()) return source.substring(0, separator.start()) + nl + block.replace("\n", nl) + source.substring(separator.start());
        }
        int headerEnd = source.indexOf('\n');
        if (headerEnd < 0) throw new IllegalArgumentException("WebVTT header is incomplete.");
        int insertion = headerEnd + 1;
        int probe = insertion;
        while (probe <= source.length()) {
            int eol = source.indexOf('\n', probe);
            if (eol < 0) eol = source.length();
            String line = source.substring(probe, eol).replace("\r", "").trim();
            if (line.length() == 0) { insertion = eol < source.length() ? eol + 1 : eol; break; }
            probe = eol + 1;
            if (eol == source.length()) break;
        }
        return source.substring(0, insertion) + "STYLE" + nl + block.replace("\n", nl) + nl + nl + source.substring(insertion);
    }

    private static String buildVttCss(Map<String, String> s) {
        String font = cssString(value(s, "Fontname", "Roboto"));
        String size = cssFontSize(value(s, "Fontsize", "42"));
        String primary = cssHex(value(s, "PrimaryColour", "&H00FFFFFF"));
        String outline = cssHex(value(s, "OutlineColour", "&H00000000"));
        String back = cssRgba(value(s, "BackColour", "&H80000000"));
        StringBuilder css = new StringBuilder();
        css.append("font-family: '").append(font).append("'; font-size: ").append(size).append("px; color: ").append(primary)
                .append("; background-color: ").append(back);
        double outlinePx = parseBounded(value(s, "Outline", "2"), 2.0, 0.0, 8.0);
        double shadowPx = parseBounded(value(s, "Shadow", "1"), 1.0, 0.0, 12.0);
        StringBuilder shadows = new StringBuilder();
        if (outlinePx > 0.0) {
            String px = trimNumber(Math.max(0.5, Math.min(4.0, outlinePx * 0.6)));
            shadows.append("-").append(px).append("px -").append(px).append("px 0 ").append(outline)
                    .append(", 0 -").append(px).append("px 0 ").append(outline)
                    .append(", ").append(px).append("px -").append(px).append("px 0 ").append(outline)
                    .append(", -").append(px).append("px 0 0 ").append(outline)
                    .append(", ").append(px).append("px 0 0 ").append(outline)
                    .append(", -").append(px).append("px ").append(px).append("px 0 ").append(outline)
                    .append(", 0 ").append(px).append("px 0 ").append(outline)
                    .append(", ").append(px).append("px ").append(px).append("px 0 ").append(outline);
        }
        if (shadowPx > 0.0) {
            if (shadows.length() > 0) shadows.append(", ");
            shadows.append("1px 1px ").append(trimNumber(shadowPx)).append("px ").append(back);
        }
        if (shadows.length() > 0) css.append("; text-shadow: ").append(shadows);
        if (truth(value(s, "Bold", "0"))) css.append("; font-weight: bold");
        if (truth(value(s, "Italic", "0"))) css.append("; font-style: italic");
        // SUBVORYN_STYLE_FIX_V1: VTT strike-through mapping
        StringBuilder decorations = new StringBuilder();
        if (truth(value(s, "Underline", "0"))) decorations.append("underline");
        if (truth(value(s, "StrikeOut", "0"))) {
            if (decorations.length() > 0) decorations.append(' ');
            decorations.append("line-through");
        }
        if (decorations.length() > 0) css.append("; text-decoration: ").append(decorations);
        return css.toString();
    }

    public static String buildVttForceStyle(Map<String, String> s) {
        StringBuilder b = new StringBuilder();
        String[] fields = {"FontName", "FontSize", "PrimaryColour", "SecondaryColour", "OutlineColour", "BackColour", "Bold", "Italic", "Underline", "StrikeOut", "BorderStyle", "Outline", "Shadow", "Alignment", "MarginL", "MarginR", "MarginV", "Spacing"};
        String[] keys = {"Fontname", "Fontsize", "PrimaryColour", "SecondaryColour", "OutlineColour", "BackColour", "Bold", "Italic", "Underline", "StrikeOut", "BorderStyle", "Outline", "Shadow", "Alignment", "MarginL", "MarginR", "MarginV", "Spacing"};
        String[] defaults = {"Roboto", "42", "&H00FFFFFF", "&H000000FF", "&H00000000", "&H80000000", "0", "0", "0", "0", "1", "2", "1", "2", "20", "20", "24", "0"};
        for (int i = 0; i < fields.length; i++) {
            if (b.length() > 0) b.append(',');
            b.append(fields[i]).append('=').append(value(s, keys[i], defaults[i]));
        }
        return b.toString();
    }

    public static int assColorToArgb(String value) {
        if (value == null) return 0xFFFFFFFF;
        String v = value.trim().toUpperCase(Locale.ROOT).replace("&H", "");
        if (v.startsWith("#")) v = v.substring(1);
        if (v.length() == 6) v = "00" + v;
        if (v.length() != 8) return 0xFFFFFFFF;
        try {
            int transparency = Integer.parseInt(v.substring(0, 2), 16); // ASS alpha is transparency (00 opaque).
            int blue = Integer.parseInt(v.substring(2, 4), 16);
            int green = Integer.parseInt(v.substring(4, 6), 16);
            int red = Integer.parseInt(v.substring(6, 8), 16);
            int alpha = 255 - transparency;
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        } catch (Exception e) { return 0xFFFFFFFF; }
    }

    public static String argbToAssColor(int argb) {
        int transparency = 255 - ((argb >>> 24) & 0xFF);
        int red = (argb >>> 16) & 0xFF, green = (argb >>> 8) & 0xFF, blue = argb & 0xFF;
        return String.format(Locale.ROOT, "&H%02X%02X%02X%02X", transparency, blue, green, red);
    }

    public static String cssHex(String ass) {
        int c = assColorToArgb(ass);
        return String.format(Locale.ROOT, "#%02X%02X%02X", (c >>> 16) & 255, (c >>> 8) & 255, c & 255);
    }

    public static String cssRgba(String ass) {
        int c = assColorToArgb(ass);
        double alpha = ((c >>> 24) & 255) / 255.0;
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)", (c >>> 16) & 255, (c >>> 8) & 255, c & 255, alpha);
    }

    public static Map<String, String> defaultStyle() {
        Map<String, String> d = new LinkedHashMap<String, String>();
        d.put("Fontname", "Roboto"); d.put("Fontsize", "42");
        d.put("PrimaryColour", "&H00FFFFFF"); d.put("SecondaryColour", "&H000000FF");
        d.put("OutlineColour", "&H00000000"); d.put("BackColour", "&H80000000");
        d.put("Bold", "0"); d.put("Italic", "0"); d.put("Underline", "0"); d.put("StrikeOut", "0");
        d.put("Spacing", "0"); d.put("BorderStyle", "1"); d.put("Outline", "2"); d.put("Shadow", "1");
        d.put("Alignment", "2"); d.put("MarginL", "20"); d.put("MarginR", "20"); d.put("MarginV", "24");
        return d;
    }

    public static Map<String, String> builtinPreset(String name) {
        if (name == null) return null;
        Map<String, String> m = defaultStyle();
        if ("Default".equals(name)) return m;
        if ("Anime Bold".equals(name)) { m.put("Fontname","Roboto Bk");m.put("Fontsize","48");m.put("Bold","-1");m.put("Outline","3");m.put("Shadow","1.5");m.put("BorderStyle","1");m.put("PrimaryColour","&H00FFFFFF");m.put("OutlineColour","&H00000000");return m; }
        if ("Clean White".equals(name)) { m.put("Fontname","Roboto");m.put("Fontsize","40");m.put("Bold","0");m.put("Outline","1.5");m.put("Shadow","0.5");m.put("PrimaryColour","&H00FFFFFF");m.put("OutlineColour","&H80000000");return m; }
        if ("Yellow Outline".equals(name)) { m.put("Fontname","Roboto Bk");m.put("Fontsize","44");m.put("Bold","-1");m.put("PrimaryColour","&H0000FFFF");m.put("OutlineColour","&H00000000");m.put("Outline","3");m.put("Shadow","1");return m; }
        if ("Minimal".equals(name)) { m.put("Fontname","Roboto");m.put("Fontsize","36");m.put("Outline","0.5");m.put("Shadow","0");m.put("BorderStyle","1");m.put("PrimaryColour","&H00FFFFFF");return m; }
        if ("High Contrast".equals(name)) { m.put("Fontname","Noto Sans");m.put("Fontsize","46");m.put("Bold","-1");m.put("Outline","3");m.put("Shadow","2");m.put("BorderStyle","3");m.put("PrimaryColour","&H00FFFFFF");m.put("BackColour","&H50000000");return m; }
        return null;
    }

    public static boolean hasInlineAppearanceOverrides(String source) {
        if (source == null) return false;
        return Pattern.compile("(?i)\\{[^}]*\\\\(?:fn|fs|b|i|u|s|bord|shad|c|1c|3c|4c|alpha|1a|3a|4a|an)").matcher(source).find();
    }

    // SUBVORYN_STYLE_LAB_V2_0_2: report actual inline ASS/SSA font overrides.
    /** Returns distinct non-empty font names found in inline \\fn tags, in source order. */
    public static List<String> inlineFontOverrides(String source) {
        if (source == null || source.length() == 0) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        Matcher matcher = Pattern.compile("(?i)\\\\fn([^\\\\}]*?)(?=\\\\[a-zA-Z]|\\})").matcher(source);
        while (matcher.find()) {
            String font = matcher.group(1) == null ? "" : matcher.group(1).trim();
            if (font.length() == 0) continue;
            boolean duplicate = false;
            for (String found : result) if (found.equalsIgnoreCase(font)) { duplicate = true; break; }
            if (!duplicate) result.add(font);
        }
        return result;
    }

    // SUBVORYN_STYLE_LAB_V2_0_2: generate a temporary preview file while preserving native styles.
    /**
     * Builds an ephemeral ASS/SSA script with the original Script Info and all named style
     * records, but one sample Dialogue using the selected named style. The imported source is
     * never rewritten. ASS Text is required to be the final Format column, per the ASS format.
     */
    public static String buildAssPreviewSource(String source, String styleName, String sampleText) {
        if (source == null || source.trim().length() == 0) throw new IllegalArgumentException("ASS/SSA preview source is empty.");
        if (styleName == null || styleName.trim().length() == 0) throw new IllegalArgumentException("Select a named style for preview.");
        String[] lines = source.split("\\r\\n|\\n|\\r", -1);
        int events = -1;
        int formatLine = -1;
        String section = "";
        for (int i = 0; i < lines.length; i++) {
            String trim = lines[i].trim();
            if (trim.startsWith("[") && trim.endsWith("]")) {
                section = trim.toLowerCase(Locale.ROOT);
                if ("[events]".equals(section)) { events = i; formatLine = -1; continue; }
                if (events >= 0 && i > events) break;
            }
            if (events >= 0 && "[events]".equals(section) && startsIgnoreCase(trim, "Format:")) {
                formatLine = i;
                break;
            }
        }
        if (events < 0 || formatLine < 0) throw new IllegalArgumentException("No [Events] Format declaration was found for ASS/SSA preview.");
        String formatText = lines[formatLine].substring(lines[formatLine].indexOf(':') + 1).trim();
        List<String> fields = csv(formatText);
        int textIndex = indexOfIgnoreCase(fields, "Text");
        int styleIndex = indexOfIgnoreCase(fields, "Style");
        int startIndex = indexOfIgnoreCase(fields, "Start");
        int endIndex = indexOfIgnoreCase(fields, "End");
        if (textIndex < 0 || textIndex != fields.size() - 1)
            throw new IllegalArgumentException("ASS/SSA preview needs Text as the final Events Format column.");
        if (styleIndex < 0 || startIndex < 0 || endIndex < 0)
            throw new IllegalArgumentException("ASS/SSA Events Format is missing Style, Start or End.");
        List<String> knownStyles = styleNames(source);
        boolean styleFound = false;
        for (String candidate : knownStyles) if (candidate.equalsIgnoreCase(styleName)) { styleFound = true; break; }
        if (!styleFound) throw new IllegalArgumentException("Preview style was not found in the source file: " + styleName);
        List<String> values = new ArrayList<String>();
        for (int i = 0; i < fields.size(); i++) values.add("");
        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i).trim().toLowerCase(Locale.ROOT);
            if ("layer".equals(field)) values.set(i, "0");
            else if ("marked".equals(field)) values.set(i, "Marked=0");
            else if ("start".equals(field)) values.set(i, "0:00:00.00");
            else if ("end".equals(field)) values.set(i, "0:00:05.00");
            else if ("style".equals(field)) values.set(i, styleName);
            else if ("marginl".equals(field)) values.set(i, defaultStyleValue(source, styleName, "MarginL", "20"));
            else if ("marginr".equals(field)) values.set(i, defaultStyleValue(source, styleName, "MarginR", "20"));
            else if ("marginv".equals(field)) values.set(i, defaultStyleValue(source, styleName, "MarginV", "24"));
            else if ("text".equals(field)) values.set(i, (sampleText == null || sampleText.length() == 0) ? "Main tumhe kabhi chhodunga nahi." : sampleText.replace("\r", " ").replace("\n", "\\N"));
        }
        StringBuilder out = new StringBuilder(source.length() + 180);
        for (int i = 0; i < events; i++) out.append(lines[i]).append('\n');
        out.append(lines[events]).append('\n').append(lines[formatLine]).append('\n');
        out.append("Dialogue: ").append(joinCsv(values)).append('\n');
        return out.toString();
    }

    private static String defaultStyleValue(String source, String styleName, String fieldName, String fallback) {
        String value = styleValue(source, styleName, fieldName);
        return value == null || value.length() == 0 ? fallback : value;
    }

    private static String value(Map<String, String> map, String key, String fallback) {
        if (map == null || map.get(key) == null || map.get(key).trim().length() == 0) return fallback;
        return map.get(key).trim();
    }
    private static String cssString(String value) { return value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", " ").replace("\n", " "); }
    private static String cssFontSize(String value) {
        try { double d = Double.parseDouble(value); if (d >= 8 && d <= 200 && !Double.isNaN(d) && !Double.isInfinite(d)) return trimNumber(d); }
        catch (Exception ignored) { }
        return "42";
    }
    private static double parseBounded(String value, double fallback, double min, double max) {
        try { double d=Double.parseDouble(value); if(!Double.isNaN(d)&&!Double.isInfinite(d)) return Math.max(min,Math.min(max,d)); }
        catch(Exception ignored) { }
        return fallback;
    }
    private static boolean truth(String value) { return "1".equals(value) || "-1".equals(value) || "true".equalsIgnoreCase(value); }
    private static String trimNumber(double d) { return d == Math.rint(d) ? String.valueOf((long)d) : String.format(Locale.ROOT, "%.2f", d).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private static boolean startsIgnoreCase(String line, String prefix) { return line.regionMatches(true, 0, prefix, 0, prefix.length()); }
    private static List<String> csv(String s) { return new ArrayList<String>(Arrays.asList(s.split(",", -1))); }
    private static String joinCsv(List<String> values) { StringBuilder b = new StringBuilder(); for (int i=0;i<values.size();i++) { if(i>0)b.append(','); b.append(values.get(i)); } return b.toString(); }
    private static int indexOfIgnoreCase(List<String> values, String key) { for(int i=0;i<values.size();i++) if(values.get(i).trim().equalsIgnoreCase(key)) return i; return -1; }
    private static List<Line> splitLines(String source) {
        List<Line> list = new ArrayList<Line>();
        int start = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\n') { int textEnd = i; String ending = "\n"; if (textEnd > start && source.charAt(textEnd-1) == '\r') { textEnd--; ending = "\r\n"; } list.add(new Line(source.substring(start,textEnd), ending)); start = i+1; }
            else if (c == '\r' && (i+1 >= source.length() || source.charAt(i+1) != '\n')) { list.add(new Line(source.substring(start,i), "\r")); start=i+1; }
        }
        if (start < source.length() || source.length() == 0) list.add(new Line(source.substring(start), ""));
        return list;
    }
    private static final class Line { final String text, ending; Line(String t,String e){text=t;ending=e;} }
}
