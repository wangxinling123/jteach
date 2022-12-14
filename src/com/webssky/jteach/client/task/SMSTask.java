package com.webssky.jteach.client.task;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import com.webssky.jteach.client.JCWriter;
import com.webssky.jteach.client.JClient;
import com.webssky.jteach.util.JCmdTools;
import com.webssky.jteach.util.JTeachIcon;


/**
 * Image send thread for Screen monitor. <br />
 * 
 * @author chenxin - chenxin619315@gmail.com
 */
public class SMSTask implements JCTaskInterface {
	
	public static final Rectangle SCREEN_RECT = new Rectangle(
			JClient.SCREEN_SIZE.width, JClient.SCREEN_SIZE.height);

	private int TStatus = T_RUN;
	private Robot robot = null;
	private JCWriter writer = null;
	
	public SMSTask() {
		try {
			robot = new Robot();
		} catch (AWTException e) {
			JOptionPane.showMessageDialog(null, "Fail to create Robot Object",
					"JTeach:", JOptionPane.ERROR_MESSAGE);
		}
		
		writer = new JCWriter();
	}

	@Override
	public void startCTask(String...args) {
		JClient.threadPool.execute(this);
		JClient.getInstance().setTipInfo("Screen Monitor Thread Is Working.");
	}

	@Override
	public void stopCTask() {
		setTStatus(T_STOP);
		JClient.getInstance().setTipInfo("Screen Monitor Thread Is Overed.");
	}
	
	@Override
	public void run() {
		BufferedImage S_IMG = null, I_BAK = null;
		Point mouse = null;
		byte[] data = null;
		while ( getTStatus() == T_RUN ) {
			try {
				/**get the screen image*/
				S_IMG = robot.createScreenCapture(SCREEN_RECT);

				if ( I_BAK == null ) {
					I_BAK = S_IMG;
				} else if (JTeachIcon.ImageEquals(I_BAK, S_IMG) ) {
					continue;
				}
				
				/**mouse location information*/
				mouse = MouseInfo.getPointerInfo().getLocation();
				
				/**
				 * encode the Screen Image and
				 * store them in byte[] 
				 */
				ByteArrayOutputStream ais = new ByteArrayOutputStream();
				try {
					// JPEGCodec.createJPEGEncoder(ais).encode(S_IMG);
					ImageIO.write(S_IMG, "jpeg", ais);
					data = ais.toByteArray();
					ais.flush();
				} catch (IOException e) {
					continue;
				}
				
				/**
				 * send the image byte data to server 
				 */
				writer.send(JCmdTools.SEND_DATA_SYMBOL, mouse.x, mouse.y, data.length, data);
				
				// reset the image
				I_BAK = S_IMG;
			} catch (IOException e) {
				JClient.getInstance().offLineClear();
				break;
			}
		}
	}
	
	public synchronized void setTStatus(int t) {
		TStatus = t;
	}
	
	public synchronized  int getTStatus() {
		return TStatus;
	}

}
