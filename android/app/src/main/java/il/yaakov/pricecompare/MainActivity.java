package il.yaakov.pricecompare;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Base {
    Spinner spin;
    EditText q;
    LinearLayout results, suggest;
    TextView footer;
    boolean spinnerReady;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setup("השוואת מחירים · אשדוד");

        // --- which store ---
        LinearLayout storeCard = Util.card(this);
        TextView ask = Util.text(this, "באיזו חנות באשדוד אתה נמצא?", 16, Util.INK, true);
        storeCard.addView(ask);
        spin = new Spinner(this);
        spin.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        storeCard.addView(spin, Util.lp(this, -1, Util.dp(this, 52), 0, 4, 0, 0));
        body.addView(storeCard, Util.lp(this, -1, -2, 0, 0, 0, 12));

        // --- search by name ---
        q = new EditText(this);
        q.setHint("חיפוש מוצר לפי שם");
        q.setSingleLine(true);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setTextSize(17);
        q.setBackground(Util.shape(this, Util.CARD, 16));
        q.setPadding(Util.dp(this, 14), Util.dp(this, 10), Util.dp(this, 14), Util.dp(this, 10));
        q.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
        TextView go = Util.button(this, "חפש", Util.PEACH, 52, v -> search());
        LinearLayout sr = new LinearLayout(this);
        sr.setOrientation(LinearLayout.HORIZONTAL);
        sr.addView(q, new LinearLayout.LayoutParams(0, Util.dp(this, 52), 1f));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Util.dp(this, 92), Util.dp(this, 52));
        gp.setMarginStart(Util.dp(this, 10));
        sr.addView(go, gp);
        body.addView(sr, Util.lp(this, -1, -2, 0, 0, 0, 12));

        // --- big barcode button ---
        body.addView(Util.button(this, "📷  סרוק ברקוד", Util.MINT, 78, v -> scan()), Util.lp(this, -1, -2, 0, 0, 0, 12));

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        body.addView(results, Util.lp(this, -1, -2, 0, 0, 0, 0));

        suggest = new LinearLayout(this);
        suggest.setOrientation(LinearLayout.VERTICAL);
        body.addView(suggest, Util.lp(this, -1, -2, 0, 0, 0, 0));

        // --- action buttons: every one in its own soft color, full text visible ---
        body.addView(Util.row(this,
                        Util.button(this, "🧾  סרוק חשבונית", Util.ROSE, 64, v -> openReceipts(true)),
                        Util.button(this, "✍️  סרוק רשימה בכתב יד", Util.BUTTER, 64, v -> openList(true))),
                Util.lp(this, -1, Util.dp(this, 68), 0, 14, 0, 0));
        body.addView(Util.row(this,
                        Util.button(this, "📚  חשבוניות שלי", Util.LAV, 64, v -> openReceipts(false)),
                        Util.button(this, "🛒  רשימת קניות", Util.SKY, 64, v -> openList(false))),
                Util.lp(this, -1, Util.dp(this, 68), 0, 10, 0, 0));
        body.addView(Util.button(this, "⚙️  הגדרות", Util.SAND, 52, v -> startActivity(new Intent(this, SettingsActivity.class))),
                Util.lp(this, -1, -2, 0, 10, 0, 0));

        footer = Util.text(this, "", 12, Util.MUTED, false);
        footer.setGravity(Gravity.CENTER);
        body.addView(footer, Util.lp(this, -1, -2, 0, 14, 0, 0));

        loadStores();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Util.IO.execute(() -> {
            Learn.refresh(this);
            Util.ui(this::showSuggestions);
        });
    }

    void loadStores() {
        Util.IO.execute(() -> {
            try {
                Data.loadCore();
                Util.ui(() -> {
                    List<String> names = new ArrayList<>();
                    int sel = 0;
                    String saved = Util.prefs(this).getString("store", "");
                    for (int i = 0; i < Data.stores.size(); i++) {
                        names.add(Data.stores.get(i).label());
                        if (Data.stores.get(i).id.equals(saved)) sel = i;
                    }
                    ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
                    ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spin.setAdapter(ad);
                    spin.setSelection(sel);
                    spin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                            Util.prefs(MainActivity.this).edit().putString("store", Data.stores.get(pos).id).apply();
                            if (spinnerReady) showSuggestions();
                            spinnerReady = true;
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> p) {
                        }
                    });
                    String d = Data.built.length() >= 10 ? Data.built.substring(8, 10) + "/" + Data.built.substring(5, 7) + "/" + Data.built.substring(0, 4) : "";
                    footer.setText("מחירים מקבצי שקיפות המחירים" + (d.isEmpty() ? "" : ", עודכנו ב-" + d));
                });
            } catch (Exception e) {
                Util.ui(() -> footer.setText(Net.offline(e) ? "אין חיבור לאינטרנט. התחבר ונסה שוב." : "טוען רשימת חנויות... נכשל"));
            }
        });
    }

    String storeId() {
        return Util.prefs(this).getString("store", "");
    }

    // ---------- suggestions learned from your receipts ----------
    void showSuggestions() {
        suggest.removeAllViews();
        Learn l = Learn.current;
        List<Learn.Stat> top = l.topProducts(6, true);
        LinearLayout card = Util.card(this);
        card.addView(Util.text(this, "⭐ מוצרים שמתאימים לך", 16, Util.INK, true));
        if (top.isEmpty()) {
            card.addView(Util.text(this, "סרוק חשבוניות כדי שאלמד אילו מוצרים ומותגים אתה קונה, ואציע לך אותם כאן.", 13, Util.MUTED, false),
                    Util.lp(this, -1, -2, 0, 4, 0, 0));
        } else {
            card.addView(Util.text(this, "לפי מה שאתה קונה הכי הרבה, עם המחיר בחנות שבחרת", 12, Util.MUTED, false));
            for (Learn.Stat s : top) {
                TextView name = Util.text(this, s.name, 15, Util.INK, false);
                TextView price = Util.text(this, "", 14, Util.MUTED, true);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(price);
                row.setPadding(0, Util.dp(this, 9), 0, Util.dp(this, 9));
                row.setOnClickListener(v -> openProduct(s.barcode));
                card.addView(row);
                Util.IO.execute(() -> {
                    try {
                        Data.Product p = Data.product(s.barcode);
                        Integer here = p == null ? null : p.prices.get(storeId());
                        String t = here == null ? "אין בחנות" : Util.shekel(here);
                        Util.ui(() -> price.setText(t));
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        suggest.addView(card, Util.lp(this, -1, -2, 0, 12, 0, 0));
    }

    // ---------- actions ----------
    void search() {
        String text = q.getText().toString().trim();
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        im.hideSoftInputFromWindow(q.getWindowToken(), 0);
        results.removeAllViews();
        if (text.isEmpty()) return;
        results.addView(Util.text(this, "מחפש...", 14, Util.MUTED, false));
        Util.IO.execute(() -> {
            try {
                Data.loadNames();
                List<Integer> hits = Data.search(text, 30);
                Util.ui(() -> {
                    results.removeAllViews();
                    if (hits.isEmpty()) {
                        results.addView(Util.text(this, "לא נמצאו מוצרים תואמים.", 15, Util.MUTED, false));
                        return;
                    }
                    LinearLayout card = Util.card(this);
                    for (int idx : hits) {
                        String bc = Data.nBar[idx];
                        boolean liked = Learn.knows(bc);
                        TextView t = Util.text(this, (liked ? "⭐ " : "") + Data.nName[idx] + "  ·  " + Data.nCnt[idx] + " חנויות", 15, Util.INK, liked);
                        t.setPadding(0, Util.dp(this, 9), 0, Util.dp(this, 9));
                        t.setOnClickListener(v -> openProduct(bc));
                        card.addView(t);
                    }
                    results.addView(card, Util.lp(this, -1, -2, 0, 0, 0, 4));
                });
            } catch (Exception e) {
                Util.ui(() -> {
                    results.removeAllViews();
                    results.addView(Util.text(this, Net.offline(e) ? "אין חיבור לאינטרנט או שהשרת לא זמין." : "שגיאה בקריאת הנתונים", 15, Util.MUTED, false));
                });
            }
        });
    }

    void scan() {
        GmsBarcodeScannerOptions o = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build();
        GmsBarcodeScanning.getClient(this, o).startScan()
                .addOnSuccessListener(code -> {
                    String v = code.getRawValue();
                    if (v == null || Data.cleanBarcode(v).length() < 6) Util.toast(this, "ברקוד לא תקין");
                    else openProduct(v);
                })
                .addOnFailureListener(e -> Util.toast(this, "הסורק לא זמין (נדרש Google Play Services)"));
    }

    void openProduct(String barcode) {
        Intent i = new Intent(this, ProductActivity.class);
        i.putExtra("barcode", barcode);
        startActivity(i);
    }

    void openReceipts(boolean scan) {
        Intent i = new Intent(this, ReceiptActivity.class);
        i.putExtra("scan", scan);
        startActivity(i);
    }

    void openList(boolean handwritten) {
        Intent i = new Intent(this, ListActivity.class);
        i.putExtra("scan", handwritten);
        startActivity(i);
    }
}
