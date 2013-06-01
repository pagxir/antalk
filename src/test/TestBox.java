package test;

import java.util.Scanner;
import com.zhuri.slot.SlotAsync;
import com.zhuri.slot.SlotTimer;
import com.zhuri.slot.SlotThread;

public class TestBox {

	static SlotTimer mTimer = new SlotTimer() {
		public void invoke() {
			System.out.println("start https test");

			try {
				TestTalkClient client = new TestTalkClient();
				client.start();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		}
	};

	static private Runnable mLooper = new Runnable() {
		public void run() {
			while (SlotThread.step());
		}
	};

	static private Runnable mQuit = new Runnable() {
		public void run() {
			SlotThread.quit();
		}
	};

	static private SlotAsync mQuitAsync = new SlotAsync(mQuit);

	public static void main(String args[]) {
		WebCrawler crawler = null;
		Thread looper = new Thread(mLooper);

		SlotThread.Init();
		mQuitAsync.setup();

		mTimer.reset(100);
		try {
			for (String url: args)
				new WebCrawler(url).start();
		} catch (Exception e) {
			e.printStackTrace();
		}

		/* after looping start, all slot function should not call out of looper thread. */
		looper.start();

		System.out.println("please input any key to quit");
		Scanner sc = new Scanner(System.in);
		sc.next();
		System.out.println("waiting looper to quit");
		mQuitAsync.toggle();

		try {
			looper.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
