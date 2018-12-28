/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.decodethread;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import android.os.Handler;
import android.os.Looper;

import com.google.zxing.DecodeHintType;
import com.google.zxing.color.Classifier;

import edu.cuhk.ie.authbarcodescanner.android.ScannerFragment;
import edu.cuhk.ie.authbarcodescanner.android.ScannerFragmentHandler;
import edu.cuhk.ie.authbarcodescanner.android.camera.CameraManager;



/**
 * Perform the actual decoding in a new thread. All messages from this thread should be returned to the handler 
 * @author solon li
 */

public final class DecodeThread extends Thread {
	
	private final CameraManager camManager;	
	private Map<DecodeHintType,Object> hints;
	private final ScannerFragmentHandler msgHandler;
	
	private DecodeThreadHandler decodeHandler;
	private final CountDownLatch handlerLatch;
	private Classifier colorClassifier;
	
	// context
	private ScannerFragment activity;
	private int indicator;
	
	public DecodeThread(CameraManager camManager, Map<DecodeHintType,Object> hints, ScannerFragmentHandler handler, ScannerFragment activity){
		this.camManager=camManager;
		this.hints=hints;
		this.msgHandler=handler;
		this.handlerLatch = new CountDownLatch(1);
		this.activity = activity;
		this.colorClassifier=activity.getColorClassifier();
	}

	public Handler getHandler() {
	    try {
	    	handlerLatch.await();
	    } catch (InterruptedException ie) {
	      // continue?
	    }
	    return decodeHandler;
	}
	
	public void setHints(Map<DecodeHintType,Object> hints){
		try {
	    	handlerLatch.await();
	    } catch (InterruptedException ie) {
	      // continue?
	    }
		this.hints=hints;
	    decodeHandler.setHints(hints);
	}
	
	public void setColor(int indicator){
		this.indicator=indicator;
		try {
	    	handlerLatch.await();
	    } catch (InterruptedException ie) {
	      // continue?
	    }		
	    decodeHandler.colorIndicator=indicator;
	}
	@Override
	public void run() {
		Looper.prepare();
		decodeHandler = new DecodeThreadHandler(msgHandler, camManager, hints, activity, indicator, colorClassifier);
		handlerLatch.countDown();
	    Looper.loop();
	}
}