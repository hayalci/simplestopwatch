package omegacentauri.mobi.simplestopwatch;
//
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

public class MyChrono {
    private final Activity context;
    BigTextView mainView;
    TextView fractionView;
    public long baseTime;
    public long pauseTime;
    public boolean paused = false;
    public boolean active = false;
    TextView view;
    Timer timer;
    int maxSize;
    Handler updateHandler;
    SharedPreferences options;
    public int precision = 100;

    @SuppressLint("NewApi")
    public MyChrono(Activity context, SharedPreferences options, BigTextView mainView, TextView fractionView) {
        this.mainView = mainView;
        this.context = context;
        this.options = options;
        this.fractionView = fractionView;
        this.maxSize = Integer.parseInt(options.getString(Options.PREF_MAX_SIZE, "1200"));
        Log.v("chrono", "maxSize " +this.maxSize);

        updateHandler = new Handler() {
            public void handleMessage(Message m) {
                updateViews();
            }
        };
    }

    public void updateViews() {
        long t = active ? (( paused ? pauseTime : SystemClock.elapsedRealtime() ) - baseTime) : 0;
        String line1 = formatTime(t);
        Log.v("chrono", "update");
        maximizeSize(mainView, line1, 0.96f, 10);
        fractionView.setText(formatTimeFraction(t));
    }

    static private void optionalSetSizeAndText(TextView v, float newSize, String text) {
        if (Math.abs(newSize - v.getTextSize()) > 10)
            v.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize);
        if (! v.getText().equals(text))
            v.setText(text);

    }

    String formatTime(long t) {
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
        if (mainView.getHeight() > mainView.getWidth()) {
            if (h != 0)
                return String.format("%d:%02d\n%02d", h, m, s);
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

    String formatTimeFraction(long t) {
        if (precision == 100)
            return String.format(".%01d", (int)((t / 100) % 10));
        else if (precision == 10)
            return String.format(".%02d", (int)((t / 10) % 100));
        else if (precision == 1)
            return String.format(".%03d", (int)(t % 1000));
        else
            return "";
    }

    @SuppressLint("ResourceType")
    private void maximizeSize(BigTextView v, String text, float scale, int prec) {
        float curSize = v.getTextSizePixels();

        v.setText(text);

        if (text.length() == 0) {
            return;
        }
        float vWidth = v.getWidth();
        float vHeight = v.getHeight();
        if (vWidth == 0 || vHeight == 0) {
            return;
        }
        RectF bounds = new RectF();
        v.measureText(bounds);
        float textWidth = bounds.width();
        float textHeight = bounds.height();
        if (textWidth == 0 || textHeight == 0)
            return;

        float newSize = Math.min(maxSize, Math.min(vWidth/textWidth, vHeight/textHeight) * curSize * scale);

        if (Math.abs(newSize-curSize) < prec)
            return;

        Log.v("chrono", "new size "+newSize+ " on height "+v.getHeight());
        Log.v("chrono", "screen height " +context.getWindow().getDecorView().getHeight());
        Log.v("chrono", "activity height " +context.findViewById(R.id.main).getHeight());

        v.setTextSizePixels(newSize);
    }

    public void resetButton() {
        if (! paused)
            return;
        active = false;
        stopUpdating();
        save();
        updateViews();
    }

    public void startStopButton() {
        if (active && paused) {
            baseTime += SystemClock.elapsedRealtime() - pauseTime;
            paused = false;
            startUpdating();
            save();
        }
        else if (!active) {
            baseTime = SystemClock.elapsedRealtime();
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
        maxSize = Integer.parseInt(options.getString(Options.PREF_MAX_SIZE, "1200"));
        baseTime = options.getLong(Options.PREFS_START_TIME, 0);
        pauseTime = options.getLong(Options.PREFS_PAUSED_TIME, 0);
        active = options.getBoolean(Options.PREFS_ACTIVE, false);
        paused = options.getBoolean(Options.PREFS_PAUSED, false);
        precision = Integer.parseInt(options.getString(Options.PREFS_PRECISION, "100"));
        if (SystemClock.elapsedRealtime() <= baseTime)
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
        if (timer == null) {
            timer = new Timer();
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    updateHandler.obtainMessage(1).sendToTarget();
                }
            }, 0, precision);
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
}
