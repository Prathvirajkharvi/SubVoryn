package com.subvoryn.voryn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java subtitle parsing, cleanup and export core (no Android dependencies). */
public final class SubtitleEngine {
    public static final int FORMAT_SRT = 0;
    public static final int FORMAT_VTT = 1;
    public static final int FORMAT_ASS = 2;
    public static final int POSITION_ORIGINAL = 0;
    public static final int POSITION_TOP = 1;
    public static final int POSITION_BOTTOM = 2;

    private static final Pattern TIMESTAMP_LINE = Pattern.compile(
        "(?m)^\\s*((?:\\d{1,2}:)?\\d{2}:\\d{2}[,.]\\d{1,3})\\s*-->\\s*((?:\\d{1,2}:)?\\d{2}:\\d{2}[,.]\\d{1,3})(?:\\s+.*)?$"
    );
    private static final Pattern ASS_ALIGNMENT = Pattern.compile("\\\\an([1-9])");
    private static final Pattern ASS_OVERRIDE = Pattern.compile("\\{[^}]*\\}");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private SubtitleEngine() { }

    public static int guessFormat(String fileName, String content) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.US);
        if (name.endsWith(".ass") || name.endsWith(".ssa")) return FORMAT_ASS;
        if (name.endsWith(".vtt")) return FORMAT_VTT;
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("WEBVTT")) return FORMAT_VTT;
        if (trimmed.indexOf("[Events]") >= 0 && trimmed.indexOf("Dialogue:") >= 0) return FORMAT_ASS;
        return FORMAT_SRT;
    }

    public static List<SubtitleCue> parse(String content, int format) {
        if (content == null) content = "";
        content = content.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        if (format == FORMAT_ASS) return parseAss(content);
        return parseSrtVtt(content);
    }

    private static List<SubtitleCue> parseSrtVtt(String content) {
        List<SubtitleCue> result = new ArrayList<SubtitleCue>();
        String[] blocks = content.split("\\n\\s*\\n");
        for (int i = 0; i < blocks.length; i++) {
            String block = blocks[i].trim();
            if (block.length() == 0 || block.startsWith("WEBVTT") || block.startsWith("NOTE") || block.startsWith("STYLE") || block.startsWith("REGION")) continue;
            String[] lines = block.split("\\n");
            int timeLine = -1;
            Matcher matcher = null;
            for (int j = 0; j < lines.length; j++) {
                Matcher test = TIMESTAMP_LINE.matcher(lines[j]);
                if (test.find()) { timeLine = j; matcher = test; break; }
            }
            if (timeLine < 0 || matcher == null) continue;
            long start = parseTimestamp(matcher.group(1));
            long end = parseTimestamp(matcher.group(2));
            if (start < 0 || end < 0 || end <= start) continue;
            StringBuilder body = new StringBuilder();
            for (int j = timeLine + 1; j < lines.length; j++) {
                if (body.length() > 0) body.append('\n');
                body.append(lines[j]);
            }
            String raw = body.toString().trim();
            if (raw.length() == 0) continue;
            SubtitleCue cue = new SubtitleCue(start, end, raw);
            cue.rawText = raw;
            cue.text = raw;
            result.add(cue);
        }
        sort(result);
        return result;
    }

    private static List<SubtitleCue> parseAss(String content) {
        List<SubtitleCue> result = new ArrayList<SubtitleCue>();
        Map<String, Integer> styleAlignments = new HashMap<String, Integer>();
        String lower = content.toLowerCase(Locale.US);
        int eventsIndex = lower.indexOf("[events]");
        String header = eventsIndex >= 0 ? content.substring(0, eventsIndex).trim() : "";
        String section = "";
        String[] lines = content.split("\\n");
        List<String> eventFormat = null;
        List<String> styleFormat = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.toLowerCase(Locale.US);
                continue;
            }
            if (line.length() == 0 || line.startsWith(";")) continue;
            if (section.equals("[v4+ styles]") || section.equals("[v4 styles]")) {
                if (line.regionMatches(true, 0, "Format:", 0, 7)) {
                    styleFormat = splitFormat(line.substring(7));
                } else if (line.regionMatches(true, 0, "Style:", 0, 6) && styleFormat != null) {
                    String[] values = splitCsv(line.substring(6), styleFormat.size());
                    String name = getField(values, styleFormat, "Name", "Default");
                    String align = getField(values, styleFormat, "Alignment", "2");
                    try { styleAlignments.put(name, Integer.valueOf(Integer.parseInt(align.trim()))); }
                    catch (Exception ignored) { styleAlignments.put(name, Integer.valueOf(2)); }
                }
            } else if (section.equals("[events]")) {
                if (line.regionMatches(true, 0, "Format:", 0, 7)) {
                    eventFormat = splitFormat(line.substring(7));
                } else if (line.regionMatches(true, 0, "Dialogue:", 0, 9) && eventFormat != null) {
                    String[] values = splitCsv(line.substring(9), eventFormat.size());
                    long start = parseAssTimestamp(getField(values, eventFormat, "Start", ""));
                    long end = parseAssTimestamp(getField(values, eventFormat, "End", ""));
                    if (start < 0L || end <= start) continue;
                    String raw = getField(values, eventFormat, "Text", "");
                    if (raw.trim().length() == 0) continue;
                    String style = getField(values, eventFormat, "Style", "Default");
                    SubtitleCue cue = new SubtitleCue(start, end, raw);
                    cue.rawText = raw;
                    cue.text = raw;
                    cue.style = style;
                    cue.assHeader = header;
                    cue.assName = getField(values, eventFormat, "Name", "");
                    cue.assEffect = getField(values, eventFormat, "Effect", "");
                    cue.marginL = parseInt(getField(values, eventFormat, "MarginL", "0"), 0);
                    cue.marginR = parseInt(getField(values, eventFormat, "MarginR", "0"), 0);
                    cue.marginV = parseInt(getField(values, eventFormat, "MarginV", "25"), 25);
                    cue.layer = parseInt(getField(values, eventFormat, "Layer", getField(values, eventFormat, "Marked", "0")), 0);
                    Matcher am = ASS_ALIGNMENT.matcher(raw);
                    if (am.find()) cue.alignment = parseInt(am.group(1), 0);
                    else if (styleAlignments.containsKey(style)) cue.alignment = styleAlignments.get(style).intValue();
                    result.add(cue);
                }
            }
        }
        sort(result);
        return result;
    }

    private static List<String> splitFormat(String value) {
        List<String> out = new ArrayList<String>();
        String[] parts = value.split(",");
        for (int i = 0; i < parts.length; i++) out.add(parts[i].trim());
        return out;
    }

    private static String[] splitCsv(String value, int expectedFields) {
        return value.split(",", Math.max(1, expectedFields));
    }

    private static String getField(String[] values, List<String> format, String wanted, String fallback) {
        for (int i = 0; i < format.size(); i++) {
            if (format.get(i).equalsIgnoreCase(wanted) && i < values.length) return values[i].trim();
        }
        return fallback;
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; }
    }

    private static long parseAssTimestamp(String value) {
        try {
            String[] p = value.trim().split(":");
            if (p.length != 3) return -1;
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            String[] sec = p[2].split("\\.");
            int s = Integer.parseInt(sec[0]);
            int cs = sec.length > 1 ? Integer.parseInt(padRight(sec[1], 2).substring(0, 2)) : 0;
            return ((h * 3600L + m * 60L + s) * 1000L) + (cs * 10L);
        } catch (Exception e) { return -1; }
    }

    private static String padRight(String value, int size) {
        while (value.length() < size) value += "0";
        return value;
    }

    public static long parseTimestamp(String value) {
        try {
            String normalized = value.trim().replace(',', '.');
            String[] parts = normalized.split(":");
            int h = 0, m, s;
            String secPart;
            if (parts.length == 3) {
                h = Integer.parseInt(parts[0]);
                m = Integer.parseInt(parts[1]);
                secPart = parts[2];
            } else if (parts.length == 2) {
                m = Integer.parseInt(parts[0]);
                secPart = parts[1];
            } else return -1;
            String[] sf = secPart.split("\\.");
            s = Integer.parseInt(sf[0]);
            int ms = sf.length > 1 ? Integer.parseInt(padRight(sf[1], 3).substring(0, 3)) : 0;
            if (m > 59 || s > 59 || ms > 999) return -1;
            return ((h * 3600L + m * 60L + s) * 1000L) + ms;
        } catch (Exception e) { return -1; }
    }

    public static String formatSrtTime(long ms) {
        if (ms < 0) ms = 0;
        long h = ms / 3600000L; ms %= 3600000L;
        long m = ms / 60000L; ms %= 60000L;
        long s = ms / 1000L; ms %= 1000L;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms);
    }

    public static String formatVttTime(long ms) {
        return formatSrtTime(ms).replace(',', '.');
    }

    public static String formatAssTime(long ms) {
        if (ms < 0) ms = 0;
        long h = ms / 3600000L; ms %= 3600000L;
        long m = ms / 60000L; ms %= 60000L;
        long s = ms / 1000L; ms %= 1000L;
        long cs = ms / 10L;
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs);
    }

    public static String displayText(SubtitleCue cue) {
        String text = cue == null ? "" : cue.text;
        if (text == null) return "";
        text = text.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ");
        text = ASS_OVERRIDE.matcher(text).replaceAll("");
        text = HTML_TAG.matcher(text).replaceAll("");
        return text.trim();
    }

    public static String normalizedText(SubtitleCue cue) {
        String value = displayText(cue).toLowerCase(Locale.US).trim();
        value = value.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        value = value.replaceAll("\\s+", " ");
        return value;
    }

    public static int countDuplicates(List<SubtitleCue> cues, long mergeGapMs) {
        if (cues == null || cues.size() < 2) return 0;
        int count = 0;
        for (int i = 1; i < cues.size(); i++) {
            SubtitleCue a = cues.get(i - 1), b = cues.get(i);
            if (normalizedText(a).length() > 0 && normalizedText(a).equals(normalizedText(b)) && b.startMs <= a.endMs + mergeGapMs) count++;
        }
        return count;
    }

    /** Merge only consecutive, same-text cues whose intervals overlap or are separated by a small gap. */
    public static int mergeConsecutiveDuplicates(List<SubtitleCue> cues, long mergeGapMs) {
        if (cues == null || cues.size() < 2) return 0;
        sort(cues);
        int removed = 0;
        int i = 1;
        while (i < cues.size()) {
            SubtitleCue previous = cues.get(i - 1);
            SubtitleCue current = cues.get(i);
            String a = normalizedText(previous), b = normalizedText(current);
            if (a.length() > 0 && a.equals(b) && current.startMs <= previous.endMs + mergeGapMs) {
                if (current.endMs > previous.endMs) previous.endMs = current.endMs;
                cues.remove(i);
                removed++;
            } else i++;
        }
        return removed;
    }

    public static int countShortCues(List<SubtitleCue> cues, long thresholdMs) {
        if (cues == null) return 0;
        int count = 0;
        for (int i = 0; i < cues.size(); i++) if (cues.get(i).durationMs() > 0 && cues.get(i).durationMs() < thresholdMs) count++;
        return count;
    }

    /** Heuristic only: extends short cues but stops at the next cue's start where possible. */
    public static int extendShortCues(List<SubtitleCue> cues, long thresholdMs) {
        if (cues == null || cues.size() == 0) return 0;
        sort(cues);
        int changed = 0;
        for (int i = 0; i < cues.size(); i++) {
            SubtitleCue cue = cues.get(i);
            if (cue.durationMs() <= 0 || cue.durationMs() >= thresholdMs) continue;
            long proposed = cue.startMs + thresholdMs;
            if (i + 1 < cues.size()) {
                long nextStart = cues.get(i + 1).startMs;
                if (nextStart > cue.startMs && nextStart < proposed) proposed = nextStart;
            }
            if (proposed > cue.endMs) { cue.endMs = proposed; changed++; }
        }
        return changed;
    }

    public static String export(List<SubtitleCue> cues, int format, int position) {
        if (cues == null) cues = new ArrayList<SubtitleCue>();
        sort(cues);
        if (format == FORMAT_VTT) return exportVtt(cues, position);
        if (format == FORMAT_ASS) return exportAss(cues, position);
        return exportSrt(cues);
    }

    private static String exportSrt(List<SubtitleCue> cues) {
        StringBuilder out = new StringBuilder();
        int emitted = 0;
        for (int i = 0; i < cues.size(); i++) {
            SubtitleCue c = cues.get(i);
            String text = displayText(c);
            if (text.length() == 0 || c.endMs <= c.startMs) continue;
            emitted++;
            out.append(emitted).append('\n').append(formatSrtTime(c.startMs)).append(" --> ").append(formatSrtTime(c.endMs)).append('\n');
            out.append(text).append("\n\n");
        }
        return out.toString();
    }

    private static String exportVtt(List<SubtitleCue> cues, int position) {
        StringBuilder out = new StringBuilder("WEBVTT\n\n");
        for (int i = 0; i < cues.size(); i++) {
            SubtitleCue c = cues.get(i);
            String text = displayText(c);
            if (text.length() == 0 || c.endMs <= c.startMs) continue;
            String setting = vttSetting(c, position);
            out.append(formatVttTime(c.startMs)).append(" --> ").append(formatVttTime(c.endMs));
            if (setting.length() > 0) out.append(' ').append(setting);
            out.append('\n').append(escapeVttText(text)).append("\n\n");
        }
        return out.toString();
    }

    private static String vttSetting(SubtitleCue cue, int position) {
        int a = cue.alignment;
        if (position == POSITION_TOP) return "line:0%";
        if (position == POSITION_BOTTOM) return "line:100%";
        if (a >= 7) return "line:0%";
        if (a >= 4) return "line:50%";
        if (a >= 1) return "line:100%";
        return "";
    }

    private static String escapeVttText(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String exportAss(List<SubtitleCue> cues, int position) {
        StringBuilder out = new StringBuilder();
        String originalHeader = "";
        if (cues != null) {
            for (SubtitleCue cue : cues) {
                if (cue != null && cue.assHeader != null && cue.assHeader.trim().length() > 0) {
                    originalHeader = cue.assHeader.trim();
                    break;
                }
            }
        }
        if (originalHeader.length() > 0) {
            out.append(originalHeader).append("\n\n");
        } else {
            out.append("[Script Info]\nScriptType: v4.00+\nWrapStyle: 0\nScaledBorderAndShadow: yes\n\n");
            out.append("[V4+ Styles]\n");
            out.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n");
            out.append("Style: Default,Arial,42,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,0,2,20,20,25,1\n\n");
        }
        out.append("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");
        for (int i = 0; cues != null && i < cues.size(); i++) {
            SubtitleCue c = cues.get(i);
            if (displayText(c).length() == 0 || c.endMs <= c.startMs) continue;
            int alignment = c.alignment >= 1 && c.alignment <= 9 ? c.alignment : 2;
            if (position == POSITION_TOP) alignment = 8;
            else if (position == POSITION_BOTTOM) alignment = 2;
            String raw = c.rawText == null ? c.text : c.rawText;
            if (raw == null) raw = "";
            if (position != POSITION_ORIGINAL) {
                raw = stripAssPositionOverrides(raw);
                raw = "{\\an" + alignment + "}" + raw.replace("\n", "\\N");
            } else {
                Matcher m = ASS_ALIGNMENT.matcher(raw);
                if (!m.find() && c.alignment >= 1 && c.alignment <= 9) raw = "{\\an" + alignment + "}" + raw;
                if (raw.indexOf('\n') >= 0) raw = raw.replace("\n", "\\N");
            }
            int marginV = position == POSITION_ORIGINAL ? Math.max(0, c.marginV) : 25;
            String style = c.style == null || c.style.trim().length() == 0 ? "Default" : c.style;
            String name = c.assName == null ? "" : c.assName;
            String effect = c.assEffect == null ? "" : c.assEffect;
            int marginL = Math.max(0, c.marginL);
            int marginR = Math.max(0, c.marginR);
            out.append("Dialogue: ").append(Math.max(0, c.layer)).append(',').append(formatAssTime(c.startMs)).append(',').append(formatAssTime(c.endMs))
                .append(',').append(style).append(',').append(name).append(',').append(marginL).append(',').append(marginR).append(',').append(marginV)
                .append(',').append(effect).append(',').append(raw.replace('\r', ' ')).append('\n');
        }
        return out.toString();
    }

    private static String stripAssPositionOverrides(String raw) {
        if (raw == null) return "";
        // Keep styling such as italic, bold, and colour; remove only tags that control placement.
        raw = raw.replaceAll("\\\\an[1-9]", "");
        raw = raw.replaceAll("\\\\pos\\([^)]*\\)", "");
        raw = raw.replaceAll("\\\\move\\([^)]*\\)", "");
        raw = raw.replaceAll("\\\\org\\([^)]*\\)", "");
        raw = raw.replaceAll("\\{\\s*\\}", "");
        return raw;
    }

    public static String formatMs(long ms) {
        return formatSrtTime(ms).replace(',', '.');
    }

    public static String prettyTime(long ms) {
        if (ms < 0) ms = 0;
        long h = ms / 3600000L; ms %= 3600000L;
        long m = ms / 60000L; ms %= 60000L;
        long s = ms / 1000L; ms %= 1000L;
        if (h > 0) return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, ms);
        return String.format(Locale.US, "%02d:%02d.%03d", m, s, ms);
    }

    private static void sort(List<SubtitleCue> cues) {
        Collections.sort(cues, new Comparator<SubtitleCue>() {
            public int compare(SubtitleCue a, SubtitleCue b) {
                if (a.startMs < b.startMs) return -1;
                if (a.startMs > b.startMs) return 1;
                if (a.endMs < b.endMs) return -1;
                if (a.endMs > b.endMs) return 1;
                return 0;
            }
        });
    }
}
