package com.zhuri.talk;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;
import android.net.wifi.WifiManager;

import com.zhuri.slot.*;
import com.zhuri.talk.RoidTalkRobot;

public class TalkService extends Service implements Runnable {
	private Thread worker = null;
	private boolean running = false;
	private WifiManager.WifiLock mWifiLock = null;
	private PowerManager.WakeLock mWakeLock = null;
	
	static final String TAG = "TALK";

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	public static Intent serviceIntent(Context context) {
		Intent intent = new Intent(context, TalkService.class);
		return intent;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "onCreate");

		WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		mWifiLock = wifiManager.createWifiLock("com.zhuri.talk");
		mWifiLock.acquire();

		PowerManager powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.zhuri.talk");
		mWakeLock.acquire();
		
        SlotThread.Init();
		worker = new Thread(this);
		running = true;
		worker.start();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy");
		
		SlotThread.quit();

		try {
			worker.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if (running) {
			throw new RuntimeException("worker thread should not running.");
		}
		
		mWifiLock.release();
		mWakeLock.release();
		worker = null;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		Log.i(TAG, "onStart");
		super.onStart(intent, startId);
	}

	private RoidTalkRobot mClient;

	private void initialize() {
		mClient = new RoidTalkRobot(this);
		mClient.start();
		return;
	}

	@Override
	public void run() {
		Log.i(TAG, "run prepare");

		initialize();
        try {
            while (SlotThread.step());
        } catch (Exception e) {
            e.printStackTrace();
			stopSelf();
        }
		
		mClient.close();
		running = false;
		Log.i(TAG, "run finish");
	}
}
