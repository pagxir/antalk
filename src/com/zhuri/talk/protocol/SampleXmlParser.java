package com.zhuri.talk.protocol;

import java.nio.ByteBuffer;

public class SampleXmlParser {
	public final static int TYPE_NONE = 0x00;
	public final static int TYPE_OPEN = 0x01;
	public final static int TYPE_CLOSE = 0x02;

	private int mTypeLast = TYPE_NONE;
	public SampleXmlParser() {

	}

	private boolean skipBar(ByteBuffer b) {
		int p;

		p = b.position();
		for ( ; ; ) {
			switch (b.get()) {
				case '\r':
				case '\t':
				case '\n':
				case ' ':
					p ++;
					break;

				default:
					break out;
			}
		}
out:
		b.position(p);
		return true;
	}

	private boolean charMatch(byte dot, String table) {
		int s = 0;
		boolean neg = true;

		if (s >= table.length() ||
			table.charAt(s++) != '[')
			return false;

		if (s < table.length() &&
			table.charAt(s) == '^') {
			neg = false;
			s++;
		}

		while (s < table.length() && table.charAt(s) != ']') {
			String sub = table.substring(s, 3);
			if (sub.equals("A-Z")) {
				if (dot >= 'A' && dot <= 'Z')
					return neg;
				s += 3;
			} else if (sub.equals("a-z")) {
				if (dot >= 'a' && dot <= 'z')
					return neg;
				s += 3;
			} else if (sub.equals("0-9")) {
				if (dot >= '0' && dot <= '9')
					return neg;
				s += 3;
			} else {
				if (table.charAt(s) == dot)
					return neg;
				s++;
			}
		}

		return !neg;
	}

	private boolean skipName(ByteBuffer b) {
		int p;
		byte n;

		do
			n = b.get();
		while (charMatch(n, "[A-Za-z0-9:]"));

		p = b.position() - 1;
		b.position(p);
		return true;
	}

	private boolean skipAttr(ByteBuffer b) {
		int p;

		do {
			int bar;

			do bar = b.get();
			while (charMatch(bar, "[A-Za-z0-9:= ]"));

			if (bar == '\"' || bar == '\'') {
				while (b.get() != bar);
			} else {
				break;
			}
		} while (1);

		p = b.position() - 1;
		b.position(p);
		return true;
	}

	private boolean skipDeclaration(ByteBuffer b) {
		int p;

		p = b.position();
		if (b.get() != '<'
			|| b.get() != '?') {
			b.reset();
			return true;
		}

		if (skipName(b) &&
			skipAttr(b) && skipBar(b)) {
			if (b.get() == '?' && b.get() =='>')
				return true;
		}

		b.position(p);
		return false;
	}

	private boolean skipTagBegin(ByteBuffer b) {
		Byte n;
		skipBar(b);

		mTypeLast = TYPE_NONE;
		if (b.get() != '<') {
			b.reset();
			return false;
		}

		if (skipName(b) &&
			skipAttr(b) && skipBar(b)) {
			switch (b.get()) {
				case '/':
					if (b.get() == '>') {
						mTypeLast = TYPE_OPEN;
						return true;
					}
					break;

				case '>':
					mTypeLast = TYPE_CLOSE;
					return true;

				default:
					break;
			}
		}

		return false;
	}

	public boolean skipTagContent(ByteBuffer b) {
		skipBar(b);
		skipTagBegin(b);

		if (mTypeLast == TYPE_OPEN) {
			skipText(b);

			while (b.hasRemaining() && !isTagEnd(b)) {
				if (!skipTagContent(b))
					return false;
				skipText(b);
			}

			if (isTagEnd(b)) {
				skipTagEnd(b);
				return true;
			}
		}

		return false;
	}
}
