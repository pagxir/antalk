package com.zhuri.slot;

import java.nio.channels.*;

public class SlotThread {
	static boolean _aborted = false;
	static Exception _exception = null;
	static final int WT_CLEANING = 0x00000010;
	static final int WT_WAITSCAN = 0x00000008;
	static final int WT_EXTERNAL = 0x00000004;
	static final int WT_COMPLETE = 0x00000002;
	static final int WT_INACTIVE = 0x00000001;

	public static void bugCheck(Exception e) {
		_exception = e;
		_aborted = true;
		return;
	}

	/* BEGIN: implement timer module */
	static long _stilltick;
	static Slot _stilltimers = new Slot();

	static long _microtick;
	static long _microwheel;
	static Slot[] _microtimers = new Slot[50];

	static long _macrotick;
	static long _macrowheel;
	static Slot[] _macrotimers = new Slot[60];

	public static void Init() {
		try {
			SlotThread.slInit();
			SlotThread.chInit();
			SlotThread.tmInit();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void quit() {
		requestquited = true;
		if (selector != null)
			selector.wakeup();
		return;
	}

	public static void signal() {
		if (selector != null)
			selector.wakeup();
		return;
	}

	public static void Finit() {
	}

	static void tmInvoke() {
		int wheel;
		Timer tNow, tNext;
		long tick = System.currentTimeMillis();

		while ((long)(tick - _microtick - 20) >= 0) {
			_microtick += 20;
			_microwheel++;
			wheel = (int)(_microwheel % 50);
			_microtimers[wheel].wakeup();
		}

		while ((long)(tick - _macrotick - 1000) >= 0) {
			_macrotick += 1000;
			_macrowheel++;
			wheel = (int)(_macrowheel % 60);
			Wait timer = _macrotimers[wheel].getHeader();
			while (timer.next != null) {
				tNow = (Timer)timer.next;
				if ((long)(tNow.lState - tick) < 20) {
					tNow.cancel();
					tNow.schedule();
				} else {
					tNow.cancel();
					tmReset(tNow, tNow.lState - tick);
				}
			}
		}

		if ((long)(tick - _stilltick - 60000) >= 0) {
			_stilltick = tick;
			tNow = (Timer)_stilltimers.getHeader().next;
			while (tNow != null) {
				tNext = (Timer)tNow.next;
				if ((long)(tNow.lState - tick) < 20) {
					tNow.cancel();
					tNow.schedule();
				} else if ((long)(tNow.lState - tick) < 60000) {
					tNow.cancel();
					tmReset(tNow, tNow.lState - tick);
				}
				tNow = tNext;
			}
		}
	}

	static void tmReset(Timer timer, long millisec) {
		int wheel;
		long millisec1;
		long microwheel, macrowheel;

		timer.clear();
		millisec1 = (millisec + System.currentTimeMillis());
		macrowheel = (millisec1 - _macrotick) / 1000;
		microwheel = (millisec1 - _microtick) / 20;
		timer.lState = millisec1;

		if (microwheel == 0) {
			System.out.println("[warn]: too small timer not supported!");
			microwheel = 1;
		}

		if (microwheel < 50) {
			wheel = (int)(_microwheel + microwheel) % 50;
			_microtimers[wheel].record(timer);
			return;
		}

		if (macrowheel < 60) {
			wheel = (int)(_macrowheel + macrowheel) % 60;
			_macrotimers[wheel].record(timer);
			return;
		}

		_stilltimers.record(timer);
		return;
	}

	static Wait _timer_scan = new Wait() {
		public void invoke() {
			tmInvoke();
			return;
		}
	};

	public static void tmInit() {
		long tick = System.currentTimeMillis();
		_stilltick = tick;

		_microtick = tick;
		_microwheel = 0;

		_macrotick = tick;
		_macrowheel = 0;

		for (int i = 0; i < 50; i++)
			_microtimers[i] = new Slot();

		for (int i = 0; i < 60; i++)
			_macrotimers[i] = new Slot();

		_timer_scan.flags &= ~WT_EXTERNAL;
		_timer_scan.flags |= WT_WAITSCAN;
		_timer_scan.schedule();
	}
	/* END: implement timer module */

	/* BEGIN: implement slot module */
	static Wait readyqueue;
	static Wait readytailer;
	static Slot stopslot =  new Slot();

	static int waitrescan = 0;
	static boolean requestquited = false;

	public static void atstop(Wait wait) {
		stopslot.record(wait);
	}

	public static void remove(Wait wait) {
		if (wait == readytailer)
			readytailer = wait.prev;
	}

	public static void stop() {
		stopslot.wakeup();
		requestquited = true;
	}

	public static void slInit() {
		readyqueue = new Wait();
		readytailer = readyqueue;
		requestquited = false;
	}

	public static void slFini() {
		readytailer = null;
		readyqueue = null;
	}

	public static void schedule(Wait wait) {
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
		Wait iter = null;
		Wait marker = new Wait();
		marker.flags &= ~WT_EXTERNAL;

		waitrescan = 0;
		marker.schedule();
		for ( ; ; ) {
			iter = readyqueue.next;

			iter.cancel();
			if (iter == marker) {
				if (waitrescan == 0) {
					if (requestquited) {
						marker.cancel();
						waitrescan = 0;
						return false;
					}
					chInvoke(50);
					tmInvoke();
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
	/* END: implement slot module */

	/* BEGIN: implement channel module */
	static Selector selector = null;
	static public Channel getSlotChannel(SelectableChannel selectable) {
		Channel channel;
		SelectionKey key = selectable.keyFor(selector);

		if (key != null) {
			channel = (Channel)key.attachment();
			return channel;
		}

		return null;
	}

	static void chInvoke(long timeout) throws Exception {
		int count;
		Channel channel;

		count = timeout > 0? selector.select(timeout): selector.selectNow();
		if (count > 0) {
			int flags = 0;

			for (SelectionKey key: selector.selectedKeys()) {
				channel = (Channel)key.attachment();
				channel.wakeup();
			}

			selector.selectedKeys().clear();
		}
	}

	static Wait _channel_scan = new Wait() {
		public void invoke() {
			try {
				chInvoke(0);
			} catch (Exception e) {
				System.out.println("init failure");
				e.printStackTrace();
			}
		}
	};

	public static void chInit() throws Exception {
		selector = Selector.open();

		_channel_scan.flags &= ~WT_EXTERNAL;
		_channel_scan.flags |= WT_WAITSCAN;
		_channel_scan.schedule();
	}

	public static void chFini() throws Exception {
		_channel_scan.clean();
		_channel_scan = null;
		selector.close();
		selector = null;
	}
	/* END: implement channel module */

	static class Channel {
		int mFlags;
		SelectionKey mSlotKey;

		Slot mSlotIn = new Slot();
		Slot mSlotOut = new Slot();

		Channel() {
			mFlags = SelectionKey.OP_CONNECT |
				SelectionKey.OP_WRITE | SelectionKey.OP_READ;
			mSlotKey = null;
		}

		public void attach(SelectableChannel channel) {

			/* channel or selector should not be null. */
			if (channel == null || selector == null)
				throw new IllegalArgumentException("SlotChannel.attach tcp failure");

			try {
				mFlags = channel.validOps();
				channel.configureBlocking(false);
				mSlotKey = channel.register(selector, mFlags, this);
			} catch (Exception e) {
				e.printStackTrace();
				throw new IllegalArgumentException("SlotChannel.attach x failure");
			}
		}

		public void detach() {
			mSlotIn = null;
			mSlotOut = null;
			if (mSlotKey != null)
				mSlotKey.cancel();
			mSlotKey = null;
		}

		public boolean wantIn(Wait wait) {
			/* TODO: assert wait not active or is in the slotIn. */

			wait.cancel();
			mSlotIn.record(wait);
			/* if (SelectionKey.OP_READ != (mFlags & SelectionKey.OP_READ)) */ {
				mFlags |= SelectionKey.OP_READ;
				mSlotKey.interestOps(mFlags);
			}

			return false;
		}

		public boolean wantOut(Wait wait) {
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

	static class Slot {
		private Wait header = new Wait();

		public Wait getHeader() {
			return header;
		}

		public boolean isEmpty() {
			return header.next == null;
		}

		public void record(Wait wait) {

			if (WT_INACTIVE != (wait.flags & WT_INACTIVE)) {
				throw new IllegalStateException("Slot::record wait state is not WT_INACTIVE");
			}

			wait.flags &= ~WT_INACTIVE;
			wait.next = header.next;
			if (wait.next != null)
				wait.next.prev = wait;
			wait.prev = header;
			header.next = wait;
		}

		public void wakeup() {
			Wait wait;

			while (header.next != null) {
				wait = header.next;
				wait.cancel();
				schedule(wait);
			}
		}
	}

	static class Wait {
		int flags;
		Wait next;
		Wait prev;

		Object result;
		private Runnable callback;

		Wait() {
			this.flags = (WT_INACTIVE| WT_EXTERNAL);
			this.next = null;
			this.callback = new Runnable() {
				public void run() {
					System.out.println("Wait()");
					throw new RuntimeException("");
				}
			};
		}

		Wait(Object state) {
			this.flags = (WT_INACTIVE| WT_EXTERNAL);
			this.next = null;
			this.callback = new Runnable() {
				public void run() {
					System.out.println("Wait(Object state)");
				}
			};
		}

		Wait(Runnable call) {
			this.flags = (WT_INACTIVE| WT_EXTERNAL);
			this.callback = call;
			this.next = null;
		}

		public void invoke() {
			callback.run();
			return;
		}

		void schedule() {
			SlotThread.schedule(this);
		}

		public void cancel() {
			if (this.active() && 0 == (flags & WT_CLEANING)) {
				this.prev.next = this.next;
				if (this.next != null) {
					this.next.prev = this.prev;
				} else {
					SlotThread.remove(this);
				}

				this.prev = this.next = null;
				this.flags |= WT_INACTIVE;
			}
		}

		public void clean() {
			this.cancel();
			this.flags = WT_CLEANING;
			this.callback = null;
		}

		public void clear() {
			int flags;
			this.cancel();
			flags = this.flags;
			this.flags = flags & ~WT_COMPLETE;
		}

		public boolean completed() {
			int flags = this.flags;
			flags &= WT_COMPLETE;
			return (flags != 0);
		}

		boolean started() {
			int flags = this.flags;
			flags &= (WT_INACTIVE| WT_COMPLETE);
			return (flags != WT_INACTIVE);
		}

		boolean active() {
			int flags = this.flags;
			flags &= WT_INACTIVE;
			return (flags == 0);
		}
	}

	static class Async extends Wait {
		private Runnable runnable;
		private boolean signaled = false;

		public Async(Runnable r) {
			runnable = r;
		}

		final public void invoke() {
			if (signaled)
				runnable.run();
		}

		/* this method can call from other thread. */
		public void toggle() {
			signaled = true;
			SlotThread.signal();
		}

		public void setup() {
			flags &= ~WT_EXTERNAL;
			flags |= WT_WAITSCAN;
			schedule();
		}
	}

	static class Timer extends Wait {
		public Timer() {
			super();
		}

		public Timer(Runnable r) {
			super(r);
		}

		long lState;
	}

	static class ReadWrite extends Wait {

	}
}

