
package com.orca.backup;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import eu.chainfire.libsuperuser.Shell;

public class Preferences extends PreferenceActivity {

    CheckBoxPreference mPermanentStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs_main);
        mPermanentStorage = (CheckBoxPreference) findPreference("perm_storage");
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {

        if (preference == mPermanentStorage) {
            boolean checked = ((CheckBoxPreference) preference).isChecked();

            if (checked) {
                // move from /sdcard/Android
                Shell.SU.run("mv /sdcard/Android/data/com.orca.backup/files/backups /sdcard/ORCA_Backup/");
            } else {
                // move to /sdcard/Data
                Shell.SU.run("mv /sdcard/ORCA_Backup/ /sdcard/Android/data/com.orca.backup/files/backups");

            }

            return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

}
