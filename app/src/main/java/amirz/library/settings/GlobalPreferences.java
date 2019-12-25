package amirz.library.settings;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that provides synchronized preferences using the singleton design pattern.
 * Extensions should add a static getInstance() method.
 */
public abstract class GlobalPreferences {
    private static final String TAG = "GlobalPreferences";
    private final SparseArray<Ref> mPreferences = new SparseArray<>();

    /**
     * Loads all preferences from the SharedPreferences instance.
     * @param prefs Instance from which the data is pulled.
     * @param res Resources used to deserialize the default values as fallback values.
     */
    public void applyAll(SharedPreferences prefs, Resources res) {
        for (int i = 0; i < mPreferences.size(); i++) {
            // noinspection unchecked
            apply(prefs, res, mPreferences.valueAt(i));
        }
    }

    /**
     * Loads one key's preference from the SharedPreferences instance.
     * @param prefs Instance from which the data is pulled.
     * @param res Resources used to deserialize the default value as a fallback value.
     * @param tunable Reference to preference.
     * @return New value of the preference.
     */
    public <T> T apply(SharedPreferences prefs, Resources res, Ref<T> tunable) {
        String key = res.getString(tunable.settingId);
        TypedValue defaultValue = new TypedValue();
        res.getValue(tunable.defaultId, defaultValue, true);

        Log.d(TAG, "Updating " + key);
        tunable.load(prefs, key, defaultValue);
        return tunable.get();
    }

    public final class ResetContext implements AutoCloseable {
        private final SharedPreferences mPrefs;
        private final Resources mRes;
        private final List<String> mReset = new ArrayList<>();

        private ResetContext(SharedPreferences prefs, Resources res) {
            mPrefs = prefs;
            mRes = res;
        }

        public <T> void reset(Ref<T> tunable) {
            mReset.add(mRes.getString(tunable.settingId));
        }

        @Override
        public void close() {
            SharedPreferences.Editor edit = mPrefs.edit();
            for (String reset : mReset) {
                edit.remove(reset);
            }
            edit.apply();
            GlobalPreferences.this.applyAll(mPrefs, mRes);
        }
    }

    public interface ResetContextFunc {
        void onReset(ResetContext ctx);
    }

    public void reset(SharedPreferences prefs, Resources res, ResetContextFunc todo) {
        try (ResetContext ctx = new ResetContext(prefs, res)) {
            todo.onReset(ctx);
        }
    }

   /**
    * Referenced setting that holds a boolean.
    */
    public final class BooleanRef extends Ref<Boolean> {
        public BooleanRef(int settingId, int defaultId) {
            super(settingId, defaultId);
        }

        @Override
        void load(SharedPreferences prefs, String key, TypedValue defaultValue) {
           value = prefs.getBoolean(key, defaultValue.data == 1);
        }
    }

    /**
     * Referenced setting that holds a floating point number.
     */
    public final class FloatRef extends Ref<Float> {
        public FloatRef(int settingId, int defaultId) {
            super(settingId, defaultId);
        }

        @Override
        void load(SharedPreferences prefs, String key, TypedValue defaultValue) {
            String defaultString = defaultValue.coerceToString().toString();
            value = Float.valueOf(prefs.getString(key, defaultString));
        }
    }

    /**
     * Referenced setting that holds an integer.
     */
    public final class IntegerRef extends Ref<Integer> {
        public IntegerRef(int settingId, int defaultId) {
            super(settingId, defaultId);
        }

        @Override
        void load(SharedPreferences prefs, String key, TypedValue defaultValue) {
            String defaultString = defaultValue.coerceToString().toString();
            value = Integer.valueOf(prefs.getString(key, defaultString));
        }
    }

    /**
     * Referenced setting that holds a string.
     */
    public final class StringRef extends Ref<String> {
        public StringRef(int settingId, int entries) {
            super(settingId, entries);
        }

        @Override
        void load(SharedPreferences prefs, String key, TypedValue defaultValue) {
            String defaultString = defaultValue.coerceToString().toString();
            value = prefs.getString(key, defaultString);
        }
    }

    private abstract class Ref<T> {
        T value;
        final int settingId;
        final int defaultId;

        Ref(int settingId, int defaultId) {
            this.settingId = settingId;
            this.defaultId = defaultId;
            mPreferences.append(settingId, this);
        }

        public T get() {
            return value;
        }

        abstract void load(SharedPreferences prefs, String key, TypedValue defaultValue);
    }

    /**
     * Empty constructor that prevents direct instantiation of this class.
     */
    protected GlobalPreferences() {
    }
}
