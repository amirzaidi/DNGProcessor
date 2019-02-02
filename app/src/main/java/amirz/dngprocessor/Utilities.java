package amirz.dngprocessor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

public class Utilities {
    public static final boolean ATLEAST_OREO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

    /**
     * Globally used preferences.
     * @param context Context instance used to retrieve the {@link SharedPreferences} instance.
     * @return Single {@link SharedPreferences} instance that is used by the application.
     */
    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
    }
}
