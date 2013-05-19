package com.zhuri.pstcp;

public class AppFace {
	public static native void start();
	public static native void loop();
	public static native void stop();
	public static native void setPort(int port);
	public static native void setForward(byte[] address, int port);

	public static native String stunGetName();
	public static native void stunSendRequest(String target, int type);

	static {
		System.loadLibrary("pstcp");
	}
}

