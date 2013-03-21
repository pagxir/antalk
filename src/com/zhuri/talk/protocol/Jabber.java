package com.zhuri.talk.protocol;

import com.zhuri.ssl.*;
import com.zhuri.slot.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.security.*;
import javax.net.ssl.*;
import java.nio.*;
import org.w3c.dom.*;
import java.nio.channels.*;
import com.zhuri.talk.PacketCallback;
import java.util.logging.*;
import com.zhuri.talk.protocol.FastXmlVisitor;
import com.zhuri.util.InetUtil;
import com.zhuri.talk.ProtoPlugcan;

public class Jabber implements Runnable {
	public boolean disconnected = false;
	public String  messageTitle = "not connected";
	private SlotWait dnsWait = new SlotWait(this);
	private SlotWait readWait = new SlotWait(this);
	private SlotWait writeWait = new SlotWait(this);
	private static Logger logger = Logger.getLogger("jabber");

	final static String auth = "<auth mechanism='PLAIN' xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>";
	final static String binder = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><resource>#RESOURCE#</resource></bind>";
	final static String session = "<session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>";
	final static String starttls = "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";

	public boolean isLogining() {
		if (disconnected)
			return false;
		return (bgflags & WF_PRESENCE) == 0;
	}

	public void useTLS(boolean usetls) {
		if (usetls == true) {
			uiflags |= WF_ENABLETLS;
		} else {
			uiflags &= ~WF_ENABLETLS;
		}
		return;
	}

	private void aborted(Exception e) {
		logger.log(Level.SEVERE, "aborted", e);
		bgflags |= WF_DISCONNECT;
		disconnected = true;
		stateCallback.run();
		destroy();
	}

	private void disconnect(String msg) {
		logger.log(Level.SEVERE, "disconnected " + msg);
		bgflags |= WF_DISCONNECT;
		disconnected = true;
		stateCallback.run();
		destroy();
	}

	String plainSaslAuth(String user, String password) {
		byte[] buf;
		String data;
		ByteBuffer buffer = ByteBuffer.allocate(1000);

		buffer.put((byte)0x0);
		buffer.put(user.getBytes());
		buffer.put((byte)0x0);
		buffer.put(password.getBytes());
		buffer.flip();
		buffer.get(buf = new byte[buffer.limit()]);

		//data = Base64.encodeToString(buf, 0);
		data = com.zhuri.util.Base64Codec.encode(buf);
		return auth + data + "</auth>";
	}

	String openStream(String domain) {
		String header = "<stream:stream to='#DOMAIN#' xmlns='jabber:client' "
			+ "xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>";
		return header.replaceFirst("#DOMAIN#", domain);
	}

	SocketChannel channel = null;
	MySSLChannel  sslwrapper = null;

	XmlParser parser = new XmlParser();
	SlotChannel chanslot = new SlotChannel();

	public void run() {
		if ((bgflags & WF_DESTROY) == 0) {
			if (!matchFlags(WF_DESTROY, WF_DISCONNECT)) {
				try {
					dispatchEvent();
				} catch (Exception e) {
					aborted(e);
				}
				return;
			}
			destroy();
		}
	}

	public boolean putPacket(String text) {
		int count = 0;
		ByteBuffer buffer;
		buffer = ByteBuffer.wrap(text.getBytes());

		try {
			count = sslwrapper.write(buffer);
		} catch (Exception e) {
			aborted(e);
		}

		logger.log(Level.INFO, "send " + text);
		return (count > 0);
	}

	private int bgflags = 0;
	private int uiflags = 0;
	private SlotWait bgWait = new SlotWait(this);
	private SlotWait bindWait = new SlotWait(this);
	private SlotWait sessionWait = new SlotWait(this);
	private SlotAsync uiAsync = new SlotAsync(this);

	public static final int SET = 0x01;
	public static final int GET = 0x02;

	final static int WF_RESOLV = 0x00000001;
	final static int WF_HEADER = 0x00000002;
	final static int WF_FEATURE = 0x00000004;
	final static int WF_PROCEED = 0x00000008;
	final static int WF_SUCCESS  = 0x00000010;
	final static int WF_STARTTLS = 0x00000020;
	final static int WF_QUERY1ST = 0x00000040;
	final static int WF_CONNECTED = 0x00000080;
	final static int WF_HANDSHAKE = 0x00000100;
	final static int WF_PLAINSASL = 0x00000200;
	final static int WF_LOGINSTEP9 = 0x00000400;

	final static int WF_BINDER = 0x00000800;
	final static int WF_DESTROY = 0x00020000;
	final static int WF_SESSION = 0x00001000;
	final static int WF_PRESENCE = 0x00002000;
	final static int WF_CONFIGURE = 0x00004000;
	final static int WF_CONNECTING = 0x00008000;
	final static int WF_DISCONNECT = 0x00010000;
	final static int WF_FORCETLS   = 0x10000000;
	final static int WF_ENABLETLS  = 0x20000000;

	private boolean matchFlags(int nxt, int prev) {
		int flags = (bgflags| uiflags);
		flags &= (nxt| prev);
		return (flags == prev);
	}

	private String selfjid = "";
	public String getSelfJid() {
		if (!bindWait.completed())
			return "no-jid-yet";
		return selfjid;
	}

	private void resetSession() {
		parser.reset();
		chanslot.wantIn(readWait);
		chanslot.wantOut(writeWait);
		bgflags &= ~(WF_HEADER| WF_FEATURE);
	}

	boolean stateChanged() {
		logger.log(Level.INFO, "stateChanged " + uiflags + " " + bgflags);
		uiAsync.send();
		return true;
	}

	boolean loginSuccess(Element packet) {
		disconnected = packet.getTagName().equals("failure");
		assert(disconnected || packet.getTagName().equals("success"));
		return packet.getTagName().equals("success");
	}

	private void parseSelfJid(Element result) {
		FastXmlVisitor visitor = new FastXmlVisitor(result);
		selfjid = visitor.getElement("bind").getElement("jid").getValue();
		return;
	}

	private void lookupServer(String domain) throws Exception {
		SRVlookup.DnsSrvRecord record = null;
		String example = "_xmpp-client._tcp." + domain;

		logger.log(Level.INFO, "lookup " + example);
		record = locator.lookup(example, 33, 1, dnsWait);
		if (record == null) {
			logger.log(Level.INFO, "waiting dns lookup.");
			return;
		}

		loginServer = record.srvDomain;
		if (loginServer == null) {
			bgflags |= WF_DISCONNECT;
			return;
		}

		return;
	}

	private boolean dispatchEvent() throws Exception {
		logger.log(Level.INFO, "dispatchEvent invoke ");

		if (matchFlags(WF_RESOLV, WF_CONFIGURE)) {
			messageTitle = "finding server";
			if (loginServer == null)
				lookupServer(loginDomain);
			if (loginServer != null)
				bgflags |= WF_RESOLV;
		}

		if (matchFlags(WF_CONNECTING, WF_RESOLV)) {
			chanslot.attach(channel);
			messageTitle = "connect server";
			InetAddress addr = InetAddress.getByName(loginServer);
			sslwrapper = new MySSLChannel(channel);
			channel.connect(new InetSocketAddress(addr, 5222));
			logger.log(Level.INFO, "connect", loginServer);
			chanslot.wantOut(writeWait);
			SlotTimer.reset(timeo, 45000);
			bgflags |= WF_CONNECTING;
		}

		if (matchFlags(WF_CONNECTED, WF_CONNECTING)) {
			if (writeWait.completed()) {
				channel.finishConnect();
				bgflags |= WF_CONNECTED;
			}
		}

		if (matchFlags(WF_HEADER, WF_CONNECTED)) {
			if (writeWait.completed() &&
					putPacket(openStream(loginDomain))) {
				chanslot.wantIn(readWait);
				bgflags |= WF_HEADER;
			}
		}

		if (matchFlags(WF_FEATURE, WF_HEADER)) {
			if (readWait.completed() &&
					parser.readPacket(sslwrapper)) {
				bgflags |= WF_FEATURE;
			} else if (!parser.atEOF()) {
				readWait.clear();
				chanslot.wantIn(readWait);
			} else {
				String msg = "need feature but EOF.";
				disconnect(msg);
			}
		}

		if (matchFlags(WF_STARTTLS, WF_FEATURE)) {
			if (writeWait.completed()) {
				if ((uiflags & WF_ENABLETLS) == WF_ENABLETLS) {
					bgflags |= WF_STARTTLS;
					putPacket(starttls);
				} else {
					messageTitle = "disable secure connect";
					bgflags |= WF_HANDSHAKE;
				}
				chanslot.wantIn(readWait);
			}
		}

		if (matchFlags(WF_PROCEED, WF_STARTTLS)) {
			messageTitle = "est secure connect";
			if (readWait.completed() &&
					parser.readPacket(sslwrapper)) {
				bgflags |= WF_PROCEED;
			} else if (!parser.atEOF()) {
				readWait.clear();
				chanslot.wantIn(readWait);
			} else {
				String msg = "need proceed but EOF.";
				disconnect(msg);
			}
		}

		if (matchFlags(WF_HANDSHAKE, WF_PROCEED)) {
			if (!sslwrapper.doHandshake()) {
				logger.log(Level.INFO, "TLS", "hand shaking");
				chanslot.wantIn(readWait);
				return false;
			}

			resetSession();
			writeWait.cancel();
			writeWait.schedule();
			bgflags |= WF_HANDSHAKE;
			return false;
		}

		if (matchFlags(WF_PLAINSASL, WF_FEATURE| WF_HANDSHAKE)) {
			if (writeWait.completed()) {
				putPacket(plainSaslAuth(loginName, loginPassword));
				chanslot.wantIn(readWait);
				bgflags |= WF_PLAINSASL;
			}
		}

		if (matchFlags(WF_SUCCESS, WF_PLAINSASL)) {
			if (readWait.completed() &&
					parser.readPacket(sslwrapper)) {
				if (loginSuccess(parser.packet())) {
					messageTitle = "auth success";
					bgflags |= WF_SUCCESS;
				} else {
					disconnect("login failure");
					messageTitle = "auth failure";
				}
			} else if (!parser.atEOF()) {
				readWait.clear();
				chanslot.wantIn(readWait);
			} else {
				String msg = "need success but EOF";
				disconnect(msg);
			}
		}

		if (matchFlags(WF_LOGINSTEP9, WF_SUCCESS)) {
			bgflags |= WF_LOGINSTEP9;
			resetSession();
		}

		if (matchFlags(WF_QUERY1ST, WF_LOGINSTEP9| WF_FEATURE)) {
			String qstr = binder.replaceFirst("#RESOURCE#", loginResource);
			putQuery(Jabber.SET, null, qstr, bindWait);
			putQuery(Jabber.SET, null, session, sessionWait);
			bgflags |= WF_QUERY1ST;
		}

		if (matchFlags(WF_PRESENCE, WF_QUERY1ST) && 
				bindWait.completed() && sessionWait.completed()) {
			parseSelfJid((Element)bindWait.result);
			stateCallback.run();
			putPacket("<presence/>");
			bgflags |= WF_PRESENCE;
			messageTitle = "finish login";
		}

		if (matchFlags(WF_DISCONNECT, WF_QUERY1ST)) {
			if (parser.packet() == null)
				parser.readPacket(sslwrapper);

			Element packet = parser.packet();
			while (packet != null) {
				logger.log(Level.INFO, "receive " + FastXmlVisitor.fastFormat(packet));
				if (packet.getTagName().equals("iq")) {
					finishQuery(packet);
					parser.clear();
				} else if (packet.getTagName().equals("presence")) {
					presenceCallback.receive(packet);
					parser.clear();
				} else if (packet.getTagName().equals("message")) {
					messageCallback.receive(packet);
					parser.clear();
				} else {
					logger.log(Level.WARNING, "unexpected tag", packet.getTagName());
					parser.clear();
				}

				parser.readPacket(sslwrapper);
				packet = parser.packet();
			}

			chanslot.wantIn(readWait);
		}

		return true;
	}

	public Jabber()  {
		try {
			channel = SocketChannel.open();
		} catch (Exception e) {
			aborted(e);
		}
	}

	private String loginName;
	private String loginPassword;

	private String loginServer;
	private String loginDomain;
	private String loginResource = "wire";
	/* 8.8.8.8 198.153.194.1 198.153.192.1 208.67.222.222  208.67.220.220 */
	SRVlookup locator = new SRVlookup("198.153.192.1");

	public void login(String user, String password, String server) {
		loginName = user;
		loginServer = server;
		loginPassword = password;

		int dotIndex = user.indexOf('@');
		if (dotIndex > 0) {
			loginName = user.substring(0, dotIndex);
			loginDomain = user.substring(dotIndex + 1);
		}

		int dotIndex1 = user.indexOf('/');
		if (dotIndex1 > dotIndex) {
			/* TODO: Check dotIndex1 is valid. */
			loginResource = user.substring(dotIndex1 + 1);
			if (dotIndex > 0)
				loginDomain = user.substring(dotIndex + 1, dotIndex1);
		}

		uiflags |= WF_CONFIGURE;
		stateChanged();
	}

	SlotWait timeo = new SlotWait() {
		public void invoke() {
			if ((bgflags & WF_QUERY1ST) == 0) {
				logger.log(Level.INFO, "login", "failure: timeout");
				disconnect("timeout");
			} else {
				logger.log(Level.INFO, "keepalive", "watchdog");
				SlotTimer.reset(timeo, 180000);
				putPacket(" ");
			}
		}
	};

	private boolean destroy() {

		if (matchFlags(WF_DESTROY, WF_DISCONNECT)) {
			bgflags |= WF_DESTROY;

			try {
				if (sslwrapper != null)
					sslwrapper.close();
				chanslot.detach();
				channel.close();
			} catch (Exception e) {
				aborted(e);
			}

			sessionWait.clean();
			writeWait.clean();
			readWait.clean();
			bindWait.clean();
			dnsWait.clean();
			uiAsync.clean();
			bgWait.clean();
			timeo.clean();
		}

		return true;
	}

	public synchronized void close() {
		uiflags |= WF_DISCONNECT;
		stateChanged();
	}

	public void getAvatar(String target, SlotWait wait) {
		putQuery(Jabber.GET, target, "<vCard xmlns='vcard-temp'/>", wait);
		return;
	}

	public void getRoster(String target, SlotWait wait) {
		putQuery(Jabber.GET, null, "<query xmlns='jabber:iq:roster'/>", wait);
		return;
	}

	private static long queryID = 0x1982;
	private SlotSlot querySlot = new SlotSlot();
	public boolean putQuery(int type, String target, String payload, SlotWait wait) {
		String title;
		String title1 = "<iq type='@TYPE' id='@ID'>@QUERY</iq>";
		String title2 = "<iq type='@TYPE' id='@ID' to='@TARGET'>@QUERY</iq>";

		title = (target == null? title1: title2.replaceFirst("@TARGET", target));
		title = title.replaceFirst("@TYPE", type == SET? "set": "get");
		title = title.replaceFirst("@ID", String.valueOf(queryID));
		title = title.replaceFirst("@QUERY", payload);

		if (matchFlags(WF_DISCONNECT, WF_LOGINSTEP9| WF_FEATURE)) {
			try {
				putPacket(title);
				if (wait == null)
					return true;
				wait.lState = queryID;
				synchronized(querySlot) { querySlot.record(wait); queryID++; }
			} catch (Exception e) {
				aborted(e);
				return false;
			}
		}

		return true;
	}

	private final static String not_implemented_xml = 
		"<error type='cancel'>" + 
		"<feature-not-implemented xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>" + 
		"</error>";

	void feedbackError(Element packet) {
		int index;
		String strxml = "";
		String part1, part2;
		String target = packet.getAttribute("to");
		String sender = packet.getAttribute("from");
		FastXmlVisitor visitor = new FastXmlVisitor();

		packet.removeAttribute("from");
		packet.setAttribute("to", sender);
		packet.setAttribute("type", "error");

		strxml = visitor.fastFormat(packet);
		index = strxml.lastIndexOf('<');

		if (index > 0) {
			part1 = strxml.substring(0, index);
			part2 = strxml.substring(index);
			strxml = part1 + not_implemented_xml + part2;
		} else {
			RuntimeException e;
			e = new RuntimeException("bad xml packet format!");
			throw e;
		}

		putPacket(strxml);
	}

	void finishQuery(Element packet) {
		String id = packet.getAttribute("id");
		String type = packet.getAttribute("type");
		SlotWait wait = querySlot.getHeader();

		assert(packet.getTagName().equals("iq"));
		if (type.equals("set") || type.equals("get")) {
			ProtoPlugcan can = ProtoPlugcan.getInstance();
			if (!can.input(this, packet))
				feedbackError(packet);
			return;
		}

		long li = Integer.parseInt(id);
		while (wait.next != null) {
			SlotWait cur = wait.next;
			if (cur.lState == li) {
				cur.result = packet;
				cur.cancel();
				cur.schedule();
				break;
			}
			wait = wait.next;
		}
	}

	static String lastStatus = "ok";
	static Runnable callback = new Runnable() {
		public void run() {
			if (lastStatus != null)
				System.out.println(lastStatus);
		}
	};

	private static final String title1 = "<message type='chat'><body>#BODY#</body></message>";
	private static final String title2 = "<message to='#TARGET#' type='chat'><body>#BODY#</body></message>";
	public boolean sendMessage(String target, String body) {
		String title;
		title = (target == null? title1: title2.replaceFirst("#TARGET#", target));
		title = title.replaceFirst("#BODY#", body);
		return putPacket(title);
	}

	public static void setCallback(Runnable _callback) {
		Jabber.callback = _callback;
	}

	private static Jabber sticky = null;
	public static void setSticky(Jabber talker) {
		sticky = talker;
		return;
	}

	public static Jabber  getSticky() {
		Jabber talker = sticky;
		sticky = null;
		return talker;
	}

	private Runnable stateCallback;
	public void setStateListener(Runnable callback) {
		stateCallback = callback;
	}

	private PacketCallback messageCallback;
	public void setMessageListener(PacketCallback callback) {
		messageCallback = callback;
	}

	private PacketCallback presenceCallback;
	public void setPresenceListener(PacketCallback callback) {
		presenceCallback = callback;
	}
}

