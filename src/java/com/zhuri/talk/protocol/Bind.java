package com.zhuri.talk.protocol;

public class Bind extends Packet {
	final static String bind_with_resource = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>";
	final static String bind_without_resource = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>";

	public Bind() {
		resource = null;
	}

	public Bind(String name) {
		resource = "<resource>" + name + "</resource>";
	}

	private String resource = null;
	public String toString() {
		if (resource != null)
			return bind_with_resource + resource + "</bind>";
		return bind_without_resource;
	}
}

