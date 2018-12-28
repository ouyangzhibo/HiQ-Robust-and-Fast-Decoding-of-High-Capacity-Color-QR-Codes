/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.camera;

import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;

/**
 * autofocus callback if the default continuous focusing does not work
 * @author Solon Li
 *
 */
public final class compatibleFocusCallback implements Camera.AutoFocusCallback {
	private Handler autoFocusHandler;
	private int autoFocusMessage;

	void setHandler(Handler autoFocusHandler, int autoFocusMessage) {
		this.autoFocusHandler = autoFocusHandler;
	    this.autoFocusMessage = autoFocusMessage;
	}
	  
	@Override
	public void onAutoFocus(boolean success, Camera camera) {	
		if(autoFocusHandler != null){			
			Message message = autoFocusHandler.obtainMessage(autoFocusMessage, success);			
			//Log.d(TAG, "Got auto-focus callback; requesting another");
			autoFocusHandler.sendMessage(message);
			//autoFocusHandler.sendMessageDelayed(message, 1000L);//reset focus after 1 second
			autoFocusHandler = null;
	    }
	}
}