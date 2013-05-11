package com.zhuri.talk.protocol;

public class Session extends Packet {
	final static String session = "<session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>";

	public String toString() {
		return session;
	}
}

