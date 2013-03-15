package com.zhuri.talk;

import com.zhuri.talk.protocol.Jabber;
import com.zhuri.talk.STUNPingPong;
import com.zhuri.talk.DatagramPingPong;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import com.zhuri.talk.protocol.Jabber;

public class ProtoPlugcan {

	public static interface IProtoPlugin {
		public void input(Jabber talk, Element packet, Object object);
	}

	public static class ProtoPlugin {
		Object object;
		String prefix;
		ProtoPlugin next;
		ProtoPlugin prev;
		IProtoPlugin callback;

		public ProtoPlugin(String prefix) {
			this.next = null;
			this.prev = null;
			this.prefix = prefix;
			this.callback = null;
		}

		public ProtoPlugin(String prefix, IProtoPlugin callback) {
			this.next = null;
			this.prev = null;
			this.prefix = prefix;
			this.callback = callback;
		}

		public void input(Jabber talk, Element packet) {
			callback.input(talk, packet, object);
			return;
		}
	}

	private ProtoPlugin header;
	public ProtoPlugcan() {
		this.header = new ProtoPlugin("phony");
        registerPlugin(new DatagramPingPong());
	}

	public void registerPlugin(ProtoPlugin plugin) {
		plugin.next = header.next;
		if (plugin.next != null)
			plugin.next.prev = plugin;
		plugin.prev = header;
		header.next = plugin;
	}

	public void unregisterPlugin(ProtoPlugin plugin) {
		if (plugin.next != null)
			plugin.next.prev = plugin.prev;
		plugin.prev.next = plugin.next;
	}

	/* step1: apply filter. */
	/* step2: dispatch query. */
	/* step3: apply hook. */
	public boolean input(Jabber talk, Element packet) {
		Node query;
		String prefix = "no:use";
		ProtoPlugin plugin = header.next;

		query = packet.getFirstChild();
		System.out.println("ProtoPlugin.input ");
		if (query != null && query == packet.getLastChild()) {
			prefix = query.getNamespaceURI();
			if (prefix != null) {
			System.out.println("prefix " + prefix + " " + ((Element)query).getTagName());
				while (plugin != null) {
					if (prefix.equals(plugin.prefix)) {
						plugin.input(talk, packet);
						return true;
					}
					plugin = plugin.next;
				}
			}
		}
		
		return false;
	}

	public static ProtoPlugcan getInstance() {
		return instance;
	}

	static ProtoPlugcan instance = new ProtoPlugcan();
};

