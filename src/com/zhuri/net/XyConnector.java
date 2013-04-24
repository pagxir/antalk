package com.zhuri.net;

import java.net.*;
import java.nio.*;
import com.zhuri.slot.*;
import java.nio.channels.*;

import java.io.IOException;

public class XyConnector extends Connector {
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

	public XyConnector(String proxy) {
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
