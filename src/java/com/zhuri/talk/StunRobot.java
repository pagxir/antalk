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
import com.zhuri.util.InetUtil;

import com.zhuri.talk.TalkClient;
import com.zhuri.talk.protocol.Body;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Message;

class StunInvoke implements Runnable, TalkRobot.IReplyable {
	private String mFrom;
	private STUNClient client;
	private TalkClient mClient;
	private DatagramChannel datagram;
	private SlotSlot mDisconner = null;
	private SlotWait r = new SlotWait(this);
	private SlotWait d = new SlotWait(this);
	private SlotTimer t = new SlotTimer(this);

	public StunInvoke(String[] parts) {
		int port = 19302;
		String server = "stun.l.google.com";

		if (parts.length > 1)
			server = parts[1];
		try {
			if (parts.length > 2)
				port = Integer.parseInt(parts[2]);
		} catch (NumberFormatException e) {
			port = 3478;
		}

		try {
			datagram = DatagramChannel.open();
			client = new STUNClient(datagram, server, port);
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
	public void setTalk(TalkClient client) {
		mClient = client;
		return;
	}

	@Override
	public void invoke() {
		mDisconner.record(d);
		client.requestMapping(r);
		t.reset(5000);
	}

	public void run() {
		Message reply = new Message();
		StringBuilder builder = new StringBuilder();

		reply.setTo(mFrom);
		builder.append("l: ");
		builder.append(String.valueOf(datagram.socket().getLocalPort()));
		builder.append("\n");
		builder.append("e: ");
		builder.append(r.completed()? client.getMapping().toString(): "time out");

		if (r.completed())
			reply.add(new Body(builder.toString()));
		else if (t.completed())
			reply.add(new Body("stun: time out"));

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

public class StunRobot implements Scriptor.ICommandInterpret {
	public static void install(Scriptor scriptor) {
		scriptor.registerCommand("stun", new StunRobot());
		return;
	}

	public Scriptor.IInvokable createInvoke(List<String> params) {
		String[] arr = new String[params.size()];
		return new StunInvoke(params.toArray(arr));
	}
}

