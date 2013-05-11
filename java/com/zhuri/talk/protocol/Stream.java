package com.zhuri.talk.protocol;

public class Stream {
	public static String begin(String domain) {
		String header = "<stream:stream to='#DOMAIN#' xmlns='jabber:client' "
			+ "xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>";
		return header.replaceFirst("#DOMAIN#", domain);
	}

	public static String end() {
		return "</stream:stream>";
	}
}
