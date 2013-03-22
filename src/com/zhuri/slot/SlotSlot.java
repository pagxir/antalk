package com.zhuri.slot;

public class SlotSlot {
	static InnerWait readyqueue;
	static InnerWait readytailer;
	static SlotSlot stopslot =  new SlotSlot();

	static int waitrescan = 0;
	static boolean nonblock = false;
	static boolean requestquited = false;

	static int WT_WAITSCAN = 0x00000008;
	static int WT_EXTERNAL = 0x00000004;
	static int WT_COMPLETE = 0x00000002;
	static int WT_INACTIVE = 0x00000001;

	public static void atstop(SlotWait wait) {
		stopslot.record(wait.mInnerWait);
	}

	public static void remove(InnerWait wait) {
		if (wait == readytailer)
			readytailer = wait.prev;
	}

	public static void stop() {
		stopslot.wakeup();
		requestquited = true;
	}

	public static void init() throws Exception {
		readyqueue = new InnerWait();
		readytailer = readyqueue;
		requestquited = false;
	}

	public static void fini() throws Exception {
		readytailer = null;
		readyqueue = null;
	}

	public static void schedule(InnerWait wait) {
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

	public static boolean step() throws Exception {
		InnerWait iter = null;
		InnerWait marker = new InnerWait();
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

	private InnerWait header = new InnerWait();

	public SlotSlot() {
	}

	public InnerWait getHeader() {
		return header;
	}

	public boolean isEmpty() {
		return header.next == null;
	}

	public void record(SlotWait wait) {
		record(wait.mInnerWait);
	}

	void record(InnerWait wait) {

		if (WT_INACTIVE != (wait.flags & WT_INACTIVE)) {
			throw new IllegalStateException("SlotSlot::record wait state is not WT_INACTIVE");
		}

		wait.flags &= ~WT_INACTIVE;
		wait.next = header.next;
		if (wait.next != null)
			wait.next.prev = wait;
		wait.prev = header;
		header.next = wait;
	}

	public void wakeup() {
		InnerWait wait;

		while (header.next != null) {
			wait = header.next;
			wait.cancel();
			schedule(wait);
		}
	}

	@Override
	public void finalize() {
		InnerWait wait;

		while (header.next != null) {
			wait = header.next;
			wait.cancel();
		}
	}
}

