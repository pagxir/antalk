package com.zhuri.talk;

import java.io.*;
import com.zhuri.slot.*;
import org.w3c.dom.*;
import com.zhuri.talk.STUNPingPong;

import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import com.zhuri.util.InetUtil;
import com.zhuri.talk.protocol.Jabber;
import com.zhuri.talk.protocol.FastXmlVisitor;

public class UserConsole implements Runnable, PacketCallback {
	static Jabber talk;

	SlotWait wait = new SlotWait() {
		public void invoke() {
			printRoster((Element)result());
		}
	};

	class PhoneBack implements STUNPingPong.IPhoneBack {
		public void invoke(Jabber _client, String sid, String jid) {
			PhoneLink link = new PhoneLink();
			link.create(_client).started(jid, sid, link);
			STUNPingPong.answerPhoneCall(true);
			STUNPingPong.finishPhoneCall();
		}
	}

	private STUNPingPong newSTUNPingPong() {
		STUNPingPong.IPhoneBack phoneback;
		phoneback = new PhoneBack();
		return new STUNPingPong(phoneback);
	}

	String line = "";
	String user = "pagxir@gmail.com";
	String server = "talk2.l.google.com";

	private boolean done(String cmd) throws Exception {
		if (cmd.startsWith("init")) {
			STUNPingPong pong = newSTUNPingPong();
			ProtoPlugcan.getInstance().registerPlugin(pong);
		}

		if (cmd.startsWith("login")) {
			talk.setStateListener(UserConsole.this);
			talk.setMessageListener(UserConsole.this);
			talk.setPresenceListener(UserConsole.this);

			   talk.login("1447754732@uc.sina.com.cn",
			   "GAkJoEtq75x9", "xmpp.uc.sina.com.cn");
			/*
			   talk.login("pagx@uc.sina.com.cn",
			   "3JwcBJmYdlwx", "xmpp.uc.sina.com.cn");
			   talk.login("level@jaim.at", "hello", null);
			   talk.login("level@jabbernet.dk", "wB0BVqHI", null);
			   talk.login("level@jabbernet.dk", "wB0BVqHI", null);
			   talk.login("pagxir@jabber.org", "b4jrzV6TBaNz", null);
			 */
			//talk.login("level@jaim.at/linux", "hello", null);
			//talk.login("level@jabbernet.dk/level", "wB0BVqHI", null);
			 talk.login("1447754732@uc.sina.com.cn/ucyy",
			   "GAkJoEtq75x9", "xmpp.uc.sina.com.cn");
			return true;
		}

		if (cmd.startsWith("ring ")) {
			PhoneLink link = new PhoneLink();
			String jid = cmd.substring(5).trim();
			link.create(talk).start(jid, link);
			return true;
		}

		return false;
	}

	SlotWait command = new SlotWait() {
		public void invoke() {
			String sent = line;
			line = null;

			try {
				done(sent);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	};

	void printRoster(Element result) {
		Node node = result.getFirstChild().getFirstChild();
		while (node != null) {
			String line;
			Element e = (Element)node;
			node = node.getNextSibling();

			line = "item: " + e.getAttribute("name") + 
				"<" + e.getAttribute("jid") + ">";
			System.out.println(line);
		}
	}

	public void run() {
		try {
			talk.putQuery(Jabber.GET, null, "<query xmlns='jabber:iq:roster'/>", wait);
			System.out.println("login finish!\n");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void receive(Element packet) {
		String from = packet.getAttribute("from");
		String type = packet.getAttribute("type");
		FastXmlVisitor visitor = new FastXmlVisitor();

		if (packet.getTagName().equals("message")) {
			String body = visitor.useElement(packet)
				.getElement("body").getValue();
			/* e.getNamespaceURI();  */
			from = from.replaceAll("/.*", "");
			System.out.println("from [" + from + "] message:");
			System.out.println(body);
		} else if (packet.getTagName().equals("presence")) {
			if (type == null || type.equals(""))
				System.out.println(" online: " + from);
			else if (type.equals("unavailable"))
				System.out.println("offline: " + from);
			else
				System.out.println("user: " + from + " status [" + type + "]");
		}
	}

	public void sendCommand(String text) {
		this.line = text;
		//this.command.ipcSchedule();
		return;
	}

	public static void main(String args[]) throws Exception {
		String line;
		UserConsole client;
		BufferedReader reader;

		talk = new Jabber();
		client = new UserConsole();
		reader = new BufferedReader(new InputStreamReader(System.in));

		client.sendCommand("init");
		line = reader.readLine();
		while (line != null && !line.equals("quit")) {
			client.sendCommand(line);
			line = reader.readLine();
		}

		talk.close();
		return;
	}
}


class PhoneLink implements STUNPingPong.Negotiatable {
	private SlotChannel slot;
	private SlotSlot stuned;
	private DatagramChannel channel;
	private STUNPingPong.Client client;
	private SocketAddress player = null;

	private SlotWait readin = new SlotWait() {
		SocketAddress isa;

		public void invoke() {
			ByteBuffer buffer = ByteBuffer.allocate(2048);
			slot.wantIn(readin);

			try {
				isa = channel.receive(buffer);
			} catch (Exception e) {
				return;
			}

			int pos = buffer.position();
			if (pos > 20 && pos < 48) {
				buffer.flip();
				client.stun_event(buffer);
				return;
			}

			if (pos > 48) {
				buffer.flip();
				try {
					channel.send(buffer, isa);
				} catch (Exception e) {
					e.printStackTrace();
				}
				return;
			}

			if (pos == 4 && buffer.get(3) == 'G' &&
					buffer.get(2) == 'N' && buffer.get(0) == 'P') {
				byte[] pong = "PONG".getBytes();

				if (buffer.get(1) == 'I') {
					try {
						channel.send(ByteBuffer.wrap(pong), isa);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				System.out.println("receive " + isa + (buffer.get(1) == 'I'? "PING": "PONG"));
				stuned.wakeup();
			}
		}
	};

	public STUNPingPong.Client create(Jabber _client) {
		stuned = new SlotSlot();
		slot = new SlotChannel();

		try {
			channel = DatagramChannel.open();
			channel.socket().bind(null);
			player =  InetUtil.getInetSocketAddress("127.0.0.1", 5566);
			channel.socket().setReceiveBufferSize(8192);
			slot.attach(channel);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		client = STUNPingPong.getClient(_client, channel);
		slot.wantIn(readin);
		return client;
	}

	public void onNegotiated(InetSocketAddress address) {
		byte[] ping = "PING".getBytes();
		try	{
			channel.send(ByteBuffer.wrap(ping), address);
		} catch (Exception e) {
			e.printStackTrace();
		}

		Negotiator redo = new Negotiator(address);
		stuned.record(redo);
		redo.delay(1000);
	}

	public void destroy() {
		try { channel.close(); } catch (Exception e) {};
		client.close();
		readin.cancel();
		slot.detach();
		return;
	}


	class Negotiator extends SlotWait {
		private int time = 0;
		private SocketAddress peer;

		SlotWait redo = new SlotWait() {
			public void invoke() {
				byte[] ping = "PING".getBytes();
				try {
					channel.send(ByteBuffer.wrap(ping), peer);
				} catch (Exception e) {
					e.printStackTrace();
				}

				if (time++ < 10) {
					delay(2000);
					return;
				}
			}
		};

		public Negotiator(SocketAddress address) {
			peer = address;
		}

		public void invoke() {
			redo.clean();
			return;
		}

		public void delay(int timo) {
			SlotTimer.reset(redo, timo);
			return;
		}
	}

}

