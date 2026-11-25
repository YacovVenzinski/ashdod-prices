package il.yaakov.pricecompare;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;

public class SettingsActivity extends Base {
    EditText gem, model, repo, token;
    TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setup("הגדרות");
        android.content.SharedPreferences sp = Util.prefs(this);

        gem = field("מפתח Gemini API (מ-aistudio.google.com)", sp.getString("gemini_key", ""), true);
        model = field("דגם Gemini (ברירת מחדל: gemini-flash-latest)", sp.getString("gemini_model", ""), false);
        repo = field("מאגר GitHub פרטי (שם-משתמש/שם-מאגר)", sp.getString("repo", "YacovVenzinski/receipts"), false);
        token = field("טוקן GitHub (הרשאת Contents: Read and write למאגר הזה בלבד)", sp.getString("token", ""), true);

        body.addView(Util.button(this, "שמור ובדוק חיבור", Util.MINT, 56, v -> saveAndCheck()),
                Util.lp(this, -1, -2, 0, 18, 0, 0));
        status = addText("", 14, Util.INK, false, 14);
        addText("הנתונים נשמרים רק בטלפון שלך. המפתח והטוקן לא נשלחים לשום מקום מלבד Google ו-GitHub.", 12, Util.MUTED, false, 14);
    }

    EditText field(String hint, String value, boolean secret) {
        addText(hint, 13, Util.MUTED, false, 12);
        EditText e = new EditText(this);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setBackground(Util.shape(this, Util.CARD, 14));
        e.setPadding(Util.dp(this, 12), Util.dp(this, 10), Util.dp(this, 12), Util.dp(this, 10));
        e.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : 0));
        e.setTextDirection(android.view.View.TEXT_DIRECTION_LTR);
        body.addView(e, Util.lp(this, -1, -2, 0, 4, 0, 0));
        return e;
    }

    void saveAndCheck() {
        Util.prefs(this).edit()
                .putString("gemini_key", gem.getText().toString().trim())
                .putString("gemini_model", model.getText().toString().trim())
                .putString("repo", repo.getText().toString().trim())
                .putString("token", token.getText().toString().trim())
                .apply();
        status.setText("בודק...");
        Util.IO.execute(() -> {
            StringBuilder sb = new StringBuilder();
            if (Gemini.key(this).isEmpty()) {
                sb.append("• Gemini: לא הוגדר מפתח\n");
            } else {
                try {
                    Gemini.askJson(this, "החזר {\"ok\":true}", null, null);
                    sb.append("• Gemini: מחובר ✔\n");
                } catch (Exception e) {
                    sb.append("• Gemini: שגיאה — ").append(e.getMessage()).append("\n");
                }
            }
            if (!Hub.configured(this)) {
                sb.append("• GitHub: לא הוגדר (חשבוניות יישמרו בטלפון בלבד)");
            } else {
                try {
                    sb.append(Hub.isPrivate(this) ? "• GitHub: מחובר, המאגר פרטי ✔"
                            : "• GitHub: המאגר ציבורי! חשבוניות לא יועלו. הפוך אותו לפרטי (Private).");
                } catch (Exception e) {
                    sb.append("• GitHub: שגיאה — ").append(e.getMessage());
                }
            }
            Util.ui(() -> status.setText(sb.toString()));
        });
    }
}
