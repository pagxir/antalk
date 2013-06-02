package test;

import java.util.Scanner;
import com.zhuri.slot.SlotAsync;
import com.zhuri.slot.SlotTimer;
import com.zhuri.slot.SlotThread;

public class TestBox {
	public static void main(String args[]) {
		Thread looper = new Thread(SlotThread.getLooper());
		TestTalkClient client = new TestTalkClient();

		SlotThread.Init();

		for (String url: args)
			new WebCrawler(url).start();

		client.start();
		/* after looping start, all slot function should not call out of looper thread. */
		looper.start();

		System.out.println("please input any key to quit");
		Scanner sc = new Scanner(System.in);
		sc.next();

		System.out.println("waiting looper to quit");
		SlotThread.quit();

		try {
			looper.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
