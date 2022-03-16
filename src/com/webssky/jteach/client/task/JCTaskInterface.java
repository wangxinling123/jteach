package com.webssky.jteach.client.task;

/**
 * Task Interface for JTeach Client
 * @author chenxin
 * {@link http://www.webssky.com} 
 */
public interface JCTaskInterface extends Runnable {
	public static final int T_RUN = 1;
	public static final int T_STOP = 0;
	
	/**
	 * start the working Task 
	 */
	public void startCTask(String...args);
	
	/**
	 * stop the working Task 
	 */
	public void stopCTask();
}
