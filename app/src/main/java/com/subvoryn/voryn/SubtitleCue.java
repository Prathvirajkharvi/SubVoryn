package com.subvoryn.voryn;

/** One editable subtitle cue. Times are milliseconds from the start of the video. */
public class SubtitleCue {
    public long startMs;
    public long endMs;
    public String text;
    public String rawText;
    public String style;
    public int layer;
    /** ASS alignment: 1..9; 0 means unknown/default. */
    public int alignment;
    public int marginV;

    public SubtitleCue(long startMs, long endMs, String text) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text == null ? "" : text;
        this.rawText = this.text;
        this.style = "Default";
        this.layer = 0;
        this.alignment = 0;
        this.marginV = 25;
    }

    public long durationMs() {
        return endMs - startMs;
    }
}
