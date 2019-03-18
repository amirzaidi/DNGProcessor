package amirz.library.settings;

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.TextUtils;
import android.util.AttributeSet;

public class TextPreference extends EditTextPreference {
    public TextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getSummary() {
        CharSequence summary = super.getSummary();
        if (TextUtils.isEmpty(summary)) {
            return getText();
        }
        return getText() + " " + summary;
    }
}
