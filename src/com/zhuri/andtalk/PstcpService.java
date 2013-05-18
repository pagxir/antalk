package com.zhuri.andtalk;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import com.zhuri.pstcp.AppFace;

public class PstcpService extends Service implements Runnable {
	private int PORT = 1800;
	private Thread worker = null;
	private boolean exited = true;
	private boolean running = false;
	
	static final String TAG = "PSTCP";

	@Override
	public IBinder onBind(Intent arg0) {
		Toast.makeText(this , "Hello World", Toast.LENGTH_SHORT);
		return null;
	}
	
	@Override
	public void onCreate() {
		Log.i(TAG, "onCreate");
		super.onCreate();
		
		worker = new Thread(this);
		exited = false;
		running = true;
		worker.start();
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy");
		super.onDestroy();
		
		try {
			exited = true;
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

	@Override
	public void run() {
		AppFace.start();
		
		Log.i(TAG, "run prepare");
		while (!exited) {
			AppFace.loop();
		}
		
		Log.i(TAG, "run finish");
		AppFace.stop();
		running = false;
	}
}
