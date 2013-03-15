package com.zhuri.util;

public class ShineWrapper {
	private byte[] shine;
	private static native int getCtxBlkSize();
	private native void init(byte[] shine);
	private native void fini(byte[] shine);

	private native int encode(byte[] shine, byte[] src, int len, byte[] dst, int count);

	public ShineWrapper() {
		shine = new byte[getCtxBlkSize()];
		init(shine);
	}

	public int encodeFrame(byte[] src, int len, byte[] dst, int count) {
		int retval = encode(shine, src, len, dst, count);
		return retval;
	}

	public void release() {
		fini(shine);
	}
	
	static {
		System.loadLibrary("shine");
	}
};
