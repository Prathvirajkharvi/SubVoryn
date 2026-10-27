package com.subvoryn.voryn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback;
import com.arthenica.ffmpegkit.LogCallback;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.Statistics;
import com.arthenica.ffmpegkit.StatisticsCallback;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** SUBVORYN_ENCODING_V1: SAF-backed hardsub encoding with a bundled, runtime-verified FFmpeg engine. */
public class EncodingActivity extends Activity {
    public static final String PREFS = "subvoryn_encoding";
    public static final String KEY_VIDEO_URI = "video_uri";
    public static final String KEY_SUBTITLE_URI = "subtitle_uri";
    public static final String KEY_FONTS_URI = "font_tree_uri";
    public static final String KEY_OUTPUT_URI = "output_tree_uri";
    public static final String KEY_RESOLUTION = "default_resolution";
    public static final String KEY_FPS = "default_fps";
    public static final String KEY_CUSTOM_FPS = "default_custom_fps";
    public static final String KEY_CRF = "default_crf";
    public static final String KEY_PRESET = "default_preset";
    public static final String KEY_AUDIO = "default_audio";
    public static final String KEY_SHOW_ADVANCED = "show_advanced";

    private static final int REQ_VIDEO = 5101;
    private static final int REQ_SUBTITLE = 5102;
    private static final int REQ_FONTS = 5103;
    private static final int REQ_OUTPUT_FOLDER = 5104;
    private static final int C_BG = Color.rgb(14, 19, 26);
    private static final int C_PANEL = Color.rgb(23, 30, 40);
    private static final int C_PANEL_2 = Color.rgb(31, 40, 52);
    private static final int C_BORDER = Color.rgb(57, 72, 91);
    private static final int C_TEXT = Color.rgb(243, 246, 250);
    private static final int C_MUTED = Color.rgb(161, 176, 193);
    private static final int C_ACCENT = Color.rgb(151, 178, 207);
    private static final int C_AMBER = Color.rgb(203, 166, 113);
    private static final String ENGINE_ARTIFACT_VERSION = "8.1.9";
    private static final String RELEASES_URL = "https://github.com/ffmpegkit-maintained/ffmpeg/releases";
    private static final String[] RESOLUTIONS = {"Original Resolution", "480p", "720p", "1080p", "1440p"};
    private static final String[] FPS_OPTIONS = {"Original FPS", "23.976 FPS", "24 FPS", "25 FPS", "29.97 FPS", "30 FPS", "50 FPS", "60 FPS", "Custom FPS"};
    private static final String[] PRESETS = {"Ultrafast", "Superfast", "Veryfast", "Faster", "Fast", "Medium", "Slow", "Slower", "Veryslow"};
    private static final String[] AUDIO_OPTIONS = {"Copy original audio", "AAC 96 kbps", "AAC 128 kbps", "AAC 160 kbps", "AAC 192 kbps", "AAC 256 kbps", "AAC 320 kbps"};

    private android.content.SharedPreferences prefs;
    private Uri videoUri, subtitleUri, fontsTreeUri, outputTreeUri, lastOutputUri;
    private String videoName = "", subtitleName = "";
    private long videoSize = -1L, subtitleSize = -1L, durationMs = 0L;
    private int fontCount = 0;
    private boolean filenameEdited = false;
    private boolean programmaticFilenameChange = false;
    private boolean encoding = false;
    private boolean cancelRequested = false;
    private long encodingStartWallMs = 0L;
    private FFmpegSession activeSession;
    private File activeTempOutput;
    private File workDir;
    private String selectedEncoder = "not checked";
    private String lastEncoderLog = "";

    private LinearLayout shell;
    private TextView videoLabel, subtitleLabel, fontLabel, outputFolderLabel;
    private TextView summaryLabel, commandPreview, progressLabel, progressDetails, outputLabel, errorLogLabel;
    private EditText filenameInput, customFpsInput;
    private Spinner resolutionSpinner, fpsSpinner, presetSpinner, audioSpinner;
    private SeekBar crfSeek;
    private TextView crfLabel;
    private ProgressBar encodeProgress;
    private LinearLayout progressCard;
    private Button startButton, cancelButton, openOutputButton, shareOutputButton;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(C_BG);
            getWindow().setNavigationBarColor(C_BG);
        }
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildScreen();
        if (prefs.getBoolean(KEY_SHOW_ADVANCED, false)) commandPreview.setVisibility(View.VISIBLE);
        restoreSelections();
        updateSelectionLabels();
        refreshSummaryAndPreview();
        checkBundledEngine(false);
    }

    private void buildScreen() {
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(C_BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(20));
        content.setBackgroundColor(C_BG);
        scroll.addView(content, new android.widget.ScrollView.LayoutParams(-1, -2));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("SV", 18, C_TEXT, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackgroundDrawable(round(C_PANEL_2, C_BORDER, 13));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.setPadding(dp(11), 0, 0, 0);
        titleStack.addView(text("Encoding", 23, C_TEXT, true));
        titleStack.addView(text("HARDSUB VIDEO WORKSPACE", 10, C_ACCENT, true));
        brand.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(brand, margin(-1, -2, 0, 14));
        content.addView(text("Burn subtitles into video", 24, C_TEXT, true), margin(-1, -2, 0, 4));
        content.addView(text("Select media, tune quality, inspect the configuration, then encode.", 13, C_MUTED, false), margin(-1, -2, 0, 16));

        LinearLayout inputCard = card();
        inputCard.addView(sectionTitle("01  INPUT MEDIA"), margin(-1, -2, 0, 8));
        videoLabel = text("No video selected", 13, C_MUTED, false);
        inputCard.addView(videoLabel, margin(-1, -2, 0, 5));
        Button videoButton = button("Import Video", true);
        videoButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickDocument(REQ_VIDEO); } });
        inputCard.addView(videoButton, fullWidth());
        subtitleLabel = text("No subtitle file selected", 13, C_MUTED, false);
        inputCard.addView(subtitleLabel, margin(-1, 11, 0, 5));
        Button subtitleButton = button("Import Subtitle File", true);
        subtitleButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickDocument(REQ_SUBTITLE); } });
        inputCard.addView(subtitleButton, fullWidth());
        content.addView(inputCard, margin(-1, -2, 0, 11));

        LinearLayout fontCard = card();
        fontCard.addView(sectionTitle("02  OPTIONAL ASS/SSA FONTS"), margin(-1, -2, 0, 7));
        fontLabel = text("No custom font folder selected. System fonts will be used.", 12, C_MUTED, false);
        fontCard.addView(fontLabel, margin(-1, -2, 0, 8));
        Button fontsButton = button("Select Font Folder — Optional", false);
        fontsButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickTree(REQ_FONTS); } });
        fontCard.addView(fontsButton, fullWidth());
        content.addView(fontCard, margin(-1, -2, 0, 11));

        LinearLayout outputCard = card();
        outputCard.addView(sectionTitle("03  OUTPUT DESTINATION"), margin(-1, -2, 0, 7));
        outputFolderLabel = text("Download/SubVoryn/Encoded/", 12, C_ACCENT, false);
        outputCard.addView(outputFolderLabel, margin(-1, -2, 0, 8));
        Button outputFolderButton = button("Choose Output Folder", false);
        outputFolderButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickTree(REQ_OUTPUT_FOLDER); } });
        outputCard.addView(outputFolderButton, fullWidth());
        outputCard.addView(text("OUTPUT FILE NAME", 10, C_ACCENT, true), margin(-1, 12, 0, 5));
        filenameInput = input("[SubVoryn]-video-HSub-Original.mp4", InputType.TYPE_CLASS_TEXT);
        filenameInput.setSingleLine(true);
        filenameInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!programmaticFilenameChange) filenameEdited = true;
                refreshSummaryAndPreview();
            }
            public void afterTextChanged(Editable e) { }
        });
        outputCard.addView(filenameInput, fullWidth());
        content.addView(outputCard, margin(-1, -2, 0, 11));

        LinearLayout qualityCard = card();
        qualityCard.addView(sectionTitle("04  VIDEO QUALITY"), margin(-1, -2, 0, 8));
        qualityCard.addView(text("RESOLUTION / SCALE", 10, C_ACCENT, true), margin(-1, -2, 0, 4));
        resolutionSpinner = spinner(RESOLUTIONS);
        qualityCard.addView(resolutionSpinner, margin(-1, dp(46), 0, 10));
        qualityCard.addView(text("FRAME RATE", 10, C_ACCENT, true), margin(-1, -2, 0, 4));
        fpsSpinner = spinner(FPS_OPTIONS);
        qualityCard.addView(fpsSpinner, margin(-1, dp(46), 0, 4));
        customFpsInput = input("30", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        customFpsInput.setSingleLine(true);
        customFpsInput.setVisibility(View.GONE);
        qualityCard.addView(customFpsInput, margin(-1, dp(46), 0, 9));
        qualityCard.addView(text("ENCODER STRATEGY", 10, C_ACCENT, true), margin(-1, -2, 0, 4));
        qualityCard.addView(text("H.264 auto-detection: prefer a supported software encoder; fall back only to a detected H.264 MediaCodec encoder.", 12, C_MUTED, false), margin(-1, -2, 0, 10));
        qualityCard.addView(text("QUALITY TARGET (CRF 16–35)", 10, C_ACCENT, true), margin(-1, -2, 0, 4));
        crfSeek = new SeekBar(this);
        crfSeek.setMax(19);
        crfSeek.setProgress(prefs.getInt(KEY_CRF, 24) - 16);
        qualityCard.addView(crfSeek, margin(-1, dp(34), 0, 1));
        crfLabel = text("CRF 24 — balanced quality / size", 12, C_TEXT, true);
        qualityCard.addView(crfLabel, margin(-1, -2, 0, 11));
        qualityCard.addView(text("Lower CRF means higher quality and larger files. If the installed LGPL engine has no CRF-capable H.264 encoder, the same slider becomes a documented bitrate target instead of pretending CRF is supported.", 11, C_MUTED, false), margin(-1, -2, 0, 11));
        qualityCard.addView(text("ENCODING PRESET", 10, C_ACCENT, true), margin(-1, -2, 0, 4));
        presetSpinner = spinner(PRESETS);
        qualityCard.addView(presetSpinner, margin(-1, dp(46), 0, 9));
        qualityCard.addView(text("Faster presets usually encode sooner but may make larger files at a similar quality target. The preset is honored only if libx264 is present; MediaCodec/openh264 use their supported bitrate controls. Device, codec and source complexity affect the result.", 11, C_MUTED, false), margin(-1, -2, 0, 10));
        qualityCard.addView(text("AUDIO", 10, C_ACCENT, true), margin(-1, -2, 0, 4));
        audioSpinner = spinner(AUDIO_OPTIONS);
        qualityCard.addView(audioSpinner, margin(-1, dp(46), 0, 6));
        qualityCard.addView(text("Copy preserves the source audio stream when MP4 supports it. AAC mode re-encodes the audio; videos without audio are allowed.", 11, C_MUTED, false));
        content.addView(qualityCard, margin(-1, -2, 0, 11));

        LinearLayout summaryCard = card();
        summaryCard.addView(sectionTitle("05  CONFIGURATION SUMMARY"), margin(-1, -2, 0, 7));
        summaryLabel = text("Select both a video and subtitle file to prepare an encode.", 12, C_TEXT, false);
        summaryLabel.setGravity(Gravity.TOP);
        summaryCard.addView(summaryLabel, margin(-1, -2, 0, 8));
        Button commandButton = button("Preview Command / Configuration", false);
        commandButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                commandPreview.setVisibility(commandPreview.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        summaryCard.addView(commandButton, fullWidth());
        commandPreview = text("", 11, C_MUTED, false);
        commandPreview.setGravity(Gravity.TOP);
        commandPreview.setTextIsSelectable(true);
        commandPreview.setPadding(dp(10), dp(10), dp(10), dp(10));
        commandPreview.setBackgroundDrawable(round(C_PANEL_2, C_BORDER, 10));
        commandPreview.setVisibility(View.GONE);
        summaryCard.addView(commandPreview, margin(-1, -2, 0, 7));
        Button copyCommandButton = button("Copy Preview", false);
        copyCommandButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("SubVoryn FFmpeg configuration", buildHumanReadableCommand()));
                    toast("Readable configuration copied. It uses selected filenames as examples; the app resolves SAF paths internally.");
                }
            }
        });
        summaryCard.addView(copyCommandButton, margin(-1, 8, 0, 0));
        content.addView(summaryCard, margin(-1, -2, 0, 11));

        progressCard = card();
        progressCard.setVisibility(View.GONE);
        progressCard.addView(sectionTitle("ENCODING PROGRESS"), margin(-1, -2, 0, 8));
        progressLabel = text("Preparing engine and input files…", 13, C_TEXT, true);
        progressCard.addView(progressLabel, margin(-1, -2, 0, 6));
        encodeProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        encodeProgress.setMax(100);
        progressCard.addView(encodeProgress, margin(-1, dp(9), 0, 8));
        progressDetails = text("Percentage, processing speed and ETA will appear only when FFmpeg reports usable statistics.", 11, C_MUTED, false);
        progressDetails.setGravity(Gravity.TOP);
        progressCard.addView(progressDetails, margin(-1, -2, 0, 8));
        errorLogLabel = text("", 10, C_AMBER, false);
        errorLogLabel.setGravity(Gravity.TOP);
        errorLogLabel.setTextIsSelectable(true);
        progressCard.addView(errorLogLabel, margin(-1, -2, 0, 8));
        outputLabel = text("", 12, C_TEXT, false);
        progressCard.addView(outputLabel, margin(-1, -2, 0, 8));
        LinearLayout progressButtons = new LinearLayout(this);
        cancelButton = button("Cancel Encoding", false);
        cancelButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmCancel(); } });
        progressButtons.addView(cancelButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        startButton = button("Start Encoding", true);
        startButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startEncoding(); } });
        progressButtons.addView(startButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        progressCard.addView(progressButtons, fullWidth());
        LinearLayout outputButtons = new LinearLayout(this);
        openOutputButton = button("Open Output", false);
        openOutputButton.setEnabled(false);
        openOutputButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openOutput(); } });
        outputButtons.addView(openOutputButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        shareOutputButton = button("Share Output", false);
        shareOutputButton.setEnabled(false);
        shareOutputButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { shareOutput(); } });
        outputButtons.addView(shareOutputButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        progressCard.addView(outputButtons, margin(-1, 8, 0, 0));
        content.addView(progressCard, margin(-1, -2, 0, 11));

        content.addView(text("ASS / SSA styles are left intact. Selected subtitles are rendered once into the video, not muxed as a soft subtitle track.", 11, C_MUTED, false), margin(-1, -2, 0, 10));
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(C_BG);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        shell.addView(buildBottomNavigation(1), new LinearLayout.LayoutParams(-1, dp(60)));
        setContentView(shell);

        resolutionSpinner.setSelection(clamp(prefs.getInt(KEY_RESOLUTION, 0), 0, RESOLUTIONS.length - 1));
        fpsSpinner.setSelection(clamp(prefs.getInt(KEY_FPS, 0), 0, FPS_OPTIONS.length - 1));
        customFpsInput.setText(prefs.getString(KEY_CUSTOM_FPS, "30"));
        presetSpinner.setSelection(clamp(prefs.getInt(KEY_PRESET, 2), 0, PRESETS.length - 1));
        audioSpinner.setSelection(clamp(prefs.getInt(KEY_AUDIO, 2), 0, AUDIO_OPTIONS.length - 1));
        customFpsInput.setVisibility(fpsSpinner.getSelectedItemPosition() == 8 ? View.VISIBLE : View.GONE);
        customFpsInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                saveCurrentDefaults();
                refreshSummaryAndPreview();
            }
            public void afterTextChanged(Editable e) { }
        });
        crfLabel.setText(crfLabelText(crfSeek.getProgress() + 16));
        crfSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                crfLabel.setText(crfLabelText(progress + 16));
                refreshSummaryAndPreview();
            }
            public void onStartTrackingTouch(SeekBar seekBar) { }
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        android.widget.AdapterView.OnItemSelectedListener optionChanged = new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (parent == fpsSpinner) customFpsInput.setVisibility(position == 8 ? View.VISIBLE : View.GONE);
                saveCurrentDefaults();
                refreshProposedFilename();
                refreshSummaryAndPreview();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
        resolutionSpinner.setOnItemSelectedListener(optionChanged);
        fpsSpinner.setOnItemSelectedListener(optionChanged);
        presetSpinner.setOnItemSelectedListener(optionChanged);
        audioSpinner.setOnItemSelectedListener(optionChanged);
    }

    private void restoreSelections() {
        String video = prefs.getString(KEY_VIDEO_URI, null);
        String sub = prefs.getString(KEY_SUBTITLE_URI, null);
        String font = prefs.getString(KEY_FONTS_URI, null);
        String output = prefs.getString(KEY_OUTPUT_URI, null);
        try { if (video != null) videoUri = Uri.parse(video); } catch (Exception ignored) { }
        try { if (sub != null) subtitleUri = Uri.parse(sub); } catch (Exception ignored) { }
        try { if (font != null) fontsTreeUri = Uri.parse(font); } catch (Exception ignored) { }
        try { if (output != null) outputTreeUri = Uri.parse(output); } catch (Exception ignored) { }
        if (videoUri != null) {
            videoName = queryName(videoUri);
            videoSize = querySize(videoUri);
        }
        if (subtitleUri != null) {
            subtitleName = queryName(subtitleUri);
            subtitleSize = querySize(subtitleUri);
        }
        if (fontsTreeUri != null) {
            fontLabel.setText("Selected font folder · counting supported .ttf/.otf files…");
            countFontsInBackground(fontsTreeUri);
        }
        if (outputTreeUri != null) outputFolderLabel.setText("Custom folder · " + treeDisplayName(outputTreeUri));
        else outputFolderLabel.setText("Download/SubVoryn/Encoded/");
        if (filenameInput.getText().toString().trim().length() == 0) refreshProposedFilename();
    }

    private void updateSelectionLabels() {
        videoLabel.setText(videoUri == null ? "No video selected" : videoName + "  ·  " + formatBytes(videoSize));
        subtitleLabel.setText(subtitleUri == null ? "No subtitle file selected" : subtitleName + "  ·  " + formatBytes(subtitleSize));
        if (fontsTreeUri == null) fontLabel.setText("No custom font folder selected. System fonts will be used.");
        if (outputTreeUri == null) outputFolderLabel.setText("Download/SubVoryn/Encoded/");
        refreshProposedFilename();
    }

    private void pickDocument(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (requestCode == REQ_VIDEO) {
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_TITLE, "Select a video");
        } else {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/x-subrip", "text/vtt", "text/x-ssa", "application/octet-stream", "text/plain"});
        }
        try { startActivityForResult(Intent.createChooser(intent, requestCode == REQ_VIDEO ? "Choose video" : "Choose subtitle file"), requestCode); }
        catch (Exception e) { toast("File picker could not be opened: " + safeMessage(e)); }
    }

    private void pickTree(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try { startActivityForResult(intent, requestCode); }
        catch (Exception e) { toast("Folder picker could not be opened: " + safeMessage(e)); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) { }
        if (requestCode == REQ_VIDEO) {
            videoUri = uri;
            videoName = queryName(uri);
            videoSize = querySize(uri);
            prefs.edit().putString(KEY_VIDEO_URI, uri.toString()).apply();
            filenameEdited = false;
            updateSelectionLabels();
            toast("Video selected.");
        } else if (requestCode == REQ_SUBTITLE) {
            String name = queryName(uri);
            if (!isSupportedSubtitle(name)) {
                toast("Choose an .srt, .vtt, .ass or .ssa file.");
                return;
            }
            subtitleUri = uri;
            subtitleName = name;
            subtitleSize = querySize(uri);
            prefs.edit().putString(KEY_SUBTITLE_URI, uri.toString()).apply();
            updateSelectionLabels();
            toast("Subtitle file selected.");
        } else if (requestCode == REQ_FONTS) {
            fontsTreeUri = uri;
            fontCount = 0;
            prefs.edit().putString(KEY_FONTS_URI, uri.toString()).apply();
            fontLabel.setText("Folder selected · scanning .ttf/.otf files…");
            countFontsInBackground(uri);
            refreshSummaryAndPreview();
        } else if (requestCode == REQ_OUTPUT_FOLDER) {
            outputTreeUri = uri;
            prefs.edit().putString(KEY_OUTPUT_URI, uri.toString()).apply();
            outputFolderLabel.setText("Custom folder · " + treeDisplayName(uri));
            refreshSummaryAndPreview();
        }
    }

    private void countFontsInBackground(final Uri treeUri) {
        new Thread(new Runnable() {
            public void run() {
                final int count = countFonts(treeUri);
                runOnUiThread(new Runnable() {
                    public void run() {
                        fontCount = count;
                        String name = treeDisplayName(treeUri);
                        fontLabel.setText(name + " · " + count + " supported font file(s) (.ttf/.otf)" +
                                (count == 0 ? " — warning: no custom fonts found; system fallback will be used" : ""));
                        refreshSummaryAndPreview();
                    }
                });
            }
        }, "SubVoryn-FontScan").start();
    }

    private int countFonts(Uri treeUri) {
        try { return countFontsRecursive(treeUri, DocumentsContract.getTreeDocumentId(treeUri), 0, new int[] {0}); }
        catch (Exception e) { return 0; }
    }

    private int countFontsRecursive(Uri treeUri, String docId, int depth, int[] visited) {
        if (depth > 8 || visited[0] > 1200) return 0;
        int count = 0;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
        Cursor c = null;
        try {
            c = getContentResolver().query(children, new String[] {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            if (c == null) return 0;
            while (c.moveToNext() && visited[0]++ < 1200) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) count += countFontsRecursive(treeUri, id, depth + 1, visited);
                else if (isFontName(name)) count++;
            }
        } catch (Exception ignored) {
        } finally { if (c != null) c.close(); }
        return count;
    }

    private void startEncoding() {
        if (encoding) { toast("Encoding is already running."); return; }
        if (videoUri == null) { toast("Import a video first."); return; }
        if (subtitleUri == null) { toast("Import a subtitle file first."); return; }
        if (!isSupportedSubtitle(subtitleName)) { toast("Unsupported subtitle format."); return; }
        if (filenameInput == null || sanitizeOutputName(filenameInput.getText().toString()).length() == 0) { toast("Enter a valid output filename."); return; }
        if (Build.VERSION.SDK_INT < 29 && outputTreeUri == null) { toast("On this Android version, select an output folder with the folder picker first."); return; }
        if (fpsSpinner.getSelectedItemPosition() == 8) {
            double custom;
            try { custom = Double.parseDouble(customFpsInput.getText().toString().trim()); }
            catch (Exception e) { toast("Enter a valid custom FPS (1–120)."); return; }
            if (Double.isNaN(custom) || Double.isInfinite(custom) || custom < 1.0d || custom > 120.0d) {
                toast("Custom FPS must be between 1 and 120."); return;
            }
        }
        if (crfSeek.getProgress() < 0 || crfSeek.getProgress() > 19) { toast("Invalid quality target."); return; }
        saveCurrentDefaults();
        final Uri selectedVideo = videoUri;
        final Uri selectedSubtitle = subtitleUri;
        final Uri selectedFonts = fontsTreeUri;
        final Uri selectedOutputFolder = outputTreeUri;
        final String outName = ensureMp4(sanitizeOutputName(filenameInput.getText().toString()));
        final int resolution = resolutionSpinner.getSelectedItemPosition();
        final int fps = fpsSpinner.getSelectedItemPosition();
        final String customFps = customFpsInput.getText().toString().trim();
        final int quality = crfSeek.getProgress() + 16;
        final int preset = presetSpinner.getSelectedItemPosition();
        final int audio = audioSpinner.getSelectedItemPosition();

        encoding = true;
        cancelRequested = false;
        activeSession = null;
        activeTempOutput = null;
        lastOutputUri = null;
        outputLabel.setText("Output will appear here after successful finalization.");
        openOutputButton.setEnabled(false);
        shareOutputButton.setEnabled(false);
        progressCard.setVisibility(View.VISIBLE);
        encodeProgress.setIndeterminate(true);
        encodeProgress.setProgress(0);
        progressLabel.setText("Preparing input files and verifying the bundled FFmpeg engine…");
        progressDetails.setText("No progress percentage is shown until FFmpeg reports processed media timestamps.");
        errorLogLabel.setText("");
        startButton.setEnabled(false);
        cancelButton.setEnabled(true);
        new Thread(new Runnable() {
            public void run() {
                try {
                    runEncodingWorker(selectedVideo, selectedSubtitle, selectedFonts, selectedOutputFolder,
                            outName, resolution, fps, customFps, quality, preset, audio);
                } catch (final Throwable e) {
                    final String error = safeMessage(e);
                    deleteQuietly(activeTempOutput);
                    deleteTree(workDir);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (cancelRequested) finishCancelled();
                            else finishWithError("Preparation failed: " + error);
                        }
                    });
                }
            }
        }, "SubVoryn-EncodePrepare").start();
    }

    private void runEncodingWorker(Uri selectedVideo, Uri selectedSubtitle, Uri selectedFonts,
            Uri selectedOutputFolder, String outputName, int resolution, int fpsIndex,
            String customFps, int quality, int presetIndex, int audioIndex) throws Exception {
        File parent = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (parent == null) parent = getCacheDir();
        File workRoot = new File(parent, "SubVorynWork");
        if (!workRoot.exists() && !workRoot.mkdirs()) throw new IOException("Cannot create the app's private encoding workspace.");
        cleanOldWorkFiles(workRoot);
        workDir = new File(workRoot, "session_" + System.currentTimeMillis());
        if (!workDir.mkdirs()) throw new IOException("Cannot create a new private encoding session directory.");
        if (cancelRequested) throw new IOException("Cancelled during preparation.");

        long srcSize = querySize(selectedVideo);
        StatFs fs = new StatFs(workDir.getAbsolutePath());
        long available = fs.getAvailableBytes();
        long estimate = srcSize > 0L ? srcSize * 2L + (100L * 1024L * 1024L) : 300L * 1024L * 1024L;
        if (available < estimate) throw new IOException("Insufficient free space for safe input/output workspace. Available " + formatBytes(available) + "; estimated need " + formatBytes(estimate) + ". Free space or choose a smaller source.");

        postProgress("Verifying packaged FFmpeg version and actual encoder/filter capabilities…", "Checking runtime commands; UI success is not assumed from the Gradle dependency alone.", true);
        FFmpegSession versionSession = FFmpegKit.executeWithArguments(new String[] {"-hide_banner", "-version"});
        if (versionSession == null || !ReturnCode.isSuccess(versionSession.getReturnCode())) {
            throw new IOException("The bundled FFmpeg runtime did not pass its -version check. Install a valid SubVoryn build; no external Termux engine is assumed.");
        }
        FFmpegSession encoderSession = FFmpegKit.executeWithArguments(new String[] {"-hide_banner", "-encoders"});
        FFmpegSession filterSession = FFmpegKit.executeWithArguments(new String[] {"-hide_banner", "-filters"});
        if (encoderSession == null || !ReturnCode.isSuccess(encoderSession.getReturnCode())) throw new IOException("FFmpeg could not enumerate its encoders.");
        if (filterSession == null || !ReturnCode.isSuccess(filterSession.getReturnCode())) throw new IOException("FFmpeg could not enumerate its filters.");
        String encoderList = safeOutput(encoderSession).toLowerCase(Locale.ROOT);
        String filterList = safeOutput(filterSession).toLowerCase(Locale.ROOT);
        if (!filterList.contains("subtitles")) throw new IOException("The selected FFmpeg build does not include the 'subtitles' filter/libass. No silent no-subtitle output was created.");
        selectedEncoder = chooseUsableH264Encoder(encoderList, workDir);
        if (selectedEncoder.length() == 0) throw new IOException("No H.264 encoder passed a one-frame runtime test. Listed encoders were: " + clip(encoderList, 1000) + ". This device/engine combination cannot encode H.264 with the available build; no output was created.");
        if (cancelRequested) throw new IOException("Cancelled during engine capability checks.");

        postProgress("Copying selected video into the app workspace…", "SAF content URIs are streamed to disk; the complete video is never loaded into memory.", true);
        File input = new File(workDir, "input." + safeVideoExtension(videoName));
        copyUriToFile(selectedVideo, input);
        if (cancelRequested) throw new IOException("Cancelled during preparation.");
        postProgress("Copying subtitle file…", "Selected subtitles will be burned once into the video.", true);
        File subtitle = new File(workDir, "subtitles." + extensionOf(subtitleName));
        copyUriToFile(selectedSubtitle, subtitle);
        if (cancelRequested) throw new IOException("Cancelled during preparation.");
        if (!input.isFile() || input.length() == 0L) throw new IOException("The copied input video is empty or inaccessible.");
        if (!subtitle.isFile() || subtitle.length() == 0L) throw new IOException("The copied subtitle file is empty or inaccessible.");

        File fontsDir = new File(workDir, "fonts");
        int copiedFonts = selectedFonts == null ? 0 : copyFonts(selectedFonts, fontsDir);
        if (copiedFonts > 0) {
            FFmpegKitConfigCompat.setFontDirectories(this, fontsDir.getAbsolutePath());
        } else {
            deleteTree(fontsDir);
            FFmpegKitConfigCompat.setFontDirectories(this, null);
            postProgress("No custom fonts found; using the engine/system font fallback.", "For ASS/SSA, verify the output when the source uses a non-system font.", true);
        }

        String human = "Burning " + subtitleName + " into the selected video.\n" +
                "Encoder: " + selectedEncoder + " · resolution: " + RESOLUTIONS[resolution] +
                " · FPS: " + (fpsValueFor(fpsIndex, customFps).length() == 0 ? "Original" : fpsValueFor(fpsIndex, customFps)) +
                " · preset: " + PRESETS[clamp(presetIndex, 0, PRESETS.length - 1)] +
                " · quality target: " + quality + " · audio: " + AUDIO_OPTIONS[audioIndex];
        postProgress("Encoding with verified " + selectedEncoder + "…", human, true);

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(input.getAbsolutePath());
            String d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            durationMs = d == null ? 0L : Long.parseLong(d);
        } catch (Exception ignored) { durationMs = 0L; }
        finally { try { retriever.release(); } catch (Exception ignored) { } }
        if (durationMs <= 0L) throw new IOException("Could not read a valid source duration. This input may be unsupported or damaged.");
        activeTempOutput = new File(workDir, "encoded_" + System.currentTimeMillis() + ".mp4");
        if (activeTempOutput.exists() && !activeTempOutput.delete()) throw new IOException("Cannot create a clean temporary output file.");
        final File outFile = activeTempOutput;
        final long expectedDuration = durationMs;
        final String engineEncoder = selectedEncoder;
        final String filter = makeFilter(subtitle, fontsDir, copiedFonts, resolution);
        final String fpsValue = fpsValueFor(fpsIndex, customFps);
        final long wallStart = System.currentTimeMillis();
        encodingStartWallMs = wallStart;
        final List<String> args = new ArrayList<String>();
        Collections.addAll(args, "-hide_banner", "-y", "-i", input.getAbsolutePath(),
                "-map", "0:v:0", "-map", "0:a?", "-sn", "-vf", filter,
                "-c:v", engineEncoder);
        if ("libx264".equals(engineEncoder)) {
            Collections.addAll(args, "-preset", PRESETS[clamp(presetIndex, 0, PRESETS.length - 1)].toLowerCase(Locale.ROOT), "-crf", String.valueOf(quality));
        } else {
            // The selected LGPL encoder does not implement x264 CRF. Keep the slider meaningful
            // through a bitrate target and explicitly display this in the UI.
            Collections.addAll(args, "-b:v", bitrateForQuality(quality, resolution));
        }
        Collections.addAll(args, "-pix_fmt", "yuv420p");
        if (fpsValue.length() > 0) Collections.addAll(args, "-r", fpsValue, "-fps_mode", "cfr");
        else Collections.addAll(args, "-fps_mode", "passthrough");
        if (audioIndex == 0) Collections.addAll(args, "-c:a", "copy");
        else {
            if (!encoderList.contains(" aac ") && !encoderList.contains(" aac\n")) throw new IOException("AAC encoder is not available in this engine build.");
            String[] bitrates = {"", "96k", "128k", "160k", "192k", "256k", "320k"};
            Collections.addAll(args, "-c:a", "aac", "-b:a", bitrates[clamp(audioIndex, 1, bitrates.length - 1)]);
        }
        Collections.addAll(args, "-movflags", "+faststart", "-stats_period", "0.5", outFile.getAbsolutePath());

        runOnUiThread(new Runnable() {
            public void run() {
                encodeProgress.setIndeterminate(false);
                encodeProgress.setProgress(0);
                progressLabel.setText("0% · FFmpeg session starting");
                progressDetails.setText("Duration " + formatDuration(expectedDuration) + " · " + engineEncoder + " · " + RESOLUTIONS[resolution]);
                errorLogLabel.setText("");
            }
        });
        activeSession = FFmpegKit.executeWithArgumentsAsync(args.toArray(new String[0]),
                new FFmpegSessionCompleteCallback() {
                    public void apply(final FFmpegSession session) {
                        if (cancelRequested || session == null || ReturnCode.isCancel(session.getReturnCode())) {
                            deleteQuietly(outFile);
                            deleteTree(workDir);
                            runOnUiThread(new Runnable() { public void run() { finishCancelled(); } });
                            return;
                        }
                        if (!ReturnCode.isSuccess(session.getReturnCode()) || !outFile.isFile() || outFile.length() == 0L) {
                            final String detail = session == null ? "No FFmpeg session returned." : tail(safeOutput(session) + "\n" + safeValue(session.getFailStackTrace()), 2400);
                            deleteQuietly(outFile);
                            deleteTree(workDir);
                            runOnUiThread(new Runnable() { public void run() { finishWithError("Encoding failed. FFmpeg did not produce a valid final output.\n" + detail); } });
                            return;
                        }
                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    Uri saved = publishOutput(outFile, outputName, selectedOutputFolder);
                                    final Uri finalUri = saved;
                                    final String finalName = queryName(saved);
                                    final long outputBytes = outFile.length();
                                    deleteQuietly(outFile);
                                    deleteTree(workDir);
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            encoding = false;
                                            lastOutputUri = finalUri;
                                            encodeProgress.setIndeterminate(false);
                                            encodeProgress.setProgress(100);
                                            progressLabel.setText("Encoding complete · 100%");
                                            progressDetails.setText("Elapsed " + formatDuration(System.currentTimeMillis() - wallStart) +
                                                    " · output " + formatBytes(outputBytes) + " · engine " + engineEncoder);
                                            outputLabel.setText("Saved: " + (finalName == null || finalName.length() == 0 ? outputName : finalName) + "\n" + destinationDescription(selectedOutputFolder));
                                            errorLogLabel.setText("");
                                            startButton.setEnabled(true);
                                            cancelButton.setEnabled(false);
                                            openOutputButton.setEnabled(true);
                                            shareOutputButton.setEnabled(true);
                                            toast("Encoding completed and the output was saved.");
                                        }
                                    });
                                } catch (final Exception e) {
                                    runOnUiThread(new Runnable() { public void run() {
                                        finishWithError("Video encoded, but saving to the selected destination failed: " + safeMessage(e) +
                                                "\nThe app's temporary output may still exist under its private workspace; free space or choose another folder and retry the save.");
                                    }});
                                }
                            }
                        }, "SubVoryn-OutputFinalize").start();
                    }
                },
                new LogCallback() {
                    public void apply(final com.arthenica.ffmpegkit.Log log) {
                        if (log == null || log.getMessage() == null) return;
                        final String message = log.getMessage().trim();
                        if (message.length() == 0) return;
                        lastEncoderLog = tail(lastEncoderLog + "\n" + message, 1400);
                        if (looksLikeError(message)) runOnUiThread(new Runnable() {
                            public void run() { errorLogLabel.setText(tail(lastEncoderLog, 1600)); }
                        });
                    }
                },
                new StatisticsCallback() {
                    public void apply(final Statistics statistics) {
                        if (statistics == null) return;
                        final long processed = Math.max(0L, (long) statistics.getTime());
                        final double numericSpeed = statistics.getSpeed();
                        final String speed = numericSpeed > 0.0d ? String.format(Locale.US, "%.2fx", numericSpeed) : "unavailable";
                        final long elapsed = Math.max(0L, System.currentTimeMillis() - wallStart);
                        final int percent = expectedDuration > 0L ? clamp((int) (100L * Math.min(processed, expectedDuration) / expectedDuration), 0, 99) : 0;
                        final String eta = expectedDuration > 0L && numericSpeed > 0.0d && processed < expectedDuration
                                ? formatDuration((long) ((expectedDuration - processed) / numericSpeed)) : "calculating";
                        final long outputBytes = outFile.isFile() ? outFile.length() : 0L;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                if (!encoding || cancelRequested) return;
                                encodeProgress.setIndeterminate(false);
                                encodeProgress.setProgress(percent);
                                progressLabel.setText(percent + "% · " + formatDuration(processed) + " / " + formatDuration(expectedDuration));
                                progressDetails.setText("Speed " + (numericSpeed > 0.0d ? speed : "unavailable") +
                                        " · elapsed " + formatDuration(elapsed) + " · ETA " + eta +
                                        "\nResolution " + RESOLUTIONS[resolution] + " · FPS " + (fpsValue.length() == 0 ? "Original" : fpsValue) +
                                        " · Encoder " + engineEncoder + " · quality " + qualityLabelFor(engineEncoder, quality) +
                                        " · Audio " + AUDIO_OPTIONS[audioIndex] + " · output currently " + formatBytes(outputBytes));
                            }
                        });
                    }
                });
    }

    private String makeFilter(File subtitle, File fontsDir, int copiedFonts, int resolution) throws IOException {
        if (subtitle == null || !subtitle.isFile()) throw new IOException("Subtitle file was not staged correctly.");
        String ext = extensionOf(subtitle.getName());
        boolean ass = "ass".equals(ext) || "ssa".equals(ext);
        String subPath = escapeFilterPath(subtitle.getAbsolutePath());
        String filter = "subtitles=filename='" + subPath + "'";
        if (ass && copiedFonts > 0 && fontsDir.isDirectory()) filter += ":fontsdir='" + escapeFilterPath(fontsDir.getAbsolutePath()) + "'";
        int height = resolutionHeight(resolution);
        if (height > 0) {
            int width = (int) Math.round(height * 16.0d / 9.0d);
            filter += ",scale=w='min(iw," + width + ")':h='min(ih," + height + ")':force_original_aspect_ratio=decrease:force_divisible_by=2";
        }
        return filter;
    }

    private void confirmCancel() {
        if (!encoding) return;
        new AlertDialog.Builder(this).setTitle("Cancel encoding?")
                .setMessage("The current encode will stop and the incomplete temporary output will be deleted. Selected source files are not modified.")
                .setNegativeButton("Continue encoding", null)
                .setPositiveButton("Cancel encoding", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        cancelRequested = true;
                        progressLabel.setText("Cancellation requested…");
                        cancelButton.setEnabled(false);
                        FFmpegSession session = activeSession;
                        if (session != null) FFmpegKit.cancel(session.getSessionId());
                    }
                }).show();
    }

    private void finishCancelled() {
        encoding = false;
        cancelRequested = false;
        encodeProgress.setIndeterminate(false);
        progressLabel.setText("Encoding cancelled");
        progressDetails.setText("The incomplete temporary output was removed. Source files were not changed.");
        startButton.setEnabled(true);
        cancelButton.setEnabled(false);
        if (activeTempOutput != null) deleteQuietly(activeTempOutput);
        toast("Encoding cancelled.");
    }

    private void finishWithError(String message) {
        encoding = false;
        encodeProgress.setIndeterminate(false);
        progressLabel.setText("Not completed");
        progressDetails.setText(message == null ? "Unknown error." : message);
        startButton.setEnabled(true);
        cancelButton.setEnabled(false);
        if (activeTempOutput != null && (cancelRequested || !activeTempOutput.isFile())) deleteQuietly(activeTempOutput);
        toast("SubVoryn: " + clip(message == null ? "Encoding failed." : message, 180));
    }

    private void postProgress(final String title, final String details, final boolean indeterminate) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (!encoding) return;
                progressCard.setVisibility(View.VISIBLE);
                encodeProgress.setIndeterminate(indeterminate);
                progressLabel.setText(title);
                progressDetails.setText(details);
            }
        });
    }

    private Uri publishOutput(File source, String requestedName, Uri selectedFolder) throws Exception {
        if (!source.isFile() || source.length() <= 0L) throw new IOException("Temporary output file is missing or empty.");
        String filename = ensureMp4(sanitizeOutputName(requestedName));
        if (selectedFolder != null) return publishToTree(source, filename, selectedFolder);
        if (Build.VERSION.SDK_INT < 29) throw new IOException("A custom SAF output folder is required below Android 10.");
        final String relative = Environment.DIRECTORY_DOWNLOADS + "/SubVoryn/Encoded/";
        filename = uniqueMediaStoreName(filename, relative);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relative);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Android MediaStore could not create the destination file.");
        try {
            OutputStream out = getContentResolver().openOutputStream(uri, "w");
            if (out == null) throw new IOException("Could not open the destination output stream.");
            try { copyStream(new FileInputStream(source), out); }
            finally { try { out.close(); } catch (Exception ignored) { } }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (getContentResolver().update(uri, done, null, null) <= 0) throw new IOException("Output could not be finalized in MediaStore.");
            return uri;
        } catch (Exception e) {
            try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) { }
            throw e;
        }
    }

    private Uri publishToTree(File source, String filename, Uri tree) throws Exception {
        String docId = DocumentsContract.getTreeDocumentId(tree);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
        filename = uniqueTreeName(tree, docId, filename);
        Uri target = DocumentsContract.createDocument(getContentResolver(), parent, "video/mp4", filename);
        if (target == null) throw new IOException("The selected output folder did not create a file. Check write access.");
        try {
            OutputStream out = getContentResolver().openOutputStream(target, "w");
            if (out == null) throw new IOException("Cannot write to the selected output folder.");
            try { copyStream(new FileInputStream(source), out); }
            finally { try { out.close(); } catch (Exception ignored) { } }
            return target;
        } catch (Exception e) {
            try { DocumentsContract.deleteDocument(getContentResolver(), target); } catch (Exception ignored) { }
            throw e;
        }
    }

    private String uniqueMediaStoreName(String filename, String relative) {
        String base = filename.substring(0, filename.length() - 4);
        String candidate = filename;
        int index = 2;
        while (mediaStoreExists(candidate, relative)) candidate = base + " (" + index++ + ").mp4";
        return candidate;
    }

    private boolean mediaStoreExists(String filename, String relative) {
        Cursor c = null;
        try {
            c = getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[] {MediaStore.MediaColumns._ID},
                    MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                    new String[] {filename, relative}, null);
            return c != null && c.moveToFirst();
        } catch (Exception e) { return false; }
        finally { if (c != null) c.close(); }
    }

    private String uniqueTreeName(Uri tree, String parentId, String filename) {
        String base = filename.substring(0, filename.length() - 4);
        String candidate = filename;
        int suffix = 2;
        while (treeHasName(tree, parentId, candidate)) candidate = base + " (" + suffix++ + ").mp4";
        return candidate;
    }

    private boolean treeHasName(Uri tree, String parentId, String filename) {
        Cursor c = null;
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
            c = getContentResolver().query(children, new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (c != null) while (c.moveToNext()) if (filename.equals(c.getString(0))) return true;
        } catch (Exception ignored) { }
        finally { if (c != null) c.close(); }
        return false;
    }

    private void openOutput() {
        if (lastOutputUri == null) { toast("No saved output is available yet."); return; }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(lastOutputUri, "video/mp4");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open encoded video"));
        } catch (Exception e) { toast("No video player could open this output. Use Share Output instead."); }
    }

    private void shareOutput() {
        if (lastOutputUri == null) { toast("No saved output is available yet."); return; }
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("video/mp4");
            intent.putExtra(Intent.EXTRA_STREAM, lastOutputUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share encoded video"));
        } catch (Exception e) { toast("Could not open the share sheet."); }
    }

    private void checkBundledEngine(final boolean showToast) {
        FFmpegKit.executeWithArgumentsAsync(new String[] {"-hide_banner", "-version"},
                new FFmpegSessionCompleteCallback() {
                    public void apply(FFmpegSession session) {
                        if (showToast) runOnUiThread(new Runnable() {
                            public void run() { toast(session != null && ReturnCode.isSuccess(session.getReturnCode())
                                    ? "Bundled FFmpeg runtime is available." : "FFmpeg runtime check failed."); }
                        });
                    }
                });
    }

    private String buildHumanReadableCommand() {
        String name = filenameInput == null ? "<output>.mp4" : ensureMp4(sanitizeOutputName(filenameInput.getText().toString()));
        String video = videoName.length() == 0 ? "<selected-video>" : videoName;
        String sub = subtitleName.length() == 0 ? "<selected-subtitle>" : subtitleName;
        String filter = "subtitles='" + escapePreviewFilter(sub) + "'";
        String ext = extensionOf(subtitleName);
        if (("ass".equals(ext) || "ssa".equals(ext)) && fontsTreeUri != null && fontCount > 0) filter += ":fontsdir='<selected-font-folder>'";
        int height = resolutionHeight(resolutionSpinner == null ? 0 : resolutionSpinner.getSelectedItemPosition());
        if (height > 0) {
            int width = (int) Math.round(height * 16.0d / 9.0d);
            filter += ",scale=w='min(iw," + width + ")':h='min(ih," + height + ")':force_original_aspect_ratio=decrease:force_divisible_by=2";
        }
        int quality = crfSeek == null ? 24 : crfSeek.getProgress() + 16;
        int audio = audioSpinner == null ? 2 : audioSpinner.getSelectedItemPosition();
        String fps = fpsSpinner == null ? "" : fpsValueFor(fpsSpinner.getSelectedItemPosition(), customFpsInput == null ? "30" : customFpsInput.getText().toString().trim());
        String videoEncoder = selectedEncoder.equals("not checked") ? "<auto-detected-H.264-encoder>" : selectedEncoder;
        String videoQuality = "libx264".equals(selectedEncoder) ? "-preset " + (presetSpinner == null ? "veryfast" : PRESETS[presetSpinner.getSelectedItemPosition()].toLowerCase(Locale.ROOT)) + " -crf " + quality
                : "-b:v <quality-derived-bitrate>";
        String audioCommand;
        if (audio == 0) audioCommand = "-c:a copy";
        else {
            String[] rates = {"", "96k", "128k", "160k", "192k", "256k", "320k"};
            audioCommand = "-c:a aac -b:a " + rates[clamp(audio, 1, rates.length - 1)];
        }
        String frameRate = fps.length() == 0 ? "-fps_mode passthrough" : "-r " + fps + " -fps_mode cfr";
        return "ffmpeg -i \"" + video + "\" -vf \"" + filter + "\" -map 0:v:0 -map '0:a?' -sn -c:v " + videoEncoder + " " + videoQuality +
                " -pix_fmt yuv420p " + frameRate + " " + audioCommand + " -movflags +faststart \"" + name + "\"\n\n" +
                "This is a readable equivalent configuration. The app copies SAF streams to a private working directory and resolves actual paths at runtime; this preview does not expose those private paths and may not be directly runnable as pasted.";
    }

    private String escapePreviewFilter(String value) {
        if (value == null) return "<selected-subtitle>";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace(":", "\\:").replace(",", "\\,");
    }

    private String chooseUsableH264Encoder(String output, File workDirectory) throws IOException {
        String[] candidates = {"libx264", "libopenh264", "h264_mediacodec"};
        StringBuilder failures = new StringBuilder();
        for (String candidate : candidates) {
            if (!output.contains(candidate)) continue;
            if (probeH264Encoder(candidate, workDirectory)) return candidate;
            failures.append(candidate).append(" failed a one-frame probe; ");
        }
        if (failures.length() > 0) lastEncoderLog = tail(lastEncoderLog + "\n" + failures, 1400);
        return "";
    }

    /** Actually exercises the selected encoder on this device instead of trusting -encoders alone. */
    private boolean probeH264Encoder(String encoder, File workDirectory) {
        File probe = new File(workDirectory, "encoder_probe_" + encoder + ".mp4");
        deleteQuietly(probe);
        try {
            List<String> args = new ArrayList<String>();
            Collections.addAll(args, "-hide_banner", "-y", "-f", "lavfi", "-i", "color=c=black:s=128x128:r=1:d=0.1",
                    "-frames:v", "1", "-c:v", encoder, "-pix_fmt", "yuv420p");
            if ("libx264".equals(encoder)) Collections.addAll(args, "-preset", "veryfast", "-crf", "30");
            else Collections.addAll(args, "-b:v", "250000");
            Collections.addAll(args, "-f", "mp4", probe.getAbsolutePath());
            FFmpegSession test = FFmpegKit.executeWithArguments(args.toArray(new String[0]));
            return test != null && ReturnCode.isSuccess(test.getReturnCode()) && probe.isFile() && probe.length() > 0L;
        } catch (Throwable e) {
            lastEncoderLog = tail(lastEncoderLog + "\nEncoder probe " + encoder + ": " + safeMessage(e), 1400);
            return false;
        } finally { deleteQuietly(probe); }
    }

    private String bitrateForQuality(int quality, int resolution) {
        int h = resolutionHeight(resolution);
        double base = h <= 480 ? 900000.0d : (h <= 720 ? 2200000.0d : (h <= 1080 ? 4200000.0d : 7600000.0d));
        double target = base * Math.pow(2.0d, (24.0d - quality) / 6.0d);
        long bps = (long) Math.max(250000.0d, Math.min(24000000.0d, target));
        return String.valueOf(bps);
    }

    private String makeQualitySummary() {
        int crf = crfSeek == null ? 24 : crfSeek.getProgress() + 16;
        if ("not checked".equals(selectedEncoder)) return "Quality target " + crf + " (encoder-specific CRF/bitrate mode verified at Start)";
        return qualityLabelFor(selectedEncoder, crf);
    }

    private String qualityLabelFor(String encoder, int quality) {
        return "libx264".equals(encoder) ? "CRF " + quality : "quality target " + quality + " → bitrate estimate";
    }

    private String crfLabelText(int value) {
        String detail = value < 20 ? "higher quality / larger" : (value > 29 ? "smaller / more compression" : "balanced quality / size");
        return "CRF/quality " + value + " — " + detail;
    }

    private void refreshSummaryAndPreview() {
        if (summaryLabel == null) return;
        StringBuilder b = new StringBuilder();
        b.append("Input: ").append(videoUri == null ? "missing video" : videoName + " (" + formatBytes(videoSize) + ")").append('\n');
        b.append("Subtitles: ").append(subtitleUri == null ? "missing subtitle file" : subtitleName + " (" + formatBytes(subtitleSize) + ")").append('\n');
        b.append("Mode: hardcoded subtitles — permanently rendered into video\n");
        b.append("Resolution: ").append(RESOLUTIONS[resolutionSpinner.getSelectedItemPosition()]).append(" · FPS: ")
                .append(fpsValueFor(fpsSpinner.getSelectedItemPosition(), customFpsInput.getText().toString().trim()).length() == 0 ? "Original" : fpsValueFor(fpsSpinner.getSelectedItemPosition(), customFpsInput.getText().toString().trim())).append('\n');
        b.append("Encoder: ").append("not checked".equals(selectedEncoder) ? "auto, verified before encoding" : selectedEncoder).append(" · quality: ").append(makeQualitySummary()).append('\n');
        b.append("Preset: ").append(PRESETS[presetSpinner.getSelectedItemPosition()])
                .append(" (honored only when libx264 is actually available)").append(" · Audio: ")
                .append(AUDIO_OPTIONS[audioSpinner.getSelectedItemPosition()]).append('\n');
        b.append("Fonts: ").append(fontsTreeUri == null ? "system fallback" : fontCount + " supported file(s)").append('\n');
        b.append("Destination: ").append(destinationDescription(outputTreeUri));
        summaryLabel.setText(b.toString());
        commandPreview.setText(buildHumanReadableCommand());
    }

    private void refreshProposedFilename() {
        if (filenameInput == null || filenameEdited || videoName.length() == 0) return;
        String base = videoName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.replaceFirst("(?i)^\\[subvoryn\\][-._ ]*", "");
        base = base.replaceAll("[\\s_]+", ".").replaceAll("[^\\p{L}\\p{N}.()\\[\\]-]+", ".").replaceAll("\\.{2,}", ".");
        base = base.replaceAll("^[.]+|[.]+$", "");
        if (base.length() == 0) base = "Video";
        String res = resolutionSpinner == null || resolutionSpinner.getSelectedItemPosition() == 0 ? "Original" : RESOLUTIONS[resolutionSpinner.getSelectedItemPosition()];
        programmaticFilenameChange = true;
        filenameInput.setText("[SubVoryn]-" + base + "-HSub-" + res + ".mp4");
        filenameInput.setSelection(filenameInput.length());
        programmaticFilenameChange = false;
    }

    private void saveCurrentDefaults() {
        if (prefs == null || resolutionSpinner == null || fpsSpinner == null || presetSpinner == null || audioSpinner == null || crfSeek == null) return;
        prefs.edit().putInt(KEY_RESOLUTION, resolutionSpinner.getSelectedItemPosition())
                .putInt(KEY_FPS, fpsSpinner.getSelectedItemPosition())
                .putString(KEY_CUSTOM_FPS, customFpsInput == null ? "30" : customFpsInput.getText().toString().trim())
                .putInt(KEY_CRF, crfSeek.getProgress() + 16)
                .putInt(KEY_PRESET, presetSpinner.getSelectedItemPosition())
                .putInt(KEY_AUDIO, audioSpinner.getSelectedItemPosition()).apply();
    }

    @Override protected void onDestroy() {
        if (isFinishing() && encoding) {
            cancelRequested = true;
            FFmpegSession session = activeSession;
            if (session != null) {
                try { FFmpegKit.cancel(session.getSessionId()); } catch (Exception ignored) { }
            }
            deleteQuietly(activeTempOutput);
        }
        super.onDestroy();
    }

    private LinearLayout buildBottomNavigation(int active) {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(C_PANEL);
        String[] names = {"Home", "Encoding", "Settings"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(names[i]);
            b.setTextSize(12);
            b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            b.setTextColor(i == active ? C_ACCENT : C_MUTED);
            b.setMinHeight(0); b.setMinimumHeight(0);
            b.setPadding(dp(2), 0, dp(2), 0);
            b.setBackgroundDrawable(round(i == active ? C_PANEL_2 : C_PANEL, i == active ? C_ACCENT : C_PANEL, 12));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (index == 0) startActivity(new Intent(EncodingActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    else if (index == 2) startActivity(new Intent(EncodingActivity.this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1);
            lp.leftMargin = dp(5); lp.rightMargin = dp(5);
            nav.addView(b, lp);
        }
        nav.setPadding(dp(5), dp(5), dp(5), dp(5));
        return nav;
    }

    private void copyUriToFile(Uri uri, File destination) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException("Cannot read selected content URI: " + queryName(uri));
        OutputStream output = new FileOutputStream(destination);
        try { copyStream(input, output); }
        catch (Exception e) { deleteQuietly(destination); throw e; }
        finally { try { input.close(); } catch (Exception ignored) { } try { output.close(); } catch (Exception ignored) { } }
        if (!destination.isFile() || destination.length() == 0L) throw new IOException("Selected file copy failed: " + queryName(uri));
    }

    private void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        try {
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
        } finally { try { input.close(); } catch (Exception ignored) { } }
    }

    private int copyFonts(Uri tree, File destination) throws Exception {
        deleteTree(destination);
        if (!destination.mkdirs() && !destination.isDirectory()) throw new IOException("Cannot create a private font workspace.");
        int count = copyFontsRecursive(tree, DocumentsContract.getTreeDocumentId(tree), destination, 0, new int[] {0});
        if (count == 0) deleteTree(destination);
        return count;
    }

    private int copyFontsRecursive(Uri tree, String docId, File destination, int depth, int[] visited) throws Exception {
        if (depth > 8 || visited[0] > 1200) return 0;
        int count = 0;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        Cursor c = null;
        try {
            c = getContentResolver().query(children, new String[] {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            if (c == null) throw new IOException("The selected font folder cannot be listed.");
            while (c.moveToNext() && visited[0]++ < 1200) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    // Fontconfig's fontsdir should be a single discoverable directory; flatten supported fonts.
                    count += copyFontsRecursive(tree, id, destination, depth + 1, visited);
                } else if (isFontName(name)) {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    File out = new File(destination, safeDirectoryName(name));
                    int duplicate = 2;
                    while (out.exists()) out = new File(destination, "font-" + duplicate++ + "-" + safeDirectoryName(name));
                    copyUriToFile(docUri, out);
                    count++;
                }
            }
        } finally { if (c != null) c.close(); }
        return count;
    }

    private String queryName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        finally { if (c != null) c.close(); }
        return uri.getLastPathSegment() == null ? "selected-file" : uri.getLastPathSegment();
    }

    private long querySize(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[] {OpenableColumns.SIZE}, null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) { }
        finally { if (c != null) c.close(); }
        return -1L;
    }

    private String treeDisplayName(Uri tree) {
        if (tree == null) return "Download/SubVoryn/Encoded/";
        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            int colon = id.indexOf(':');
            String path = colon >= 0 ? id.substring(colon + 1) : id;
            return path.length() == 0 ? "Selected folder" : path;
        } catch (Exception e) { return "Selected folder"; }
    }

    private String destinationDescription(Uri folder) {
        return folder == null ? "Download/SubVoryn/Encoded/" : "Custom folder: " + treeDisplayName(folder);
    }

    private boolean isSupportedSubtitle(String name) {
        String ext = extensionOf(name);
        return "srt".equals(ext) || "vtt".equals(ext) || "ass".equals(ext) || "ssa".equals(ext);
    }

    private boolean isFontName(String name) {
        String ext = extensionOf(name);
        return "ttf".equals(ext) || "otf".equals(ext);
    }

    private String safeVideoExtension(String name) {
        String ext = extensionOf(name);
        if (ext.matches("[a-z0-9]{2,6}") && Arrays.asList("mp4", "mkv", "mov", "webm", "m4v", "avi", "ts", "mpg", "mpeg", "3gp").contains(ext)) return ext;
        return "mp4";
    }

    private String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String sanitizeOutputName(String input) {
        if (input == null) return "";
        String value = input.trim().replaceAll("[\\x00-\\x1F\\x7F/\\\\:*?\"<>|]+", "_").replaceAll("\\s+", " ");
        value = value.replaceAll("[. ]+$", "").trim();
        if (value.equals(".") || value.equals("..")) return "";
        return value;
    }

    private String ensureMp4(String filename) {
        if (filename == null || filename.length() == 0) return "SubVoryn-Encoded.mp4";
        return filename.toLowerCase(Locale.ROOT).endsWith(".mp4") ? filename : filename + ".mp4";
    }

    private String escapeFilterPath(String path) {
        return path.replace("\\", "\\\\").replace("'", "\\'").replace(":", "\\:").replace(",", "\\,").replace("[", "\\[").replace("]", "\\]").replace(";", "\\;");
    }

    private String safeDirectoryName(String name) {
        if (name == null || name.trim().length() == 0) return "fonts";
        return name.replaceAll("[\\x00-\\x1F\\x7F/\\\\:*?\"<>|]", "_");
    }

    private int resolutionHeight(int index) {
        if (index == 1) return 480;
        if (index == 2) return 720;
        if (index == 3) return 1080;
        if (index == 4) return 1440;
        return 0;
    }

    private String fpsValueFor(int index, String custom) {
        if (index <= 0) return "";
        if (index == 1) return "23.976";
        if (index == 2) return "24";
        if (index == 3) return "25";
        if (index == 4) return "29.97";
        if (index == 5) return "30";
        if (index == 6) return "50";
        if (index == 7) return "60";
        try { double x = Double.parseDouble(custom); return String.format(Locale.US, "%s", trimDecimal(x)); }
        catch (Exception e) { return ""; }
    }

    private String trimDecimal(double value) {
        String s = String.format(Locale.US, "%.3f", value);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String safeOutput(FFmpegSession session) {
        try { return session == null || session.getOutput() == null ? "" : session.getOutput(); }
        catch (Exception e) { return ""; }
    }

    private String safeValue(String value) { return value == null ? "" : value; }

    private String safeMessage(Throwable e) {
        if (e == null) return "unknown error";
        String message = e.getMessage();
        return message == null || message.trim().length() == 0 ? e.getClass().getSimpleName() : message;
    }

    private boolean looksLikeError(String message) {
        String v = message.toLowerCase(Locale.ROOT);
        return v.contains("error") || v.contains("failed") || v.contains("invalid") || v.contains("no such file") || v.contains("not found");
    }

    private String tail(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(value.length() - max);
    }

    private double parseSpeed(String speed) {
        try {
            String value = speed == null ? "" : speed.toLowerCase(Locale.ROOT).replace("x", "").trim();
            double parsed = Double.parseDouble(value);
            return parsed > 0.0d && !Double.isInfinite(parsed) && !Double.isNaN(parsed) ? parsed : 0.0d;
        } catch (Exception e) { return 0.0d; }
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private String formatDuration(long ms) {
        if (ms < 0L) ms = 0L;
        long sec = ms / 1000L;
        long h = sec / 3600L;
        long m = (sec % 3600L) / 60L;
        long s = sec % 60L;
        return h > 0L ? String.format(Locale.US, "%02d:%02d:%02d", h, m, s) : String.format(Locale.US, "%02d:%02d", m, s);
    }

    private String formatBytes(long bytes) {
        if (bytes < 0L) return "size unavailable";
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024.0d);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024.0d * 1024.0d));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0d * 1024.0d * 1024.0d));
    }

    /** Remove abandoned private-session directories only after seven days; never touch published outputs. */
    private void cleanOldWorkFiles(File root) {
        File[] files = root.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L;
        for (File f : files) {
            if (f.isDirectory() && f.lastModified() > 0L && f.lastModified() < cutoff) deleteTree(f);
        }
    }

    private void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        deleteQuietly(f);
    }

    private void deleteQuietly(File file) { try { if (file != null && file.exists()) file.delete(); } catch (Exception ignored) { } }

    private String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "...";
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackgroundDrawable(round(C_PANEL, C_BORDER, 17));
        return c;
    }

    private TextView sectionTitle(String value) { return text(value, 10, C_ACCENT, true); }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? C_BG : C_TEXT);
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        b.setBackgroundDrawable(round(primary ? C_ACCENT : C_PANEL_2, primary ? C_ACCENT : C_BORDER, 12));
        return b;
    }

    private EditText input(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setSingleLine(true); e.setTextSize(13);
        e.setTextColor(C_TEXT); e.setHintTextColor(C_MUTED); e.setInputType(inputType);
        e.setPadding(dp(11), dp(7), dp(11), dp(7));
        e.setBackgroundDrawable(round(C_PANEL_2, C_BORDER, 9));
        return e;
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setBackgroundDrawable(round(C_PANEL_2, C_BORDER, 9));
        return s;
    }

    private GradientDrawable round(int color, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), border);
        return d;
    }

    private LinearLayout.LayoutParams margin(int w, int h, int left, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.leftMargin = dp(left); p.bottomMargin = dp(bottom);
        return p;
    }

    private LinearLayout.LayoutParams fullWidth() { return new LinearLayout.LayoutParams(-1, dp(46)); }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private static final class FFmpegKitConfigCompat {
        private FFmpegKitConfigCompat() { }
        static void setFontDirectories(Context context, String fontsPath) throws IOException {
            try {
                List<String> directories = new ArrayList<String>();
                directories.add("/system/fonts");
                if (fontsPath != null && fontsPath.trim().length() > 0) directories.add(fontsPath);
                com.arthenica.ffmpegkit.FFmpegKitConfig.setFontDirectoryList(context,
                        directories, Collections.<String, String>emptyMap());
            } catch (Throwable failure) {
                throw new IOException("Custom fonts were copied, but FFmpeg font registration failed: " +
                        (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()), failure);
            }
        }
    }
}
