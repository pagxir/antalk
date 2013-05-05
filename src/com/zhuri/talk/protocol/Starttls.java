package com.zhuri.talk.protocol;

public class Starttls extends Packet {
	final static String starttls = "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";

	@Override
	public String toString() {
		return starttls;
	}
}

