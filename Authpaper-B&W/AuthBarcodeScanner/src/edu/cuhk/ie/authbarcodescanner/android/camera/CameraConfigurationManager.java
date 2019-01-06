/*
 Copyright (C) 2014 Solon Li 
 */

package edu.cuhk.ie.authbarcodescanner.android.camera;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.WindowManager;
/**
 * A class which set the camera parameters which are used to
 * configure the camera hardware.
 */
public final class CameraConfigurationManager {
    private static final String TAG = "CameraConfigurationManager";

    // This is bigger than the size of a small screen, which is still supported. The routine
    // below will select the largest screen size which is larger than the minimum (640*480), but can be displayed on screen. 
    // This prevents accidental selection of very low resolution on some devices.
    private static final int MIN_PREVIEW_PIXELS = 640 * 480; // small screen 0.3 megapixel camera)
    //private static final int MAX_PREVIEW_PIXELS = 2048 * 1536; // large/HD screen (3 megapixel camera)
    //private static final int MAX_PREVIEW_PIXELS = 2560 * 1920; // large/HD screen (5 megapixel camera)
    private static final int MIN_PICTURE_WIDTH = 2301;
    private static final int MAX_PICTURE_PIXELS = 3264*2448; //(8 megapixel camera)
    private static final float MAX_EXPOSURE_COMPENSATION = 0.5f;
    private static final float MIN_EXPOSURE_COMPENSATION = 0.01f;
    private static final double MAX_ASPECT_DISTORTION = 0.4;
    private static final int MIN_FPS = 5;

    private final Context context;
    private boolean isDepreciatedPhone=false;
    //public boolean isDepreciated(){return isDepreciatedPhone;}
    private Point screenResolution;
    private Point cameraResolution;
    private Point pictureResolution;

    CameraConfigurationManager(Context context) {
        this.context = context;
    }

    /**
     * Reads, one time, values from the camera that are needed by the app.
     */
    public void initFromCameraParameters(Camera camera, Point theScreenResolution) { 
        if(theScreenResolution==null || theScreenResolution.x <=640 || theScreenResolution.y<=640){
        	WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
	        Display display = manager.getDefaultDisplay();
        	try{
            	display.getSize(theScreenResolution);
            } catch(NoSuchMethodError e2){
            	//If the phone is Android 3.2 or below
            	isDepreciatedPhone=true;
            	theScreenResolution = new Point(display.getWidth(),display.getHeight());
            }
        }
        screenResolution = theScreenResolution;
        //Log("Screen resolution: " + screenResolution);
        Camera.Parameters parameters = camera.getParameters();

        cameraResolution = findBestPreviewSizeValue(parameters, screenResolution);
        //Log("Camera resolution: " + cameraResolution);
    }

    @SuppressLint("NewApi")
    public void setDesiredCameraParameters(Camera camera, boolean safeMode) {
        Camera.Parameters parameters = camera.getParameters();

        if(parameters == null){
            Log("Device error: no camera parameters are available. Proceeding without configuration.");
            return;
        }
     // Log("Initial camera parameters: " + parameters.flatten());
        if(safeMode) Log("Camera config safe mode ignoring the optional settings");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        //Disable the torch by default
        doSetTorch(parameters, false);
        setBestPreviewFPS(parameters);
        String focusMode = null;
        if(safeMode){
        	focusMode = findSettableValue(parameters.getSupportedFocusModes(),
        					Camera.Parameters.FOCUS_MODE_MACRO,
                            Camera.Parameters.FOCUS_MODE_EDOF,
        					Camera.Parameters.FOCUS_MODE_AUTO);
        }else{
        	focusMode = findSettableValue(parameters.getSupportedFocusModes(),
                           ((android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH)?
                           Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO),
                           Camera.Parameters.FOCUS_MODE_MACRO,
                           Camera.Parameters.FOCUS_MODE_EDOF,
                           Camera.Parameters.FOCUS_MODE_AUTO);        	
        }        
        if (focusMode != null) parameters.setFocusMode(focusMode);
        
        if(safeMode) isDepreciatedPhone=true;
        else{
            if(prefs.getBoolean("KEY_INVERT_SCAN", false)){
                String colorMode = findSettableValue(parameters.getSupportedColorEffects(),
                		Camera.Parameters.EFFECT_NEGATIVE);
                if(colorMode != null) parameters.setColorEffect(colorMode);
            }
            if(prefs.getBoolean("KEY_BARCODE_SCENE_MODE", false)){
                String sceneMode = findSettableValue(parameters.getSupportedSceneModes(),
                		Camera.Parameters.SCENE_MODE_BARCODE);
                if(sceneMode != null) parameters.setSceneMode(sceneMode);
            }
            //By default, enable the preview stabilization
            try{
        	    if (parameters.isVideoStabilizationSupported()) {
        		    Log("Enabling video stabilization...");
        		    parameters.setVideoStabilization(true);
                    }
            }catch(NoSuchMethodError e2){ 
        	    //If not supported, just leave it
        	    isDepreciatedPhone=true;
            }     
            //Set the focus and metering (exposure measurement) area
            if(!isDepreciatedPhone){
        	    if(!MeteringInterface.setFocusArea(parameters)) isDepreciatedPhone=true;
                    MeteringInterface.setMetering(parameters);
            }
        }

        parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);

        List<Integer> supportedPicFormat = parameters.getSupportedPreviewFormats();
        //The default value is NV21, just in case it is not
        if(supportedPicFormat.contains(ImageFormat.NV21))
        	parameters.setPreviewFormat(ImageFormat.NV21);
        List<Integer> supportedPictureFormat = parameters.getSupportedPictureFormats();
        if(supportedPictureFormat.contains(ImageFormat.JPEG)) {
        	parameters.setPictureFormat(ImageFormat.JPEG);
        	//parameters.setPictureFormat(ImageFormat.NV21);
        }
        camera.setParameters(parameters);

        Camera.Parameters afterParameters = camera.getParameters();                
        List<Camera.Size> rawSupportedSizes = afterParameters.getSupportedPictureSizes();
        List<Camera.Size> fitSupportedSizes = new ArrayList<Camera.Size>();
        for(Iterator<Size> i=rawSupportedSizes.iterator(); i!=null && i.hasNext();){
        	Camera.Size newSize=i.next();
        	int h=newSize.height,w=newSize.width;
        	if(h > MIN_PICTURE_WIDTH && w > MIN_PICTURE_WIDTH && h*w <=MAX_PICTURE_PIXELS)
        		fitSupportedSizes.add(newSize);
        }
        Camera.Size afterSize = null;
        if(fitSupportedSizes.size() >0){
        	for(Iterator<Size> i=fitSupportedSizes.iterator(); i!=null && i.hasNext();){
            	Camera.Size newSize=i.next();
            	if(afterSize ==null) afterSize=newSize;
            	else{
            		if(newSize.height*newSize.width > afterSize.height*afterSize.width) 
            			afterSize=newSize;
            	}
            }
        }
        if(afterSize !=null){
        	afterParameters.setPictureSize(afterSize.width, afterSize.height);
        	camera.setParameters(afterParameters);
        } else afterSize=afterParameters.getPictureSize();
        /*if (afterSize!= null && (cameraResolution.x != afterSize.width || cameraResolution.y != afterSize.height)) {
            Log("Camera said it supported preview size " + cameraResolution.x + 'x' + cameraResolution.y +
                                 ", but after setting it, preview size is " + afterSize.width + 'x' + afterSize.height);
            cameraResolution.x = afterSize.width;
            cameraResolution.y = afterSize.height;
        }*/        
        //afterSize = afterParameters.getPictureSize();
        pictureResolution = new Point(afterSize.width, afterSize.height);
    }

    public Point getCameraResolution() {
        return cameraResolution;
    }

    public Point getScreenResolution() {
        return screenResolution;
    }
    
    public Point getPictureResolution() {
	    return pictureResolution;
    }
    
    public static boolean shouldCallAutoFocus(Camera camera){
	    if(camera ==null) return false;	    
	    String focusMode = camera.getParameters().getFocusMode();	    
	    return (Camera.Parameters.FOCUS_MODE_MACRO.compareTo(focusMode) ==0 
			    || Camera.Parameters.FLASH_MODE_AUTO.compareTo(focusMode) ==0);
    }

    public boolean getTorchState(Camera camera) {
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            if (parameters != null) {
                String flashMode = camera.getParameters().getFlashMode();
                return flashMode != null &&
                        (Camera.Parameters.FLASH_MODE_ON.equals(flashMode) ||
                         Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode));
            }
        }
        return false;
    }

    public void setTorch(Camera camera, boolean newSetting) {
        Camera.Parameters parameters = camera.getParameters();
        doSetTorch(parameters, newSetting);
        camera.setParameters(parameters);
    }

    private void doSetTorch(Camera.Parameters parameters, boolean newSetting) {
        String flashMode;
        if(newSetting){
            flashMode = findSettableValue(parameters.getSupportedFlashModes(),
            		Camera.Parameters.FLASH_MODE_TORCH,
            		Camera.Parameters.FLASH_MODE_ON);
        }else{
            flashMode = findSettableValue(parameters.getSupportedFlashModes(),
            		Camera.Parameters.FLASH_MODE_OFF);
        }
        if(flashMode != null) parameters.setFlashMode(flashMode);

        //Reset the exposure
        int minExposure = parameters.getMinExposureCompensation();
        int maxExposure = parameters.getMaxExposureCompensation();
        if (minExposure != 0 || maxExposure != 0) {
        	float step = parameters.getExposureCompensationStep();
        	int desiredCompensation;
        	if (newSetting) {
        		// Light on; set low exposure compensation
        		desiredCompensation = Math.max((int) (MIN_EXPOSURE_COMPENSATION / step), minExposure);
        	} else {
        		// Light off; set high compensation
        		desiredCompensation = Math.min((int) (MAX_EXPOSURE_COMPENSATION / step), maxExposure);
        	}
        	Log("Setting exposure compensation to " + desiredCompensation + " / " + (step * desiredCompensation));
        	parameters.setExposureCompensation(desiredCompensation);
        }else Log("Camera does not support exposure compensation");        
    }

    private static void setBestPreviewFPS(Camera.Parameters parameters) {
        // Required for Glass compatibility; also improves battery/CPU performance a tad
        List<int[]> supportedPreviewFpsRanges = parameters.getSupportedPreviewFpsRange();
        if (supportedPreviewFpsRanges != null && !supportedPreviewFpsRanges.isEmpty()) {
            int[] minimumSuitableFpsRange = null;
            for (int[] fpsRange : supportedPreviewFpsRanges) {
                int fpsMax = fpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
                if (fpsMax >= MIN_FPS * 1000 &&
                        (minimumSuitableFpsRange == null ||
                         fpsMax > minimumSuitableFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX])) {
                    minimumSuitableFpsRange = fpsRange;
                }
            }
            if (minimumSuitableFpsRange == null) {
                Log("No suitable FPS range");
            } else {
                int[] currentFpsRange = new int[2];
                parameters.getPreviewFpsRange(currentFpsRange);
                if (!Arrays.equals(currentFpsRange, minimumSuitableFpsRange)) {
                    Log("Setting FPS range to " + Arrays.toString(minimumSuitableFpsRange));
                    parameters.setPreviewFpsRange(minimumSuitableFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                    		minimumSuitableFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
                }
            }
        }
    }

    private Point findBestPreviewSizeValue(Camera.Parameters parameters, Point screenResolution) {

        List<Camera.Size> rawSupportedSizes = parameters.getSupportedPreviewSizes();
        if (rawSupportedSizes == null) {
            Camera.Size defaultSize = parameters.getPreviewSize();
            return new Point(defaultSize.width, defaultSize.height);
        }

        // Sort by size, descending
        List<Camera.Size> supportedPreviewSizes = new ArrayList<Camera.Size>(rawSupportedSizes);
        Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                return (bPixels < aPixels)? -1
                		:( (bPixels > aPixels)? 1 :0 );
            }
        });
        
        double screenAspectRatio = (screenResolution.x > screenResolution.y)?
        		(double) screenResolution.x / (double) screenResolution.y
        		: (double) screenResolution.y / (double) screenResolution.x;

        // Remove sizes that are unsuitable
        Iterator<Camera.Size> it = supportedPreviewSizes.iterator();
        while (it.hasNext()) {
            Camera.Size supportedPreviewSize = it.next();
            int realWidth = supportedPreviewSize.width;
            int realHeight = supportedPreviewSize.height;
            if (realWidth * realHeight < MIN_PREVIEW_PIXELS) {
                it.remove();
                continue;
            }

            boolean isCandidatePortrait = realWidth < realHeight;
            int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
            int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if(distortion > MAX_ASPECT_DISTORTION){
                it.remove();
                continue;
            }

            if( (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) 
        	|| (maybeFlippedWidth == screenResolution.y && maybeFlippedHeight == screenResolution.x) ){
                Point exactPoint = new Point(realWidth, realHeight);
                Log("Found preview size exactly matching screen size: " + exactPoint);
                return exactPoint;
            }
        }

        // If no exact match, use largest preview size. This was not a great idea on older devices because
        // of the additional computation needed. We're likely to get here on newer Android 4+ devices, where
        // the CPU is much more powerful.
        if (!supportedPreviewSizes.isEmpty()) {
            Camera.Size largestPreview = supportedPreviewSizes.get(0);
            Point largestSize = new Point(largestPreview.width, largestPreview.height);
            Log("Using largest suitable preview size: " + largestSize);
            return largestSize;
        }

        // If there is nothing at all suitable, return current preview size
        Camera.Size defaultPreview = parameters.getPreviewSize();
        Point defaultSize = new Point(defaultPreview.width, defaultPreview.height);
        Log("No suitable preview sizes, using default: " + defaultSize);
        return defaultSize;
    }

    private static String findSettableValue(Collection<String> supportedValues,String... desiredValues) {
        //Log("Supported values: " + supportedValues);
        String result = null;
        if (supportedValues != null) {
            for (String desiredValue : desiredValues) {
                if (supportedValues.contains(desiredValue)) {
                    result = desiredValue;
                    break;
                }
            }
        }
        //Log("Set value: " + result);
        return result;
    }

    private static void Log(String message){
		//edu.cuhk.ie.authbarcodescanner.android.Log.i(CameraConfigurationManager.TAG,message);
    }  
}
