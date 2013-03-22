package com.zhuri.slot;

public class SlotWait {
	InnerWait mInnerWait;

	public SlotWait() {

	}

	public SlotWait(Object u) {

	}

	public void cancel() {
		mInnerWait.cancel();
	}

	public void clear() {
		mInnerWait.clear();
	}

	public void clean() {
		mInnerWait.clean();
	}

	public boolean active() {
		return mInnerWait.active();
	}

	public boolean started() {
		return mInnerWait.started();
	}

	public boolean completed() {
		return mInnerWait.completed();
	}

	public void schedule() {
		SlotSlot.schedule(mInnerWait);
	}

	public Object result() {
		return mInnerWait.result;
	}

	public void setResult(Object result) {
		mInnerWait.result = result;
	}

	public long lState() {
		return mInnerWait.lState;
	}

	public void setlState(long state) {
		mInnerWait.lState = state;
	}
}

class InnerWait {
	int flags;
	InnerWait next;
	InnerWait prev;

	long lState;
	Object result;
	private Object oState;
	private Object uState;
	private Runnable callback;

	public InnerWait() {
		this.flags = (WT_INACTIVE| WT_EXTERNAL);
		this.next = null;
		this.callback = new Runnable() {
			public void run() {
				System.out.println("InnerWait()");
				throw new RuntimeException("");
			}
		};
	}

	public InnerWait(Object state) {
		this.flags = (WT_INACTIVE| WT_EXTERNAL);
		this.next = null;
		this.uState = state;
		this.callback = new Runnable() {
			public void run() {
				System.out.println("InnerWait(Object state)");
			}
		};
	}

	void schedule() {
		SlotSlot.schedule(this);
	}

	public InnerWait(Runnable call) {
		this.flags = (WT_INACTIVE| WT_EXTERNAL);
		this.callback = call;
		this.next = null;
	}

	public void invoke() {
		callback.run();
		return;
	}

	public void cancel() {
		if (this.active() && 0 == (flags & WT_CLEANING)) {
			this.prev.next = this.next;
			if (this.next != null) {
				this.next.prev = this.prev;
			} else {
				SlotSlot.remove(this);
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

	public boolean started() {
		int flags = this.flags;
		flags &= (WT_INACTIVE| WT_COMPLETE);
		return (flags != WT_INACTIVE);
	}

	public boolean active() {
		int flags = this.flags;
		flags &= WT_INACTIVE;
		return (flags == 0);
	}

	static boolean _aborted = false;
	static Exception _exception = null;
	public static void bugCheck(Exception e) {
		_exception = e;
		_aborted = true;
		return;
	}

	static int WT_CLEANING = 0x00000010;
	static int WT_WAITSCAN = 0x00000008;
	static int WT_EXTERNAL = 0x00000004;
	static int WT_COMPLETE = 0x00000002;
	static int WT_INACTIVE = 0x00000001;
}

