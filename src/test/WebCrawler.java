package test;

import java.net.*;
import java.nio.*;
import com.zhuri.slot.*;
import java.nio.channels.*;

import java.io.IOException;
import com.zhuri.net.XyConnector;
import com.zhuri.net.WaitableSslChannel;

class HttpCrawler {
	private URL mUrl;
	private IWaitableChannel mChannel;

	private SlotWait mIWait = new SlotWait() {
		public void invoke() {
			long count = -1;
			String message = "";
			ByteBuffer buffer = ByteBuffer.allocate(80000);

			try {
				count = mChannel.read(buffer);
			} catch (IOException e) {
				e.printStackTrace();
				close();
				return;
			}

			if (count != -1) {
				message = new String(buffer.array(), 0, (int)count);
				System.out.println(message);
				mChannel.waitI(mIWait);
				return;
			}

			System.out.println("stream is close");
			close();
			return;
		}
	};

	private SlotWait mOWait = new SlotWait() {
		public void invoke() {
			long count;
			String message =
				"GET " + mUrl.getFile() + " HTTP/1.0\r\n" +
				"Host: " + mUrl.getHost() + "\r\n" +
				"\r\n";
			ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());

			System.out.println("sending http header " + message);
			try {
				count = mChannel.write(buffer);
			} catch (IOException e) {
				e.printStackTrace();
				close();
			}

			return;
		}
	};

	public HttpCrawler(URL url, IWaitableChannel channel) {
		mUrl = url;
		mChannel = channel;
	}

	public void start() {
		mChannel.waitO(mOWait);
		mChannel.waitI(mIWait);
	}

	public void close() {
		mIWait.clean();
		mOWait.clean();
	}
}

public class WebCrawler {
	URL mUrl;

	public WebCrawler(String url) {
		try {
			mUrl = new URL(url);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	public void start() {
		HttpCrawler crawler;
		WaitableSslChannel sslChannel;
		XyConnector proxy = new XyConnector("223.167.213.254:9418");

		int port = mUrl.getPort();
		String authority = mUrl.getAuthority();
		if (mUrl.getProtocol().equals("http")) {
			String addon = port == -1? ":80": "";
			proxy.connect(authority + addon);
			crawler = new HttpCrawler(mUrl, proxy);
			crawler.start();
		} else if (mUrl.getProtocol().equals("https")) {
			String addon = port == -1? ":443": "";
			proxy.connect(authority + addon);
			sslChannel = new WaitableSslChannel(proxy);
			try { sslChannel.handshake(); } catch (Exception e) { e.printStackTrace(); };
			crawler = new HttpCrawler(mUrl, sslChannel);
			crawler.start();
		}
	}
}

