package com.zhuri.talk.protocol;

import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;

public class Message extends Packet {
	private Element mElement;

	public Message() {
		mElement = FastXmlVisitor.fastFormat("<message/>");
	}

	public Message(Packet base) {
		mElement = base.mElement;
	}

	public boolean hasBody() {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement);
		return visitor.getElement("body").isEmpty();
	}

	public Message setTo(String to) {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement);
		visitor.setAttribute("to", to);
		return this;
	}

	public Message add(Packet packet) {
		Element e = FastXmlVisitor.fastFormat(packet.toString());
		mElement.appendChild(mElement.getOwnerDocument().adoptNode(e));
		return this;
	}

	public String getFrom() {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement);
		return visitor.getAttribute("from");
	}

	public String getTo() {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement);
		return visitor.getAttribute("to");
	}

	public String toString() {
		return FastXmlVisitor.fastFormat(mElement);
	}
}
