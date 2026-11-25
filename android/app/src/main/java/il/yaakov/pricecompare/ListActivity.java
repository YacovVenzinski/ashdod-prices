package il.yaakov.pricecompare;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shopping list: type it or scan a handwritten one, then find the cheapest store. */
public class ListActivity extends Base {
    static final int REQ = 12;

    static class Row {
        String text;
        int qty = 1;
        List<Integer> cand = new ArrayList<>();
        int sel = -1;
        Data.Product prod;
    }

    EditText ed;
    LinearLayout out;
    List<Row> rows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setup("רשימת קניות");
        addText("הקלד/תקן ידנית — שורה לכל פריט (מספר בתחילת שורה = כמות):", 13, Util.MUTED, false, 0);
        ed = new EditText(this);
        ed.setText(Util.prefs(this).getString("list", ""));
        ed.setMinLines(5);
        ed.setGravity(Gravity.TOP | Gravity.START);
        ed.setTextSize(16);
        ed.setBackground(Util.shape(this, Util.CARD, 16));
        ed.setPadding(Util.dp(this, 14), Util.dp(this, 12), Util.dp(this, 14), Util.dp(this, 12));
        body.addView(ed, Util.lp(this, -1, -2, 0, 6, 0, 0));

        body.addView(Util.row(this,
                        Util.button(this, "✍️  סרוק רשימה בכתב יד", Util.BUTTER, 60, v -> startScan()),
                        Util.button(this, "💰  חשב איפה הכי זול", Util.MINT, 60, v -> compute())),
                Util.lp(this, -1, Util.dp(this, 64), 0, 12, 0, 0));

        out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        body.addView(out, Util.lp(this, -1, -2, 0, 14, 0, 0));

        if (b == null && getIntent().getBooleanExtra("scan", false)) startScan();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Util.prefs(this).edit().putString("list", ed.getText().toString()).apply();
    }

    // ---------- handwritten list ----------
    void startScan() {
        if (Gemini.key(this).isEmpty()) {
            Util.toast(this, "קודם הגדר מפתח Gemini בהגדרות.");
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        DocScan.start(this, REQ, 2);
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
        out.removeAllViews();
        out.addView(Util.text(this, "קורא את הרשימה...", 16, Util.INK, true));
        Util.IO.execute(() -> {
            try {
                JSONObject o = Gemini.askJson(this, Gemini.LIST_PROMPT, null, pages);
                JSONArray a = o.optJSONArray("items");
                StringBuilder sb = new StringBuilder(ed.getText().toString().trim());
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject it = a.optJSONObject(i);
                    String n = it == null ? a.optString(i) : it.optString("name");
                    int q = it == null ? 1 : (int) Math.max(1, Math.round(it.optDouble("qty", 1)));
                    if (n.trim().isEmpty()) continue;
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(q > 1 ? q + " " : "").append(n.trim());
                }
                String all = sb.toString();
                Util.ui(() -> {
                    out.removeAllViews();
                    ed.setText(all);
                    Util.toast(this, "זוהו פריטים — תקן אם צריך ולחץ \"חשב\".");
                });
            } catch (Exception e) {
                Util.ui(() -> {
                    out.removeAllViews();
                    Util.toast(this, "שגיאה: " + e.getMessage());
                });
            }
        });
    }

    // ---------- match + price ----------
    void compute() {
        String txt = ed.getText().toString();
        Util.prefs(this).edit().putString("list", txt).apply();
        out.removeAllViews();
        out.addView(Util.text(this, "מתאים מוצרים ומחשב...", 16, Util.INK, true));
        Util.IO.execute(() -> {
            try {
                Data.loadCore();
                Data.loadNames();
                Learn.refresh(this);
                List<Row> rs = new ArrayList<>();
                Pattern pq = Pattern.compile("^(\\d{1,2})\\s+(.+)$");
                for (String line : txt.split("\n")) {
                    String t = line.trim();
                    if (t.isEmpty()) continue;
                    Row r = new Row();
                    Matcher m = pq.matcher(t);
                    if (m.find()) {
                        r.qty = Integer.parseInt(m.group(1));
                        t = m.group(2).trim();
                    }
                    r.text = t;
                    r.cand = Data.matchList(t, 15);
                    if (!r.cand.isEmpty()) {
                        r.sel = 0;
                        r.prod = Data.product(Data.nBar[r.cand.get(0)]);
                    }
                    rs.add(r);
                }
                rows = rs;
                Util.ui(this::render);
            } catch (Exception e) {
                Util.ui(() -> {
                    out.removeAllViews();
                    out.addView(Util.text(this, Net.offline(e) ? "אין חיבור לאינטרנט או שהשרת לא זמין." : "שגיאה: " + e.getMessage(), 15, Util.MUTED, false));
                });
            }
        });
    }

    void render() {
        out.removeAllViews();
        if (rows.isEmpty()) {
            out.addView(Util.text(this, "הרשימה ריקה.", 15, Util.MUTED, false));
            return;
        }
        // matched items, tap to switch product
        LinearLayout items = Util.card(this);
        items.addView(Util.text(this, "המוצרים שנבחרו", 15, Util.INK, true));
        List<String> missing = new ArrayList<>();
        for (Row r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Util.dp(this, 8), 0, Util.dp(this, 8));
            row.addView(Util.text(this, (r.qty > 1 ? r.qty + " × " : "") + r.text, 14, Util.INK, true));
            if (r.prod != null) {
                boolean liked = Learn.knows(r.prod.barcode);
                row.addView(Util.text(this, (liked ? "⭐ " : "") + r.prod.name + "   (לחץ להחלפה)", 13, Util.SKY[1], false));
                row.setOnClickListener(v -> choose(r));
            } else {
                row.addView(Util.text(this, "לא נמצא במאגר", 13, Util.MUTED, false));
                missing.add(r.text);
            }
            items.addView(row);
        }
        out.addView(items);

        // per-store totals
        List<Data.Store> stores = Data.stores;
        List<Object[]> res = new ArrayList<>(); // store, total, missing
        int matched = 0;
        for (Row r : rows) if (r.prod != null) matched++;
        for (Data.Store s : stores) {
            int total = 0, miss = 0;
            for (Row r : rows) {
                if (r.prod == null) continue;
                Integer p = r.prod.prices.get(s.id);
                if (p == null) miss++;
                else total += p * r.qty;
            }
            res.add(new Object[]{s, total, miss});
        }
        Collections.sort(res, (a, c) -> {
            int m = (int) a[2] - (int) c[2];
            return m != 0 ? m : (int) a[1] - (int) c[1];
        });

        LinearLayout best = Util.card(this);
        best.setBackground(Util.shape(this, Util.MINT[0], 20));
        if (matched == 0) {
            best.addView(Util.text(this, "לא נמצאו מוצרים תואמים.", 16, Util.MINT[1], true));
        } else {
            Object[] top = res.get(0);
            int full = 0;
            for (Object[] x : res) if ((int) x[2] == 0) full++;
            best.addView(Util.text(this, "באיזו חנות הכי זול (" + (matched - (int) top[2]) + " מתוך " + matched + " פריטים בחנות)", 13, Util.MINT[1], false));
            best.addView(Util.text(this, ((Data.Store) top[0]).label(), 18, Util.MINT[1], true));
            best.addView(Util.text(this, Util.shekel((int) top[1]), 32, Util.MINT[1], true));
            if ((int) top[2] > 0) {
                best.addView(Util.text(this, "(חסרים " + top[2] + " פריטים בחנות הזו)", 13, Util.MINT[1], false));
            }

            // buying every item at its cheapest store
            int sum = 0;
            Map<String, List<String>> split = new HashMap<>();
            Map<String, Integer> splitSum = new HashMap<>();
            for (Row r : rows) {
                if (r.prod == null || r.prod.prices.isEmpty()) continue;
                String bs = null;
                int bp = Integer.MAX_VALUE;
                for (Map.Entry<String, Integer> e : r.prod.prices.entrySet()) {
                    if (e.getValue() < bp) {
                        bp = e.getValue();
                        bs = e.getKey();
                    }
                }
                sum += bp * r.qty;
                if (!split.containsKey(bs)) {
                    split.put(bs, new ArrayList<String>());
                    splitSum.put(bs, 0);
                }
                split.get(bs).add(r.prod.name);
                splitSum.put(bs, splitSum.get(bs) + bp * r.qty);
            }
            best.addView(Util.text(this, "אם קונים כל פריט במחיר הזול ביותר (בכמה חנויות): " + Util.shekel(sum)
                    + (sum < (int) top[1] ? "  — חיסכון " + Util.shekel((int) top[1] - sum) : ""), 14, Util.MINT[1], true), Util.lp(this, -1, -2, 0, 10, 0, 0));
            for (Map.Entry<String, List<String>> e : split.entrySet()) {
                best.addView(Util.text(this, "• " + Data.storeLabel(e.getKey()) + " — " + e.getValue().size() + " פריטים, " + Util.shekel(splitSum.get(e.getKey())),
                        13, Util.MINT[1], false));
            }
        }
        out.addView(best, Util.lp(this, -1, -2, 0, 12, 0, 0));

        if (matched > 0) {
            LinearLayout all = Util.card(this);
            all.addView(Util.text(this, "כל החנויות", 15, Util.INK, true));
            for (Object[] x : res) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, Util.dp(this, 7), 0, Util.dp(this, 7));
                TextView n = Util.text(this, ((Data.Store) x[0]).label() + ((int) x[2] > 0 ? "  (חסרים " + x[2] + ")" : ""), 13, Util.INK, false);
                TextView p = Util.text(this, Util.shekel((int) x[1]), 13, Util.MUTED, true);
                row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(p);
                all.addView(row);
            }
            out.addView(all, Util.lp(this, -1, -2, 0, 12, 0, 0));
        }
        if (!missing.isEmpty()) {
            String m = "לא חושבו: ";
            for (int i = 0; i < missing.size(); i++) m += (i > 0 ? ", " : "") + missing.get(i);
            out.addView(Util.text(this, m, 13, Util.MUTED, false), Util.lp(this, -1, -2, 2, 10, 2, 0));
        }
    }

    void choose(Row r) {
        String[] names = new String[r.cand.size()];
        for (int i = 0; i < names.length; i++) {
            int idx = r.cand.get(i);
            names[i] = (Learn.knows(Data.nBar[idx]) ? "⭐ " : "") + Data.nName[idx];
        }
        new AlertDialog.Builder(this).setTitle(r.text).setItems(names, (d, which) -> {
            r.sel = which;
            Util.IO.execute(() -> {
                try {
                    r.prod = Data.product(Data.nBar[r.cand.get(which)]);
                } catch (Exception ignored) {
                }
                Util.ui(this::render);
            });
        }).show();
    }
}
