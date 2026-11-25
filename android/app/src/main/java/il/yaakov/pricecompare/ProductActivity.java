package il.yaakov.pricecompare;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ProductActivity extends Base {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setup(null);
        String barcode = getIntent().getStringExtra("barcode");
        TextView status = addText("בודק מחירים...", 16, Util.MUTED, false, 20);
        status.setGravity(Gravity.CENTER);
        Util.IO.execute(() -> {
            try {
                Data.loadCore();
                Data.Product p = Data.product(barcode);
                Util.ui(() -> {
                    body.removeView(status);
                    if (p == null) {
                        addText(Data.cleanBarcode(barcode) + " לא נמצא במאגר.", 17, Util.INK, true, 20);
                        addText("המוצר לא מופיע בקבצי שקיפות המחירים של החנויות באשדוד.", 14, Util.MUTED, false, 6);
                    } else {
                        show(p);
                    }
                });
            } catch (Exception e) {
                Util.ui(() -> status.setText(Net.offline(e) ? "אין חיבור לאינטרנט או שהשרת לא זמין." : "שגיאה בקריאת הנתונים"));
            }
        });
    }

    void show(Data.Product p) {
        String mine = Util.prefs(this).getString("store", "");
        TextView title = addText(p.name, 22, Util.INK, true, 4);
        title.setGravity(Gravity.CENTER);
        TextView code = addText("ברקוד " + p.barcode, 12, Util.MUTED, false, 2);
        code.setGravity(Gravity.CENTER);
        int times = Learn.timesBought(p.barcode);
        if (times > 0) {
            TextView t = addText("⭐ מוצר שאתה קונה (" + times + " פעמים בחשבוניות שלך)", 13, Util.LAV[1], true, 8);
            t.setGravity(Gravity.CENTER);
        }

        // sorted prices
        List<Map.Entry<String, Integer>> l = new ArrayList<>(p.prices.entrySet());
        Collections.sort(l, (a, c) -> a.getValue() - c.getValue());
        int cheapest = l.isEmpty() ? 0 : l.get(0).getValue();

        LinearLayout hero = Util.card(this);
        hero.setBackground(Util.shape(this, Util.MINT[0], 20));
        Integer here = p.prices.get(mine);
        if (here != null) {
            hero.addView(Util.text(this, "בחנות שבחרת: " + Data.storeLabel(mine), 13, Util.MINT[1], false));
            hero.addView(Util.text(this, Util.shekel(here), 34, Util.MINT[1], true));
            if (here > cheapest) {
                hero.addView(Util.text(this, "אפשר לחסוך " + Util.shekel(here - cheapest) + " בחנות הזולה ביותר", 14, Util.MINT[1], false));
            } else {
                hero.addView(Util.text(this, "זה המחיר הזול ביותר באשדוד ✔", 14, Util.MINT[1], true));
            }
        } else {
            hero.addView(Util.text(this, "אין מחיר למוצר הזה בחנות שבחרת.", 16, Util.MINT[1], true));
        }
        if (!l.isEmpty()) {
            hero.addView(Util.text(this, "הכי זול: " + Util.shekel(cheapest) + " ב" + Data.storeLabel(l.get(0).getKey()), 14, Util.MINT[1], false),
                    Util.lp(this, -1, -2, 0, 6, 0, 0));
        }
        body.addView(hero, Util.lp(this, -1, -2, 0, 14, 0, 0));

        LinearLayout card = Util.card(this);
        card.addView(Util.text(this, "כל החנויות (" + l.size() + ")", 15, Util.INK, true));
        for (Map.Entry<String, Integer> e : l) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Util.dp(this, 8), 0, Util.dp(this, 8));
            boolean isMine = e.getKey().equals(mine);
            TextView n = Util.text(this, (isMine ? "📍 " : "") + Data.storeLabel(e.getKey()), 14, Util.INK, isMine);
            int diff = e.getValue() - cheapest;
            TextView pr = Util.text(this, Util.shekel(e.getValue()) + (diff > 0 ? "  (+" + Util.shekel(diff) + ")" : ""), 14,
                    diff == 0 ? Util.MINT[1] : Util.MUTED, true);
            row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(pr);
            card.addView(row);
        }
        body.addView(card, Util.lp(this, -1, -2, 0, 12, 0, 0));

        body.addView(Util.button(this, "➕  הוסף לרשימת הקניות", Util.SKY, 54, v -> {
            android.content.SharedPreferences sp = Util.prefs(this);
            String cur = sp.getString("list", "");
            sp.edit().putString("list", (cur.isEmpty() ? "" : cur + "\n") + p.name).apply();
            Util.toast(this, "נוסף לרשימה");
        }), Util.lp(this, -1, -2, 0, 12, 0, 0));
    }
}
