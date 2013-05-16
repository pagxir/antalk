package com.zhuri.talk.protocol;

public class Body extends Packet {
	private String mText;

	public Body(String text) {
		mText = text;
	}

	public String toString() {
		return "<body>" + mText + "</body>";
	}
}

