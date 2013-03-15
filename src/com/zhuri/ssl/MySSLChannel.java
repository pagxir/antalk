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

public class MySSLChannel implements ReadableByteChannel {

	private boolean handshaking;
	private boolean tls;
	private SocketChannel sc;
	private SSLEngine engine;
	private SSLEngineResult.HandshakeStatus status;
	private ByteBuffer readBuffer = ByteBuffer.allocate(20000);
	private ByteBuffer writeBuffer = ByteBuffer.allocate(20000);
	private final static ByteBuffer EMPTY = ByteBuffer.allocate(0);
	
	private final static TrustManager[] trustManager = 
		new TrustManager[] { new EasyX509TrustManager(null) };

	public MySSLChannel(SocketChannel sc) {
		SSLContext sslContext;

		try  {
			sslContext = SSLContext.getInstance("TLS");
			//sslContext.init(null, null, null);
			sslContext.init(null, trustManager, null);
			engine = sslContext.createSSLEngine();
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.sc = sc;
		this.tls = false;
	}

	public boolean doHandshake() throws Exception {

		if (tls == false) {
			tls = true;

			engine.setUseClientMode(true);
			Set<String> enabledSuites = new HashSet<String>();
			String[] cipherSuites = engine.getSupportedCipherSuites();
			for (String cipherSuite: cipherSuites)
				enabledSuites.add(cipherSuite);
			engine.setEnabledCipherSuites((String[])enabledSuites
				.toArray(new String[enabledSuites.size()]));

			readBuffer.clear();
			writeBuffer.clear();
			engine.beginHandshake();
		}

		status = engine.getHandshakeStatus();
		while (status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
			switch (status) {
			case FINISHED:
				handshaking = false;
				return true;

			case NEED_TASK: {
				Runnable task = engine.getDelegatedTask();
				while (task != null) {
					task.run();
					task = engine.getDelegatedTask();
				}
			}
				status = engine.getHandshakeStatus();
				break;

			case NEED_WRAP: {
				int produced = 0, sent = 0;
				SSLEngineResult result = null;
				while (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
					result = engine.wrap(EMPTY, writeBuffer);
					status = result.getHandshakeStatus();
					produced += result.bytesProduced();
				}

				assert(writeBuffer.position() == produced);
				writeBuffer.flip();
				sent = sc.write(writeBuffer);
				assert(sent == produced);
				writeBuffer.clear();
			}
			break;

			case NEED_UNWRAP: {
				int count;
				int bytesConsumed = 0;
				SSLEngineResult result;
				
				count = sc.read(readBuffer);
				RUNTIME_ASSERT(count != -1);

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
					RUNTIME_ASSERT(status == HandshakeStatus.NEED_UNWRAP);
					return false;
				}
			}
			break;

			case NOT_HANDSHAKING:
				handshaking = false;
				return true;
			}
		}

		handshaking = false;
		return true;
	}

	public void RUNTIME_ASSERT(boolean condition) {
		if (condition == false)
			throw new RuntimeException("RUNTIME_ASSERT");
		return;
	}

	public int read(ByteBuffer dst) throws IOException {
		int readed, produced = 0;
		SSLEngineResult result;

		if (tls) {
			readed = sc.read(readBuffer);
			readBuffer.flip();

			if (readBuffer.hasRemaining()) {
				do {
					result = engine.unwrap(readBuffer, dst);
					produced += result.bytesProduced();
				} while (result.bytesProduced() > 0 && readBuffer.hasRemaining());
				readBuffer.compact();
				readBuffer.limit(20000);
				return produced;
			}

			readBuffer.clear();
			return readed;
		}

		return sc.read(dst);
	}

	public int write(ByteBuffer dst) throws IOException {
		int produced = 0;
		SSLEngineResult result = null;

		if (tls) {
			do {
				result = engine.wrap(dst, writeBuffer);
				status = result.getHandshakeStatus();
				produced += result.bytesProduced();
			} while (status == SSLEngineResult.HandshakeStatus.NEED_WRAP);
			assert(!dst.hasRemaining());
			writeBuffer.flip();
			produced = sc.write(writeBuffer);
			assert(!writeBuffer.hasRemaining());
			writeBuffer.clear();
			return produced;
		}

		return sc.write(dst);
	}

	public void close() {

	}

	public boolean isOpen() {
		return false;
	}
}

