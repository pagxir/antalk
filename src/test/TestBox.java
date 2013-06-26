package test;

import java.util.Scanner;
import com.zhuri.slot.SlotAsync;
import com.zhuri.slot.SlotTimer;
import com.zhuri.slot.SlotThread;

public class TestBox {
	private static String target;
	private static String message;
	private static TestTalkClient client;

	private static Runnable outMsg = new Runnable() {
		public void run() {
			if (message == null || target == null) {
				System.out.println("Message is not acceptable");
				return;
			}

			if (client != null) {
				client.send(target, message);
				return;
			}
		};
	};

	public static void main(String args[]) {
		String line;
		SlotAsync mAsync;
		Thread looper = new Thread(SlotThread.getLooper());

		SlotThread.Init();
		mAsync = new SlotAsync(outMsg);
		client = new TestTalkClient();
		for (String url: args)
			new WebCrawler(url).start();

		mAsync.setup();
		client.start();
		/* after looping start, all slot function should not call out of looper thread. */
		looper.start();

		System.out.println("please input quit to quit");
		Scanner sc = new Scanner(System.in);

		line = sc.nextLine();
		while (!line.startsWith("quit")) {
			if (line.startsWith("sel ")) {
				target = line.replace("sel ", "");
				System.out.println("select " + target);
			} else if (line.startsWith("msg ")) {
				message = line.replace("msg ", "");
				System.out.println("message " + message);
				mAsync.toggle();
			}
			line = sc.nextLine();
		}

		System.out.println("waiting looper to quit");
		SlotThread.quit();

		try {
			looper.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
