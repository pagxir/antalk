package com.zhuri.talk.protocol;

import java.util.*;
import org.w3c.dom.*;
import java.io.StringReader;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;

import java.io.IOException;  
import java.io.StringReader;  
import java.io.StringWriter;  
  
public class FastXmlVisitor {
	private Element element = null;

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

	public boolean isEmpty() {
		return element == null;
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
			factory.setNamespaceAware(true); 
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

	public static String textFormat(String text) {
		String r1 = text.replaceAll("&", "&amp;").
			replaceAll("<", "&lt;").replaceAll(">", "&gt;").
			replaceAll("\"", "&quot;").replaceAll("\'", "&apos;");
		return r1;
	}

	public static String fastFormat(Element e) {  
		StringWriter writer = new StringWriter();
		TransformerFactory tf = TransformerFactory.newInstance();

		try {
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.transform(new DOMSource(e), new StreamResult(writer));
		} catch (TransformerConfigurationException ce) {
			ce.printStackTrace();
		} catch (TransformerException te) {
			te.printStackTrace();
		}

		return writer.getBuffer().toString().replaceAll("\n|\r", "");
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

