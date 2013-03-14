package wave.talk;

import org.w3c.dom.Element;
import wave.slot.SlotWait;
import wave.talk.ProtoPlugcan;
import wave.talk.Jabber.*;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import wave.talk.protocol.FastXmlVisitor;
import wave.talk.ProtoPlugcan.ProtoPlugin;
import wave.talk.STUNPingPong.Negotiatable;
import wave.util.InetUtil;

public class DatagramPingPong extends ProtoPlugin {

    public DatagramPingPong() {
        super("http://www.google.com/address");
    }

	final static String former = "<iq type='result'/>";
  	public void input(Jabber talk, Element packet) {
     	int index;
		Element out;
        String strxml = "";
        String part1, part2;
        String id = packet.getAttribute("id");
        String target = packet.getAttribute("to");
        String sender = packet.getAttribute("from");
        FastXmlVisitor visitor = new FastXmlVisitor();

        out = visitor.fastFormat(former);
        out.setAttribute("id", id);
        out.setAttribute("to", sender);
        out.setAttribute("type", "error");

		visitor.useElement(packet)
			.getElement("query")
			.getElement("item");
		String port = visitor.getAttribute("port");
		String address = visitor.getAttribute("address");
		InetSocketAddress sa = null;
		try {
			sa = InetUtil.getInetSocketAddress(address, Integer.parseInt(port));
		} catch (Exception e) {
			e.printStackTrace();
		}

		visitor.useElement(packet)
			.getElement("query");

		String sid = visitor.getAttribute("sid");
		String initiator = visitor.getAttribute("initiator");

		DatagramNegotiatable item = header;
		while (item != null) {
			if (sid.equals(item.sid) &&
				initiator.equals(item.initiator)) {
        		out.setAttribute("type", "result");
				item.negotiable.onNegotiated(sa);
				break;
			}

			item = item.next;
		}

        strxml = visitor.fastFormat(out);
        talk.putPacket(strxml);
    	return;
	}

	private static class DatagramNegotiatable {
		public String sid;
		public String initiator;
		public Negotiatable negotiable;
		DatagramNegotiatable next;

		public DatagramNegotiatable(String initiator, String sid, Negotiatable negotiable) {
			this.sid = sid;
			this.initiator = initiator;
			this.negotiable = negotiable;
		}
	}
	
	private static DatagramNegotiatable header = null;
	public static Object register(String initiator, String sid, Negotiatable negable) {
		DatagramNegotiatable item = new DatagramNegotiatable(initiator, sid, negable);
		item.next = header;
		header = item;
		return item;
	}
}

