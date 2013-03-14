package wave.slot;

import java.net.*;
import java.util.*;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import wave.util.InetUtil;

public class SRVlookup {

	class DNSDatagram {
		short ident;
		short flags = 0x100;
		short qdcount = 0x0;
		short ancount = 0x0;
		short nscount = 0x0;
		short arcount = 0x0;
		ByteBuffer qdbuffer = ByteBuffer.allocate(1024);

		public ByteBuffer getBytes() {
			Random r = new Random();
			qdbuffer.flip();

			ByteBuffer buffer = ByteBuffer.allocate(qdbuffer.limit() + 12);
			packupNumber(buffer, (short)r.nextInt());
			packupNumber(buffer, flags);
			packupNumber(buffer, qdcount);
			packupNumber(buffer, ancount);
			packupNumber(buffer, nscount);
			packupNumber(buffer, arcount);
			buffer.put(qdbuffer);
			r = null;

			buffer.flip();
			return buffer;
		}

		public void packupQuestion(String name, short type, short _class) {
			packupName(qdbuffer, name);
			packupNumber(qdbuffer, type);
			packupNumber(qdbuffer, _class);
			qdcount++;
		}

		private void packupNumber(ByteBuffer buffer, short count) {
			buffer.put((byte)(count >> 8));
			buffer.put((byte)(count >> 0));
		}

		private void packupName(ByteBuffer buffer, String domain) {
			int count = 0;
			int offset = 0;
			byte[] wrapName = domain.getBytes();

			for (byte dot: wrapName) {
				if (dot != '.') {
					count++;
					continue;
				}

				if (count > 0) {
					buffer.put((byte)(count));
					buffer.put(wrapName, offset, count);
				}

				offset += (count + 1);
				count = 0;
			}

			if (count > 0) {
				buffer.put((byte)(count));
				buffer.put(wrapName, offset, count);
			}

			buffer.put((byte)0);
		}
	}

	String dnsDomain = "";
	String dnsServer = "8.8.8.8";
	DnsSrvRecord srvRecord = null;

	private void parsePacket(ByteBuffer buffer) {
		short qdcount, arcount, nscount, ancount;
		buffer.order(ByteOrder.BIG_ENDIAN);
		int ident = buffer.getShort();
		int flags = buffer.getShort();

		System.out.println("ident: " + ident);
		System.out.println("flags: " + flags);

		qdcount = buffer.getShort();
		ancount = buffer.getShort();
		nscount = buffer.getShort();
		arcount = buffer.getShort();

		for (int i = 0; i < qdcount; i++)
			new DnsQuestion().loadFrom(buffer);

		for (int i = 0; i < ancount; i++) {
			DnsRecord record = new DnsRecord().loadFrom(buffer);
			if (record.dnsType == 33 && dnsDomain.equals(record.dnsDomain)) {
				DnsSrvRecord srv = new DnsSrvRecord().loadFrom(ByteBuffer.wrap(record.dnsData));
				if (srvRecord == null) {
					srvRecord = srv;
					continue;
				}

				if (srvRecord.srvPriority != srv.srvPriority) {
					srvRecord = srvRecord.srvPriority > srv.srvPriority? srv: srvRecord;
					continue;
				}


				if (srvRecord.srvWeight < srv.srvWeight) {
					srvRecord = srv;
					continue;
				}
			}
		}

		/* only parse answer part, so set nscount, arcount to zero. */
		nscount = arcount = 0;

		for (int i = 0; i < nscount; i++)
			new DnsRecord().loadFrom(buffer);

		for (int i = 0; i < arcount; i++)
			new DnsRecord().loadFrom(buffer);

		return;
	}

	static String loadName(ByteBuffer buffer) {
		int count = buffer.get();
		String split = "";
		String domain = "";

		while (count != 0) {
			if (count <= 0 || count >= 63) {
				int offset, mark;
				offset = (count & 63) * 256 + buffer.get();
				mark = buffer.position();
				buffer.position(offset);
				domain += loadName(buffer);
				buffer.position(mark);
				return domain;
			}

			byte[] data = new byte[count];
			buffer.get(data);
			domain += (split + new String(data));
			count = buffer.get();
			split = ".";
		}

		return domain;
	}

	class DnsQuestion {
		public int dnsType;
		public int dnsClass;
		public String dnsDomain;

		public DnsQuestion loadFrom(ByteBuffer buffer) {
			dnsDomain = loadName(buffer);
			dnsType  = buffer.getShort();
			dnsClass = buffer.getShort();
			return this;
		}
	}

	class DnsRecord extends DnsQuestion {
		public int dnsTTL;
		public int dnsLen;
		public byte[] dnsData;
		
		public DnsRecord loadFrom(ByteBuffer buffer) {
			super.loadFrom(buffer);
			dnsTTL = buffer.getInt();
			dnsLen = buffer.getShort();
			dnsData = new byte[dnsLen];
			buffer.get(dnsData);
			return this;
		}
	}

	public class DnsSrvRecord {
		public int srvPort;
		public int srvWeight;
		public int srvPriority;
		public String srvDomain;

		public DnsSrvRecord loadFrom(ByteBuffer buffer) {
			buffer.order(ByteOrder.BIG_ENDIAN);
			srvPriority = buffer.getShort();
			srvWeight = buffer.getShort();
			srvPort = buffer.getShort();
			srvDomain = loadName(buffer);
			return this;
		}
	}

	int lastCounter = 0;
	ByteBuffer lastBuffer = null;
	SocketAddress lastAddress = null;
	private void delayResend(ByteBuffer dd, SocketAddress sa) {
		lastBuffer = dd;
		lastCounter = 3;
		lastAddress = sa;
		SlotTimer.reset(resender, 1000);
	}

	private void doResend() {
		int count = 0;

		if (lastCounter-- > 0) {
			try {
				lastBuffer.reset();
				count = ds.send(lastBuffer, lastAddress);
			} catch (Exception e) {
				e.printStackTrace();
			}
			SlotTimer.reset(resender, 1000);
			return;
		}

		resender.cancel();
		/* */
		return;
	}

	SlotWait resender = new SlotWait() {
		public void invoke() {
			doResend();
		}
	};

	public DnsSrvRecord lookup(String domain, int type, int _clss, SlotWait respWait) throws Exception {
		int count;
		SocketAddress sa = null;
		SocketAddress sa1 = null;
		DNSDatagram dd = new DNSDatagram();

		this.dnsDomain = domain;
		dd.packupQuestion(domain, (short)type, (short)_clss);

		ByteBuffer dnsbuf = dd.getBytes();
		dnsbuf.mark();

		String[] localDnsServers = {"192.168.0.1", "192.168.1.1"};
		if (respWait.completed()) {
			ByteBuffer packet = ByteBuffer.allocate(2048);

			try {
				/* annou exception */
				sa1 = ds.receive(packet);
				packet.flip();
			} catch (Exception e) {
				e.printStackTrace();				
			}
			
			if (sa1 != null) {
				srvRecord = null;
				resender.cancel();
				parsePacket(packet);
				if (srvRecord == null)
					throw new Exception("Server Not Found.");
				return srvRecord;
			}
		}
		
		if (at == false) {
			ch.attach(ds);
			at = true;
		}
		
		for (String dnsServer: localDnsServers) {
			sa = InetUtil.getInetSocketAddress(dnsServer, 53);
			try {
				count = ds.send((ByteBuffer)dnsbuf.reset(), sa);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		sa = InetUtil.getInetSocketAddress(dnsServer, 53);
		count = ds.send((ByteBuffer)dnsbuf.reset(), sa);
		delayResend(dnsbuf, sa);
		
		respWait.clear();
		respWait.cancel();
		ch.wantIn(respWait);
		return null;
	}

	boolean at = false;
	SlotChannel ch = null;
	DatagramChannel ds = null;

	public SRVlookup(String server) {
		ch = new SlotChannel();
		this.dnsServer = server;

		try {
			ds = DatagramChannel.open();
			ds.socket().setReceiveBufferSize(2048);
			ds.socket().bind(null);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
}

