package il.yaakov.pricecompare;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** A shopping receipt + local storage + sync with the private GitHub repo. */
final class Receipt {
    static class Item {
        String name = "", brand = "", barcode = "";
        double qty = 1, price = 0;
        boolean off; // excluded by the user in review

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("brand", brand);
            o.put("barcode", barcode);
            o.put("qty", qty);
            o.put("price", price);
            return o;
        }
    }

    String id = "", date = "", store = "", source = "scan";
    double total;
    List<Item> items = new ArrayList<>();

    private static File dir;

    static void init(Context c) {
        dir = new File(c.getApplicationContext().getFilesDir(), "receipts");
        dir.mkdirs();
    }

    static String newId() {
        String d = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return d + "-" + Integer.toHexString(new Random().nextInt(0xFFFF) + 0x10000).substring(1);
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("v", 1);
        o.put("id", id);
        o.put("date", date);
        o.put("store", store);
        o.put("total", total);
        o.put("source", source);
        JSONArray a = new JSONArray();
        for (Item i : items) if (!i.off) a.put(i.toJson());
        o.put("items", a);
        return o;
    }

    // ---------- tolerant parsing (own format, Gemini output and older saved receipts) ----------
    static Receipt fromJson(Object root, String id) {
        Receipt r = new Receipt();
        r.id = id;
        JSONArray arr = null;
        if (root instanceof JSONArray) {
            arr = (JSONArray) root;
        } else if (root instanceof JSONObject) {
            JSONObject o = (JSONObject) root;
            r.date = str(o, "date", "d", "ts", "time", "created");
            if (r.date.length() > 10) r.date = r.date.substring(0, 10);
            r.store = str(o, "store", "chain", "shop", "merchant", "network");
            r.total = num(o, "total", "sum", "amount");
            String src = str(o, "source");
            if (!src.isEmpty()) r.source = src;
            for (String k : new String[]{"items", "lines", "products", "rows"}) {
                arr = o.optJSONArray(k);
                if (arr != null) break;
            }
            if (arr == null) {
                JSONArray names = o.names();
                for (int i = 0; names != null && i < names.length() && arr == null; i++) {
                    arr = o.optJSONArray(names.optString(i));
                }
            }
        }
        if (arr == null) return r;
        for (int i = 0; i < arr.length(); i++) {
            Object e = arr.opt(i);
            Item it = new Item();
            if (e instanceof String) {
                it.name = (String) e;
            } else if (e instanceof JSONObject) {
                JSONObject o = (JSONObject) e;
                it.name = str(o, "name", "n", "desc", "description", "item", "title");
                it.brand = str(o, "brand", "b", "manufacturer");
                it.barcode = Data.cleanBarcode(str(o, "barcode", "bc", "code", "ean", "sku"));
                double q = num(o, "qty", "q", "quantity", "count");
                it.qty = q > 0 ? q : 1;
                it.price = num(o, "price", "p", "unit", "unit_price", "unitPrice");
            }
            if (!it.name.trim().isEmpty()) r.items.add(it);
        }
        if (r.total <= 0) for (Item i : r.items) r.total += i.price * i.qty;
        return r;
    }

    private static String str(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k);
            if (v != null && v != JSONObject.NULL && !String.valueOf(v).trim().isEmpty()) return String.valueOf(v).trim();
        }
        return "";
    }

    private static double num(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k);
            if (v instanceof Number) return ((Number) v).doubleValue();
            if (v instanceof String) {
                try {
                    return Double.parseDouble(((String) v).replace(",", ".").replaceAll("[^0-9.\\-]", ""));
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    // ---------- local storage ----------
    private static List<Receipt> cache;

    static synchronized List<Receipt> all() {
        if (cache != null) return cache;
        List<Receipt> l = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (!f.getName().endsWith(".json")) continue;
                try {
                    String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                    Object root = s.startsWith("[") ? new JSONArray(s) : new JSONObject(s);
                    Receipt r = fromJson(root, f.getName().replaceAll("\\.json$", ""));
                    if (r.date.isEmpty()) r.date = r.id.length() >= 8 && r.id.matches("^\\d{8}.*")
                            ? r.id.substring(0, 4) + "-" + r.id.substring(4, 6) + "-" + r.id.substring(6, 8) : "";
                    l.add(r);
                } catch (Exception ignored) {
                }
            }
        }
        Collections.sort(l, (a, b) -> b.date.compareTo(a.date));
        cache = l;
        return l;
    }

    static synchronized void saveLocal(Receipt r) throws Exception {
        File f = new File(dir, r.id + ".json");
        Files.write(f.toPath(), r.toJson().toString(1).getBytes(StandardCharsets.UTF_8));
        cache = null;
    }

    // ---------- sync with the private GitHub repo ----------
    private static Set<String> pushed(Context c) {
        return new HashSet<>(Util.prefs(c).getStringSet("pushed", new HashSet<String>()));
    }

    static volatile String lastPushError = "";

    /** Uploads one receipt; returns true if it reached the repo. */
    static boolean push(Context c, Receipt r) {
        if (!Hub.configured(c)) return false;
        try {
            Hub.put(c, "receipts/" + r.id + ".json", r.toJson().toString(1), "receipt " + r.id);
            Set<String> s = pushed(c);
            s.add(r.id);
            Util.prefs(c).edit().putStringSet("pushed", s).apply();
            return true;
        } catch (Exception e) {
            lastPushError = e.getMessage() == null ? "" : e.getMessage();
            return false;
        }
    }

    /** Pull receipts we do not have; push local ones not yet uploaded. Returns number pulled. */
    static int sync(Context c) throws Exception {
        if (!Hub.configured(c)) return 0;
        int pulled = 0;
        Set<String> have = new HashSet<>();
        for (Receipt r : all()) have.add(r.id);
        Set<String> pushed = pushed(c);
        for (String d : new String[]{"receipts", ""}) {
            JSONArray list;
            try {
                list = Hub.list(c, d);
            } catch (Net.HttpEx e) {
                if (e.code == 404) continue;
                throw e;
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject f = list.getJSONObject(i);
                String name = f.optString("name");
                if (!"file".equals(f.optString("type")) || !name.endsWith(".json")) continue;
                String id = name.replaceAll("\\.json$", "");
                if (have.contains(id)) {
                    pushed.add(id);
                    continue;
                }
                try {
                    String raw = Hub.raw(c, f.getString("path")).trim();
                    Object root = raw.startsWith("[") ? new JSONArray(raw) : new JSONObject(raw);
                    Receipt r = fromJson(root, id);
                    if (r.items.isEmpty()) continue;
                    saveLocal(r);
                    have.add(id);
                    pushed.add(id);
                    pulled++;
                } catch (Exception ignored) {
                }
            }
        }
        for (Receipt r : all()) {
            if (!pushed.contains(r.id)) {
                if (push(c, r)) pushed.add(r.id);
            }
        }
        Util.prefs(c).edit().putStringSet("pushed", pushed).apply();
        return pulled;
    }
}
