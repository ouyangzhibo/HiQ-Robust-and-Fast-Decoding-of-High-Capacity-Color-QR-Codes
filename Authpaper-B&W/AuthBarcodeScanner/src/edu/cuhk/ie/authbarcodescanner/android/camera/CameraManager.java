/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.camera;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.color.Classifier;
import com.google.zxing.color.RGBColorWrapper;

import edu.cuhk.ie.authbarcodescanner.android.Log;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.os.Handler;
import android.os.Message;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;


/**
 * This class opens a camera for scanning and handle the camera preview and auto-focus.
 * It also set the size of the framing screen. 
 * Setting on the camera hardware, except orientation, is set in CameraConfigurationManager.
 * @author solon li
 *
 */
public final class CameraManager implements Camera.PreviewCallback, Camera.PictureCallback {
	private static final String TAG = CameraManager.class.getSimpleName();
	
	//Static variables on the framing part
	private static final int MIN_FRAME_WIDTH = 240;
	private static final int MIN_FRAME_HEIGHT = 240;
	private static final int MAX_FRAME_WIDTH = 1920;
	private static final int MAX_FRAME_HEIGHT = 1080;
	private static final float desiredScanningRectRatio=1;
	
	//Camera part
	private final Context context;
	private final CameraConfigurationManager configManager;
	private Camera camera;
	private boolean initialized=false;
	private Rect framingRectInPreview=null;
	private Point cameraResolution=null;
	//Preview part
	private boolean previewing=false;
	private Handler previewPreviewHandler;
	private int previewPreviewMessage;
	
	//This object is called only if if the phone does not support MeteringInterface 
	private compatibleFocusCallback focusCallback=null; 
	//These are used in manual focusing
	private Rect manualFocusArea = null;
	private int lockFocusCount=0;
	private static int lockFocusLength=5;
	
	private int takenFrameNumber=0;
	
	public CameraManager(Context context) {
		this.context = context;
		this.configManager = new CameraConfigurationManager(context);
	}
//Camera part	
	/**
	 * Return if the camera is opened
	 * @return
	 */
	public synchronized boolean isOpen() {
	    return camera != null;
	}
	/**
	 * Open a camera for the SurfaceHolder
	 * @param holder
	 * @throws IOException
	 */
	public synchronized void openDriver(SurfaceHolder holder,SurfaceView surfaceView) throws IOException {
		Camera theCamera = camera;
	    if (theCamera == null) {
	    	int rotation=((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
	    			.getDefaultDisplay().getRotation();
	    	theCamera = openCamera(rotation, true);
	    	if(theCamera == null) theCamera = openCamera(rotation, false);
	    	if (theCamera == null) {
	    		throw new IOException();
	    	}
	    	camera = theCamera;
	    }	    
	    //theCamera.setPreviewDisplay(holder);
	    if(!initialized){
	      initialized = true;
	      LayoutParams frame=surfaceView.getLayoutParams();	      
	      configManager.initFromCameraParameters(theCamera, new Point(frame.width,frame.height));
	      //setManualFramingRect(requestedFramingRectWidth, requestedFramingRectHeight);
	      Point resolution=configManager.getCameraResolution();
	      boolean isFlipSize = ((frame.width < frame.height) && (resolution.x < resolution.y))
	    		  				|| ((frame.width > frame.height) && (resolution.x > resolution.y));
	      //Note: By changing the frame, the surface is changed accordingly.
	      frame.width=(isFlipSize)? resolution.x:resolution.y;
	      frame.height=(isFlipSize)? resolution.y:resolution.x;
	    }
	    //Config the camera
	    Camera.Parameters parameters = theCamera.getParameters();
	    String defaultParas = parameters == null ? null : parameters.flatten();
	    try {
	      configManager.setDesiredCameraParameters(theCamera, false);
	    } catch (RuntimeException re) {
	    	Log(re.getMessage()+re.toString());
	      // Reset:
	      if (defaultParas != null) {
	        parameters = theCamera.getParameters();
	        parameters.unflatten(defaultParas);
	        try {
	          theCamera.setParameters(parameters);
	          configManager.setDesiredCameraParameters(theCamera, true);
	        } catch (RuntimeException re2) {
	        	Log(re2.getMessage()+re2.toString());
	          Log("Camera reject safe-mode parameters. No configuration is set");
	        }
	      }
	    }
	    theCamera.setPreviewDisplay(holder);
	}
	/**
	 * Close the opened camera
	 */
	public synchronized void closeDriver() {
		if (camera != null) {
	      camera.release();
	      camera = null;
	      // Make sure to clear these each time we close the camera, so that any scanning rect
	      // requested by intent is forgotten.
	      //framingRect = null;
	      framingRectInPreview = null;
	    }
	}
	
//Preview part	
	public synchronized void startPreview() {
	    Camera theCamera = camera;
	    if (theCamera != null && !previewing) {
	      theCamera.startPreview();
	      previewing = true;
	      if(CameraConfigurationManager.shouldCallAutoFocus(theCamera))
	    	  focusCallback = new compatibleFocusCallback();
	      else focusCallback=null;
	    }
	}
	public synchronized void stopPreview() {
	    if (camera != null && previewing) {
	      camera.stopPreview();
	      previewPreviewHandler=null;
	      previewPreviewMessage=0;
	      if(focusCallback !=null){
	    	  focusCallback.setHandler(null, 0);
	    	  focusCallback =null;
	      }
	      previewing = false;
	    }
	}
	/**
	 * Request a preview / picture and handle the result by the handler with the message what 
	 * @param handler
	 * @param what
	 * @param isPhoto if picture is taken, put a true here. If no, just leave it or put false
	 */
	public synchronized void requestPreviewFrame(Handler handler, int what, boolean... isPhoto) {
		takenFrameNumber++;
		Log.d(TAG,"Taken the "+takenFrameNumber+" frame");
	    Camera theCamera = camera;
	    if (theCamera != null && previewing) {
	    	previewPreviewHandler=handler;
	    	previewPreviewMessage=what;
	    	if(isPhoto !=null && isPhoto.length>0 && isPhoto[0]) theCamera.takePicture(null, null, this);
	    	else theCamera.setOneShotPreviewCallback(this);
	    }
	  }
	/**
	 * Preview callback function. This function should be called by the camera only 
	 */
	@Override
	public void onPreviewFrame(byte[] data, Camera camera){
		Point cameraResolution = configManager.getCameraResolution();
		int x=cameraResolution.x , y=cameraResolution.y;
		Handler thePreviewHandler = previewPreviewHandler;
		// Reduction to luminance moved to decode thread to allow reconstruction of rgb image
		//if( data !=null && data.length > (x*y) ) data=Arrays.copyOf(data, (x*y));
		if (cameraResolution != null && thePreviewHandler != null) {
			Message message = thePreviewHandler.obtainMessage(previewPreviewMessage, x, y, data);
			message.sendToTarget();
			previewPreviewHandler = null;
		} else Log("Got preview callback, but no handler or resolution available");
	}
	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		Point cameraResolution = configManager.getPictureResolution();
		Handler thePreviewHandler = previewPreviewHandler;
		Log.d(TAG,"Taken a picture with resolution : "+cameraResolution.x+","+cameraResolution.y);
		//data=Arrays.copyOf(data, (cameraResolution.x*cameraResolution.y));
		if (cameraResolution != null && thePreviewHandler != null) {
			Message message = thePreviewHandler.obtainMessage(previewPreviewMessage, cameraResolution.x,
					cameraResolution.y, data);
			message.sendToTarget();
			previewPreviewHandler = null;
		} else Log("Got picture taking callback, but no handler or resolution available");
		Toast toast = Toast.makeText(context, "Taken a new photo", Toast.LENGTH_SHORT);
		toast.show();		
		camera.startPreview();
	}

	
//Framing part
	public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
	    Rect rect = getFramingRectInPreview();
	    if( rect ==null || data.length<(width*height) ) return null;
	    return (rect !=null)? new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
	                                        rect.width(), rect.height(), false)
	    						:null;
	}
	public RGBColorWrapper buildRGBColorWrapper(int[] data, int width, int height, Classifier colorClassifier, Map<DecodeHintType,Object> hints) {
	    Rect rect = getFramingRectInPreview();
	    if( rect ==null || data.length<(width*height) ) return null;	    
		RGBColorWrapper wrapper= new RGBColorWrapper(data, width, height, rect.left, rect.top, 
				rect.width(), rect.height(), false, colorClassifier, hints);		
		Log.d(TAG, (wrapper == null)? "no color wrapper":"Has color wrapper");		
		return wrapper;
	}
	public synchronized Rect getFramingRectInPreview() {
		if (this.framingRectInPreview != null) return this.framingRectInPreview;
		Point cameraResolution = configManager.getCameraResolution();
		if (cameraResolution == null) {
		    // Called early, before init even finished			
			return null;
		}
		int width = Math.round(desiredScanningRectRatio*cameraResolution.x);
		int height = Math.round(desiredScanningRectRatio*cameraResolution.y);
		//Handle the case that the phone is hold upright
		int tmpWidth=0;
		if(width < height){
			tmpWidth=width;
			width=height;
			height=tmpWidth;
		}
		width = (width < MIN_FRAME_WIDTH)? MIN_FRAME_WIDTH :
			 (width > MAX_FRAME_WIDTH)? MAX_FRAME_WIDTH : width ;
		height = (height < MIN_FRAME_HEIGHT)? MIN_FRAME_HEIGHT : 
			 (height > MAX_FRAME_HEIGHT)? MAX_FRAME_HEIGHT : height ;
		//Switch back after limiting the frame size
		if(tmpWidth >0){
			tmpWidth=width;
			width=height;
			height=tmpWidth;
		}	
		int leftOffset = (cameraResolution.x - width) / 2;
	    int topOffset = (cameraResolution.y - height) / 2;
	    this.framingRectInPreview = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
	    this.cameraResolution = cameraResolution;
	    return framingRectInPreview;
    }
	public synchronized Point getCameraResolution(){
		return (this.cameraResolution !=null)? this.cameraResolution : configManager.getCameraResolution(); 
	}
//Other functions
	public static Camera openCamera(int rotation, boolean isRear){
		int numCameras = Camera.getNumberOfCameras();
	    if (numCameras == 0) {
	      Log("No camera found");
	      return null;
	    }	    
	    int index = 0;
	    while (index < numCameras) {
	      Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
	      Camera.getCameraInfo(index, cameraInfo);
	      if( (isRear && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) ||
			 (!isRear && cameraInfo.facing != Camera.CameraInfo.CAMERA_FACING_BACK) ){
	        break;
	      }
	      index++;
	    }	    
	    Camera camera = null;
	    if (index < numCameras) {
	      Log("Opening camera #" + index);
	      camera = Camera.open(index);
	    } else {
	      Log((isRear)? "No camera facing back;":"No camera facing front;");
	      //camera = Camera.open(0);
		  return null;
	    }
	    setCameraDisplayOrientation(rotation, index, camera);
	    return camera;
	}
	public static void setCameraDisplayOrientation(int rotation, int cameraIndex, Camera camera){
		int degrees = 0;
	    switch (rotation) {
	    	case Surface.ROTATION_0: degrees = 0; break;
	        case Surface.ROTATION_90: degrees = 90; break;
	        case Surface.ROTATION_180: degrees = 180; break;
	        case Surface.ROTATION_270: degrees = 270; break;
	    }
	    Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
	    Camera.getCameraInfo(cameraIndex, cameraInfo);
	    int result;
	    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
	    	result = (cameraInfo.orientation + degrees) % 360;
	        result = (360 - result) % 360;  // compensate the mirror
	    } else {  // back-facing
	        result = (cameraInfo.orientation - degrees + 360) % 360;
	    }
	    camera.setDisplayOrientation(result);
	}
	
	public synchronized void setTorch(boolean newSetting) {
	    if(camera !=null && newSetting != configManager.getTorchState(camera))	       
	        configManager.setTorch(camera, newSetting);	    
	}
	/**
	 * Request the camera to do a auto focus. 
	 * Note that it only works for Android 2x or 3x, For Android 4+, it will do nothing as auto-focus is already handled by MeteringInterface 
	 * @param handler
	 * @param message
	 */
	public void requestAutoFocus(Handler handler, int message){
		if(camera != null && previewing && focusCallback !=null){
	    	focusCallback.setHandler(handler, message);
	    	try {
	    		//camera.cancelAutoFocus();
	    		camera.autoFocus(focusCallback);
	    	} catch (RuntimeException re) {
	    		// Strange RuntimeException
	    		Log("Unexpected exception while focusing"+re.getMessage()+re.toString());
	    	}
	    } // If meteringInterface is used (compatibleFocusCallback==null), no need to do anything
	}
	/**
	 * Perform a one time auto focus request
	 */
	public synchronized void oneOffAutoFocus(){
		lockFocusCount=100;		
		if(focusCallback ==null){
			try{
				camera.autoFocus(new AutoFocusCallback(){
					@Override
					public void onAutoFocus(boolean success, Camera camera) {						
						lockFocusCount=lockFocusLength;
					}
				});
			}catch(RuntimeException re){
	    		// Strange RuntimeException
				lockFocusCount=lockFocusLength;
	    	}
		} //Let compatible focus handler to handle, no need to do any thing here 
	}
	/**
	 * Set the focus area of the camera, the area should be (0,0) at top left corner and (0,canvasHeight) at bottom left corner
	 * If null is passed, the previous set focus area will be used instead
	 * This function should be called by scannerFragmentHandler only
	 * @param newFocusArea
	 * @return
	 */
	public synchronized void changeFocusArea(Rect newFocusArea){
		lockFocusCount--;
		//Make sure no new focus setting request is created during resetting focus area
		if(lockFocusCount >-1 && newFocusArea ==null) return; 
		if(newFocusArea !=null && manualFocusArea !=null && 
				( newFocusArea.contains(manualFocusArea) || manualFocusArea.contains(newFocusArea) ) ){
			//Old focus area, no need to perform focus resetting again.
			if(lockFocusCount<0) oneOffAutoFocus();
			return; 
		}
		if(newFocusArea ==null && manualFocusArea ==null){
			//No suggested area, just call a auto-focus
			if(lockFocusCount<0) oneOffAutoFocus(); 
			return; 
		}
		//Now reset the focus area
		//It is OK to input null in newFocusArea, we just use the manual one
		if(newFocusArea ==null){
			newFocusArea=manualFocusArea;
		}else manualFocusArea=newFocusArea;
		Point camRes=getCameraResolution();
		if(camRes ==null || ( newFocusArea !=null && (newFocusArea.left <0 || newFocusArea.top <0 
				|| newFocusArea.right > camRes.x || newFocusArea.bottom > camRes.y) )) return;
		//When using auto focus, we need to stop it before setting the focus area
		if(camera != null && previewing && focusCallback !=null)
			camera.cancelAutoFocus();
		MeteringInterface.resetFocusArea(camera, newFocusArea, camRes.x, camRes.y);
		//Add the compatible focus callback if necessary
		oneOffAutoFocus();
	}
	public synchronized Rect getFocusArea(){
		Point camRes=getCameraResolution();
		return MeteringInterface.getFocusArea(camera, camRes.x, camRes.y);
	}
	private static void Log(String message){
		//android.util.Log.d(CameraManager.TAG,message);
	}

	/**
	 * Method to get camera details
	 */
	public CameraConfigurationManager getCamConfiManager(){
		return configManager;
	}
	
	public Camera getCamera() {
		return camera;
	}
	
}
