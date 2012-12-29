package wave.talk;

import java.util.*;
import org.w3c.dom.*;
import java.io.StringReader;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;


public class FastXmlVisitor {
	private Element element;

	public FastXmlVisitor() {
	}

	public FastXmlVisitor(Element element) {
		this.element = element;
	}

	public String getValue() {
		Node node;
		if (element == null)
			return "";
		node = element.getFirstChild();
		if (node == null)
			return "";
		return node.getNodeValue();
	}

	public FastXmlVisitor useElement(Element element) {
		this.element = element;
		return this;
	}

	public String format() {
		if (element == null)
			return "";
		return fastFormat(element);
	}

	public static Element fastFormat(String xmlstr) {
		Element retval = null;

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			StringReader reader = new StringReader(xmlstr);
			InputSource source = new InputSource(reader);
			Document doc = builder.parse(source);
			retval = doc.getDocumentElement();
   			/* doc.normalize(); */
		} catch (Exception e) {
			System.out.println("xmlstr: " + xmlstr);
			e.printStackTrace();
			return null;
		}

		return retval;
	}

	public static String fastFormat(Element element) {
		int count = 0;
		Element child;

		String content = "";
		String attrtxt = "";

		String tagEnd = "";
		String tagStart = "";

		String tagName = element.getTagName();
		NodeList children = element.getChildNodes();
		NamedNodeMap attributes = element.getAttributes();

		for (int i = 0; i < attributes.getLength(); i++) {
			Attr attr = (Attr)attributes.item(i);
			attrtxt += (" " + attr.getNodeName());
			attrtxt += ("='" + attr.getNodeValue() + "'");
		}

		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);

			switch (node.getNodeType()) {
				case Node.TEXT_NODE:
					content += node.getNodeValue();
					break;

				case Node.ELEMENT_NODE:
					child = (Element)node;
					content += fastFormat(child);
					break;

				default:
					throw new RuntimeException("Unkown Support Tag Type");
			}
			count++;
		}

		if (count == 0) {
			content = "<" + tagName + attrtxt + "/>";
			return content;
		}

		tagStart = "<" + tagName + attrtxt + ">";
		tagEnd = "</" + tagName + ">";
		
		return tagStart + content + tagEnd;
	}

	public FastXmlVisitor getElement(String name) {
		int i;
		NodeList nodelist;

		if (element != null) {
			nodelist = element.getChildNodes();
			element = null;

			for (i = 0; i < nodelist.getLength(); i++) {
				Node node = nodelist.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					Element t = (Element)node;
					if (name.equals(t.getNodeName())) {
						element = t;
						break;
					}
				}
			}
		}

		return this;
	}

	public String getAttribute(String name) {
		if (element != null)
			return element.getAttribute(name);
		return null;
	}

	public FastXmlVisitor setAttribute(String name, String value) {
		if (element != null) 
			element.setAttribute(name, value);
		return this;
	}
}

