package com.zhuri.talk;

import java.nio.*;
import com.zhuri.net.*;
import com.zhuri.slot.*;
import java.io.IOException;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Stream;
import com.zhuri.talk.protocol.SampleXmlParser;

public class SampleXmlChannel {
	public final static int XML_NEXT = 0x01;
	public final static int XML_PEEK = 0x02;

	private IWaitableChannel mChannel = null;
	private final SampleXmlParser  mXmlParser = new SampleXmlParser();

	public SampleXmlChannel(IWaitableChannel channel) {
		mChannel = channel;
	}

	private final SlotSlot mISlot = new SlotSlot();
	public void waitI(SlotWait wait) {
		mISlot.record(wait);
		return;
	}

	private final SlotSlot mOSlot = new SlotSlot();
	public void waitO(SlotWait wait) {
		mOSlot.record(wait);
		return;
	}

	public boolean open(String domain) {
		long count;
		String tag = Stream.begin(domain);

		try {
			count = mChannel.write(ByteBuffer.wrap(tag.getBytes()));
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public void close() {
		return;
	}

	public boolean put(Packet packet) {
		return false;
	}

	public void mark(int flags) {
		return;
	}

	public Packet get() {
		return null;
	}
}

