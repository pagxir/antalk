package com.zhuri.slot;

import java.nio.channels.*;

public class SlotChannel {
	int flags;
	SelectionKey slotKey;

	SlotSlot slotIn = new SlotSlot();
	SlotSlot slotOut = new SlotSlot();

	public SlotChannel() {
		flags = SelectionKey.OP_CONNECT |
			 SelectionKey.OP_WRITE| SelectionKey.OP_READ;
	}

	public void attach(DatagramChannel channel) 
	throws Exception {
		channel.configureBlocking(false);
		flags = SelectionKey.OP_READ;
		if (channel == null)
			System.out.println("channel");
		if (selector == null)
			System.out.println("selector");
		slotKey = channel.register(selector, flags, this);
	}

	public void attach(SocketChannel channel) throws Exception {
		channel.configureBlocking(false);
		assert(channel != null);
		assert(selector != null);
		slotKey = channel.register(selector, flags, this);
	}

	public void detach() {
		if (slotKey != null)
			slotKey.cancel();
		slotKey = null;
	}

	public boolean wantIn(SlotWait wait) {
		/* TODO: assert wait not active or is in the slotIn. */
		wait.cancel();
		slotIn.record(wait);
		/* if (SelectionKey.OP_READ != (flags & SelectionKey.OP_READ)) */ {
			flags |= SelectionKey.OP_READ;
			slotKey.interestOps(flags);
		}
		return false;
	}

	public boolean wantOut(SlotWait wait) {
		/* TODO: assert wait not active or is in the slotOut. */
		wait.cancel();
		slotOut.record(wait);
		if (SelectionKey.OP_WRITE != (flags & SelectionKey.OP_WRITE)) {
			flags |= SelectionKey.OP_WRITE;
			slotKey.interestOps(flags);
		}
		return false;
	}

	void doWakeup() {
		int wakeup_flags = 0;
		int write_flags = SelectionKey.OP_CONNECT| SelectionKey.OP_WRITE;

		SelectionKey key = slotKey;
		if (key.isConnectable() || key.isWritable()) {
			if (slotOut.isEmpty()) {
				flags &= ~write_flags;
				key.interestOps(flags);
			}
			wakeup_flags |= 0x1;
		}

		if (key.isReadable()) {
			if (slotIn.isEmpty()) {
				flags &= ~SelectionKey.OP_READ;
				key.interestOps(flags);
			}
			wakeup_flags |= 0x2;
		}

		if (!slotIn.isEmpty() &&
			(wakeup_flags & 0x2) > 0) {
			slotIn.wakeup();
		}

		if (!slotOut.isEmpty() &&
			(wakeup_flags & 0x1) > 0) {
			slotOut.wakeup();
		}
	}

	static Selector selector = null;
	static void ipcWakeup() {
		if (selector != null)
			selector.wakeup();
		return;
	}

	static void invoke(long timeout) throws Exception {
		int count;
		SlotChannel channel;

		count = timeout > 0? selector.select(timeout): selector.selectNow();
		if (count > 0) {
			int flags = 0;
			for (SelectionKey key: selector.selectedKeys()) {
				channel = (SlotChannel)key.attachment();
				channel.doWakeup();
			}
			selector.selectedKeys().clear();
		}

	}

	static SlotWait _channel_scan;
	public static void init() throws Exception {
		selector = Selector.open();
		_channel_scan = new SlotWait() {
			public void invoke() {
				try {
					SlotChannel.invoke(0);
				} catch (Exception e) {
					System.out.println("init failure");
				}
			}
		};

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
}

