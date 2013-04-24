package com.zhuri.talk;

import java.nio.*;
import java.io.IOException;

import com.zhuri.slot.*;
import com.zhuri.util.DEBUG;
import com.zhuri.net.Connector;
import com.zhuri.net.XyConnector;
import com.zhuri.net.IConnectable;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Stream;

public class TalkClient {
	final static int WF_RESOLV = 0x00000001;
	final static int WF_HEADER = 0x00000002;
	final static int WF_FEATURE = 0x00000004;
	final static int WF_PROCEED = 0x00000008;
	final static int WF_SUCCESS  = 0x00000010;
	final static int WF_STARTTLS = 0x00000020;
	final static int WF_QUERY1ST = 0x00000040;
	final static int WF_CONNECTED = 0x00000080;
	final static int WF_HANDSHAKE = 0x00000100;
	final static int WF_PLAINSASL = 0x00000200;
	final static int WF_LOGINSTEP9 = 0x00000400;

	final static int WF_BINDER = 0x00000800;
	final static int WF_DESTROY = 0x00020000;
	final static int WF_SESSION = 0x00001000;
	final static int WF_PRESENCE = 0x00002000;
	final static int WF_CONFIGURE = 0x00004000;
	final static int WF_CONNECTING = 0x00008000;
	final static int WF_DISCONNECT = 0x00010000;
	final static int WF_FORCETLS   = 0x10000000;
	final static int WF_ENABLETLS  = 0x20000000;

	final private static String LOG_TAG = "TalkClient";

	final private int mInterval = 10000;
	final private Connector mConnector = new XyConnector("223.167.213.254:9418");

	final private SlotTimer mKeepalive = new SlotTimer() {
		public void invoke() {
			DEBUG.Print("time out event");
			routine();
			mKeepalive.reset(mInterval);
			return;
		}
	};

	final private SlotWait mWaitOut = new SlotWait() {
		public void invoke() {
			DEBUG.Print("output event");
			mKeepalive.reset(mInterval);
			routine();
			return;
		}
	};

	final private SlotWait mWaitIn = new SlotWait() {
		public void invoke() {
			long count = -1;
			String message = "";
			ByteBuffer buffer = ByteBuffer.allocate(80000);

			DEBUG.Print("input event");
			mKeepalive.reset(mInterval);

			try {
				count = mProxyChannel.read(buffer);
			} catch (IOException e) {
				e.printStackTrace();
			}

			/* update */

			if (count != -1 && thisPacket == null) {
				message = new String(buffer.array(), 0, (int)count);
				System.out.println(message);
				mProxyChannel.waitI(mWaitIn);
				return;
			}

			System.out.println("stream is close");
			routine();
			return;
		}
	};

	private int mStateFlags = 0;
	private boolean stateMatch(int next, int prev) {
		int flags = mStateFlags;
		flags &= (next | prev);
		return (flags == prev);
	}

	public void start() {
		mStateFlags |= WF_CONFIGURE;
		routine();
		return;
	}

	private IWaitableChannel mProxyChannel = null;
	protected boolean put(String packet) {
		long count = 0;
		ByteBuffer buffer;
		buffer = ByteBuffer.wrap(packet.getBytes());

		try {
			count = mProxyChannel.write(buffer);
		} catch (IOException e) {
			e.printStackTrace();
		}

		DEBUG.Print(LOG_TAG, "put:\n" + packet);
		return (count > 0);
	}

	public boolean put(Packet packet) {
		String content;

		content = packet.toString();
		return put(content);
	}

	private Packet thisPacket = null;
	static private final Packet emptyPacket = new Packet();

	static private final int TAG_THIS = 0x0001;
	static private final int TAG_NEXT = 0x0000;
	public Packet get(int flags) {
		Packet packet = null;

		switch (flags) {
			case TAG_NEXT:
				mProxyChannel.waitI(mWaitIn);
				thisPacket = null;
				break;

			case TAG_THIS:
				packet = thisPacket;
				if (thisPacket == null)
					packet = emptyPacket;
				break;

			default:
				packet = emptyPacket;
				break;
		}

		return packet;
	}

	public void updateFeature(Packet packet) {

		return;
	}

	private void routine() {
		if (stateMatch(WF_RESOLV, WF_CONFIGURE)) {
			mStateFlags |= WF_RESOLV;
		}

		if (stateMatch(WF_CONNECTING, WF_RESOLV)) {
			mConnector.connect("xmpp.l.google.com:5222");
			mKeepalive.reset(mInterval);
			mConnector.waitO(mWaitOut);
			mStateFlags |= WF_CONNECTING;
		}

		if (stateMatch(WF_CONNECTED, WF_CONNECTING)) {
			if (mWaitOut.completed()) {
				mStateFlags |= WF_CONNECTED;
				mProxyChannel = mConnector;
				mWaitOut.clear();
			}
		}


		if (stateMatch(WF_HEADER, WF_CONNECTED)) {
			put(Stream.begin("gmail.com"));
			mStateFlags |= WF_HEADER;
			get(TAG_NEXT);
		}

		if (stateMatch(WF_FEATURE, WF_HEADER)) {
			if (get(TAG_THIS).matchTag("feature")) {
				updateFeature(get(TAG_THIS));
				mStateFlags |= WF_FEATURE;
			}
		}

		if (stateMatch(WF_STARTTLS, WF_FEATURE)) {
			mStateFlags |= WF_STARTTLS;
		}

		if (stateMatch(WF_PROCEED, WF_STARTTLS)) {
			mStateFlags |= WF_PROCEED;
		}

		if (stateMatch(WF_HANDSHAKE, WF_PROCEED)) {
			System.out.println("WF_HANDSHAKE");
			mStateFlags |= WF_HANDSHAKE;
		}
	}
}

