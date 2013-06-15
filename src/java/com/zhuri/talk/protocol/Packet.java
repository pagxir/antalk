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

	Element mElement;
	Packet(Element element) {
		mElement = element;
	}

	public String get(String key) {
		if (EMPTY_PACKET != this)
			return mElement.getAttribute(key);
		return null;
	}

	public String getTag() {
		if (EMPTY_PACKET != this)
			return mElement.getTagName();
		return "";
	}

	public String getURI() {
		if (EMPTY_PACKET != this)
			return mElement.getNamespaceURI();
		return "";
	}

	public String toString() {
		if (EMPTY_PACKET != this)
			return FastXmlVisitor.fastFormat(mElement);
		return super.toString();
	}

	public boolean matchURI(String uri) {
		String muri;
		if (EMPTY_PACKET == this)
			return false;
		muri = mElement.getNamespaceURI();
		System.out.println("URI  " + muri);
		return uri.equals(muri);
	}

	public boolean matchTag(String tag) {
		String tagName;
		if (EMPTY_PACKET == this)
			return false;
		tagName = mElement.getTagName();
		return tagName.replaceAll(".*:", "").equals(tag);
	}

	public static Packet parse(byte[] data, int off, int length) {
		Document document;
		InputStream stream;
		DocumentBuilder builder;

		stream = new ByteArrayInputStream(data, off, length);
		factory.setNamespaceAware(true);

		try {
			builder = factory.newDocumentBuilder();
			document = builder.parse(stream);
			return new Packet(document.getDocumentElement());
		} catch (Exception e1) {
			stream = new ByteArrayInputStream(data, off, length);
			factory.setNamespaceAware(false);

			try {
				builder = factory.newDocumentBuilder();
				document = builder.parse(stream);
			} catch (Exception e) {
				e.printStackTrace();
				return EMPTY_PACKET;
			}
		}

		return new Packet(document.getDocumentElement());
	}
}
