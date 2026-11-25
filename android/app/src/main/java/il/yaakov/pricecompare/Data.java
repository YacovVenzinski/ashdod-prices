package il.yaakov.pricecompare;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Price catalog from the static JSON API (GitHub Pages) with a local cache. */
final class Data {
    static final String BASE = "https://yacovvenzinski.github.io/ashdod-prices/";
    static final long HOUR = 3600_000L;

    static class Store {
        String id, chain, name, address;

        String label() {
            return chain + " – " + name;
        }
    }

    static class Product {
        String barcode, name;
        Map<String, Integer> prices = new LinkedHashMap<>();
    }

    private static File dir;
    static volatile List<Store> stores = new ArrayList<>();
    static volatile String built = "";
    static volatile boolean namesReady;
    static String[] nBar = new String[0], nName = new String[0], nNorm = new String[0];
    static int[] nCnt = new int[0];
    static Map<String, int[]> inv = new HashMap<>();
    static Map<String, Integer> idxByBar = new HashMap<>();

    private Data() {}

    static void init(Context c) {
        dir = c.getApplicationContext().getFilesDir();
    }

    // ---------- cache ----------
    static String cached(String rel, long maxAgeMs) throws IOException {
        File f = new File(dir, rel.replace('/', '_'));
        long age = System.currentTimeMillis() - f.lastModified();
        if (f.exists() && age < maxAgeMs) return readFile(f);
        try {
            String s = Net.get(BASE + rel);
            File tmp = new File(dir, rel.replace('/', '_') + ".tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(s.getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
            return s;
        } catch (IOException e) {
            if (f.exists()) return readFile(f);
            throw e;
        }
    }

    private static String readFile(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    // ---------- stores / names ----------
    static void loadCore() throws Exception {
        if (!stores.isEmpty()) return;
        JSONArray a = new JSONArray(cached("stores.json", 6 * HOUR));
        List<Store> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Store s = new Store();
            s.id = o.getString("id");
            s.chain = o.optString("chain");
            s.name = o.optString("name");
            s.address = o.optString("address");
            l.add(s);
        }
        Collections.sort(l, (x, y) -> {
            int c = x.chain.compareTo(y.chain);
            return c != 0 ? c : x.name.compareTo(y.name);
        });
        try {
            built = new JSONObject(cached("meta.json", 6 * HOUR)).optString("built");
        } catch (Exception ignored) {
        }
        stores = l;
    }

    static String storeLabel(String id) {
        for (Store s : stores) if (s.id.equals(id)) return s.label();
        return id;
    }

    static synchronized void loadNames() throws Exception {
        if (namesReady) return;
        JSONArray a = new JSONArray(cached("names.json", 12 * HOUR));
        int n = a.length();
        String[] bar = new String[n], name = new String[n], norm = new String[n];
        int[] cnt = new int[n];
        Map<String, ArrayList<Integer>> tmp = new HashMap<>();
        Map<String, Integer> byBar = new HashMap<>();
        for (int i = 0; i < n; i++) {
            JSONArray e = a.getJSONArray(i);
            bar[i] = e.getString(0);
            name[i] = e.getString(1);
            cnt[i] = e.getInt(2);
            norm[i] = norm(name[i]);
            byBar.put(bar[i], i);
            for (String t : norm[i].split(" ")) {
                if (t.length() < 2) continue;
                ArrayList<Integer> l = tmp.get(t);
                if (l == null) {
                    l = new ArrayList<>();
                    tmp.put(t, l);
                }
                l.add(i);
            }
        }
        Map<String, int[]> iv = new HashMap<>();
        for (Map.Entry<String, ArrayList<Integer>> e : tmp.entrySet()) {
            int[] arr = new int[e.getValue().size()];
            for (int i = 0; i < arr.length; i++) arr[i] = e.getValue().get(i);
            iv.put(e.getKey(), arr);
        }
        nBar = bar;
        nName = name;
        nNorm = norm;
        nCnt = cnt;
        inv = iv;
        idxByBar = byBar;
        namesReady = true;
    }

    // ---------- text normalisation ----------
    static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[\"'׳״`.,()\\-־/\\\\*:;!?_%+]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    static String cleanBarcode(String b) {
        return b == null ? "" : b.replaceAll("\\D", "").replaceFirst("^0+", "");
    }

    // ---------- products ----------
    private static final LinkedHashMap<String, JSONObject> SHARDS = new LinkedHashMap<String, JSONObject>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, JSONObject> e) {
            return size() > 12;
        }
    };

    static Product product(String barcode) throws Exception {
        String bc = cleanBarcode(barcode);
        if (bc.length() < 3) return null;
        String sh = bc.substring(bc.length() - 3);
        JSONObject o;
        synchronized (SHARDS) {
            o = SHARDS.get(sh);
        }
        if (o == null) {
            o = new JSONObject(cached("p/" + sh + ".json", 12 * HOUR));
            synchronized (SHARDS) {
                SHARDS.put(sh, o);
            }
        }
        JSONObject e = o.optJSONObject(bc);
        if (e == null) return null;
        Product p = new Product();
        p.barcode = bc;
        p.name = e.optString("n");
        JSONObject pr = e.getJSONObject("p");
        for (Iterator<String> it = pr.keys(); it.hasNext(); ) {
            String k = it.next();
            p.prices.put(k, pr.getInt(k));
        }
        return p;
    }

    // ---------- search / matching (ranked with learned preferences) ----------
    /** Every query word must appear in the name. */
    static List<Integer> search(String q, int limit) {
        String[] t = norm(q).split(" ");
        if (t.length == 0 || t[0].isEmpty()) return new ArrayList<>();
        List<double[]> hits = new ArrayList<>();
        for (int i = 0; i < nNorm.length; i++) {
            boolean all = true;
            for (String w : t) {
                if (!nNorm[i].contains(w)) {
                    all = false;
                    break;
                }
            }
            if (all) hits.add(new double[]{i, 1000 + Learn.score(i) + Math.log(1 + nCnt[i]) * 3 - nNorm[i].length() * 0.15});
        }
        return top(hits, limit);
    }

    /** Best candidates for a free-text shopping-list line (tolerates a missing word). */
    static List<Integer> matchList(String text, int limit) {
        String[] t = norm(text).split(" ");
        if (t.length == 0 || t[0].isEmpty()) return new ArrayList<>();
        int need = t.length <= 2 ? t.length : (int) Math.ceil(t.length * 0.6);
        List<double[]> hits = new ArrayList<>();
        for (int i = 0; i < nNorm.length; i++) {
            int h = 0;
            for (String w : t) if (nNorm[i].contains(w)) h++;
            if (h >= need && h > 0) {
                double frac = h / (double) t.length;
                hits.add(new double[]{i, frac * 1000 + Learn.score(i) + Math.log(1 + nCnt[i]) * 3 - nNorm[i].length() * 0.15});
            }
        }
        return top(hits, limit);
    }

    private static List<Integer> top(List<double[]> hits, int limit) {
        Collections.sort(hits, (a, b) -> Double.compare(b[1], a[1]));
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < hits.size() && i < limit; i++) r.add((int) hits.get(i)[0]);
        return r;
    }

    /** Map a receipt line to a catalog index (barcode first, then fuzzy word overlap). -1 if none. */
    static int resolve(String barcode, String name) {
        if (!namesReady) return -1;
        String bc = cleanBarcode(barcode);
        if (bc.length() >= 8) {
            Integer i = idxByBar.get(bc);
            if (i != null) return i;
        }
        String[] t = norm(name).split(" ");
        Map<Integer, Integer> cnt = new HashMap<>();
        int tokens = 0;
        for (String w : t) {
            if (w.length() < 2) continue;
            tokens++;
            int[] p = inv.get(w);
            if (p == null || p.length > 4000) continue;
            for (int i : p) {
                Integer c = cnt.get(i);
                cnt.put(i, c == null ? 1 : c + 1);
            }
        }
        if (tokens == 0) return -1;
        int best = -1;
        double bestS = 0;
        for (Map.Entry<Integer, Integer> e : cnt.entrySet()) {
            int i = e.getKey();
            int shared = e.getValue();
            int nt = 0;
            for (String w : nNorm[i].split(" ")) if (w.length() >= 2) nt++;
            double j = shared / (double) (tokens + nt - shared);
            boolean ok = j >= 0.5 && (shared >= 2 || (tokens == 1 && nt == 1));
            double s = j + Math.min(nCnt[i], 20) * 0.001;
            if (ok && s > bestS) {
                bestS = s;
                best = i;
            }
        }
        return best;
    }
}
