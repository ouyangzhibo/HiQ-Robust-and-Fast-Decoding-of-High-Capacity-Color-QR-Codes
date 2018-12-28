/*
 Copyright (C) 2014 Ken in MobiTeC, CUHK 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class HelpWebView extends WebView {

    private GestureDetector gestureDetector;
    private AtomicBoolean mPreventAction = new AtomicBoolean(false);
    private AtomicLong mPreventActionTime = new AtomicLong(0);

    public HelpWebView(Context context) {
        super(context);
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public HelpWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public HelpWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    @SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	public HelpWebView(Context context, AttributeSet attrs, int defStyle, boolean privateBrowsing) {
        super(context, attrs, defStyle, privateBrowsing);
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
    	int index = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        int pointId = event.getPointerId(index);
        HelpWebView mWebView = (HelpWebView) findViewById(R.id.result_content_webview);
        if(mWebView ==null) return super.onTouchEvent(event);
    	WebSettings settings = mWebView.getSettings(); 
    	
    	if (event.getPointerCount() > 1) {
    	    //Log.d("TAP","Multitouch event"); 
    	    settings.setSupportZoom(true);
    		settings.setBuiltInZoomControls(true);
    	} else {
    	    // Single touch event
    	    //Log.d("TAP","Single touch event"); 
    	    settings.setSupportZoom(false);
    		settings.setBuiltInZoomControls(false);
    	}
    	
     // just use one(first) finger, prevent double tap with two and more fingers
        if (pointId == 0){
            gestureDetector.onTouchEvent(event);

            if (mPreventAction.get()){
                if (System.currentTimeMillis() - mPreventActionTime.get() > ViewConfiguration.getDoubleTapTimeout()){
                    mPreventAction.set(false);
                } else {
                	mPreventAction.set(false);
                	Log.d("TAP","Douple tap"); 
                	settings.setSupportZoom(true);
            		settings.setBuiltInZoomControls(true);
                }
            }
            return super.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
        	mPreventAction.set(true);
            mPreventActionTime.set(System.currentTimeMillis());
            return super.onDoubleTap(e);
        }
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            mPreventAction.set(true);
            mPreventActionTime.set(System.currentTimeMillis());
            return super.onDoubleTapEvent(e);
        }
    }
}
