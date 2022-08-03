package org.lionsoul.jteach.server.task;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import javax.swing.JFileChooser;

import org.lionsoul.jteach.log.Log;
import org.lionsoul.jteach.msg.FileInfoMessage;
import org.lionsoul.jteach.msg.Packet;
import org.lionsoul.jteach.msg.JBean;
import org.lionsoul.jteach.server.JServer;
import org.lionsoul.jteach.util.CmdUtil;


/**
 * list all the online JBeans
 * @author chenxin - chenxin619315@gmail.com
 */
public class UFTask extends JSTaskBase {
	
	public static final Log log = Log.getLogger(UFTask.class);
	public static final int POINT_LENGTH = 60;

	private File selectedFile;
	private BufferedInputStream bis;

	public UFTask(JServer server) {
		super(server);
	}

	@Override
	public boolean _before() {
		if (beanList.size() == 0) {
			server.println("empty client list");
			return false;
		}

		final JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setMultiSelectionEnabled(false);
		final int _result = chooser.showOpenDialog(null);
		if ( _result != JFileChooser.APPROVE_OPTION ) {
			server.stopJSTask();
			return false;
		}

		// create the input stream
		selectedFile = chooser.getSelectedFile();
		try {
			bis = new BufferedInputStream(new FileInputStream(selectedFile));
		} catch (FileNotFoundException e) {
			return false;
		}

		final Packet p;
		try {
			p = new FileInfoMessage(selectedFile.length(), selectedFile.getName()).encode();
		} catch (IOException e) {
			server.println(log.getError("failed to encode the file info message {%s, %d}",
					selectedFile.getName(), selectedFile.length()));
			return false;
		}

		/* inform all the beans the information of the file name and size */
		synchronized (beanList) {
			final Iterator<JBean> it = beanList.iterator();
			while (it.hasNext()) {
				final JBean b = it.next();
				try {
					b.offer(Packet.COMMAND_UPLOAD_START);
					b.offer(p);
				} catch (IllegalAccessException e) {
					b.reportClosedError();
					it.remove();
				}
			}
		}

		return true;
	}

	@Override
	public void _run() {
		server.println("File Information:");
		server.println("-+---name: %s", selectedFile.getName());
		server.println("-+---size: %dKiB - %d", selectedFile.length()/1024, selectedFile.length());
		server.println("sending file %s ... ", selectedFile.getAbsolutePath());

		try {
			/*
			 * read b.length byte from the buffer InputStream
			 * then send the byte[] to all the JBeans
			 * till all the bytes were sent out
			 */
			double readLen = 0;
			int counter = 0, len = 0;
			byte b[] = new byte[1024* CmdUtil.FILE_UPLOAD_ONCE_SIZE];
			while ( (len = bis.read(b, 0, b.length)) > 0 ) {
				readLen += len;
				counter++;

				final Packet dp = Packet.valueOf(b, 0, len);

				// do the chunked data send
				boolean checkSize = false;
				synchronized (beanList) {
					final Iterator<JBean> it = beanList.iterator();
					while (it.hasNext()) {
						final JBean bean = it.next();
						try {
							boolean r = bean.offer(dp, JBean.DEFAULT_OFFER_TIMEOUT_SECS, TimeUnit.SECONDS);
							if (r == false) {
								server.println("client %s removed due to offer timeout");
								it.remove();
							}
						} catch (IllegalAccessException | InterruptedException e) {
							checkSize = true;
							server.println("client %s removed due to %s: %s",
									bean.getHost(), e.getClass().getName(), e.getMessage());
							it.remove();
						}
					}
				}

				// check the bean list size
				if (checkSize && beanList.size() == 0) {
					server.println("task is overed due to empty client list");
					break;
				}


				/* file transmission progress bar */
				if ( counter % POINT_LENGTH == 0 ) {
					server.println("%dKiB - %f%%", (int)(readLen/1024), readLen/selectedFile.length()*100);
					counter = 0;
				} else if ( readLen == selectedFile.length() ) {
					server.println("%dKiB - 100%%", (int)(readLen/1024));
				} else {
					server.print(".");
				}

				if ( getStatus() != T_RUN ) {
					break;
				}
			}
			server.println("file send completed");
			bis.close();
		} catch (IOException e) {
			server.println(log.getError("aborted due to %s", e.getClass().getName()));
		}

		// check and close the buffer input stream
		if ( bis != null ) {
			try {
				bis.close();
			} catch (IOException e) {
			}
		}
	}
	
}
