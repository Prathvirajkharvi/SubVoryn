package com.subvoryn.voryn;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative OCR text cleanup. It never changes cue timing or ASS layout metadata. */
public final class OcrTextCleaner {
    private static final Pattern CONTROL_EXCEPT_NEWLINES = Pattern.compile("[\\p{Cc}&&[^\\n]]");
    private static final Pattern EDGE_SPACE = Pattern.compile("(?m)[ \\t]+$|^[ \\t]+");

    private OcrTextCleaner() { }

    public static String clean(String input) {
        if (input == null) return "";
        String value = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replace("\r\n", "\n").replace('\r', '\n')
                .replace('\t', ' ').replace('\u00a0', ' ')
                .replace('\uFFFD', ' ');
        value = CONTROL_EXCEPT_NEWLINES.matcher(value).replaceAll(" ");
        value = removeUnmatchedParentheses(value);
        value = value.replaceAll("\\(\\s*\\)", " ");
        // A curly apostrophe between letters is a common OCR substitution for missing spacing.
        value = value.replaceAll("(?<=\\p{L})[‘’](?=\\p{L})", " ");

        // Repairs retained from the project's observed OCR examples. These apply only to
        // exact, narrowly matched corruptions; no general dictionary rewriting is performed.
        value = value.replaceAll("(?i)\\bkitna\\s*['’]?\\s*bada\\b", "Kitna bada")
                .replaceAll("(?i)\\btohisse\\b", "toh isse")
                .replaceAll("(?i)\\bkartej?\\s*hain\\b", "karte hain")
                .replaceAll("(?i)\\bzyadaj\\b", "zyada")
                .replaceAll("(?i)\\bone\\s+sters\\b", "monsters")
                .replaceAll("(?i)\\bmagicj?stone\\b", "magic stone")
                .replaceAll("(?i)\\bhilagbhag\\b", "hi lagbhag");

        // Remove obvious OCR-only rows without stripping legitimate question/exclamation marks.
        value = value.replaceAll("(?m)^\\s*[|¦{}~^_=+*/\\\\<>]+\\s*$", "");
        value = value.replaceAll("[|¦]{2,}", " ");

        // Collapse accidental repeated marks but preserve meaningful single punctuation.
        value = value.replaceAll("([!?])\\1+", "$1")
                .replaceAll("([;:])\\1+", "$1")
                .replaceAll("[ \\t]+([,.!?;:])", "$1")
                .replaceAll("([!?])(?=\\p{L})", "$1 ")
                .replaceAll("[ \\t]{2,}", " ");
        value = EDGE_SPACE.matcher(value).replaceAll("")
                .replaceAll("\\n[ \\t]+", "\n")
                .replaceAll("\\n{3,}", "\n\n").trim();

        if (isNoiseOnly(value)) return "";
        return value;
    }

    /** True only for empty cues or symbol debris; short real dialogue is not classified as noise. */
    public static boolean isNoiseOnly(String input) {
        if (input == null || input.trim().length() == 0) return true;
        String value = input.replace("\\N", " ").replace('\n', ' ').trim();
        if (value.matches("(?s).*\\p{L}.*") || value.matches("(?s).*\\p{N}.*")) return false;
        // '?' and '!' can be expressive dialogue; do not discard them automatically.
        return value.matches("[\\s()\\[\\]{}|¦~^_=+*/\\\\<>\uFFFD.·•_-]+");
    }

    private static String removeUnmatchedParentheses(String value) {
        char[] chars = value.toCharArray();
        boolean[] remove = new boolean[chars.length];
        int[] stack = new int[chars.length];
        int top = 0;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '(') {
                stack[top++] = i;
            } else if (chars[i] == ')') {
                if (top > 0) {
                    top--;
                } else {
                    remove[i] = true;
                }
            }
        }
        while (top > 0) remove[stack[--top]] = true;
        StringBuilder out = new StringBuilder(chars.length);
        for (int i = 0; i < chars.length; i++) if (!remove[i]) out.append(chars[i]);
        return out.toString();
    }

    /** Match key for temporal/duplicate comparison only; do not use this as exported text. */
    public static String normalizedForMatch(String input) {
        return clean(input).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ").trim();
    }
}
