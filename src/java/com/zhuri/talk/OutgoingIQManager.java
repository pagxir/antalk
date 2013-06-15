package com.zhuri.talk;

import com.zhuri.talk.protocol.IQ;
import com.zhuri.talk.protocol.Packet;

public class OutgoingIQManager {
	private int mGenerator = 0x1982;

	public IQ createQuery(Packet query) {
		IQ iq = IQ.createQuery(query);
		iq.setId(mGenerator++);
		return iq;
	}
}
