package com.subvoryn.voryn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import java.util.Locale;

/** SubVoryn Settings: persistent defaults plus honest bundled-engine diagnostics. */
public class SettingsActivity extends Activity {
    private static final int C_BG = Color.rgb(14, 19, 26);
    private static final int C_PANEL = Color.rgb(23, 30, 40);
    private static final int C_PANEL_2 = Color.rgb(31, 40, 52);
    private static final int C_BORDER = Color.rgb(57, 72, 91);
    private static final int C_TEXT = Color.rgb(243, 246, 250);
    private static final int C_MUTED = Color.rgb(161, 176, 193);
    private static final int C_ACCENT = Color.rgb(151, 178, 207);
    private static final String RELEASES_URL = "https://github.com/ffmpegkit-maintained/ffmpeg/releases";
    private static final String ENGINE_VERSION = "8.1.9";
    private SharedPreferences prefs;
    private TextView engineStatus, engineDetails, updateStatus, appVersionText, storageText, folderText;
    private Spinner resolution, fps, crf, preset, audio;
    private CheckBox showAdvanced;
    private Button checkButton, updateButton, resetButton;
    private volatile boolean checking = false;
    private volatile boolean checkingUpdates = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(C_BG);
            getWindow().setNavigationBarColor(C_BG);
        }
        prefs = getSharedPreferences(EncodingActivity.PREFS, MODE_PRIVATE);
        buildScreen();
        loadDefaults();
        refreshDiagnostics();
        checkEngine(false);
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(C_BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("SV", 18, C_TEXT, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackgroundDrawable(round(C_PANEL_2, C_BORDER, 13));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setPadding(dp(11), 0, 0, 0);
        stack.addView(text("Settings", 23, C_TEXT, true));
        stack.addView(text("PREFERENCES & DIAGNOSTICS", 10, C_ACCENT, true));
        brand.addView(stack, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(brand, margin(-1, -2, 0, 14));

        LinearLayout engine = card();
        engine.addView(section("FFMPEG ENGINE"), margin(-1, -2, 0, 8));
        engineStatus = text("Checking the bundled FFmpeg runtime…", 14, C_TEXT, true);
        engine.addView(engineStatus, margin(-1, -2, 0, 7));
        engineDetails = text("The engine is packaged inside SubVoryn and checked by an actual -version command.", 12, C_MUTED, false);
        engineDetails.setGravity(Gravity.TOP);
        engine.addView(engineDetails, margin(-1, -2, 0, 10));
        LinearLayout row = new LinearLayout(this);
        checkButton = button("Check Engine", true);
        checkButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { checkEngine(true); } });
        row.addView(checkButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        updateButton = button("Check Updates", false);
        updateButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { checkUpstreamUpdate(); } });
        LinearLayout.LayoutParams updateLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        updateLp.leftMargin = dp(8);
        row.addView(updateButton, updateLp);
        engine.addView(row, fullWidth());
        updateStatus = text("Update status: not checked", 12, C_MUTED, false);
        engine.addView(updateStatus, margin(-1, 9, 0, 8));
        Button releases = button("Open Official Engine Releases", false);
        releases.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openReleases(); } });
        engine.addView(releases, fullWidth());
        Button licenseButton = button("View FFmpeg License Text", false);
        licenseButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showLicensePicker(); } });
        engine.addView(licenseButton, margin(-1, 8, 0, 0));
        engine.addView(text("The native runtime is bundled with the app. Updates are checked against Maven Central metadata, but a new engine is installed only through a reviewed SubVoryn app release; the app does not download/execute an unverified native binary.", 11, C_MUTED, false), margin(-1, 9, 0, 0));
        content.addView(engine, margin(-1, -2, 0, 12));

        LinearLayout defaults = card();
        defaults.addView(section("DEFAULT ENCODING OPTIONS"), margin(-1, -2, 0, 8));
        defaults.addView(text("Output resolution", 11, C_ACCENT, true));
        resolution = spinner(new String[] {"Original Resolution", "480p", "720p", "1080p", "1440p (no upscale)"});
        defaults.addView(resolution, margin(-1, dp(46), 0, 8));
        defaults.addView(text("Frame rate", 11, C_ACCENT, true));
        fps = spinner(new String[] {"Original FPS", "23.976 FPS", "24 FPS", "25 FPS", "29.97 FPS", "30 FPS", "50 FPS", "60 FPS", "Custom FPS"});
        defaults.addView(fps, margin(-1, dp(46), 0, 8));
        defaults.addView(text("Default quality target", 11, C_ACCENT, true));
        crf = spinner(new String[] {"16 — high quality / large", "18", "20", "22", "24 — balanced", "26", "28", "30", "32", "35 — smaller"});
        defaults.addView(crf, margin(-1, dp(46), 0, 8));
        defaults.addView(text("Video preset", 11, C_ACCENT, true));
        preset = spinner(new String[] {"Ultrafast", "Superfast", "Veryfast", "Faster", "Fast", "Medium", "Slow", "Slower", "Veryslow"});
        defaults.addView(preset, margin(-1, dp(46), 0, 8));
        defaults.addView(text("Audio mode / bitrate", 11, C_ACCENT, true));
        audio = spinner(new String[] {"Copy original audio", "AAC 96 kbps", "AAC 128 kbps", "AAC 160 kbps", "AAC 192 kbps", "AAC 256 kbps", "AAC 320 kbps"});
        defaults.addView(audio, margin(-1, dp(46), 0, 8));
        showAdvanced = new CheckBox(this);
        showAdvanced.setText("Show advanced command configuration by default");
        showAdvanced.setTextColor(C_TEXT);
        showAdvanced.setButtonTintList(android.content.res.ColorStateList.valueOf(C_ACCENT));
        showAdvanced.setChecked(prefs.getBoolean(EncodingActivity.KEY_SHOW_ADVANCED, false));
        showAdvanced.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean checked) {
                prefs.edit().putBoolean(EncodingActivity.KEY_SHOW_ADVANCED, checked).apply();
            }
        });
        defaults.addView(showAdvanced, margin(-1, -2, 0, 8));
        Button save = button("Save Defaults", true);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { saveDefaults(); } });
        defaults.addView(save, fullWidth());
        resetButton = button("Reset Encoding Defaults", false);
        resetButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmReset(); } });
        defaults.addView(resetButton, margin(-1, 8, 0, 0));
        content.addView(defaults, margin(-1, -2, 0, 12));

        LinearLayout storage = card();
        storage.addView(section("STORAGE & APP INFORMATION"), margin(-1, -2, 0, 8));
        folderText = text("Default output: Download/SubVoryn/Encoded/\nFonts: choose an optional folder from Encoding.", 12, C_MUTED, false);
        folderText.setGravity(Gravity.TOP);
        storage.addView(folderText, margin(-1, -2, 0, 8));
        storageText = text("Checking available storage…", 12, C_MUTED, false);
        storage.addView(storageText, margin(-1, -2, 0, 8));
        appVersionText = text("App version: unavailable\nFFmpeg package target: " + ENGINE_VERSION, 12, C_TEXT, false);
        storage.addView(appVersionText, margin(-1, -2, 0, 4));
        content.addView(storage, margin(-1, -2, 0, 12));
        content.addView(text("SubVoryn keeps OCR local. Encoding files are staged in app-controlled storage. Use the Home screen for extraction/review and Encoding for hardsub output.", 12, C_MUTED, false), margin(-1, -2, 0, 12));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(C_BG);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        shell.addView(buildBottomNavigation(2), new LinearLayout.LayoutParams(-1, dp(60)));
        setContentView(shell);
    }

    private void loadDefaults() {
        resolution.setSelection(clamp(prefs.getInt(EncodingActivity.KEY_RESOLUTION, 0), 0, 4));
        fps.setSelection(clamp(prefs.getInt(EncodingActivity.KEY_FPS, 0), 0, 8));
        int quality = clamp(prefs.getInt(EncodingActivity.KEY_CRF, 24), 16, 35);
        int[] values = {16,18,20,22,24,26,28,30,32,35};
        int index = 4;
        for (int i=0;i<values.length;i++) if (values[i] == quality) { index = i; break; }
        crf.setSelection(index);
        preset.setSelection(clamp(prefs.getInt(EncodingActivity.KEY_PRESET, 2), 0, 8));
        audio.setSelection(clamp(prefs.getInt(EncodingActivity.KEY_AUDIO, 2), 0, 6));
    }

    private void saveDefaults() {
        int[] qualityValues = {16,18,20,22,24,26,28,30,32,35};
        prefs.edit().putInt(EncodingActivity.KEY_RESOLUTION, resolution.getSelectedItemPosition())
                .putInt(EncodingActivity.KEY_FPS, fps.getSelectedItemPosition())
                .putInt(EncodingActivity.KEY_CRF, qualityValues[crf.getSelectedItemPosition()])
                .putInt(EncodingActivity.KEY_PRESET, preset.getSelectedItemPosition())
                .putInt(EncodingActivity.KEY_AUDIO, audio.getSelectedItemPosition()).apply();
        toast("Encoding defaults saved.");
    }

    private void confirmReset() {
        new AlertDialog.Builder(this).setTitle("Reset encoding settings?")
                .setMessage("Resets quality and encoder preferences to Original FPS, Original Resolution, quality 24, Veryfast and AAC 128 kbps. Selected files and folders are preserved.")
                .setNegativeButton("Keep settings", null)
                .setPositiveButton("Reset", (dialog, which) -> {
                    prefs.edit().putInt(EncodingActivity.KEY_RESOLUTION, 0)
                            .putInt(EncodingActivity.KEY_FPS, 0).putString(EncodingActivity.KEY_CUSTOM_FPS, "30")
                            .putInt(EncodingActivity.KEY_CRF, 24).putInt(EncodingActivity.KEY_PRESET, 2)
                            .putInt(EncodingActivity.KEY_AUDIO, 2).putBoolean(EncodingActivity.KEY_SHOW_ADVANCED, false).apply();
                    loadDefaults();
                    showAdvanced.setChecked(false);
                    toast("Defaults reset.");
                }).show();
    }

    private void checkEngine(final boolean showToast) {
        if (checking) return;
        checking = true;
        if (engineStatus != null) engineStatus.setText("Checking packaged FFmpeg runtime…");
        if (checkButton != null) checkButton.setEnabled(false);
        new Thread(new Runnable() {
            public void run() {
                String output = "";
                String error = "";
                boolean ok = false;
                try {
                    FFmpegSession session = FFmpegKit.executeWithArguments(new String[] {"-hide_banner", "-version"});
                    if (session != null) {
                        output = session.getOutput() == null ? "" : session.getOutput();
                        ok = ReturnCode.isSuccess(session.getReturnCode()) && output.toLowerCase(Locale.ROOT).contains("ffmpeg version");
                        if (!ok) error = session.getFailStackTrace() == null ? "Runtime did not return a successful version check." : session.getFailStackTrace();
                    } else error = "FFmpegKit returned no session.";
                } catch (Throwable e) { error = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "unknown error" : e.getMessage()); }
                final boolean available = ok;
                final String versionOutput = output;
                final String failure = error;
                runOnUiThread(new Runnable() {
                    public void run() {
                        checking = false;
                        if (checkButton != null) checkButton.setEnabled(true);
                        if (available) {
                            String versionLine = versionOutput.trim().split("\\n")[0];
                            engineStatus.setText("Engine installed and responds to -version");
                            engineStatus.setTextColor(C_ACCENT);
                            engineDetails.setText("Package release target " + ENGINE_VERSION + "\n" + versionLine + "\nRuntime is verified for this app process. Filter/codec support is checked again before encoding.");
                            if (showToast) toast("FFmpeg runtime verified.");
                        } else {
                            engineStatus.setText("Engine unavailable / initialization failed");
                            engineStatus.setTextColor(Color.rgb(230, 153, 130));
                            engineDetails.setText("No Ready/Installed success state is shown until the real check passes.\n" + clip(failure, 900) + "\nUpdate the SubVoryn app from a trusted release, then retry.");
                            if (showToast) toast("FFmpeg engine check failed. See details.");
                        }
                    }
                });
            }
        }, "SubVoryn-EngineCheck").start();
    }

    private void refreshDiagnostics() {
        try {
            String version;
            if (Build.VERSION.SDK_INT >= 28) {
                android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                version = info.versionName + " (code " + info.getLongVersionCode() + ")";
            } else {
                android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                version = info.versionName + " (code " + info.versionCode + ")";
            }
            appVersionText.setText("App version: " + version + "\nFFmpeg package target: " + ENGINE_VERSION + "\nPackage ID: " + getPackageName());
        } catch (Exception ignored) { }
        try {
            File data = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (data == null) data = getCacheDir();
            StatFs fs = new StatFs(data.getAbsolutePath());
            storageText.setText("Available app-workspace storage: " + formatBytes(fs.getAvailableBytes()) + "\nWorking directory: app-private Movies folder (path intentionally not exposed here).");
        } catch (Exception e) { storageText.setText("Storage diagnostics unavailable: " + e.getClass().getSimpleName()); }
    }


    private void checkUpstreamUpdate() {
        if (checkingUpdates) return;
        checkingUpdates = true;
        updateButton.setEnabled(false);
        updateStatus.setText("Checking Maven Central metadata…");
        new Thread(new Runnable() {
            public void run() {
                HttpURLConnection connection = null;
                String latest = "";
                String error = "";
                try {
                    URL url = new URL("https://search.maven.org/solrsearch/select?q=g%3Adev.ffmpegkit-maintained%20AND%20a%3Affmpeg-kit-full&rows=1&wt=json");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("User-Agent", "SubVoryn-EngineCheck");
                    int code = connection.getResponseCode();
                    if (code != 200) throw new IOException("Maven Central returned HTTP " + code);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                    StringBuilder body = new StringBuilder();
                    char[] buffer = new char[4096];
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        if (body.length() + read > 1024 * 1024) throw new IOException("Update metadata response was unexpectedly large.");
                        body.append(buffer, 0, read);
                    }
                    reader.close();
                    JSONObject json = new JSONObject(body.toString());
                    JSONObject response = json.optJSONObject("response");
                    org.json.JSONArray docs = response == null ? null : response.optJSONArray("docs");
                    if (docs == null || docs.length() == 0) throw new IOException("No published artifact metadata was returned.");
                    latest = docs.optJSONObject(0) == null ? "" : docs.optJSONObject(0).optString("latestVersion", "");
                    if (!latest.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IOException("Latest artifact version could not be validated.");
                } catch (Throwable e) {
                    error = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "network check failed" : e.getMessage());
                } finally { if (connection != null) connection.disconnect(); }
                final String remote = latest;
                final String failure = error;
                runOnUiThread(new Runnable() {
                    public void run() {
                        checkingUpdates = false;
                        updateButton.setEnabled(true);
                        if (failure.length() > 0) {
                            updateStatus.setText("Unable to check updates. Retry when online.\n" + clip(failure, 400));
                            updateStatus.setTextColor(Color.rgb(230, 153, 130));
                        } else {
                            int comparison = compareVersions(remote, ENGINE_VERSION);
                            if (comparison > 0) {
                                updateStatus.setText("Update available: upstream " + remote + " · bundled target " + ENGINE_VERSION + ". Install after a reviewed SubVoryn app release; no engine binary was downloaded.");
                                updateStatus.setTextColor(Color.rgb(230, 196, 130));
                            } else if (comparison == 0) {
                                updateStatus.setText("Up to date: Maven Central reports " + remote + ", matching the bundled package target.");
                                updateStatus.setTextColor(C_ACCENT);
                            } else {
                                updateStatus.setText("Maven Central reports " + remote + ", older than bundled target " + ENGINE_VERSION + ". No downgrade required.");
                                updateStatus.setTextColor(C_MUTED);
                            }
                        }
                    }
                });
            }
        }, "SubVoryn-UpdateCheck").start();
    }

    private int compareVersions(String a, String b) {
        Matcher ma = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$").matcher(a);
        Matcher mb = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$").matcher(b);
        if (!ma.matches() || !mb.matches()) return 0;
        for (int i = 1; i <= 3; i++) {
            int x = Integer.parseInt(ma.group(i));
            int y = Integer.parseInt(mb.group(i));
            if (x != y) return x < y ? -1 : 1;
        }
        return 0;
    }


    private void showLicensePicker() {
        showLicenseText("third_party/FFmpeg-LGPL-3.0.txt", "GNU Lesser General Public License 3.0 — selected FFmpeg package");
    }

    private void showLicenseText(String assetPath, String title) {
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(assetPath), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
            reader.close();
        } catch (Exception e) {
            toast("Could not read license text: " + e.getClass().getSimpleName());
            return;
        }
        ScrollView scroll = new ScrollView(this);
        TextView body = text(text.toString(), 11, C_TEXT, false);
        body.setGravity(Gravity.TOP | Gravity.LEFT);
        body.setTextIsSelectable(true);
        body.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close", null).show();
    }

    private void openReleases() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))); }
        catch (Exception e) { toast("Could not open official engine release page."); }
    }

    private LinearLayout buildBottomNavigation(int active) {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(C_PANEL);
        String[] names = {"Home", "Encoding", "Settings"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            Button b = new Button(this);
            b.setText(names[i]); b.setAllCaps(false); b.setTextSize(12); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            b.setTextColor(i == active ? C_ACCENT : C_MUTED);
            b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(2), 0, dp(2), 0);
            b.setBackgroundDrawable(round(i == active ? C_PANEL_2 : C_PANEL, i == active ? C_ACCENT : C_PANEL, 12));
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (index == 0) startActivity(new Intent(SettingsActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                else if (index == 1) startActivity(new Intent(SettingsActivity.this, EncodingActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }});
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1);
            lp.leftMargin = dp(5); lp.rightMargin = dp(5); nav.addView(b, lp);
        }
        nav.setPadding(dp(5), dp(5), dp(5), dp(5));
        return nav;
    }

    private LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(14), dp(14), dp(14), dp(14)); c.setBackgroundDrawable(round(C_PANEL, C_BORDER, 17)); return c; }
    private TextView section(String s) { return text(s, 10, C_ACCENT, true); }
    private TextView text(String s, float size, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setGravity(Gravity.CENTER_VERTICAL); return t; }
    private Button button(String s, boolean primary) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(12); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(primary ? C_BG : C_TEXT); b.setPadding(dp(7), dp(5), dp(7), dp(5)); b.setBackgroundDrawable(round(primary ? C_ACCENT : C_PANEL_2, primary ? C_ACCENT : C_BORDER, 12)); return b; }
    private Spinner spinner(String[] options) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options)); ((ArrayAdapter<?>) s.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setBackgroundDrawable(round(C_PANEL_2, C_BORDER, 9)); return s; }
    private GradientDrawable round(int color, int border, int r) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(r)); d.setStroke(dp(1), border); return d; }
    private LinearLayout.LayoutParams margin(int w, int h, int left, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.leftMargin = dp(left); p.bottomMargin = dp(bottom); return p; }
    private LinearLayout.LayoutParams fullWidth() { return new LinearLayout.LayoutParams(-1, dp(46)); }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private int clamp(int v, int low, int high) { return Math.max(low, Math.min(high, v)); }
    private String formatBytes(long n) { if (n < 0) return "unknown"; if (n < 1024) return n + " B"; if (n < 1048576) return String.format(Locale.US, "%.1f KB", n / 1024.0); if (n < 1073741824) return String.format(Locale.US, "%.1f MB", n / 1048576.0); return String.format(Locale.US, "%.2f GB", n / 1073741824.0); }
    private String clip(String s, int max) { if (s == null) return ""; return s.length() <= max ? s : s.substring(0, max - 3) + "..."; }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
}
