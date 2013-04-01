package com.zhuri.slot;

public class SlotTimer extends SlotThread.Timer {
	public void reset(long millisec) {
		SlotThread.tmReset(this, millisec);
	}
}

