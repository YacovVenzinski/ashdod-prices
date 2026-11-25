package il.yaakov.pricecompare;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.util.ArrayList;
import java.util.List;

/** Google's on-device document scanner (needs Google Play Services, no camera permission). */
final class DocScan {
    private DocScan() {}

    static void start(Activity a, int req, int pages) {
        GmsDocumentScannerOptions o = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(pages)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();
        GmsDocumentScanning.getClient(o).getStartScanIntent(a)
                .addOnSuccessListener(sender -> {
                    try {
                        a.startIntentSenderForResult(sender, req, null, 0, 0, 0);
                    } catch (Exception e) {
                        Util.toast(a, "לא ניתן לפתוח את הסורק");
                    }
                })
                .addOnFailureListener(e -> Util.toast(a, "הסורק לא זמין (נדרש Google Play Services)"));
    }

    static List<Uri> pages(Intent data) {
        List<Uri> l = new ArrayList<>();
        GmsDocumentScanningResult r = GmsDocumentScanningResult.fromActivityResultIntent(data);
        if (r != null && r.getPages() != null) {
            for (GmsDocumentScanningResult.Page p : r.getPages()) l.add(p.getImageUri());
        }
        return l;
    }
}
