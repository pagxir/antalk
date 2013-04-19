package test;

import java.net.*;
import java.nio.*;
import com.zhuri.slot.*;
import java.nio.channels.*;

import java.io.IOException;
import com.zhuri.ssl.WaitableSslChannel;

interface IConnectable {
	public void connect(String target);
}

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

class Connector implements IWaitableChannel, IConnectable {
	SlotChannel sChannel;
	SocketChannel mSocket;

	public Connector() {
		try {
			mSocket = SocketChannel.open();
			sChannel = SlotChannel.open(mSocket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* mSocket.finishConnect(); */
	public void connect(String target) {
		String[] parts;

		try {
			parts = target.split(":");
			mSocket.connect(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void waitI(SlotWait wait) {
		sChannel.wantIn(wait);
	}

	public void waitO(SlotWait wait) {
		sChannel.wantOut(wait);
	}

	public long read(ByteBuffer dst) throws IOException {
		mSocket.finishConnect();
		return mSocket.read(dst);
	}

	public long write(ByteBuffer src) throws IOException {
		mSocket.finishConnect();
		return mSocket.write(src);
	}

	public void close() throws IOException {
		sChannel.detach();
		mSocket.close();
	}
}

class XyConnect implements IWaitableChannel, IConnectable {
	String mProxy;
	String mAuthority;
	Connector mConnector;
	IWaitableChannel mIOProxy;
	final SlotSlot mInSlot = new SlotSlot();
	final SlotSlot mOutSlot = new SlotSlot();

	private final IWaitableChannel NonConnectedIO = new IWaitableChannel() {
		public void waitI(SlotWait wait) {
			mInSlot.record(wait);
		}

		public void waitO(SlotWait wait) {
			mOutSlot.record(wait);
		}

		public long read(ByteBuffer dst) throws IOException {
			throw new IOException("not connected");
		}

		public long write(ByteBuffer src) throws IOException {
			throw new IOException("not connected");
		}
	};

	private final IWaitableChannel ConnectedIO = new IWaitableChannel() {
		public void waitI(SlotWait wait) {
			mConnector.waitI(wait);
		}

		public void waitO(SlotWait wait) {
			mConnector.waitO(wait);
		}

		public long read(ByteBuffer dst) throws IOException {
			return mConnector.read(dst);
		}

		public long write(ByteBuffer src) throws IOException {
			return mConnector.write(src);
		}
	};

	private final SlotWait mSWait = new SlotWait() {
		public void invoke() {
			mInSlot.wakeup();
			return;
		}
	};

	private final SlotWait mOWait = new SlotWait() {
		public void invoke() {
			String xyHead =
				"CONNECT " + mAuthority + " HTTP/1.0\r\n" +
				"Proxy-Authorization: Basic cHJveHk6QWRabkdXVE0wZExUCg==\r\n" +
				"\r\n";

			System.out.println("Conntion " + xyHead);
			ByteBuffer buffer = ByteBuffer.wrap(xyHead.getBytes());

			try {
				mConnector.write(buffer);
			} catch (IOException e) {
				e.printStackTrace();
			}

			mConnector.waitI(mIWait);
		}
	};

	private final SlotWait mIWait = new SlotWait() {
		public void invoke() {
			long count = -1;
			String title = "";
			ByteBuffer buffer = ByteBuffer.allocate(8000);

			try {
				count = mConnector.read(buffer);
				title = new String(buffer.array(), 0, (int)count);

				if (title.endsWith("\r\n\r\n")) {
				System.out.println("Proxy Response: " + title);
					mIOProxy = ConnectedIO;
					mConnector.waitI(mSWait);
					mOutSlot.wakeup();
					mIWait.clean();
					return;
				}
			} catch (Exception e) {
				mIOProxy = ConnectedIO;
				mOutSlot.wakeup();
				mInSlot.wakeup();
				close();
				return;
			}

			if (count != -1) {
				mConnector.waitI(mIWait);
				return;
			}

			mIOProxy = ConnectedIO;
			mOutSlot.wakeup();
			mInSlot.wakeup();
			close();
		}
	};

	public XyConnect(String proxy) {
		mProxy = proxy;
		mIOProxy = NonConnectedIO;
		mConnector = new Connector();
	}

	public void connect(String authority) {
		mAuthority = authority;
		mConnector.waitO(mOWait);
		mConnector.connect(mProxy);
	}

	public void waitI(SlotWait wait) {
		mIOProxy.waitI(wait);
	}

	public void waitO(SlotWait wait) {
		mIOProxy.waitO(wait);
	}

	public long read(ByteBuffer dst) throws IOException {
		return mIOProxy.read(dst);
	}

	public long write(ByteBuffer src) throws IOException {
		return mIOProxy.write(src);
	}

	public void close() {
		mIWait.clean();
		mOWait.clean();
	}
}

public class WebCrawler {
	URL mUrl;

	public WebCrawler(String url) throws Exception {
		mUrl = new URL(url);
	}

	public void start() {
		HttpCrawler crawler;
		WaitableSslChannel sslChannel;
		XyConnect proxy = new XyConnect("223.167.213.254:9418");

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

