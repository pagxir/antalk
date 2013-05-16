package com.zhuri.talk.protocol;

public class IQPacket extends Packet {
	private int mId = 0;
	private String mTo = null;
	private String mFrom = null;
	private String mType = null;
	private Packet mQuery = null;

	public void setId(int id) {
		mId = id;
		return;
	}

	public void setTo(String to) {
		mTo = to;
		return;
	}

	public void setFrom(String from) {
		mFrom = from;
		return;
	}

	public void setType(String type) {
		mType = type;
		return;
	}

	public IQPacket(Packet query) {
		mQuery = query;
	}

	public String toString() {
		String builder = "";

		builder += "<iq ";
		builder += "id='" + mId + "' ";
		if (mTo != null)
			builder += "to='" + mTo + "' ";
		if (mFrom != null)
			builder += "from='" + mFrom + "' ";
		builder += "type='" + (mType == null? "get": mType) + "' ";
		builder += ">";
		builder += mQuery.toString();
		builder += "</iq>";

		return builder;
	}
}

