package com.zhuri.slot;

public class SlotTimer {
	static long _stilltick;
	static SlotSlot _stilltimers = new SlotSlot();

	static long _microtick;
	static long _microwheel;
	static SlotSlot[] _microtimers = new SlotSlot[50];

	static long _macrotick;
	static long _macrowheel;
	static SlotSlot[] _macrotimers = new SlotSlot[60];

	public static void invoke() {
		int wheel;
		SlotWait event, nextEvent;
		long tick = System.currentTimeMillis();

		for ( ; ; ) {
			if ((long)(tick - _microtick - 20) < 0)
				break;
			_microtick += 20;
			_microwheel++;
			wheel = (int)(_microwheel % 50);
			_microtimers[wheel].wakeup();
		}

		for ( ; ; ) {
			if ((long)(tick - _macrotick - 1000) < 0)
				break;
			_macrotick += 1000;
			_macrowheel++;
			wheel = (int)(_macrowheel % 60);
			SlotWait wait = _macrotimers[wheel].getHeader();
			while (wait.next != null) {
				event = wait.next;
				if ((long)(event.lState - tick) < 20) {
					event.cancel();
					event.schedule();
				} else {
					event.cancel();
					SlotTimer.reset(event, event.lState - tick);
				}
			}
		}

		if ((long)(tick - _stilltick - 60000) >= 0) {
			_stilltick = tick;
			event = _stilltimers.getHeader().next;
			while (event != null) {
				nextEvent = event.next;
				if ((long)(event.lState - tick) < 20) {
					event.cancel();
					event.schedule();
				} else if ((long)(event.lState - tick) < 60000) {
					event.cancel();
					SlotTimer.reset(event, event.lState - tick);
				}
				event = nextEvent;
			}
		}
	}

	public static void reset(SlotWait wait, long millisec) {
		int wheel;
		long millisec1;
		long microwheel, macrowheel;

		wait.clear();
		millisec1 = (millisec + System.currentTimeMillis());
		macrowheel = (millisec1 - _macrotick) / 1000;
		microwheel = (millisec1 - _microtick) / 20;
		wait.lState = millisec1;

		if (microwheel == 0) {
			System.out.println("[warn]: too small timer not supported!");
			microwheel = 1;
		}

		if (microwheel < 50) {
			wheel = (int)(_microwheel + microwheel) % 50;
			_microtimers[wheel].record(wait);
			return;
		}

		if (macrowheel < 60) {
			wheel = (int)(_macrowheel + macrowheel) % 60;
			_macrotimers[wheel].record(wait);
			wait = _macrotimers[wheel].getHeader();
			return;
		}

		_stilltimers.record(wait);
		return;
	}

	static SlotWait _timer_scan;
	public static void init() {
		long tick = System.currentTimeMillis();
		_stilltick = tick;

		_microtick = tick;
		_microwheel = 0;

		_macrotick = tick;
		_macrowheel = 0;

		for (int i = 0; i < 50; i++)
			_microtimers[i] = new SlotSlot();
		for (int i = 0; i < 60; i++)
			_macrotimers[i] = new SlotSlot();

		_timer_scan = new SlotWait() {
			public void invoke() {
				SlotTimer.invoke();
				return;
			}
		};

		_timer_scan.flags &= ~WT_EXTERNAL;
		_timer_scan.flags |= WT_WAITSCAN;
		_timer_scan.schedule();
	}

	static int WT_WAITSCAN = 0x00000008;
	static int WT_EXTERNAL = 0x00000004;
	static int WT_COMPLETE = 0x00000002;
	static int WT_INACTIVE = 0x00000001;
}

