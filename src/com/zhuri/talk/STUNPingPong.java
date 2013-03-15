package com.zhuri.talk;

import org.w3c.dom.Element;
import com.zhuri.slot.SlotWait;
import com.zhuri.talk.ProtoPlugcan;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import com.zhuri.talk.ProtoPlugcan.ProtoPlugin;
import com.zhuri.talk.protocol.Jabber;
import com.zhuri.talk.protocol.FastXmlVisitor;

public class STUNPingPong extends ProtoPlugin {
	private IPhoneBack phoneback;

	public STUNPingPong(IPhoneBack phoneback) {
		super("http://www.google.com/location"); 
		this.phoneback = phoneback;
	}

	public interface IPhoneBack {
		public void invoke(Jabber talk, String sid, String jid);
	}

	final static String former = "<iq type='result'/>";
  	public void input(Jabber talk, Element packet) {
     	int index;
        String strxml = "";
        String part1, part2;
        String id = packet.getAttribute("id");
        String target = packet.getAttribute("to");
        String sender = packet.getAttribute("from");
        FastXmlVisitor visitor = new FastXmlVisitor();
		String sid = visitor.useElement(packet).getElement("query").getAttribute("sid");

        packet = visitor.fastFormat(former);
        packet.setAttribute("id", id);
        packet.setAttribute("to", sender);
		if (response0 != null) {
        	packet.setAttribute("type", "error");
        	strxml = visitor.fastFormat(packet);
			talk.putPacket(strxml);
			return;
		}

		ssid = sid;
		talker = talk;
        packet.setAttribute("type", "result");
        response0 = visitor.fastFormat(packet);
        packet.setAttribute("type", "error");
        response1 = visitor.fastFormat(packet);
        phoneback.invoke(talk, sid, sender);
    	return;
	}

	static private String ssid;
	static private String response0;
	static private String response1;
	static private Jabber talker;
	public static void startPhoneCall() {
		if (response0 == null)
			response0 = "deny all";
		return;
	}

	public static String answerPhoneCall(boolean answer) {
		String result = answer? response0: response1;
		if (response0 != null)
        	talker.putPacket(result);
		android.util.Log.i("jabber", "phone call ssid = " + ssid);
		return ssid;
	}

	public static void finishPhoneCall() {
		response0 = null;
		response1 = null;
		talker = null;
		ssid = null;
		return;
	}

	static int negseq = 0;
	public static Client getClient(Jabber talk, DatagramChannel channel) {
		if (channel == null) {
			try {
				channel = DatagramChannel.open();
				channel.socket().bind(null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return new Client(talk, negseq++, channel);
	}

	public interface Negotiatable {
		void onNegotiated(InetSocketAddress address);
	}

	public static class Client implements Runnable {
		private String sid;
		private SlotWait stun;
		private SlotWait wait;
		private SlotWait waitLocal;
		private SlotWait waitRemote;
		private STUNClient client;
		private Jabber talk;
		public  String notifyMessage;

		public Client(Jabber talk, int seq, DatagramChannel channel) {
			this.talk = talk;
			this.sid  = "" + seq;
			this.stun = new SlotWait(this);
			this.wait = new SlotWait(this);
			this.waitLocal = new SlotWait(this);
			this.waitRemote = new SlotWait(this);
			this.client = new STUNClient(channel, "stun.l.google.com", 19302);
		}

		private String target;
		private Negotiatable negable;
		public void start(String target, Negotiatable negable) {
			notifyMessage = "calling peer";
			this.negable = negable;
			this.target = target;
			this.run();
		}

		public Negotiatable dummy = new Negotiatable() {
			public void onNegotiated(InetSocketAddress address) {
				System.out.println("Negotiatable: " + address);
			}
		};

		public void started(String target, String sid, Negotiatable negotiable) {
			this.sid = sid;
			this.target = target;
			this.initiator = target;
			DatagramPingPong.register(initiator, sid, negotiable);
			notifyMessage = "anser peer";
			android.util.Log.i("jabber", "started call ssid = " + sid + ", initiator = " + initiator);
			wait.schedule();
		}

		private boolean supportSTUN(SlotWait wait) {
			Element result;
			FastXmlVisitor visitor = new FastXmlVisitor();

			if (wait.completed()) {
				if (initiator.equals(talk.getSelfJid())) {
					String type = "result";
					String typeMatch = "";
					result = (Element)wait.result;
					typeMatch = visitor.useElement(result).getAttribute("type");
					if (typeMatch.equals("error"))
						notifyMessage = "peer reject calling";
					return typeMatch.equals(type);
				}
				return true;
			}

			return false;
		}

		private String initiator = "";
		public void run() {
			if (!wait.started()) {
				String qstr = "<query sid='#ID#' xmlns='http://www.google.com/location'/>";
				initiator = talk.getSelfJid();
				qstr = qstr.replaceFirst("#ID#", sid);
        		talk.putQuery(Jabber.SET, target, qstr, wait);
				DatagramPingPong.register(initiator, sid, negable);
			}

			if (supportSTUN(wait) && !waitLocal.started()) {
				notifyMessage = "local channel neg";
				String qstr = buildQuery(client.getLocal(), "local");
        		talk.putQuery(Jabber.SET, target, qstr, waitLocal);
			}

			if (supportSTUN(wait) && !stun.started()) {
				stun.clear();
				notifyMessage = "find remote address";
            	client.send(client.requestMapping(), stun);
			}

			if (stun.completed() && !waitRemote.started()) {
				notifyMessage = "remote channel negi";
				InetSocketAddress socketAddress = client.getMapping();
				if (socketAddress != null) {
					String qstr = buildQuery(socketAddress, "remote");
        			talk.putQuery(Jabber.SET, target, qstr, waitRemote);
				}
			}
		}

		public void SendThirdAddress(InetSocketAddress address) {
			String qstr = buildQuery(address, "selected");
        	talk.putQuery(Jabber.SET, target, qstr, null);
			return;
		}

		String queryFormer =
			"<query xmlns='http://www.google.com/address'><item/></query>";

		private String buildQuery(InetSocketAddress address, String type) {
			FastXmlVisitor fxv = new FastXmlVisitor();
			Element element = FastXmlVisitor.fastFormat(queryFormer);
			fxv.useElement(element);
			fxv.setAttribute("sid", sid);
			fxv.setAttribute("initiator", initiator);
			fxv.getElement("item");

			fxv.setAttribute("type", type);
			fxv.setAttribute("port", "" + address.getPort());
			fxv.setAttribute("address", address.getAddress().getHostAddress());
			return fxv.fastFormat(element);
		}

		public void stun_event(ByteBuffer buffer) {
			client.input(buffer);
			return;
		}

		public void close() {
			waitRemote.clean();
			waitLocal.clean();
			client.close();
			stun.clean();
			wait.clean();
		}
	}
}

