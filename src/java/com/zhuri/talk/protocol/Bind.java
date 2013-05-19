package com.zhuri.talk.protocol;

public class Bind extends Packet {
	final static String bind = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>";

	final static String resource = "<resource>ROBOT-V1</resource>";
	final static String bind_with_resource = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>";

	public String toString() {
		return bind_with_resource + resource + "</bind>";
	}
}
