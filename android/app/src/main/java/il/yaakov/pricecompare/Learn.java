package il.yaakov.pricecompare;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Learns what you like to buy from your saved receipts: exact products, brands, chains and
 * typical words. The profile re-ranks search results and shopping-list matches and drives the
 * "suggested for you" list on the home screen.
 */
final class Learn {
    static class Stat {
        String key, name, barcode;
        int n;
        double w;
    }

    static volatile Learn current = new Learn();

    int receipts;
    final Map<String, Double> bc = new HashMap<>();      // barcode -> weighted purchases
    final Map<String, Double> brand = new HashMap<>();   // normalised brand -> weighted purchases
    final Map<String, Double> tok = new HashMap<>();     // word -> weighted purchases
    final Map<String, Double> chain = new HashMap<>();   // chain name -> weighted visits
    final Map<String, Stat> products = new HashMap<>();
    final Map<String, Integer> brandCount = new HashMap<>();
    double[] brandBoost;                                  // per catalog index

    /** Recent purchases count more: weight halves roughly every four months. */
    private static double weight(String date) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            double age = (System.currentTimeMillis() - d.getTime()) / 86400000.0;
            return Math.max(0.25, Math.pow(0.5, Math.max(0, age) / 120.0));
        } catch (Exception e) {
            return 0.7;
        }
    }

    /** Rebuild the profile from all local receipts. Call on a background thread. */
    static void refresh(Context c) {
        try {
            Data.loadNames();
        } catch (Exception ignored) {
        }
        Learn l = new Learn();
        List<Receipt> rs = Receipt.all();
        l.receipts = rs.size();
        Set<String> brands = new HashSet<>();
        for (Receipt r : rs) {
            double w = weight(r.date);
            if (!r.store.trim().isEmpty()) merge(l.chain, r.store.trim(), w);
            for (Receipt.Item it : r.items) {
                String b = Data.norm(it.brand);
                if (!b.isEmpty()) {
                    merge(l.brand, b, w);
                    l.brandCount.put(it.brand.trim(), l.brandCount.containsKey(it.brand.trim()) ? l.brandCount.get(it.brand.trim()) + 1 : 1);
                    brands.add(b);
                }
                int idx = Data.resolve(it.barcode, it.name);
                String key, name, barcode = "";
                if (idx >= 0) {
                    key = Data.nBar[idx];
                    name = Data.nName[idx];
                    barcode = key;
                    merge(l.bc, key, w);
                } else {
                    key = "n:" + Data.norm(it.name);
                    name = it.name;
                }
                Stat s = l.products.get(key);
                if (s == null) {
                    s = new Stat();
                    s.key = key;
                    s.name = name;
                    s.barcode = barcode;
                    l.products.put(key, s);
                }
                s.n++;
                s.w += w;
                for (String t : Data.norm(it.name).split(" ")) if (t.length() >= 3) merge(l.tok, t, w);
            }
        }
        if (Data.namesReady && !brands.isEmpty()) {
            double[] bb = new double[Data.nNorm.length];
            for (int i = 0; i < bb.length; i++) {
                String padded = " " + Data.nNorm[i] + " ";
                for (String b : brands) {
                    if (padded.contains(" " + b + " ")) bb[i] = Math.max(bb[i], l.brand.get(b));
                }
            }
            l.brandBoost = bb;
        }
        current = l;
    }

    private static void merge(Map<String, Double> m, String k, double w) {
        Double v = m.get(k);
        m.put(k, v == null ? w : v + w);
    }

    /** Preference score of a catalog product (0 when nothing is learned). */
    static double score(int idx) {
        Learn l = current;
        if (l.receipts == 0) return 0;
        double s = 0;
        Double c = l.bc.get(Data.nBar[idx]);
        if (c != null) s += 100 * Math.log1p(c);
        if (l.brandBoost != null && idx < l.brandBoost.length) s += 25 * Math.log1p(l.brandBoost[idx]);
        double t = 0;
        for (String w : Data.nNorm[idx].split(" ")) {
            Double x = l.tok.get(w);
            if (x != null) t += Math.log1p(x);
        }
        return s + Math.min(t * 2, 12);
    }

    static boolean knows(String barcode) {
        return current.bc.containsKey(barcode);
    }

    static int timesBought(String barcode) {
        Stat s = current.products.get(barcode);
        return s == null ? 0 : s.n;
    }

    List<Stat> topProducts(int n, boolean onlyCatalog) {
        List<Stat> l = new ArrayList<>();
        for (Stat s : products.values()) if (!onlyCatalog || !s.barcode.isEmpty()) l.add(s);
        Collections.sort(l, (a, b) -> Double.compare(b.w, a.w));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    List<String> topBrands(int n) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(brandCount.entrySet());
        Collections.sort(l, (a, b) -> b.getValue() - a.getValue());
        List<String> r = new ArrayList<>();
        for (int i = 0; i < l.size() && i < n; i++) r.add(l.get(i).getKey() + " (" + l.get(i).getValue() + ")");
        return r;
    }

    List<String> topChains(int n) {
        List<Map.Entry<String, Double>> l = new ArrayList<>(chain.entrySet());
        Collections.sort(l, (a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> r = new ArrayList<>();
        for (int i = 0; i < l.size() && i < n; i++) r.add(l.get(i).getKey());
        return r;
    }
}
