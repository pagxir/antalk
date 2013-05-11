package com.zhuri.slot;

import java.io.IOException;
import java.nio.ByteBuffer;
import com.zhuri.slot.SlotWait;

public interface IWaitableChannel {
	public void waitI(SlotWait wait);
	public void waitO(SlotWait wait);
	public long read(ByteBuffer dst) throws IOException;
	public long write(ByteBuffer src) throws IOException;
}

