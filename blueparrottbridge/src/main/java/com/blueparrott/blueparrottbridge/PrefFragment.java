package com.blueparrott.blueparrottbridge;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.blueparrott.blueparrottbridge.R;


public class PrefFragment extends PreferenceFragmentCompat {

    Preference mPrefDevMode;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_prefs, rootKey);
    }
}
