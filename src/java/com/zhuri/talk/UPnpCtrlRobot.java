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
	private SlotWait r = new SlotWait(this);
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
		client.start(mParams);
		mCancel.record(d);
		t.reset(5000);
	}

	private int mOffset = 0;
	private int lineParse(String lines, int from) {
		int finish = from;

		for (int offset = from; offset < lines.length(); offset++) {
			if (lines.charAt(offset) == '\n') {
				finish = offset + 1;
			} else if (offset > from + 8000) {
				finish = (from < finish? finish: offset);
				break;
			}
		}

		return finish;
	}

	public void run() {
		String line;
		String response = client.getResponse();

		while (!d.completed() && mOffset < response.length()) {
			Message reply = new Message();

			reply.setTo(mFrom);
			if (mOffset + 8000 > response.length()) {
				line = response.substring(mOffset);
				mOffset = response.length();
			} else {
				int start = mOffset;
				mOffset = lineParse(response, mOffset);
				line = response.substring(start, mOffset);
			}

			if (client.completed())
				reply.add(new Body(line));

			if (!mClient.put(reply)) {
				mClient.waitO(r);
				return;
			}
		}

		if (!d.completed() && t.completed()) {
			Message reply = new Message();

			reply.setTo(mFrom);
			reply.add(new Body("ctrl: time out"));

			if (!mClient.put(reply)) {
				mClient.waitO(r);
				return;
			}
		}

		close();
	}

	public void close() {
		client.close();
		r.clean();
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

