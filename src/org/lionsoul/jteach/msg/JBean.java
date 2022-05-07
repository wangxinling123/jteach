package org.lionsoul.jteach.msg;

import org.lionsoul.jteach.server.JServer;
import org.lionsoul.jteach.util.JCmdTools;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Client Bean.
 *
 * @author chenxin<chenxin619315@gmail.com>
 */
public class JBean {

	private final Socket socket;
	private volatile boolean closed;
	private final DataOutputStream output;
	private final DataInputStream input;

	private String name;
	private String addr;

	private long lastReadAt = 0;

	/* message read/send blocking queue */
	private final BlockingDeque<Packet> sendPool;
	private final BlockingDeque<Packet> readPool;

	public JBean(Socket s) throws IOException {
		if (s == null) {
			throw new NullPointerException();
		}

		this.socket = s;
		// this.socket.setTcpNoDelay(true);
		this.socket.setSoTimeout(JCmdTools.SO_TIMEOUT);
		this.name = socket.getInetAddress().getHostName();
		this.addr = socket.getInetAddress().getHostAddress();
		this.output = new DataOutputStream(socket.getOutputStream());
		this.input  = new DataInputStream(socket.getInputStream());

		/* create the message pool */
		this.sendPool = new LinkedBlockingDeque(12);
		this.readPool = new LinkedBlockingDeque(12);
	}

	public void start() {
		JServer.threadPool.execute(new ReadTask());
		JServer.threadPool.execute(new SendTask());
		JServer.threadPool.execute(new HeartBeartTask());
	}

	public void stop() {
		clear();
		sendPool.notify();
	}

	public long getLastReadAt() {
		return lastReadAt;
	}

	public long getLastReadFromNow() {
		final long now = System.currentTimeMillis();
		return now - lastReadAt;
	}

	public boolean isClosed() {
		return closed;
	}

	public String getName() {
		return name;
	}

	public String getAddr() {
		return addr;
	}

	public Socket getSocket() {
		return socket;
	}


	/** send a message immediately */
	public void send(Packet p) throws IOException, IllegalAccessException {
		if (isClosed()) {
			throw new IllegalAccessException("socket closed exception");
		}

		synchronized (output) {
			output.write(p.encode());
			output.flush();
		}
	}

	/** wait until the message push to the queue */
	public void put(Packet p) throws IllegalAccessException {
		if (isClosed()) {
			throw new IllegalAccessException("socket closed exception");
		}

		try {
			sendPool.put(p);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/** offer or throw an exception for the message */
	public boolean offer(Packet p) throws IllegalAccessException {
		if (isClosed()) {
			throw new IllegalAccessException("socket closed exception");
		}

		return sendPool.offer(p);
	}


	/* read a packet */
	private Packet _read() throws IOException, IllegalAccessException {
		if (isClosed()) {
			throw new IllegalAccessException("socket closed exception");
		}

		synchronized (input) {
			socket.setSoTimeout(0);
			final byte symbol = input.readByte();
			final byte attr = input.readByte();

			// update the last read at
			lastReadAt = System.currentTimeMillis();
			// if (!JCmdTools.validSymbol(symbol)) {
			// 	throw new UnknownSymbolException("unknown symbol " + symbol);
			// }

			// check and parse the command
			final int cmd;
			if ((attr & Packet.HAS_CMD) == 0) {
				cmd = JCmdTools.COMMAND_NULL;
			} else {
				cmd = input.readInt();
			}

			// check and receive the data
			final byte[] data;
			if ((attr & Packet.HAS_DATA) == 0) {
				data = null;
			} else {
				/* the length of the data */
				final int dLen = input.readInt();

				/* read the byte data into the buffer
				 * cause cannot read all the data by once when the data is large */
				data = new byte[dLen];
				int rLen = 0;
				while (rLen < dLen) {
					final int size = input.read(data, rLen, dLen - rLen);
					if (size > 0) {
						rLen += size;
					} else {
						break;
					}
				}
			}

			return new Packet(symbol, cmd, data);
		}
	}

	/** read the first packet */
	public Packet poll() throws IllegalAccessException {
		if (isClosed()) {
			throw new IllegalAccessException("socket closed exception");
		}

		return readPool.poll();
	}

	/** take or wait for the first Packet */
	public Packet take() throws InterruptedException, IllegalAccessException {
		if (isClosed()) {
			throw new IllegalAccessException("socket closed exception");
		}

		return readPool.take();
	}

	/** Message read thread */
	private class ReadTask implements Runnable {
		@Override
		public void run() {
			while (true) {
				if (closed) {
					System.out.printf("%s: client %s read thread closed\n", JBean.this.getClass().getName(), getName());
					break;
				}

				try {
					final Packet p = _read();

					/* check and dispatch the specified packet */
					if (p.isSymbol(JCmdTools.SYMBOL_SEND_ARP)) {
						// task level heartbeat
						continue;
					} else if (p.isSymbol(JCmdTools.SYMBOL_SEND_HBT)) {
						// global heartbeat and try to extend the last active at
						// @Note: already done it in the #_read
						System.out.printf("%s: heartbeat received\n", JBean.this.getClass().getName());
						continue;
					}

					readPool.put(p);
				} catch (SocketTimeoutException e) {
					System.out.printf("%s: client %s read aborted count due to %s\n", JBean.this.getClass().getName(), name, e.getClass().getName());
				} catch (IOException e) {
					System.out.printf("%s: client %s went offline due to read %s\n", JBean.this.getClass().getName(), name, e.getClass().getName());
					clear();
					break;
				} catch (IllegalAccessException e) {
					reportClosedError();
					break;
				} catch (InterruptedException e) {
					System.out.printf("%s: read interrupted of client %s\n", JBean.this.getClass().getName(), name);
				}
			}
		}
	}

	/** data send thread */
	private class SendTask implements Runnable {
		@Override
		public void run() {
			while (true) {
				if (closed) {
					System.out.printf("%s: client %s send thread closed\n", JBean.this.getClass().getName(), getName());
					break;
				}

				try {
					final Packet p = sendPool.take();
					/* lock the socket and send the message data */
					synchronized (output) {
						output.write(p.encode());
						output.flush();
					}
				} catch (InterruptedException e) {
					System.out.printf("%s: client %s send pool take were interrupted\n",
							JBean.this.getClass().getName(), name);
				} catch (IOException e) {
					System.out.printf("%s: client %s went offline due to send %s\n",
							JBean.this.getClass().getName(), name, e.getClass().getName());
					clear();
					break;
				}
			}
		}
	}

	/** Heartbeat thread */
	private class HeartBeartTask implements Runnable {
		@Override
		public void run() {
			while (true) {
				if (closed) {
					System.out.printf("%s: client %s heartbeat thread stopped", JBean.this.getClass().getName(), getName());
					break;
				}

				try {
					Thread.sleep(JCmdTools.SO_TIMEOUT);
				} catch (InterruptedException e) {
					// Ignore the interrupted
				}

				try {
					synchronized (output) {
						output.write(Packet.HEARTBEAT.encode());
						output.flush();
					}
				} catch (IOException e) {
					System.out.printf("%s: client %s went offline due to send %s\n",
							JBean.this.getClass().getName(), name, e.getClass().getName());
					clear();
					break;
				}
			}
		}
	}

	public void clear() {
		try {
			input.close();
			output.close();
			socket.close();
		} catch (IOException e) {}

		closed = true;
		readPool.clear();
		sendPool.clear();
	}
	
	public void reportClosedError() {
		System.out.printf("%s: client %s closed\n", this.getClass().getName(), name);
	}

	public String toString() {
		return "IP:" + addr + ", HOST:" + name;
	}

}