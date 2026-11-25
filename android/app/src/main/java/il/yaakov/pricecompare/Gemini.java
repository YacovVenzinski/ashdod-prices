package il.yaakov.pricecompare;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Google Gemini REST API (the user's own free key) for reading receipts and handwritten lists. */
final class Gemini {
    private Gemini() {}

    static final String RECEIPT_PROMPT =
            "אתה קורא חשבוניות סופרמרקט ישראליות בעברית. החזר JSON בלבד בצורה: "
                    + "{\"store\":\"שם הרשת\",\"date\":\"YYYY-MM-DD או ריק\",\"total\":מספר,"
                    + "\"items\":[{\"name\":\"שם המוצר כפי שמופיע\",\"brand\":\"שם המותג/היצרן אם ידוע, אחרת ריק\","
                    + "\"barcode\":\"ברקוד אם מודפס, אחרת ריק\",\"qty\":מספר,\"price\":מחיר ליחידה בשקלים}]}. "
                    + "התעלם משורות הנחה, סכומי ביניים, מע\"מ ושורות תשלום. בפריט שנמכר לפי משקל: qty=1 ו-price=המחיר הכולל של השורה. "
                    + "אם החשבונית מכילה כמה עמודים, אחד את כולם לרשימה אחת בלי כפילויות.";

    static final String LIST_PROMPT =
            "זו רשימת קניות בכתב יד בעברית. החזר JSON בלבד: {\"items\":[{\"name\":\"שם המוצר\",\"qty\":מספר}]}. "
                    + "qty=1 אם לא נכתבה כמות. אל תוסיף מוצרים שלא כתובים.";

    static String key(Context c) {
        return Util.prefs(c).getString("gemini_key", "").trim();
    }

    static String model(Context c) {
        String m = Util.prefs(c).getString("gemini_model", "").trim();
        return m.isEmpty() ? "gemini-flash-latest" : m;
    }

    private static String jpeg(Context c, Uri u) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / s > 2400) s *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        Bitmap b;
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            b = BitmapFactory.decodeStream(in, null, o);
        }
        if (b == null) throw new IOException("לא התקבלה תמונה");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 85, bo);
        b.recycle();
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    /** Sends the prompt (+ optional text and images) and returns the parsed JSON answer. */
    static JSONObject askJson(Context c, String prompt, String extraText, List<Uri> images) throws Exception {
        if (key(c).isEmpty()) throw new IllegalStateException("קודם הגדר מפתח Gemini בהגדרות.");
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", extraText == null ? prompt : prompt + "\n\n" + extraText));
        if (images != null) {
            for (Uri u : images) {
                JSONObject inline = new JSONObject().put("mime_type", "image/jpeg").put("data", jpeg(c, u));
                parts.put(new JSONObject().put("inline_data", inline));
            }
        }
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("parts", parts)))
                .put("generationConfig", new JSONObject().put("temperature", 0).put("responseMimeType", "application/json"));
        // Overloaded (503/500/429): retry with a pause, then fall back to other models.
        String[] models = {model(c), "gemini-2.5-flash", "gemini-flash-lite-latest", "gemini-2.5-flash-lite"};
        String resp = null;
        IOException last = null;
        int[] pauses = {0, 3000, 6000};
        outer:
        for (int m = 0; m < models.length; m++) {
            if (m > 0 && models[m].equals(models[0])) continue;
            for (int a = 0; a < pauses.length; a++) {
                if (pauses[a] > 0) Thread.sleep(pauses[a]);
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + models[m] + ":generateContent";
                try {
                    resp = Net.request("POST", url, body.toString(), "x-goog-api-key", key(c), "Content-Type", "application/json");
                    break outer;
                } catch (Net.HttpEx e) {
                    String msg = e.body;
                    try {
                        msg = new JSONObject(e.body).getJSONObject("error").optString("message", e.body);
                    } catch (Exception ignored) {
                    }
                    last = new IOException("Gemini " + e.code + ": " + msg);
                    boolean busy = e.code == 503 || e.code == 500 || e.code == 429 || e.code == 404;
                    if (!busy) throw last;
                    if (e.code == 404) break; // model name unknown: try the next model
                }
            }
        }
        if (resp == null) throw last != null ? last : new IOException("תשובה לא צפויה מ-Gemini");
        JSONArray cand = new JSONObject(resp).optJSONArray("candidates");
        if (cand == null || cand.length() == 0) throw new IOException("תשובה לא צפויה מ-Gemini");
        String t = cand.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim();
        int a = t.indexOf('{'), z = t.lastIndexOf('}');
        if (a < 0 || z <= a) throw new IOException("תשובה לא צפויה מ-Gemini");
        return new JSONObject(t.substring(a, z + 1));
    }

    static Receipt readReceipt(Context c, String text, List<Uri> images, String source) throws Exception {
        JSONObject o = askJson(c, RECEIPT_PROMPT, text, images);
        Receipt r = Receipt.fromJson(o, Receipt.newId());
        r.source = source;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})$").matcher(r.date);
        if (m.find()) {
            String y = m.group(3).length() == 2 ? "20" + m.group(3) : m.group(3);
            r.date = y + "-" + String.format(java.util.Locale.US, "%02d", Integer.parseInt(m.group(2)))
                    + "-" + String.format(java.util.Locale.US, "%02d", Integer.parseInt(m.group(1)));
        }
        if (!r.date.matches("^\\d{4}-\\d{2}-\\d{2}$")) r.date = Receipt.today();
        return r;
    }
}
