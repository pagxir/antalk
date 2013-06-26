package test;

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
import com.zhuri.talk.TalkRobot;
import com.zhuri.talk.UpnpRobot;
import com.zhuri.talk.StunRobot;
import com.zhuri.talk.protocol.Body;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Message;

public class TestTalkClient {
	private TalkRobot mRobot;
	private TalkClient client;

	final private SlotTimer mDelay = new SlotTimer() {
		public void invoke() {
			start();
			return;
		}
	};

	final private SlotWait onDisconnect = new SlotWait() {
		public void invoke() {
			mDelay.reset(5000);
			return;
		}
	};

	public void send(String user, String message) {
		Message packet = new Message();
		packet.add(new Body(message));
		packet.setTo(user);
		client.put(packet);
		return;
	}

	public void start() {
		client = new TalkClient();
		mRobot = new TalkRobot(client) {
			@Override
			protected void onMessage(Packet packet) {
				Message message = new Message(packet);

				if (message.hasBody()) {
					String msg = message.getContent();
					if (msg == null || msg.equals("")) {
						DEBUG.Print("EMPTY Message");
						return;
					}

					DEBUG.Print("Message " + msg);
					return;
				}
			}
		};

		mRobot.onDisconnect(onDisconnect);
		client.start("\u7B11\u5929\u5B54\u540E", "uc.sina.com.cn", "GAkJoEtq75x9", "xmpp.uc.sina.com.cn:5222");
		client.setResource("java");
		return;
	}
}
