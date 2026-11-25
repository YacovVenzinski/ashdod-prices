package il.yaakov.pricecompare;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/** Private GitHub repo used as cloud storage for receipts (own repo + own token). */
final class Hub {
    private Hub() {}

    static String repo(Context c) {
        return Util.prefs(c).getString("repo", "YacovVenzinski/receipts").trim();
    }

    static String token(Context c) {
        return Util.prefs(c).getString("token", "").trim();
    }

    static boolean configured(Context c) {
        return repo(c).contains("/") && !token(c).isEmpty();
    }

    private static String[] headers(Context c, String accept) {
        return new String[]{"Authorization", "Bearer " + token(c), "Accept", accept,
                "X-GitHub-Api-Version", "2022-11-28", "Content-Type", "application/json"};
    }

    private static String api(Context c, String path) {
        return "https://api.github.com/repos/" + repo(c) + path;
    }

    static boolean isPrivate(Context c) throws Exception {
        String s = Net.get(api(c, ""), headers(c, "application/vnd.github+json"));
        return new JSONObject(s).optBoolean("private", false);
    }

    static JSONArray list(Context c, String dir) throws Exception {
        String s = Net.get(api(c, "/contents" + (dir.isEmpty() ? "" : "/" + dir)), headers(c, "application/vnd.github+json"));
        return new JSONArray(s);
    }

    static String raw(Context c, String path) throws Exception {
        return Net.get(api(c, "/contents/" + path), headers(c, "application/vnd.github.raw+json"));
    }

    static void put(Context c, String path, String content, String message) throws Exception {
        if (!isPrivate(c)) throw new IllegalStateException("המאגר ציבורי! חשבוניות לא יועלו. הפוך אותו לפרטי (Private).");
        JSONObject b = new JSONObject();
        b.put("message", message);
        b.put("content", Base64.encodeToString(content.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        Net.request("PUT", api(c, "/contents/" + path), b.toString(), headers(c, "application/vnd.github+json"));
    }
}
