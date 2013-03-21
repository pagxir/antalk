package com.zhuri.slot;

import java.nio.channels.*;

public class SlotChannel {
	static Selector selector = null;

	static void invoke(long timeout) throws Exception {
		int count;
		InnerChannel channel;

		count = timeout > 0? selector.select(timeout): selector.selectNow();
		if (count > 0) {
			int flags = 0;

			for (SelectionKey key: selector.selectedKeys()) {
				channel = (InnerChannel)key.attachment();
				channel.wakeup();
			}

			selector.selectedKeys().clear();
		}

	}

	static SlotWait _channel_scan = new SlotWait() {
		public void invoke() {
			try {
				SlotChannel.invoke(0);
			} catch (Exception e) {
				System.out.println("init failure");
				e.printStackTrace();
			}
		}
	};

	public static void init() throws Exception {
		selector = Selector.open();

		_channel_scan.flags &= ~WT_EXTERNAL;
		_channel_scan.flags |= WT_WAITSCAN;
		_channel_scan.schedule();
	}

	public static void fini() throws Exception {
		_channel_scan.clean();
		_channel_scan = null;
		selector.close();
		selector = null;
	}

	static int WT_WAITSCAN = 0x00000008;
	static int WT_EXTERNAL = 0x00000004;
	static int WT_COMPLETE = 0x00000002;
	static int WT_INACTIVE = 0x00000001;

	private InnerChannel mInnerChannel = null;

	public SlotChannel() {
		mInnerChannel = new InnerChannel();
	}

	public boolean wantIn(SlotWait wait) {
		return mInnerChannel.wantIn(wait);
	}

	public boolean wantOut(SlotWait wait) {
		return mInnerChannel.wantOut(wait);
	}

	public void attach(DatagramChannel channel) {
		mInnerChannel.attach(channel);
	}

	public void attach(SocketChannel channel) {
		mInnerChannel.attach(channel);
	}

	public void detach() {
		mInnerChannel.detach();
	}

	@Override
	public void finalize() {
		mInnerChannel.detach();
	}
}

class InnerChannel {
	int mFlags;
	SelectionKey mSlotKey;

	SlotSlot mSlotIn = new SlotSlot();
	SlotSlot mSlotOut = new SlotSlot();

	InnerChannel() {
		mFlags = SelectionKey.OP_CONNECT |
			SelectionKey.OP_WRITE | SelectionKey.OP_READ;
		mSlotKey = null;
	}

	void attach(DatagramChannel channel) {

		/* channel or selector should not be null. */
		if (channel == null || SlotChannel.selector == null)
			throw new IllegalArgumentException("SlotChannel.attach udp failure");

		try {
			mFlags = SelectionKey.OP_READ;
			channel.configureBlocking(false);
			mSlotKey = channel.register(SlotChannel.selector, mFlags, this);
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("SlotChannel.attach udp failure");
		}
	}

	void attach(SocketChannel channel) {

		/* channel or selector should not be null. */
		if (channel == null || SlotChannel.selector == null)
			throw new IllegalArgumentException("SlotChannel.attach tcp failure");

		try {
			channel.configureBlocking(false);
			mSlotKey = channel.register(SlotChannel.selector, mFlags, this);
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("SlotChannel.attach tcp failure");
		}
	}

	void detach() {
		if (mSlotKey != null)
			mSlotKey.cancel();
		mSlotKey = null;
		mSlotOut = null;
		mSlotIn = null;
	}

	boolean wantIn(SlotWait wait) {
		/* TODO: assert wait not active or is in the slotIn. */

		wait.cancel();
		mSlotIn.record(wait);
		/* if (SelectionKey.OP_READ != (mFlags & SelectionKey.OP_READ)) */ {
			mFlags |= SelectionKey.OP_READ;
			mSlotKey.interestOps(mFlags);
		}

		return false;
	}

	boolean wantOut(SlotWait wait) {
		/* TODO: assert wait not active or is in the slotOut. */

		wait.cancel();
		mSlotOut.record(wait);
		if (SelectionKey.OP_WRITE != (mFlags & SelectionKey.OP_WRITE)) {
			mFlags |= SelectionKey.OP_WRITE;
			mSlotKey.interestOps(mFlags);
		}

		return false;
	}

	void wakeup() {
		int wakeup_flags = 0;
		int write_flags  = SelectionKey.OP_CONNECT| SelectionKey.OP_WRITE;

		int oldflags = mFlags;
		SelectionKey key = mSlotKey;

		if (key.isConnectable() || key.isWritable()) {
			if (mSlotOut.isEmpty())
				mFlags &= ~write_flags;
			wakeup_flags |= 0x1;
		}

		if (key.isReadable()) {
			if (mSlotIn.isEmpty())
				mFlags &= ~SelectionKey.OP_READ;
			wakeup_flags |= 0x2;
		}

		if (oldflags != mFlags) {
			key.interestOps(mFlags);
		}

		if (!mSlotIn.isEmpty() &&
				(wakeup_flags & 0x2) > 0) {
			mSlotIn.wakeup();
		}

		if (!mSlotOut.isEmpty() &&
				(wakeup_flags & 0x1) > 0) {
			mSlotOut.wakeup();
		}
	}
}
