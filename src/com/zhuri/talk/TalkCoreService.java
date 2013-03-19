package com.zhuri.talk;

import com.zhuri.slot.SlotSlot;
import com.zhuri.slot.SlotWait;
import com.zhuri.slot.SlotTimer;
import com.zhuri.slot.SlotChannel;

import android.util.Log;
import android.os.Handler;
import android.os.Binder;
import android.os.IBinder;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.widget.Toast;
import com.zhuri.talk.protocol.Jabber;

public class TalkCoreService extends Service {
	private static final String LOG_TAG = "TalkCoreService";

	private TalkThread mProcessor = null;
	private XMPPBinder mBinder = new XMPPBinder();

	private static TalkCoreService mInstance = null;

	public static void broadcastIntent(Intent intent) {
		mInstance.sendBroadcast(intent);
		return;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
		Log.d(LOG_TAG, "TalkCoreService started");
	}

	@Override
	public void onCreate() {
		mInstance = this;

		mProcessor = new TalkThread();
		mProcessor.start();

		Log.d(LOG_TAG, "TalkCoreService created");
	}

	@Override
	public void onDestroy() {
		Log.d(LOG_TAG, "TalkCoreService destroy");

		mProcessor.quit();
	}

	class TalkThread extends Thread {

		void loop() throws Exception {

			SlotSlot.init();
			SlotTimer.init();
			SlotChannel.init();
			SlotWait.ipc_init();
			VoiceCall.init();

			while (SlotSlot.step());

			VoiceCall.fini();
			SlotWait.ipc_fini();
			SlotChannel.fini();
			/* SlotTimer.fini(); */
			SlotSlot.fini();
		}

		public void run() {
			String title = "jabber exited unnormal";

			try {
				loop();
			} catch (Exception e) {
				e.printStackTrace();
				stopSelf();
			}
		}

		public void quit() {
			this.join();
		}
	}

	public class XMPPBinder extends Binder {
		private Jabber client;

		TalkCoreService getService() {
			return TalkCoreService.this;
		}

		Jabber getClient(String name) {
			return client;
		}

		void setClient(Jabber _client) {
			client = _client;
		}
	}

	class PhoneBack implements Runnable, STUNPingPong.IPhoneBack {
			private String sid;
			private String jid;
			private Jabber client;
			private boolean idling;

			public synchronized void invoke(Jabber _client, String _sid, String sender) {
				sid = _sid;
				jid = sender;
				client = _client;
				idling = false;

				/* fghandler.post(this); */
				while (!idling) {
					try {
						this.wait();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			public synchronized void run() {
				Intent calling = doPhoneCalling("answer", jid);
				idling = true;
				Jabber.setSticky(client);
				calling.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
				startActivity(calling);
				this.notify();
			}

			public Intent doPhoneCalling(String action, String jid) {
				Intent calling = new Intent(TalkCoreService.this, RecordActivity.class);
				calling.putExtra("jid", jid);
				calling.putExtra("name", "unkown");
				calling.putExtra("action", action);
				return calling;
			}
	}
}

