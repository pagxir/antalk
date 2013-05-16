package com.zhuri.talk;

import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.IQPacket;

public class OutgoingIQManager {
	private int mGenerator = 0x1982;

	public IQPacket createPacket(Packet query) {
		IQPacket iq = new IQPacket(query);
		iq.setId(mGenerator++);
		return iq;
	}
}
