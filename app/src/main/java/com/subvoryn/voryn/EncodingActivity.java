package com.subvoryn.voryn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.ImageView;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

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
    public static final String KEY_ENCODER_MODE = "encoder_mode_v2";
    private static final String[] ENCODER_MODES = {"Auto (recommended)", "CPU software", "Hardware / GPU (MediaCodec, if supported)"};

    private static final int REQ_VIDEO = 5101;
    private static final int REQ_SUBTITLE = 5102;
    private static final int REQ_STYLE_EDITOR = 5105;
    private static final int REQ_FONTS = 5103;
    private static final int REQ_OUTPUT_FOLDER = 5104;
    private static final int C_BG = Color.rgb(22, 24, 36);
    private static final int C_PANEL = Color.rgb(39, 41, 60);
    private static final int C_PANEL_2 = Color.rgb(49, 53, 76);
    private static final int C_BORDER = Color.rgb(72, 78, 105);
    private static final int C_TEXT = Color.rgb(243, 246, 250);
    private static final int C_MUTED = Color.rgb(172, 180, 205);
    private static final int C_ACCENT = Color.rgb(194, 182, 241);
    private static final int C_AMBER = Color.rgb(231, 185, 128);
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
    private volatile boolean savePartialOnCancel = true;
    private volatile long lastProcessedMediaMs = 0L;
    private long encodingStartWallMs = 0L;
    private FFmpegSession activeSession;
    private File activeTempOutput;
    private File workDir;
    private String selectedEncoder = "not checked";
    private String lastEncoderLog = "";

    private LinearLayout shell;
    private android.widget.ScrollView screenScroll;
    private TextView videoLabel, subtitleLabel, fontLabel, outputFolderLabel;
    private TextView summaryLabel, commandPreview, startHint, progressLabel, progressDetails, outputLabel, errorLogLabel;
    private EditText filenameInput, customFpsInput;
    private Spinner resolutionSpinner, fpsSpinner, presetSpinner, audioSpinner, encoderModeSpinner;
    private SeekBar crfSeek;
    private TextView crfLabel;
    private ProgressBar encodeProgress;
    private LinearLayout progressCard;
    private Button startButton, cancelButton, openOutputButton, shareOutputButton;
    private Button editStyleButton;
    private TextView editStyleHint;
    private File appliedStyledSubtitle;
    private String appliedStyleSourceUri = "";

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
        screenScroll = scroll;
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
        subtitleLabel = text("No subtitle file selected in Encoding", 13, C_MUTED, false);
        inputCard.addView(subtitleLabel, margin(-1, 11, 0, 5));
        Button subtitleButton = button("Import Subtitle File", true);
        subtitleButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickDocument(REQ_SUBTITLE); } });
        inputCard.addView(subtitleButton, fullWidth());
        // SUBVORYN_STYLE_FIX_V1: visible disabled editor action
        editStyleButton = button("Edit Subtitle Style", false);
        editStyleButton.setEnabled(false);
        editStyleButton.setAlpha(1.0f);
        editStyleButton.setTextColor(C_MUTED);
        editStyleButton.setBackgroundDrawable(round(C_PANEL_2, C_ACCENT, 12));
        editStyleButton.setContentDescription("Edit Subtitle Style. Import an ASS, SSA, or VTT file in Encoding to enable this action.");
        editStyleButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openSubtitleStyleEditor(); } });
        LinearLayout.LayoutParams styleButtonParams = new LinearLayout.LayoutParams(-1, dp(48));
        styleButtonParams.topMargin = dp(7);
        inputCard.addView(editStyleButton, styleButtonParams);
        editStyleHint = text("Import an ASS, SSA, or VTT file in Encoding to edit its style. A subtitle loaded on Home is separate and is not selected here.", 11, C_MUTED, false);
        editStyleHint.setGravity(Gravity.TOP | Gravity.LEFT);
        editStyleHint.setPadding(dp(4), dp(7), dp(4), dp(2));
        editStyleHint.setVisibility(View.VISIBLE);
        inputCard.addView(editStyleHint, margin(-1, -2, 0, 0));
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
        qualityCard.addView(text("ENCODER MODE", 10, C_ACCENT, true), margin(-1, -2, 0, 4));
        encoderModeSpinner = spinner(ENCODER_MODES);
        encoderModeSpinner.setSelection(clamp(prefs.getInt(KEY_ENCODER_MODE, 0), 0, ENCODER_MODES.length - 1));
        encoderModeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                saveCurrentDefaults();
                refreshSummaryAndPreview();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        qualityCard.addView(encoderModeSpinner, margin(-1, dp(46), 0, 7));
        qualityCard.addView(text("Hardware mode uses Android MediaCodec only when the device exposes a working H.264 hardware encoder. This is not a promise of direct GPU compute. Auto mode probes supported encoders before encoding.", 11, C_MUTED, false), margin(-1, -2, 0, 10));
        qualityCard.addView(text("QUALITY TARGET (CRF / BITRATE TARGET 16–35)", 10, C_ACCENT, true), margin(-1, -2, 0, 4));
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
        startHint = text("Select both a video and subtitle file to enable encoding.", 11, C_MUTED, false);
        summaryCard.addView(startHint, margin(-1, -2, 0, 5));
        startButton = button("Start Encoding", true);
        startButton.setEnabled(false);
        startButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startEncoding(); } });
        summaryCard.addView(startButton, margin(-1, dp(48), 0, 8));
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
        cancelButton = button("Cancel Encoding", false);
        cancelButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmCancel(); } });
        progressCard.addView(cancelButton, fullWidth());
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
        // SUBVORYN_STYLE_FIX_V1: explicit editor state explanation
        String styleFormat = SubtitleStyleEngine.detectFormat(subtitleName, "");
        boolean styleFormatSupported = subtitleUri != null && SubtitleStyleEngine.isStyleEditorSupported(subtitleName);
        String shownFormat = styleFormat.length() > 0 ? styleFormat.toUpperCase(Locale.ROOT) : extensionOf(subtitleName).toUpperCase(Locale.ROOT);
        subtitleLabel.setText(subtitleUri == null ? "No subtitle file selected in Encoding" : subtitleName + "  ·  " + (shownFormat.length() == 0 ? "SUBTITLE" : shownFormat) + "  ·  " + formatBytes(subtitleSize) + (isStyledSubtitleForCurrentSource() ? "  ·  Style applied" : ""));
        if (editStyleButton != null) {
            editStyleButton.setVisibility(View.VISIBLE);
            editStyleButton.setEnabled(SubtitleStyleEngine.canEditStyleButton(subtitleUri != null, subtitleName, encoding));
            editStyleButton.setAlpha(1.0f);
            editStyleButton.setTextColor(styleFormatSupported && !encoding ? C_TEXT : C_MUTED);
            editStyleButton.setBackgroundDrawable(round(C_PANEL_2, C_ACCENT, 12));
            editStyleButton.setText(isStyledSubtitleForCurrentSource() ? "Edit Subtitle Style · Applied" : "Edit Subtitle Style");
            editStyleButton.setContentDescription(styleFormatSupported && !encoding
                    ? "Edit subtitle style for " + subtitleName
                    : "Edit Subtitle Style disabled. Import an ASS, SSA, or VTT file in Encoding.");
        }
        if (editStyleHint != null) {
            String hint;
            if (encoding) hint = "Encoding is running; style editing is temporarily disabled.";
            else if (subtitleUri == null) hint = "Import an ASS, SSA, or VTT file in Encoding to edit its style. A subtitle loaded on Home is separate and is not selected here.";
            else if (!styleFormatSupported) hint = "Style Editor supports ASS, SSA, and VTT. The selected file is " + (shownFormat.length() == 0 ? "an unknown format" : shownFormat) + "; choose a supported subtitle format.";
            else hint = "Selected in Encoding: " + subtitleName + " (" + shownFormat + "). Changes use a working copy; the imported original stays unchanged.";
            editStyleHint.setText(hint);
            editStyleHint.setTextColor(subtitleUri != null && !styleFormatSupported ? Color.rgb(238, 174, 111) : C_MUTED);
            editStyleHint.setVisibility(View.VISIBLE);
        }
        if (fontsTreeUri == null) fontLabel.setText("No custom font folder selected. System fonts will be used.");
        if (outputTreeUri == null) outputFolderLabel.setText("Download/SubVoryn/Encoded/");
        refreshProposedFilename();
        boolean inputsReady = videoUri != null && subtitleUri != null;
        if (startButton != null) startButton.setEnabled(inputsReady && !encoding);
        if (startHint != null) {
            if (encoding) startHint.setText("Encoding is running. Live status is shown below.");
            else if (!inputsReady) startHint.setText("Select both a video and subtitle file to enable encoding.");
            else startHint.setText("Ready to encode. Tap Start Encoding; the screen will scroll to live progress.");
        }
    }

    private void openSubtitleStyleEditor() {
        if (subtitleUri == null) { toast("Import a subtitle file first."); return; }
        Intent intent = new Intent(this, SubtitleStyleEditorActivity.class);
        intent.putExtra(SubtitleStyleEditorActivity.EXTRA_URI, subtitleUri.toString());
        intent.putExtra(SubtitleStyleEditorActivity.EXTRA_NAME, subtitleName);
        intent.putExtra(SubtitleStyleEditorActivity.EXTRA_FONTS_URI, fontsTreeUri == null ? "" : fontsTreeUri.toString());
        try { startActivityForResult(intent, REQ_STYLE_EDITOR); }
        catch (Exception e) { toast("Subtitle Style Editor could not be opened: " + safeMessage(e)); }
    }

    // SUBVORYN_STYLE_FIX_V1: validate styled working file
    /** Reject stale, malformed, wrong-format, or empty editor working files before binding them to Encoding. */
    private boolean isValidStyledSubtitleWorkingFile(File candidate, String format) {
        if (candidate == null || !candidate.isFile() || candidate.length() <= 0L
                || candidate.length() > 20L * 1024L * 1024L) return false;
        if (!(SubtitleStyleEngine.ASS.equals(format) || SubtitleStyleEngine.SSA.equals(format)
                || SubtitleStyleEngine.VTT.equals(format))) return false;
        StringBuilder body = new StringBuilder((int) Math.min(candidate.length(), 65536L));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(candidate), "UTF-8"))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (body.length() + read > 20 * 1024 * 1024) return false;
                body.append(buffer, 0, read);
            }
        } catch (Exception ignored) { return false; }
        String text = body.toString();
        if (!format.equals(SubtitleStyleEngine.detectFormat(candidate.getName(), text))) return false;
        if (SubtitleStyleEngine.VTT.equals(format)) {
            String trimmed = text.replace("\uFEFF", "").trim();
            return trimmed.regionMatches(true, 0, "WEBVTT", 0, 6)
                    && text.contains(SubtitleStyleEngine.MARKER_START)
                    && text.contains(SubtitleStyleEngine.MARKER_END);
        }
        return !SubtitleStyleEngine.styleNames(text).isEmpty();
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
        if (requestCode == REQ_STYLE_EDITOR) {
            if (resultCode == RESULT_OK && data != null) {
                String path = data.getStringExtra(SubtitleStyleEditorActivity.EXTRA_STYLED_PATH);
                String source = data.getStringExtra(SubtitleStyleEditorActivity.EXTRA_SOURCE_URI);
                String format = data.getStringExtra(SubtitleStyleEditorActivity.EXTRA_FORMAT);
                File candidate = path == null ? null : new File(path);
                String expectedFormat = SubtitleStyleEngine.detectFormat(subtitleName, "");
                // SUBVORYN_STYLE_FIX_V1: validate editor return contract
                boolean valid = subtitleUri != null
                        && source != null && source.equals(subtitleUri.toString())
                        && candidate != null && candidate.isFile() && candidate.length() > 0L
                        && format != null && format.equals(expectedFormat)
                        && candidate != null && format.equals(SubtitleStyleEngine.detectFormat(candidate.getName(), ""))
                        && isValidStyledSubtitleWorkingFile(candidate, format);
                if (valid) {
                    appliedStyledSubtitle = candidate; appliedStyleSourceUri = source;
                    updateSelectionLabels(); refreshSummaryAndPreview();
                    toast("Subtitle style applied to the current encoding workflow.");
                } else toast("Styled result was rejected: the source, format, or working file did not match the current Encoding selection.");
            }
            return;
        }
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
            appliedStyledSubtitle = null; appliedStyleSourceUri = "";
            if (editStyleButton != null) editStyleButton.setText("Edit Subtitle Style");
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
        final int encoderMode = encoderModeSpinner.getSelectedItemPosition();
        final String selectedStyledPath = appliedStyledSubtitle != null && appliedStyledSubtitle.isFile() && subtitleUri != null && appliedStyleSourceUri.equals(subtitleUri.toString()) ? appliedStyledSubtitle.getAbsolutePath() : "";
        final boolean selectedVttStyleApplied = selectedStyledPath.length() > 0 && "vtt".equals(extensionOf(subtitleName));
        prefs.edit().putInt(KEY_ENCODER_MODE, encoderMode).apply();

        encoding = true;
        cancelRequested = false;
        savePartialOnCancel = true;
        lastProcessedMediaMs = 0L;
        activeSession = null;
        activeTempOutput = null;
        lastOutputUri = null;
        outputLabel.setText("Output will appear here after successful finalization.");
        openOutputButton.setEnabled(false);
        shareOutputButton.setEnabled(false);
        progressCard.setVisibility(View.VISIBLE);
        if (startHint != null) startHint.setText("Preparing the files and verifying the FFmpeg engine…");
        if (screenScroll != null) {
            progressCard.post(new Runnable() {
                public void run() {
                    if (screenScroll != null) screenScroll.smoothScrollTo(0, Math.max(0, progressCard.getTop()));
                }
            });
        }
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
                            outName, resolution, fps, customFps, quality, preset, audio, encoderMode, selectedStyledPath, selectedVttStyleApplied);
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
            String customFps, int quality, int presetIndex, int audioIndex, int encoderMode, String styledSubtitlePath, boolean vttStyleApplied) throws Exception {
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
        selectedEncoder = chooseUsableH264Encoder(encoderList, workDir, encoderMode);
        if (selectedEncoder.length() == 0) throw new IOException("No H.264 encoder passed a one-frame runtime test. Listed encoders were: " + clip(encoderList, 1000) + ". This device/engine combination cannot encode H.264 with the available build; no output was created.");
        if (cancelRequested) throw new IOException("Cancelled during engine capability checks.");

        postProgress("Copying selected video into the app workspace…", "SAF content URIs are streamed to disk; the complete video is never loaded into memory.", true);
        File input = new File(workDir, "input." + safeVideoExtension(videoName));
        copyUriToFile(selectedVideo, input);
        if (cancelRequested) throw new IOException("Cancelled during preparation.");
        postProgress("Copying subtitle file…", "Selected subtitles will be burned once into the video.", true);
        File subtitleSource = new File(workDir, "subtitles_source." + extensionOf(subtitleName));
        if (styledSubtitlePath != null && styledSubtitlePath.trim().length() > 0) {
            File styledFile = new File(styledSubtitlePath);
            if (!styledFile.isFile() || styledFile.length() == 0L) throw new IOException("The applied styled subtitle working file is missing. Reopen Edit Subtitle Style and apply the changes again.");
            copyLocalFile(styledFile, subtitleSource);
        } else copyUriToFile(selectedSubtitle, subtitleSource);
        if (cancelRequested) throw new IOException("Cancelled during preparation.");
        if (!input.isFile() || input.length() == 0L) throw new IOException("The copied input video is empty or inaccessible.");
        if (!subtitleSource.isFile() || subtitleSource.length() == 0L) throw new IOException("The copied subtitle file is empty or inaccessible.");
        File subtitle = prepareSubtitleForRender(subtitleSource, workDir);
        if (cancelRequested) throw new IOException("Cancelled during subtitle preparation.");

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
        final File sessionWorkDir = workDir;
        final long expectedDuration = durationMs;
        final String engineEncoder = selectedEncoder;
        final String filter = makeFilter(subtitle, fontsDir, copiedFonts, resolution, vttStyleApplied);
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
        // Fragmented MP4 commits short fragments during encoding. It allows a canceled job to retain only the encoded prefix.
        Collections.addAll(args, "-movflags", "+frag_keyframe+empty_moov+default_base_moof", "-frag_duration", "1000000", "-stats_period", "0.5", outFile.getAbsolutePath());

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
                            if (cancelRequested && savePartialOnCancel && outFile.isFile() && outFile.length() > 1024L) {
                                finalizeAndPublishPartial(outFile, sessionWorkDir, outputName, selectedOutputFolder, expectedDuration, engineEncoder, wallStart);
                            } else {
                                deleteQuietly(outFile);
                                deleteTree(sessionWorkDir);
                                runOnUiThread(new Runnable() { public void run() { finishCancelled(); } });
                            }
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
                                            if (startHint != null) startHint.setText("Encoding complete. Output saved below; ready for another job.");
                                            startButton.setEnabled(videoUri != null && subtitleUri != null);
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
                        lastProcessedMediaMs = processed;
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

    private File prepareSubtitleForRender(File source, File sessionDir) throws IOException {
        String ext = extensionOf(source.getName());
        if ("ass".equals(ext) || "ssa".equals(ext)) return source; // Never flatten or rewrite existing ASS/SSA styling.
        int format = "vtt".equals(ext) ? SubtitleEngine.FORMAT_VTT : SubtitleEngine.FORMAT_SRT;
        if (source.length() > 20L * 1024L * 1024L) throw new IOException("Subtitle file exceeds the 20 MB safety limit.");
        StringBuilder body = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(source), "UTF-8"));
        try {
            char[] buffer = new char[8192]; int count;
            while ((count = reader.read(buffer)) != -1) body.append(buffer, 0, count);
        } finally { try { reader.close(); } catch (Exception ignored) { } }
        List<SubtitleCue> parsed = SubtitleEngine.parse(body.toString(), format);
        if (parsed == null || parsed.isEmpty()) throw new IOException("The selected SRT/VTT file has no valid subtitle cues.");
        String ass = SubtitleEngine.export(parsed, SubtitleEngine.FORMAT_ASS, SubtitleEngine.POSITION_ORIGINAL);
        File renderFile = new File(sessionDir, "subtitles_render.ass");
        FileOutputStream output = new FileOutputStream(renderFile, false);
        try { output.write(ass.getBytes("UTF-8")); output.flush(); }
        finally { try { output.close(); } catch (Exception ignored) { } }
        if (!renderFile.isFile() || renderFile.length() == 0L) throw new IOException("Could not convert SRT/VTT cues to a renderer-compatible ASS file.");
        return renderFile;
    }

    private String makeFilter(File subtitle, File fontsDir, int copiedFonts, int resolution, boolean vttStyleApplied) throws IOException {
        if (subtitle == null || !subtitle.isFile()) throw new IOException("Subtitle file was not staged correctly.");
        String ext = extensionOf(subtitle.getName());
        boolean ass = "ass".equals(ext) || "ssa".equals(ext);
        String subPath = escapeFilterPath(subtitle.getAbsolutePath());
        String filter = "subtitles=filename='" + subPath + "'";
        if (ass && copiedFonts > 0 && fontsDir.isDirectory()) filter += ":fontsdir='" + escapeFilterPath(fontsDir.getAbsolutePath()) + "'";
        // Only edited VTT input gets WebVTT-style renderer overrides. ASS/SSA always keep native styles and never get force_style.
        if (vttStyleApplied) {
            String forceStyle = currentVttForceStyle();
            if (forceStyle.length() > 0) filter += ":force_style='" + escapeFilterForceStyle(forceStyle) + "'";
        }
        int height = resolutionHeight(resolution);
        if (height > 0) {
            // A selected 480p/720p/1080p/1440p target means that exact output height, with aspect ratio intact.
            // Original Resolution returns above without adding any scale filter.
            filter += ",scale=-2:" + height;
        }
        return filter;
    }

    private void confirmCancel() {
        if (!encoding) return;
        new AlertDialog.Builder(this).setTitle("Stop encoding?")
                .setMessage("Save only the already-encoded beginning of this video as a playable PARTIAL file? The remaining unencoded duration will not be included. Choose Discard to remove the temporary output instead.")
                .setNegativeButton("Continue encoding", null)
                .setNeutralButton("Cancel & discard", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) { requestEncodingCancel(false); }
                })
                .setPositiveButton("Cancel & save partial", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) { requestEncodingCancel(true); }
                }).show();
    }

    private void requestEncodingCancel(boolean keepPartial) {
        savePartialOnCancel = keepPartial;
        cancelRequested = true;
        progressLabel.setText(keepPartial ? "Stopping and preparing the encoded prefix…" : "Cancellation requested; partial output will be discarded…");
        if (startHint != null) startHint.setText(keepPartial ? "Saving only the already-encoded part; unprocessed video will not be included." : "Stopping encoding and discarding the temporary file.");
        cancelButton.setEnabled(false);
        FFmpegSession session = activeSession;
        if (session != null) FFmpegKit.cancel(session.getSessionId());
    }

    private void finalizeAndPublishPartial(final File raw, final File sessionDir, final String requestedName,
            final Uri selectedFolder, final long expectedDuration, final String encoder, final long wallStart) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    final File playable = finalizePartialFile(raw, sessionDir);
                    long mediaDuration = durationOfVideoFile(playable);
                    if (mediaDuration <= 0L) mediaDuration = lastProcessedMediaMs;
                    if (expectedDuration > 0L) mediaDuration = Math.min(mediaDuration, expectedDuration);
                    if (mediaDuration <= 0L) throw new IOException("FFmpeg stopped before a measurable playable video segment was produced.");
                    final long savedDuration = mediaDuration;
                    final long bytes = playable.length();
                    final String partialName = buildPartialFilename(requestedName, savedDuration);
                    final Uri savedUri = publishOutput(playable, partialName, selectedFolder);
                    final String actualName = queryName(savedUri);
                    deleteTree(sessionDir);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            finishPartialSaved(savedUri, actualName == null || actualName.length() == 0 ? partialName : actualName,
                                    savedDuration, bytes, wallStart, encoder, expectedDuration);
                        }
                    });
                } catch (final Throwable failure) {
                    deleteTree(sessionDir);
                    runOnUiThread(new Runnable() {
                        public void run() { finishCancelWithoutPlayablePartial("The encode was stopped, but the produced fragment could not be validated/finalized as playable. No false successful file was saved. Detail: " + safeMessage(failure)); }
                    });
                }
            }
        }, "SubVoryn-PartialFinalize").start();
    }

    private File finalizePartialFile(File raw, File sessionDir) throws IOException {
        if (raw == null || !raw.isFile() || raw.length() <= 1024L) throw new IOException("The partial MP4 is empty or too small to validate.");
        File remuxed = new File(sessionDir, "partial-finalized.mp4");
        deleteQuietly(remuxed);
        String[] remuxArgs = new String[] {"-hide_banner", "-y", "-i", raw.getAbsolutePath(), "-map", "0:v:0", "-map", "0:a?", "-c", "copy", "-movflags", "+faststart", remuxed.getAbsolutePath()};
        try {
            FFmpegSession remux = FFmpegKit.executeWithArguments(remuxArgs);
            if (remux != null && ReturnCode.isSuccess(remux.getReturnCode()) && isPlayablePartial(remuxed)) return remuxed;
        } catch (Throwable ignored) { }
        if (isPlayablePartial(raw)) return raw;
        throw new IOException("The fragmented MP4 did not pass remux/playability checks. Try encoding a little longer before canceling.");
    }

    private boolean isPlayablePartial(File candidate) {
        if (candidate == null || !candidate.isFile() || candidate.length() <= 1024L) return false;
        if (durationOfVideoFile(candidate) <= 0L) return false;
        try {
            FFmpegSession check = FFmpegKit.executeWithArguments(new String[] {"-hide_banner", "-v", "error", "-i", candidate.getAbsolutePath(), "-map", "0:v:0", "-f", "null", "-"});
            return check != null && ReturnCode.isSuccess(check.getReturnCode());
        } catch (Throwable ignored) { return false; }
    }

    private long durationOfVideoFile(File file) {
        if (file == null || !file.isFile()) return 0L;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0L : Math.max(0L, Long.parseLong(value));
        } catch (Throwable ignored) { return 0L; }
        finally { try { retriever.release(); } catch (Exception ignored) { } }
    }

    private String buildPartialFilename(String requested, long durationMs) {
        String name = ensureMp4(sanitizeOutputName(requested));
        String base = name.substring(0, name.length() - 4);
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long minutes = totalSeconds / 60L, seconds = totalSeconds % 60L;
        return base + "-PARTIAL-" + String.format(Locale.US, "%02dm%02ds", minutes, seconds) + ".mp4";
    }

    private void finishPartialSaved(Uri uri, String name, long mediaMs, long bytes, long wallStart, String encoder, long expectedDuration) {
        encoding = false; cancelRequested = false; savePartialOnCancel = true; lastOutputUri = uri;
        encodeProgress.setIndeterminate(false);
        int percent = expectedDuration > 0L ? clamp((int) (100L * Math.min(mediaMs, expectedDuration) / expectedDuration), 0, 99) : 0;
        encodeProgress.setProgress(percent);
        progressLabel.setText("Cancelled · playable partial saved");
        progressDetails.setText("Saved only the encoded prefix: " + formatDuration(mediaMs) + " of " + formatDuration(expectedDuration) +
                " source duration. Remaining unencoded video is not included.\nOutput " + formatBytes(bytes) + " · encoder " + encoder +
                " · elapsed " + formatDuration(Math.max(0L, System.currentTimeMillis() - wallStart)));
        outputLabel.setText("PARTIAL video saved: " + name + "\n" + destinationDescription(outputTreeUri) + "\nPreview this segment before restarting the full encode.");
        if (startHint != null) startHint.setText("Playable partial saved. Fix subtitle timing/settings, then start a new encode from the beginning.");
        startButton.setEnabled(videoUri != null && subtitleUri != null); cancelButton.setEnabled(false);
        openOutputButton.setEnabled(true); shareOutputButton.setEnabled(true);
        toast("Playable partial video saved: " + formatDuration(mediaMs));
    }

    private void finishCancelWithoutPlayablePartial(String detail) {
        encoding = false; cancelRequested = false; savePartialOnCancel = true;
        encodeProgress.setIndeterminate(false);
        progressLabel.setText("Encoding cancelled · no playable partial");
        progressDetails.setText(detail);
        startButton.setEnabled(videoUri != null && subtitleUri != null); cancelButton.setEnabled(false);
        if (startHint != null) startHint.setText("No playable partial could be saved. Try canceling after more video has encoded.");
        if (activeTempOutput != null) deleteQuietly(activeTempOutput);
        toast("Encoding stopped; partial output could not be finalized.");
    }

    private void finishCancelled() {
        encoding = false;
        cancelRequested = false;
        encodeProgress.setIndeterminate(false);
        progressLabel.setText("Encoding cancelled");
        progressDetails.setText("The incomplete temporary output was removed. Source files were not changed.");
        startButton.setEnabled(videoUri != null && subtitleUri != null);
        if (startHint != null) startHint.setText("Encoding was cancelled. You can review settings and start again.");
        cancelButton.setEnabled(false);
        if (activeTempOutput != null) deleteQuietly(activeTempOutput);
        toast("Encoding cancelled.");
    }

    private void finishWithError(String message) {
        encoding = false;
        encodeProgress.setIndeterminate(false);
        progressLabel.setText("Not completed");
        progressDetails.setText(message == null ? "Unknown error." : message);
        startButton.setEnabled(videoUri != null && subtitleUri != null);
        if (startHint != null) startHint.setText("Encoding did not complete. Review the error details below, then retry.");
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
        if ("vtt".equals(ext) && isStyledSubtitleForCurrentSource()) { String forceStyle = currentVttForceStyle(); if (forceStyle.length() > 0) filter += ":force_style='" + escapeFilterForceStyle(forceStyle) + "'"; }
        if (("ass".equals(ext) || "ssa".equals(ext)) && fontsTreeUri != null && fontCount > 0) filter += ":fontsdir='<selected-font-folder>'";
        int height = resolutionHeight(resolutionSpinner == null ? 0 : resolutionSpinner.getSelectedItemPosition());
        if (height > 0) filter += ",scale=-2:" + height;
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
                " -pix_fmt yuv420p " + frameRate + " " + audioCommand + " -movflags +frag_keyframe+empty_moov+default_base_moof -frag_duration 1000000 \"" + name + "\"\n\n" +
                "This is a readable equivalent configuration. The app copies SAF streams to a private working directory and resolves actual paths at runtime; this preview does not expose those private paths and may not be directly runnable as pasted.";
    }

    private String escapePreviewFilter(String value) {
        if (value == null) return "<selected-subtitle>";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace(":", "\\:").replace(",", "\\,");
    }

    private String chooseUsableH264Encoder(String output, File workDirectory, int requestedMode) throws IOException {
        final String[] candidates;
        if (requestedMode == 1) candidates = new String[] {"libx264", "libopenh264"};
        else if (requestedMode == 2) candidates = new String[] {"h264_mediacodec"};
        else candidates = new String[] {"libx264", "libopenh264", "h264_mediacodec"};
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
        b.append("Subtitle style: ").append(isStyledSubtitleForCurrentSource() ? "edited style will be used" : "source styling/default renderer styling").append('\n');
        b.append("Mode: hardcoded subtitles — permanently rendered into video\n");
        b.append("Resolution: ").append(RESOLUTIONS[resolutionSpinner.getSelectedItemPosition()]).append(" · FPS: ")
                .append(fpsValueFor(fpsSpinner.getSelectedItemPosition(), customFpsInput.getText().toString().trim()).length() == 0 ? "Original" : fpsValueFor(fpsSpinner.getSelectedItemPosition(), customFpsInput.getText().toString().trim())).append('\n');
        b.append("Encoder mode: ").append(encoderModeSpinner == null ? ENCODER_MODES[0] : ENCODER_MODES[clamp(encoderModeSpinner.getSelectedItemPosition(), 0, ENCODER_MODES.length - 1)]).append('\n');
        b.append("Actual encoder: ").append("not checked".equals(selectedEncoder) ? "verified at Start" : selectedEncoder).append(" · quality: ").append(makeQualitySummary()).append('\n');
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
        if (prefs == null || resolutionSpinner == null || fpsSpinner == null || presetSpinner == null || audioSpinner == null || crfSeek == null || encoderModeSpinner == null) return;
        prefs.edit().putInt(KEY_RESOLUTION, resolutionSpinner.getSelectedItemPosition())
                .putInt(KEY_FPS, fpsSpinner.getSelectedItemPosition())
                .putString(KEY_CUSTOM_FPS, customFpsInput == null ? "30" : customFpsInput.getText().toString().trim())
                .putInt(KEY_CRF, crfSeek.getProgress() + 16)
                .putInt(KEY_PRESET, presetSpinner.getSelectedItemPosition())
                .putInt(KEY_AUDIO, audioSpinner.getSelectedItemPosition()).putInt(KEY_ENCODER_MODE, encoderModeSpinner.getSelectedItemPosition()).apply();
    }

    @Override protected void onDestroy() {
        if (isFinishing() && encoding) {
            // Leave the fragmented file intact so the completion callback can validate and save the encoded prefix.
            cancelRequested = true;
            savePartialOnCancel = true;
            FFmpegSession session = activeSession;
            if (session != null) {
                try { FFmpegKit.cancel(session.getSessionId()); } catch (Exception ignored) { }
            }
        }
        super.onDestroy();
    }

    private LinearLayout buildBottomNavigation(int active) {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(C_PANEL);
        nav.setElevation(dp(5));
        int[] icons = new int[] {R.drawable.ic_nav_home, R.drawable.ic_nav_encode, R.drawable.ic_nav_settings};
        final String[] labels = new String[] {"Home", "Encoding", "Settings"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout tile = new LinearLayout(this);
            tile.setGravity(Gravity.CENTER);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setContentDescription(labels[i]);
            tile.setFocusable(true);
            tile.setBackgroundDrawable(round(i == active ? C_PANEL_2 : C_PANEL,
                    i == active ? C_ACCENT : C_BORDER, 16));
            tile.setElevation(dp(i == active ? 6 : 2));
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(i == active ? C_ACCENT : C_MUTED);
            icon.setContentDescription(labels[i]);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
            tile.addView(icon, iconParams);
            tile.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (index == 0 && !(false)) startActivity(new Intent(EncodingActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    else if (index == 1 && !(true)) startActivity(new Intent(EncodingActivity.this, EncodingActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    else if (index == 2 && !(false)) startActivity(new Intent(EncodingActivity.this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                }
            });
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
            tileParams.leftMargin = dp(5); tileParams.rightMargin = dp(5);
            nav.addView(tile, tileParams);
        }
        nav.setPadding(dp(7), dp(6), dp(7), dp(6));
        return nav;
    }

    private boolean isStyledSubtitleForCurrentSource() {
        if (appliedStyledSubtitle == null || !appliedStyledSubtitle.isFile() || subtitleUri == null || !appliedStyleSourceUri.equals(subtitleUri.toString())) return false;
        SharedPreferences stylePrefs = getSharedPreferences(SubtitleStyleEditorActivity.PREFS, MODE_PRIVATE);
        return subtitleUri.toString().equals(stylePrefs.getString("last_source_uri", ""));
    }

    private String currentVttForceStyle() {
        SharedPreferences p = getSharedPreferences(SubtitleStyleEditorActivity.PREFS, MODE_PRIVATE);
        if (subtitleUri == null || !subtitleUri.toString().equals(p.getString("last_source_uri", ""))) return "";
        Map<String, String> m = new LinkedHashMap<String, String>();
        String[] fields = {"Fontname","Fontsize","PrimaryColour","SecondaryColour","OutlineColour","BackColour","Bold","Italic","Underline","StrikeOut","Spacing","BorderStyle","Outline","Shadow","Alignment","MarginL","MarginR","MarginV"};
        for (String field : fields) m.put(field, p.getString("last_" + field, ""));
        return SubtitleStyleEngine.buildVttForceStyle(m);
    }

    private String escapeFilterForceStyle(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'").replace(":", "\\:");
    }

    private void copyLocalFile(File source, File destination) throws IOException {
        if (source == null || !source.isFile() || source.length() == 0L) throw new IOException("Styled subtitle file is missing or empty.");
        InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(destination, false);
        try { byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); out.flush(); }
        finally { try { in.close(); } catch (Exception ignored) { } try { out.close(); } catch (Exception ignored) { } }
        if (!destination.isFile() || destination.length() == 0L) throw new IOException("Styled subtitle could not be copied into the FFmpeg workspace.");
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
        c.setElevation(dp(5));
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
        b.setElevation(dp(primary ? 5 : 2));
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
