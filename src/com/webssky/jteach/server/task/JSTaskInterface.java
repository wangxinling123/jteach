package com.webssky.jteach.server.task;

import com.webssky.jteach.server.JBean;

/**
 * Task Interface for JTeach Server
 * @author chenxin - chenxin619315@gmail.com
 */
public interface JSTaskInterface {
	
	public static final int T_RUN = 1;
	public static final int T_STOP = 0;
	
	/**
	 * start the working Task 
	 */
	public void startTask();

	/* add a new client */
	public void addClient(JBean bean);

	/**
	 * stop the working Task 
	 */
	public void stopTask();
}
