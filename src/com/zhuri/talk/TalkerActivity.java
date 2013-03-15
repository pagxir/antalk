package com.zhuri.talk;

import java.io.*;
import java.util.*;
import com.zhuri.talk.*;
import android.util.Log;
import com.zhuri.slot.SlotChannel;
import java.nio.ByteBuffer;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import com.zhuri.talk.TalkCoreService.XMPPBinder;

import android.os.Bundle;
import android.content.Context;
import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.view.View.OnClickListener;

import android.widget.Toast; 
import android.widget.CheckBox;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.OnItemClickListener;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

import android.text.method.*;
import android.graphics.drawable.Drawable;

import org.w3c.dom.*;
import com.zhuri.slot.SlotWait;
import com.zhuri.slot.SlotRunner;
import com.zhuri.talk.VoiceActivity;
import com.zhuri.talk.protocol.Jabber;
import com.zhuri.talk.protocol.FastXmlVisitor;
import android.os.IBinder;
import android.os.Handler;
import android.util.Base64;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.content.Intent;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.ServiceConnection;

public class TalkerActivity extends Activity
	implements ServiceConnection, OnClickListener
{
	private Intent intent;
	private Jabber talker;
	private Handler handle;
	private XMPPBinder binder;
	private TalkerAdapter adapter;
	private SimpleAdapter uiadapter;
	final private static int LOGIN_REQUEST = 0x1982;
	private STUNPingPong stunPingPong = null; 
	/* @Override */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		handle = new Handler();
		intent = new Intent(this, TalkCoreService.class);
		adapter = new TalkerAdapter(TalkerActivity.this, handle);

		startService(intent);
		talker = newSession();
		//startService(intent);
		setupWidgets(savedInstanceState);
		//bindService(intent, this, BIND_AUTO_CREATE);
		stunPingPong = newSTUNPingPong(handle, this);
        ProtoPlugcan.getInstance().registerPlugin(stunPingPong);
	}

	private void setupWidgets(Bundle bundle) {
		Intent intent;
		TabHost tabhost;
		ListView listView = (ListView)findViewById(R.id.list);

		tabhost = (TabHost)findViewById(R.id.tabhost);
		tabhost.setup();

		uiadapter = getSimpleAdapter();
		listView.setAdapter(uiadapter);
		listView.setOnItemClickListener(adapter);

		tabhost.addTab(tabhost.newTabSpec("tab1")
				.setIndicator("list").setContent(R.id.list));
		tabhost.addTab(tabhost.newTabSpec("tab2")
				.setIndicator("chat").setContent(R.id.layout2));
		tabhost.addTab(tabhost.newTabSpec("tab3")
				.setIndicator("log").setContent(R.id.exception_detail));
		tabhost.setCurrentTab(0);

		if (bundle != null) {
			System.out.println("Hello World!");
			return;
		}

		LayoutInflater factory = LayoutInflater.from(this);
		final View login = factory.inflate(R.layout.login, null);
		final AlertDialog dialog = new AlertDialog.Builder(this)
			.setIcon(R.drawable.icon)
			.setTitle(R.string.login)
			.setView(login)
			.setPositiveButton(R.string.ok, loginClick)
			.setNegativeButton(R.string.cancel, loginClick)
			.create();

		String user, password;
		EditText passEditText, userEditText;

		dialog.show();
		passEditText = (EditText)dialog.findViewById(R.id.login_edit_pwd);
		userEditText = (EditText)dialog.findViewById(R.id.login_edit_account);

		SharedPreferences cookie = getSharedPreferences("cookie", MODE_PRIVATE);
		user = cookie.getString("user", "level@jabbernet.dk");
		userEditText.setText(user);

		password = cookie.getString("password", "wB0BVqHI");
		passEditText.setText(password);

		return;
	}

	DialogInterface.OnClickListener loginClick = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog_, int which) {
			CheckBox usetls;
			String user, password;
			EditText passEditText, userEditText;
			AlertDialog dialog = (AlertDialog)dialog_;

			switch (which) {
				case Dialog.BUTTON_POSITIVE:
					break;

				case Dialog.BUTTON_NEGATIVE:
					break;

				default:
					break;
			}

			if (which == Dialog.BUTTON_POSITIVE) {
				usetls = (CheckBox)dialog.findViewById(R.id.login_cb_usetls);
				passEditText = (EditText)dialog.findViewById(R.id.login_edit_pwd);
				userEditText = (EditText)dialog.findViewById(R.id.login_edit_account);
				setTitle("login...");

				user = userEditText.getText().toString();
				password = passEditText.getText().toString();

				talker.useTLS(usetls.isChecked());
				talker.setStateListener(adapter);
				talker.setMessageListener(adapter);
				talker.setPresenceListener(adapter);
				
				SharedPreferences cookie = getSharedPreferences("cookie", MODE_PRIVATE);
				SharedPreferences.Editor editor = cookie.edit();
				editor.putString("user", user);
				editor.putString("password", password);
				editor.commit();

				try {
					Log.i("jabber", "start login");
					talker.login(user, password, null);
					handle.postDelayed(flushStatus, 1000);
				} catch (Exception e) {
					Log.i("jabber", "abort login");
					e.printStackTrace();
				}
			}
		}
	};

	private Jabber newSession() {
		Jabber client;
		/* TODO: create jabber client. */
		client = new Jabber();
		client.setCallback(new EmptyRunnable());
		return client;
	}

	/* @Override */
	public void onDestroy() {
		super.onDestroy();
        ProtoPlugcan.getInstance()
			.unregisterPlugin(stunPingPong);
		//unbindService(this);
		//stopService(intent);
		adapter.destroy();
		talker.close();
	}

	/* @Override */
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, 0, 0, "call");
		menu.add(0, 1, 1, "abort");
		menu.add(0, 2, 2, "exit");
		return true;
	}

	private Runnable flushStatus = new Runnable() {
		public void run() {
			setTitle(talker.messageTitle);
			if (talker.disconnected) {
				ShowText("disconnected");
				return;
			}

			if (talker.isLogining()) {
				handle.postDelayed(flushStatus, 1000);
				return;
			}
			return;
		}
	};

	/* @Override */
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			moveTaskToBack(false);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	private void ShowText(String text) {
		Toast.makeText(this, text, Toast.LENGTH_LONG).show();
		return;
	}

	/* @Override */
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		Intent voiceIntent = null;

		switch (item.getItemId()) {
			case 0:
				VoiceCall.dial(talker, adapter.getCurrentJID());
				/*
				if (adapter.isCallable()) {
					Intent calling = adapter.doPhoneCalling("calling", null);
					Jabber.setSticky(talker);
					startActivity(calling);
				} else {
					String title = "please choose a user";
					ShowText(title);
				}
				*/
				break;

			case 1:
				String title = "welcome to pagxir's blog.";
				ShowText("welcome to pagxir's blog.");
				break;

			case 2:
				this.finish();
				break;

			default:
				break;
		}

		return true;
	}

	/* OnClickListener.onClick */
	public void onClick(View view) {
		EditText editText = (EditText)findViewById(R.id.message);
		adapter.sendMessage(editText.getText().toString());
		editText.setText("");
	} 

	/* ServiceConnection.onServiceConnected */
	public void onServiceConnected(ComponentName name, IBinder service) {
		binder = (XMPPBinder)service;
	}

	/* ServiceConnection.onServiceDisconnected */
	public void onServiceDisconnected(ComponentName className) {
		binder = null;
	}

	private SimpleAdapter getSimpleAdapter() {
		int[] sidents;
		String[] columns;
		SimpleAdapter uiadapter;

		columns = new String[] {"name", "stat", "show", "avatar" };
		sidents = new int[] {R.id.UserName, R.id.UserIP, R.id.UserStat, R.id.ImageView};
		uiadapter = new SimpleAdapter(this, adapter.rosters,
							R.layout.roster, columns, sidents) {
			public void setViewImage(ImageView v, String value) {
				adapter.setViewImage(v, value);
			}
		};

		return uiadapter;
	}

	static class EmptyRunnable implements Runnable {
		public void run() {
			System.out.println("state message call.");
			return;
		}
	}

	class PhoneBack implements Runnable, STUNPingPong.IPhoneBack {
			private String sid;
			private String jid;
			private Handler handle;
			private boolean idling;
			private Activity activity;
			private Jabber client;

			public PhoneBack(Handler handle, Activity activity) {
				this.handle = handle;
				this.activity = activity;
			}

			public synchronized void invoke(Jabber _client, String _sid, String sender) {
				sid = _sid;
				jid = sender;
				client = _client;
				idling = false;

				handle.post(this);
				while (!idling) {
					try {
						this.wait();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			public synchronized void run() {
				Intent calling = adapter.doPhoneCalling("answer", jid);
				idling = true;
				adapter.phoneCallNotify(TalkerActivity.this.getIntent());
				Jabber.setSticky(talker);
				startActivity(calling);
				this.notify();
			}
	}

	private STUNPingPong newSTUNPingPong(Handler handle, Activity activity) {
		STUNPingPong pong;
		STUNPingPong.IPhoneBack phoneback;
		phoneback = new PhoneBack(handle, activity);
		pong = new STUNPingPong(phoneback);
		return pong;
	}

	static class RunnableWrapper implements Runnable {
		private Handler handle;
		private Runnable wrapper;
		
		public RunnableWrapper(Handler handle, Runnable wrapper) {
			this.wrapper = wrapper;
			this.handle = handle;
		}

		public void run() {
			handle.post(wrapper);
			return;
		}
	}

	class TalkerAdapter implements Runnable, PacketCallback, OnItemClickListener {

		private String peer;
		private String hashAvatar;
		
		private boolean closed;
		private Handler handle;
		private Activity activity;

		ArrayList<HashMap<String, String>> rosters;
		private HashMap<String, Element> rosterCollection; 
		private HashMap<String, Element> presenceCollection;

		private void writeAvatarFile(String hash, String base64) {
			try {
				FileOutputStream stream = openFileOutput(hash + ".avatar", 0);
				stream.write(Base64.decode(base64, 0));
				stream.close();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}

		private SlotWait avatarWait = new SlotWait() {
			String title;
			public void invoke() {
				Element e = (Element)result;
				FastXmlVisitor visitor = new FastXmlVisitor(e);
				title = visitor.getElement("vCard")
					.getElement("PHOTO").getElement("BINVAL").getValue();
				if (hashAvatar != null && !hashAvatar.equals(""))
					writeAvatarFile(hashAvatar, title);
				hashAvatar = "";
				delayUpdate();
				if (!avatarWait.active()) {
					System.out.println("avatar wait");
					fetchAvatar();
				}
				this.clear();
			}
		};

		public void fetchAvatar() {
			for (Element e: presenceCollection.values()) {
				String hash = getAvatar(e);
				if (hash != null && !hash.equals("")) {
					boolean found = false;
					for (String file: fileList()) {
						if (file.equals(hash + ".avatar")) {
							found = true;
							break;
						}
					}
					if (!found) {
						talker.getAvatar(e.getAttribute("from"), avatarWait);
						hashAvatar = hash;
						break;
					}
				}
			}
		}

		public String getCurrentJID() {
			return peer;
		}

		public Intent doPhoneCalling(String action, String jid) {
			Intent calling = new Intent(activity, RecordActivity.class);
			jid = (jid == null? peer: jid);
			calling.putExtra("jid", jid);
			calling.putExtra("name", getNickName(jid));
			calling.putExtra("action", action);
			return calling;
		}

		public TalkerAdapter(Activity activity, Handler handle) {
			this.handle = handle;
			this.activity = activity;
			this.rosterCollection = new HashMap<String, Element>();
			this.presenceCollection = new HashMap<String, Element>();
			this.rosters = new ArrayList<HashMap<String, String>>();
		}

		private SlotRunner uiwrapper = new SlotRunner(this) {
			public void invoke() {
				TextView textView;
				textView = (TextView)activity.findViewById(R.id.textview2);
				textView.setMovementMethod(ScrollingMovementMethod.getInstance());
				textView.append("Hello World!");
			}
		};

		private SlotWait cowrapper = new SlotWait(this) {
			public void invoke() {
				if (closed) {
					avatarWait.clean();
					rosterWait.clean();
				}
				return;
			}
		};

		public void receive(Element e) {
			String tag;
			boolean updating = false;

			tag = e.getTagName();
			if (tag.equals("message")) {
				FastXmlVisitor visitor = new FastXmlVisitor(e);
				String title = visitor.getElement("body").getValue();
				updating = (title != null && !title.equals(""));
			}

			if (tag.equals("presence")) {
				String from = e.getAttribute("from");
				if (presenceCollection.containsKey(from))
					presenceCollection.remove(from);
				if (!e.getAttribute("type").equals("unavailable"))
					presenceCollection.put(from, e);
				if (!avatarWait.active()) {
					System.out.println("avatar wait");
					fetchAvatar();
				}
				delayUpdate();
			}

			if (updating)
				handle.post(uiwrapper);
		}

		public void sendMessage(String body) {
			talker.sendMessage(peer, body);
			return;
		}

		private void delayUpdate() {
			handle.removeCallbacks(updater);
			handle.postDelayed(updater, 500);
		}

		String getAvatar(Element e) {
			FastXmlVisitor visitor = new FastXmlVisitor(e);
			return visitor.getElement("x").getElement("photo").getValue();
		}

		String getStatus(Element e) {
			FastXmlVisitor visitor = new FastXmlVisitor(e);
			return visitor.getElement("status").getValue();
		}

		String getShow(Element e) {
			FastXmlVisitor visitor = new FastXmlVisitor(e);
			String show = visitor.getElement("show").getValue();

			if (show.equals("away"))
				return "away";
			if (show.equals("chat"))
				return "idle";
			if (show.equals("dnd"))
				return "busy";
			if (show.equals("xa"))
				return "leave";

			return "online";
		}

		public String getNickName(String from) {
			from = from.replaceAll("/.*", "");

			if (rosterCollection.containsKey(from)) {
				Element packet = rosterCollection.get(from);
				String name = packet.getAttribute("name");
				if (name != null && !name.equals(""))
					return name;
			}
			return from;
		}

    	int notification_id = 19821131;
    	/* @mark */
    	private void phoneCallNotify(Intent intent) {
        	int icon = R.drawable.icon;
        	long millis = System.currentTimeMillis();
        	String title = "softphone";
        	String content = "waiting answer";
        	String trickertext = "XMPP call";
        	Notification notification = new Notification(icon, trickertext, millis);
        	notification.defaults = Notification.DEFAULT_ALL;
        	PendingIntent pt = PendingIntent.getActivity(TalkerActivity.this, 0, intent, 0);
        	notification.setLatestEventInfo(TalkerActivity.this, title, content, pt);
        	NotificationManager manager =
            	(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        	manager.notify(notification_id, notification);
    	}

		public void onItemClick(android.widget.AdapterView<?> parent,
				View view, int position, long id) {

			Button button = (Button)activity.findViewById(R.id.send);
			button.setOnClickListener((TalkerActivity)activity);

			TabHost tabhost = (TabHost)activity.findViewById(R.id.tabhost);
			peer = rosters.get(position).get("jid");
			tabhost.setCurrentTab(1);

			activity.setTitle("with " + getNickName(peer) + " chatting");
		}

		private void updateRosterView(Element presence, Element roster) {
			String name;
			HashMap<String, String> map
				= new HashMap<String, String>();

			map.put("jid", roster.getAttribute("jid"));
			if (presence != null) {
				map.put("show", getShow(presence));
				map.put("stat", getStatus(presence));
				map.put("avatar", getAvatar(presence));
				map.put("jid", presence.getAttribute("from"));
			}

			name = roster.getAttribute("name");
			if (name == null || name.equals("")) {
				map.put("name", roster.getAttribute("jid"));
			} else {
				map.put("name", name);
			}

			rosters.add(map);
		}

		private Runnable updater = new Runnable() {
			public void run() {
				update();
				return;
			}
		};

		private SlotWait rosterWait = new SlotWait() {
			public void invoke() {
				Element packet = (Element)result;
				rosterCollection.clear();
				NodeList nodelist = packet.getElementsByTagName("item");
				for (int i = 0; i < nodelist.getLength(); i++) {
					Element e = (Element)nodelist.item(i);
					rosterCollection.put(e.getAttribute("jid"), e);
				}
				delayUpdate();
			}
		};

		public void run() {
			talker.getRoster(null, rosterWait);
			handle.post(updater);
			return;
		}

		public void update() {
			HashMap<String, Object> displayed
				= new HashMap<String, Object>();

			rosters.clear();
			for (String key : presenceCollection.keySet()) {
				String bare = key.replaceAll("/.*", "");
				if (displayed.containsKey(bare))
					continue;

				displayed.put(bare, bare);
				if (rosterCollection.containsKey(bare))
					updateRosterView(presenceCollection.get(key),
							rosterCollection.get(bare));
			}

			for (String key : rosterCollection.keySet()) {
				if (displayed.containsKey(key))
					continue;
				updateRosterView(null, rosterCollection.get(key));
			}

			ListView listView = (ListView)activity.findViewById(R.id.list);
			SimpleAdapter adapter = (SimpleAdapter)listView.getAdapter();               
			adapter.notifyDataSetChanged();
		}

		public void setViewImage(ImageView v, String value) {
			String path;
			String[] files = fileList();

			path = value + ".avatar";
			for (String file: files) {
				if (file.equals(path)) {
					try {
						InputStream input = openFileInput(path);
						Drawable drawable = Drawable.createFromStream(input, path);
						v.setImageDrawable(drawable);
						input.close();
					} catch (IOException e) {
						v.setImageResource(R.drawable.avatar);
						e.printStackTrace();
						return;
					}
					return;
				}
			}

			v.setImageResource(R.drawable.avatar);
			return;
		}

		public void destroy() {
			closed = true;
			cowrapper.ipcSchedule();
			return;
		}
	}
}

