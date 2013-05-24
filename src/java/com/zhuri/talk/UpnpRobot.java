package com.zhuri.talk;

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

public class UpnpRobot implements Runnable {
	private Packet packet;
	private UPNPClient client;
	private TalkClient mClient;
	private DatagramChannel datagram;
	private SlotSlot mDisconner = null;
	private SlotWait r = new SlotWait(this);
	private SlotWait d = new SlotWait(this);
	private SlotTimer t = new SlotTimer(this);

	public UpnpRobot(TalkClient tclient,
			SlotSlot disconner, Packet p, String[] parts) {
		int port = 19302;
		String server = "stun.l.google.com";

		mClient = tclient;
		mDisconner = disconner;

		try {
			if (parts.length > 1)
				server = parts[1];
			if (parts.length > 2)
				port = Integer.parseInt(parts[2]);
			packet = p;
			datagram = DatagramChannel.open();
			client = new UPNPClient(datagram, server, port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void start() {
		mDisconner.record(d);
		client.search(r);
		t.reset(5000);
	}

	public void run() {
		Message reply = new Message();
		Message message = new Message(packet);

		reply.setTo(message.getFrom());

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
