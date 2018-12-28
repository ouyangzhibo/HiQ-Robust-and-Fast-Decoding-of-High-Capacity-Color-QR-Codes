/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import edu.cuhk.ie.authbarcodescanner.android.R;


public class SettingFragment extends Activity {

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new fragmentSetting()).commit();
    }

	private class fragmentSetting extends PreferenceFragment implements OnSharedPreferenceChangeListener {
		private final static String TAG = "fragmentSetting";
		private String analyticsIndex, analyticsSummary;
		
		@Override
	    public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);	        
	        analyticsIndex=getString(R.string.pref_key_analytics);
	        analyticsSummary=getString(R.string.setting_analytics_summary);
	        // Load the preferences from an XML resource
	        addPreferencesFromResource(R.xml.preferences);
	        PreferenceManager.setDefaultValues(this.getActivity(), R.xml.preferences, false);
	        updateSummary();
	        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	    }
		
		private void updateSummary() {			
			Preference analyticPref = findPreference(analyticsIndex);
			ListPreference analyticPrefList = (ListPreference) analyticPref;
			analyticPref.setSummary(analyticsSummary + " " + analyticPrefList.getEntry());
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			Log.d(TAG, "Changed preference on " + key);						
			if(key.equals(analyticsIndex)) updateSummary();			
		}		
	}
}