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
	final private static String XYHOST  = "223.167.213.254:9418";

	final private int mInterval = 10000;
	final private Connector mConnector = new XyConnector(XYHOST);

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
			DEBUG.Print("input event");
			mKeepalive.reset(mInterval);
			routine();
			return;
		}
	};

	private int mStateFlags = 0;
	private SampleXmlChannel mXmlChannel = null;
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
				mXmlChannel = new SampleXmlChannel(mConnector);
				mStateFlags |= WF_CONNECTED;
				mWaitOut.clear();
			}
		}

		if (stateMatch(WF_HEADER, WF_CONNECTED)) {
			mXmlChannel.mark(SampleXmlChannel.XML_NEXT);
			mXmlChannel.open("gmail.com");
			mXmlChannel.waitI(mWaitIn);
			mStateFlags |= WF_HEADER;
		}

		if (stateMatch(WF_FEATURE, WF_HEADER)) {
			Packet packet = mXmlChannel.get();
			if (packet.matchTag("feature")) {
				mStateFlags |= WF_FEATURE;
				updateFeature(packet);
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

