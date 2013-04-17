package com.zhuri.ssl;

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

public class EmotionSSLChannel implements ReadableByteChannel {

	interface IStreamIO {
		public void selectIn(SlotWait wait);
		public void selectOut(SlotWait wait);
		public boolean handshake() throws Exception;
		public int read(ByteBuffer dst) throws IOException;
		public int write(ByteBuffer src) throws IOException;
	}

	private SSLEngine engine;
	private IStreamIO ioProxy;
	private SocketChannel sc;
	private SSLEngineResult.HandshakeStatus status;

	private SlotChannel slotChannel = new SlotChannel();
	private ByteBuffer readBuffer = ByteBuffer.allocate(20000);
	private ByteBuffer writeBuffer = ByteBuffer.allocate(20000);

	private final static String LOG_TAG = "EmotionSSLChannel";
	private final static ByteBuffer EMPTY = ByteBuffer.allocate(0);
	private final static TrustManager[] trustManager = 
		new TrustManager[] { new EasyX509TrustManager(null) };

	public void selectIn(SlotWait wait) {
		ioProxy.selectIn(wait);
	};

	public void selectOut(SlotWait wait) {
		ioProxy.selectOut(wait);
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

	private final IStreamIO NormalIO = new IStreamIO() {
		public boolean handshake() throws Exception {
			Set<String> enabledSuites = new HashSet<String>();
			String[] cipherSuites = engine.getSupportedCipherSuites();

			ioProxy = HandshakeIO;
			readBuffer.clear();
			writeBuffer.clear();
			engine.setUseClientMode(true);
			for (String cipherSuite: cipherSuites)
				enabledSuites.add(cipherSuite);
			engine.setEnabledCipherSuites((String[])enabledSuites
					.toArray(new String[enabledSuites.size()]));
			engine.beginHandshake();

			sslInvoke();
			return true;
		}

		public void selectIn(SlotWait wait) {
			slotChannel.wantIn(wait);
		}

		public void selectOut(SlotWait wait) {
			slotChannel.wantOut(wait);
		}

		public int read(ByteBuffer dst) throws IOException {
			return sc.read(dst);
		}

		public int write(ByteBuffer src) throws IOException {
			return sc.write(src);
		}
	};

	private final IStreamIO HandshakeIO = new IStreamIO() {
		public boolean handshake() throws Exception {
			return true;
		}

		public void selectIn(SlotWait wait) {
			mInSlot.record(wait);
		}

		public void selectOut(SlotWait wait) {
			mOutSlot.record(wait);
		}

		public int read(ByteBuffer dst) throws IOException {
			throw new IOException("in ssl handshaking state");
		}

		public int write(ByteBuffer src) throws IOException {
			throw new IOException("in ssl handshaking state");
		}
	};

	private final IStreamIO ProxyIO = new IStreamIO() {
		public boolean handshake() throws Exception {
			return true;
		}

		public void selectIn(SlotWait wait) {
			slotChannel.wantIn(wait);
		}

		public void selectOut(SlotWait wait) {
			slotChannel.wantOut(wait);
		}

		public int read(ByteBuffer dst) throws IOException {
			int readed, produced = 0;
			SSLEngineResult result = null;

			readed = sc.read(readBuffer);
			readBuffer.flip();

			if (!readBuffer.hasRemaining()) {
				readBuffer.clear();
				return readed;
			}

			do {
				result = engine.unwrap(readBuffer, dst);
				produced += result.bytesProduced();
			} while (result.bytesProduced() > 0 && readBuffer.hasRemaining());

			readBuffer.compact();
			readBuffer.limit(20000);
			return produced;
		}

		public int write(ByteBuffer src) throws IOException {
			int produced = 0;
			SSLEngineResult result = null;

			do {
				result = engine.wrap(src, writeBuffer);
				status = result.getHandshakeStatus();
				produced += result.bytesProduced();
			} while (status == SSLEngineResult.HandshakeStatus.NEED_WRAP);

			DEBUG.Assert(!src.hasRemaining());

			writeBuffer.flip();
			produced = sc.write(writeBuffer);

			DEBUG.Assert(!writeBuffer.hasRemaining());
			writeBuffer.clear();
			return produced;
		}
	};

	public EmotionSSLChannel(SocketChannel sc) {
		SSLContext sslContext;

		this.sc = sc;
		this.ioProxy = NormalIO;

		try  {
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustManager, null);
			engine = sslContext.createSSLEngine();
			slotChannel.attach(sc);
		} catch (Exception e) {
			e.printStackTrace();
			/* throw e; */
		}
	}

	public void sslInvoke() {
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
							slotChannel.wantIn(mInWait);
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
						int produced = 0, sent = 0;
						while (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
							result = engine.wrap(EMPTY, writeBuffer);
							status = result.getHandshakeStatus();
							produced += result.bytesProduced();
						}

						DEBUG.Assert(writeBuffer.position() == produced);
						writeBuffer.flip();
						sent = sc.write(writeBuffer);
						DEBUG.Assert(sent == produced);
						writeBuffer.clear();
						break;
						//}

					case NEED_UNWRAP:
						//{
						int count;
						int bytesConsumed = 0;

						count = sc.read(readBuffer);
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
							slotChannel.wantIn(mInWait);
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

	public int write(ByteBuffer src) throws IOException {
		return ioProxy.write(src);
	}

	public int read(ByteBuffer dst) throws IOException {
		return ioProxy.read(dst);
	}

	public void close() {
		throw new UnsupportedOperationException("close");
	}

	public boolean isOpen() {
		throw new UnsupportedOperationException("isOpen");
	}
}
