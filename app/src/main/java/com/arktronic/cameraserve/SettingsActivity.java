package com.arktronic.cameraserve;

import android.content.SharedPreferences;
import android.hardware.Camera;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener  {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        populateCameras();

        updateSummaries();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        EditTextPreference portPref = (EditTextPreference) findPreference("port");
        try {
            Integer.parseInt(portPref.getText());
        } catch (NumberFormatException nfe) {
            portPref.setText("8080");
            sharedPreferences.edit().putString("port", "8080").apply();
        }

        populateResolutions();
        updateSummaries();
    }

    private void populateCameras() {
        ListPreference camPref = (ListPreference) findPreference("cam");
        int cams = Camera.getNumberOfCameras();
        List<CharSequence> camEntries = new ArrayList<>(),
                camEntryValues = new ArrayList<>();
        for(int i = 0; i< cams; i++) {
            camEntries.add(String.valueOf("Cam " + (i+1)));
            camEntryValues.add(String.valueOf(i));
        }
        CharSequence[] entries = new CharSequence[camEntries.size()],
                       entryValues = new CharSequence[camEntryValues.size()];
        entries = camEntries.toArray(entries);
        entryValues = camEntryValues.toArray(entryValues);
        camPref.setEntries(entries);
        camPref.setEntryValues(entryValues);

        populateResolutions();
    }

    private void populateResolutions() {
        ListPreference camPref = (ListPreference) findPreference("cam");
        int camId = Integer.parseInt(camPref.getValue());
        ListPreference resPref = (ListPreference) findPreference("resolution");

        List<Camera.Size> sizes = MainActivity.getCameraSizes().get(camId);

        List<CharSequence> resEntries = new ArrayList<>(),
                resEntryValues = new ArrayList<>();
        for(int i = 0; i < sizes.size(); i++) {
            Camera.Size s = sizes.get(i);
            resEntries.add(s.width + "x" + s.height);
            resEntryValues.add(s.width + "x" + s.height);
        }
        CharSequence[] entries = new CharSequence[resEntries.size()],
                entryValues = new CharSequence[resEntryValues.size()];
        entries = resEntries.toArray(entries);
        entryValues = resEntryValues.toArray(entryValues);
        resPref.setEntries(entries);
        resPref.setEntryValues(entryValues);
    }

    private void updateSummaries() {
        EditTextPreference portPref = (EditTextPreference) findPreference("port");
        portPref.setSummary("Currently set to " + portPref.getText());

        ListPreference camPref = (ListPreference) findPreference("cam");
        camPref.setSummary("Cam " + (Integer.parseInt(camPref.getValue()) + 1));

        ListPreference resPref = (ListPreference) findPreference("resolution");
        resPref.setSummary(resPref.getValue().replace("x", " x "));

        ListPreference rotPref = (ListPreference) findPreference("rotation");
        rotPref.setSummary(rotPref.getValue());
    }
}
