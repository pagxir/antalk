package com.zhuri.net;

import java.net.*;
import java.nio.*;
import java.util.List;
import java.util.ArrayList;
import com.zhuri.slot.*;
import java.nio.channels.*;

import java.io.IOException;
import com.zhuri.net.Connector;
import com.zhuri.net.WaitableSslChannel;

class UPnPData {
	List<String> mKeys = new ArrayList<String>();
	List<String> mValuess = new ArrayList<String>();

	public int count() {
		return mKeys.size();
	}

	public void add(String key, String value) {
		mKeys.add(key);
		mValuess.add(value);
	}

	public String key(int index) {
		return mKeys.get(index);
	}

	public String value(int index) {
		return mValuess.get(index);
	}
}

public class UPnpControler extends SlotWait {

	final static String UPNP_SEARCH_ACTION = 
		"M-SEARCH * HTTP/1.1\r\n" +
		"HOST: 239.255.255.250:1900\r\n" +
		"MAN: \"ssdp:discover\"\r\n" +
		"MX: 5\r\n" +
		"ST: %s\r\n" +
		"\r\n";
	
	final static String[] UPNP_DEVICES_NAME = {
		"urn:schemas-upnp-org:service:WANPPPConnection:1",
		"urn:schemas-upnp-org:service:WANIPConnection:1",
		"urn:schemas-upnp-org:device:InternetGatewayDevice:1",
		"UPnP:rootdevice"
	};

	final static String UPNP_REQUEST_HEADER =
		"POST %s HTTP/1.1\r\n" +
		"HOST: %s%s\r\n" +
		"Content-Length: %d\r\n" +
		"Content-Type: text/xml; charset=\"utf-8\"\r\n" +
		"Connection: close\r\n" +
		"SOAPACTION: %s#%s\r\n";

	final static String UPNP_REQUEST_CONTENT = 
		"<s:Envelope\r\n" +
		"    xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
		"    s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n" +
		"  <s:Body>\r\n" +
		"    <u:%s xmlns:u=\"%s\">\r\n" +
		"%s" +
		"    </u:%s>\r\n" +
		"  </s:Body>\r\n" +
		"</s:Envelope>\r\n";

	private String mUrl = "";
	private String mRequest = "";
	private String mScheme  = UPNP_DEVICES_NAME[1];
	private Connector mConnector = new Connector();
	private ByteBuffer mResponse = ByteBuffer.allocate(80000);

	public UPnpControler(Runnable runnable) {
		super(runnable);
	}

	public void start(String[] params) {
		UPnPData pnpData = new UPnPData();

		if (params.length > 3) {
			mUrl = params[1];
			mScheme = (params[2].equals("")? UPNP_DEVICES_NAME[1]: params[2]);
			for (int i = 4; i < params.length; i++) {
				String[] pairs = params[i].split("=", 2);
				if (pairs.length == 2
						&& !pairs[0].equals(""))
					pnpData.add(pairs[0], pairs[1]);
			}

			pnpRequest(params[3], pnpData);
		}

		return;
	}

	public int getExternalIPAddress() {
		UPnPData pnpData = new UPnPData();
		return pnpRequest("GetExternalIPAddress", pnpData);
	}

	public int getGenericPortMappingEntry(int index) {
		UPnPData pnpData = new UPnPData();
		pnpData.add("PortMappingIndex", String.valueOf(index));
		return pnpRequest("GetGenericPortMappingEntry", pnpData);
	}

	public int deletePortMapping(String remoteHost, String externalPort, String protocol) {
		UPnPData pnpData = new UPnPData();
		pnpData.add("RemoteHost", remoteHost);
		pnpData.add("ExternalPort", externalPort);
		pnpData.add("Protocol", protocol);
		return pnpRequest("DeletePortMapping", pnpData);
	}

	public int addPortMapping(String remoteHost, String externalPort,
			String protocol, String internalPort, String internalClient,
			String enabled, String description, String leaseDuration) {
		UPnPData pnpData = new UPnPData();
		pnpData.add("RemoteHost", remoteHost);
		pnpData.add("ExternalPort", externalPort);
		pnpData.add("Protocol", protocol);
		pnpData.add("InternalPort", internalPort);
		pnpData.add("InternalClient", internalClient);
		pnpData.add("Enabled", enabled);
		pnpData.add("PortMappingDescription", description);
		pnpData.add("LeaseDuration", leaseDuration);
		return pnpRequest("AddPortMapping", pnpData);
	}

	public int pnpRequest(String action, UPnPData param) {
		int port = -1;
		String path = null;
		String host = null;

		try {
			URL url = new URL(mUrl);
			path = url.getPath();
			host = url.getHost();
			port = url.getPort();
		} catch (MalformedURLException e) {
			e.printStackTrace();
			path = "/upnpcontorl/";
			host = "192.168.1.1";
			port = -1;
		}

		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < param.count(); i++) {
			builder.append("	<New" + param.key(i) + ">");
			builder.append(param.value(i));
			builder.append("</New" + param.key(i) + ">\r\n");
		}

		String content = String.format(UPNP_REQUEST_CONTENT, action, mScheme, builder.toString(), action);
		mRequest = String.format(UPNP_REQUEST_HEADER, path, host, port == -1? "": ":" + port, content.length(), mScheme, action) + "\r\n" + content;

		mConnector.connect(port == -1? host + ":80": host + ":" + port);
		mConnector.waitO(mWaitOut);
		mConnector.waitI(mWaitIn);
		return 0;
	}

	public String getResponse() {
		byte[] arr = mResponse.array();
		return new String(arr, 0, mResponse.position());
	}

	private SlotWait mWaitIn = new SlotWait() {
		public void invoke() {
			long count = -1;

			try {
				count = mConnector.read(mResponse);
			} catch (IOException e) {
				UPnpControler.this.schedule();
				e.printStackTrace();
				return;
			}

			if (count == -1) {
				UPnpControler.this.schedule();
				mWaitIn.clear();
				return;
			}

			mConnector.waitI(mWaitIn);
			return;
		}
	};

	private SlotWait mWaitOut = new SlotWait() {
		public void invoke() {
			long count;
			ByteBuffer buffer = ByteBuffer.wrap(mRequest.getBytes());

			try {
				System.out.println("sending http header\r\n" + mRequest);
				count = mConnector.write(buffer);
			} catch (IOException e) {
				UPnpControler.this.schedule();
				e.printStackTrace();
			}

			return;
		}
	};

	public void close() {
		try {
			mConnector.close();
		} catch (IOException e) {
			e.printStackTrace();
		};
		mWaitOut.clean();
		mWaitIn.clean();
	}
}
