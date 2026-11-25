package il.yaakov.pricecompare;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opens a digital-receipt link (SMS from the chain, shared or VIEW intent), reads the rendered page
 * text and lets Gemini turn it into a receipt that you can review and save.
 */
public class LinkReceiptActivity extends Base {
    WebView web;
    TextView status;
    int tries;
    String lastText = "";
    boolean done;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setup("חשבונית מקישור");
        status = Util.text(this, "טוען את הקבלה...", 17, Util.INK, true);
        status.setGravity(Gravity.CENTER);
        body.addView(status, Util.lp(this, -1, -2, 0, 10, 0, 10));

        String url = findUrl(getIntent());
        if (url == null) {
            status.setText("לא נמצא קישור בהודעה.");
            return;
        }
        if (Gemini.key(this).isEmpty()) {
            status.setText("קודם הגדר מפתח Gemini בהגדרות.");
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String u, Bitmap f) {
                status.setText("טוען את הקבלה...");
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                poll(1500);
            }

            @Override
            public void onReceivedError(WebView v, android.webkit.WebResourceRequest r, android.webkit.WebResourceError e) {
                if (r.isForMainFrame()) status.setText("לא הצלחתי לפתוח את הקישור: " + e.getDescription());
            }
        });
        body.addView(web, Util.lp(this, -1, Util.dp(this, 260), 0, 0, 0, 0));
        web.loadUrl(url);
    }

    static String findUrl(Intent i) {
        String s = null;
        if (Intent.ACTION_SEND.equals(i.getAction())) s = i.getStringExtra(Intent.EXTRA_TEXT);
        else if (i.getDataString() != null) s = i.getDataString();
        else s = i.getStringExtra(Intent.EXTRA_TEXT);
        if (s == null) return null;
        Matcher m = Pattern.compile("https?://[^\\s<>\"]+").matcher(s);
        return m.find() ? m.group() : null;
    }

    /** The page is often rendered by JavaScript, so wait until its text stops growing. */
    void poll(long delay) {
        Util.UI.postDelayed(() -> {
            if (done || web == null) return;
            web.evaluateJavascript("(function(){return document.body ? document.body.innerText : ''})()", v -> {
                String t = decode(v);
                tries++;
                if (t.length() > 150 && t.equals(lastText)) {
                    finish(t);
                } else if (tries < 14) {
                    lastText = t;
                    status.setText("הדף עדיין נטען...");
                    poll(1500);
                } else if (t.length() > 60) {
                    finish(t);
                } else {
                    status.setText("שגיאה בקריאת הדף: לא נמצא תוכן");
                }
            });
        }, delay);
    }

    static String decode(String js) {
        if (js == null || js.equals("null")) return "";
        try {
            return new org.json.JSONArray("[" + js + "]").getString(0);
        } catch (Exception e) {
            return "";
        }
    }

    void finish(String text) {
        done = true;
        status.setText("קורא את החשבונית...");
        String t = text.length() > 30000 ? text.substring(0, 30000) : text;
        Util.IO.execute(() -> {
            try {
                Receipt r = Gemini.readReceipt(this, "טקסט החשבונית:\n" + t, null, "link");
                Util.ui(() -> {
                    if (r.items.isEmpty()) {
                        status.setText("לא נמצאו פריטים בחשבונית");
                        return;
                    }
                    try {
                        Intent i = new Intent(this, ReceiptActivity.class);
                        i.putExtra("review", r.toJson().toString());
                        startActivity(i);
                        super.finish();
                    } catch (Exception e) {
                        status.setText("שגיאה: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Util.ui(() -> status.setText("שגיאה: " + e.getMessage()));
            }
        });
    }
}
