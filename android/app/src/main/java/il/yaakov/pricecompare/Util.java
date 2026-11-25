package il.yaakov.pricecompare;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** UI helpers, soft pastel palette, background threads. */
final class Util {
    static final ExecutorService IO = Executors.newFixedThreadPool(4);
    static final Handler UI = new Handler(Looper.getMainLooper());

    static final int BG = 0xFFFAF8F5, CARD = 0xFFFFFFFF, INK = 0xFF2B2B33, MUTED = 0xFF70707C, LINE = 0xFFEAE6DE;
    // {background, text} - soft pastel pairs, each action has its own color
    static final int[] MINT = {0xFFC9EEDB, 0xFF1D5A41};
    static final int[] LAV = {0xFFDDD6F5, 0xFF3F3470};
    static final int[] SKY = {0xFFCFE6F8, 0xFF1E4F73};
    static final int[] PEACH = {0xFFFCDCC8, 0xFF704020};
    static final int[] ROSE = {0xFFF8D4E0, 0xFF6E2644};
    static final int[] BUTTER = {0xFFFBF0BD, 0xFF5E4C0C};
    static final int[] SAND = {0xFFE8E4DA, 0xFF4B473D};

    private Util() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("p", Context.MODE_PRIVATE);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable shape(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Soft rounded button. Text always fully visible: wraps to 2 lines and auto-shrinks. */
    static TextView button(Context c, String label, int[] pal, float heightDp, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextColor(pal[1]);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        t.setMinHeight(dp(c, heightDp));
        t.setAutoSizeTextTypeUniformWithConfiguration(12, 19, 1, TypedValue.COMPLEX_UNIT_SP);
        GradientDrawable bg = shape(c, pal[0], 20);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), bg, null));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(shape(c, CARD, 18));
        l.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        return l;
    }

    /** Two (or more) equal-width buttons in one row, with gaps. */
    static LinearLayout row(Context c, View... views) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < views.length; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) p.setMarginStart(dp(c, 10));
            r.addView(views[i], p);
        }
        return r;
    }

    static String shekel(int agorot) {
        return String.format(Locale.US, "%.2f ₪", agorot / 100.0);
    }

    static String money(double v) {
        return String.format(Locale.US, "%.2f ₪", v);
    }

    static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    static void ui(Runnable r) {
        UI.post(r);
    }
}
