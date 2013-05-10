package com.zhuri.net;

import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.io.IOException;

import com.zhuri.net.*;
import com.zhuri.util.*;
import com.zhuri.slot.*;

public class STUNClient {

	final static short ZERO_PADDING = 0x0000;
	final static short BINDING_REQUEST = 0x0001;
	final static short BINDING_RESPONSE = 0x0101;

	final static int MAPPED_ADDRESS = 0x0001;
	final static int CHANGE_REQUEST = 0x0003;
	final static int CHANGED_ADDRESS = 0x0005;

	final static int STUN_TID0 = 0x0005;
	final static int STUN_TID1 = 0x000a;
	final static int STUN_TID2 = 0x0005;
	final static int STUN_TID3 = 0x000a;

	private SlotSlot slep = null;
	private SlotChannel slchannel = null;
	private DatagramChannel channel = null;
	private InetSocketAddress soaddr = null;
	private InetSocketAddress stunServer = null;

	public STUNClient(DatagramChannel channel, String domain, int port) {

		try {
			this.channel = channel;
			this.channel.socket().bind(null);
			this.slchannel = SlotChannel.open(channel);
			this.stunServer = InetUtil.getInetSocketAddress(domain, port);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

		this.slep = new SlotSlot();
	}

	private ByteBuffer hold;
	private SlotTimer redo = new SlotTimer() {
		public void invoke() {
			hold.mark();
			try {
				channel.send(hold, stunServer);
			} catch (Exception e) {
				e.printStackTrace();
			}
			hold.reset();
			reset(1000);
		}
	};

	public boolean input(ByteBuffer packet) {
		soaddr = getMappedAddress(packet);
		if (soaddr != null) {
			hold = null;
			redo.cancel();
			slep.wakeup();
		}
		return (soaddr != null);
	}

	private SlotWait mIWait = new SlotWait() {
		public void invoke() {
			try {
				ByteBuffer b = ByteBuffer.allocate(65536);
				SocketAddress c = channel.receive(b);
				b.flip();
				input(b);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	};

	public void send(ByteBuffer buffer, SlotWait wait) {
		buffer.mark();
		try {
			channel.send(buffer, stunServer);
		} catch (Exception e) {
			e.printStackTrace();
		}
		buffer.reset();
		hold = buffer;

		slchannel.wantIn(mIWait);
		slep.record(wait);
		redo.reset(1000);
	}

	public void requestMapping(SlotWait wait) {
		ByteBuffer b = requestMapping();
		send(b, wait);
	}

	public ByteBuffer requestMapping() {
		ByteBuffer buffer = ByteBuffer.allocate(20);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.putShort(I16(BINDING_REQUEST));
		buffer.putShort(I16(ZERO_PADDING));
		buffer.putInt(I32(STUN_TID0));
		buffer.putInt(I32(STUN_TID1));
		buffer.putInt(I32(STUN_TID2));
		buffer.putInt(I32(STUN_TID3));
		buffer.flip();
		return buffer;
	}

	public ByteBuffer requestChanging(boolean sameIP) {
		ByteBuffer buffer = ByteBuffer.allocate(28);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.putShort(I16(BINDING_REQUEST));
		buffer.putShort(I16(ZERO_PADDING));
		buffer.putInt(I32(STUN_TID0));
		buffer.putInt(I32(STUN_TID1));
		buffer.putInt(I32(STUN_TID2));
		buffer.putInt(I32(STUN_TID3));

		buffer.putShort(I16(CHANGE_REQUEST));
		buffer.putShort(I16(4));
		buffer.put(I8(0));
		buffer.put(I8(0));
		buffer.put(I8(0));
		buffer.put(sameIP? I8(4): I8(6));
		buffer.flip();
		return buffer;
	}

	public boolean checkResponse(ByteBuffer buffer) {
		if (buffer.getShort() != BINDING_RESPONSE)
			return false;
		buffer.getShort();
		if (buffer.getInt() != STUN_TID0)
			return false;
		if (buffer.getInt() != STUN_TID1)
			return false;
		if (buffer.getInt() != STUN_TID2)
			return false;
		if (buffer.getInt() != STUN_TID3)
			return false;
		return true;
	}

	public InetSocketAddress getMappedAddress(ByteBuffer buffer) {
		InetAddress addr = null;
		byte[] attr = getSTUNAttr(buffer, I16(MAPPED_ADDRESS));
		if (attr != null) {
			int port;
			byte[] ip_part = new byte[4];
			ByteBuffer tb = ByteBuffer.wrap(attr);
			tb.order(ByteOrder.BIG_ENDIAN);
			port = tb.getShort();
			port = (tb.getShort() & 0xFFFF);
			tb.get(ip_part);
			try {
				return InetUtil.getInetSocketAddress(ip_part, port);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return null;
	}

	public InetSocketAddress getChangedAddress(ByteBuffer buffer) {
		InetAddress addr = null;
		byte[] attr = getSTUNAttr(buffer, I16(CHANGED_ADDRESS));
		if (attr != null) {
			int port;
			byte[] ip_part = new byte[4];
			ByteBuffer tb = ByteBuffer.wrap(attr);
			tb.order(ByteOrder.BIG_ENDIAN);
			port = tb.getShort();
			port = (tb.getShort() & 0xFFFF);
			tb.get(ip_part);
			try {
				return InetUtil.getInetSocketAddress(ip_part, port);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private byte[] getSTUNAttr(ByteBuffer buffer, short type) {
		short typ, len;

		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.mark();
		if (checkResponse(buffer)) {
			buffer.position(20);

			while (buffer.hasRemaining()) {
				typ = buffer.getShort();
				len = buffer.getShort();

				if (typ == type) {
					byte[] attr = new byte[len];
					buffer.get(attr);
					buffer.reset();
					return attr;
				}

				buffer.get(new byte[len]);
			}
		}

		buffer.reset();
		return null;
	}

	public static InetSocketAddress getLocal() {
		int port = 0;
		InetAddress address = null;
		InetSocketAddress localAdress = null;

		try {
			DatagramSocket datagram = new DatagramSocket();
			datagram.connect(InetUtil.getInetSocketAddress("8.8.8.8", 53));
			address = datagram.getLocalAddress();
			//port = datagram.socket().getLocalPort();
			datagram.close();
			localAdress = new InetSocketAddress(address, port);
			System.out.println("local address: " + localAdress);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return localAdress;
	}

	public InetSocketAddress getMapping() {
		return soaddr;
	}

	public void close() {
		try {
			mIWait.clear();
			slep.wakeup();
			redo.clean();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private short I16(int value) {
		return (short)value;
	}

	private int I32(int value) {
		return value;
	}

	private byte I8(int value) {
		return (byte)value;
	}
}

class TestSTUNClient {
	private STUNClient client;

	public TestSTUNClient() {
		try {
			DatagramChannel datagram = DatagramChannel.open();
			client = new STUNClient(datagram, "stun.l.google.com", 19302);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private SlotWait w = new SlotWait() {
		public void invoke() {
			System.out.println("Mapping: " + client.getMapping());
		}
	};

	public void start() {
		ByteBuffer b = client.requestMapping();
		STUNClient.getLocal();
		client.send(b, w);
	}
}

