package com.zhuri.slot;

public class SlotSlot {
	SlotWait header = new SlotWait();

	public SlotSlot() {
	}

	public SlotWait getHeader() {
		return header;
	}

	public boolean isEmpty() {
		return header.next == null;
	}

	public void record(SlotWait wait) {
		assert(WT_INACTIVE == (wait.flags & WT_INACTIVE));

		wait.flags &= ~WT_INACTIVE;
		wait.next = header.next;
		if (wait.next != null)
			wait.next.prev = wait;
		wait.prev = header;
		header.next = wait;
	}

	public static void schedule(SlotWait wait) {
		int flags;

		flags = wait.flags;
		waitrescan =
		    ((flags & WT_EXTERNAL) > 0? 1: waitrescan);

		readytailer.next = wait;
		wait.next = null;
		wait.prev = readytailer;
		wait.flags |= WT_COMPLETE;
		wait.flags &= ~WT_INACTIVE;
		readytailer = wait;
	}

	public static void remove(SlotWait wait) {
		if (wait == readytailer)
			readytailer = wait.prev;
	}

	public void wakeup() {
		SlotWait wait;

		while (header.next != null) {
			wait = header.next;
			wait.cancel();
			schedule(wait);
		}
	}

	static SlotSlot stopslot =  new SlotSlot();
	public static void atstop(SlotWait wait) {
		stopslot.record(wait);
	}

	public static void stop() {
		stopslot.wakeup();
		requestquited = true;
	}

	static int WT_WAITSCAN = 0x00000008;
	static int WT_EXTERNAL = 0x00000004;
	static int WT_COMPLETE = 0x00000002;
	static int WT_INACTIVE = 0x00000001;

	static SlotWait readyqueue;
	static SlotWait readytailer;
	static int waitrescan = 0;
	static boolean nonblock = false;
	static boolean requestquited = false;
	public static boolean step() throws Exception {
		SlotWait iter = null;
		SlotWait marker = new SlotWait();
		marker.flags &= ~WT_EXTERNAL;

		waitrescan = 0;
		marker.schedule();
		for ( ; ; ) {
			iter = readyqueue.next;

			iter.cancel();
			if (iter == marker) {
				if (waitrescan == 0) {
					if (requestquited || nonblock) {
						marker.cancel();
						waitrescan = 0;
						return false;
					}
					SlotChannel.invoke(50);
					SlotTimer.invoke();
				}
				iter.schedule();
				continue;
			}

			if (WT_WAITSCAN == (iter.flags & WT_WAITSCAN)) {
				iter.schedule();
				iter.invoke();
				continue;
			}

			iter.flags |= WT_COMPLETE;
			iter.invoke();
			break;
		}

		marker.cancel();
		return true;
	}

	public static void init() throws Exception {
		readyqueue = new SlotWait();
		readytailer = readyqueue;
		requestquited = false;
	}

	public static void fini() throws Exception {
		readytailer = null;
		readyqueue = null;
	}
}

