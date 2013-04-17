package test;

import java.net.*;
import java.nio.*;
import com.zhuri.slot.*;
import java.nio.channels.*;
import com.zhuri.ssl.EmotionSSLChannel;

public class WebCrawler {
	SocketChannel socketChannel;
	EmotionSSLChannel mEmotionSSLChannel;

	SlotWait mReadBlock = new SlotWait() {
		public void invoke() {
			long count = -1;
			System.out.println("read event");
			try {
				ByteBuffer buffer = ByteBuffer.allocate(8000);
				count = mEmotionSSLChannel.read(buffer);
				System.out.println(new String(buffer.array(), 0, (int)count));
			} catch (Exception e) {
				close();
				return;
			}

			if (count != -1) {
				mEmotionSSLChannel.selectIn(mReadBlock);
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
				mEmotionSSLChannel.write(buffer);
			} catch (Exception e) {
				e.printStackTrace();
				close();
			}
		}
	};

	SlotWait mConnBlock = new SlotWait() {
		public void invoke() {
			try {
				System.out.println("Conntion block is OK");
				socketChannel.finishConnect();
				mEmotionSSLChannel.handshake();
				mEmotionSSLChannel.selectIn(mReadBlock);
				mEmotionSSLChannel.selectOut(mWriteBlock);
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		}
	};

	public WebCrawler() throws Exception {
		socketChannel = SocketChannel.open();
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
			address = InetAddress.getByName("pwd.tcl-ta.com");
			mEmotionSSLChannel = new EmotionSSLChannel(socketChannel);
			mEmotionSSLChannel.selectOut(mConnBlock);
			socketChannel.connect(new InetSocketAddress(address, 443));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void close() {
		mWriteBlock.clean();
		mReadBlock.clean();
	}
}

