package com.zhuri.slot;

import java.nio.channels.*;

public class SlotChannel {
	static Selector selector = null;

	static void invoke(long timeout) throws Exception {
		int count;
		SlotChannel channel;

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

	@Override
	public void finalize() {
		mInnerChannel.detach();
	}

	static class InnerChannel {
		int mFlags;
		SelectionKey mSlotKey;

		SlotSlot mSlotIn = new SlotSlot();
		SlotSlot mSlotOut = new SlotSlot();

		public InnerChannel() {
			mFlags = SelectionKey.OP_CONNECT |
				SelectionKey.OP_WRITE | SelectionKey.OP_READ;
			mSlotKey = null;
		}

		public void attach(DatagramChannel channel) throws Exception {

			/* channel or selector should not be null. */
			if (channel == null || selector == null)
				throw new Exception("SlotChannel.attach udp failure");

			mFlags = SelectionKey.OP_READ;
			channel.configureBlocking(false);
			mSlotKey = channel.register(mSelector, mFlags, this);
		}

		public void attach(SocketChannel channel) throws Exception {

			/* channel or selector should not be null. */
			if (channel == null || selector == null)
				throw new Exception("SlotChannel.attach tcp failure");

			channel.configureBlocking(false);
			mSlotKey = channel.register(selector, mFlags, this);
		}

		public void detach() {
			if (mSlotKey != null)
				mSlotKey.cancel();
			mSlotKey = null;
			mSlotOut = null;
			mSlotIn = null;
		}

		public boolean wantIn(SlotWait wait) {
			/* TODO: assert wait not active or is in the slotIn. */

			wait.cancel();
			slotIn.record(wait);
			/* if (SelectionKey.OP_READ != (mFlags & SelectionKey.OP_READ)) */ {
				mFlags |= SelectionKey.OP_READ;
				mSlotKey.interestOps(mFlags);
			}

			return false;
		}

		public boolean wantOut(SlotWait wait) {
			/* TODO: assert wait not active or is in the slotOut. */

			wait.cancel();
			slotOut.record(wait);
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
				if (slotOut.isEmpty())
					mFlags &= ~write_flags;
				wakeup_flags |= 0x1;
			}

			if (key.isReadable()) {
				if (slotIn.isEmpty())
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
}

