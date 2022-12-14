package com.webssky.jteach.client.task;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
//import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.webssky.jteach.client.JCWriter;
import com.webssky.jteach.client.JClient;
import com.webssky.jteach.util.JCmdTools;
import com.webssky.jteach.util.JTeachIcon;


/**
 * Task for Broadcast Receive when server started the
 * 		Broadcast Send Thread <br />
 * 
 * @author chenxin <br />
 */
public class SBRTask extends JFrame implements JCTaskInterface {

	private static final long serialVersionUID = 1L;

	/* Lang package */
	public static final String title = "JTeach - Remote Window";
	public static final String EMTPY_INFO = "Loading Image Resource From Server";
	public static final Font IFONT = new Font("Arial", Font.BOLD, 18);
	public static Image MOUSE_CURSOR = JTeachIcon.Create("m_pen.png").getImage();
	public static float BIT = 1;
	public static Dimension IMG_SIZE = null;

	private int TStatus = T_RUN;
	private volatile Point MOUSE_POS = null;
	private volatile BufferedImage B_IMG = null;
	private final ImageJPanel imgJPanel;

	public SBRTask() {
		this.setTitle(title);
		this.setUndecorated(true);
		this.setAlwaysOnTop(true);
		//this.setExtendedState(JFrame.MAXIMIZED_BOTH);
		this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		this.setSize(JClient.SCREEN_SIZE);
		this.setResizable(false);
		setLayout(new BorderLayout());
		imgJPanel = new ImageJPanel();
		getContentPane().add(imgJPanel, BorderLayout.CENTER);
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				/*
				 * stop the JCTask and dispose the window 
				 */
				//stopCTask();
				//_dispose();
			}
		});
	}
	
	/**
	 * Remote BufferedImage show JPanel. <br />
	 * 		paint the BufferedImage Load from the socket. <br />
	 * 
	 * @author chenxin
	 */
	private class ImageJPanel extends JPanel {
		
		private static final long serialVersionUID = 1L;

		public ImageJPanel() {}
		
		@Override
		public void update(Graphics g) {
			paintComponent(g);
		}
		
		@Override
		protected void paintComponent(Graphics g) {
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, getWidth(), getHeight());
			if ( B_IMG == null ) {
				g.setColor(Color.WHITE);
				g.setFont(IFONT);
				FontMetrics m = getFontMetrics(IFONT);
				g.drawString(EMTPY_INFO, (getWidth() - m.stringWidth(EMTPY_INFO))/2, getHeight()/2);
				return;
			}
			
			if ( IMG_SIZE == null ) {
				BIT = Math.max((float)B_IMG.getWidth()/JClient.SCREEN_SIZE.width,
					(float)B_IMG.getHeight()/JClient.SCREEN_SIZE.height);
			}
			
			/*
			 * Draw the image from server
			 * start from point IMG_POS with size IMG_SIZE 
			 */
			// final int w = getWidth(), h = getHeight();
			// final int dst_w = (int) Math.ceil(w * 0.96);
			// final int dst_h = (int) Math.ceil(h * 0.96);
			final int dst_w = getWidth();
			final int dst_h = getHeight();
			final BufferedImage img = JTeachIcon.resize_2(B_IMG, dst_w, dst_h);
			g.drawImage(img, 0, 0, dst_w, dst_h, null);

			/*Draw the Mouse*/
			g.drawImage(MOUSE_CURSOR, (int) (MOUSE_POS.x / BIT),
					(int) ( MOUSE_POS.y / BIT), null);
		}
	}
	
	private void repaintImageJPanel() {
		SwingUtilities.invokeLater(() -> imgJPanel.repaint());
	}
	
	/**
	 * dispose the JFrame 
	 */
	public void _dispose() {
		this.setVisible(false);
		//dispose();
	}

	@Override
	public void startCTask(String...args) {
		JClient.getInstance().setTipInfo("Broadcast Thread Is Working");
		JClient.threadPool.execute(this);
		SwingUtilities.invokeLater(() -> {
			setVisible(true);
			requestFocus();
		});
	}

	@Override
	public void stopCTask() {
		setTSTATUS(T_STOP);
		//_dispose();
	}

	@Override
	public void run() {
		DataInputStream reader = JClient.getInstance().getReader();
		final JCWriter writer = new JCWriter();
		while ( getTSTATUS() == T_RUN ) {
			try {
				// send the heartbeat packet
				writer.send(JCmdTools.SEND_HBT_SYMBOL);
				final char symbol = reader.readChar();

				/*
				 * Check the symbol type
				 * case SEND_CMD_SYMBOL, then stop the current thread
				 * case SEND_IMG_SYMBOL, then receive the image data from server
				 * 		get the image size then get the image
				 */
				if (symbol == JCmdTools.SEND_CMD_SYMBOL) {
					int cmd = reader.readInt();
					if (cmd == JCmdTools.SERVER_TASK_STOP_CMD) {
						System.out.printf("Task %s is overed by stop command\n", this.getClass().getName());
						break;
					}
				} else if (symbol != JCmdTools.SEND_DATA_SYMBOL) {
					System.out.printf("Ignore %s symbol\n", symbol);
					continue;
				}

				/*load the mouse location information */
				MOUSE_POS = new Point(reader.readInt(), reader.readInt());

				/* the size of the BufferedImage */
				int imgSize = reader.readInt();

				/*
				 * the BufferedImage byte data
				 * read the byte data into the buffer
				 * cause cannot read all the data by once when the image is large
				 */
				byte buffer[] = new byte[imgSize];
				int length = 0;
				while (length < imgSize) {
					final int rSize = reader.read(buffer, length, imgSize - length);
					if (rSize > 0) {
						length += rSize;
					} else {
						break;
					}
				}

				/*turn the byte data to a BufferedImage */
				final ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
				//ZipInputStream zis = new ZipInputStream(bis);
				//zis.getNextEntry();
				B_IMG = ImageIO.read(bis);

				/* repaint the ImageJPanel */
				repaintImageJPanel();
			} catch (SocketTimeoutException e) {
				System.out.printf("Task %s read timeout\n", this.getClass().getName());
			} catch (EOFException e) {
			} catch (Exception e) {
				JClient.getInstance().offLineClear();
				System.out.printf("Task %s is overed by %s\n", getClass().getName(), e.getClass().getName());
				break;
			}
		}
		
		//dispose the JFrame
		_dispose();
		JClient.getInstance().resetJCTask();
		JClient.getInstance().notifyCmdMonitor();
		JClient.getInstance().setTipInfo("Broadcast Thread Is Overed!");
	}
	
	public synchronized void setTSTATUS(int s) {
		TStatus = s;
	}
	
	public synchronized int getTSTATUS() {
		return TStatus;
	}

}
