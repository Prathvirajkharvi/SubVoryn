package com.subvoryn.voryn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.text.InputType;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.googlecode.tesseract.android.TessBaseAPI;

/** SubVoryn Beta V1.1: hardsub video OCR, subtitle timing review and format conversion. */
public class MainActivity extends Activity {
    private static final int REQUEST_VIDEO = 4100;
    private static final int REQUEST_IMPORT = 4101;
    private static final int REQUEST_EXPORT = 4102;
    private static final int MAX_VISIBLE_CUES = 24;
    private static final int C_BG = Color.rgb(20, 25, 32);
    private static final int C_PANEL = Color.rgb(29, 37, 46);
    private static final int C_PANEL_2 = Color.rgb(35, 44, 55);
    private static final int C_BORDER = Color.rgb(57, 72, 88);
    private static final int C_TEXT = Color.rgb(240, 244, 248);
    private static final int C_MUTED = Color.rgb(164, 178, 193);
    private static final int C_ACCENT = Color.rgb(158, 184, 208);
    private static final int C_AMBER = Color.rgb(194, 154, 101);

    private LinearLayout root;
    private LinearLayout content;
    private TextView statusText;
    private TextView statsText;
    private TextView fileNameText;
    private TextView qcText;
    private TextView cueCountText;
    private LinearLayout cueList;
    private LinearLayout exportRow;
    private Button showAllButton;
    private RadioGroup positionGroup;
    private TextView videoNameText;
    private TextView videoInfoText;
    private ImageView videoPreview;
    private Spinner speedSpinner;
    private Spinner regionSpinner;
    private ProgressBar ocrProgress;
    private TextView ocrProgressText;
    private Button extractButton;
    private Button cancelScanButton;
    private Uri videoUri;
    private String videoName = "";
    private long videoDurationMs = 0L;
    private volatile boolean cancelScan = false;
    private volatile boolean scanning = false;
    private Thread scanThread;
    private List<SubtitleCue> cues = new ArrayList<SubtitleCue>();
    private String sourceName = "";
    private int sourceFormat = SubtitleEngine.FORMAT_SRT;
    private int selectedPosition = SubtitleEngine.POSITION_ORIGINAL;
    private int exportFormat = SubtitleEngine.FORMAT_SRT;
    private boolean imported = false;
    private boolean showAllCues = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(C_BG);
            getWindow().setNavigationBarColor(C_BG);
        }
        buildScreen();
        refreshScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(C_BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(28));
        root.setBackgroundColor(C_BG);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = new TextView(this);
        mark.setText("SV");
        mark.setTextSize(18);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setTextColor(C_TEXT);
        mark.setGravity(Gravity.CENTER);
        mark.setBackgroundDrawable(round(C_PANEL_2, C_BORDER, 14));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout brandWords = new LinearLayout(this);
        brandWords.setOrientation(LinearLayout.VERTICAL);
        brandWords.setPadding(dp(12), 0, 0, 0);
        TextView title = text("SubVoryn", 24, C_TEXT, true);
        TextView version = text("SUBTITLE WORKSPACE  •  BETA V1.1 OCR", 10, C_ACCENT, true);
        brandWords.addView(title);
        brandWords.addView(version);
        brand.addView(brandWords, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settingsTag = text("LOCAL", 10, C_MUTED, true);
        settingsTag.setGravity(Gravity.CENTER);
        settingsTag.setPadding(dp(10), dp(7), dp(10), dp(7));
        settingsTag.setBackgroundDrawable(round(C_PANEL, C_BORDER, 18));
        brand.addView(settingsTag);
        root.addView(brand, margin(-1, -2, 0, 18));

        root.addView(text("Extract. Refine. Convert.", 25, C_TEXT, true), margin(-1, -2, 0, 4));
        root.addView(text("Subtitle tools built for precise timing and clean exports.", 13, C_MUTED, false), margin(-1, -2, 0, 18));

        // Main workflow: extract hardcoded subtitles from the VIDEO itself.
        LinearLayout videoCard = card();
        videoCard.addView(text("HARDSUB VIDEO OCR", 10, C_ACCENT, true), margin(-1, -2, 0, 7));
        videoNameText = text("No video selected", 16, C_TEXT, true);
        videoCard.addView(videoNameText, margin(-1, -2, 0, 4));
        videoInfoText = text("Choose a video with subtitles permanently burned into the picture.", 12, C_MUTED, false);
        videoCard.addView(videoInfoText, margin(-1, -2, 0, 10));
        Button chooseVideoButton = button("Import hardsub video", true);
        chooseVideoButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseVideo(); } });
        videoCard.addView(chooseVideoButton, fullWidth());
        videoPreview = new ImageView(this);
        videoPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        videoPreview.setAdjustViewBounds(true);
        videoPreview.setBackgroundDrawable(round(C_PANEL_2, C_BORDER, 10));
        videoPreview.setVisibility(View.GONE);
        videoCard.addView(videoPreview, margin(-1, dp(170), 0, 10));
        videoCard.addView(text("SCAN SPEED", 10, C_ACCENT, true), margin(-1, -2, 0, 4));
        speedSpinner = spinner(new String[] {"Fast — sample every 1.2 seconds", "Balanced — sample every 0.6 seconds", "Accurate — sample every 0.3 seconds"});
        speedSpinner.setSelection(1);
        videoCard.addView(speedSpinner, margin(-1, dp(46), 0, 8));
        videoCard.addView(text("SUBTITLE AREA", 10, C_ACCENT, true), margin(-1, -2, 0, 4));
        regionSpinner = spinner(new String[] {"Bottom 35% (recommended)", "Bottom 50%", "Top 35%", "Full frame"});
        videoCard.addView(regionSpinner, margin(-1, dp(46), 0, 8));
        ocrProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        ocrProgress.setMax(100);
        ocrProgress.setProgress(0);
        videoCard.addView(ocrProgress, margin(-1, dp(8), 0, 4));
        ocrProgressText = text("Ready when a video is selected.", 12, C_MUTED, false);
        videoCard.addView(ocrProgressText, margin(-1, -2, 0, 9));
        LinearLayout extractionButtons = new LinearLayout(this);
        extractionButtons.setOrientation(LinearLayout.HORIZONTAL);
        extractButton = button("Extract subtitles", true);
        extractButton.setEnabled(false);
        extractButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startOcrExtraction(); } });
        extractionButtons.addView(extractButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        cancelScanButton = button("Cancel", false);
        cancelScanButton.setEnabled(false);
        cancelScanButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { cancelScan = true; ocrProgressText.setText("Stopping after current frame…"); } });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, dp(46), 0.62f);
        cancelLp.leftMargin = dp(7);
        extractionButtons.addView(cancelScanButton, cancelLp);
        videoCard.addView(extractionButtons, fullWidth());
        videoCard.addView(text("Offline English OCR. Timing is estimated from sampled frames; Accurate mode is best for short dialogue but takes longer.", 11, C_MUTED, false), margin(-1, 9, 0, 0));
        root.addView(videoCard, margin(-1, -2, 0, 12));

        LinearLayout importCard = card();
        importCard.addView(text("OPTIONAL SUBTITLE FILE", 10, C_ACCENT, true), margin(-1, -2, 0, 7));
        fileNameText = text("No file imported yet", 16, C_TEXT, true);
        importCard.addView(fileNameText, margin(-1, -2, 0, 5));
        statusText = text("Optional: import an existing SRT, VTT or ASS file to edit/convert it.", 12, C_MUTED, false);
        importCard.addView(statusText, margin(-1, -2, 0, 13));
        Button importButton = button("Import existing subtitle file", true);
        importButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseImport(); } });
        importCard.addView(importButton, fullWidth());
        root.addView(importCard, margin(-1, -2, 0, 12));

        LinearLayout statsCard = card();
        statsCard.addView(text("PROJECT SUMMARY", 10, C_ACCENT, true), margin(-1, -2, 0, 10));
        statsText = text("0 cues  ·  00:00 duration", 16, C_TEXT, true);
        statsCard.addView(statsText, margin(-1, -2, 0, 4));
        cueCountText = text("Supported: .srt  ·  .vtt  ·  .ass/.ssa", 12, C_MUTED, false);
        statsCard.addView(cueCountText);
        root.addView(statsCard, margin(-1, -2, 0, 12));

        LinearLayout convertCard = card();
        convertCard.addView(text("EXPORT FORMAT", 10, C_ACCENT, true), margin(-1, -2, 0, 7));
        convertCard.addView(text("Choose the format to save your edited cues.", 12, C_MUTED, false), margin(-1, -2, 0, 12));
        exportRow = new LinearLayout(this);
        exportRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = new String[] {"SRT", "VTT", "ASS"};
        for (int i = 0; i < labels.length; i++) {
            final int format = i;
            Button b = button(labels[i], i == exportFormat);
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { exportFormat = format; updateExportButtons(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 0) lp.leftMargin = dp(7);
            exportRow.addView(b, lp);
        }
        convertCard.addView(exportRow, fullWidth());
        Button exportButton = button("Export subtitle file", true);
        exportButton.setId(10002);
        exportButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseExport(); } });
        convertCard.addView(exportButton, margin(-1, 11, 0, 0));
        root.addView(convertCard, margin(-1, -2, 0, 12));

        LinearLayout positionCard = card();
        positionCard.addView(text("SUBTITLE POSITION", 10, C_ACCENT, true), margin(-1, -2, 0, 5));
        positionCard.addView(text("Applies to VTT/ASS exports. SRT cannot reliably store positioning.", 12, C_MUTED, false), margin(-1, -2, 0, 9));
        positionGroup = new RadioGroup(this);
        positionGroup.setOrientation(RadioGroup.VERTICAL);
        addPositionChoice(positionGroup, "Original — preserve detected ASS alignment", SubtitleEngine.POSITION_ORIGINAL);
        addPositionChoice(positionGroup, "Top — ASS MarginV 25", SubtitleEngine.POSITION_TOP);
        addPositionChoice(positionGroup, "Bottom — ASS MarginV 25", SubtitleEngine.POSITION_BOTTOM);
        positionGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Object tag = group.findViewById(checkedId) == null ? null : group.findViewById(checkedId).getTag();
                if (tag instanceof Integer) selectedPosition = ((Integer) tag).intValue();
            }
        });
        positionCard.addView(positionGroup);
        root.addView(positionCard, margin(-1, -2, 0, 12));

        LinearLayout qcCard = card();
        qcCard.addView(text("TIMING & DUPLICATE TOOLS", 10, C_ACCENT, true), margin(-1, -2, 0, 7));
        qcText = text("Import a file to run quality checks.", 12, C_MUTED, false);
        qcCard.addView(qcText, margin(-1, -2, 0, 12));
        Button duplicateButton = button("Merge adjacent duplicate dialogues", false);
        duplicateButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { mergeDuplicates(); } });
        qcCard.addView(duplicateButton, fullWidth());
        Button reviewButton = button("Review short-duration cues", false);
        reviewButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { reviewShortCues(); } });
        qcCard.addView(reviewButton, margin(-1, 8, 0, 0));
        Button fixButton = button("Suggest a minimum duration", false);
        fixButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { promptExtendShortCues(); } });
        qcCard.addView(fixButton, margin(-1, 8, 0, 0));
        root.addView(qcCard, margin(-1, -2, 0, 12));

        LinearLayout cuesCard = card();
        LinearLayout cueHead = new LinearLayout(this);
        cueHead.setOrientation(LinearLayout.HORIZONTAL);
        cueHead.setGravity(Gravity.CENTER_VERTICAL);
        cueHead.addView(text("DIALOGUE REVIEW", 10, C_ACCENT, true), new LinearLayout.LayoutParams(0, -2, 1));
        showAllButton = button("Show all", false);
        showAllButton.setTextSize(11);
        showAllButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showAllCues = !showAllCues; refreshCueList(); } });
        cueHead.addView(showAllButton, new LinearLayout.LayoutParams(-2, dp(34)));
        cuesCard.addView(cueHead, margin(-1, -2, 0, 7));
        cuesCard.addView(text("Tap a cue to edit its text or timestamps.", 12, C_MUTED, false), margin(-1, -2, 0, 10));
        cueList = new LinearLayout(this);
        cueList.setOrientation(LinearLayout.VERTICAL);
        cuesCard.addView(cueList, fullWidth());
        root.addView(cuesCard, margin(-1, -2, 0, 12));

        LinearLayout ocrCard = card();
        TextView ocrTitle = text("OCR NOTES", 10, C_AMBER, true);
        ocrCard.addView(ocrTitle, margin(-1, -2, 0, 6));
        ocrCard.addView(text("This beta uses the bundled English Tesseract OCR model as the first on-device extraction engine. It does not upload your video. Use the subtitle-area control to reduce credits/signage being detected. PaddleOCR can be evaluated as a later engine option.", 12, C_MUTED, false));
        root.addView(ocrCard, margin(-1, -2, 0, 8));

        setContentView(scroll);
    }

    private void addPositionChoice(RadioGroup group, String label, int position) {
        RadioButton rb = new RadioButton(this);
        rb.setText(label);
        rb.setTextSize(13);
        rb.setTextColor(C_TEXT);
        rb.setPadding(0, dp(5), 0, dp(5));
        rb.setTag(Integer.valueOf(position));
        rb.setId(2000 + position);
        group.addView(rb, new RadioGroup.LayoutParams(-1, -2));
        if (position == selectedPosition) rb.setChecked(true);
    }

    private Spinner spinner(String[] values) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setBackgroundDrawable(round(C_PANEL_2, C_BORDER, 8));
        return sp;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackgroundDrawable(round(C_PANEL, C_BORDER, 16));
        return box;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? C_BG : C_TEXT);
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        b.setBackgroundDrawable(round(primary ? C_ACCENT : C_PANEL_2, primary ? C_ACCENT : C_BORDER, 11));
        return b;
    }

    private GradientDrawable round(int color, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), border);
        return d;
    }

    private LinearLayout.LayoutParams margin(int w, int h, int left, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.leftMargin = dp(left);
        lp.bottomMargin = dp(bottom);
        return lp;
    }

    private LinearLayout.LayoutParams fullWidth() { return new LinearLayout.LayoutParams(-1, dp(46)); }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void chooseVideo() {
        if (scanning) { toast("Stop the current scan before changing the video."); return; }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_VIDEO); }
        catch (Exception e) { toast("Could not open the video picker on this device."); }
    }

    private void chooseImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/x-subrip", "text/vtt", "text/x-ssa", "application/octet-stream", "text/plain"});
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(Intent.createChooser(intent, "Choose an SRT, VTT or ASS subtitle file"), REQUEST_IMPORT); }
        catch (Exception e) { toast("Could not open file picker on this device."); }
    }

    private void chooseExport() {
        if (!imported || cues.size() == 0) {
            toast("Pehle video se subtitles extract karo ya subtitle file import karo.");
            return;
        }
        if (Build.VERSION.SDK_INT < 19) {
            toast("Export requires Android 4.4 or newer (Storage Access Framework).");
            return;
        }
        String base = sourceName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        String ext = exportFormat == SubtitleEngine.FORMAT_SRT ? ".srt" : (exportFormat == SubtitleEngine.FORMAT_VTT ? ".vtt" : ".ass");
        String mime = exportFormat == SubtitleEngine.FORMAT_VTT ? "text/vtt" : (exportFormat == SubtitleEngine.FORMAT_ASS ? "text/x-ssa" : "application/x-subrip");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, (base.length() == 0 ? "SubVoryn_export" : base) + ext);
        try { startActivityForResult(intent, REQUEST_EXPORT); }
        catch (Exception e) { toast("Could not open export picker."); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_VIDEO) {
            try {
                if (Build.VERSION.SDK_INT >= 19) getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            importVideoUri(uri);
        } else if (requestCode == REQUEST_IMPORT) importUri(uri);
        else if (requestCode == REQUEST_EXPORT) exportUri(uri);
    }

    private void importUri(Uri uri) {
        try {
            String name = queryName(uri);
            String mime = getContentResolver().getType(uri);
            if (!isSubtitleFileName(name) || (mime != null && mime.toLowerCase(Locale.US).startsWith("video/"))) {
                toast("Ye video nahi, subtitle file import karne ka option hai. Hardsub extraction ke liye upar 'Import hardsub video' use karo.");
                return;
            }
            long fileSize = querySize(uri);
            if (fileSize > 20L * 1024L * 1024L) {
                toast("Subtitle file unusually large hai. 20 MB se chhoti SRT/VTT/ASS file select karo.");
                return;
            }
            String content = readText(uri);
            int format = SubtitleEngine.guessFormat(name, content);
            List<SubtitleCue> parsed = SubtitleEngine.parse(content, format);
            if (parsed.size() == 0) {
                toast("No valid subtitle cues found. Check that the file is SRT, VTT or ASS.");
                return;
            }
            cues = parsed;
            sourceName = name == null ? "Imported subtitle" : name;
            sourceFormat = format;
            imported = true;
            showAllCues = false;
            selectedPosition = SubtitleEngine.POSITION_ORIGINAL;
            if (positionGroup != null && positionGroup.getChildCount() > 0) positionGroup.check(2000);
            refreshScreen();
            toast("Imported " + cues.size() + " subtitle cues.");
        } catch (Exception e) {
            toast("Import failed: " + safeMessage(e));
        }
    }

    private String queryName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) {
        } finally { if (cursor != null) cursor.close(); }
        String path = uri.getLastPathSegment();
        return path == null ? "subtitle.txt" : path;
    }

    private String readText(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("Could not read the selected file.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > 20 * 1024 * 1024) throw new IOException("Subtitle file is too large.");
                out.write(buffer, 0, read);
            }
        } finally { in.close(); }
        return new String(out.toByteArray(), Charset.forName("UTF-8"));
    }

    private void exportUri(Uri uri) {
        try {
            String output = SubtitleEngine.export(cues, exportFormat, selectedPosition);
            OutputStream stream = getContentResolver().openOutputStream(uri, "w");
            if (stream == null) throw new Exception("Could not open the output file.");
            stream.write(output.getBytes("UTF-8"));
            stream.flush();
            stream.close();
            toast("Export complete: " + extensionFor(exportFormat));
        } catch (Exception e) {
            toast("Export failed: " + safeMessage(e));
        }
    }

    private String extensionFor(int format) {
        if (format == SubtitleEngine.FORMAT_VTT) return ".vtt";
        if (format == SubtitleEngine.FORMAT_ASS) return ".ass";
        return ".srt";
    }

    private boolean isSubtitleFileName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.US).trim();
        return lower.endsWith(".srt") || lower.endsWith(".vtt") || lower.endsWith(".ass") || lower.endsWith(".ssa");
    }

    private long querySize(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[] {OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx);
            }
        } catch (Exception ignored) {
        } finally { if (cursor != null) cursor.close(); }
        return -1L;
    }

    private void importVideoUri(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Bitmap thumb = null;
        try {
            retriever.setDataSource(this, uri);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long parsedDuration = duration == null ? 0L : Long.parseLong(duration);
            if (parsedDuration <= 0L) throw new IOException("Could not read video duration or the file has no video track.");
            String name = queryName(uri);
            videoUri = uri;
            videoName = name == null ? "Selected video" : name;
            videoDurationMs = parsedDuration;
            cues.clear();
            imported = false;
            sourceName = "";
            try { thumb = getSampleFrame(retriever, Math.min(1000000L, Math.max(0L, videoDurationMs / 2L)), 960, 540); }
            catch (Throwable ignored) { thumb = null; }
            videoNameText.setText(videoName);
            videoInfoText.setText("Duration: " + durationLabel(videoDurationMs) + "  ·  Ready for local OCR");
            if (thumb != null) {
                videoPreview.setImageBitmap(thumb);
                videoPreview.setVisibility(View.VISIBLE);
            } else {
                videoPreview.setVisibility(View.GONE);
            }
            ocrProgress.setProgress(0);
            ocrProgressText.setText("Video ready. Choose scan speed and subtitle area, then extract.");
            extractButton.setEnabled(true);
            refreshScreen();
            toast("Video selected. Ab OCR run kar sakte ho.");
        } catch (Throwable e) {
            videoUri = null;
            videoDurationMs = 0L;
            if (thumb != null && !thumb.isRecycled()) thumb.recycle();
            videoNameText.setText("No video selected");
            videoInfoText.setText("Could not open this video. Try another MP4/MKV file from the phone's file picker.");
            videoPreview.setImageDrawable(null);
            videoPreview.setVisibility(View.GONE);
            extractButton.setEnabled(false);
            toast("Video open nahi hua: " + safeThrowable(e));
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
    }

    private void startOcrExtraction() {
        if (videoUri == null || videoDurationMs <= 0L) { toast("Pehle 'Import hardsub video' se video select karo."); return; }
        if (scanning) { toast("OCR already running hai."); return; }
        final Uri selectedUri = videoUri;
        final long duration = videoDurationMs;
        final int intervalMs;
        int speedChoice = speedSpinner == null ? 1 : speedSpinner.getSelectedItemPosition();
        if (speedChoice == 0) intervalMs = 1200;
        else if (speedChoice == 2) intervalMs = 300;
        else intervalMs = 600;
        final int regionChoice = regionSpinner == null ? 0 : regionSpinner.getSelectedItemPosition();
        cancelScan = false;
        scanning = true;
        extractButton.setEnabled(false);
        cancelScanButton.setEnabled(true);
        ocrProgress.setProgress(0);
        ocrProgressText.setText("Preparing English OCR model…");
        scanThread = new Thread(new Runnable() {
            public void run() { runOcrWorker(selectedUri, duration, intervalMs, regionChoice); }
        }, "SubVoryn-OCR");
        scanThread.start();
    }

    private void runOcrWorker(Uri selectedUri, long duration, int intervalMs, int regionChoice) {
        final List<SubtitleCue> found = new ArrayList<SubtitleCue>();
        TessBaseAPI tess = null;
        MediaMetadataRetriever retriever = null;
        String error = null;
        boolean stopped = false;
        long lastUiUpdate = -1000L;
        try {
            File tessBase = new File(getFilesDir(), "tesseract");
            File tessData = new File(tessBase, "tessdata");
            if (!tessData.exists() && !tessData.mkdirs()) throw new IOException("Could not create OCR model folder.");
            File trainedData = new File(tessData, "eng.traineddata");
            if (!trainedData.exists() || trainedData.length() < 1000000L) copyEnglishModel(trainedData);

            tess = new TessBaseAPI();
            if (!tess.init(tessBase.getAbsolutePath(), "eng", TessBaseAPI.OEM_LSTM_ONLY)) {
                throw new IOException("English OCR model could not initialize. Reinstall the app and try again.");
            }
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
            tess.setVariable("preserve_interword_spaces", "1");

            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, selectedUri);
            String durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long actualDurationValue = duration;
            if (durationString != null) actualDurationValue = Math.min(duration, Long.parseLong(durationString));
            final long actualDuration = actualDurationValue;
            long lastFingerprint = Long.MIN_VALUE;
            String cachedText = "";
            String activeNorm = "";
            long lastDetectedAt = -1L;
            for (long t = 0L; t < actualDuration; t += intervalMs) {
                if (cancelScan) { stopped = true; break; }
                Bitmap frame = null;
                Bitmap crop = null;
                String recognized = "";
                try {
                    frame = getSampleFrame(retriever, t * 1000L, 1280, 720);
                    if (frame != null) {
                        crop = cropSubtitleRegion(frame, regionChoice);
                        long fingerprint = bitmapFingerprint(crop);
                        if (fingerprint == lastFingerprint) {
                            // Identical sampled subtitle region: reuse the prior OCR result and avoid another OCR call.
                            recognized = cachedText;
                        } else {
                            tess.setImage(crop);
                            recognized = cleanOcrText(tess.getUTF8Text());
                            if (tess.meanConfidence() < 14) recognized = "";
                            lastFingerprint = fingerprint;
                            cachedText = recognized;
                        }
                        String normalized = normalizeOcr(recognized);
                        if (recognized.length() > 0 && normalized.length() > 1) {
                            if (found.size() > 0 && normalized.equals(activeNorm) && lastDetectedAt >= 0L && t - lastDetectedAt <= (long)(intervalMs * 2.1)) {
                                SubtitleCue active = found.get(found.size() - 1);
                                active.endMs = Math.min(actualDuration, t + intervalMs);
                                if (recognized.length() > active.text.length() && recognized.length() < 180) {
                                    active.text = recognized;
                                    active.rawText = recognized;
                                }
                            } else {
                                long end = Math.min(actualDuration, t + intervalMs);
                                if (end <= t) end = Math.min(actualDuration, t + 100L);
                                SubtitleCue cue = new SubtitleCue(t, end, recognized);
                                cue.rawText = recognized;
                                cue.text = recognized;
                                cue.alignment = 2;
                                cue.marginV = 25;
                                found.add(cue);
                            }
                            activeNorm = normalized;
                            lastDetectedAt = t;
                        }
                    }
                } finally {
                    if (crop != null && crop != frame && !crop.isRecycled()) crop.recycle();
                    if (frame != null && !frame.isRecycled()) frame.recycle();
                }
                if (t - lastUiUpdate >= 500L) {
                    final int progress = (int)Math.min(99L, (t * 100L) / Math.max(1L, actualDuration));
                    final long scannedAt = t;
                    final int cueCount = found.size();
                    runOnUiThread(new Runnable() { public void run() {
                        if (ocrProgress != null) ocrProgress.setProgress(progress);
                        if (ocrProgressText != null) ocrProgressText.setText("Scanning " + durationLabel(scannedAt) + " / " + durationLabel(actualDuration) + "  ·  " + cueCount + " cues found");
                    }});
                    lastUiUpdate = t;
                }
            }
            SubtitleEngine.mergeConsecutiveDuplicates(found, intervalMs * 2L);
        } catch (Throwable e) {
            error = safeThrowable(e);
        } finally {
            if (tess != null) { try { tess.recycle(); } catch (Throwable ignored) { } }
            if (retriever != null) { try { retriever.release(); } catch (Throwable ignored) { } }
        }
        final String finalError = error;
        final boolean wasStopped = stopped || cancelScan;
        final List<SubtitleCue> result = found;
        runOnUiThread(new Runnable() {
            public void run() {
                scanning = false;
                if (extractButton != null) extractButton.setEnabled(videoUri != null);
                if (cancelScanButton != null) cancelScanButton.setEnabled(false);
                if (finalError != null) {
                    ocrProgressText.setText("OCR failed. Video is kept selected; you can retry.");
                    toast("OCR error: " + finalError);
                    return;
                }
                if (result.size() == 0) {
                    ocrProgress.setProgress(0);
                    ocrProgressText.setText("No readable subtitle text was detected. Try another subtitle area or Accurate mode.");
                    toast(wasStopped ? "Scan stopped before finding subtitles." : "No subtitle cues detected. Try Bottom 50% or Full frame.");
                    return;
                }
                cues.clear();
                cues.addAll(result);
                sourceName = baseName(videoName) + ".srt";
                sourceFormat = SubtitleEngine.FORMAT_SRT;
                exportFormat = SubtitleEngine.FORMAT_SRT;
                selectedPosition = SubtitleEngine.POSITION_ORIGINAL;
                imported = true;
                showAllCues = false;
                refreshScreen();
                ocrProgress.setProgress(wasStopped ? Math.min(99, (int)(100L * result.get(result.size() - 1).endMs / Math.max(1L, videoDurationMs))) : 100);
                ocrProgressText.setText((wasStopped ? "Partial scan ready" : "OCR complete") + " · " + result.size() + " cues · check short timings before export.");
                toast((wasStopped ? "Partial OCR ready: " : "OCR complete: ") + result.size() + " cues. Choose SRT/VTT/ASS and export.");
            }
        });
    }

    private String baseName(String name) {
        if (name == null || name.length() == 0) return "SubVoryn_Extracted";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void copyEnglishModel(File target) throws IOException {
        InputStream in = getAssets().open("tessdata/eng.traineddata");
        OutputStream out = new BufferedOutputStream(new FileOutputStream(target));
        byte[] buffer = new byte[16384];
        int read;
        try { while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read); }
        finally { try { in.close(); } catch (Exception ignored) { } try { out.close(); } catch (Exception ignored) { } }
    }

    private Bitmap getSampleFrame(MediaMetadataRetriever retriever, long timeUs, int maxWidth, int maxHeight) {
        if (Build.VERSION.SDK_INT >= 27) {
            return retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, maxWidth, maxHeight);
        }
        return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
    }

    private Bitmap cropSubtitleRegion(Bitmap frame, int regionChoice) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        int top = 0;
        int cropHeight = height;
        if (regionChoice == 0) { cropHeight = Math.max(1, (int)(height * 0.35f)); top = height - cropHeight; }
        else if (regionChoice == 1) { cropHeight = Math.max(1, (int)(height * 0.50f)); top = height - cropHeight; }
        else if (regionChoice == 2) { cropHeight = Math.max(1, (int)(height * 0.35f)); top = 0; }
        if (top == 0 && cropHeight == height) return frame;
        return Bitmap.createBitmap(frame, 0, top, width, cropHeight);
    }

    private long bitmapFingerprint(Bitmap bitmap) {
        long hash = 1469598103934665603L;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 32; x++) {
                int px = bitmap.getPixel(Math.min(w - 1, x * w / 32), Math.min(h - 1, y * h / 16));
                int gray = ((Color.red(px) * 30) + (Color.green(px) * 59) + (Color.blue(px) * 11)) / 100;
                hash ^= (gray / 8);
                hash *= 1099511628211L;
            }
        }
        return hash;
    }

    private String cleanOcrText(String raw) {
        if (raw == null) return "";
        String[] lines = raw.replace('\r', '\n').split("\\n");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replaceAll("\\s+", " ").trim();
            line = line.replaceAll("^[|¦·•_=~]+", "").replaceAll("[|¦·•_=~]+$", "").trim();
            if (line.length() == 0) continue;
            if (out.length() > 0) out.append("\\N");
            out.append(line);
        }
        String value = out.toString().trim();
        String norm = normalizeOcr(value);
        return norm.length() < 2 ? "" : value;
    }

    private String normalizeOcr(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{N}]+", " ").replaceAll("\\s+", " ").trim();
    }

    private String safeThrowable(Throwable e) {
        if (e == null || e.getMessage() == null || e.getMessage().trim().length() == 0) return e == null ? "unknown error" : e.getClass().getSimpleName();
        return e.getMessage();
    }

    @Override
    protected void onDestroy() {
        cancelScan = true;
        super.onDestroy();
    }

    private void refreshScreen() {
        if (statusText == null) return;
        if (!imported) {
            fileNameText.setText("No file imported yet");
            statusText.setText("Use the video extractor above, or import an existing SRT/VTT/ASS file to edit it.");
            statsText.setText("0 cues  ·  00:00 duration");
            cueCountText.setText("Supported: .srt  ·  .vtt  ·  .ass/.ssa");
            qcText.setText("Import a file to run quality checks.");
        } else {
            fileNameText.setText(sourceName);
            statusText.setText(formatName(sourceFormat) + " imported · Local processing · Editable cues");
            long last = 0;
            for (int i = 0; i < cues.size(); i++) if (cues.get(i).endMs > last) last = cues.get(i).endMs;
            statsText.setText(cues.size() + " cues  ·  " + durationLabel(last));
            cueCountText.setText("Source: " + formatName(sourceFormat) + "  ·  Short cues (< 0.70s): " + SubtitleEngine.countShortCues(cues, 700));
            qcText.setText("Possible adjacent duplicates: " + SubtitleEngine.countDuplicates(cues, 350) + "  ·  Short cues (< 0.70s): " + SubtitleEngine.countShortCues(cues, 700) + "\nTiming fixes are heuristic—preview your video before final use.");
        }
        updateExportButtons();
        refreshCueList();
    }

    private void updateExportButtons() {
        if (exportRow == null) return;
        for (int i = 0; i < exportRow.getChildCount(); i++) {
            Button b = (Button) exportRow.getChildAt(i);
            boolean active = i == exportFormat;
            b.setTextColor(active ? C_BG : C_TEXT);
            b.setBackgroundDrawable(round(active ? C_ACCENT : C_PANEL_2, active ? C_ACCENT : C_BORDER, 11));
            b.setEnabled(imported && cues.size() > 0);
        }
        View v = root.findViewById(10002);
        if (v != null) v.setEnabled(imported && cues.size() > 0);
    }

    private String formatName(int format) {
        if (format == SubtitleEngine.FORMAT_ASS) return "ASS";
        if (format == SubtitleEngine.FORMAT_VTT) return "WebVTT";
        return "SRT";
    }

    private String durationLabel(long ms) {
        if (ms < 0L) ms = 0L;
        long hours = ms / 3600000L;
        long minutes = (ms % 3600000L) / 60000L;
        long seconds = (ms % 60000L) / 1000L;
        if (hours > 0L) return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%02d:%02d", ms / 60000L, seconds);
    }

    private void refreshCueList() {
        if (cueList == null) return;
        if (showAllButton != null) showAllButton.setText(showAllCues ? "Show less" : "Show all");
        cueList.removeAllViews();
        if (!imported || cues.size() == 0) {
            cueList.addView(text("Your imported dialogue cues will appear here.", 12, C_MUTED, false));
            return;
        }
        int visible = showAllCues ? cues.size() : Math.min(MAX_VISIBLE_CUES, cues.size());
        for (int i = 0; i < visible; i++) {
            final int index = i;
            final SubtitleCue c = cues.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(11), dp(10), dp(11), dp(10));
            item.setBackgroundDrawable(round(C_PANEL_2, C_BORDER, 10));
            TextView time = text("#" + (i + 1) + "   " + SubtitleEngine.prettyTime(c.startMs) + "  →  " + SubtitleEngine.prettyTime(c.endMs) + "   ·   " + String.format(Locale.US, "%.2fs", c.durationMs() / 1000.0), 11, c.durationMs() < 700 ? C_AMBER : C_ACCENT, true);
            item.addView(time, margin(-1, -2, 0, 5));
            TextView body = text(SubtitleEngine.displayText(c).replace("\n", " / "), 13, C_TEXT, false);
            body.setMaxLines(3);
            item.addView(body);
            item.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { editCue(index); } });
            cueList.addView(item, margin(-1, -2, 0, 7));
        }
        if (!showAllCues && cues.size() > visible) {
            TextView more = text("+ " + (cues.size() - visible) + " more cues · use Show all", 12, C_MUTED, false);
            more.setGravity(Gravity.CENTER);
            cueList.addView(more, margin(-1, dp(35), 0, 0));
        }
    }

    private void editCue(final int index) {
        if (index < 0 || index >= cues.size()) return;
        final SubtitleCue cue = cues.get(index);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setBackgroundColor(C_PANEL);
        form.setPadding(dp(18), dp(8), dp(18), dp(4));
        final EditText start = input(SubtitleEngine.formatMs(cue.startMs), "Start time: HH:MM:SS.mmm", InputType.TYPE_CLASS_TEXT);
        final EditText end = input(SubtitleEngine.formatMs(cue.endMs), "End time: HH:MM:SS.mmm", InputType.TYPE_CLASS_TEXT);
        final EditText body = input(SubtitleEngine.displayText(cue), "Dialogue text", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        body.setMinLines(3);
        body.setGravity(Gravity.TOP | Gravity.LEFT);
        form.addView(text("START TIME", 10, C_MUTED, true)); form.addView(start, margin(-1, dp(48), 0, 8));
        form.addView(text("END TIME", 10, C_MUTED, true)); form.addView(end, margin(-1, dp(48), 0, 8));
        form.addView(text("DIALOGUE", 10, C_MUTED, true)); form.addView(body, margin(-1, dp(90), 0, 0));
        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Edit cue #" + (index + 1))
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete", null)
            .setPositiveButton("Save", null)
            .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            public void onShow(android.content.DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        long newStart = SubtitleEngine.parseTimestamp(start.getText().toString());
                        long newEnd = SubtitleEngine.parseTimestamp(end.getText().toString());
                        String newText = body.getText().toString().trim();
                        if (newStart < 0 || newEnd <= newStart || newText.length() == 0) {
                            toast("Check the timestamps and dialogue. End must be after start.");
                            return;
                        }
                        cue.startMs = newStart; cue.endMs = newEnd; cue.text = newText; cue.rawText = newText;
                        sortCues();
                        dialog.dismiss();
                        refreshScreen();
                    }
                });
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        new AlertDialog.Builder(MainActivity.this).setTitle("Delete cue?").setMessage("This dialogue will be removed from the current project.")
                            .setNegativeButton("Keep", null).setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) { cues.remove(index); dialog.dismiss(); refreshScreen(); }
                            }).show();
                    }
                });
            }
        });
        dialog.show();
    }

    private EditText input(String value, String hint, int type) {
        EditText e = new EditText(this);
        e.setSingleLine((type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(C_TEXT);
        e.setHintTextColor(C_MUTED);
        e.setPadding(dp(10), dp(7), dp(10), dp(7));
        e.setInputType(type);
        e.setBackgroundDrawable(round(C_PANEL_2, C_BORDER, 8));
        return e;
    }

    private void mergeDuplicates() {
        if (!ensureImported()) return;
        int removed = SubtitleEngine.mergeConsecutiveDuplicates(cues, 350);
        refreshScreen();
        toast(removed == 0 ? "No adjacent duplicate cues found." : "Merged " + removed + " duplicate cue(s).");
    }

    private void reviewShortCues() {
        if (!ensureImported()) return;
        StringBuilder msg = new StringBuilder();
        int count = 0;
        for (int i = 0; i < cues.size(); i++) {
            SubtitleCue c = cues.get(i);
            if (c.durationMs() > 0 && c.durationMs() < 700) {
                if (count < 12) msg.append("#").append(i + 1).append("  ").append(SubtitleEngine.prettyTime(c.startMs)).append(" - ").append(SubtitleEngine.prettyTime(c.endMs)).append("  ").append(clip(SubtitleEngine.displayText(c), 58)).append('\n');
                count++;
            }
        }
        if (count == 0) msg.append("No cues shorter than 0.70 seconds.");
        else {
            msg.insert(0, "Found " + count + " short cue(s). These may be real short dialogue or bad OCR timing. Check against the video.\n\n");
            if (count > 12) msg.append("\nShowing first 12 only. Tap cues in Dialogue Review to edit timestamps.");
        }
        new AlertDialog.Builder(this).setTitle("Short-duration review").setMessage(msg.toString()).setPositiveButton("OK", null).show();
    }

    private void promptExtendShortCues() {
        if (!ensureImported()) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setBackgroundColor(C_PANEL);
        form.setPadding(dp(18), dp(6), dp(18), dp(2));
        form.addView(text("Minimum cue duration (seconds)", 13, C_TEXT, true), margin(-1, -2, 0, 5));
        final EditText seconds = input("1.0", "e.g. 1.0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(seconds, fullWidth());
        form.addView(text("Heuristic only: a cue can be extended up to the next cue's start. This cannot infer the true dialogue end without checking the video.", 12, C_MUTED, false), margin(-1, -2, 0, 0));
        new AlertDialog.Builder(this).setTitle("Suggest timing fix").setView(form).setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int which) {
                    try {
                        double value = Double.parseDouble(seconds.getText().toString().trim());
                        if (value < 0.2 || value > 5.0) { toast("Choose between 0.2 and 5.0 seconds."); return; }
                        int changed = SubtitleEngine.extendShortCues(cues, (long) (value * 1000.0));
                        refreshScreen();
                        toast("Adjusted " + changed + " cue(s). Please review them against the video.");
                    } catch (Exception e) { toast("Enter a valid duration in seconds."); }
                }
            }).show();
    }

    private boolean ensureImported() {
        if (!imported || cues.size() == 0) { toast("Pehle video se subtitles extract karo ya subtitle file import karo."); return false; }
        return true;
    }

    private String clip(String value, int max) {
        if (value == null) return "";
        value = value.replace('\n', ' ');
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private void sortCues() {
        java.util.Collections.sort(cues, new java.util.Comparator<SubtitleCue>() {
            public int compare(SubtitleCue a, SubtitleCue b) { return a.startMs < b.startMs ? -1 : (a.startMs == b.startMs ? 0 : 1); }
        });
    }

    private String safeMessage(Exception e) {
        String s = e.getMessage();
        return s == null ? "unexpected error" : s;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
