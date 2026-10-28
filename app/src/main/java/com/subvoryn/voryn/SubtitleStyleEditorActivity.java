package com.subvoryn.voryn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Real, preservation-first style editor for ASS/SSA and WebVTT subtitle files. */
public class SubtitleStyleEditorActivity extends Activity {
    public static final String EXTRA_URI = "subvoryn_style_uri";
    public static final String EXTRA_NAME = "subvoryn_style_name";
    public static final String EXTRA_FONTS_URI = "subvoryn_style_fonts_uri";
    public static final String EXTRA_STYLED_PATH = "subvoryn_styled_subtitle_path";
    public static final String EXTRA_SOURCE_URI = "subvoryn_styled_source_uri";
    public static final String EXTRA_FORMAT = "subvoryn_styled_format";
    public static final String PREFS = "subvoryn_style_editor";
    private static final int REQ_SAVE_AS = 6201;
    private static final int MAX_SOURCE_BYTES = 20 * 1024 * 1024;
    private static final int BG = Color.rgb(18, 20, 30);
    private static final int PANEL = Color.rgb(31, 34, 49);
    private static final int RAISED = Color.rgb(41, 45, 64);
    private static final int BORDER = Color.rgb(72, 77, 104);
    private static final int TEXT = Color.rgb(244, 242, 255);
    private static final int MUTED = Color.rgb(176, 178, 202);
    private static final int ACCENT = Color.rgb(190, 166, 255);
    private static final int ACCENT_DARK = Color.rgb(89, 71, 143);

    private Uri sourceUri, fontsTreeUri, pendingSaveUri;
    private String sourceName = "subtitle.ass", sourceFormat = "", sourceText = "", workingText = "", selectedStyle = "Default", pendingSaveText = "";
    private String currentSourceKey = "";
    private SharedPreferences prefs;
    private final Map<String, String> values = new LinkedHashMap<String, String>();
    private List<String> styleNames = new ArrayList<String>();
    private List<String> fontNames = new ArrayList<String>();
    private LinearLayout root, content;
    private TextView status, subtitleInfo, overrideWarning, fontFolderStatus;
    private Spinner styleSpinner, fontSpinner, alignmentSpinner, borderSpinner;
    private EditText sizeEdit, outlineEdit, shadowEdit, spacingEdit, marginLEdit, marginREdit, marginVEdit;
    private CheckBox boldBox, italicBox, underlineBox, strikeBox;
    private Button primaryColorButton, secondaryColorButton, outlineColorButton, backColorButton;
    private StrokePreviewView preview;
    private boolean loaded = false, bindingValues = false;
    private int primaryArgb = Color.WHITE, secondaryArgb = Color.RED, outlineArgb = Color.BLACK, backArgb = 0x80000000;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Intent in = getIntent();
        try { sourceUri = Uri.parse(in.getStringExtra(EXTRA_URI)); } catch (Exception ignored) { }
        try {
            String ft = in.getStringExtra(EXTRA_FONTS_URI);
            if (ft != null && ft.length() > 0) fontsTreeUri = Uri.parse(ft);
        } catch (Exception ignored) { }
        sourceName = in.getStringExtra(EXTRA_NAME) == null ? "subtitle.ass" : in.getStringExtra(EXTRA_NAME);
        currentSourceKey = sourceUri == null ? sourceName : sourceUri.toString();
        showLoading();
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (sourceUri == null) throw new IOException("No subtitle file was supplied to the editor.");
                    sourceText = readSource(sourceUri);
                    workingText = sourceText;
                    sourceFormat = SubtitleStyleEngine.detectFormat(sourceName, sourceText);
                    if (!(SubtitleStyleEngine.ASS.equals(sourceFormat) || SubtitleStyleEngine.SSA.equals(sourceFormat) || SubtitleStyleEngine.VTT.equals(sourceFormat)))
                        throw new IOException("Unsupported subtitle format. Choose ASS, SSA or VTT. The original file was not modified.");
                    if (SubtitleStyleEngine.ASS.equals(sourceFormat) || SubtitleStyleEngine.SSA.equals(sourceFormat)) {
                        styleNames = SubtitleStyleEngine.styleNames(sourceText);
                        if (styleNames.isEmpty()) throw new IOException("No editable ASS/SSA styles were found. The file may be malformed or have no Style definitions; the original remains unchanged.");
                        selectedStyle = prefs.getString(key("selected_style"), styleNames.get(0));
                        if (!styleNames.contains(selectedStyle)) selectedStyle = styleNames.get(0);
                    } else {
                        styleNames = new ArrayList<String>(); styleNames.add("WebVTT cue style"); selectedStyle = styleNames.get(0);
                    }
                    fontNames = scanFonts(fontsTreeUri);
                    runOnUiThread(new Runnable() { public void run() { buildEditor(); loadValuesFromSource(); } });
                } catch (final Exception ex) {
                    runOnUiThread(new Runnable() { public void run() {
                        new AlertDialog.Builder(SubtitleStyleEditorActivity.this).setTitle("Cannot edit subtitle")
                                .setMessage(message(ex) + "\n\nThe source file has not been changed.")
                                .setPositiveButton("Close", (d, w) -> finish()).setOnCancelListener(d -> finish()).show();
                    }});
                }
            }
        }, "SubVoryn-StyleLoad").start();
    }

    private void showLoading() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(24), dp(32), dp(24), dp(32));
        TextView t = text("SUBVORYN · STYLE LAB", 18, TEXT, true); t.setGravity(Gravity.CENTER); box.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView d = text("Reading subtitle through Android's document provider…", 13, MUTED, false); d.setGravity(Gravity.CENTER); box.addView(d, margin(-1, dp(50), 0, 0));
        scroll.addView(box); setContentView(scroll);
    }

    private void buildEditor() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16), dp(14), dp(16), dp(18));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("SV", 17, TEXT, true); mark.setGravity(Gravity.CENTER); mark.setBackgroundDrawable(round(RAISED, BORDER, 15)); header.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.setPadding(dp(12), 0, 0, 0);
        titles.addView(text("Subtitle Style Editor", 21, TEXT, true)); titles.addView(text("ASS · SSA · WEBVTT", 10, ACCENT, true)); header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(header, margin(-1, -2, 0, 14));
        LinearLayout sourceCard = card(); sourceCard.addView(section("01  SUBTITLE FILE & STYLE"), margin(-1, -2, 0, 7));
        subtitleInfo = text(sourceName + " · " + sourceFormat.toUpperCase(Locale.ROOT), 12, MUTED, false); sourceCard.addView(subtitleInfo, margin(-1, -2, 0, 9));
        if (SubtitleStyleEngine.ASS.equals(sourceFormat) || SubtitleStyleEngine.SSA.equals(sourceFormat)) {
            sourceCard.addView(text("NAMED STYLE", 10, ACCENT, true), margin(-1, -2, 0, 4));
            styleSpinner = spinner(styleNames.toArray(new String[0])); sourceCard.addView(styleSpinner, margin(-1, dp(46), 0, 5));
            styleSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { if (loaded && !bindingValues) { try { workingText = SubtitleStyleEngine.editAssStyle(workingText, selectedStyle, collectValues()); } catch (Exception ex) { status.setText("Previous style edits could not be applied: " + message(ex)); return; } selectedStyle = styleNames.get(pos); prefs.edit().putString(key("selected_style"), selectedStyle).apply(); loadValuesFromSource(); } }
                public void onNothingSelected(android.widget.AdapterView<?> p) { }
            });
        } else sourceCard.addView(text("VTT styling uses a portable STYLE block; rendering-only stroke/shadow are applied by SubVoryn's hardsub path.", 11, MUTED, false), margin(-1, -2, 0, 4));
        status = text("Editing a safe working copy. The imported original will remain unchanged.", 11, ACCENT, false); sourceCard.addView(status, margin(-1, -2, 0, 0));
        content.addView(sourceCard, margin(-1, -2, 0, 11));

        LinearLayout fontCard = card(); fontCard.addView(section("02  FONT SETTINGS"), margin(-1, -2, 0, 7));
        fontCard.addView(text("FONT FAMILY", 10, ACCENT, true), margin(-1, -2, 0, 4));
        fontSpinner = spinner(fontOptionsForUi()); fontCard.addView(fontSpinner, margin(-1, dp(46), 0, 7));
        fontFolderStatus = text(fontsTreeUri == null ? "No custom font folder selected; system fonts will be used." : "Font folder: " + treeFolderName(fontsTreeUri) + " · " + fontNames.size() + " supported .ttf/.otf file label(s) discovered.", 11, fontsTreeUri != null && !fontNames.isEmpty() ? ACCENT : MUTED, false);
        fontCard.addView(fontFolderStatus, margin(-1, -2, 0, 6));
        fontCard.addView(text("File names are not always the embedded family name; verify the final FFmpeg render.", 11, MUTED, false), margin(-1, -2, 0, 6));
        if (!fontNames.isEmpty()) {
            Button foundFonts = button("Show detected fonts (" + fontNames.size() + ")", false);
            foundFonts.setOnClickListener(v -> chooseDetectedFont()); fontCard.addView(foundFonts, fullWidth());
        }
        fontCard.addView(text("FONT SIZE", 10, ACCENT, true), margin(-1, 8, 0, 4)); sizeEdit = numberInput("42", true); fontCard.addView(sizeEdit, fullWidth());
        LinearLayout toggles = new LinearLayout(this); toggles.setOrientation(LinearLayout.VERTICAL);
        boldBox = checkbox("Bold"); italicBox = checkbox("Italic"); underlineBox = checkbox("Underline"); strikeBox = checkbox("Strike-through");
        toggles.addView(boldBox); toggles.addView(italicBox); toggles.addView(underlineBox); toggles.addView(strikeBox); fontCard.addView(toggles, margin(-1, 5, 0, 0));
        content.addView(fontCard, margin(-1, -2, 0, 11));

        LinearLayout colorCard = card(); colorCard.addView(section("03  TEXT & COLOR"), margin(-1, -2, 0, 7));
        primaryColorButton = colorButton("Primary Colour", Color.WHITE); colorCard.addView(primaryColorButton, fullWidth());
        secondaryColorButton = colorButton("Secondary / Karaoke Colour", Color.RED); colorCard.addView(secondaryColorButton, margin(-1, 7, 0, 0));
        content.addView(colorCard, margin(-1, -2, 0, 11));

        LinearLayout strokeCard = card(); strokeCard.addView(section("04  STROKE & SHADOW"), margin(-1, -2, 0, 7));
        outlineColorButton = colorButton("Outline / Stroke Colour", Color.BLACK); strokeCard.addView(outlineColorButton, fullWidth());
        backColorButton = colorButton("Shadow / Background Colour", 0x80000000); strokeCard.addView(backColorButton, margin(-1, 7, 0, 7));
        outlineEdit = numberInput("2.0", true); addStepper(strokeCard, "Outline / Stroke", outlineEdit, 0, 20, 0.5);
        shadowEdit = numberInput("1.0", true); addStepper(strokeCard, "Shadow Depth", shadowEdit, 0, 20, 0.5);
        spacingEdit = numberInput("0", true); addStepper(strokeCard, "Character Spacing", spacingEdit, 0, 40, 0.25);
        strokeCard.addView(text("BORDER STYLE", 10, ACCENT, true), margin(-1, 8, 0, 4)); borderSpinner = spinner(new String[] {"Outline + shadow", "Opaque box"}); strokeCard.addView(borderSpinner, margin(-1, dp(46), 0, 0));
        content.addView(strokeCard, margin(-1, -2, 0, 11));

        LinearLayout positionCard = card(); positionCard.addView(section("05  POSITION & MARGINS"), margin(-1, -2, 0, 7));
        positionCard.addView(text("ALIGNMENT", 10, ACCENT, true), margin(-1, -2, 0, 4));
        alignmentSpinner = spinner(new String[] {"Bottom Left (1)", "Bottom Center (2)", "Bottom Right (3)", "Middle Left (4)", "Middle Center (5)", "Middle Right (6)", "Top Left (7)", "Top Center (8)", "Top Right (9)"});
        positionCard.addView(alignmentSpinner, margin(-1, dp(46), 0, 7));
        marginLEdit = numberInput("20", false); positionCard.addView(labeled("Margin Left", marginLEdit), margin(-1, -2, 0, 6));
        marginREdit = numberInput("20", false); positionCard.addView(labeled("Margin Right", marginREdit), margin(-1, -2, 0, 6));
        marginVEdit = numberInput("24", false); positionCard.addView(labeled("Margin Vertical", marginVEdit), margin(-1, -2, 0, 0));
        if (SubtitleStyleEngine.VTT.equals(sourceFormat)) positionCard.addView(text("VTT cue positioning is preserved. Alignment/margins here target SubVoryn's hardsub renderer; portable WebVTT players may use the original cue settings.", 11, MUTED, false), margin(-1, 8, 0, 0));
        content.addView(positionCard, margin(-1, -2, 0, 11));

        LinearLayout previewCard = card(); previewCard.addView(section("06  LIVE PREVIEW"), margin(-1, -2, 0, 7));
        preview = new StrokePreviewView(this); preview.setBackgroundDrawable(round(Color.rgb(10, 12, 18), BORDER, 13)); previewCard.addView(preview, margin(-1, dp(172), 0, 7));
        previewCard.addView(text("Preview approximates the selected style. Final appearance may differ slightly in FFmpeg/libass, especially for custom fonts and inline ASS overrides.", 11, MUTED, false));
        overrideWarning = text("", 11, ACCENT, false); previewCard.addView(overrideWarning, margin(-1, 7, 0, 0));
        content.addView(previewCard, margin(-1, -2, 0, 11));

        LinearLayout presetsCard = card(); presetsCard.addView(section("07  PRESETS"), margin(-1, -2, 0, 7));
        String[] builtins = {"Default", "Anime Bold", "Clean White", "Yellow Outline", "Minimal", "High Contrast"};
        Spinner presetSpinner = spinner(builtins); presetsCard.addView(presetSpinner, margin(-1, dp(46), 0, 7));
        Button loadPreset = button("Load Selected Preset", false); loadPreset.setOnClickListener(v -> applyPreset((String)presetSpinner.getSelectedItem())); presetsCard.addView(loadPreset, fullWidth());
        Button customPreset = button("Load Saved Custom Preset", false); customPreset.setOnClickListener(v -> loadCustomPreset()); presetsCard.addView(customPreset, margin(-1, 7, 0, 0));
        Button savePreset = button("Save Custom Preset", false); savePreset.setOnClickListener(v -> saveCustomPreset()); presetsCard.addView(savePreset, margin(-1, 7, 0, 0));
        content.addView(presetsCard, margin(-1, -2, 0, 11));

        LinearLayout actions = card(); actions.addView(section("08  APPLY & EXPORT"), margin(-1, -2, 0, 7));
        Button previewButton = button("Refresh Live Preview", false); previewButton.setOnClickListener(v -> updatePreview()); actions.addView(previewButton, fullWidth());
        Button reset = button("Reset Current Changes", false); reset.setOnClickListener(v -> { workingText = sourceText; loadValuesFromSource(); status.setText("Changes reset to the source style. Original file unchanged."); }); actions.addView(reset, margin(-1, 7, 0, 0));
        Button apply = button("Apply Changes to Encoding", true); apply.setOnClickListener(v -> applyToEncoding()); actions.addView(apply, margin(-1, 7, 0, 0));
        Button saveAs = button("Save Styled Subtitle As New File", false); saveAs.setOnClickListener(v -> saveAsNewFile()); actions.addView(saveAs, margin(-1, 7, 0, 0));
        content.addView(actions, margin(-1, -2, 0, 11));

        LinearLayout bottom = new LinearLayout(this); bottom.setPadding(dp(12), dp(8), dp(12), dp(8)); bottom.setBackgroundColor(PANEL);
        Button back = button("Back", false); back.setOnClickListener(v -> finish()); bottom.addView(back, new LinearLayout.LayoutParams(0, dp(44), 1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(60))); setContentView(root);
        if (styleSpinner != null) styleSpinner.setSelection(Math.max(0, styleNames.indexOf(selectedStyle)));
        attachChanges();
        loaded = true;
    }

    private void attachChanges() {
        TextWatcher watcher = new TextWatcher() { public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){ updatePreview(); } public void afterTextChanged(Editable e){} };
        for (EditText e : new EditText[]{sizeEdit, outlineEdit, shadowEdit, spacingEdit, marginLEdit, marginREdit, marginVEdit}) e.addTextChangedListener(watcher);
        View.OnClickListener toggle = v -> updatePreview();
        boldBox.setOnClickListener(toggle); italicBox.setOnClickListener(toggle); underlineBox.setOnClickListener(toggle); strikeBox.setOnClickListener(toggle);
        fontSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() { public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){ if(!bindingValues) updatePreview(); } public void onNothingSelected(android.widget.AdapterView<?> p){} });
        alignmentSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() { public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){ if(!bindingValues) updatePreview(); } public void onNothingSelected(android.widget.AdapterView<?> p){} });
        borderSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() { public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){ if(!bindingValues) updatePreview(); } public void onNothingSelected(android.widget.AdapterView<?> p){} });
    }

    private void loadValuesFromSource() {
        if (sourceText == null || sourceText.length() == 0) return;
        bindingValues = true;
        values.clear();
        if (SubtitleStyleEngine.VTT.equals(sourceFormat)) values.putAll(SubtitleStyleEngine.defaultStyle());
        else for (Map.Entry<String,String> e : SubtitleStyleEngine.defaultStyle().entrySet()) {
            String actual = SubtitleStyleEngine.styleValue(workingText, selectedStyle, e.getKey());
            values.put(e.getKey(), actual.length() == 0 ? e.getValue() : actual);
        }
        setSpinnerToValue(fontSpinner, value("Fontname", "Roboto"));
        setText(sizeEdit, value("Fontsize", "42")); setText(outlineEdit, value("Outline", "2")); setText(shadowEdit, value("Shadow", "1")); setText(spacingEdit, value("Spacing", "0"));
        setText(marginLEdit, value("MarginL", "20")); setText(marginREdit, value("MarginR", "20")); setText(marginVEdit, value("MarginV", "24"));
        boldBox.setChecked(isTrue(value("Bold","0"))); italicBox.setChecked(isTrue(value("Italic","0"))); underlineBox.setChecked(isTrue(value("Underline","0"))); strikeBox.setChecked(isTrue(value("StrikeOut","0")));
        setSpinnerToValue(borderSpinner, "3".equals(value("BorderStyle","1")) ? "Opaque box" : "Outline + shadow");
        setSpinnerToValue(alignmentSpinner, alignmentLabel(value("Alignment","2")));
        primaryArgb = SubtitleStyleEngine.assColorToArgb(value("PrimaryColour","&H00FFFFFF"));
        secondaryArgb = SubtitleStyleEngine.assColorToArgb(value("SecondaryColour","&H000000FF"));
        outlineArgb = SubtitleStyleEngine.assColorToArgb(value("OutlineColour","&H00000000"));
        backArgb = SubtitleStyleEngine.assColorToArgb(value("BackColour","&H80000000"));
        updateColorButton(primaryColorButton, "Primary Colour", primaryArgb); updateColorButton(secondaryColorButton,"Secondary / Karaoke Colour",secondaryArgb);
        updateColorButton(outlineColorButton,"Outline / Stroke Colour",outlineArgb); updateColorButton(backColorButton,"Shadow / Background Colour",backArgb);
        if (overrideWarning != null) overrideWarning.setText((SubtitleStyleEngine.ASS.equals(sourceFormat)||SubtitleStyleEngine.SSA.equals(sourceFormat)) && SubtitleStyleEngine.hasInlineAppearanceOverrides(sourceText)
                ? "Some dialogue has inline ASS overrides (font, size, colour, border or alignment). Those overrides are preserved and may supersede the selected named style." : "");
        bindingValues = false;
        updatePreview();
    }

    private void applyToEncoding() {
        try {
            String output = buildOutputText();
            File dir = new File(getCacheDir(), "subvoryn-style-editor");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create the private style workspace.");
            String base = sourceName.replaceAll("(?i)\\.(ass|ssa|vtt)$", "").replaceAll("[^\\p{L}\\p{N}._-]+", "_");
            File destination = new File(dir, (base.length()==0?"subtitle":base) + "-styled." + sourceFormat);
            writeUtf8(destination, output);
            persistEffectiveStyle();
            Intent result = new Intent(); result.putExtra(EXTRA_STYLED_PATH, destination.getAbsolutePath()); result.putExtra(EXTRA_SOURCE_URI, sourceUri.toString()); result.putExtra(EXTRA_FORMAT, sourceFormat);
            setResult(RESULT_OK, result);
            status.setText("Styled subtitle applied to this encoding session. Original imported file remains unchanged.");
            Toast.makeText(this, "Style applied to encoding.", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) { showError("Could not apply style", e); }
    }

    private String buildOutputText() {
        Map<String,String> m = collectValues();
        if (SubtitleStyleEngine.ASS.equals(sourceFormat) || SubtitleStyleEngine.SSA.equals(sourceFormat)) return SubtitleStyleEngine.editAssStyle(workingText, selectedStyle, m);
        return SubtitleStyleEngine.editVttStyle(workingText, m);
    }

    private Map<String,String> collectValues() {
        Map<String,String> m = new LinkedHashMap<String,String>();
        m.put("Fontname", String.valueOf(fontSpinner.getSelectedItem())); m.put("Fontsize", checkedNumber(sizeEdit, "Font size", 8, 200));
        m.put("PrimaryColour", SubtitleStyleEngine.argbToAssColor(primaryArgb)); m.put("SecondaryColour", SubtitleStyleEngine.argbToAssColor(secondaryArgb));
        m.put("OutlineColour", SubtitleStyleEngine.argbToAssColor(outlineArgb)); m.put("BackColour", SubtitleStyleEngine.argbToAssColor(backArgb));
        m.put("Bold", boldBox.isChecked()?"-1":"0"); m.put("Italic", italicBox.isChecked()?"-1":"0");
        m.put("Underline", underlineBox.isChecked()?"-1":"0"); m.put("StrikeOut", strikeBox.isChecked()?"-1":"0");
        m.put("Spacing", checkedNumber(spacingEdit, "Character spacing", 0, 40)); m.put("BorderStyle", borderSpinner.getSelectedItemPosition()==1?"3":"1");
        m.put("Outline", checkedNumber(outlineEdit, "Outline thickness", 0, 20)); m.put("Shadow", checkedNumber(shadowEdit, "Shadow depth", 0, 20));
        m.put("Alignment", String.valueOf(alignmentSpinner.getSelectedItemPosition()+1));
        m.put("MarginL", checkedInteger(marginLEdit, "Left margin", 0, 100000)); m.put("MarginR", checkedInteger(marginREdit, "Right margin", 0, 100000)); m.put("MarginV", checkedInteger(marginVEdit, "Vertical margin", 0, 100000));
        return m;
    }

    private void persistEffectiveStyle() {
        Map<String,String> m=collectValues(); SharedPreferences.Editor e=prefs.edit();
        e.putString(key("source_uri"), currentSourceKey).putString(key("source_format"),sourceFormat);
        for(Map.Entry<String,String> row:m.entrySet()) e.putString(key(row.getKey()),row.getValue());
        e.apply();
    }

    private String key(String k) { return "last_" + k; }
    private String value(String k,String d) { String x=values.get(k); return x==null||x.length()==0?d:x; }

    private void saveAsNewFile() {
        try {
            pendingSaveText = buildOutputText();
            String mime = SubtitleStyleEngine.VTT.equals(sourceFormat) ? "text/vtt" : "text/plain";
            String base = sourceName.replaceAll("(?i)\\.(ass|ssa|vtt)$", "");
            String name = base + "-styled." + sourceFormat;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(mime);
            i.putExtra(Intent.EXTRA_TITLE, name); startActivityForResult(i, REQ_SAVE_AS);
        } catch (Exception e) { showError("Could not prepare styled subtitle", e); }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQ_SAVE_AS) return;
        if(resultCode!=RESULT_OK || data==null || data.getData()==null) { status.setText("Save cancelled; no file was written."); return; }
        Uri target=data.getData();
        new Thread(() -> {
            String error=null;
            try { OutputStream out=getContentResolver().openOutputStream(target,"w"); if(out==null) throw new IOException("Cannot write to selected document."); try { out.write(pendingSaveText.getBytes(StandardCharsets.UTF_8)); out.flush(); } finally { out.close(); } }
            catch(Exception ex){error=message(ex);}
            final String err=error;
            runOnUiThread(() -> { if(err==null){status.setText("Styled copy saved. Original subtitle was not overwritten."); Toast.makeText(this,"Styled subtitle saved.",Toast.LENGTH_LONG).show();} else showMessage("Save failed",err); });
        },"SubVoryn-StyleSave").start();
    }

    private void applyPreset(String preset) {
        Map<String,String> m = SubtitleStyleEngine.builtinPreset(preset);
        if (m == null) { showMessage("Preset not found", "Choose one of the built-in presets or load a saved custom preset."); return; }
        bindMap(m);
        status.setText(preset + " preset loaded. Tap Apply or Save As to use it.");
    }

    private void saveCustomPreset() {
        EditText name=new EditText(this); name.setSingleLine(true); name.setHint("Preset name"); name.setTextColor(TEXT); name.setHintTextColor(MUTED);
        new AlertDialog.Builder(this).setTitle("Save custom preset").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String n=name.getText().toString().trim(); if(n.length()==0){toast("Enter a preset name.");return;}
            try { JSONObject all=new JSONObject(prefs.getString("custom_presets","{}")); JSONObject v=new JSONObject(); for(Map.Entry<String,String> e:collectValues().entrySet())v.put(e.getKey(),e.getValue()); all.put(n,v); prefs.edit().putString("custom_presets",all.toString()).apply(); toast("Preset saved: "+n); }
            catch(Exception e){showError("Could not save preset",e);}
        }).show();
    }

    private void loadCustomPreset() {
        try {
            JSONObject all=new JSONObject(prefs.getString("custom_presets","{}")); List<String> names=new ArrayList<String>(); java.util.Iterator<String> it=all.keys(); while(it.hasNext())names.add(it.next());
            if(names.isEmpty()){showMessage("No saved presets","Save a custom preset first.");return;}
            String[] list=names.toArray(new String[0]); new AlertDialog.Builder(this).setTitle("Load saved preset").setItems(list,(d,which)->{try{JSONObject obj=all.getJSONObject(list[which]);Map<String,String> m=new LinkedHashMap<String,String>();java.util.Iterator<String> k=obj.keys();while(k.hasNext()){String key=k.next();m.put(key,obj.getString(key));}bindMap(m);status.setText("Custom preset loaded: "+list[which]);}catch(Exception ex){showError("Preset could not be loaded",ex);}}).show();
        } catch(Exception e){showError("Could not read presets",e);}
    }

    private void bindMap(Map<String,String> m) {
        bindingValues=true;
        if(m.containsKey("Fontname"))setSpinnerToValue(fontSpinner,m.get("Fontname"));
        setText(sizeEdit,m.getOrDefault("Fontsize","42"));setText(outlineEdit,m.getOrDefault("Outline","2"));setText(shadowEdit,m.getOrDefault("Shadow","1"));setText(spacingEdit,m.getOrDefault("Spacing","0"));
        setText(marginLEdit,m.getOrDefault("MarginL","20"));setText(marginREdit,m.getOrDefault("MarginR","20"));setText(marginVEdit,m.getOrDefault("MarginV","24"));
        boldBox.setChecked(isTrue(m.getOrDefault("Bold","0")));italicBox.setChecked(isTrue(m.getOrDefault("Italic","0")));underlineBox.setChecked(isTrue(m.getOrDefault("Underline","0")));strikeBox.setChecked(isTrue(m.getOrDefault("StrikeOut","0")));
        setSpinnerToValue(borderSpinner,"3".equals(m.getOrDefault("BorderStyle","1"))?"Opaque box":"Outline + shadow");setSpinnerToValue(alignmentSpinner,alignmentLabel(m.getOrDefault("Alignment","2")));
        primaryArgb=SubtitleStyleEngine.assColorToArgb(m.getOrDefault("PrimaryColour","&H00FFFFFF"));secondaryArgb=SubtitleStyleEngine.assColorToArgb(m.getOrDefault("SecondaryColour","&H000000FF"));outlineArgb=SubtitleStyleEngine.assColorToArgb(m.getOrDefault("OutlineColour","&H00000000"));backArgb=SubtitleStyleEngine.assColorToArgb(m.getOrDefault("BackColour","&H80000000"));
        updateColorButton(primaryColorButton,"Primary Colour",primaryArgb);updateColorButton(secondaryColorButton,"Secondary / Karaoke Colour",secondaryArgb);updateColorButton(outlineColorButton,"Outline / Stroke Colour",outlineArgb);updateColorButton(backColorButton,"Shadow / Background Colour",backArgb);
        bindingValues=false;updatePreview();
    }

    private void updatePreview() {
        if(preview==null || !loaded)return;
        try {Map<String,String> m=collectValues(); preview.setStyle(String.valueOf(fontSpinner.getSelectedItem()),number(sizeEdit,42,8,200),primaryArgb,outlineArgb,backArgb,number(outlineEdit,2,0,20),number(shadowEdit,1,0,20),boldBox.isChecked(),italicBox.isChecked(),underlineBox.isChecked(),strikeBox.isChecked(),number(spacingEdit,0,0,40),alignmentSpinner==null?2:alignmentSpinner.getSelectedItemPosition()+1);}
        catch(Exception ignored){}
    }

    private void openColorDialog(String key,String title) {
        int current="PrimaryColour".equals(key)?primaryArgb:"SecondaryColour".equals(key)?secondaryArgb:"OutlineColour".equals(key)?outlineArgb:backArgb;
        EditText input=new EditText(this); input.setSingleLine(true); input.setInputType(EditorInfo.TYPE_CLASS_TEXT); input.setHint("#RRGGBB or #AARRGGBB"); input.setText(String.format(Locale.ROOT,"#%08X",current)); input.setTextColor(TEXT); input.setHintTextColor(MUTED);
        new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{
            try {String x=input.getText().toString().trim().replace("#",""); long n=Long.parseLong(x,16); int c; if(x.length()==6)c=(int)(0xFF000000L|n); else if(x.length()==8)c=(int)n; else throw new IllegalArgumentException("Use 6 or 8 hexadecimal digits.");
                if("PrimaryColour".equals(key))primaryArgb=c;else if("SecondaryColour".equals(key))secondaryArgb=c;else if("OutlineColour".equals(key))outlineArgb=c;else backArgb=c;
                updateColorButton(primaryColorButton,"Primary Colour",primaryArgb);updateColorButton(secondaryColorButton,"Secondary / Karaoke Colour",secondaryArgb);updateColorButton(outlineColorButton,"Outline / Stroke Colour",outlineArgb);updateColorButton(backColorButton,"Shadow / Background Colour",backArgb);updatePreview();
            }catch(Exception ex){showError("Invalid colour",ex);}
        }).show();
    }

    private void chooseDetectedFont() {
        if(fontNames.isEmpty()){showMessage("No custom fonts found","Select a font folder from Encoding first. Only accessible .ttf and .otf files are listed.");return;}
        String[] names=fontNames.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("Detected font files").setItems(names,(d,which)->{String chosen=names[which];setSpinnerToValue(fontSpinner,chosen);status.setText("Selected font file: "+chosen+". Its internal family name may differ; verify the final FFmpeg output.");updatePreview();}).show();
    }

    private List<String> scanFonts(Uri tree) {
        List<String> result=new ArrayList<String>(); if(tree==null)return result;
        try {scanFontsRecursive(tree,DocumentsContract.getTreeDocumentId(tree),0,new int[]{0},result);}catch(Exception ignored){}
        return result;
    }
    private void scanFontsRecursive(Uri tree,String docId,int depth,int[] visited,List<String> out) throws Exception {
        if(depth>7||visited[0]>700)return;Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,docId);Cursor c=null;
        try{c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null);if(c==null)return;
            while(c.moveToNext()&&visited[0]++<700){String id=c.getString(0),name=c.getString(1),mime=c.getString(2);if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))scanFontsRecursive(tree,id,depth+1,visited,out);else if(name!=null&&(name.toLowerCase(Locale.ROOT).endsWith(".ttf")||name.toLowerCase(Locale.ROOT).endsWith(".otf"))&&!out.contains(name.substring(0,name.lastIndexOf('.'))))out.add(name.substring(0,name.lastIndexOf('.'));}
        }finally{if(c!=null)c.close();}
    }

    private String readSource(Uri uri) throws Exception {
        InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new IOException("Android could not open the selected subtitle document.");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        try{while((n=in.read(buffer))!=-1){if(out.size()+n>MAX_SOURCE_BYTES)throw new IOException("Subtitle exceeds the 20 MB editor safety limit.");out.write(buffer,0,n);}}finally{in.close();}
        return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
    private String buildOutputText() {Map<String,String> m=collectValues();if(SubtitleStyleEngine.VTT.equals(sourceFormat))return SubtitleStyleEngine.editVttStyle(sourceText,m);return SubtitleStyleEngine.editAssStyle(sourceText,selectedStyle,m);}
    private void writeUtf8(File file,String text)throws IOException {FileOutputStream out=new FileOutputStream(file,false);try{out.write(text.getBytes(StandardCharsets.UTF_8));out.flush();}finally{out.close();}if(!file.isFile()||file.length()==0)throw new IOException("Styled working file was not written.");}

    private void addStepper(LinearLayout parent,String title,EditText edit,double min,double max,double step) {
        parent.addView(text(title.toUpperCase(Locale.ROOT),10,ACCENT,true),margin(-1,7,0,4));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(edit,new LinearLayout.LayoutParams(0,dp(44),1));Button minus=button("−",false),plus=button("+",false);row.addView(minus,margin(dp(48),dp(44),7,0));row.addView(plus,margin(dp(48),dp(44),7,0));
        minus.setOnClickListener(v->step(edit,-step,min,max));plus.setOnClickListener(v->step(edit,step,min,max));parent.addView(row,margin(-1,-2,0,3));
    }
    private void step(EditText e,double delta,double min,double max){double n=number(e,min,min,max);setText(e,formatNum(Math.max(min,Math.min(max,n+delta))));updatePreview();}
    private void setText(EditText e,String value){if(e!=null)e.setText(value);}
    private EditText numberInput(String hint,boolean decimal){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(14);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setHint(hint);e.setPadding(dp(11),dp(6),dp(11),dp(6));e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|(decimal?android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED:0));e.setBackgroundDrawable(round(RAISED,BORDER,11));return e;}
    private double number(EditText e,double fallback,double min,double max){try{double n=Double.parseDouble(e.getText().toString().trim());if(Double.isNaN(n)||Double.isInfinite(n))return fallback;return Math.max(min,Math.min(max,n));}catch(Exception ex){return fallback;}}
    private String numeric(EditText e,double fallback,double min,double max){return formatNum(number(e,fallback,min,max));}
    private String checkedNumber(EditText e,String label,double min,double max){try{double n=Double.parseDouble(e.getText().toString().trim());if(Double.isNaN(n)||Double.isInfinite(n)||n<min||n>max)throw new IllegalArgumentException(label+" must be between "+min+" and "+max+".");return formatNum(n);}catch(NumberFormatException ex){throw new IllegalArgumentException("Enter a valid number for "+label+".");}}
    private String checkedInteger(EditText e,String label,int min,int max){try{String raw=e.getText().toString().trim();if(!raw.matches("[0-9]+"))throw new IllegalArgumentException(label+" must be a whole number.");int n=Integer.parseInt(raw);if(n<min||n>max)throw new IllegalArgumentException(label+" must be between "+min+" and "+max+".");return String.valueOf(n);}catch(NumberFormatException ex){throw new IllegalArgumentException(label+" is out of range.");}}
    private String treeFolderName(Uri tree){try{String id=DocumentsContract.getTreeDocumentId(tree);int colon=id.indexOf(':');String path=colon>=0?id.substring(colon+1):id;int slash=path.lastIndexOf('/');String name=slash>=0?path.substring(slash+1):path;return name.length()==0?"selected folder":name;}catch(Exception e){return "selected folder";}}
    private String formatNum(double n){return n==Math.rint(n)?String.valueOf((long)n):String.format(Locale.ROOT,"%.2f",n).replaceAll("0+$","").replaceAll("\\.$","");}
    private String[] fontOptionsForUi(){
        List<String> options=new ArrayList<String>(Arrays.asList("Arial","Roboto","Roboto Bk","Noto Sans","sans-serif","serif","monospace"));
        for(String f:fontNames)if(!options.contains(f))options.add(f);
        String current=SubtitleStyleEngine.ASS.equals(sourceFormat)||SubtitleStyleEngine.SSA.equals(sourceFormat)?SubtitleStyleEngine.styleValue(sourceText,selectedStyle,"Fontname"):prefs.getString(key("Fontname"),"Roboto");
        if(current!=null&&current.trim().length()>0&&!options.contains(current))options.add(current);
        return options.toArray(new String[0]);
    }
    private void setSpinnerToValue(Spinner sp,String value){
        if(sp==null||value==null)return;
        for(int i=0;i<sp.getCount();i++)if(String.valueOf(sp.getItemAtPosition(i)).equalsIgnoreCase(value)||String.valueOf(sp.getItemAtPosition(i)).startsWith(value+" ")){sp.setSelection(i);return;}
        if(sp==fontSpinner){List<String> choices=new ArrayList<String>();for(int i=0;i<sp.getCount();i++)choices.add(String.valueOf(sp.getItemAtPosition(i)));choices.add(value);ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,choices);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);sp.setAdapter(a);sp.setSelection(choices.size()-1);}
    }
    private boolean isTrue(String s){return "1".equals(s)||"-1".equals(s)||"true".equalsIgnoreCase(s);}
    private String alignmentLabel(String s){int i=2;try{i=Integer.parseInt(s);}catch(Exception ignored){}i=Math.max(1,Math.min(9,i));String[] labels={"Bottom Left (1)","Bottom Center (2)","Bottom Right (3)","Middle Left (4)","Middle Center (5)","Middle Right (6)","Top Left (7)","Top Center (8)","Top Right (9)"};return labels[i-1];}

    private void updateColorButton(Button b,String label,int color){if(b==null)return;b.setText(label+"   "+String.format(Locale.ROOT,"#%08X",color));GradientDrawable d=round(RAISED,BORDER,12);d.setStroke(dp(1),ACCENT);b.setBackgroundDrawable(d);GradientDrawable swatch=new GradientDrawable();swatch.setColor(color);swatch.setCornerRadius(dp(5));swatch.setStroke(dp(1),TEXT);swatch.setBounds(0,0,dp(24),dp(24));b.setCompoundDrawablePadding(dp(8));b.setCompoundDrawables(swatch,null,null,null);b.setOnClickListener(v->openColorDialog(label.startsWith("Primary")?"PrimaryColour":label.startsWith("Secondary")?"SecondaryColour":label.startsWith("Outline")?"OutlineColour":"BackColour",label));b.setContentDescription(label+", current colour "+String.format(Locale.ROOT,"#%08X",color));}
    private Button colorButton(String label,int color){Button b=button(label,false);updateColorButton(b,label,color);return b;}
    private CheckBox checkbox(String label){CheckBox b=new CheckBox(this);b.setText(label);b.setTextColor(TEXT);b.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));return b;}
    private LinearLayout labeled(String label,EditText input){LinearLayout box=new LinearLayout(this);box.setGravity(Gravity.CENTER_VERTICAL);TextView t=text(label,12,TEXT,false);box.addView(t,new LinearLayout.LayoutParams(0,dp(44),1));box.addView(input,new LinearLayout.LayoutParams(dp(104),dp(44)));return box;}
    private Spinner spinner(String[] options){Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,options);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setBackgroundDrawable(round(RAISED,BORDER,11));return s;}
    private Button button(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?Color.rgb(25,20,42):TEXT);b.setPadding(dp(9),dp(6),dp(9),dp(6));b.setBackgroundDrawable(round(primary?ACCENT:RAISED,primary?ACCENT:BORDER,13));b.setMinHeight(dp(44));return b;}
    private TextView section(String s){return text(s,10,ACCENT,true);}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(14),dp(14),dp(14));c.setBackgroundDrawable(round(PANEL,BORDER,19));c.setElevation(dp(2));return c;}
    private GradientDrawable round(int color,int border,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),border);return d;}
    private LinearLayout.LayoutParams margin(int w,int h,int left,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.leftMargin=dp(left);p.bottomMargin=dp(bottom);return p;}
    private LinearLayout.LayoutParams fullWidth(){return new LinearLayout.LayoutParams(-1,dp(46));}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private String message(Throwable e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void showError(String title,Throwable e){showMessage(title,message(e));}
    private void showMessage(String title,String body){new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("OK",null).show();}

    private final class StrokePreviewView extends View {
        private final String sample="Main tumhe kabhi chhodunga nahi.";
        private String family="Roboto"; private float size=42,stroke=2,shadow=1,spacing=0; private int fill=Color.WHITE,edge=Color.BLACK,back=0x80000000,alignment=2; private boolean bold,italic,under,strike;
        StrokePreviewView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setPadding(dp(12),dp(12),dp(12),dp(12));}
        void setStyle(String f,float s,int p,int o,int bg,float st,float sh,boolean b,boolean i,boolean u,boolean x,float sp,int a){family=f;size=s;fill=p;edge=o;back=bg;stroke=st;shadow=sh;bold=b;italic=i;under=u;strike=x;spacing=sp;alignment=Math.max(1,Math.min(9,a));invalidate();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight());int style=(bold?Typeface.BOLD:Typeface.NORMAL)|(italic?Typeface.ITALIC:0);Typeface tf=Typeface.create(family,style);
            TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);p.setTypeface(tf);p.setTextSize(spToPx(size));p.setLetterSpacing(Math.min(.5f,spacing/100.0f));p.setUnderlineText(under);p.setStrikeThruText(strike);
            Layout.Alignment textAlignment=(alignment%3==1)?Layout.Alignment.ALIGN_NORMAL:(alignment%3==0?Layout.Alignment.ALIGN_OPPOSITE:Layout.Alignment.ALIGN_CENTER);
            StaticLayout layout=StaticLayout.Builder.obtain(sample,0,sample.length(),p,(int)w).setAlignment(textAlignment).setIncludePad(false).build();
            int verticalRow=(alignment-1)/3;float y=verticalRow==2?getPaddingTop():(verticalRow==1?(getHeight()-layout.getHeight())/2f:(getHeight()-getPaddingBottom()-layout.getHeight()));
            canvas.save();canvas.translate(getPaddingLeft(),y);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeWidth(stroke*getResources().getDisplayMetrics().density*2f);p.setColor(edge);layout.draw(canvas);
            p.setStyle(Paint.Style.FILL);p.setColor(fill);if(shadow>0)p.setShadowLayer(shadow*getResources().getDisplayMetrics().density,0,shadow*getResources().getDisplayMetrics().density,back);layout.draw(canvas);p.clearShadowLayer();canvas.restore();
        }
        private float spToPx(float v){return v*getResources().getDisplayMetrics().scaledDensity;}
    }
}
