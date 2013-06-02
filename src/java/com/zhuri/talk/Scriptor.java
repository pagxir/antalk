package com.zhuri.talk;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class Scriptor {
	private Map<String, ICommandInterpret> mInterprets = new HashMap<String, ICommandInterpret>();

	public interface IInvokable {
		public void invoke();
	}

	public interface ICommandInterpret {
		public IInvokable createInvoke(List<String> params);
	}

	private char charAt(String s, int p, char fail) {
		if (p < s.length())
			return s.charAt(p);
		return fail;
	}

	public IInvokable evalate(String script) {
		int off = 0;
		int old = 1;
		char bar = ' ';
		StringBuilder builder = new StringBuilder();
		List<String> mParts = new ArrayList<String>();

		bar = charAt(script, off++, ' ');
		while (off < script.length()) {
			if (Character.isSpace(bar)) {
				if (old != off)mParts.add(builder.toString());
				builder = new StringBuilder();
				bar = charAt(script, off++, ' ');
				old = off;
				continue;
			}

			if (bar == '\'' || bar == '\"') {
				char mark = bar;
				bar = charAt(script, off++, mark);
				while (bar != mark) {
					builder.append(bar);
					bar = charAt(script, off++, mark);
				}
				bar = charAt(script, off++, mark);
				continue;
			}

			do {
				builder.append(bar);
				bar = charAt(script, off++, ' ');
			} while (bar != ' ');
		}

		if (old != off)mParts.add(builder.toString());

		if (mParts.size() > 0) {
			String method = mParts.get(0);
			if (mInterprets.containsKey(method)) {
				ICommandInterpret interpret = mInterprets.get(method);
				return interpret.createInvoke(mParts);
			}
		}

		return null;
	}

	public void registerCommand(String name, ICommandInterpret interpret) {
		mInterprets.put(name, interpret);
		return;
	}
}

