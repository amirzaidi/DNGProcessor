package amirz.dngprocessor;

import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.text.TextUtils;

import amirz.dngprocessor.scheduler.DngParseService;
import amirz.dngprocessor.util.Utilities;
import amirz.library.settings.GlobalPreferences;

public class Preferences extends GlobalPreferences {
    private static final Preferences sInstance = new Preferences();

    public static Preferences global() {
        return sInstance;
    }

    /*
     * FILES
     */

    public final BooleanRef backgroundProcess =
            new BooleanRef(R.string.pref_background_process,
                    R.bool.pref_background_process_default);

    public final BooleanRef deleteOriginal =
            new BooleanRef(R.string.pref_delete_original,
                    R.bool.pref_delete_original_default);

    public final BooleanRef suffix =
            new BooleanRef(R.string.pref_suffix,
                    R.bool.pref_suffix_default);

    public final StringRef savePath =
            new StringRef(R.string.pref_save_path,
                    R.string.pref_save_path_default);

    /*
     * SATURATION
     */

    public final FloatRef saturationRed =
            new FloatRef(R.string.pref_saturation_r,
                    R.integer.pref_saturation_default);

    public final FloatRef saturationYellow =
            new FloatRef(R.string.pref_saturation_y,
                    R.integer.pref_saturation_default);

    public final FloatRef saturationGreen =
            new FloatRef(R.string.pref_saturation_g,
                    R.integer.pref_saturation_default);

    public final FloatRef saturationCyan =
            new FloatRef(R.string.pref_saturation_c,
                    R.integer.pref_saturation_default);

    public final FloatRef saturationBlue =
            new FloatRef(R.string.pref_saturation_b,
                    R.integer.pref_saturation_default);

    public final FloatRef saturationIndigo =
            new FloatRef(R.string.pref_saturation_i,
                    R.integer.pref_saturation_default);

    public final FloatRef saturationViolet =
            new FloatRef(R.string.pref_saturation_v,
                    R.integer.pref_saturation_default);

    public final FloatRef saturationMagenta =
            new FloatRef(R.string.pref_saturation_m,
                    R.integer.pref_saturation_default);

    public final FloatRef saturationLimit =
            new FloatRef(R.string.pref_saturation_limit,
                    R.integer.pref_saturation_limit_default);

    /*
     * PIPELINE
     */

    public final StringRef processMode =
            new StringRef(R.string.pref_post_process,
                    R.string.pref_post_process_default);

    public final BooleanRef noiseReduce =
            new BooleanRef(R.string.pref_noise_reduce,
                    R.bool.pref_noise_reduce_default);

    public final BooleanRef lce =
            new BooleanRef(R.string.pref_lce,
                    R.bool.pref_lce_default);

    public final BooleanRef ahe =
            new BooleanRef(R.string.pref_ahe,
                    R.bool.pref_ahe_default);

    public final BooleanRef forwardMatrix =
            new BooleanRef(R.string.pref_forward_matrix,
                    R.bool.pref_forward_matrix_default);

    public final BooleanRef gainMap =
            new BooleanRef(R.string.pref_gain_map,
                    R.bool.pref_gain_map_default);

    public enum PostProcessMode {
        Disabled,
        Natural,
        Boosted
    }

    public static class Fragment extends PreferenceFragment {
        private MainActivity mActivity;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mActivity = (MainActivity) getActivity();

            getPreferenceManager().setSharedPreferencesName(mActivity.getPackageName());
            addPreferencesFromResource(R.xml.preferences);

            findPreference(getString(R.string.pref_manual_select))
                    .setOnPreferenceClickListener(mActivity::requestImage);

            findPreference(getString(R.string.pref_reprocess))
                    .setOnPreferenceClickListener(p -> {
                        String uri = Utilities.prefs(mActivity)
                                .getString(mActivity.getString(R.string.pref_reprocess), "");
                        if (!TextUtils.isEmpty(uri)) {
                            DngParseService.runForUri(mActivity, Uri.parse(uri));
                        }
                        return false;
                    });

            findPreference(getString(R.string.pref_saturation_reset))
                    .setOnPreferenceClickListener(p -> {
                        Preferences pref = global();
                        pref.reset(Utilities.prefs(mActivity), mActivity.getResources(), ctx -> {
                            ctx.reset(pref.saturationRed);
                            ctx.reset(pref.saturationYellow);
                            ctx.reset(pref.saturationGreen);
                            ctx.reset(pref.saturationCyan);
                            ctx.reset(pref.saturationBlue);
                            ctx.reset(pref.saturationIndigo);
                            ctx.reset(pref.saturationViolet);
                            ctx.reset(pref.saturationMagenta);
                        });
                        mActivity.recreate();
                        return true;
                    });
        }
    }

    public static PostProcessMode postProcess() {
        return PostProcessMode.valueOf(global().processMode.get());
    }
}
