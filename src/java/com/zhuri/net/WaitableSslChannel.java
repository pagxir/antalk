package com.zhuri.net;

import java.util.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import com.zhuri.util.DEBUG;
import com.zhuri.slot.SlotWait;
import com.zhuri.slot.SlotSlot;
import com.zhuri.slot.SlotChannel;
import com.zhuri.slot.IWaitableChannel;

public class WaitableSslChannel implements IWaitableChannel {

	private SSLEngine engine;
	private IWaitableChannel channel;
	private IWaitableSslChannel ioProxy;
	private SSLEngineResult.HandshakeStatus status;

	private ByteBuffer tempBuffer = null;
	private ByteBuffer readBuffer = ByteBuffer.allocate(20000);
	private ByteBuffer writeBuffer = ByteBuffer.allocate(20000);

	private final static String LOG_TAG = "WaitSslChannel";
	private final static ByteBuffer EMPTY = ByteBuffer.allocate(0);
	private final static TrustManager[] trustManager = 
		new TrustManager[] { new EasyX509TrustManager(null) };

	public void waitI(SlotWait wait) {
		ioProxy.waitI(wait);
	}

	public void waitO(SlotWait wait) {
		ioProxy.waitO(wait);
	};

	private final SlotWait mInWait = new SlotWait() {
		public void invoke() {
			DEBUG.Print(LOG_TAG, "mInWait");
			if (ioProxy == ProxyIO) {
				mInSlot.wakeup();
				return;
			}
			sslInvoke();
		}
	};

	private final SlotWait mOutWait = new SlotWait() {
		public void invoke() {
			DEBUG.Print(LOG_TAG, "mOutWait");

			if (tempBuffer != null) {
				try {
					channel.write(tempBuffer);
				} catch (IOException e) {
					tempBuffer.position(tempBuffer.limit());
				}

				if (tempBuffer.hasRemaining()) {
					channel.waitO(mOutWait);
					return;
				}

				tempBuffer.clear();
				tempBuffer = null;
			}

			if (ioProxy == ProxyIO) {
				mOutSlot.wakeup();
				return;
			}

			sslInvoke();
		}
	};

	private final SlotSlot mInSlot = new SlotSlot();
	private final SlotSlot mOutSlot = new SlotSlot();

	interface IWaitableSslChannel extends IWaitableChannel {
		boolean handshake() throws Exception;
	}

	private final IWaitableSslChannel NormalIO = new IWaitableSslChannel() {
		public boolean handshake() throws Exception {
			Set<String> enabledSuites = new HashSet<String>();

			ioProxy = HandshakeIO;
			readBuffer.clear();
			writeBuffer.clear();
			engine.setUseClientMode(true);

			String[] cipherSuites = engine.getSupportedCipherSuites();
			for (String cipherSuite: cipherSuites)
				enabledSuites.add(cipherSuite);
			engine.setEnabledCipherSuites((String[])enabledSuites
					.toArray(new String[enabledSuites.size()]));
			engine.beginHandshake();

			channel.waitO(mInWait);
			return true;
		}

		public void waitI(SlotWait wait) {
			channel.waitI(wait);
		}

		public void waitO(SlotWait wait) {
			channel.waitO(wait);
		}

		public long read(ByteBuffer dst) throws IOException {
			return channel.read(dst);
		}

		public long write(ByteBuffer src) throws IOException {
			return channel.write(src);
		}
	};

	private final IWaitableSslChannel HandshakeIO = new IWaitableSslChannel() {
		public boolean handshake() throws Exception {
			return true;
		}

		public void waitI(SlotWait wait) {
			mInSlot.record(wait);
		}

		public void waitO(SlotWait wait) {
			mOutSlot.record(wait);
		}

		public long read(ByteBuffer dst) throws IOException {
			throw new IOException("in ssl handshaking state");
		}

		public long write(ByteBuffer src) throws IOException {
			throw new IOException("in ssl handshaking state");
		}
	};

	private final IWaitableSslChannel ProxyIO = new IWaitableSslChannel() {
		public boolean handshake() throws Exception {
			return true;
		}

		public void waitI(SlotWait wait) {
			channel.waitI(wait);
		}

		public void waitO(SlotWait wait) {
			channel.waitO(wait);
		}

		public long read(ByteBuffer dst) throws IOException {
			long readed, produced = 0;
			SSLEngineResult result = null;

			readed = channel.read(readBuffer);
			readBuffer.flip();

			if (!readBuffer.hasRemaining()) {
				readBuffer.clear();
				return readed;
			}

			do {
				result = engine.unwrap(readBuffer, dst);
				produced += result.bytesProduced();
			} while (result.bytesProduced() > 0 && readBuffer.hasRemaining());

/*
			int appSize = engine.getSession().getApplicationBufferSize();
			DEBUG.Print(LOG_TAG, "produced: " + produced + "appSize: " + appSize);
			DEBUG.Print(LOG_TAG, "Status: " + result.getStatus());
*/
			readBuffer.compact();
			return (readed == -1 && produced == 0)? -1: produced;
		}

		public long write(ByteBuffer src) throws IOException {
			long produced = 0;
			SSLEngineResult result = null;

			if (tempBuffer != null) {
				DEBUG.Print(LOG_TAG, "waiting flush buffer");
				return 0;
			}

			DEBUG.Assert(writeBuffer.hasRemaining());
			result = engine.wrap(src, writeBuffer);

			writeBuffer.flip();
			channel.write(writeBuffer);

			if (writeBuffer.hasRemaining()) {
				DEBUG.Print(LOG_TAG, "pending for flush buffer");
				tempBuffer = writeBuffer;
				channel.waitO(mOutWait);
				return result.bytesConsumed();
			}

			writeBuffer.clear();
			return result.bytesConsumed();
		}
	};

	public WaitableSslChannel(IWaitableChannel channel) {
		SSLContext sslContext;

		this.channel = channel;
		this.ioProxy = NormalIO;

		try  {
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustManager, null);
			engine = sslContext.createSSLEngine();
		} catch (Exception e) {
			e.printStackTrace();
			/* throw e; */
		}
	}

	private void sslInvoke() {
		SSLEngineResult result;
		DEBUG.Print(LOG_TAG, "sslInvoke");

		try {
			status = engine.getHandshakeStatus();
			while (status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {

				DEBUG.Print(LOG_TAG, "HandshakeStatus " + status);
				switch (status) {
					case NEED_TASK:
						//{
						engine.getDelegatedTask().run();
						status = engine.getHandshakeStatus();
						break;
						//}

					case NEED_WRAP:
						//{
						long produced = 0;
						DEBUG.Assert(tempBuffer == null);

						result = engine.wrap(EMPTY, writeBuffer);
						status = result.getHandshakeStatus();

						DEBUG.Assert(writeBuffer.position() == result.bytesProduced());

						writeBuffer.flip();
						channel.write(writeBuffer);
						if (writeBuffer.hasRemaining()) {
							tempBuffer = writeBuffer;
							channel.waitO(mOutWait);
							return;
						}
						writeBuffer.compact();
						break;
						//}

					case NEED_UNWRAP:
						//{
						long count;
						long bytesConsumed = 0;

						count = channel.read(readBuffer);
						DEBUG.Assert(count != -1);

						readBuffer.flip();
						do {

							if (!readBuffer.hasRemaining()) {
								DEBUG.Print(LOG_TAG, "no buffer data");
								break;
							}

							ByteBuffer b = ByteBuffer.allocate(10000);
							result = engine.unwrap(readBuffer, b);
							status = result.getHandshakeStatus();
							bytesConsumed += result.bytesConsumed();

							DEBUG.Print(LOG_TAG, "limit " + b.limit());
							DEBUG.Print(LOG_TAG, "position " + b.position());
							DEBUG.Assert(b.position() == 0);

							if (0 == result.bytesConsumed()) {
								DEBUG.Print(LOG_TAG, "need more buffer data");
								break;
							}

						} while (status == HandshakeStatus.NEED_UNWRAP);

						readBuffer.compact();

						if (bytesConsumed == 0) {
							DEBUG.Assert(status == HandshakeStatus.NEED_UNWRAP);
							channel.waitI(mInWait);
							return;
						}
						break;
						//}

					default:
						status = engine.getHandshakeStatus();
						break;
				}
			}

		} catch (Exception e) {
			/* TODO: Handshake Exception. */
			e.printStackTrace();
			ioProxy = NormalIO;
			mOutSlot.wakeup();
			mInSlot.wakeup();
			return;
		}

		if (readBuffer.hasRemaining())
			mInSlot.wakeup();
		else if (!mInSlot.isEmpty())
			channel.waitI(mInWait);
		mOutSlot.wakeup();
		ioProxy = ProxyIO;
		return;
	}

	public boolean handshake() throws Exception {
		return ioProxy.handshake();
	}

	public long write(ByteBuffer src) throws IOException {
		return ioProxy.write(src);
	}

	public long read(ByteBuffer dst) throws IOException {
		return ioProxy.read(dst);
	}

	public void close() {
		throw new UnsupportedOperationException("close");
	}

	public boolean isOpen() {
		throw new UnsupportedOperationException("isOpen");
	}
}
