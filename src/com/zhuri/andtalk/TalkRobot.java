package com.zhuri.andtalk;

import java.nio.channels.*;
import java.net.InetAddress;
import java.io.IOException;
import com.zhuri.util.DEBUG;
import com.zhuri.slot.SlotSlot;
import com.zhuri.slot.SlotWait;
import com.zhuri.slot.SlotTimer;
import com.zhuri.net.STUNClient;
import com.zhuri.pstcp.AppFace;
import com.zhuri.util.InetUtil;

import com.zhuri.talk.TalkClient;
import com.zhuri.talk.protocol.Body;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Message;

import android.net.Uri;
import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;

public class TalkRobot {
	private Context mContext;
	private TalkClient mClient;
	final private SlotSlot mDisconnect = new SlotSlot();

	public TalkRobot(Context context) {
		mContext = context;
	}

	class ReplyContext implements Runnable {
		private Packet packet;
		private STUNClient client;
		private DatagramChannel datagram;
		private SlotWait r = new SlotWait(this);
		private SlotWait d = new SlotWait(this);
		private SlotTimer t = new SlotTimer(this);
		
		public ReplyContext(Packet p, String[] parts) {
			int port = 19302;
			String server = "stun.l.google.com";


			try {
				if (parts.length > 1)
					server = parts[1];
				if (parts.length > 2)
					port = Integer.parseInt(parts[2]);
				packet = p;
				datagram = DatagramChannel.open();
				client = new STUNClient(datagram, server, port);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void start() {
			mDisconnect.record(d);
			client.requestMapping(r);
			t.reset(5000);
		}

		public void run() {
			Message reply = new Message();
			Message message = new Message(packet);

			reply.setTo(message.getFrom());

			if (r.completed())
				reply.add(new Body(client.getMapping().toString()));
			else if (t.completed())
				reply.add(new Body("time out"));

			if (!d.completed())
				mClient.put(reply);

			close();
		}

		public void close() {
			try {
				datagram.close();
				client.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			t.clean();
			r.clean();
			d.clean();
		}
	}

	private Intent parseIntent(String[] args) {
		Intent intent = new Intent();
		intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
		for (int i = 2; i < args.length; i++) {
			if (args[i].equals("-a")) {
				if (++i < args.length)
					intent.setAction(args[i]);
			} else if (args[i].equals("-d")) {
				if (++i < args.length)
					intent.setData(Uri.parse(args[i]));
			} else if (args[i].equals("-t")) {
				if (++i < args.length)
					intent.setType(args[i]);
			} else if (args[i].equals("-c")) {
				if (++i < args.length)
					intent.addCategory(args[i]);
			} else if (args[i].equals("-e")) {
				i += 2;
			} else if (args[i].equals("--es")) {
				i += 2;
			} else if (args[i].equals("--esn")) {
				i += 1;
			} else if (args[i].equals("--ez")) {
				i += 2;
			} else if (args[i].equals("--ei")) {
				i += 2;
			} else if (args[i].equals("-n")) {
				if (++i < args.length) {
					String[] parts = args[i].split("/");
					if (parts.length == 2) {
						ComponentName n = new ComponentName(parts[0], parts[1]);
						intent.setComponent(n);
					}
				}
			} else if (args[i].equals("-f")) {
				++i;
			} else if (args[i].equals("--grant-read-uri-permission")) {
				intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			} else if (args[i].equals("--grant-write-uri-permission")) {
				intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			} else if (args[i].equals("--debug-log-resolution")) {
				intent.addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);
			} else if (args[i].equals("--activity-brought-to-front")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
			} else if (args[i].equals("--activity-clear-top")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			} else if (args[i].equals("--activity-clear-when-task-reset")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			} else if (args[i].equals("--activity-exclude-from-recents")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
			} else if (args[i].equals("--activity-launched-from-history")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
			} else if (args[i].equals("--activity-multiple-task")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
			} else if (args[i].equals("--activity-no-animation")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			} else if (args[i].equals("--activity-no-history")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
			} else if (args[i].equals("--activity-no-user-action")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
			} else if (args[i].equals("--activity-previous-is-top")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
			} else if (args[i].equals("--activity-reorder-to-front")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
			} else if (args[i].equals("--activity-reset-task-if-needed")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
			} else if (args[i].equals("--activity-single-top")) {
				intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
			} else if (args[i].equals("--receiver-registered-only")) {
				intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
			} else if (args[i].equals("--receiver-replace-pending")) {
				intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
			} else if (!args[i].startsWith("-")) {
				intent.setData(Uri.parse(args[i]));
			}
		}

		return intent;
	}

	private void amStart(String[] args) {
		if (args.length < 3) {
			return;
		}

		try {
			Intent intent = parseIntent(args);

			if (args[1].equals("start")) {
				mContext.startActivity(intent);
			} else if (args[1].equals("startservice")) {
				mContext.startService(intent);
			} else if (args[1].equals("broadcast")) {
				mContext.sendBroadcast(intent);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String doTcpForward(String[] parts) {

		try {
			if (parts.length >= 3) {
				int port = Integer.parseInt(parts[2]);
				InetAddress addr = InetUtil.getInetAddress(parts[1]);
				AppFace.setForward(addr.getAddress(), port);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "doTcpForward: failure";
		}

		return "doTcpForward: OK";
	}

	private String doStunSend(String[] parts) {
		if (parts.length >= 2)
			AppFace.stunSendRequest(parts[1], 1);
		return "doStunSend: OK";
	}

	private void onMessage(Packet packet) {
		Message message = new Message(packet);

		if (message.hasBody()) {
			String cmd;
			String msg = message.getContent();
			if (msg == null || msg.equals("")) {
				DEBUG.Print("EMPTY Message");
				return;
			}

			String[] parts = msg.split(" ");

			cmd = parts[0];
			if (cmd.equals("stun")) {
				ReplyContext context = new ReplyContext(packet, parts);
				context.start();
			} else if (cmd.equals("am")) {
				amStart(parts);
			} else if (cmd.equals("forward")) {
				Message reply = new Message();
				Message message1 = new Message(packet);
				String title = doTcpForward(parts);

				reply.setTo(message1.getFrom());
				reply.add(new Body(title));
				mClient.put(reply);
			} else if (cmd.equals("stun-send")) {
				Message reply = new Message();
				Message message1 = new Message(packet);
				String title = doStunSend(parts);

				reply.setTo(message1.getFrom());
				reply.add(new Body(title));
				mClient.put(reply);
			} else if (cmd.equals("stun-name")) {
				Message reply = new Message();
				Message message1 = new Message(packet);

				reply.setTo(message1.getFrom());
				reply.add(new Body(AppFace.stunGetName()));
				mClient.put(reply);
			} else {
				DEBUG.Print("MSG " + msg);
			}
		}
	}

	private final SlotWait onReceive = new SlotWait() {
		public void invoke() {
			Packet packet = mClient.get();

			while (packet != Packet.EMPTY_PACKET) {
				DEBUG.Print("INCOMING", packet.toString());
				if (packet.matchTag("presence")) {
					mClient.processIncomingPresence(packet);
				} else if (packet.matchTag("message")) {
					mClient.processIncomingMessage(packet);
					onMessage(packet);
				} else if (packet.matchTag("iq")) {
					mClient.processIncomingIQ(packet);
				} else {
					DEBUG.Print("unkown TAG: " + packet.getTag());
				}
				mClient.mark();
				packet = mClient.get();
			}

			if (!mClient.isStreamClosed()) {
				mClient.waitI(onReceive);
			   return;
			}

			/* release connect resource than retry */
			mDisconnect.wakeup();
			mClient.disconnect();
			mDelay.reset(5000);
			return;
		}
	};

	public void close() {
		if (mClient != null)
			mClient.disconnect();
		mDisconnect.wakeup();
		mDelay.clean();
		return;
	}

	final private SlotTimer mDelay = new SlotTimer() {
		public void invoke() {
			start();
			return;
		}
	};

	public void start() {
		mClient = new TalkClient();
		mClient.waitI(onReceive);
		mClient.start();
		return;
	}
}
