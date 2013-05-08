package com.zhuri.talk.protocol;

import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;

public class Packet {
	final static DocumentBuilderFactory factory = 
				DocumentBuilderFactory.newInstance();
	public final static Packet EMPTY_PACKET = new Packet();

	Packet() {
	}

	private Element mElement;
	Packet(Element element) {
		mElement = element;
	}

	public String getTag() {
		if (EMPTY_PACKET != this)
			return mElement.getTagName();
		return "";
	}

	public boolean matchTag(String tag) {
		String tagName;
		if (EMPTY_PACKET == this)
			return false;
		tagName = mElement.getTagName();
		System.out.println("TAG# " + tag + " " + tagName.replaceAll(".*:", ""));
		return tagName.replaceAll(".*:", "").equals(tag);
	}

	public static Packet parse(byte[] data, int off, int length) {
		Document document;
		InputStream stream;
		DocumentBuilder builder;

		stream = new ByteArrayInputStream(data, off, length);

		factory.setNamespaceAware(false);

		try {
			builder = factory.newDocumentBuilder();
			document = builder.parse(stream);
		} catch (Exception e) {
			e.printStackTrace();
			return EMPTY_PACKET;
		}

		return new Packet(document.getDocumentElement());
	}
}
