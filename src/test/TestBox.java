package test;
import com.zhuri.slot.SlotThread;

public class TestBox {
	public static void main(String args[]) {
		WebCrawler crawler = null;

		try {
			SlotThread.Init();
			crawler = new WebCrawler();
			crawler.start();
			while (SlotThread.step());
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
