package wave.talk;

import wave.slot.SlotSlot;
import wave.slot.SlotWait;
import wave.slot.SlotTimer;
import wave.slot.SlotChannel;

import android.util.Log;
import android.os.Handler;
import android.os.Binder;
import android.os.IBinder;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.widget.Toast;
import wave.talk.protocol.Jabber;

public class JabberDaemon extends Service implements Runnable {
	private XMPPBinder binder = new XMPPBinder();
	private static final String TAG = "JabberDaemon";

	/* private Jabber talker; */
	private Thread bgworker = null;
	private Handler fghandler = null;
	private boolean selfstoped = true;
	private SlotWait stophandle = null;

	private static JabberDaemon thisOne = null;
	public static void broadcastIntent(Intent intent) {
		thisOne.sendBroadcast(intent);
		return;
	}

	private Runnable stopnow = new Runnable() {
		public void run() {
			selfstoped = false;
			SlotSlot.stop();
		}
	};

	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	@Override
	public void onCreate() {
		thisOne = this;

		bgworker = new Thread(this);
		fghandler = new Handler();
		stophandle = new SlotWait(stopnow);
		selfstoped = true;
		bgworker.start();

		//Toast.makeText(this, "My Service Created", Toast.LENGTH_LONG).show();
        //Log.d(TAG, "onCreate");
	}

	private void mainLoop() throws Exception {
		STUNPingPong pong;

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
			mainLoop();
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (selfstoped) {
			/* Toast.makeText(this, title, Toast.LENGTH_SHORT).show(); */
			stopSelf();
		}
	}

	@Override
	public void onDestroy() {
		//Toast.makeText(this, "My Service onDestroy", Toast.LENGTH_LONG).show();
        //Log.d(TAG, "onDestroy");

		if (stophandle != null) {
			stophandle.ipcSchedule();
			stophandle = null;
		}

		try {
			bgworker.join(); 
		} catch (Exception e) {
			e.printStackTrace();
		}

		thisOne = null;
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
		//Toast.makeText(this, "My Service started", Toast.LENGTH_LONG).show();
        //Log.d(TAG, "onStarted");
	}

	public class XMPPBinder extends Binder {
		private Jabber client;

		JabberDaemon getService() {
			return JabberDaemon.this;
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

				fghandler.post(this);
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
				Intent calling = new Intent(JabberDaemon.this, RecordActivity.class);
				calling.putExtra("jid", jid);
				calling.putExtra("name", "unkown");
				calling.putExtra("action", action);
				return calling;
			}
	}
}

