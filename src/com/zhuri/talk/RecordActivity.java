package com.zhuri.talk;

import java.io.*;
import java.util.*;
import android.util.Log;
import android.os.SystemClock;
import android.text.Editable;
import android.app.Activity;
import android.app.TabActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.text.method.*;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.media.MediaRecorder.*;
import android.media.MediaRecorder.AudioSource.*;
import android.widget.AdapterView.OnItemClickListener;
import android.view.View.OnClickListener;
import android.graphics.drawable.Drawable;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import com.zhuri.talk.TalkCoreService.XMPPBinder;

import android.os.Message;
import android.os.Handler;
import org.w3c.dom.*;
import com.zhuri.slot.SlotWait;
import com.zhuri.slot.SlotTimer;
import com.zhuri.slot.SlotSlot;
import com.zhuri.slot.SlotChannel;
import com.zhuri.slot.SlotRunner;
import com.zhuri.util.ShineWrapper;
import com.zhuri.util.MadPlayer;
import android.util.Base64;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast; 
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

import java.net.*;
import java.util.*;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import com.zhuri.talk.protocol.Jabber;
import com.zhuri.talk.STUNPingPong.Negotiatable;

public class RecordActivity extends Activity
	implements OnClickListener, Runnable, Negotiatable
{
	private String action = null;
	private SlotSlot stuned = null;
	private Recorder recorder = null;
	private Jabber talker = null;
	private DatagramChannel datagram = null;
	private static final String PREFS_NAME = "GoogleTalker";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.recorder);

		stuned = new SlotSlot();
		action = getIntent().getStringExtra("action");

		if (!action.equals("calling"))
			phoneCallNotify(getIntent());

		Button startButton = (Button)findViewById(R.id.start_record);
		startButton.setText(action.equals("calling")? "Call": "Receive");
		startButton.setOnClickListener(this);
		STUNPingPong.startPhoneCall();

		Button stopButton = (Button)findViewById(R.id.stop_record);
		this.setTitle("to" + getIntent().getStringExtra("name") + "calling");
		stopButton.setOnClickListener(this);

		try {
			datagram = DatagramChannel.open();
			datagram.socket().bind(null);
			datagram.socket().setReceiveBufferSize(8192);
		} catch (Exception e) {
			e.printStackTrace();
		}

		//bindService(new Intent(this, TalkCoreService.class), connection, BIND_AUTO_CREATE);
		talker = Jabber.getSticky();
		recorder = Recorder.create(datagram);
		connected = false;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		handler.removeCallbacks(recdog);
		handler.removeCallbacks(update);

		play = false;
		recorder.stop();
		ipchandler.ipcSchedule();

		try {
			datagram.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

        NotificationManager manager =
            (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(notification_id);
		STUNPingPong.finishPhoneCall();
		//unbindService(connection);
	}

	boolean play = false;
	boolean playing = false;
	boolean peerrcv = false;
	boolean started = false;
	Handler handler = new Handler();
	SlotWait ipchandler = new SlotWait(this);

    public void onClick(View view) {		
        String title;
        NotificationManager manager;

		switch (view.getId()) {
			case R.id.start_record:
				if (play == false) {
					while(playing);
					playing = play = true;
					ipchandler.ipcSchedule();
					setTitle("calling");
				}
        		manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		        manager.cancel(notification_id);
          		break;

			case R.id.stop_record:
				if (play == true) {
					play = false;
					ipchandler.ipcSchedule();
					while(playing);
				}
        		manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		        manager.cancel(notification_id);
				finish();
          		break;

			default:
				return;
		}
	}

	InetSocketAddress peerAddress = null;
	STUNPingPong.Client client = null;

	class Negotiator extends SlotWait implements Runnable {
		private int count = 0;
		private InetSocketAddress peer;

		SlotWait redo = new SlotWait(this);
		public void run() {
			invoke();
			return;
		}

		public void invoke() {
			if (redo.completed()) {
				byte[] ping = ("PING " + peer.toString().replaceFirst(".*/", "kitty/")).getBytes();
				Log.i("jabber", "Negotiator re send " + peer.toString());
				try {
					datagram.send(ByteBuffer.wrap(ping), peer);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			if (this.completed()) {
				redo.clean();
				return;
			} else if (count++ < 300) {
				delay(2000);
				return;
			}
		}

		public Negotiator(InetSocketAddress address) {
			peer = address;
		}

		public void delay(int timo) {
			SlotTimer.reset(redo, timo);
			return;
		}
	}

	java.util.HashMap<String, Negotiator> allNegotiates = new java.util.HashMap<String, Negotiator>();
	public void onNegotiated(InetSocketAddress address) {
		String data = address.toString().replaceFirst(".*/", "kitty/");
		if (!allNegotiates.containsKey(data) && !peerrcv) {
			byte[] ping = ("PING " + data).getBytes();
			try {
				datagram.send(ByteBuffer.wrap(ping), address);
			} catch (Exception e) {
				e.printStackTrace();
			}
			android.util.Log.i("jabber", "PING " + address.toString());
			Negotiator redo = new Negotiator(address);
			allNegotiates.put(data, redo);
			stuned.record(redo);
			redo.delay(1000);
		}
	}

	boolean connected = false;
	Runnable recdog = new Runnable() {
		public void run() {
			connected = true;
			setTitle("	recording ");
			recorder.start(peerAddress);
			return ;
		}
	};

	private void quietSendto(DatagramChannel datagram, ByteBuffer buffer, InetSocketAddress target) {
		try {
			datagram.send(buffer, target);
		} catch (IOException e) {
			Log.e("jabber", "quietSendto", e);
		}
	}

	private SlotChannel slot;
	private SlotWait readin = new SlotWait() {
		InetSocketAddress isa;

		public void invoke() {
			int limit;
			ByteBuffer buffer = ByteBuffer.allocate(2048);
			slot.wantIn(readin);

			try {
				isa = (InetSocketAddress)datagram.receive(buffer);
				buffer.flip();
			} catch (Exception e) {
				Log.e("jabber", "STUN", e);
				return;
			}

			limit = buffer.limit();

			if (limit > 4) {
				String tag;
				byte[] buf = new byte[4];
				buffer.mark();
				buffer.get(buf);
				tag = new String(buf);

				if (tag.equals("PING")) {
					buf = ("PONG " + isa.toString().replaceFirst(".*/", "kitty/")).getBytes();
					android.util.Log.i("jabber", "ping " + isa.toString());
					InetSocketAddress address = parseInetSocketAddress(buffer);
					android.util.Log.i("jabber", "pong to " + address.toString());
					quietSendto(datagram, ByteBuffer.wrap(buf), isa);
				} else if (tag.equals("PONG")) {
					android.util.Log.i("jabber", "pong " + isa.toString());
					InetSocketAddress address = parseInetSocketAddress(buffer);
					android.util.Log.i("jabber", "pong from " + address.toString());
					client.SendThirdAddress(address);
					start(isa);
				}
				buffer.reset();
			}

			if (limit > 20 && limit < 48) {
				client.stun_event(buffer);
				return;
			}


			if (started && limit > 48) {
				recorder.writeAudio(buffer);
				return;
			}

			android.util.Log.i("jabber", "unkown packet from " + isa.toString());
		}
	};

	InetSocketAddress parseInetSocketAddress(ByteBuffer buffer) {
		String port, domain;
		int count = buffer.limit() - buffer.position();
		byte[] data = new byte[count];
		buffer.get(data);
		String addr = new String(data);
		int part1 = addr.indexOf("/");
		if (part1 > 0)
			addr = addr.substring(part1 + 1);
		int part2 = addr.indexOf(":");
		if (part2 > 0) {
			domain = addr.substring(0, part2);
			port = addr.substring(part2 + 1);
			try {
				return com.zhuri.util.InetUtil.getInetSocketAddress(domain, Integer.parseInt(port));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public void start(InetSocketAddress isa) {
		if (playing && !peerrcv) {
			peerrcv = true;
			peerAddress = isa;
			handler.post(recdog);
		}
		stuned.wakeup();
	}

	private Runnable update = new Runnable() {
		public void run() {
			if (!connected) {
				String message = client.notifyMessage;
				RecordActivity.this.setTitle(message);
			}
			return;
		}
	};

	public void run() {
		String jid;
		String sid;

		if (play && !started) {
			slot = new SlotChannel();
			jid = getIntent().getStringExtra("jid");
			try { slot.attach(datagram); } catch (Exception e) {};
			slot.wantIn(readin);
			if (action.equals("calling")) {
				client = STUNPingPong.getClient(talker, datagram);
				client.start(jid, this);
			} else {
				sid = STUNPingPong.answerPhoneCall(true);
				client = STUNPingPong.getClient(talker, datagram);
				client.started(jid, sid, this);
			}
			handler.postDelayed(update, 1000);
			recorder.createPlayer((AudioManager)getSystemService(AUDIO_SERVICE));
			started = true;
		}

		if (!play && playing) {
			if (started == true) {
				started = false;
				recorder.closePlayer();
				readin.clean();
				client.close();
				slot.detach();
			}
			playing = false;
		}
	}

	int notification_id = 19821130;
    /* @mark */
    private void phoneCallNotify(Intent intent) {
        int icon = R.drawable.icon;
        long millis = System.currentTimeMillis();
        String title = "softPhone";
        String content = "wait answering";
        String trickertext = "XMPP calling";
        Notification notification = new Notification(icon, trickertext, millis);
        notification.defaults = Notification.DEFAULT_ALL;
        PendingIntent pt = PendingIntent.getActivity(this, 0, intent, 0);
        notification.setLatestEventInfo(this, title, content, pt);
        NotificationManager manager =
            (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(notification_id, notification);
    }


	private ServiceConnection connection = new ServiceConnection() {
		private XMPPBinder binder;
        public void onServiceConnected(ComponentName name, IBinder service) {  
			binder = (XMPPBinder)service;
			talker = binder.getClient("");
        }  
          
        public void onServiceDisconnected(ComponentName name) {  
			binder = null;
			talker = null;
        }  
    };  
}

class Recorder implements Runnable {
	private boolean started = false;
	private boolean playing = false;
	private Thread feedback = null;
	private InetSocketAddress peeraddr = null;
	private DatagramChannel datagram = null;

	private int freq = 8000;
	private int nframe = 1152; 
	private int format = AudioFormat.ENCODING_PCM_16BIT;
	private int channel = AudioFormat.CHANNEL_IN_MONO;
	private int channelout = AudioFormat.CHANNEL_OUT_MONO;

	private int saveAudioMode = 0;
	private AudioManager audioManager = null;

	public static Recorder create(DatagramChannel datagram) {
		Recorder record = new Recorder();
		record.datagram = datagram;
		return record;
	}

	public void start(InetSocketAddress address) {
		if (started == false) {
			feedback = new Thread(this);
			while (playing);
			started = true;
			peeraddr = address;
			feedback.start();
		}
	}

	private int audiosize = 0;
	private int getAudioSize() {
		return audiosize;
	}

	private static int[] mSampleRates = new int[] { 44100, 22050, 1600, 11025, 8000 };
	public AudioRecord findAudioRecord() {
		AudioRecord recorder = null;
		
    	for (int rate : mSampleRates) {
        	for (short audioFormat : new short[] { AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_8BIT}) {
            	for (short channelConfig : new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO }) {
                	try {
                    	int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
                    	if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                    		android.util.Log.e("jabber", "sampleRate: " + rate + " format: " + audioFormat + " config: " + channelConfig);
							if (recorder == null) {
								audiosize = bufferSize;
                        		recorder = new AudioRecord(AudioSource.DEFAULT, rate, channelConfig, audioFormat, bufferSize);
                        		if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
									recorder = null;
							}
                    	}
                	} catch (Exception e) {
                    	android.util.Log.e("jabber", rate + "Exception, keep trying.",e);
                	}
            	}
        	}
    	}

    	return recorder;
	}

	public void run() {
		int offset;
		int bufsize;
		byte[] buffer;
		byte[] output;
		ByteBuffer sndbuf;
		AudioRecord record;
		ShineWrapper wrapper;

		bufsize = AudioRecord.getMinBufferSize(freq, channel, format);
		if (bufsize != AudioRecord.ERROR_BAD_VALUE) {
			bufsize = nframe * ((bufsize + nframe - 1) / nframe);
			offset  = 0;

			record = new AudioRecord(AudioSource.MIC, freq, channel, format, bufsize);
		} else {
			record = findAudioRecord();
			bufsize = getAudioSize();
			offset = 0;
		}

		output = new byte[4096];
		buffer = new byte[bufsize];
		wrapper = new ShineWrapper();
		sndbuf  = ByteBuffer.allocate(1460);

		record.startRecording();
		while (started) {
			int count = record.read(buffer, 0, nframe);
			try {
				count = wrapper.encodeFrame(buffer, count, output, 4096);
				if (offset + count < 1152) {
					sndbuf.put(output, 0, count);
					offset += count;
					count = 0;
				}

				if (offset > 50) {
					sndbuf.flip();
					datagram.send(sndbuf, peeraddr);
					sndbuf.position(0);
					sndbuf.limit(1460);
					offset = 0;
				}

				sndbuf.put(output, 0, count);
			} catch (Exception e) {
				e.printStackTrace();
			}
		};
		
		record.stop();
		record.release();
		wrapper.release();
		playing = false;
	}

	public void stop() {
		started = false;
		while (playing);
	}

	MadPlayer player;
	AudioTrack track;
	public void createPlayer(AudioManager manager) {
		int bufsize;
		byte[] buffer;
		player = new MadPlayer();
		bufsize = AudioTrack.getMinBufferSize(freq, channelout, format);
/*
		if (bufsize < 4096)
			bufsize = 4096;
*/

		audioManager = manager;
		saveAudioMode = audioManager.getMode();
		audioManager.setSpeakerphoneOn(true);
		//audioManager.setMode(AudioManager.MODE_IN_CALL);

		track = new AudioTrack(AudioManager.STREAM_VOICE_CALL, freq,
					channelout, format, bufsize, AudioTrack.MODE_STREAM);
	}

	boolean playStarted = false;
	byte[] audioData = new byte[16384];
	public void writeAudio(ByteBuffer buffer) {
		int count = buffer.limit();

		if (!playStarted) {
			playStarted = true;
			track.play();
		}
		player.write(buffer.array(), 0, count);

		count = player.read(audioData);
		while (count > 0) {
			track.write(audioData, 0, count);
			count = player.read(audioData);
		}

		return;
	}

	public void closePlayer() {
		track.stop();
		track.release();
		player.release();
		audioManager.setMode(saveAudioMode);
	}

 	OnRecordPositionUpdateListener recupdate = new OnRecordPositionUpdateListener() {
		public void onMarkerReached(AudioRecord recoder) {
			//finish();
		}

		public void onPeriodicNotification(AudioRecord recoder) {
			//finish();
		}
	};
}

