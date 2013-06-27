package com.zhuri.talk;

import java.util.List;
import java.nio.channels.*;
import java.net.InetAddress;
import java.io.IOException;
import com.zhuri.util.DEBUG;
import com.zhuri.slot.SlotSlot;
import com.zhuri.slot.SlotWait;
import com.zhuri.slot.SlotTimer;
import com.zhuri.net.STUNClient;
import com.zhuri.net.UPNPClient;
import com.zhuri.util.InetUtil;

import com.zhuri.talk.TalkClient;
import com.zhuri.talk.protocol.Body;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Message;

class UpnpInvoke implements Runnable, TalkRobot.IReplyable {
	private String mFrom;
	private UPNPClient client;
	private TalkClient mClient;
	private DatagramChannel datagram;
	private SlotSlot mDisconner = null;
	private SlotWait r = new SlotWait(this);
	private SlotWait d = new SlotWait(this);
	private SlotTimer t = new SlotTimer(this);

	public UpnpInvoke(String[] parts) {
		int port = 1900;
		String server = null;

		try {
			if (parts.length > 1)
				server = parts[1];
			if (parts.length > 2)
				port = Integer.parseInt(parts[2]);
			datagram = DatagramChannel.open();
			client = new UPNPClient(datagram, server, port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void setCancel(SlotSlot cancel) {
		mDisconner = cancel;
		return;
	}

	@Override
	public void setReply(String reply) {
		mFrom = reply;
		return;
	}

	@Override
	public void setRobot(TalkRobot robot) {
		return;
	}

	@Override
	public void setTalk(TalkClient client) {
		mClient = client;
		return;
	}

	@Override
	public void invoke() {
		mDisconner.record(d);
		client.search(r);
		t.reset(5000);
	}

	public void run() {
		Message reply = new Message();

		reply.setTo(mFrom);

		if (r.completed())
			reply.add(new Body("UPNP: " + client.getSearchResult()));
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

public class UpnpRobot implements Scriptor.ICommandInterpret {
	public static void install(Scriptor scriptor) {
		scriptor.registerCommand("upnp", new UpnpRobot());
		return;
	}

	public Scriptor.IInvokable createInvoke(List<String> params) {
		String[] arr = new String[params.size()];
		return new UpnpInvoke(params.toArray(arr));
	}
}

