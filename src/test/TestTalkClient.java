package test;

import java.nio.channels.*;
import java.io.IOException;
import com.zhuri.util.DEBUG;
import com.zhuri.slot.SlotSlot;
import com.zhuri.slot.SlotWait;
import com.zhuri.slot.SlotTimer;
import com.zhuri.net.STUNClient;
import com.zhuri.talk.TalkClient;
import com.zhuri.talk.protocol.Body;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Message;

public class TestTalkClient {
	private TalkClient mClient;
	final private SlotSlot mDisconnect = new SlotSlot();

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
