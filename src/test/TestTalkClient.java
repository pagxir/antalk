package test;

import com.zhuri.slot.SlotWait;
import com.zhuri.talk.TalkClient;

public class TestTalkClient {
	private TalkClient mClient = new TalkClient();

	private final SlotWait mRetry = new SlotWait() {
		public void invoke() {
			System.out.println("reconnecting");
			mClient.disconnect();
			mClient = new TalkClient();
			start();
		}
	};

	public void start() {
		mClient.onDisconnect(mRetry);
		mClient.start();
		return;
	}
}
