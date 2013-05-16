package com.zhuri.talk.protocol;

public class Bind extends Packet {
	final static String bind = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>";

	public String toString() {
		return bind;
	}
}
