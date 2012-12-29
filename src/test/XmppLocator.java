package test;

import java.io.*;
import java.net.*;
import java.nio.*;
import wave.slot.*;
import java.nio.channels.*;

public class XmppLocator {
	SlotWait mReadBlock;
	SlotWait mWriteBlock;
	SlotChannel mSlotChannel;
	SocketChannel socketChannel;

	public XmppLocator() throws Exception {
		mSlotChannel = new SlotChannel();
		socketChannel = SocketChannel.open();

		mSlotChannel.attach(socketChannel);
		mReadBlock = new SlotWait() {
			public void invoke() {
				long count = -1;
				System.out.println("read event");
				try {
					ByteBuffer buffer = ByteBuffer.allocate(8000);
					count = socketChannel.read(buffer);
					System.out.println(new String(buffer.array(), 0, (int)count));
				} catch (Exception e) {
					close();
					return;
				}

				if (count != -1) {
					mSlotChannel.wantIn(mReadBlock);
					return;
				}

				System.out.println("stream is close");
				close();
				return;
			}
		};

		mWriteBlock = new SlotWait() {
			public void invoke() {
				System.out.println("write event");
				try {
					String header = "GET / HTTP/1.0\r\n\r\n";
					ByteBuffer buffer = ByteBuffer.wrap(header.getBytes());
					socketChannel.finishConnect();
					socketChannel.write(buffer);
				} catch (Exception e) {
					e.printStackTrace();
					close();
				}
			}
		};

	}

	public void start() {
		try {
			InetAddress address;
			mSlotChannel.wantIn(mReadBlock);
			mSlotChannel.wantOut(mWriteBlock);
			address = InetAddress.getByName("www.baidu.com");
			socketChannel.connect(new InetSocketAddress(address, 80));
		} catch (Exception e) {
		}
	}

	public void close() {
		mSlotChannel.detach();
		mWriteBlock.clean();
		mReadBlock.clean();
	}
}

