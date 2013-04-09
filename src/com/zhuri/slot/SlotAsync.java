package com.zhuri.slot;

public class SlotAsync extends SlotThread.Wait {

	/* this method can call from other thread. */
	public void toggle() {
		signaled = true;
		SlotThread.signal();
	}
}
