package amirz.dngprocessor;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public class Settings {
    private static final String PREF_MANUAL_SELECT = "pref_manual_select";
    private static final String PREF_BACKGROUND_PROCESS = "pref_background_process";
    private static final String PREF_DELETE_ORIGINAL = "pref_delete_original";

    public static class Fragment extends PreferenceFragment {
        private MainActivity mActivity;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mActivity = (MainActivity) getActivity();

            getPreferenceManager().setSharedPreferencesName(mActivity.getPackageName());
            addPreferencesFromResource(R.xml.preferences);

            findPreference(PREF_MANUAL_SELECT).setOnPreferenceClickListener(mActivity::requestImage);
        }
    }

    public static boolean backgroundProcess(Context context) {
        return Utilities.prefs(context).getBoolean(PREF_BACKGROUND_PROCESS, true);
    }

    public static boolean deleteOriginal(Context context) {
        return Utilities.prefs(context).getBoolean(PREF_DELETE_ORIGINAL, false);
    }
}
