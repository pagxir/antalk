/*
 * Copyright 2004 WIT-Software, Lda.
 * - web: http://www.wit-software.com
 * - email: info@wit-software.com
 *
 * All rights reserved. Relased under terms of the
 * Creative Commons' Attribution-NonCommercial-ShareAlike license.
 */
package com.zhuri.ssl;

import com.zhuri.slot.*;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.nio.channels.SocketChannel;

/**
 * @author Nuno Santos
 */
public class SSLChannelFactory {
	private final boolean clientMode;
	private final SSLContext sslContext;


	public static SSLContext createSSLContext(boolean clientMode) throws Exception {
		//SSLContext sslContext = SSLContext.getDefault();
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, null, null);
		//sslContext.init(null, new TrustManager[]{new EasyX509TrustManager(null)}, null);
		return sslContext;
	}

	public SSLChannelFactory(boolean clientMode) throws Exception {
		this.clientMode = clientMode;
		sslContext = createSSLContext(clientMode);
	}

	public SSLChannel createChannel(SlotChannel slot, SocketChannel sc) throws Exception {
		SSLEngine engine = sslContext.createSSLEngine();
		engine.setUseClientMode(clientMode);
		return new SSLChannel(slot, sc, engine);
	}
}
