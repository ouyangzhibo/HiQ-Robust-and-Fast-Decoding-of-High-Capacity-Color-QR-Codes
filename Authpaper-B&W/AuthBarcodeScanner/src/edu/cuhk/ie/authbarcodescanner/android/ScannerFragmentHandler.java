/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;


import com.google.zxing.qrcode.decoder.DataBlock;
import edu.cuhk.ie.authbarcodescanner.analytic.GetCameraInfo;
import edu.cuhk.ie.authbarcodescanner.analytic.GetCameraInfoTask;
import edu.cuhk.ie.authbarcodescanner.android.camera.CameraManager;
import edu.cuhk.ie.authbarcodescanner.android.camera.CameraOverlay;
import edu.cuhk.ie.authbarcodescanner.android.decodethread.DecodeThread;
import edu.cuhk.ie.authbarcodescanner.android.decodethread.DecodeThreadHandler.DetectResult;
import edu.cuhk.ie.authbarcodescanner.android.R;


/**
 * This class handles the messages between the display thread (ScannerFragment) and the decoding thread
 * @author solon li
 *
 */
public final class ScannerFragmentHandler extends Handler{
	private static final String TAG = ScannerFragmentHandler.class.getSimpleName();
	
	private static enum State {
	    DETECT,DECODE,SUCCESS,
	    QUIT,PAUSE
	}
	private static int deepScanLimit=30;
	private static int datablockKeepLimit=10;
	
	public final ScannerFragment activity;
	private final CameraManager camManager;
	private final ResultPointCallback displayCallback;
	private final Map<DecodeHintType,Object> hints;
	private final Collection<BarcodeFormat> defaultBarcodeFormat;
	//Decoding thread related
	private final DecodeThread decodeThread;
	private State state;
	private int scanCount=0, deepScanCount=0, keepDatablockCount;
	private int scanTimeTotal=0;
	private BarcodeFormat lastDecodingFormat=null;
	//Record the scan count and time for each channel of the color QR code
	private int rCount=0,gCount=0,bCount=0,rTime=0,gTime=0,bTime=0, detectCount=0;
	int[] decodeProgress=new int[3];
	private int[] channelFailCount= new int[]{0, 0, 0};
	private int anyChannelFailLimit=5;
	private boolean isphoto = false;
	Context context;
	private SharedPreferences sharedPref;
	
	public ScannerFragmentHandler(ScannerFragment activity, 
			Collection<BarcodeFormat> defaultBarcodeFormat,
            CameraManager camManager,
            ResultPointCallback callback,
            Context context) {
		this.activity = activity;
		this.displayCallback=callback;
		this.camManager = camManager;
		this.defaultBarcodeFormat = defaultBarcodeFormat;
		this.context= context;
		
		hints = new EnumMap<DecodeHintType,Object>(DecodeHintType.class);
		hints.put(DecodeHintType.POSSIBLE_FORMATS, defaultBarcodeFormat);
		hints.put(DecodeHintType.TRY_HARDER, true);
		hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, this.displayCallback);
		//hints.put(DecodeHintType.NEED_LOG_CALLBACK, new Log());
	    //To scan QR codes with shuffled codewords
	    //hints.put(DecodeHintType.Shuffled_Codeword, true);
		hints.put(DecodeHintType.Need_Successful_DataBlocks, null);		
		decodeThread = new DecodeThread(this.camManager, this.hints, this, activity);
		decodeThread.start();
		state = State.SUCCESS;
		// send one off email about camera details
		sharedPref = PreferenceManager.getDefaultSharedPreferences(activity.context);				
		Log.d(TAG, "Init Scanning. Cam details to be sent " + Boolean.toString(sharedPref.getBoolean(SendService.POST_CAMERA, true)));
		if(sharedPref.getBoolean(SendService.POST_CAMERA, true)) {
			GetCameraInfoTask getCameraInfoTask = new GetCameraInfoTask(this, camManager, context);
			getCameraInfoTask.execute();
		}
	}
	
	@Override
	public void handleMessage(Message message) {
		if(state ==State.QUIT) return;
		if(state ==State.PAUSE){
			//block all messages except decode success, restart decode and auto-focus
			if(message.what !=R.id.decode_succeeded && message.what !=R.id.resume_decode
					&& message.what !=R.id.auto_focus) return;
		}
		int msg=message.what;
		if(msg == R.id.decode_failed || msg ==R.id.decode_detected || msg ==R.id.decode_partial_succeeded || msg ==R.id.decode_succeeded)
			Log.d(TAG, "Decoded the "+(scanCount+1)+" frame");			
		switch (message.what) {
			case R.id.auto_focus:
				removeMessages(R.id.auto_focus);
	        	if(message.arg1 == 1){
	        		camManager.requestAutoFocus(this, R.id.auto_focus);  		
	        	}else{
	        		activity.LogMsg("Restart scanning after autofocusing");
	        		removeMessages(R.id.decode_detected);
		    	    removeMessages(R.id.decode_failed);
		    	    scanCount = 0;
		    	    deepScanCount = 0;
		    	    scanTimeTotal =0;
		    	    camManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decodeDeep);
		        	Message reFocusMsg =this.obtainMessage(R.id.auto_focus);
		        	reFocusMsg.arg1 = 1;
		        	this.sendMessageDelayed(reFocusMsg, 1000); //reset focus after 1 second
	        	}
	        	break;
			case R.id.init_decode:
				camManager.startPreview();
			case R.id.resume_decode:
				activity.LogMsg("Resume decoding");
				state = State.SUCCESS;
			case R.id.start_decode:
		        scanCount=0;
		        deepScanCount=0;
		        scanTimeTotal=0;
		        detectCount=0;
		        rCount=0;gCount=0;bCount=0;rTime=0;gTime=0;bTime=0;
		        if(state ==State.SUCCESS){
		        	state = State.DETECT;
		        	camManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);
		        	camManager.requestAutoFocus(this, R.id.auto_focus);
		        	activity.drawCameraOverlay();
		        }
		        break;
			case R.id.pause_decode:
				activity.LogMsg("Got pause decoding message");
		    	state = State.PAUSE;
		    	break;
			case R.id.decode_failed:
				//changeFocusArea(null);
				if (message.arg2 == 0) {//monochrome
					detectSingleBarcode(message, null,null);
				} else {
					if(rCount >0 && gCount >0){
						Log.d(TAG, "enter only yellow fail case");
						if(channelFailCount[0] >anyChannelFailLimit || 
								channelFailCount[1] >anyChannelFailLimit ||
								channelFailCount[2] >anyChannelFailLimit)
							detectSingleBarcode(message, null, decodeProgress, true);
						else
							detectSingleBarcode(message, null, decodeProgress, isphoto);
			        	break;
					}
					detectSingleBarcode(message, null, decodeProgress, isphoto);
				}
	        	break;
			case R.id.decode_detected:
				Result result=((Result) message.obj);
				//changeFocusArea(result.getResultPoints());
				if (message.arg2 == 0) {//monochrome
					detectSingleBarcode(message, result.getBarcodeFormat(), result.getDataBlocks());
				} else {//color
					if(rCount >0 && gCount >0){
						if(channelFailCount[0] >anyChannelFailLimit || 
								channelFailCount[1] >anyChannelFailLimit ||
								channelFailCount[2] >anyChannelFailLimit){
							BarcodeFormat format =result.getBarcodeFormat();
							detectSingleBarcode(message, format, decodeProgress,true);
							detectCount++;
							break;
						}
					}				
					detectSingleBarcode(message, result.getBarcodeFormat(), decodeProgress, isphoto);
				}
		        detectCount++;
	        	break;
	        case R.id.decode_partial_succeeded:				
				Log.d(TAG, "###enter partially function");
				detectCount++;
				Result[] results = ((Result[]) message.obj);
				if(results[0] != null){
					hints.put(DecodeHintType.REG_CHANNEL_DECODED, results[0]);
					Log.d(TAG, "backup Red channel");
					StringBuilder failedBlockList = new StringBuilder();
					decodeProgress[0]=(results[0].getRawBytes() !=null)? 200:
						calculateDecodeProgress(results[0].getDataBlocks(),failedBlockList);
					Log.d(TAG, "Failed Red Channel Block List : "+failedBlockList.toString());
					if(results[0].getRawBytes() !=null && rCount==0){						
						rCount = scanCount+1;
						rTime = scanTimeTotal + message.arg1;
					}
				}else Log.d(TAG, "None of Blocks in Red Channel Decoded");
				if (results[1] != null) {
					hints.put(DecodeHintType.GREEN_CHANNEL_DECODED, results[1]);
					Log.d(TAG, "backup Green channel");
					StringBuilder failedBlockList = new StringBuilder();
					decodeProgress[1]=(results[1].getRawBytes() !=null)? 200:
						calculateDecodeProgress(results[1].getDataBlocks(),failedBlockList);
					Log.d(TAG, "Failed Green Channel Block List : "+failedBlockList.toString());
					if(results[1].getRawBytes() !=null && gCount==0){						
						gCount = scanCount+1;
						gTime = scanTimeTotal + message.arg1;
					}
				}else Log.d(TAG, "None of Blocks in Green Channel Decoded");
				if (results[2] != null) {
					hints.put(DecodeHintType.BLUE_CHANNEL_DECODED, results[2]);
					Log.d(TAG, "backup Blue channel");
					StringBuilder failedBlockList = new StringBuilder();
					decodeProgress[2]=(results[2].getRawBytes() !=null)? 200:
						calculateDecodeProgress(results[2].getDataBlocks(),failedBlockList);
					Log.d(TAG, "Failed Blue Channel Block List : "+failedBlockList.toString());
					if(results[2].getRawBytes() !=null && bCount==0){						
						bCount = scanCount+1;
						bTime = scanTimeTotal + message.arg1;
					}
				}else Log.d(TAG, "None of Blocks in Blue Channel Decoded");		
				if(channelFailCount[0] >anyChannelFailLimit || 
						channelFailCount[1] >anyChannelFailLimit ||
						channelFailCount[2] >anyChannelFailLimit){
					Log.d(TAG, "###start taking image");
					BarcodeFormat format = (results[0] !=null)? results[0].getBarcodeFormat() : 
						( (Result) hints.get(DecodeHintType.REG_CHANNEL_DECODED) ).getBarcodeFormat();
					detectSingleBarcode(message, format, decodeProgress,true);
					break;
				}
				if(results[0] != null){				
					detectSingleBarcode(message, results[0].getBarcodeFormat(), decodeProgress, isphoto);
				}else detectSingleBarcode(message, null, decodeProgress, isphoto);
	        	break;
			case R.id.decode_succeeded:
		        //Record the number of failed scanning and average time of decoding
		        scanCount++;
		        deepScanCount++;
		        detectCount++;
		        scanTimeTotal+=message.arg1;
		        state = State.SUCCESS;
		        // pack scan statistics for sending IF allowed in user preference
		        String sendPref = sharedPref.getString(context.getString(R.string.pref_key_analytics), 
		        		context.getString(R.string.setting_analytics_value_default));
				if(sendPref.equals(context.getString(R.string.setting_analytics_value_none))) {
					Log.d(TAG, "Requested scan statistics not to be sent");
				}else {
			        Log.d(TAG, "Requesting scan statistics"); 
			        Boolean sendExtra = sendPref.equals(context.getString(R.string.setting_analytics_value_extra));
			        sendScanStatistics(((Result) message.obj), sendExtra);				
				}
		        activity.handleDecodeResult((Result) message.obj);
		        break;
			case R.id.save_image:
				// Save detected coordinates and filename to memory
				activity.fragmentCallback.addImageToList((DetectResult) message.obj);
				break;
			case R.id.scan_color:
				decodeThread.setColor(message.arg1);
				activity.handleScanColororBW(message.arg1);
				break;
		}
		System.gc();
	}
	public void quit() {
	    state = State.QUIT;
	    camManager.stopPreview();
	    Message quit = Message.obtain(decodeThread.getHandler(), R.id.end_decode);
	    quit.sendToTarget();
	    try {
	      // Wait at most half a second; should be enough time, and onPause() will timeout quickly
	      decodeThread.join(500L);
	    }catch(InterruptedException e){ }
	    // Be absolutely sure we don't send any queued up messages
	    removeMessages(R.id.decode_succeeded);
	    removeMessages(R.id.decode_detected);
	    removeMessages(R.id.decode_failed);
	}
	private void sendScanStatistics(Result result, boolean sendExtra) {
		Log.d(TAG, "Adding Scan Stat to Camera Mail");
		
		JSONObject jsonObj = new JSONObject();
		try {
			String codeFormat = result.getBarcodeFormat().toString();
			int codeSize = result.getRawBytes().length;			
			// If extra scan stat to be sent, request the list of images to zip up
			if (sendExtra) {				
				String zipFileName = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(Calendar.getInstance().getTime()) + "_scanstat";
				// add random string to name to uniquely identify this zip file
				SecureRandom random = new SecureRandom();
				String randomStr = new BigInteger(130, random).toString(32);
				
				Log.d(TAG, "ScanStat zip name " + zipFileName);
				jsonObj.put("statExtraName", zipFileName);
				jsonObj.put("statExtraPRN", randomStr);
			}
			jsonObj.put("scanCount", scanCount);
			jsonObj.put("deepScanCount", deepScanCount);
			jsonObj.put("scanTime", scanTimeTotal);
			jsonObj.put("barcodeFormat", codeFormat);
			jsonObj.put("barcodeSize", codeSize);
			
			// Extract metadata
			Map<ResultMetadataType, Integer> intMetadata
		  		=new EnumMap<ResultMetadataType,Integer>(ResultMetadataType.class);
			Map<ResultMetadataType, String> strMetadata
		  		=new EnumMap<ResultMetadataType,String>(ResultMetadataType.class);
			result.copyMetadata(intMetadata, strMetadata, null, null);
			if(!intMetadata.isEmpty() || !strMetadata.isEmpty() || rCount>0 || gCount>0 || bCount>0){
				JSONObject mdJson = new JSONObject();
				if(!intMetadata.isEmpty()){
					for(Map.Entry<ResultMetadataType, Integer> entry : intMetadata.entrySet()) {
						mdJson.put(entry.getKey().toString(), entry.getValue());
					}
				}
				if(!strMetadata.isEmpty()){
					for(Map.Entry<ResultMetadataType, String> entry : strMetadata.entrySet()) {
						mdJson.put(entry.getKey().toString(), entry.getValue());
					}
				}				
				String ecLevel=result.getStringMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL);
				//Check if the scanned QR code is colorby reading its error correction level		
				if(ecLevel !=null && ecLevel.length() >1){
					mdJson.put("Layer1ScanCount", (rCount>0)? rCount:scanCount);
					mdJson.put("Layer1ScanTime", (rCount>0)? rTime:scanTimeTotal);
					mdJson.put("Layer2ScanCount", (gCount>0)? gCount:scanCount);
					mdJson.put("Layer2ScanTime", (gCount>0)? gTime:scanTimeTotal);
					mdJson.put("Layer3ScanCount", (bCount>0)? bCount:scanCount);
					mdJson.put("Layer3ScanTime", (bCount>0)? bTime:scanTimeTotal);
				}
				mdJson.put("QRcodeDetectedCount", detectCount);
				jsonObj.put("metadata", mdJson);
			}		
			// get camera details before camera is closed
			GetCameraInfo getCameraInfo = new GetCameraInfo(camManager, context);
			getCameraInfo.setInitJson(jsonObj); // add scan statistics
			jsonObj = getCameraInfo.getCameraParameters();			
			
			GetCameraInfoTask getCameraInfoTask = new GetCameraInfoTask(this, camManager, context);
			getCameraInfoTask.setRequestCamDetails(false);
			getCameraInfoTask.addScanDetails(jsonObj); // also added camera details
			getCameraInfoTask.execute();			
		} catch (JSONException e) {
			Log.e(TAG, "Error converting to JSON " + e.toString());
			e.printStackTrace();
		}
	}
		
	private synchronized void detectSingleBarcode(Message message, BarcodeFormat confirmedBarcodeFormat, DataBlock[] datablocks){
		//Decoding Monochrome QR code
		//Hardcode for CUPP
        /*if(scanCount >4){
        	Result rawResult=HistoryFragment.getInstance().getLatestEntry();
        	if(rawResult !=null){
        		activity.handleDecodeResult(rawResult);
        		return;
        	}
        }*/
		//TODO: Minimize the code and function calls from getting the message to new requestPreviewFrame
		boolean isNewHints=false;
		if(datablocks != null){
			hints.put(DecodeHintType.Need_Successful_DataBlocks,datablocks);
			isNewHints=true;
			keepDatablockCount=0;
		}else if(hints.containsKey(DecodeHintType.Need_Successful_DataBlocks)){
			//It cannot detect any code, but some data blocks are stored in the previous frames
			keepDatablockCount++;
			if(keepDatablockCount >datablockKeepLimit){
				hints.remove(DecodeHintType.Need_Successful_DataBlocks);
				isNewHints=true;
				keepDatablockCount=0;
			}
		}
		if(confirmedBarcodeFormat !=null && deepScanCount < deepScanLimit){
			if(lastDecodingFormat ==null || lastDecodingFormat.compareTo(confirmedBarcodeFormat) !=0 ){
				lastDecodingFormat=confirmedBarcodeFormat;
				hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(confirmedBarcodeFormat) );
				isNewHints=true;
			}
        	state = State.DECODE;    		
    		//Count the % of blocks which are decoded
    		float percentOfSuccessBlock=0;
    		if(datablocks !=null){
    			for(int i=0,l=datablocks.length;i<l;i++){
    				if(datablocks[i] !=null) percentOfSuccessBlock++;
    			}
    			percentOfSuccessBlock = (percentOfSuccessBlock / datablocks.length)*100;
    		}
        	activity.handleDetectedBarcode(confirmedBarcodeFormat.toString(), percentOfSuccessBlock);
        	deepScanCount++;
    	}else{
    		if(lastDecodingFormat !=null){
				lastDecodingFormat=null;
				hints.put(DecodeHintType.POSSIBLE_FORMATS, defaultBarcodeFormat);
				isNewHints=true;
			}
    		state = State.DETECT;
    		activity.handleDetectedBarcode(null,0);
    		deepScanCount=0;
    	}
		scanCount++;
        scanTimeTotal+=message.arg1;
        if(isNewHints) decodeThread.setHints(hints);
		camManager.requestPreviewFrame(decodeThread.getHandler(), 
			(state == State.DECODE)? R.id.decodeDeep : R.id.decode);
	}
	
	private synchronized void detectSingleBarcode(Message message, BarcodeFormat confirmedBarcodeFormat, 
			int[] decodeProgress, boolean isPhoto){		
		//Decoding Color QR code
		//Hardcode for CUPP
        /*if(scanCount >4){
        	Result rawResult=HistoryFragment.getInstance().getLatestEntry();
        	if(rawResult !=null){
        		activity.handleDecodeResult(rawResult);
        		return;
        	}
        }*/
		if(isPhoto) Log.d(TAG,"Scan using photos");
		else Log.d(TAG,"Scan using previews");
		//TODO: Minimize the code and function calls from getting the message to new requestPreviewFrame
		if(confirmedBarcodeFormat !=null && deepScanCount < deepScanLimit){
			if(lastDecodingFormat ==null || lastDecodingFormat.compareTo(confirmedBarcodeFormat) !=0 ){
				lastDecodingFormat=confirmedBarcodeFormat;
				hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(confirmedBarcodeFormat) );
	        	decodeThread.setHints(hints);
			}
        	state = State.DECODE;        	
        	if(isPhoto) camManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decodeDeep, true);
        	else camManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decodeDeep);
    		if(decodeProgress !=null) activity.handleDetectedBarcode(confirmedBarcodeFormat.toString(), decodeProgress);
    		else activity.handleDetectedBarcode(confirmedBarcodeFormat.toString(), message.arg2);
        	deepScanCount++;
    	}else{
    		if(lastDecodingFormat !=null){
				lastDecodingFormat=null;
				hints.put(DecodeHintType.POSSIBLE_FORMATS, defaultBarcodeFormat);
	    		decodeThread.setHints(hints);
			}
    		state = State.DETECT;
    		if(isPhoto) camManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decodeDeep, true);
    		else camManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);
    		if(decodeProgress !=null) activity.handleDetectedBarcode(null, decodeProgress);
    		else activity.handleDetectedBarcode(null, message.arg2);
    		deepScanCount=0;
    	}
		scanCount++;
        scanTimeTotal+=message.arg1;
	}
	
	private void changeFocusArea(ResultPoint[] points){
		if(points !=null){
			Rect previewFrame = camManager.getFramingRectInPreview();
			if(previewFrame ==null) return;
			Point lastFrameCenter=new Point(previewFrame.centerX(), previewFrame.centerY());
			if(lastFrameCenter ==null || lastFrameCenter.x <=0 || lastFrameCenter.y <=0) return;
			
			int lineLeft=0,lineRight=0,lineTop=0,lineBottom=0;
			for(ResultPoint point : points){
				//Need to rotate 90 degrees to make it correct
				if(point ==null || point.getX() <=0 || point.getY() <=0) continue;
				point =CameraOverlay.rotate90Clock(point, lastFrameCenter);
            	if(point ==null) continue;
            	int pX= (int) (point.getX()+0.5), pY=(int) (point.getY()+0.5);
            	lineLeft = (lineLeft > 0 && lineLeft < pX)? lineLeft : pX ;
            	lineRight = (lineRight > 0 && lineRight > pX)? lineRight : pX ;
            	lineTop = (lineTop > 0 && lineTop < pY)? lineTop : pY;
            	lineBottom = (lineBottom > 0 && lineBottom > pY)? lineBottom : pY;
			}			
			if(lineLeft >0 && lineRight >0 && lineTop >0 && lineBottom >0)				
				camManager.changeFocusArea(new Rect(lineLeft, lineTop, lineRight, lineBottom));
		}else camManager.changeFocusArea(null);
	}
	
	private static int calculateDecodeProgress(com.google.zxing.qrcode.decoder.DataBlock[] datablocks, StringBuilder failedBlockList){
		//Count the % of blocks which are decoded		
		failedBlockList.append("[");
		float percentOfSuccessBlock=0;	
		if(datablocks !=null){
			for(int i=0,l=datablocks.length;i<l;i++){
				if(datablocks[i] !=null) percentOfSuccessBlock++;
				else failedBlockList.append(i+",");
			}
			percentOfSuccessBlock = (percentOfSuccessBlock / datablocks.length)*100;
		}
		failedBlockList.append("]");
		return (int) percentOfSuccessBlock;
	}
	
}
