package com.zhuri.talk.ui;

import com.zhuri.talk.R;
import android.util.Log;
import android.util.Base64;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.preference.PreferenceActivity;

public class TalkRobotSettings extends PreferenceActivity {
	static final String LOG_TAG ="TalkRobotSettings";

	SharedPreferences prefs = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.talk_robot_settings);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

}
