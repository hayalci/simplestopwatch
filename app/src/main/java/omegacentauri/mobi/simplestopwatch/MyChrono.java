package omegacentauri.mobi.simplestopwatch;
//
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.text.style.TabStopSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MyChrono {
    private final Activity context;
    private ToneGenerator toneG = null;
    private String currentLapViewText;
    BigTextView mainView;
    TextView fractionView;
    public long baseTime;
    public long pauseTime;
    public long delayTime;
    String lapData;
    static final long MIN_DELAY_TIME = -9000;
    static final long delayTimes[] = { 0, -3000, -6000, MIN_DELAY_TIME };
    long lastLapTime;
    public boolean paused = false;
    public boolean active = false;
    boolean quiet = false;
    long lastAnnounced = 0;
    TextView lapView;
    Timer timer;
    int maxSize;
    Handler updateHandler;
    SharedPreferences options;
    public int precision = 100;
    private TextToSpeech tts = null;
    private boolean ttsMode;

    @SuppressLint("NewApi")
    public MyChrono(Activity context, SharedPreferences options, BigTextView mainView, TextView fractionView, TextView lapView) {
        this.mainView = mainView;
        this.context = context;
        this.options = options;
        this.lapData = "";
        this.delayTime = options.getLong(Options.PREF_DELAY, 0);
        this.fractionView = fractionView;
        this.lastLapTime = 0;
        this.lapView = lapView;
        this.lapView.setText("");
        this.currentLapViewText = "";
        this.lapView.setMovementMethod(new ScrollingMovementMethod());
        toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        tts = null;

        Log.v("chrono", "maxSize " +this.maxSize);

        updateHandler = new Handler() {
            public void handleMessage(Message m) {
                updateViews();
            }
        };
    }

    public long getTime() {
        return active ? (( paused ? pauseTime : SystemClock.elapsedRealtime() ) - baseTime) : delayTime;
    }

    private void announce(long t) {
        if (quiet)
            return;
        if (t < -3000 || t >= 1000) {
            lastAnnounced = floorDiv(t, 1000)*1000;
        }
        else if (t < 0) {
            if (tts != null && ttsMode) {
                String msg;
                if (-1000 <= t) {
                    msg = "1";
                }
                else if (-2000 <= t) {
                    msg = "2";
                }
                else {
                    msg = "3";
                }
                Log.v("chrono", "say: "+msg);
                tts.speak(msg,TextToSpeech.QUEUE_FLUSH, null);
            }
            else {
                if (toneG != null)
                    toneG.startTone(ToneGenerator.TONE_DTMF_S, 50);
            }
            lastAnnounced = floorDiv(t, 1000)*1000;
        }
        else if (t >= 0) {
            if (toneG != null) {
                toneG.startTone(ToneGenerator.TONE_DTMF_S, 600);
            }
            lastAnnounced = 0;
        }
    }

    protected void updateViews() {
        long t = getTime();
        if (lastAnnounced < 0 && lastAnnounced + 1000 <= t) {
            announce(t+10);
        }
        mainView.setText(formatTime(t,mainView.getHeight() > mainView.getWidth()));
        fractionView.setText(formatTimeFraction(t, active && paused));
        if (lapData.length() == 0) {
            if (lapView.getVisibility() != View.GONE)
                lapView.setVisibility(View.GONE);
        }
        else {
            String adjLapData;
            if (paused && active && lapData.length() > 0) {
                adjLapData = lapData + "\n" + formatLapTime(pauseTime-baseTime);
            }
            else {
                adjLapData = lapData;
            }

            if (lapView.getVisibility() == View.GONE || ! currentLapViewText.equals(adjLapData)) {
                SpannableStringBuilder span = new SpannableStringBuilder(adjLapData);
                int w1 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30, context.getResources().getDisplayMetrics());
                int w2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 110, context.getResources().getDisplayMetrics());
                span.setSpan(new TabStopSpan.Standard(w1), 0, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
                span.setSpan(new TabStopSpan.Standard(w2), 0, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );

                lapView.setText(span);
                currentLapViewText = adjLapData;
                lapView.setMaxLines(Math.min(5,getNumberOfLaps()));
                lapView.setVisibility(View.VISIBLE);
//                this.lapView.setMovementMethod(new ScrollingMovementMethod());
            }
        }
    }

   static final long floorDiv(long a, long b) {
        long q = a/b;
        if (q*b > a)
            return q-1;
        else
            return q;
    }

    String formatTime(long t, boolean multiline) {
        //t+=1000*60*60*60;
        if (t<0)
            return String.format("\u2212%d", -floorDiv(t,1000));
        String format = options.getString(Options.PREF_FORMAT, "h:m:s");
        t /= 1000;
        if (format.equals("s")) {
            return String.format("%02d", t);
        }
        int s = (int) (t % 60);
        t /= 60;
        int m;
        int h;
        if (format.equals("h:m:s")) {
            m = (int) (t % 60);
            t /= 60;
            h = (int) t;
        }
        else {
            m = (int) t;
            h = 0;
        }
        if (multiline) {
            Boolean threeLine = options.getBoolean(Options.PREF_THREE_LINE, true);
            if (h != 0) {
                if (threeLine)
                    return String.format("%02d\n%02d\n%02d", h, m, s);
                else
                    return String.format("%d:%02d\n%02d", h, m, s);
            }
            else
                return String.format("%02d\n%02d", m, s);
        }
        else {
            if (h != 0)
                return String.format("%d:%02d:%02d", h, m, s);
            else
                return String.format("%d:%02d", m, s);
        }
    }

    String formatTimeFraction(long t, boolean greaterPrecision) {
        if (t<0)
            return "";
        if (precision == 10 || (precision > 10 && greaterPrecision))
            return String.format(".%02d", (int)((t / 10) % 100));
        else if (precision == 100)
            return String.format(".%01d", (int)((t / 100) % 10));
        else if (precision == 1)
            return String.format(".%03d", (int)(t % 1000));
        else
            return "";
    }

    String formatTimeFull(long t) {
        if (t<0) {
            if (precision == 1)
                return String.format("%.03f", (float)(t/1000.));
            else
                return String.format("%.02f", (float)(t/1000.));
        }
        return formatTime(t, false)+formatTimeFraction(t,true );
    }

    public int getNumberOfLaps() {
        if (lapData.length() == 0)
            return 0;
        return lapData.split("\\n").length;
    }

    private String formatLapTime(long t) {
        return String.format("#%d\t%s\t%s", getNumberOfLaps() + 1, formatTimeFull(t), formatTimeFull(t-lastLapTime));
    }

    public void secondButton() {
        if (! paused) {
            long t = getTime();
            if (0 <= t) {
                String l = formatLapTime(t);
                if (lapData.length() > 0)
                    lapData += "\n" + l;
                else
                    lapData = l;
                lastLapTime = t;
            }
            updateViews();
        }
        else if (active) {
            active = false;
            lapData = "";
            lastLapTime = 0;
            stopUpdating();
        }
        else {
            int i = 0;
            for (i = 0; i < delayTimes.length; i++) {
                if (delayTimes[i] == delayTime) {
                    i = (i+1) % delayTimes.length;
                    break;
                }
            }
            delayTime = delayTimes[i];
        }
        save();
        updateViews();
    }

    public void firstButton() {
        if (active && paused) {
            baseTime += SystemClock.elapsedRealtime() - pauseTime;
            paused = false;
            startUpdating();
            save();
        }
        else if (!active) {
            lastLapTime = 0;
            if (delayTime < 0)
                lastAnnounced = delayTime - 1000;
            else
                lastAnnounced = 1000;
            baseTime = SystemClock.elapsedRealtime() - delayTime;
            paused = false;
            active = true;
            startUpdating();
            save();
        }
        else {
            paused = true;
            pauseTime = SystemClock.elapsedRealtime();
            stopUpdating();
            save();
        }
        updateViews();
    }

    public void restore() {
        baseTime = options.getLong(Options.PREFS_START_TIME, 0);
        pauseTime = options.getLong(Options.PREFS_PAUSED_TIME, 0);
        delayTime = options.getLong(Options.PREF_DELAY, 0);
        active = options.getBoolean(Options.PREFS_ACTIVE, false);
        paused = options.getBoolean(Options.PREFS_PAUSED, false);
        lapData = options.getString(Options.PREF_LAPS, "");
        lastLapTime = options.getLong(Options.PREF_LAST_LAP_TIME, 0);
        lastAnnounced = options.getLong(Options.PREF_LAST_ANNOUNCED, 0);
        String soundMode = options.getString(Options.PREF_SOUND, "voice");
        if (soundMode.equals("voice")) {
            quiet = false;
            ttsMode = true;
            if (tts == null)
                tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if(status == TextToSpeech.SUCCESS){
    /*                    int result=tts.setLanguage(Locale.US);
                        if(result==TextToSpeech.LANG_MISSING_DATA ||
                                result==TextToSpeech.LANG_NOT_SUPPORTED){
                            Log.e("chrono", "Language is not supported");
                        }
                        else{
                            Log.v("chrono", "success");
                         */
                        }
                        else {
                            tts = null;
                        }
                    }
                });

        }
        else if (soundMode.equals("beeps")) {
            quiet = false;
            ttsMode = false;
        }
        else {
            quiet = true;
        }

        precision = Integer.parseInt(options.getString(Options.PREFS_PRECISION, "100"));
        if (SystemClock.elapsedRealtime() < baseTime + MIN_DELAY_TIME)
            active = false;

        if (active && !paused) {
            startUpdating();
        }
        else {
            stopUpdating();
        }
        updateViews();
    }

    public void save() {
        SharedPreferences.Editor ed = options.edit();
        ed.putLong(Options.PREFS_START_TIME, baseTime);
        ed.putLong(Options.PREFS_PAUSED_TIME, pauseTime);
        ed.putBoolean(Options.PREFS_ACTIVE, active);
        ed.putBoolean(Options.PREFS_PAUSED, paused);
        ed.putLong(Options.PREF_DELAY, delayTime);
        ed.putString(Options.PREF_LAPS, lapData);
        ed.putLong(Options.PREF_LAST_LAP_TIME, lastLapTime);
        ed.putLong(Options.PREF_LAST_ANNOUNCED, lastAnnounced);
        ed.apply();
    }

    public void stopUpdating() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        ((Activity)context).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void startUpdating() {
        if (delayTime < 0 && lastAnnounced < 0 && !quiet) {
        }
        if (timer == null) {
            timer = new Timer();
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    updateHandler.obtainMessage(1).sendToTarget();
                }
            }, 0, precision<=10 ? precision : 50); // avoid off-by-1 errors at lower precisions, at cost of some battery life
        }
        if (options.getBoolean(Options.PREFS_SCREEN_ON, false))
            ((Activity)context).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            ((Activity)context).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public static void clearSaved(SharedPreferences pref) {
        SharedPreferences.Editor ed = pref.edit();
        ed.putBoolean(Options.PREFS_ACTIVE, false);
        ed.apply();
        Log.v("chrono", "cleared "+Options.PREFS_ACTIVE);
    }

    public static void detectBoot(SharedPreferences options) {
        return;
        /*
        long bootTime = java.lang.System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        long oldBootTime = options.getLong(Options.PREFS_BOOT_TIME, -100000);
        SharedPreferences.Editor ed = options.edit();
        if (Math.abs(oldBootTime-bootTime)>60000) {
            ed.putBoolean(Options.PREFS_ACTIVE, false);
        }
        ed.putLong(Options.PREFS_BOOT_TIME, bootTime);
        ed.apply(); */
    }

    public void copyToClipboard() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            long t = getTime();
            String s = formatTime(t, false) + formatTimeFraction(t, true);
            ClipboardManager clip = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData data = ClipData.newPlainText("time", s);
            clip.setPrimaryClip(data);
//            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT);
        }
    }

    public void copyLapsToClipboard() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            ClipboardManager clip = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData data = ClipData.newPlainText("laps", currentLapViewText.replace('\t', ' '));
            clip.setPrimaryClip(data);
//            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT);
        }
    }

    public void clearLapData() {
        lapData = "";
        lastLapTime = 0;
        save();
        updateViews();
    }

    public void destroy() {
        if (toneG != null) {
            toneG.release();
            toneG = null;
        }
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
    }
}
