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
import android.content.ContentValues;
import android.os.Environment;
import android.provider.MediaStore;
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
    private static final int C_BG = Color.rgb(14, 19, 26);
    private static final int C_PANEL = Color.rgb(23, 30, 40);
    private static final int C_PANEL_2 = Color.rgb(31, 40, 52);
    private static final int C_BORDER = Color.rgb(57, 72, 91);
    private static final int C_TEXT = Color.rgb(243, 246, 250);
    private static final int C_MUTED = Color.rgb(161, 176, 193);
    private static final int C_ACCENT = Color.rgb(151, 178, 207);
    private static final int C_AMBER = Color.rgb(203, 166, 113);

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
    private ArrayList<SubtitleCue> undoCues;
    private String undoLabel = "";
    private Button undoCleanupButton;

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
        TextView version = text("SUBTITLE WORKSPACE  •  OFFLINE OCR", 10, C_ACCENT, true);
        brandWords.addView(title);
        brandWords.addView(version);
        brand.addView(brandWords, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settingsTag = text("ON-DEVICE", 10, C_MUTED, true);
        settingsTag.setGravity(Gravity.CENTER);
        settingsTag.setPadding(dp(10), dp(7), dp(10), dp(7));
        settingsTag.setBackgroundDrawable(round(C_PANEL, C_BORDER, 18));
        brand.addView(settingsTag);
        root.addView(brand, margin(-1, -2, 0, 18));

        root.addView(text("Extract. Clean. Export.", 25, C_TEXT, true), margin(-1, -2, 0, 4));
        root.addView(text("Offline OCR  •  Smarter cleanup  •  SRT / VTT / ASS", 13, C_MUTED, false), margin(-1, -2, 0, 18));

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
        speedSpinner = spinner(new String[] {"Quick Preview — sample every 0.6 seconds", "Balanced — sample every 0.3 seconds", "Accurate — sample every 0.1 seconds"});
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
        convertCard.addView(exportButton, margin(-1, dp(46), 0, 0));
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
        qcCard.addView(text("QUALITY CONTROL & SMART CLEANUP", 10, C_ACCENT, true), margin(-1, -2, 0, 7));
        qcText = text("Import a file to run quality checks.", 12, C_MUTED, false);
        qcCard.addView(qcText, margin(-1, -2, 0, 12));
        undoCleanupButton = button("Undo last cleanup", false);
        undoCleanupButton.setEnabled(false);
        undoCleanupButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { undoLastCleanup(); } });
        qcCard.addView(undoCleanupButton, margin(-1, dp(44), 0, 8));
        // SUBVORYN_OCR_ARTIFACT_CLEANUP_V5
        Button cleanTextButton = button("Clean OCR spelling & stray brackets", true);
        cleanTextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { cleanupCueTextArtifacts(); }
        });
        qcCard.addView(cleanTextButton, fullWidth());
        Button removeNoiseButton = button("Review / remove obvious OCR junk", false);
        removeNoiseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { reviewAndRemoveOcrNoise(); }
        });
        qcCard.addView(removeNoiseButton, margin(-1, 8, 0, 0));
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
        cuesCard.addView(cueList, new LinearLayout.LayoutParams(-1, -2));
        root.addView(cuesCard, margin(-1, -2, 0, 12));

        LinearLayout ocrCard = card();
        TextView ocrTitle = text("OCR NOTES", 10, C_AMBER, true);
        ocrCard.addView(ocrTitle, margin(-1, -2, 0, 6));
        ocrCard.addView(text("This beta uses the bundled English Tesseract OCR model as the first on-device extraction engine. It does not upload your video. Use the subtitle-area control to reduce credits/signage being detected. PaddleOCR can be evaluated as a later engine option.", 12, C_MUTED, false));
        root.addView(ocrCard, margin(-1, -2, 0, 8));

        LinearLayout appShell = new LinearLayout(this);
        appShell.setOrientation(LinearLayout.VERTICAL);
        appShell.setBackgroundColor(C_BG);
        appShell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        appShell.addView(buildBottomNavigation(0), new LinearLayout.LayoutParams(-1, dp(60)));
        setContentView(appShell);
    }

    private LinearLayout buildBottomNavigation(int active) {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(C_PANEL);
        String[] labels = new String[] {"Home", "Encoding", "Settings"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button item = button(labels[i], i == active);
            item.setTextSize(12);
            item.setMinHeight(0); item.setMinimumHeight(0);
            item.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (index == 1) startActivity(new Intent(MainActivity.this, EncodingActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                else if (index == 2) startActivity(new Intent(MainActivity.this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }});
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            lp.leftMargin = dp(5); lp.rightMargin = dp(5); nav.addView(item, lp);
        }
        nav.setPadding(dp(5), dp(5), dp(5), dp(5));
        return nav;
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
        box.setBackgroundDrawable(round(C_PANEL, C_BORDER, 18));
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
        b.setBackgroundDrawable(round(primary ? C_ACCENT : C_PANEL_2, primary ? C_ACCENT : C_BORDER, 13));
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
        String base = sourceName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        if (base.length() == 0) base = "SubVoryn_export";
        final String filename = base + extensionFor(exportFormat);
        String mime = exportFormat == SubtitleEngine.FORMAT_VTT ? "text/vtt"
                : (exportFormat == SubtitleEngine.FORMAT_ASS ? "text/x-ssa" : "application/x-subrip");

        // Android 10+ saves directly to Download/SubVoryn without a folder picker.
        if (Build.VERSION.SDK_INT >= 29) {
            saveToDownloadsSubVoryn(filename, mime);
            return;
        }
        // Older Android versions use the Storage Access Framework fallback.
        if (Build.VERSION.SDK_INT < 19) {
            toast("Export requires Android 4.4 or newer.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        try { startActivityForResult(intent, REQUEST_EXPORT); }
        catch (Exception e) { toast("Could not open export picker."); }
    }

    private void saveToDownloadsSubVoryn(String filename, String mime) {
        Uri pendingUri = null;
        try {
            String output = SubtitleEngine.export(cues, exportFormat, selectedPosition);
            String relativePath = Environment.DIRECTORY_DOWNLOADS + "/SubVoryn/";

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename + ".pending");
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            pendingUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (pendingUri == null) throw new IOException("Android could not create the output file.");

            OutputStream stream = getContentResolver().openOutputStream(pendingUri, "w");
            if (stream == null) throw new IOException("Could not open the output file.");
            try {
                stream.write(output.getBytes("UTF-8"));
                stream.flush();
            } finally {
                stream.close();
            }

            // Replace an older export only after the new file has been written successfully.
            Cursor oldFiles = null;
            try {
                String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                        + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
                oldFiles = getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        new String[] {MediaStore.MediaColumns._ID},
                        selection, new String[] {filename, relativePath}, null);
                if (oldFiles != null) {
                    while (oldFiles.moveToNext()) {
                        long oldId = oldFiles.getLong(0);
                        Uri oldUri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                String.valueOf(oldId));
                        getContentResolver().delete(oldUri, null, null);
                    }
                }
            } finally {
                if (oldFiles != null) oldFiles.close();
            }

            ContentValues publish = new ContentValues();
            publish.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            publish.put(MediaStore.MediaColumns.IS_PENDING, 0);
            int changed = getContentResolver().update(pendingUri, publish, null, null);
            if (changed <= 0) throw new IOException("Could not finalize the saved subtitle file.");
            toast("Saved: Download/SubVoryn/" + filename);
        } catch (Exception e) {
            if (pendingUri != null) {
                try { getContentResolver().delete(pendingUri, null, null); } catch (Exception ignored) { }
            }
            toast("Export failed: " + safeMessage(e));
        }
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
            undoCues = null;
            if (undoCleanupButton != null) undoCleanupButton.setEnabled(false);
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
        if (speedChoice == 0) intervalMs = 600;
        else if (speedChoice == 2) intervalMs = 100;
        else intervalMs = 300;
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

    // SUBVORYN_TEMPORAL_OCR_V4
    // A dialogue begins only after stable observations. It ends when OCR sees a stable blank
    // or a different stable dialogue. At 100 ms, this gives a boundary estimate of roughly +/- 50 ms.
    private static final class OcrTimelineState {
        SubtitleCue activeCue;
        long lastActiveSampleMs = -1L;
        long firstEmptySampleMs = -1L;
        int emptySampleCount = 0;
        String candidateText = "";
        long candidateStartMs = -1L;
        long candidateLastSampleMs = -1L;
        int candidateSampleCount = 0;
        ArrayList<String> candidateReadings = new ArrayList<String>();
    }

    private void clearOcrCandidate(OcrTimelineState state) {
        state.candidateText = "";
        state.candidateStartMs = -1L;
        state.candidateLastSampleMs = -1L;
        state.candidateSampleCount = 0;
        state.candidateReadings.clear();
    }

    private boolean isUsableOcrDialogue(String text) {
        if (text == null) return false;
        String cleaned = OcrTextCleaner.clean(text);
        if (cleaned.length() == 0 || OcrTextCleaner.isNoiseOnly(cleaned)) return false;
        String[] lines = cleaned.replace("\\N", "\n").split("\n");
        for (String line : lines) if (isLikelySubtitleLine(line.trim())) return true;
        return false;
    }

    private boolean matchesOcrCueReadings(SubtitleCue cue,
            java.util.IdentityHashMap<SubtitleCue, ArrayList<String>> readingsByCue,
            String incoming) {
        if (cue == null || incoming == null) return false;
        if (areSimilarOcrTexts(cue.text, incoming)) return true;
        ArrayList<String> readings = readingsByCue == null ? null : readingsByCue.get(cue);
        if (readings != null) {
            for (String old : readings) {
                if (areSimilarOcrTexts(old, incoming)) return true;
            }
        }
        return false;
    }

    private boolean matchesCandidateReadings(OcrTimelineState state, String incoming) {
        if (state.candidateText.length() > 0 && areSimilarOcrTexts(state.candidateText, incoming)) return true;
        for (String old : state.candidateReadings) {
            if (areSimilarOcrTexts(old, incoming)) return true;
        }
        return false;
    }

    private long midpointMs(long a, long b) {
        if (a < 0L) return Math.max(0L, b);
        if (b < 0L) return Math.max(0L, a);
        return a + (b - a) / 2L;
    }

    private void applyConsensusToCue(SubtitleCue cue,
            java.util.IdentityHashMap<SubtitleCue, ArrayList<String>> readingsByCue) {
        if (cue == null || readingsByCue == null) return;
        ArrayList<String> readings = readingsByCue.get(cue);
        if (readings == null || readings.size() == 0) return;
        String consensus = buildConsensusOcrText(readings);
        if (consensus.length() > 0) {
            cue.text = consensus;
            cue.rawText = consensus;
        }
    }

    private void applyConsensusToOcrCues(List<SubtitleCue> cues,
            java.util.IdentityHashMap<SubtitleCue, ArrayList<String>> readingsByCue) {
        if (cues == null) return;
        for (SubtitleCue cue : cues) applyConsensusToCue(cue, readingsByCue);
    }

    private void processOcrSample(List<SubtitleCue> found,
            java.util.IdentityHashMap<SubtitleCue, ArrayList<String>> readingsByCue,
            OcrTimelineState state, String rawText, long sampleMs,
            int intervalMs, long durationMs) {
        String text = rawText == null ? "" : rawText.trim();
        if (!isUsableOcrDialogue(text)) {
            clearOcrCandidate(state);
            if (state.activeCue != null) {
                if (state.emptySampleCount == 0) state.firstEmptySampleMs = sampleMs;
                state.emptySampleCount++;
                int requiredBlankSamples = intervalMs <= 300 ? 2 : 1;
                if (state.emptySampleCount >= requiredBlankSamples) {
                    long end = midpointMs(state.lastActiveSampleMs, state.firstEmptySampleMs);
                    long minimumEnd = state.activeCue.startMs + Math.max(50L, Math.min(180L, intervalMs / 2L));
                    if (end < minimumEnd) end = minimumEnd;
                    state.activeCue.endMs = Math.min(durationMs, end);
                    state.activeCue = null;
                    state.lastActiveSampleMs = -1L;
                    state.firstEmptySampleMs = -1L;
                    state.emptySampleCount = 0;
                }
            }
            return;
        }

        state.emptySampleCount = 0;
        state.firstEmptySampleMs = -1L;

        if (state.activeCue != null && matchesOcrCueReadings(state.activeCue, readingsByCue, text)) {
            addOcrReading(readingsByCue, state.activeCue, text);
            applyConsensusToCue(state.activeCue, readingsByCue);
            state.activeCue.endMs = Math.min(durationMs, sampleMs + Math.max(40L, intervalMs / 2L));
            state.lastActiveSampleMs = sampleMs;
            clearOcrCandidate(state);
            return;
        }

        long candidateWindowMs = Math.max(350L, intervalMs * 3L);
        boolean candidateIsRecent = state.candidateStartMs >= 0L
                && sampleMs - state.candidateLastSampleMs <= candidateWindowMs;
        if (!candidateIsRecent || !matchesCandidateReadings(state, text)) {
            clearOcrCandidate(state);
            state.candidateText = text;
            state.candidateStartMs = sampleMs;
            state.candidateLastSampleMs = sampleMs;
            state.candidateSampleCount = 1;
            state.candidateReadings.add(text);
        } else {
            state.candidateLastSampleMs = sampleMs;
            state.candidateSampleCount++;
            boolean exists = false;
            String key = normalizeOcrForMatch(text);
            for (String old : state.candidateReadings) {
                if (normalizeOcrForMatch(old).equals(key)) { exists = true; break; }
            }
            if (!exists && state.candidateReadings.size() < 12) state.candidateReadings.add(text);
            if (subtitleTextQuality(text) > subtitleTextQuality(state.candidateText)) state.candidateText = text;
        }

        // Fast preview accepts one sample; Balanced/Accurate require repeated evidence.
        int requiredSamples = intervalMs <= 300 ? 2 : 1;
        if (state.candidateSampleCount < requiredSamples) return;

        long cueStart = Math.max(0L, state.candidateStartMs - intervalMs / 2L);
        if (state.activeCue != null) {
            long boundary = midpointMs(state.lastActiveSampleMs, state.candidateStartMs);
            long minBoundary = state.activeCue.startMs + Math.max(50L, Math.min(180L, intervalMs / 2L));
            if (boundary < minBoundary) boundary = minBoundary;
            if (boundary > state.candidateStartMs) boundary = state.candidateStartMs;
            if (boundary < state.activeCue.startMs) boundary = state.activeCue.startMs;
            state.activeCue.endMs = Math.min(durationMs, boundary);
            cueStart = state.activeCue.endMs;
            state.activeCue = null;
        }

        String consensus = buildConsensusOcrText(state.candidateReadings);
        if (consensus.length() == 0) consensus = state.candidateText;
        long cueEnd = Math.min(durationMs, sampleMs + Math.max(40L, intervalMs / 2L));
        if (cueEnd <= cueStart) cueEnd = Math.min(durationMs, cueStart + Math.max(80L, intervalMs));
        if (cueEnd <= cueStart) {
            clearOcrCandidate(state);
            state.lastActiveSampleMs = -1L;
            return;
        }

        SubtitleCue cue = new SubtitleCue(cueStart, cueEnd, consensus);
        cue.rawText = consensus;
        cue.text = consensus;
        cue.alignment = 2;
        cue.marginV = 25;
        found.add(cue);
        readingsByCue.put(cue, new ArrayList<String>());
        for (String reading : state.candidateReadings) addOcrReading(readingsByCue, cue, reading);
        state.activeCue = cue;
        state.lastActiveSampleMs = sampleMs;
        clearOcrCandidate(state);
    }

    private void finishOcrTimeline(List<SubtitleCue> found,
            java.util.IdentityHashMap<SubtitleCue, ArrayList<String>> readingsByCue,
            OcrTimelineState state, long durationMs, int intervalMs) {
        if (state.activeCue != null) {
            long end = state.lastActiveSampleMs < 0L ? state.activeCue.endMs
                    : Math.min(durationMs, state.lastActiveSampleMs + Math.max(40L, intervalMs / 2L));
            if (end <= state.activeCue.startMs) end = Math.min(durationMs, state.activeCue.startMs + Math.max(80L, intervalMs));
            state.activeCue.endMs = Math.min(durationMs, end);
        }
        applyConsensusToOcrCues(found, readingsByCue);
        state.activeCue = null;
        clearOcrCandidate(state);
    }

    private void runOcrWorker(Uri selectedUri, long duration, int intervalMs, int regionChoice) {
        final List<SubtitleCue> found = new ArrayList<SubtitleCue>();
        final java.util.IdentityHashMap<SubtitleCue, ArrayList<String>> cueReadings = new java.util.IdentityHashMap<SubtitleCue, ArrayList<String>>();
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
            OcrTimelineState timeline = new OcrTimelineState();
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
                            recognized = recognizeFrameText(tess, crop);
                            lastFingerprint = fingerprint;
                            cachedText = recognized;
                        }
                        processOcrSample(found, cueReadings, timeline, recognized, t, intervalMs, actualDuration);
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
            finishOcrTimeline(found, cueReadings, timeline, actualDuration, intervalMs);
            applyConsensusToOcrCues(found, cueReadings);
            for (int i = 0; i < found.size(); i++) {
                SubtitleCue cue = found.get(i);
                String raw = cue.rawText == null ? cue.text : cue.rawText;
                String clean = sanitizeSubtitleArtifacts(raw);
                cue.text = clean;
                cue.rawText = clean;
            }
            mergeNearbySimilarOcrCues(found, 650L);
            SubtitleEngine.mergeConsecutiveDuplicates(found, Math.max(100L, intervalMs / 2L));
            stabilizeOcrCueTimings(found, actualDuration, intervalMs);
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
                undoCues = null;
                if (undoCleanupButton != null) undoCleanupButton.setEnabled(false);
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

    /** Run a normal OCR pass, then try enhanced pixels + automatic segmentation only when useful. */
    private String recognizeFrameText(TessBaseAPI tess, Bitmap crop) {
        String best = "";
        int bestScore = Integer.MIN_VALUE;
        int confidence = 0;
        try {
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
            tess.setImage(crop);
            String raw = tess.getUTF8Text();
            confidence = Math.max(0, (int)tess.meanConfidence());
            best = cleanOcrText(raw);
            bestScore = scoreOcrCandidate(best, confidence);
        } catch (Throwable ignored) {
            best = "";
            bestScore = Integer.MIN_VALUE / 2;
        }

        if (best.length() == 0 || confidence < 58 || containsLikelyOcrNoise(best) || bestScore < 70) {
            Bitmap enhanced = null;
            try {
                enhanced = enhanceOcrBitmap(crop);
                tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
                tess.setImage(enhanced);
                String alternateRaw = tess.getUTF8Text();
                int alternateConfidence = Math.max(0, (int)tess.meanConfidence());
                String alternate = cleanOcrText(alternateRaw);
                int alternateScore = scoreOcrCandidate(alternate, alternateConfidence);
                if (alternateScore > bestScore) {
                    best = alternate;
                    bestScore = alternateScore;
                }
            } catch (Throwable ignored) {
                // Keep the first pass if preprocessing/alternate segmentation fails.
            } finally {
                if (enhanced != null && !enhanced.isRecycled()) enhanced.recycle();
            }
        }
        try { tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK); } catch (Throwable ignored) { }
        return stripOcrEdgeArtifacts(best);
    }

    /** High-contrast grayscale + moderate upscaling; no destructive hard threshold. */
    private Bitmap enhanceOcrBitmap(Bitmap source) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        double scale = Math.min(1.75d, Math.min(1920.0d / Math.max(1, sw), 960.0d / Math.max(1, sh)));
        if (scale < 1.0d) scale = 1.0d;
        int dw = Math.max(1, (int)Math.round(sw * scale));
        int dh = Math.max(1, (int)Math.round(sh * scale));
        int[] input = new int[sw * sh];
        int[] output = new int[dw * dh];
        source.getPixels(input, 0, sw, 0, 0, sw, sh);
        for (int y = 0; y < dh; y++) {
            int sy = Math.min(sh - 1, (int)(y / scale));
            for (int x = 0; x < dw; x++) {
                int sx = Math.min(sw - 1, (int)(x / scale));
                int pixel = input[sy * sw + sx];
                int gray = (Color.red(pixel) * 30 + Color.green(pixel) * 59 + Color.blue(pixel) * 11) / 100;
                int contrast;
                if (gray <= 65) contrast = 0;
                else if (gray >= 220) contrast = 255;
                else contrast = (gray - 65) * 255 / 155;
                output[y * dw + x] = Color.rgb(contrast, contrast, contrast);
            }
        }
        Bitmap result = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888);
        result.setPixels(output, 0, dw, 0, 0, dw, dh);
        return result;
    }

    private int scoreOcrCandidate(String text, int confidence) {
        if (text == null || text.trim().length() == 0) return -10000;
        int score = subtitleTextQuality(text) + Math.min(100, Math.max(0, confidence)) / 2;
        String[] words = normalizeOcrForMatch(text.replace("\\N", " ")).split(" ");
        for (String word : words) {
            if (word.length() == 0) continue;
            if (isOcrNoiseWord(word)) score -= 16;
            else if (word.length() >= 3 && containsVowel(word)) score += 1;
        }
        return score;
    }

    private boolean containsLikelyOcrNoise(String text) {
        if (text == null || text.length() == 0) return true;
        String[] words = normalizeOcrForMatch(text.replace("\\N", " ")).split(" ");
        int noisy = 0;
        int total = 0;
        for (String word : words) {
            if (word.length() < 3) continue;
            total++;
            if (isOcrNoiseWord(word) || word.endsWith("j")) noisy++;
        }
        return total > 0 && noisy > 0 && noisy / (double)total >= 0.12d;
    }

    private void addOcrReading(java.util.IdentityHashMap<SubtitleCue, ArrayList<String>> map,
                               SubtitleCue cue, String text) {
        if (map == null || cue == null || text == null || text.trim().length() == 0) return;
        ArrayList<String> values = map.get(cue);
        if (values == null) {
            values = new ArrayList<String>();
            map.put(cue, values);
        }
        String normalized = normalizeOcrForMatch(text);
        for (String old : values) {
            if (normalized.equals(normalizeOcrForMatch(old))) return;
        }
        // Keep the memory footprint bounded on long videos.
        if (values.size() >= 16) values.remove(0);
        values.add(text);
    }

    private long bitmapFingerprint(Bitmap bitmap) {
        long hash = 1469598103934665603L;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 48; x++) {
                int px = bitmap.getPixel(Math.min(w - 1, x * w / 48), Math.min(h - 1, y * h / 24));
                int gray = ((Color.red(px) * 30) + (Color.green(px) * 59) + (Color.blue(px) * 11)) / 100;
                hash ^= (gray / 4);
                hash *= 1099511628211L;
            }
        }
        return hash;
    }

    // SUBVORYN_OCR_ARTIFACT_CLEANUP_V5
    /** Conservative text-only cleanup. It does not alter cue times or ASS positioning tags. */
    // SUBVORYN_OCR_ARTIFACT_CLEANUP_V6
    /** Conservative OCR cleanup; cue timing and ASS metadata are handled separately. */
    private String sanitizeSubtitleArtifacts(String input) {
        return OcrTextCleaner.clean(input);
    }

    private String cleanOcrText(String raw) {
        if (raw == null) return "";
        String[] lines = raw.replace('\r', '\n').split("\\n");
        ArrayList<String> accepted = new ArrayList<String>();
        ArrayList<Integer> scores = new ArrayList<Integer>();

        for (String item : lines) {
            String line = item.replaceAll("[|¦·•_=~]+", " ")
                    .replaceAll("\\s+", " ").trim();
            line = line.replaceAll("^[^\\p{L}\\p{N}]+", "")
                    .replaceAll("[^\\p{L}\\p{N}!?.,'’\\-]+$", "").trim();
            line = sanitizeSubtitleArtifacts(line);
            if (!isLikelySubtitleLine(line)) continue;

            String norm = normalizeOcr(line);
            boolean duplicate = false;
            for (String old : accepted) {
                if (norm.equals(normalizeOcr(old))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                accepted.add(line);
                scores.add(Integer.valueOf(subtitleLineScore(line)));
            }
        }

        if (accepted.size() == 0) return "";
        ArrayList<Integer> chosen = new ArrayList<Integer>();
        while (chosen.size() < 2 && chosen.size() < accepted.size()) {
            int best = -1;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < scores.size(); i++) {
                if (chosen.contains(Integer.valueOf(i))) continue;
                int score = scores.get(i).intValue();
                if (score > bestScore) {
                    best = i;
                    bestScore = score;
                }
            }
            if (best < 0 || bestScore < 10) break;
            if (chosen.size() > 0 &&
                    bestScore < scores.get(chosen.get(0).intValue()).intValue() * 0.50) break;
            chosen.add(Integer.valueOf(best));
        }

        if (chosen.size() == 2 && chosen.get(0) > chosen.get(1)) {
            Integer temp = chosen.get(0);
            chosen.set(0, chosen.get(1));
            chosen.set(1, temp);
        }

        StringBuilder out = new StringBuilder();
        for (Integer index : chosen) {
            if (out.length() > 0) out.append("\\N");
            out.append(accepted.get(index.intValue()));
        }
        String value = out.toString().trim();
        return normalizeOcr(value).length() < 2 ? "" : stripOcrEdgeArtifacts(value);
    }

    private boolean isLikelySubtitleLine(String line) {
        if (line == null) return false;
        String value = OcrTextCleaner.clean(line);
        if (value.length() == 0 || value.length() > 110 || OcrTextCleaner.isNoiseOnly(value)) return false;
        String norm = normalizeOcrForMatch(value);
        if (norm.length() < 2) return false;
        int visible = 0, alnum = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isWhitespace(ch)) visible++;
            if (Character.isLetterOrDigit(ch)) alnum++;
        }
        if (visible == 0 || alnum == 0 || alnum / (double) visible < 0.45d) return false;
        String[] words = norm.split(" ");
        if (words.length > 15) return false;
        for (String word : words) if (word.length() > 28) return false;
        // Keep short real lines such as "No!", "Oh?", "Go." and "I"; no dictionary gate.
        if (words.length == 1 && norm.length() == 1 && !norm.matches("(?i)[ia]")) return false;
        return true;
    }

    private boolean hasKnownShortDialogueWord(String[] words) {
        if (words == null) return false;
        // Small-word vocabulary deliberately includes common English and Roman-Hindi anime dialogue.
        String known = "oh no yes hi hey okay ok right go away come here look wait stop help what why how who you me my we they he she it is are was were do did can will know think want need get got good bad great fine well please thanks sorry yeah yep nope wow gajab sirf ye magic stone hi lagbhag gold kitna bada zyada taaqatwar monsters shikaar karte hain hai na toh isse bhi main tum mujhe kya kyun ka ki ke ko se par aur lekin phir bahut achha achhi arre arey haan nahi nahi ji chalo ruko dekho suno " ;
        for (String word : words) {
            if (known.contains(" " + word + " ") || known.startsWith(word + " ") || known.endsWith(" " + word)) return true;
        }
        return false;
    }

    private int subtitleLineScore(String line) {
        String norm = normalizeOcr(line);
        String[] words = norm.split(" ");
        String common = " the and you he she we they it is are was were be do did have has to of or but if what when where why how not yes no this that with for from in on at my your me us can will just so hai hain ho na bhi toh to main mein tum mujhe ye woh ek nahi kya ka ki ke ko se sirf magic stone hi lagbhag gold gajab kitna bada zyada taaqatwar monsters shikaar karte adventurers high level isse chahiye tha thi raha rahi rahe bahut bilkul achha achhi accha arre arey haan abhi yahan wahan dekho suno ";
        int longWords = 0, shortWords = 0, known = 0;
        for (String word : words) {
            if (word.length() >= 3) longWords++;
            if (word.length() <= 2) shortWords++;
            if (common.contains(" " + word + " ")) known++;
        }
        int score = Math.min(norm.length(), 45) / 2 + longWords * 6 + known * 7 - shortWords * 4;
        if (known >= 2) score += 10;
        return score;
    }

    private int subtitleTextQuality(String text) {
        if (text == null || text.length() == 0) return 0;
        String[] lines = text.split("\\\\N|\\n");
        int score = 0;
        for (String line : lines) score += subtitleLineScore(line);
        return score;
    }

    private void mergeSimilarOcrCues(List<SubtitleCue> source,
            java.util.IdentityHashMap<SubtitleCue, ArrayList<String>> sourceReadings,
            long maxGapMs) {
        if (source == null || source.size() == 0) return;
        ArrayList<SubtitleCue> ordered = new ArrayList<SubtitleCue>(source);
        java.util.Collections.sort(ordered, new java.util.Comparator<SubtitleCue>() {
            public int compare(SubtitleCue a, SubtitleCue b) {
                return a.startMs < b.startMs ? -1 : (a.startMs == b.startMs ? 0 : 1);
            }
        });

        ArrayList<SubtitleCue> clusters = new ArrayList<SubtitleCue>();
        ArrayList<ArrayList<String>> clusterReadings = new ArrayList<ArrayList<String>>();
        for (SubtitleCue cue : ordered) {
            ArrayList<String> variants = sourceReadings == null ? null : sourceReadings.get(cue);
            if (variants == null || variants.size() == 0) {
                variants = new ArrayList<String>();
                if (cue.text != null && cue.text.trim().length() > 0) variants.add(cue.text);
            }
            int matchIndex = -1;
            for (int j = clusters.size() - 1; j >= 0; j--) {
                SubtitleCue cluster = clusters.get(j);
                if (cue.startMs > cluster.endMs + maxGapMs) continue;
                if (cue.endMs < cluster.startMs - maxGapMs) continue;
                ArrayList<String> existing = clusterReadings.get(j);
                long temporalOverlap = Math.min(cue.endMs, cluster.endMs) - Math.max(cue.startMs, cluster.startMs);
                long minimumDuration = Math.min(cue.endMs - cue.startMs, cluster.endMs - cluster.startMs);
                boolean match = false;
                for (String oldText : existing) {
                    for (String newText : variants) {
                        if (areSimilarOcrTexts(oldText, newText)
                                || areTemporallySimilarOcrTexts(oldText, newText, temporalOverlap, minimumDuration)) {
                            match = true; break;
                        }
                    }
                    if (match) break;
                }
                if (match) { matchIndex = j; break; }
            }

            if (matchIndex < 0) {
                clusters.add(cue);
                clusterReadings.add(new ArrayList<String>(variants));
            } else {
                SubtitleCue target = clusters.get(matchIndex);
                if (cue.startMs < target.startMs) target.startMs = cue.startMs;
                if (cue.endMs > target.endMs) target.endMs = cue.endMs;
                ArrayList<String> combined = clusterReadings.get(matchIndex);
                for (String value : variants) addUniqueOcrVariant(combined, value);
                clusterReadings.set(matchIndex, combined);
                String consensus = buildConsensusOcrText(combined);
                if (consensus.length() > 0) {
                    target.text = consensus;
                    target.rawText = consensus;
                }
            }
        }

        source.clear();
        source.addAll(clusters);
        if (sourceReadings != null) {
            sourceReadings.clear();
            for (int i = 0; i < clusters.size(); i++) sourceReadings.put(clusters.get(i), clusterReadings.get(i));
        }
        for (int i = 0; i < clusters.size(); i++) {
            String consensus = buildConsensusOcrText(clusterReadings.get(i));
            if (consensus.length() > 0) {
                clusters.get(i).text = consensus;
                clusters.get(i).rawText = consensus;
            }
        }
        java.util.Collections.sort(source, new java.util.Comparator<SubtitleCue>() {
            public int compare(SubtitleCue a, SubtitleCue b) {
                return a.startMs < b.startMs ? -1 : (a.startMs == b.startMs ? 0 : 1);
            }
        });
    }

    private void addUniqueOcrVariant(ArrayList<String> values, String value) {
        if (values == null || value == null || value.trim().length() == 0) return;
        String norm = normalizeOcrForMatch(value);
        for (String old : values) if (norm.equals(normalizeOcrForMatch(old))) return;
        if (values.size() < 32) values.add(value);
    }

    private String buildConsensusOcrText(List<String> variants) {
        if (variants == null || variants.size() == 0) return "";
        String best = "";
        int bestScore = Integer.MIN_VALUE;
        for (String candidate : variants) {
            if (candidate == null || candidate.trim().length() == 0) continue;
            int score = scoreConsensusCandidate(candidate, variants);
            if (score > bestScore) { bestScore = score; best = candidate; }
        }
        if (best.length() == 0) return "";

        String flat = best.replace("\\N", " ").replace('\n', ' ').replace('\r', ' ')
                .replaceAll("[{}|¦]+", " ").replaceAll("\\s+", " ").trim();
        String[] words = flat.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String original = words[i];
            String norm = normalizeOcrWord(original);
            if (isOcrNoiseWord(norm) || norm.endsWith("j")) {
                String replacement = findConsensusWordReplacement(words, i, variants);
                if (replacement != null && replacement.length() > 0) words[i] = preserveTokenPunctuation(original, replacement);
            }
        }
        StringBuilder joined = new StringBuilder();
        for (String word : words) {
            if (word.length() == 0) continue;
            if (joined.length() > 0) joined.append(' ');
            joined.append(word);
        }
        String result = joined.toString();
        // Common OCR joins/splits in Romanized subtitles; these do not alter timestamps.
        result = result.replaceAll("(?i)\\btohisse\\b", "toh isse")
                .replaceAll("(?i)\\bkartej?hain\\b", "karte hain")
                .replaceAll("(?i)\\bzyadaj\\b", "zyada")
                .replaceAll("(?i)\\bone\\s+sters\\b", "monsters");
        return stripOcrEdgeArtifacts(result);
    }

    private int scoreConsensusCandidate(String text, List<String> variants) {
        int score = subtitleTextQuality(text);
        String[] words = normalizeOcrForMatch(text.replace("\\N", " ")).split(" ");
        for (String word : words) {
            if (word.length() == 0) continue;
            if (isOcrNoiseWord(word)) score -= 18;
            else if (containsVowel(word) && word.length() >= 3) score += 1;
            int support = countVariantWordSupport(word, variants);
            if (support >= 2) score += 5;
            if (word.endsWith("j")) score -= 4;
        }
        return score;
    }

    private int countVariantWordSupport(String word, List<String> variants) {
        int count = 0;
        if (variants == null) return 0;
        for (String variant : variants) {
            String[] tokens = normalizeOcrForMatch(variant.replace("\\N", " ")).split(" ");
            for (String token : tokens) {
                if (token.equals(word) || wordSimilarity(token, word) >= 0.87d) { count++; break; }
            }
        }
        return count;
    }

    private String findConsensusWordReplacement(String[] baseWords, int index, List<String> variants) {
        String baseWord = normalizeOcrWord(baseWords[index]);
        String previous = index > 0 ? normalizeOcrWord(baseWords[index - 1]) : "";
        String next = index + 1 < baseWords.length ? normalizeOcrWord(baseWords[index + 1]) : "";
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        if (variants == null) return null;
        for (String variant : variants) {
            String flattened = variant.replace("\\N", " ").replace('\n', ' ');
            String[] alt = flattened.split("\\s+");
            for (int j = 0; j < alt.length; j++) {
                String candidate = normalizeOcrWord(alt[j]);
                if (candidate.length() < 3 || candidate.equals(baseWord) || isOcrNoiseWord(candidate)) continue;
                double similarity = wordSimilarity(baseWord, candidate);
                int context = 0;
                if (previous.length() > 0 && j > 0 && wordSimilarity(previous, normalizeOcrWord(alt[j - 1])) >= 0.68d) context += 3;
                if (next.length() > 0 && j + 1 < alt.length && wordSimilarity(next, normalizeOcrWord(alt[j + 1])) >= 0.68d) context += 4;
                boolean suffixJFix = baseWord.endsWith("j") && baseWord.substring(0, baseWord.length() - 1).equals(candidate);
                if (context == 0 && !suffixJFix && !(isOcrNoiseWord(baseWord) && similarity >= 0.40d)) continue;
                if (similarity < 0.42d && !isOcrNoiseWord(baseWord) && !suffixJFix && context < 6) continue;
                int score = context * 4 + ocrWordQuality(candidate) + (int)(similarity * 5.0d)
                        + countVariantWordSupport(candidate, variants) * 2;
                if (suffixJFix) score += 10;
                if (score > bestScore) { bestScore = score; best = candidate; }
            }
        }
        if (best == null || best.equals(baseWord)) return null;
        return best;
    }

    private String preserveTokenPunctuation(String original, String replacement) {
        if (original == null) return replacement;
        int start = 0;
        while (start < original.length() && !Character.isLetterOrDigit(original.charAt(start))) start++;
        int end = original.length();
        while (end > start && !Character.isLetterOrDigit(original.charAt(end - 1))) end--;
        return original.substring(0, start) + replacement + original.substring(end);
    }

    private int ocrWordQuality(String word) {
        if (word == null) return -20;
        String value = normalizeOcrWord(word);
        if (value.length() == 0) return -20;
        int score = Math.min(value.length(), 12);
        if (containsVowel(value)) score += 4;
        else if (value.length() >= 5) score -= 8;
        if (hasRepeatedCharRun(value, 3)) score -= 16;
        if (value.endsWith("j")) score -= 4;
        if (value.length() > 16) score -= 8;
        return score;
    }

    private boolean isOcrNoiseWord(String word) {
        if (word == null || word.length() == 0) return true;
        String value = normalizeOcrWord(word);
        if (value.length() == 0) return true;
        if (value.length() > 18) return true;
        if (hasRepeatedCharRun(value, 3)) return true;
        if (value.length() >= 6 && !containsVowel(value)) return true;
        int letters = 0;
        for (int i = 0; i < value.length(); i++) if (Character.isLetter(value.charAt(i))) letters++;
        return value.length() >= 5 && letters / (double)value.length() < 0.70d;
    }

    private boolean hasRepeatedCharRun(String value, int minimum) {
        if (value == null || value.length() < minimum) return false;
        int run = 1;
        for (int i = 1; i < value.length(); i++) {
            if (value.charAt(i) == value.charAt(i - 1)) {
                run++;
                if (run >= minimum) return true;
            } else run = 1;
        }
        return false;
    }

    private boolean containsVowel(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        for (int i = 0; i < lower.length(); i++) if ("aeiou".indexOf(lower.charAt(i)) >= 0) return true;
        return false;
    }

    private String normalizeOcrWord(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{N}]+", "").trim();
    }

    private String normalizeOcrForMatch(String value) {
        if (value == null) return "";
        String result = normalizeOcr(value.replace("\\N", " "));
        result = result.replaceAll("\\btohisse\\b", "toh isse")
                .replaceAll("\\bkartej?hain\\b", "karte hain")
                .replaceAll("\\bzyadaj\\b", "zyada")
                .replaceAll("\\bone\\s+sters\\b", "monsters");
        return result.replaceAll("\\s+", " ").trim();
    }

    private String stripOcrEdgeArtifacts(String text) {
        return OcrTextCleaner.clean(text);
    }

    private boolean areSimilarOcrTexts(String a, String b) {
        if (a == null || b == null) return false;
        String na = normalizeOcrForMatch(a);
        String nb = normalizeOcrForMatch(b);
        if (na.length() < 5 || nb.length() < 5) return false;
        if (na.equals(nb)) return true;

        String ca = na.replace(" ", "");
        String cb = nb.replace(" ", "");
        double lengthRatio = Math.min(ca.length(), cb.length()) / (double)Math.max(ca.length(), cb.length());
        if (lengthRatio >= 0.68d && characterSimilarity(ca, cb) >= 0.78d) return true;

        String[] left = meaningfulOcrWords(na);
        String[] right = meaningfulOcrWords(nb);
        if (left.length == 0 || right.length == 0) return false;
        int common = 0;
        for (String word : left) {
            for (String other : right) {
                if (word.equals(other) || wordSimilarity(word, other) >= 0.87d) { common++; break; }
            }
        }
        int maxWords = Math.max(left.length, right.length);
        int minWords = Math.min(left.length, right.length);
        double coverageOfLonger = maxWords == 0 ? 0.0d : common / (double)maxWords;
        // Require broad phrase agreement; shared object names alone must not merge two lines.
        return (common >= 3 && coverageOfLonger >= 0.52d)
                || (common >= 2 && minWords <= 3 && coverageOfLonger >= 0.70d);
    }


    private boolean areTemporallySimilarOcrTexts(String a, String b, long overlapMs, long minimumDurationMs) {
        if (overlapMs <= 0L || minimumDurationMs <= 0L) return false;
        double overlapRatio = overlapMs / (double)minimumDurationMs;
        if (overlapRatio < 0.40d) return false;
        String[] left = meaningfulOcrWords(normalizeOcrForMatch(a));
        String[] right = meaningfulOcrWords(normalizeOcrForMatch(b));
        if (left.length == 0 || right.length == 0) return false;
        int common = 0;
        for (String word : left) {
            for (String other : right) {
                if (word.equals(other) || wordSimilarity(word, other) >= 0.86d) {
                    common++;
                    break;
                }
            }
        }
        int denominator = Math.min(left.length, right.length);
        return common >= 3 && denominator > 0 && common / (double)denominator >= 0.60d;
    }

    private String[] meaningfulOcrWords(String text) {
        String stop = " the and you he she we they it is am are was were be do did have has to of or but if what when where why how not yes no this that with for from in on at my your me us can will just so hai hain ho na bhi toh to main mein tum mujhe ye woh ek nahi kya ka ki ke ko se aur ne par par hi sirf ";
        String[] tokens = normalizeOcrForMatch(text).split(" ");
        ArrayList<String> useful = new ArrayList<String>();
        for (String token : tokens) {
            if (token.length() < 3) continue;
            if (stop.contains(" " + token + " ") || stop.startsWith(token + " ") || stop.endsWith(" " + token)) continue;
            if (!useful.contains(token)) useful.add(token);
        }
        return useful.toArray(new String[useful.size()]);
    }

    private double wordSimilarity(String a, String b) {
        String left = normalizeOcrWord(a);
        String right = normalizeOcrWord(b);
        if (left.length() == 0 || right.length() == 0) return 0.0d;
        if (left.equals(right)) return 1.0d;
        if (left.endsWith("j") && left.substring(0, left.length() - 1).equals(right)) return 0.95d;
        if (right.endsWith("j") && right.substring(0, right.length() - 1).equals(left)) return 0.95d;
        return characterSimilarity(left, right);
    }

    private double characterSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0d;
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1.0d;
        if (Math.abs(a.length() - b.length()) > max * 0.50d) return 0.0d;
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(previous[j] + 1,
                        Math.min(current[j - 1] + 1, previous[j - 1] + cost));
            }
            int[] swap = previous; previous = current; current = swap;
        }
        return 1.0d - previous[b.length()] / (double)max;
    }

    private void stabilizeOcrCueTimings(List<SubtitleCue> cues, long durationMs, int intervalMs) {
        if (cues == null || cues.size() == 0) return;
        java.util.Collections.sort(cues, new java.util.Comparator<SubtitleCue>() {
            public int compare(SubtitleCue a, SubtitleCue b) {
                return a.startMs < b.startMs ? -1 : (a.startMs == b.startMs ? 0 : 1);
            }
        });
        int i = 0;
        while (i < cues.size()) {
            SubtitleCue cue = cues.get(i);
            cue.startMs = Math.max(0L, Math.min(durationMs, cue.startMs));
            cue.endMs = Math.max(0L, Math.min(durationMs, cue.endMs));
            if (cue.endMs <= cue.startMs) {
                cues.remove(i);
                continue;
            }
            i++;
        }
        long minCueMs = Math.max(80L, Math.min(180L, intervalMs / 2L));
        i = 0;
        while (i < cues.size() - 1) {
            SubtitleCue previous = cues.get(i);
            SubtitleCue next = cues.get(i + 1);
            if (previous.endMs > next.startMs) {
                long boundary = (previous.endMs + next.startMs) / 2L;
                long minBoundary = previous.startMs + minCueMs;
                long maxBoundary = next.endMs - minCueMs;
                if (minBoundary <= maxBoundary) {
                    if (boundary < minBoundary) boundary = minBoundary;
                    if (boundary > maxBoundary) boundary = maxBoundary;
                    previous.endMs = boundary;
                    next.startMs = boundary;
                } else {
                    // If one cue cannot keep a meaningful duration, keep the cleaner OCR cue.
                    if (scoreConsensusCandidate(previous.text, null) >= scoreConsensusCandidate(next.text, null)) cues.remove(i + 1);
                    else { cues.remove(i); if (i > 0) i--; }
                    continue;
                }
            }
            i++;
        }
    }

    private String normalizeOcr(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.US)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ").trim();
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
                        captureUndoSnapshot("cue edit");
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
                                public void onClick(android.content.DialogInterface d, int w) { captureUndoSnapshot("cue delete"); cues.remove(index); dialog.dismiss(); refreshScreen(); }
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

    // SUBVORYN_OCR_ARTIFACT_CLEANUP_V5
    /** Join adjacent fragments only when the full phrases strongly match; object-name overlap alone is insufficient. */
    private int mergeNearbySimilarOcrCues(List<SubtitleCue> target, long maxGapMs) {
        if (target == null || target.size() < 2) return 0;
        java.util.Collections.sort(target, new java.util.Comparator<SubtitleCue>() {
            public int compare(SubtitleCue a, SubtitleCue b) {
                return a.startMs < b.startMs ? -1 : (a.startMs == b.startMs ? 0 : 1);
            }
        });
        int merged = 0;
        int i = 1;
        while (i < target.size()) {
            SubtitleCue previous = target.get(i - 1);
            SubtitleCue current = target.get(i);
            long gap = current.startMs - previous.endMs;
            if (gap <= maxGapMs) {
                String previousVisible = sanitizeSubtitleArtifacts(SubtitleEngine.displayText(previous));
                String currentVisible = sanitizeSubtitleArtifacts(SubtitleEngine.displayText(current));
                if (areConservativelySameDialogue(previousVisible, currentVisible)) {
                    String previousRaw = cleanCuePreservingFormatting(previous.rawText == null ? previous.text : previous.rawText);
                    String currentRaw = cleanCuePreservingFormatting(current.rawText == null ? current.text : current.rawText);
                    boolean useCurrent = subtitleTextQuality(currentVisible) > subtitleTextQuality(previousVisible);
                    if (useCurrent) {
                        previous.text = currentRaw;
                        previous.rawText = currentRaw;
                    } else {
                        previous.text = previousRaw;
                        previous.rawText = previousRaw;
                    }
                    if (current.startMs < previous.startMs) previous.startMs = current.startMs;
                    if (current.endMs > previous.endMs) previous.endMs = current.endMs;
                    target.remove(i);
                    merged++;
                    continue;
                }
            }
            i++;
        }
        return merged;
    }

    private void captureUndoSnapshot(String label) {
        undoCues = new ArrayList<SubtitleCue>();
        for (SubtitleCue cue : cues) {
            SubtitleCue copy = new SubtitleCue(cue.startMs, cue.endMs, cue.text);
            copy.rawText = cue.rawText; copy.style = cue.style; copy.layer = cue.layer;
            copy.alignment = cue.alignment; copy.marginV = cue.marginV;
            copy.assHeader = cue.assHeader; copy.assName = cue.assName; copy.assEffect = cue.assEffect;
            copy.marginL = cue.marginL; copy.marginR = cue.marginR;
            undoCues.add(copy);
        }
        undoLabel = label == null ? "cleanup" : label;
        if (undoCleanupButton != null) undoCleanupButton.setEnabled(true);
    }

    private void undoLastCleanup() {
        if (undoCues == null) { toast("There is no cleanup change to undo."); return; }
        cues = new ArrayList<SubtitleCue>(undoCues);
        undoCues = null;
        if (undoCleanupButton != null) undoCleanupButton.setEnabled(false);
        refreshScreen();
        toast("Undid " + undoLabel + ".");
        undoLabel = "";
    }

    /** Clean plain dialogue while preserving ASS override blocks and explicit line-break/space tags. */
    private String cleanCuePreservingFormatting(String input) {
        if (input == null) return "";
        if (sourceFormat != SubtitleEngine.FORMAT_ASS) return OcrTextCleaner.clean(input);
        java.util.regex.Pattern protectedPattern = java.util.regex.Pattern.compile("\\{[^}]*\\}|\\\\[Nn]|\\\\h");
        java.util.regex.Matcher matcher = protectedPattern.matcher(input);
        StringBuilder staged = new StringBuilder();
        ArrayList<String> protectedTokens = new ArrayList<String>();
        int cursor = 0;
        while (matcher.find()) {
            staged.append(input.substring(cursor, matcher.start()));
            String marker = "\uE000" + protectedTokens.size() + "\uE001";
            protectedTokens.add(matcher.group());
            staged.append(marker);
            cursor = matcher.end();
        }
        staged.append(input.substring(cursor));
        String cleaned = OcrTextCleaner.clean(staged.toString());
        for (int i = 0; i < protectedTokens.size(); i++) {
            String marker = "\uE000" + i + "\uE001";
            cleaned = cleaned.replace(marker, protectedTokens.get(i));
        }
        return cleaned.trim();
    }

    private boolean areConservativelySameDialogue(String left, String right) {
        String a = OcrTextCleaner.normalizedForMatch(left);
        String b = OcrTextCleaner.normalizedForMatch(right);
        if (a.length() < 3 || b.length() < 3) return false;
        if (a.equals(b)) return true;
        String compactA = a.replace(" ", "");
        String compactB = b.replace(" ", "");
        double ratio = Math.min(compactA.length(), compactB.length()) / (double) Math.max(compactA.length(), compactB.length());
        return ratio >= 0.82d && characterSimilarity(compactA, compactB) >= 0.90d;
    }

    private void cleanupCueTextArtifacts() {
        if (!ensureImported()) return;
        captureUndoSnapshot("text cleanup");
        int changed = 0;
        for (int i = 0; i < cues.size(); i++) {
            SubtitleCue cue = cues.get(i);
            String before = cue.rawText == null ? cue.text : cue.rawText;
            String after = cleanCuePreservingFormatting(before);
            if (after.length() == 0) after = SubtitleEngine.displayText(cue);
            if (!after.equals(before)) {
                cue.rawText = after;
                cue.text = after;
                changed++;
            }
        }
        int similarMerged = mergeNearbySimilarOcrCues(cues, 650L);
        int merged = SubtitleEngine.mergeConsecutiveDuplicates(cues, 350);
        sortCues();
        refreshScreen();
        toast("Cleanup complete: " + changed + " cue(s) cleaned, " + (similarMerged + merged) + " duplicate/fragment cue(s) merged. Review spelling before export.");
    }

    private boolean isObviousOcrNoiseCue(SubtitleCue cue) {
        if (cue == null) return true;
        String visible = SubtitleEngine.displayText(cue).trim();
        if (visible.length() == 0) return true;
        String flat = visible.replace("\\N", " ").replace('\n', ' ').trim();
        String norm = normalizeOcrForMatch(flat);
        if (OcrTextCleaner.isNoiseOnly(flat)) return true;
        if (norm.length() == 0) return !flat.matches("[!?！？]+");
        if (flat.matches("[!?！？]+")) return false;
        int nonSpace = 0, alphaNum = 0, symbolNoise = 0;
        for (int i = 0; i < flat.length(); i++) {
            char ch = flat.charAt(i);
            if (Character.isWhitespace(ch)) continue;
            nonSpace++;
            if (Character.isLetterOrDigit(ch)) alphaNum++;
            else if (".,!?'-’:\\N".indexOf(ch) < 0) symbolNoise++;
        }
        if (nonSpace == 0 || alphaNum / (double) nonSpace < 0.52d) return true;
        if (symbolNoise / (double) Math.max(1, nonSpace) > 0.18d) return true;
        return containsLikelyOcrNoise(sanitizeSubtitleArtifacts(flat)) && alphaNum / (double) nonSpace < 0.72d;
    }

    private void reviewAndRemoveOcrNoise() {
        if (!ensureImported()) return;
        final ArrayList<SubtitleCue> candidates = new ArrayList<SubtitleCue>();
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < cues.size(); i++) {
            SubtitleCue cue = cues.get(i);
            if (isObviousOcrNoiseCue(cue)) {
                candidates.add(cue);
                if (candidates.size() <= 8) {
                    preview.append("#").append(i + 1).append("  ")
                            .append(clip(SubtitleEngine.displayText(cue), 70)).append('\n');
                }
            }
        }
        if (candidates.size() == 0) {
            toast("No clearly garbled OCR cues detected.");
            return;
        }
        if (candidates.size() > 8) preview.append("…and ").append(candidates.size() - 8).append(" more cue(s).\n");
        preview.append("\nThis is a heuristic. A short or stylized real line could be flagged, so check the preview before removing.");
        new AlertDialog.Builder(this)
                .setTitle("Review " + candidates.size() + " possible junk cue(s)")
                .setMessage(preview.toString())
                .setNegativeButton("Keep all", null)
                .setPositiveButton("Remove selected", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        captureUndoSnapshot("OCR junk removal");
                        cues.removeAll(candidates);
                        refreshScreen();
                        toast("Removed " + candidates.size() + " likely junk cue(s).");
                    }
                }).show();
    }

    private void mergeDuplicates() {
        if (!ensureImported()) return;
        int duplicateCount = SubtitleEngine.countDuplicates(cues, 350);
        if (duplicateCount > 0) captureUndoSnapshot("duplicate merge");
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
