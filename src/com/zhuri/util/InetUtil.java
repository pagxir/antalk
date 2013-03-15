package com.zhuri.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public final class InetUtil {
	public static InetAddress getInetAddress(String address) throws UnknownHostException {
		int index = 0;
		String[] parts = address.split("\\.");

		if (parts.length != 4) {
			InetAddress retval = InetAddress.getByName(address);
			android.util.Log.i("jabber", "getByName0: " + address);
			return retval;
		}

		byte[] array = new byte[4];
		for (String part: parts) {
			if (!part.matches("[0-9][0-9]*")) {
				android.util.Log.i("jabber", "getByName: " + address);
				InetAddress retval = InetAddress.getByName(address);
				return retval;
			}
			array[index] = new Integer(part).byteValue();
			index++;
		}

		return getInetAddress(array);
	}

	public static InetAddress getInetAddress(byte[] address) throws UnknownHostException {
		return InetAddress.getByAddress("kitty", address);
	}

	public static InetSocketAddress getInetSocketAddress(String address, int port) throws UnknownHostException {
		return new InetSocketAddress(getInetAddress(address), port);
	}

	public static InetSocketAddress getInetSocketAddress(byte[] address, int port) throws UnknownHostException {
		return new InetSocketAddress(getInetAddress(address), port);
	}
}

