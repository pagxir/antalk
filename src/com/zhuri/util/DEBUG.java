package com.zhuri.util;

public class DEBUG {
	static public void Assert(boolean condition) {
		if (condition) return;
		throw new RuntimeException("DEBUG.Assert");
	}

	static public void Print(String log) {
		System.out.println(log);
	}

	static public void Print(String tag, String log) {
		System.out.println(tag + ": " + log);
	}
}
