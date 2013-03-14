package wave.talk.protocol;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;

public class XmlParser {
	Document document;
	DocumentBuilder builder;
	DocumentBuilderFactory factory =
	    DocumentBuilderFactory.newInstance();

	public String title = "";
	boolean parsePacket(byte[] packet, int off, int len) {
		InputStream is = new ByteArrayInputStream(packet, off, len);

		try {
			factory.setNamespaceAware(true);
			builder = factory.newDocumentBuilder();
			document = builder.parse(is);
		} catch (Exception e) {
			return false;
		}

		title = new String(packet, off, len);
		return true;
	}

	long lastRead = 0;
	boolean atEOF() {
		return lastRead == -1;
	}

	boolean reset() {
		lastRead = 0;
		buffer.position(0);
		buffer.limit(65536);
		arrcalc[0] = arrcalc[1] = 0;
		return false;
	}

	int[] arrcalc = new int[2];
	ByteBuffer buffer = ByteBuffer.allocate(65536);
	boolean readPacket(ReadableByteChannel channel) throws Exception {

		int tagidx = 0;
		byte dotcur = '.';
		byte dotprev = '$';

		lastRead = channel.read(buffer);
		buffer.flip();

		int[] savArrCalc = new int[2];
		savArrCalc[0] = arrcalc[0];
		savArrCalc[1] = arrcalc[1];
		while (buffer.hasRemaining()) {
			dotcur = buffer.get();
			if (dotcur == '/' &&
			        dotprev == '<') {
				tagidx = 1;
			}

			if (dotcur == '>' &&
			        dotprev == '/') {
				arrcalc[0]++;
				tagidx = 1;
			}

			if (dotcur == '>' &&
			        dotprev == '?') {
				dotprev = dotcur;
				tagidx = 0;
				continue;
			}

			if (dotcur == '>') {
				arrcalc[tagidx]++;
				tagidx = 0;
			}

			if (dotcur == '>' && arrcalc[0] == arrcalc[1] + 1) {
				if (arrcalc[1] == 0) {
					savArrCalc[0] = arrcalc[0];
					savArrCalc[1] = arrcalc[1];
					dotprev = dotcur;
					buffer.compact();
					buffer.flip();
					continue;
				}

				parsePacket(buffer.array(), 0, buffer.position());
				buffer.compact();
				buffer.limit(65536);
				return true;
			}

			dotprev = dotcur;
		}

		arrcalc[0] = savArrCalc[0];
		arrcalc[1] = savArrCalc[1];
		buffer.limit(65536);
		return false;
	}

	public Element packet() {
		return document == null? null:
		       document.getDocumentElement();
	}

	public void clear() {
		document = null;
	}
}

