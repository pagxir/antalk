package com.zhuri.talk.protocol;

public class Body extends Packet {
	private String mText;

	public Body(String text) {
		mText = text;
	}

	public String toString() {
		String r1 = mText.replaceAll("&", "&amp;").
			replaceAll("<", "&lt;").replaceAll(">", "&gt;").
			replaceAll("\"", "&quot;").replaceAll("\'", "&apos;");
		return "<body>" + r1 + "</body>";
	}
}

