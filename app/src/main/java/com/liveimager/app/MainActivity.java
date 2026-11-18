package com.liveimager.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1, REQ_PROJECTION = 2, PERM_MIC = 10, PERM_CAPTURE = 11;
    private static final int BG = 0xFF0B0E14;
    private static final int SLIDER_STEPS = 1000;

    private final Engine eng = Engine.get();
    private StereoImager im;
    private ScopeView scope;
    private BandView bands;
    private TextView status;
    private Button pauseBtn, bypassBtn;
    private final SeekBar[] widthSeek = new SeekBar[4];
    private final TextView[] widthLbl = new TextView[4];
    private final SeekBar[] xSeek = new SeekBar[3];
    private final TextView[] xLbl = new TextView[3];
    private SeekBar stSeek;
    private TextView stLbl;
    private boolean visible;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        im = eng.imager;
        density = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
        syncControls();
    }

    // ---------- UI ----------

    private int dp(float v) { return Math.round(v * density); }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout row(View... views) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : views) r.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }

    private TextView label(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView header(String s) {
        TextView t = label(s, 14, 0xFFE6EAF0);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(4));
        return t;
    }

    private SeekBar seek(int max, IntConsumer onUser) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) { if (fromUser) onUser.accept(p); }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        return s;
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(24));

        TextView title = label("Live Imager", 22, 0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        root.addView(label("Real-time multiband stereo imager", 12, 0xFF8B95A5));

        root.addView(row(
                button("File", v -> pickFile()),
                button("Mic", v -> startMic()),
                button("System", v -> startCapture()),
                button("Stop", v -> stopAll())));
        pauseBtn = button("Pause", v -> eng.setPaused(!eng.isPaused()));
        bypassBtn = button("Bypass: off", v -> { im.bypass = !im.bypass; updateButtons(); });
        root.addView(row(pauseBtn, bypassBtn, button("Reset", v -> { im.reset(); syncControls(); })));

        status = label("", 12, 0xFF8B95A5);
        status.setPadding(0, dp(6), 0, dp(6));
        root.addView(status);

        scope = new ScopeView(this);
        root.addView(scope, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(header("Bands  (drag lines = crossover, drag in band = width)"));
        bands = new BandView(this);
        bands.setListener(this::syncControls);
        root.addView(bands, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)));

        root.addView(header("Band width"));
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            widthLbl[i] = label("", 12, BandView.COLORS[i]);
            widthSeek[i] = seek(200, p -> {
                int v = p - 100;
                if (Math.abs(v) < 3) v = 0;
                im.width[idx] = v / 100f;
                im.changed();
                updateLabels();
            });
            root.addView(widthLbl[i]);
            root.addView(widthSeek[i]);
        }

        root.addView(header("Crossovers"));
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            xLbl[i] = label("", 12, 0xFFB8C0CC);
            xSeek[i] = seek(SLIDER_STEPS, p -> {
                im.setCrossover(idx, sliderToHz(p));
                syncControls();
            });
            root.addView(xLbl[i]);
            root.addView(xSeek[i]);
        }

        root.addView(header("Stereoize"));
        stLbl = label("", 12, 0xFFB8C0CC);
        stSeek = seek(100, p -> { im.stereoize = p / 100f; updateLabels(); });
        root.addView(stLbl);
        root.addView(stSeek);

        TextView help = label(
                "• File: plays any audio file through the imager (loops). Use Bypass to A/B.\n"
                + "• Mic: live input processed in real time — wear headphones to avoid feedback.\n"
                + "• System: shows the scope, correlation and band widths of whatever other apps "
                + "are playing (Android 10+). Android does not let apps modify other apps' sound, "
                + "so this mode is analysis only. Some apps block capture.\n"
                + "• Width −100 = mono, 0 = unchanged, +100 = twice as wide. Stereoize adds width "
                + "to mono material while staying mono-compatible.",
                12, 0xFF6B7585);
        help.setPadding(0, dp(16), 0, 0);
        root.addView(help);

        sv.addView(root);
        return sv;
    }

    private static float sliderToHz(int p) {
        return (float) (StereoImager.MIN_F * Math.pow(StereoImager.MAX_F / StereoImager.MIN_F, p / (double) SLIDER_STEPS));
    }

    private static int hzToSlider(float f) {
        return (int) Math.round(SLIDER_STEPS * Math.log(f / StereoImager.MIN_F) / Math.log(StereoImager.MAX_F / StereoImager.MIN_F));
    }

    private void syncControls() {
        for (int i = 0; i < 4; i++) widthSeek[i].setProgress(Math.round(im.width[i] * 100) + 100);
        for (int i = 0; i < 3; i++) xSeek[i].setProgress(hzToSlider(im.xover[i]));
        stSeek.setProgress(Math.round(im.stereoize * 100));
        updateLabels();
        bands.invalidate();
    }

    private void updateLabels() {
        for (int i = 0; i < 4; i++) {
            String lo = i == 0 ? "20 Hz" : BandView.hz(im.xover[i - 1]);
            String hi = i == 3 ? "20 kHz" : BandView.hz(im.xover[i]);
            widthLbl[i].setText(String.format(Locale.US, "Band %d  (%s – %s)   width %+d",
                    i + 1, lo, hi, Math.round(im.width[i] * 100)));
        }
        for (int i = 0; i < 3; i++) xLbl[i].setText("Crossover " + (i + 1) + ":  " + BandView.hz(im.xover[i]));
        stLbl.setText(String.format(Locale.US, "Amount  %d%%", Math.round(im.stereoize * 100)));
    }

    private void setIfChanged(TextView t, String s) {
        if (!s.contentEquals(t.getText())) t.setText(s);
    }

    private void updateButtons() {
        setIfChanged(pauseBtn, eng.isPaused() ? "Resume" : "Pause");
        setIfChanged(bypassBtn, im.bypass ? "Bypass: ON" : "Bypass: off");
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            scope.invalidate();
            bands.invalidate();
            setIfChanged(status, eng.status());
            updateButtons();
            scope.postOnAnimation(this);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        scope.postOnAnimation(tick);
    }

    @Override protected void onPause() {
        visible = false;
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (isFinishing()) stopAll();
        super.onDestroy();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    // ---------- Sources ----------

    private void stopCaptureService() { stopService(new Intent(this, CaptureService.class)); }

    private void stopAll() {
        eng.stop();
        stopCaptureService();
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(i, REQ_FILE);
    }

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return "audio file";
    }

    private void startMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERM_MIC);
            return;
        }
        doStartMic();
    }

    private void doStartMic() {
        stopCaptureService();
        try {
            eng.start(new MicSource(), "● Live input — use headphones!");
        } catch (Exception e) {
            toast("Microphone error: " + e.getMessage());
        }
    }

    private void startCapture() {
        if (Build.VERSION.SDK_INT < 29) {
            toast("System audio capture needs Android 10 or newer.");
            return;
        }
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), PERM_CAPTURE);
            return;
        }
        doStartCapture();
    }

    private void doStartCapture() {
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        Intent i = Build.VERSION.SDK_INT >= 34
                ? mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                : mpm.createScreenCaptureIntent();
        startActivityForResult(i, REQ_PROJECTION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (!mic) {
            toast("Microphone/recording permission is required for this mode.");
            return;
        }
        if (requestCode == PERM_MIC) doStartMic();
        else if (requestCode == PERM_CAPTURE) doStartCapture();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri u = data.getData();
            stopCaptureService();
            try {
                eng.start(new FileSource(this, u), "▶ " + displayName(u) + "  (looping)");
            } catch (Exception e) {
                toast("Can't open file: " + e.getMessage());
            }
        } else if (requestCode == REQ_PROJECTION) {
            if (resultCode != RESULT_OK || data == null) {
                toast("System audio capture was not allowed.");
                return;
            }
            eng.stop();
            Intent s = new Intent(this, CaptureService.class)
                    .putExtra(CaptureService.EXTRA_CODE, resultCode)
                    .putExtra(CaptureService.EXTRA_DATA, data);
            startForegroundService(s);
        }
    }
}
