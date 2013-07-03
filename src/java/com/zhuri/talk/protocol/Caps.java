package com.zhuri.talk.protocol;

import org.w3c.dom.*;
import com.zhuri.talk.protocol.FastXmlVisitor;

public class Caps extends Packet {
	/* <c xmlns="http://jabber.org/protocol/caps" ext="robot" node="http://pagxir.cublog.cn/client/caps" ver="2.6"/>; */
	public final static String tag = "c";
	public final static String uri = "http://jabber.org/protocol/caps";
	public final static String node_uri = "http://pagxir.cublog.cn/client/caps";

	private Element mCaps = FastXmlVisitor.fastFormat("<c xmlns='http://jabber.org/protocol/caps' />");

	public Caps(String ext) {
		FastXmlVisitor fxv = new FastXmlVisitor(mCaps);
		fxv.setAttribute("ext", ext);
		fxv.setAttribute("ver", "2.6");
		fxv.setAttribute("node", node_uri);
	}

	public static boolean isTypeof(Packet packet) {
		return (packet.matchTag(tag) && packet.matchURI(uri));
	}

	public String toString() {
		return FastXmlVisitor.fastFormat(mCaps);
	}
}

