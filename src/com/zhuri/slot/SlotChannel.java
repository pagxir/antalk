package com.zhuri.slot;

import java.nio.channels.SelectableChannel;

public class SlotChannel extends SlotThread.Channel {
	public static SlotChannel open(SelectableChannel selectable) {
		SlotChannel channel;

		if (selectable.isRegistered()) {
			channel = (SlotChannel)SlotThread.getSlotChannel(selectable);
			return channel;
		}

		channel = new SlotChannel();
		channel.attach(selectable);
		return channel;
	}
}

