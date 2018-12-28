/*
 Copyright (C) 2015 Marco in MobiTeC, CUHK 
 */
package edu.cuhk.ie.authbarcodescanner.android;


public class Log implements com.google.zxing.LogCallback{
	private static boolean DEBUG_MODE = false;
	
	// log debug message
	public static void d(String tag, String msg) {
		if (DEBUG_MODE) {
			android.util.Log.d(tag, msg);
		}
	}
	
	// log error message
	public static void e(String tag, String msg) {
		if (DEBUG_MODE) {
			android.util.Log.e(tag, msg);
		}		
	}
	
	// log info message
	public static void i(String tag, String msg) {
		if (DEBUG_MODE) {
			android.util.Log.i(tag, msg);
		}		
	}
	
	// log verbose message
	public static void v(String tag, String msg) {
		if (DEBUG_MODE) {
			android.util.Log.v(tag, msg);		
		}
	}
	
	// log warning message
	public static void w(String tag, String msg) {
		if (DEBUG_MODE) {
			android.util.Log.w(tag, msg);		
		}
	}
	
	// log what a terrible failure message
	public static void wtf(String tag, String msg) {
		if (DEBUG_MODE) {
			android.util.Log.wtf(tag, msg);		
		}
	}

	private static java.util.List<Long> previousLoggingTime = new java.util.ArrayList<Long>();
	@Override
	public void LogMsg(String msg, boolean isShowTimeDiff, int level){
		if(level >10) return;
		if(DEBUG_MODE){
			while(previousLoggingTime.size() <=level){
				previousLoggingTime.add((long) 0);
			}
			long lastTime=previousLoggingTime.get(level);
			previousLoggingTime.set(level, System.currentTimeMillis());
			String suffix = (isShowTimeDiff)? " Duration from last core log in the same function level :"+(previousLoggingTime.get(level)-lastTime)
					: " Current time:"+previousLoggingTime.get(level);					
			android.util.Log.d("core", msg+suffix);			
		}
	}
	public void LogMsg(String msg, boolean isShowTimeDiff){
		LogMsg(msg,isShowTimeDiff,0);
	}
}
