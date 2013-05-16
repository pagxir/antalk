package com.zhuri.talk.protocol;

public class Keepalive extends Packet {
	final static String live = "<keepalive xmlns='pagxir@gmail.com'/>";

	public String toString() {
		return live;
	}
}
