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
			channel.waitI(wait);
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
			readBuffer.limit(20000);
			return (readed == -1 && produced == 0)? -1: produced;
		}

		public long write(ByteBuffer src) throws IOException {
			long produced = 0;
			SSLEngineResult result = null;

			do {
				result = engine.wrap(src, writeBuffer);
				status = result.getHandshakeStatus();
				produced += result.bytesProduced();
			} while (status == SSLEngineResult.HandshakeStatus.NEED_WRAP);

			if (src.hasRemaining()) {
				DEBUG.Print("WaitableSslChannel", "write error");
				return -1;
			}

			writeBuffer.flip();
			produced = channel.write(writeBuffer);

			if (writeBuffer.hasRemaining()) {
				DEBUG.Print("WaitableSslChannel", "write error");
				return -1;
			}
			writeBuffer.clear();
			return produced;
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
					case FINISHED:
						if (readBuffer.hasRemaining())
							mInSlot.wakeup();
						else if (!mInSlot.isEmpty())
							channel.waitI(mInWait);
						mOutSlot.wakeup();
						ioProxy = ProxyIO;
						return;

					case NEED_TASK:
						//{
						Runnable task = engine.getDelegatedTask();
						while (task != null) {
							task.run();
							task = engine.getDelegatedTask();
						}
						status = engine.getHandshakeStatus();
						break;
						//}

					case NEED_WRAP:
						//{
						long produced = 0, sent = 0;
						while (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
							result = engine.wrap(EMPTY, writeBuffer);
							status = result.getHandshakeStatus();
							produced += result.bytesProduced();
						}

						DEBUG.Assert(writeBuffer.position() == produced);
						writeBuffer.flip();
						sent = channel.write(writeBuffer);
						DEBUG.Assert(sent == produced);
						writeBuffer.clear();
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

							writeBuffer.clear();
							result = engine.unwrap(readBuffer, writeBuffer);
							status = result.getHandshakeStatus();
							bytesConsumed += result.bytesConsumed();

							if (0 == result.bytesConsumed()) {
								DEBUG.Print(LOG_TAG, "need more buffer data");
								break;
							}

						} while (status == HandshakeStatus.NEED_UNWRAP);

						readBuffer.compact();
						readBuffer.limit(20000);

						if (bytesConsumed == 0) {
							DEBUG.Assert(status == HandshakeStatus.NEED_UNWRAP);
							channel.waitI(mInWait);
							return;
						}
						break;
						//}

					case NOT_HANDSHAKING:
						/* TODO: NOT_HANDSHAKING */
						break;
				}

			}

		} catch (Exception e) {
			/* TODO: Handshake Exception. */
			e.printStackTrace();
		}

		ioProxy = NormalIO;
		mOutSlot.wakeup();
		mInSlot.wakeup();
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
