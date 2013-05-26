package com.zhuri.andtalk;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.zhuri.slot.*;
import com.zhuri.andtalk.TalkRobot;

public class TalkService extends Service implements Runnable {
	private Thread worker = null;
	private boolean running = false;
	
	static final String TAG = "TALK";

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "onCreate");
		
		worker = new Thread(this);
		running = true;
		worker.start();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy");
		
		try {
			mAsync.toggle();
			worker.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if (running) {
			throw new RuntimeException("worker thread should not running.");
		}
		
		worker = null;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		Log.i(TAG, "onStart");
		super.onStart(intent, startId);
	}

	private SlotAsync mAsync;
	private TalkRobot mClient;

	final private Runnable mQuit = new Runnable() {
		public void run() {
			mClient.close();
			SlotThread.stop();
			return;
		}
	};

	private void initialize() {
		mClient = new TalkRobot(this);
		mClient.start();

		mAsync = new SlotAsync(mQuit);
		mAsync.setup();
		return;
	}

	@Override
	public void run() {
		Log.i(TAG, "run prepare");

        try {
            SlotThread.Init();
			initialize();
            while (SlotThread.step());
        } catch (Exception e) {
            e.printStackTrace();
			stopSelf();
        }
		
		Log.i(TAG, "run finish");
		running = false;
	}
}
