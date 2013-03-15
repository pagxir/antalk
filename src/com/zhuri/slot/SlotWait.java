package com.zhuri.slot;

public class SlotWait {
	public int flags;
	public SlotWait next;
	public SlotWait prev;

	public long lState;
	public Object result;
	public Object oState;
	public Object uState;
	public Runnable callback;

	public SlotWait() {
		this.flags = (WT_INACTIVE| WT_EXTERNAL);
		this.next = null;
		this.callback = new Runnable() {
			public void run() {
				System.out.println("SlotWait()");
				throw new RuntimeException("");
			}
		};
	}

	public SlotWait(Object state) {
		this.flags = (WT_INACTIVE| WT_EXTERNAL);
		this.next = null;
		this.uState = state;
		this.callback = new Runnable() {
			public void run() {
				System.out.println("SlotWait(Object state)");
			}
		};
	}

	public SlotWait(Runnable call) {
		this.flags = (WT_INACTIVE| WT_EXTERNAL);
		this.callback = call;
		this.next = null;
	}

	public void schedule() {
		SlotSlot.schedule(this);
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

	public boolean ipcSchedule() {
		boolean error = false;

		synchronized(_ipc_slot) {
			if (_aborted)
				throw new RuntimeException(_exception);

			if (!this.active()) {
				_ipc_slot.record(this);
				error = true;
			}
			SlotChannel.ipcWakeup();
		}

		return error;
	}

	static SlotSlot _ipc_slot;
	static SlotWait _ipc_selscan;
	public static void ipc_init() {
		_ipc_slot = new SlotSlot();
		_ipc_selscan = new SlotWait() {
			public void invoke() {
				synchronized(_ipc_slot) {
					SlotWait next;
					SlotWait header = _ipc_slot.getHeader();
					while (header.next != null) {
						next = header.next;
						next.cancel();
						next.schedule();
					}
				}
			}
		};

		_ipc_selscan.flags &= ~WT_EXTERNAL;
		_ipc_selscan.flags |= WT_WAITSCAN;
		_ipc_selscan.schedule();
	}

	public static void ipc_fini() {
		_ipc_selscan.clean();
	}

	static int WT_CLEANING = 0x00000010;
	static int WT_WAITSCAN = 0x00000008;
	static int WT_EXTERNAL = 0x00000004;
	static int WT_COMPLETE = 0x00000002;
	static int WT_INACTIVE = 0x00000001;
}

