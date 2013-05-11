package com.zhuri.slot;

public class SlotTimer extends SlotThread.Timer {
	public SlotTimer() {
		super();
	}

	public SlotTimer(Runnable r) {
		super(r);
	}

	public void reset(long millisec) {
		SlotThread.tmReset(this, millisec);
	}
}

