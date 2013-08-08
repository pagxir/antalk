package com.zhuri.talk.protocol;

import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;

public class Presence extends Packet {

	static public class Status extends Packet {
		private String mText = "unkown";

		public Status(String status) {
			mText = status;
		}

		public String toString() {
			return "<status>" + mText + "</status>";
		}
	}

	private Element mElement;

	public Presence() {
		mElement = FastXmlVisitor.fastFormat("<presence/>");
	}

	public Presence(Packet base) {
		mElement = base.mElement;
	}

	public Presence setTo(String to) {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement);
		visitor.setAttribute("to", to);
		return this;
	}

	public Presence setType(String type) {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement);
		visitor.setAttribute("type", type);
		return this;
	}

	public Presence setStatus(String status) {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement).getElement("status");
		if (visitor.isEmpty()) {
			add(new Status(status));
			return this;
		}

		visitor.setValue(status);
		return this;
	}

	public Presence add(Packet packet) {
		Element e = FastXmlVisitor.fastFormat(packet.toString());
		mElement.appendChild(mElement.getOwnerDocument().adoptNode(e));
		return this;
	}

	public String getType() {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement);
		return visitor.getAttribute("type");
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
