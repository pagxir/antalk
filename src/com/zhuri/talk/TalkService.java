package com.zhuri.talk;

import android.os.IBinder;
import android.app.Service;
import android.content.Intent;

public class TalkService extends Service {

	private static final String TAG = "TalkService";

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onStart(Intent intent, int startid) {
		super.onStart(intent, startid);
	}
}
