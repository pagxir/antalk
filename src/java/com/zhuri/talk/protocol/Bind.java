package com.zhuri.talk.protocol;

public class Bind extends Packet {
	final static String bind_with_resource = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>";
	final static String bind_without_resource = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>";

	public final static String tag = "bind";
	public final static String uri = "urn:ietf:params:xml:ns:xmpp-bind";

	public Bind() {
		resource = null;
	}

	public Bind(String name) {
		resource = "<resource>" + name + "</resource>";
	}

	public static boolean isTypeof(Packet packet) {
		return (packet.matchTag(tag) && packet.matchURI(uri));
	}

	public static String getJid(Packet packet) {
		FastXmlVisitor visitor = new FastXmlVisitor(packet.mElement);
		return visitor.getElement("jid").getValue();
	}

	private String resource = null;
	public String toString() {
		if (resource != null)
			return bind_with_resource + resource + "</bind>";
		return bind_without_resource;
	}
}

