package com.zhuri.util;

public class MadPlayer {

    private byte[] context;
    private static native int length();
    private native void init(byte[] context);
    private native void fini(byte[] context);

    private native int feed(byte[] context, byte[] src, int off, int len);
    private native int frame(byte[] context, byte[] dst, int off, int len);

    public MadPlayer() {
        context = new byte[length()];
        init(context);
    }

    public int write(byte[] dat, int off, int len) {
        int retval = feed(context, dat, off, len);
        return retval;
	}

    public int write(byte[] dat) {
        int retval = write(dat, 0, dat.length);
        return retval;
	}

    public int read(byte[] out, int off, int len) {
        int retval = frame(context, out, off, len);
        return retval;
	}

    public int read(byte[] out) {
        int retval = read(out, 0, out.length);
        return retval;
    }

    public void release() {
        fini(context);
    }

    static {
        System.loadLibrary("madplay");
    }
};

