package org.lionsoul.jteach.client.task;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.SocketTimeoutException;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.lionsoul.jteach.client.JClient;
import org.lionsoul.jteach.msg.JBean;
import org.lionsoul.jteach.msg.Packet;
import org.lionsoul.jteach.msg.ScreenMessage;
import org.lionsoul.jteach.util.JCmdTools;
import org.lionsoul.jteach.util.JTeachIcon;


/**
 * Task for Broadcast Receive when server started the Broadcast Send Thread
 * 
 * @author chenxin<chenxin619315@gmail.com>
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
	private final ImageJPanel imgJPanel;
	private final JBean bean;
	private volatile ScreenMessage screen = null;

	public SBRTask(JClient client) {
		this.setTitle(title);
		this.setUndecorated(true);
		this.setAlwaysOnTop(true);
		//this.setExtendedState(JFrame.MAXIMIZED_BOTH);
		this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		this.setSize(JClient.SCREEN_SIZE);
		this.setResizable(false);
		this.setLayout(new BorderLayout());
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				/* stop the JCTask and dispose the window */
				//stopCTask();
				//_dispose();
			}
		});

		this.bean = client.getBean();
		imgJPanel = new ImageJPanel();
		getContentPane().add(imgJPanel, BorderLayout.CENTER);
	}
	
	/**
	 * Remote BufferedImage show JPanel.
	 * paint the BufferedImage Load from the socket.
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

			/* Draw the waiting typo */
			if ( screen == null ) {
				g.setColor(Color.WHITE);
				g.setFont(IFONT);
				FontMetrics m = getFontMetrics(IFONT);
				g.drawString(EMTPY_INFO, (getWidth() - m.stringWidth(EMTPY_INFO))/2, getHeight()/2);
				return;
			}
			
			if ( IMG_SIZE == null ) {
				BIT = Math.max(
					(float)screen.img.getWidth()/JClient.SCREEN_SIZE.width,
					(float)screen.img.getHeight()/JClient.SCREEN_SIZE.height
				);
			}
			
			/* Draw the image */
			final int dst_w = getWidth();
			final int dst_h = getHeight();
			final BufferedImage img = JTeachIcon.resize_2(screen.img, dst_w, dst_h);
			g.drawImage(img, 0, 0, dst_w, dst_h, null);

			/* Draw the Mouse */
			g.drawImage(MOUSE_CURSOR, (int)(screen.mouse.x/BIT), (int) (screen.mouse.y/BIT), null);
		}
	}
	
	private void repaintImageJPanel() {
		SwingUtilities.invokeLater(() -> imgJPanel.repaint());
	}
	
	/** dispose the JFrame */
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
		while ( getTSTATUS() == T_RUN ) {
			try {
				final Packet p = bean.read();

				/* Check the symbol type */
				if (p.symbol == JCmdTools.SYMBOL_SEND_CMD) {
					if (p.cmd == JCmdTools.COMMAND_TASK_STOP) {
						System.out.printf("Task %s is overed by stop command\n", this.getClass().getName());
						break;
					}
					System.out.printf("Ignore command %d\n", p.cmd);
					continue;
				} else if (p.symbol != JCmdTools.SYMBOL_SEND_DATA) {
					System.out.printf("Ignore symbol %s\n", p.symbol);
					continue;
				}

				/* decode the packet to the ScreenMessage */
				try {
					screen = ScreenMessage.decode(p);
				} catch (IOException e) {
					System.out.printf("failed to decode the screen message");
					continue;
				}

				/* repaint the ImageJPanel */
				repaintImageJPanel();
			} catch (SocketTimeoutException e) {
				System.out.printf("Task %s read timeout\n", this.getClass().getName());
			} catch (IOException e) {
				System.out.printf("Task %s is overed by %s\n", getClass().getName(), e.getClass().getName());
				bean.clear();
				break;
			} catch (IllegalAccessException e) {
				e.printStackTrace();
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
