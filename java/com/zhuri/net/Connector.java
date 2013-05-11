package com.zhuri.net;

import java.net.*;
import java.nio.*;
import com.zhuri.slot.*;
import java.nio.channels.*;

import java.io.IOException;

public class Connector implements IWaitableChannel, IConnectable {
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

