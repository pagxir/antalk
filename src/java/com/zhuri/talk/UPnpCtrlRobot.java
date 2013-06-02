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
import com.zhuri.net.UPnpControler;
import com.zhuri.util.InetUtil;

import com.zhuri.talk.TalkClient;
import com.zhuri.talk.protocol.Body;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Message;

class UPnpCtrlInvoke implements Runnable, TalkRobot.IReplyable {
	private String mFrom;
	private String[]mParams;
	private SlotSlot mCancel = null;
	private TalkClient mClient = null;
	private SlotWait d = new SlotWait(this);
	private SlotTimer t = new SlotTimer(this);
	private UPnpControler client = new UPnpControler(this);

	public UPnpCtrlInvoke(String[] parts) {
		mParams = parts;
		return;
	}

	@Override
	public void setCancel(SlotSlot cancel) {
		mCancel = cancel;
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
		client.start(mParams);
		mCancel.record(d);
		t.reset(5000);
	}

	public void run() {
		Message reply = new Message();

		reply.setTo(mFrom);
		if (client.completed())
			reply.add(new Body("ctrl: " + client.getResponse()));
		else if (t.completed())
			reply.add(new Body("ctrl: time out"));

		if (!d.completed())
			mClient.put(reply);

		close();
	}

	public void close() {
		client.close();
		t.clean();
		d.clean();
	}
}

public class UPnpCtrlRobot implements Scriptor.ICommandInterpret {
	public static void install(Scriptor scriptor) {
		scriptor.registerCommand("upnpctrl", new UPnpCtrlRobot());
		return;
	}

	public Scriptor.IInvokable createInvoke(List<String> params) {
		String[] arr = new String[params.size()];
		return new UPnpCtrlInvoke(params.toArray(arr));
	}
}

