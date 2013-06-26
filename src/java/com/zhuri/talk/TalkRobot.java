package com.zhuri.talk;

import java.util.List;
import java.nio.channels.*;
import java.io.IOException;
import com.zhuri.util.DEBUG;
import com.zhuri.slot.SlotSlot;
import com.zhuri.slot.SlotWait;
import com.zhuri.slot.SlotTimer;
import com.zhuri.net.STUNClient;
import com.zhuri.talk.TalkClient;
import com.zhuri.talk.Scriptor;
import com.zhuri.talk.UpnpRobot;
import com.zhuri.talk.StunRobot;
import com.zhuri.talk.protocol.Body;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Message;
import com.zhuri.talk.protocol.Presence;

public class TalkRobot {
	static final String TAG = "TalkRobot";

	public interface IReplyable extends Scriptor.IInvokable {
		public void setRobot(TalkRobot robot);
		public void setTalk(TalkClient client);
		public void setReply(String reply);
		public void setCancel(SlotSlot cancel);
	}

	protected TalkClient mClient;
	final protected Scriptor mScriptor = new Scriptor();
	final protected SlotSlot mDisconnect = new SlotSlot();

	public TalkRobot(TalkClient client) {
		UPnpCtrlRobot.install(mScriptor);
		StunRobot.install(mScriptor);
		UpnpRobot.install(mScriptor);
		client.waitI(onReceive);
		mClient = client;
	}

	public void onDisconnect(SlotWait wait) {
		mDisconnect.record(wait);
		return;
	}

	protected void onDisconnect() {
		/* TODO: add stub */
		return;
	}

	public void presence(String presence) {
		Presence packet;

		if (mClient != null) {
			packet = new Presence();
			packet.setStatus(presence);
			mClient.setPresence(packet);
		}

		return;
	}

	private boolean mIsRobotOn = true;
	protected void onMessage(Packet packet) {
		Message message = new Message(packet);

		if (message.hasBody()) {
			String cmd;
			String msg = message.getContent();
			if (msg == null || msg.equals("")) {
				DEBUG.Print("EMPTY Message");
				return;
			}

			if (msg.startsWith("off " + mClient.getJID())) {
				DEBUG.Print(TAG, "robot turn off");
				mIsRobotOn = false;
				return;
			} else if (msg.startsWith("on " + mClient.getJID())) {
				DEBUG.Print(TAG, "robot turn on");
				mIsRobotOn = true;
				return;
			} else if (!mIsRobotOn) {
				DEBUG.Print(TAG, "robot is off");
				return;
			}

			Scriptor.IInvokable invokable = mScriptor.evalate(msg);
			if (invokable == null) {
				Message reply = new Message();
				reply.setTo(message.getFrom());
				reply.add(new Body("unkown command"));
				DEBUG.Print("unkown " + msg);
				mClient.put(reply);
				return;
			}

			IReplyable replyable = (IReplyable)invokable;
			replyable.setCancel(mDisconnect);
			replyable.setReply(message.getFrom());
			replyable.setTalk(mClient);
			replyable.setRobot(this);

			invokable.invoke();
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
			onDisconnect();
			return;
		}
	};

	public void close() {
		if (mClient != null)
			mClient.disconnect();
		mDisconnect.wakeup();
		return;
	}
}
