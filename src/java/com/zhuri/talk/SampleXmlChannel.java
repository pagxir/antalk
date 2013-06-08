package com.zhuri.talk;

import java.nio.*;
import com.zhuri.net.*;
import com.zhuri.slot.*;
import java.io.IOException;
import com.zhuri.util.DEBUG;
import com.zhuri.talk.protocol.Packet;
import com.zhuri.talk.protocol.Stream;
import com.zhuri.talk.protocol.SampleXmlParser;

public class SampleXmlChannel {
	public final static int XML_NEXT = 0x01;
	public final static int XML_PEEK = 0x02;

	private long lastRead = 0;
	private boolean mOpenSent = false;
	private boolean mXmlOpened = false;
	private IWaitableChannel mChannel = null;
	private final ByteBuffer  mXmlBuffer = ByteBuffer.allocate(65536);
	private final SampleXmlParser mXmlParser = new SampleXmlParser();

	public SampleXmlChannel(IWaitableChannel channel) {
		mChannel = channel;
		mXmlBuffer.flip();
	}

	private final SlotWait mIWait = new SlotWait() {
		public void invoke() {

			try {
				mXmlBuffer.compact();
				DEBUG.Assert(mXmlBuffer.hasRemaining());
				lastRead = mChannel.read(mXmlBuffer);
				mXmlBuffer.flip();
			} catch (IOException e) {
				e.printStackTrace();
				mISlot.wakeup();
				mOSlot.wakeup();
				lastRead = -1;
				return;
			}

			mXmlBuffer.mark();
			if (mXmlOpened == false) {
				try {
					mXmlOpened = mXmlParser.open(mXmlBuffer);
				} catch (Exception e) {
					//e.printStackTrace();
				}

				if (mXmlOpened) mXmlBuffer.mark();
			}

			if (mXmlOpened == false) {
				mXmlBuffer.reset();
				tryNextRead();
				return;
			}

			try {
				if (mXmlParser.skipTagContent(mXmlBuffer)) {
					mISlot.wakeup();
				} else {
					tryNextRead();
				}
			} catch (Exception e) {
				//e.printStackTrace();
				tryNextRead();
			}

			mXmlBuffer.reset();
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

	public boolean disconnected() {
		/* connection is disconnected if lastRead == -1 */
		return (lastRead == -1);
	}

	private void tryNextRead() {
		if (lastRead != -1) {
			mChannel.waitI(mIWait);
			return;
		}
		mISlot.wakeup();
		mOSlot.wakeup();
		return;
	}

	public boolean open(String domain) {
		long count;
		String tag = Stream.begin(domain);

		try {
			count = mChannel.write(ByteBuffer.wrap(tag.getBytes()));
		} catch (IOException e) {
			e.printStackTrace();
			mISlot.wakeup();
			mOSlot.wakeup();
			lastRead = -1;
			return false;
		}

		mOpenSent = true;
		return true;
	}

	public void close() {
		long count;

		if (mOpenSent) {
			String tag = Stream.end();
			try {
				count = mChannel.write(ByteBuffer.wrap(tag.getBytes()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		mOSlot.wakeup();
		mISlot.wakeup();
		return;
	}

	private ByteBuffer pendingBuffer = null;
	private SlotWait mOutWait = new SlotWait() {
		public void invoke() {
			if (pendingBuffer == null) {
				mOSlot.wakeup();
				return;
			}

			try {
				mChannel.write(pendingBuffer);
			} catch (IOException e) {
				pendingBuffer.position(pendingBuffer.limit());
			}

			if (pendingBuffer.hasRemaining()) {
				mChannel.waitO(mOutWait);
				return;
			}

			pendingBuffer = null;
			mOSlot.wakeup();
			return;
		}
	};

	public boolean put(Packet packet) {
		long count;
		String content = packet.toString();
		ByteBuffer buffer = ByteBuffer.wrap(content.getBytes());

		DEBUG.Print("OUTGOING", content);
		if (pendingBuffer != null) {
			DEBUG.Print("TRACE", "packet is pending");
			return false;
		}

		try {
			count = mChannel.write(buffer);
		} catch (IOException e) {
			e.printStackTrace();
			lastRead = -1;
			return false;
		}

		if (buffer.hasRemaining()) {
			mChannel.waitO(mOutWait);
			pendingBuffer = buffer;
			return false;
		}

		return true;
	}

	private Packet mPacket = Packet.EMPTY_PACKET;
	public Packet get() {
		if (mXmlOpened && mPacket == Packet.EMPTY_PACKET) {
			/* DEBUG.Print("SampleXmlChannel::get"); */
			update();
		}
		return mPacket;
	}

	private boolean update() {
		int start;
		int finish;

		mXmlBuffer.mark();
		try {
			start = mXmlBuffer.position();
			if (mXmlParser.skipTagContent(mXmlBuffer)) {
				finish = mXmlBuffer.position();
				mPacket = Packet.parse(mXmlBuffer.array(), start, finish - start);
				return true;
			}
		} catch (Exception e) {
			/* e.printStackTrace(); */
			mXmlBuffer.reset();
			return false;
		}

		return false;
	}

	public void mark(int flags) {
		if (flags == XML_NEXT) {
			if (mXmlOpened == false || !update()) {
				mPacket = Packet.EMPTY_PACKET;
				tryNextRead();
			}
		}
		return;
	}
}

