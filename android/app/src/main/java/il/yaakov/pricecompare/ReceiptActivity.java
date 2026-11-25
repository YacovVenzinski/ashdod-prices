package il.yaakov.pricecompare;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptActivity extends Base {
    static final int REQ = 11;
    LinearLayout content;
    Receipt reviewing;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setup("חשבוניות");
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        body.addView(content, new LinearLayout.LayoutParams(-1, -2));

        String rj = getIntent().getStringExtra("review");
        if (rj != null) {
            try {
                JSONObject o = new JSONObject(rj);
                Receipt r = Receipt.fromJson(o, o.optString("id", Receipt.newId()));
                showReview(r, true);
                return;
            } catch (Exception ignored) {
            }
        }
        home();
        if (b == null && getIntent().getBooleanExtra("scan", false)) startScan();
        Util.IO.execute(() -> {
            try {
                Receipt.sync(this);
            } catch (Exception ignored) {
            }
            Learn.refresh(this);
            Util.ui(() -> {
                if (reviewing == null) home();
            });
        });
    }

    // ---------- home: scan, what I learned, saved receipts ----------
    void home() {
        reviewing = null;
        content.removeAllViews();
        content.addView(Util.row(this,
                        Util.button(this, "🧾  סרוק חשבונית", Util.ROSE, 64, v -> startScan()),
                        Util.button(this, "🔗  מקישור שהועתק", Util.SKY, 64, v -> fromClipboard())),
                Util.lp(this, -1, Util.dp(this, 68), 0, 0, 0, 0));
        TextView hint = Util.text(this, "חשבונית ארוכה — סרוק אותה בכמה חלקים (עד 4 עמודים). קישור לחשבונית דיגיטלית מ-SMS אפשר לשתף ישר לאפליקציה.", 12, Util.MUTED, false);
        content.addView(hint, Util.lp(this, -1, -2, 2, 6, 2, 0));

        Learn l = Learn.current;
        LinearLayout learn = Util.card(this);
        learn.addView(Util.text(this, "🧠 מה למדתי עליך", 16, Util.INK, true));
        if (l.receipts == 0) {
            learn.addView(Util.text(this, "עדיין אין חשבוניות שמורות.", 13, Util.MUTED, false), Util.lp(this, -1, -2, 0, 4, 0, 0));
        } else {
            learn.addView(Util.text(this, "מתוך " + l.receipts + " חשבוניות", 12, Util.MUTED, false));
            List<Learn.Stat> top = l.topProducts(8, false);
            if (!top.isEmpty()) {
                learn.addView(Util.text(this, "מוצרים שאתה קונה הכי הרבה:", 13, Util.LAV[1], true), Util.lp(this, -1, -2, 0, 8, 0, 0));
                for (Learn.Stat s : top) learn.addView(Util.text(this, "• " + s.name + "  ×" + s.n, 14, Util.INK, false));
            }
            List<String> br = l.topBrands(6);
            if (!br.isEmpty()) {
                learn.addView(Util.text(this, "מותגים מועדפים:", 13, Util.LAV[1], true), Util.lp(this, -1, -2, 0, 8, 0, 0));
                learn.addView(Util.text(this, join(br), 14, Util.INK, false));
            }
            List<String> ch = l.topChains(4);
            if (!ch.isEmpty()) {
                learn.addView(Util.text(this, "חברות מועדפות:", 13, Util.LAV[1], true), Util.lp(this, -1, -2, 0, 8, 0, 0));
                learn.addView(Util.text(this, join(ch), 14, Util.INK, false));
            }
        }
        content.addView(learn, Util.lp(this, -1, -2, 0, 14, 0, 0));

        List<Receipt> all = Receipt.all();
        TextView head = Util.text(this, "חשבוניות שמורות: " + all.size(), 15, Util.INK, true);
        content.addView(head, Util.lp(this, -1, -2, 2, 16, 2, 4));
        for (Receipt r : all) {
            LinearLayout row = Util.card(this);
            row.addView(Util.text(this, (r.store.isEmpty() ? "חשבונית" : r.store) + "  ·  " + r.date, 15, Util.INK, true));
            row.addView(Util.text(this, r.items.size() + " פריטים" + (r.total > 0 ? "  ·  " + Util.money(r.total) : ""), 13, Util.MUTED, false));
            row.setOnClickListener(v -> showReview(r, false));
            content.addView(row, Util.lp(this, -1, -2, 0, 4, 0, 4));
        }
    }

    static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) sb.append(sb.length() == 0 ? "" : "  ·  ").append(s);
        return sb.toString();
    }

    // ---------- scanning ----------
    void startScan() {
        if (Gemini.key(this).isEmpty()) {
            Util.toast(this, "קודם הגדר מפתח Gemini בהגדרות.");
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        DocScan.start(this, REQ, 4);
    }

    void fromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String s = "";
        ClipData d = cm.getPrimaryClip();
        if (d != null && d.getItemCount() > 0 && d.getItemAt(0).getText() != null) s = d.getItemAt(0).getText().toString();
        Matcher m = Pattern.compile("https?://[^\\s<>\"]+").matcher(s);
        if (!m.find()) {
            Util.toast(this, "לא נמצא קישור בהודעה. העתק את ההודעה עם הקישור ונסה שוב.");
            return;
        }
        Intent i = new Intent(this, LinkReceiptActivity.class);
        i.putExtra(Intent.EXTRA_TEXT, s);
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ || res != RESULT_OK || data == null) return;
        List<Uri> pages = DocScan.pages(data);
        if (pages.isEmpty()) {
            Util.toast(this, "לא התקבלה תמונה");
            return;
        }
        content.removeAllViews();
        TextView t = Util.text(this, "קורא את החשבונית...", 17, Util.INK, true);
        t.setGravity(Gravity.CENTER);
        content.addView(t, Util.lp(this, -1, -2, 0, 30, 0, 0));
        Util.IO.execute(() -> {
            try {
                Receipt r = Gemini.readReceipt(this, null, pages, "scan");
                Util.ui(() -> {
                    if (r.items.isEmpty()) {
                        Util.toast(this, "לא נמצאו פריטים בחשבונית");
                        home();
                    } else {
                        showReview(r, true);
                    }
                });
            } catch (Exception e) {
                Util.ui(() -> {
                    Util.toast(this, "שגיאה: " + e.getMessage());
                    home();
                });
            }
        });
    }

    // ---------- review / view one receipt ----------
    void showReview(Receipt r, boolean editable) {
        reviewing = r;
        content.removeAllViews();
        TextView title = Util.text(this, (r.store.isEmpty() ? "חשבונית" : r.store) + "  ·  " + r.date, 18, Util.INK, true);
        content.addView(title);
        if (r.total > 0) content.addView(Util.text(this, "סה\"כ " + Util.money(r.total), 14, Util.MUTED, false));
        if (editable) {
            content.addView(Util.text(this, "זוהו " + r.items.size() + " פריטים. לחץ על פריט כדי להוציא אותו מהחשבונית, ואז שמור.", 13, Util.MUTED, false),
                    Util.lp(this, -1, -2, 0, 4, 0, 0));
        }
        LinearLayout card = Util.card(this);
        for (Receipt.Item it : r.items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Util.dp(this, 8), 0, Util.dp(this, 8));
            TextView n = Util.text(this, it.name, 15, Util.INK, false);
            String sub = (it.brand.isEmpty() ? "" : "מותג: " + it.brand + "  ·  ") + (it.qty != 1 ? trim(it.qty) + " × " : "") + Util.money(it.price);
            TextView s = Util.text(this, sub, 12, Util.MUTED, false);
            TextView m = Util.text(this, "", 12, Util.MINT[1], false);
            row.addView(n);
            row.addView(s);
            row.addView(m);
            if (editable) {
                row.setOnClickListener(v -> {
                    it.off = !it.off;
                    n.setPaintFlags(it.off ? n.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                            : n.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    row.setAlpha(it.off ? 0.4f : 1f);
                });
            }
            card.addView(row);
            Util.IO.execute(() -> {
                try {
                    Data.loadNames();
                    int idx = Data.resolve(it.barcode, it.name);
                    String t = idx >= 0 ? "✔ זוהה במאגר: " + Data.nName[idx] : "— לא נמצא במאגר";
                    Util.ui(() -> m.setText(t));
                } catch (Exception ignored) {
                }
            });
        }
        content.addView(card, Util.lp(this, -1, -2, 0, 10, 0, 0));
        if (editable) {
            content.addView(Util.button(this, "💾  שמור חשבונית", Util.MINT, 58, v -> save(r)), Util.lp(this, -1, -2, 0, 12, 0, 0));
        }
        content.addView(Util.button(this, editable ? "ביטול" : "חזרה", Util.SAND, 50, v -> home()), Util.lp(this, -1, -2, 0, 10, 0, 0));
    }

    static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    void save(Receipt r) {
        List<Receipt.Item> keep = new ArrayList<>();
        for (Receipt.Item i : r.items) if (!i.off) keep.add(i);
        if (keep.isEmpty()) {
            Util.toast(this, "לא נמצאו פריטים בחשבונית");
            return;
        }
        r.items = keep;
        r.total = 0;
        for (Receipt.Item i : keep) r.total += i.price * i.qty;
        TextView t = Util.text(this, "שומר במאגר...", 17, Util.INK, true);
        content.removeAllViews();
        t.setGravity(Gravity.CENTER);
        content.addView(t, Util.lp(this, -1, -2, 0, 30, 0, 0));
        Util.IO.execute(() -> {
            String msg;
            try {
                Receipt.saveLocal(r);
                boolean up = Receipt.push(this, r);
                if (up) msg = "✔ הקבלה נשמרה במאגר";
                else if (!Hub.configured(this)) msg = "נשמר. שמור במכשיר בלבד (ענן לא הוגדר)";
                else msg = "נשמר במכשיר. סנכרון נכשל: " + Receipt.lastPushError;
            } catch (Exception e) {
                msg = "שמירה נכשלה: " + e.getMessage();
            }
            Learn.refresh(this);
            String m = msg;
            Util.ui(() -> {
                Util.toast(this, m);
                home();
            });
        });
    }
}
