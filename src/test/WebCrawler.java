package test;

import java.net.*;
import java.nio.*;
import com.zhuri.slot.*;
import java.nio.channels.*;

public class WebCrawler {
	SlotChannel mSlotChannel;
	SocketChannel socketChannel;

	SlotWait mReadBlock = new SlotWait() {
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

	SlotWait mWriteBlock = new SlotWait() {
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

	public WebCrawler() throws Exception {
		mSlotChannel = new SlotChannel();
		socketChannel = SocketChannel.open();

		mSlotChannel.attach(socketChannel);
	}

	SlotTimer mTimer = new SlotTimer() {
		public void invoke() {
			System.out.println("SlotTimer.invoke");
			reset(3000);
		}
	};

	public void start() {
		InetAddress address;

		mTimer.reset(100);
		try {
			mSlotChannel.wantIn(mReadBlock);
			mSlotChannel.wantOut(mWriteBlock);
			address = InetAddress.getByName("www.baidu.com");
			socketChannel.connect(new InetSocketAddress(address, 80));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void close() {
		mSlotChannel.detach();
		mWriteBlock.clean();
		mReadBlock.clean();
	}
}

