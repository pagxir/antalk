package com.zhuri.talk;

import java.nio.*;
import com.zhuri.net.*;
import com.zhuri.slot.*;
import java.io.IOException;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Stream;

public class SampleXmlChannel {
	public final static int XML_NEXT = 0x01;
	public final static int XML_PEEK = 0x02;

	private long lastRead = -1;
	private IWaitableChannel mChannel = null;
	private final int[] arrcalc = new int[2];
	private final ByteBuffer  mXmlBuffer = ByteBuffer.allocate(65536);

	public SampleXmlChannel(IWaitableChannel channel) {
		mChannel = channel;
	}

	private final SlotWait mIWait = new SlotWait() {
		public void invoke() {
			System.out.println("mIWait");

			int tagidx = 0;
			byte dotcur = '.';
			byte dotprev = '$';

			try {
				lastRead = mChannel.read(mXmlBuffer);
				mXmlBuffer.flip();
			} catch (IOException e) {
				e.printStackTrace();
				lastRead = -1;
				return;
			}

			int[] savArrCalc = new int[2];
			savArrCalc[0] = arrcalc[0];
			savArrCalc[1] = arrcalc[1];

			while (mXmlBuffer.hasRemaining()) {
				dotcur = mXmlBuffer.get();
				if (dotcur == '/' &&
						dotprev == '<') {
					tagidx = 1;
				}

				if (dotcur == '>' &&
						dotprev == '/') {
					arrcalc[0]++;
					tagidx = 1;
				}

				if (dotcur == '>' &&
						dotprev == '?') {
					dotprev = dotcur;
					tagidx = 0;
					continue;
				}

				if (dotcur == '>') {
					arrcalc[tagidx]++;
					tagidx = 0;
				}

				if (dotcur == '>' && arrcalc[0] == arrcalc[1] + 1) {
					if (arrcalc[1] == 0) {
						savArrCalc[0] = arrcalc[0];
						savArrCalc[1] = arrcalc[1];
						dotprev = dotcur;
						mXmlBuffer.compact();
						mXmlBuffer.flip();
						continue;
					}

					//parsePacket(buffer.array(), 0, buffer.position());
					mXmlBuffer.compact();
					mISlot.wakeup();
					return;
				}

				dotprev = dotcur;
			}

			arrcalc[0] = savArrCalc[0];
			arrcalc[1] = savArrCalc[1];
			return;
		}
	};

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
		long count;
		String tag = Stream.end();

		try {
			count = mChannel.write(ByteBuffer.wrap(tag.getBytes()));
		} catch (IOException e) {
			e.printStackTrace();
		}

		return;
	}

	public boolean put(Packet packet) {
		long count;
		String content = packet.toString();

		try {
			count = mChannel.write(ByteBuffer.wrap(content.getBytes()));
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public void mark(int flags) {
		switch (flags) {
			case XML_NEXT:
				mChannel.waitI(mIWait);
				break;

			case XML_PEEK:
			default:
				break;
		}
		return;
	}

	public Packet get() {
		return null;
	}
}

