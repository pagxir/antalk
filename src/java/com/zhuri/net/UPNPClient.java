package com.zhuri.net;

import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.io.IOException;

import com.zhuri.net.*;
import com.zhuri.util.*;
import com.zhuri.slot.*;

public class UPNPClient {

	private SlotSlot slep = null;
	private SlotChannel slchannel = null;
	private DatagramChannel channel = null;
	private InetSocketAddress stunServer = null;
	private final String UNPN_SEARCH_ADDRESS = "239.255.255.250";

	public UPNPClient(DatagramChannel channel, String domain, int port) {

		try {
			this.channel = channel;
			this.channel.socket().bind(null);
			this.channel.socket().setBroadcast(true);
			this.slchannel = SlotChannel.open(channel);
			this.stunServer = InetUtil.getInetSocketAddress(domain == null? UNPN_SEARCH_ADDRESS: domain, port);
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

	private String result = "";
	public String getSearchResult() {
		return result;
	}

	public boolean input(ByteBuffer packet) {
		hold = null;
		redo.cancel();
		slep.wakeup();
		result = new String(packet.array(), 0, packet.limit());
		return true;
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

	private final String UPNP_SEARCH_HEADER = 
		"M-SEARCH * HTTP/1.1\r\n" + 
		"HOST: 239.255.255.250:1900\r\n" +
		"MAN: \"ssdp:discover\"\r\n" +
		"MX: 5\r\n" +
		"ST: %s\r\n" +
		"\r\n";

	private ByteBuffer builtUPnPSearch(String devices_type) {
		String upnp_search = UPNP_SEARCH_HEADER.replace("%s", devices_type);
		return ByteBuffer.wrap(upnp_search.getBytes());
	}

	/*
	   "urn:schemas-upnp-org:service:WANPPPConnection:1",
	   "urn:schemas-upnp-org:service:WANIPConnection:1",
	   "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
	   "UPnP:rootdevice",
	 */
	public void search(SlotWait wait) {
		ByteBuffer b2 = builtUPnPSearch("urn:schemas-upnp-org:service:WANIPConnection:1");
		ByteBuffer b1 = builtUPnPSearch("urn:schemas-upnp-org:service:WANPPPConnection:1");
		try {
			channel.send(b1, stunServer);
		} catch (Exception e) {
			e.printStackTrace();
		}
		send(b2, wait);
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
}

