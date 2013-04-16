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

public class EmotionSSLChannel implements ReadableByteChannel {

	interface IStreamIO {
		public boolean handshake() throws Exception;
		public int read(ByteBuffer dst) throws IOException;
		public int write(ByteBuffer src) throws IOException;
	}

	private boolean tls;
	private boolean shaking;

	private IStreamIO ioProxy;
	private SSLEngine engine;
	private SocketChannel sc;
	private SSLEngineResult.HandshakeStatus status;
	private ByteBuffer readBuffer = ByteBuffer.allocate(20000);
	private ByteBuffer writeBuffer = ByteBuffer.allocate(20000);

	private final static ByteBuffer EMPTY = ByteBuffer.allocate(0);
	private final static TrustManager[] trustManager = 
		new TrustManager[] { new EasyX509TrustManager(null) };

	private final IStreamIO NormalIO = new IStreamIO() {
		public boolean handshake() throws Exception {
			Set<String> enabledSuites = new HashSet<String>();
			String[] cipherSuites = engine.getSupportedCipherSuites();

			ioProxy = ProxyIO;
			readBuffer.clear();
			writeBuffer.clear();
			engine.setUseClientMode(true);
			for (String cipherSuite: cipherSuites)
				enabledSuites.add(cipherSuite);
			engine.setEnabledCipherSuites((String[])enabledSuites
					.toArray(new String[enabledSuites.size()]));
			engine.beginHandshake();

			invoke();
			return true;
		}

		public int read(ByteBuffer dst) throws IOException {
			return sc.read(dst);
		}

		public int write(ByteBuffer src) throws IOException {
			return sc.write(src);
		}
	};

	private final IStreamIO ProxyIO = new IStreamIO() {
		public boolean handshake() throws Exception {
			return true;
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
		this.tls = false;
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

	public void invoke() throws Exception {
		SSLEngineResult result;
		status = engine.getHandshakeStatus();
		while (status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
			switch (status) {
				case FINISHED:
					shaking = false;
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
							System.out.println("no buffer data");
							break;
						}

						writeBuffer.clear();
						result = engine.unwrap(readBuffer, writeBuffer);
						status = result.getHandshakeStatus();
						bytesConsumed += result.bytesConsumed();

						if (0 == result.bytesConsumed()) {
							System.out.println("need more buffer data");
							break;
						}
					} while (status == HandshakeStatus.NEED_UNWRAP);

					readBuffer.compact();
					readBuffer.limit(20000);

					if (bytesConsumed == 0) {
						DEBUG.Assert(status == HandshakeStatus.NEED_UNWRAP);
						return;
					}
					break;
					//}

				case NOT_HANDSHAKING:
					shaking = false;
					return;
			}
		}

		shaking = false;
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

