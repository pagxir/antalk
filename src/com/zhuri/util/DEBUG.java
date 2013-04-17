package com.zhuri.util;

public class DEBUG {
	static public void Assert(boolean condition) {
		if (condition) return;
		throw new RuntimeException("DEBUG.Assert");
	}
}
