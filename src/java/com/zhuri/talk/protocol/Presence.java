package com.zhuri.talk.protocol;

public class Presence extends Packet {
	private String mStatus = null;

	public void setStatus(String status) {
		mStatus = status;
		return;
	}

	public String toString() {
		StringBuilder builder = new StringBuilder();

		if (mStatus != null) {
			builder.append("<presence>");
			builder.append("<status>");
			builder.append(FastXmlVisitor.textFormat(mStatus));
			builder.append("</status>");
			builder.append("</presence>");
			return builder.toString();
		}

		return "<presence/>";
	}
}
