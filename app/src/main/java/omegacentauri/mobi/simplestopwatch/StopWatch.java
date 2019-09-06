package omegacentauri.mobi.simplestopwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

public class StopWatch extends Activity {
    SharedPreferences options;
    long baseTime = 0;
    long pausedTime = 0;
    boolean active = false;
    boolean paused = false;
    boolean chronoStarted = false;
    private BigTextView chrono = null;
    private MyChrono stopwatch;
    private Button secondButton;
    private Button firstButton;
    private float unselectedThickness = 2f;
    private float selectedThickness = 6f;
    private static final int RECOLORABLE_TEXTVIEW[] = {
        R.id.fraction, R.id.laps
    };
    private static final int RECOLORABLE_BUTTON[] = {
            R.id.start, R.id.reset
    };
    private View.OnTouchListener highlighter;
    private TextView laps;

    public float dp2px(float dp){
        return dp * (float)getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        options = PreferenceManager.getDefaultSharedPreferences(this);
        MyChrono.detectBoot(options);
        setContentView(R.layout.activity_stop_watch);
        chrono = (BigTextView)findViewById(R.id.chrono);
        secondButton = (Button)findViewById(R.id.reset);
        firstButton = (Button)findViewById(R.id.start);
        laps = (TextView)findViewById(R.id.laps);
        stopwatch = new MyChrono(this, options, chrono, (TextView)findViewById(R.id.fraction),
                laps);
        highlighter = new View.OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    GradientDrawable gd = (GradientDrawable) view.getBackground();
                    gd.setStroke((int)dp2px(selectedThickness), Options.getForeColor(options));
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    GradientDrawable gd = (GradientDrawable) view.getBackground();
                    gd.setStroke((int)dp2px(unselectedThickness), Options.getForeColor(options));
                }
                return false;
            }
        };
        secondButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (stopwatch.active && stopwatch.lapData.length() > 0) {
                    askClearLapData();
                }
                return true;
            }
        });
        firstButton.setOnTouchListener(highlighter);
        secondButton.setOnTouchListener(highlighter);
        chrono.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                stopwatch.copyToClipboard();
                return false;
            }
        });
        laps.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                stopwatch.copyLapsToClipboard();
                return false;
            }
        });
    }

    private void askClearLapData() {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Clear lap data");
        alertDialog.setMessage("Clear lap data?");
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Yes",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        stopwatch.clearLapData();
                    } });
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {} });
        alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {} });
        alertDialog.show();
    }

    void setTheme() {
        chrono.setFont(Options.getFont(options));
        chrono.setKeepAspect(options.getBoolean(Options.PREF_KEEP_ASPECT, true));
        chrono.setLineSpacing(Float.parseFloat(options.getString(Options.PREF_LINE_SPACING, "105%").replace("%",""))/100f);
        chrono.setLetterSpacing(Float.parseFloat(options.getString(Options.PREF_LETTER_SPACING, "95%").replace("%",""))/100f);
        chrono.setScale(Float.parseFloat(options.getString(Options.PREF_SCALE, "98%").replace("%",""))/100f);

        int fore = Options.getForeColor(options);
        int back = Options.getBackColor(options);

        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0).setBackgroundColor(back);

        for (int id : RECOLORABLE_TEXTVIEW) {
            ((TextView)findViewById(id)).setTextColor(fore);
        }

        chrono.setTextColor(fore);

        for (int id : RECOLORABLE_BUTTON) {
            Button b = findViewById(id);
            b.setTextColor(fore);
            GradientDrawable gd = (GradientDrawable)b.getBackground();
            gd.setStroke((int)dp2px(unselectedThickness), fore);
        }

        ((ImageButton)findViewById(R.id.settings)).setColorFilter(fore, PorterDuff.Mode.MULTIPLY);
    }

    @Override
    protected void onResume() {
        super.onResume();

        String o = options.getString(Options.PREFS_ORIENTATION, "automatic");
        if (o.equals("landscape"))
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        else if (o.equals("portrait"))
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        Log.v("chrono", "theme");
        setTheme();
        int orientation = getResources().getConfiguration().orientation;
        chrono.post(new Runnable() {
            @Override
            public void run() {
                stopwatch.updateViews();
            }
        });

        Log.v("chrono", "onResume");

        stopwatch.restore();
        stopwatch.updateViews();
        updateButtons();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.v("chrono", "onConfChanged");
        super.onConfigurationChanged(newConfig);
        stopwatch.updateViews();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //stopwatch.save();
        stopwatch.stopUpdating();
    }

    void updateButtons() {
        if (!stopwatch.active) {
            firstButton.setText("Start");
            secondButton.setText("Delay");
        }
        else {
            if (stopwatch.paused) {
                firstButton.setText("Continue");
                secondButton.setText("Reset");
                //secondButton.setVisibility(View.VISIBLE);
            } else {
                firstButton.setText("Stop");
                secondButton.setText("Lap");
            }
        }
    }

    void pressReset() {
        stopwatch.secondButton();
        updateButtons();
    }

    void pressStart() {
        stopwatch.firstButton();
        updateButtons();
    }

    public void onButtonStart(View v) {
        pressStart();
    }

    public void onButtonReset(View v) {
        pressReset();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN)
            return false;
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP /*|| keyCode == KeyEvent.KEYCODE_A*/) {
            pressStart();
            return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN /*|| keyCode == KeyEvent.KEYCODE_C*/) {
            pressReset();
            return true;
        }
        return false;
    }

    public void onButtonSettings(View view) {
        startActivity(new Intent(this, Options.class));
    }
}
