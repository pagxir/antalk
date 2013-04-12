/**
 * Copyright (C) 2003 Alexander Kout
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.zhuri.ssl;

import java.net.*;
import java.nio.*;
import java.util.*;
import java.nio.channels.*;
import javax.net.ssl.*;
import java.io.*;

/**
 *  a SocketChannel with TLS/SSL encryption
 *
 *@author     Alexander Kout
 *@created    25. Mai 2005
 */

public class SSLSocketChannel implements ReadableByteChannel {

    int SSL;
    ByteBuffer clientIn, clientOut, cTOs, sTOc, wbuf;
    SocketChannel sc = null;
    SSLEngineResult res;
    SSLEngine sslEngine;

    public SSLSocketChannel() throws IOException {
        sc = SocketChannel.open();
    }

    public SSLSocketChannel(SocketChannel sc) {
        this.sc = sc;
    }

    public int tryTLS(int pSSL) throws Exception {
        SSL = pSSL;
        if (SSL == 0)
            return 0;

        SSLContext sslContext=null;
        try {
            // create SSLContext
            //sslContext = SSLContext.getDefault();
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, null, null);
            //sslContext.init(null, new TrustManager[] {new EasyX509TrustManager(null)}, null);

            // create Engine
            sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(true);
            Set<String> enabledSuites = new HashSet<String>();
            String[] cipherSuites = sslEngine.getSupportedCipherSuites();
            for (String cipherSuite: cipherSuites)
                enabledSuites.add(cipherSuite);
            sslEngine.setEnabledCipherSuites((String[])enabledSuites
                                          .toArray(new String[enabledSuites.size()]));

            //sslEngine.setEnableSessionCreation(true);
            createBuffers(sslEngine.getSession());
            // wrap
            sslEngine.beginHandshake();
            clientOut.clear();
            sc.write(wrap(clientOut));

            while (res.getHandshakeStatus() !=
                    SSLEngineResult.HandshakeStatus.FINISHED) {
                if (res.getHandshakeStatus() ==
                        SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
// unwrap
                    sTOc.clear();
                    while (sc.read(sTOc) < 1)
                        Thread.sleep(20);
                    sTOc.flip();
                    unwrap(sTOc);
                    if (res.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED) {
                        clientOut.clear();
                        sc.write(wrap(clientOut));
                    }
                } else if (res.getHandshakeStatus() ==
                           SSLEngineResult.HandshakeStatus.NEED_WRAP) {
// wrap
                    clientOut.clear();
                    sc.write(wrap(clientOut));
                } else {
                    Thread.sleep(1000);
                }
            }

            clientIn.clear();
            clientIn.flip();
            SSL = 4;
        } catch (Exception e) {
            e.printStackTrace(System.out);
            SSL = 0;
            throw e;
        }
        return SSL;
    }

    private synchronized ByteBuffer wrap(ByteBuffer b) throws SSLException {
        cTOs.clear();
        res = sslEngine.wrap(b, cTOs);
        cTOs.flip();
        return cTOs;
    }

    private synchronized ByteBuffer unwrap(ByteBuffer b) throws SSLException {
        clientIn.clear();
        int pos;
        while (b.hasRemaining()) {
            res = sslEngine.unwrap(b, clientIn);
            if (res.getHandshakeStatus() ==
                    SSLEngineResult.HandshakeStatus.NEED_TASK) {
                // Task
                Runnable task;
                while ((task=sslEngine.getDelegatedTask()) != null)
                {
                    task.run();
                }
            } else if (res.getHandshakeStatus() ==
                       SSLEngineResult.HandshakeStatus.FINISHED) {
                return clientIn;
            } else if (res.getStatus() ==
                       SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                return clientIn;
            }
        }
        return clientIn;
    }

    private void createBuffers(SSLSession session) {

        int appBufferMax = session.getApplicationBufferSize();
        int netBufferMax = session.getPacketBufferSize();

        clientIn = ByteBuffer.allocate(65536);
        clientOut = ByteBuffer.allocate(appBufferMax);
        wbuf = ByteBuffer.allocate(65536);

        cTOs = ByteBuffer.allocate(netBufferMax);
        sTOc = ByteBuffer.allocate(netBufferMax);

    }

    public int write(ByteBuffer src) throws IOException {
        if (SSL == 4) {
            return sc.write(wrap(src));
        }
        return sc.write(src);
    }

    public int read(ByteBuffer dst) throws IOException {
        int amount = 0, limit;
        if (SSL == 4) {
            // test if there was a buffer overflow in dst
            if (clientIn.hasRemaining()) {
                limit = Math.min(clientIn.remaining(), dst.remaining());
                for (int i = 0; i < limit; i++) {
                    dst.put(clientIn.get());
                    amount++;
                }
                return amount;
            }
            // test if some bytes left from last read (e.g. BUFFER_UNDERFLOW)
            if (sTOc.hasRemaining()) {
                unwrap(sTOc);
                clientIn.flip();
                limit = Math.min(clientIn.limit(), dst.remaining());
                for (int i = 0; i < limit; i++) {
                    dst.put(clientIn.get());
                    amount++;
                }
                if (res.getStatus() != SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    sTOc.clear();
                    sTOc.flip();
                    return amount;
                }
            }
            if (!sTOc.hasRemaining())
                sTOc.clear();
            else
                sTOc.compact();

            if (sc.read(sTOc) == -1) {
                sTOc.clear();
                sTOc.flip();
                return -1;
            }
            sTOc.flip();
            unwrap(sTOc);
            // write in dst
            clientIn.flip();
            limit = Math.min(clientIn.limit(), dst.remaining());
            for (int i = 0; i < limit; i++) {
                dst.put(clientIn.get());
                amount++;
            }
            return amount;
        }
        return sc.read(dst);
    }

    public boolean isConnected() {
        return sc.isConnected();
    }

    public void close() throws IOException {
        if (SSL == 4) {
            sslEngine.closeOutbound();
            clientOut.clear();
            sc.write(wrap(clientOut));
            sc.close();
        } else
            sc.close();
    }

    public SelectableChannel configureBlocking(boolean b) throws IOException {
        return sc.configureBlocking(b);
    }

    public boolean connect(SocketAddress remote) throws IOException {
        return sc.connect(remote);
    }

    public boolean finishConnect() throws IOException {
        return sc.finishConnect();
    }

    public Socket socket() {
        return sc.socket();
    }

    public boolean isInboundDone() {
        return sslEngine.isInboundDone();
    }

    public boolean isOpen() {
        return false;
    }
}
