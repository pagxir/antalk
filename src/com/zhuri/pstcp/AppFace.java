package com.zhuri.pstcp;

public class AppFace {
	public static native void start();
	public static native void loop();
	public static native void stop();
	public static native void setPort(int port);

	static {
		System.loadLibrary("pstcp");
	}
}

