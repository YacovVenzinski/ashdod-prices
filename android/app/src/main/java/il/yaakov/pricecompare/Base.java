package il.yaakov.pricecompare;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Common RTL scrolling page with a title. */
abstract class Base extends Activity {
    LinearLayout body;
    ScrollView scroll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
    }

    void setup(String title) {
        getWindow().setStatusBarColor(Util.BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Util.BG);
        scroll.setFillViewport(true);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Util.dp(this, 16), Util.dp(this, 18), Util.dp(this, 16), Util.dp(this, 24));
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        if (title != null) {
            TextView t = Util.text(this, title, 22, Util.INK, true);
            t.setGravity(Gravity.CENTER);
            body.addView(t, Util.lp(this, -1, -2, 0, 0, 0, 14));
        }
    }

    TextView addText(String s, float sp, int color, boolean bold, float topMargin) {
        TextView t = Util.text(this, s, sp, color, bold);
        body.addView(t, Util.lp(this, -1, -2, 2, topMargin, 2, 0));
        return t;
    }
}
