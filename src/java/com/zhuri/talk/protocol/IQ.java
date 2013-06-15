
package com.zhuri.talk.protocol;

import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;

public class IQ extends Packet {
	private Element mElement;

	public IQ() {
		mElement = FastXmlVisitor.fastFormat("<iq/>");
	}

	public IQ(Packet base) {
		mElement = base.mElement;
	}

	public static IQ createQuery(Packet query) {
		return new IQ().setQuery(query);
	}

	public IQ setId(int id) {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement);
		visitor.setAttribute("id", String.valueOf(id));
		return this;
	}

	public IQ setTo(String to) {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement);
		visitor.setAttribute("to", to);
		return this;
	}

	public IQ setType(String type) {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement);
		visitor.setAttribute("type", type);
		return this;
	}

	public IQ setQuery(Packet packet) {
		Element q = FastXmlVisitor.fastFormat(packet.toString());
		Node n = mElement.getOwnerDocument().adoptNode(q);
		if (mElement.hasChildNodes())
			mElement.replaceChild(mElement.getFirstChild(), n);
		else
			mElement.appendChild(n);
		return this;
	}

	public Packet getResult() {
		if (!mElement.hasChildNodes()) {
			return null;
		}

		NodeList list = mElement.getChildNodes();

		for (int i = 0; i < list.getLength(); i++) {
			Node item = list.item(i);
			if (item.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}

			Element element = (Element)item;
			if (!element.getTagName().equals("error")) {
				return new Packet(element);
			}
		}

		return null;
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

	public String getContent() {
		FastXmlVisitor visitor = new FastXmlVisitor(mElement);
		return visitor.getElement("body").getValue();
	}

	public String toString() {
		return FastXmlVisitor.fastFormat(mElement);
	}
}
