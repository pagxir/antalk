package com.zhuri.talk.protocol;

public class Body extends Packet {
	private String mText;

	public Body(String text) {
		mText = text;
	}

	public String toString() {
		String r1 = mText.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
		return "<body>" + r1 + "</body>";
	}
}

