package com.adnet.webrtc.ui;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.adnet.webrtc.R;

/**
 * Settings fragment for AppRTC.
 */
@SuppressWarnings("ALL")
public class SettingsFragment extends PreferenceFragment {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Load the preferences from an XML resource
    addPreferencesFromResource(R.xml.preferences);
  }
}
