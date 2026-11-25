package il.yaakov.pricecompare;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Data.init(this);
        Receipt.init(this);
    }
}
