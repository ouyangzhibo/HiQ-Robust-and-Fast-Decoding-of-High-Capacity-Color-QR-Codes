/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.decodethread;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.stat.descriptive.moment.Variance;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.SparseArray;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.color.Classifier;
import com.google.zxing.color.RGBColorWrapper;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.ColorQRCodeReader;

import edu.cuhk.ie.authbarcodescanner.analytic.RGBtoVYUConvertor;
import edu.cuhk.ie.authbarcodescanner.analytic.YUVtoRGBConvertor;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import edu.cuhk.ie.authbarcodescanner.android.R;
import edu.cuhk.ie.authbarcodescanner.android.ScannerFragment;
import edu.cuhk.ie.authbarcodescanner.android.ScannerFragmentHandler;
import edu.cuhk.ie.authbarcodescanner.android.SendService;
import edu.cuhk.ie.authbarcodescanner.android.camera.CameraManager;

/**
 * Passed from the decode thread to the camera manager to decode the preview image
 * This handler does most of the heavy lifting things
 * @author solon li
 *
 */
public final class DecodeThreadHandler extends Handler{
	private static final String TAG=DecodeThreadHandler.class.getSimpleName();
	private final CameraManager camManager;	
	private MultiFormatReader multiFormatReader;
	private final ScannerFragmentHandler targetHandler;
	private boolean isRunning=true;
	private Map<DecodeHintType,Object> hints;
	private Classifier colorClassifier = null;	
	File outputFile = null;
	
	private SharedPreferences sharedPref;
	private Context context;
	
	public int colorIndicator=0;//0 means scan both color and black and white, 1 means scan BW only, 2 means color only
	
	public DecodeThreadHandler(ScannerFragmentHandler targetHandler, CameraManager camManager, 
			Map<DecodeHintType,Object> hints, ScannerFragment activity, int colorIndicator, Classifier colorClassifier){
		this.targetHandler=targetHandler;
		this.camManager=camManager;
		this.multiFormatReader = new MultiFormatReader();
	    this.multiFormatReader.setHints(hints);
	    this.hints = hints;
	    this.sharedPref = PreferenceManager.getDefaultSharedPreferences(activity.getActivity());
	    this.context = activity.getActivity();
	    this.colorClassifier = colorClassifier;
	    this.colorIndicator = colorIndicator;
	    try{
			outputFile=File.createTempFile("temp", ".jpg", context.getCacheDir());
		}catch(IOException e){ }
	}
	/**
	 * Handle the message from the targetHandler
	 */
	@Override
	public void handleMessage(Message message) {
		if(!isRunning) return;
		switch (message.what) {
	    	case R.id.decode:
	    		decode((byte[]) message.obj, message.arg1, message.arg2, false);
	    		break;
	    	case R.id.decodeDeep:
	    		decode((byte[]) message.obj, message.arg1, message.arg2, true);
	    		break;
	    	case R.id.end_decode:
	    		isRunning = false;
	    		Looper.myLooper().quit();
	    		break;
	    }
	}
	public void setHints(Map<DecodeHintType,Object> hints){
		this.multiFormatReader = new MultiFormatReader();
	    this.multiFormatReader.setHints(hints);
	}

	/**
	 * Decode the image based on imageByte from preview image
	 * This function does all the heavy-lifting things after taking a preview photo
	 * @param imageByte
	 * @param width
	 * @param height
	 */
	public Result decode(byte[] imageByte, int width, int height, boolean isDeep){
		System.gc();
		// decide whether the code is color or monochrome
		long s = System.currentTimeMillis();
		boolean isColor = (colorIndicator==0)? isColor(width / 2, height / 2, width, height, imageByte, false)
				: (colorIndicator==1)? false:true;
		Log.d(TAG, "###isColor interval: "+(System.currentTimeMillis()-s) + isColor);
		s = System.currentTimeMillis();
		
		Result rawResult = null;		
		int inteval = 0;
		if(!isColor){// monochrome
			MultiFormatReader reader=this.multiFormatReader;//then the hints be set already!
			hints.remove(DecodeHintType.Shuffled_Codeword);
			reader.setHints(hints);
		    // Reduce imageByte to only have luminance 
	    	byte[] lumImageByte=Arrays.copyOf(imageByte, (width*height));
			//If targetHandler is not set, it means this class is called to decode local image. So settings from camManager are not needed
		    PlanarYUVLuminanceSource source = (targetHandler !=null)? 
		    		camManager.buildLuminanceSource(lumImageByte, width, height) : 
		    		new PlanarYUVLuminanceSource(lumImageByte, width, height, 0, 0,
		    				width, height, false);
		    
		    if(source == null || reader ==null) return null;
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
			//BinaryBitmap bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
			try{
				rawResult = reader.decodeWithState(bitmap);
			}catch(ReaderException re){ }
			if(isDeep && (rawResult ==null || rawResult.getRawBytes()==null) ){
				reader.reset();
				// bitmap = new BinaryBitmap(new
				// LocalMeanBinarizer(source));
				bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
				try {
					rawResult = reader.decodeWithState(bitmap);
				} catch (ReaderException re) { }
			}
			inteval = (int) (System.currentTimeMillis() - s);
		}else{
			Log.d(TAG, "Changed to RGB");
			BinaryBitmap binaryBitmap = null;
			int[] RGBImageData = null;
			boolean photoFlag = false;
			boolean blackMarkerFlag = false;
			
			// For data from taking a picture, it is already in RGB format
			if (photoFlag && width > 2300 && height > 2300) {
				Bitmap bitmap = BitmapFactory.decodeByteArray(imageByte, 0,
						imageByte.length);
				if (bitmap != null) {
					Log.d(TAG, "###RGB data");
					int imgWidth = bitmap.getWidth(), imgHeight = bitmap
							.getHeight();
					RGBImageData = new int[imgWidth * imgHeight];
					bitmap.getPixels(RGBImageData, 0, imgWidth, 0, 0, imgWidth,
							imgHeight);
					// saveImage(bitmap, "temp"+new
					// SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(Calendar.getInstance().getTime())+".jpg",
					// width, height);
					bitmap.recycle();
					if (outputFile != null)
						outputFile.delete();
				}
			} else {
				RGBImageData = YUVtoRGBConvertor.YUVtoRGBpixels(imageByte, width,
						height);
			}
			System.gc();

			if (RGBImageData == null) {
				Log.d(TAG, "###RGB data is null");
				return null;
			}
			RGBColorWrapper colorWrapper = (targetHandler !=null)?  
					camManager.buildRGBColorWrapper(RGBImageData, width, height, colorClassifier, hints) : 
					new RGBColorWrapper(RGBImageData, width, height, 0, 0, width, height, false, colorClassifier, hints);
			if(colorWrapper ==null) return null;
			DetectorResult firstDetectRst = null;
			if (blackMarkerFlag) {
				// use Y channel do detection first, *only for black markers
				byte[] lumImageByte = Arrays.copyOf(imageByte, (width * height));
				PlanarYUVLuminanceSource source = (targetHandler !=null)?
						camManager.buildLuminanceSource(lumImageByte, width, height) :
						new PlanarYUVLuminanceSource(lumImageByte, width, height, 0, 0, width, height, false);
				if(source == null) return null;
				binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

				try {
					firstDetectRst = ColorQRCodeReader.roughDetect(
							binaryBitmap.getBlackMatrix(), hints);
				}catch(NotFoundException e1){ }
				if(firstDetectRst == null){
					if(targetHandler !=null){
						Log.d(TAG, "###first detection: not found!");
						int failedChannel = 0;
						if (hints.get(DecodeHintType.REG_CHANNEL_DECODED) == null)
							failedChannel += 1;
						if (hints.get(DecodeHintType.GREEN_CHANNEL_DECODED) == null)
							failedChannel += 2;
						if (hints.get(DecodeHintType.BLUE_CHANNEL_DECODED) == null)
							failedChannel += 4;
						targetHandler.obtainMessage(R.id.decode_failed, 0,failedChannel, rawResult).sendToTarget();
					}
					return null; // not detected
				}
			}

			// rough detection succeeded or not executed
			if (blackMarkerFlag) {
				Log.d(TAG, "color wrapper not null");
				try {
					colorWrapper.setBitMatrix(binaryBitmap.getBlackMatrix());
				}catch(NotFoundException e1){ }
			}

			ColorQRCodeReader colorQRCodeReader = new ColorQRCodeReader(
					colorWrapper);
			Result[] results = null;
			Log.d(TAG, "Decoder prepared!");
			try {
				hints.put(DecodeHintType.Need_Successful_DataBlocks, null);
				//hints.put(DecodeHintType.Shuffled_Codeword, true);
				// hints.remove(DecodeHintType.Need_Successful_DataBlocks);
				results = colorQRCodeReader.decode(hints, firstDetectRst);
			} catch (FormatException e) {
			} catch (ChecksumException e) {
			} catch (NotFoundException e) {
			}
			inteval = (int) (System.currentTimeMillis() - s);
			if (results != null) {
				Log.d(TAG, "Results decoded!");
				// check hints, see if all three channels are decoded
				int decodedChannelNum = 0;
				Result[] newResults = new Result[3];
				newResults[0] = (results[0] != null) ? results[0]
						: (Result) hints
								.get(DecodeHintType.REG_CHANNEL_DECODED);
				if (newResults[0] != null
						&& newResults[0].getRawBytes() != null)
					decodedChannelNum++;
				newResults[1] = (results[1] != null) ? results[1]
						: (Result) hints
								.get(DecodeHintType.GREEN_CHANNEL_DECODED);
				if (newResults[1] != null
						&& newResults[1].getRawBytes() != null)
					decodedChannelNum++;
				newResults[2] = (results[2] != null) ? results[2]
						: (Result) hints
								.get(DecodeHintType.BLUE_CHANNEL_DECODED);
				if (newResults[2] != null
						&& newResults[2].getRawBytes() != null)
					decodedChannelNum++;

				if (decodedChannelNum == 3) {// all channels are decoded
					Log.d(TAG, "###all channels decoded!");
					rawResult = combineColorResult(newResults);
				} else {// no, backup decoded channels
					if(targetHandler !=null){
						String a = "";
						String b = "";
						String c = "";
						int count = 0;
						int failedChannel = 0;

						for (int i = 0; i < 3; i++) {
							if (results[i] != null) {// some or all blocks are decoded in this channel
								a = a + " " + i;
								count++;
							}
							if (newResults[i] != null && newResults[i].getRawBytes() != null) {
								b = b + " " + i;
							} else {
								c = c + " " + i;
								failedChannel += Math.pow(2, i);
							}
						}

						if (count > 0) { // backup successfully decoded channels
							Log.d(TAG, "###some channels decoded!");
							targetHandler.obtainMessage(
									R.id.decode_partial_succeeded, inteval,
									failedChannel, results).sendToTarget();
						} else {
							Log.d(TAG, "###no new channels decoded!");
							targetHandler.obtainMessage(R.id.decode_failed,
									inteval, failedChannel, rawResult)
									.sendToTarget();
						}
					}
					return null;
				}
			} else {
				Log.d(TAG, "###no channels decoded!: null results");
				if(targetHandler !=null){
					int failedChannel = 0;
					if (hints.get(DecodeHintType.REG_CHANNEL_DECODED) == null)
						failedChannel += 1;
					if (hints.get(DecodeHintType.GREEN_CHANNEL_DECODED) == null)
						failedChannel += 2;
					if (hints.get(DecodeHintType.BLUE_CHANNEL_DECODED) == null)
						failedChannel += 4;
					targetHandler.obtainMessage(R.id.decode_failed, inteval,
							failedChannel, rawResult).sendToTarget();
				}
				return null;
			}
		}
	    Log.d(TAG, "Decode time "+inteval);
	    System.gc();
	    if(targetHandler !=null){
	    	String imageYUVName = "";
	    	// Only save images if extended scan statistic option selected
	        String sendPref = sharedPref.getString(context.getString(R.string.pref_key_analytics), 
	        		context.getString(R.string.setting_analytics_value_default));
			if(sendPref.equals(context.getString(R.string.setting_analytics_value_extra))) {
				imageYUVName = buildImageYUVName(rawResult);
				
				Camera camera = camManager.getCamera();
				Parameters param = camera.getParameters();

				// save image
				saveImage(imageByte, imageYUVName, width, height, param);
				// record image name for later use and get coordinates if detected
				DetectResult result = null;
				if (rawResult != null) {
					result = new DetectResult(imageYUVName, rawResult.getResultPoints());
				}else{
					result = new DetectResult(imageYUVName);
				}
				targetHandler.obtainMessage(R.id.save_image, result).sendToTarget();
			}	    	
			
			int typeFlag = (isColor)? 1:0;
	    	if(rawResult != null){
	    		if(rawResult.getRawBytes() !=null){
	    			Log.d(TAG, "###monochrome succeed!");
	    			targetHandler.obtainMessage(R.id.decode_succeeded, inteval, 
	            			rawResult.getRawBytes().length, rawResult).sendToTarget();
	    		}else{
	    			targetHandler.obtainMessage(R.id.decode_detected, inteval,
	    					typeFlag, rawResult).sendToTarget();
					// get coordinates for detected image 
	    		}
	    	}else{
	    		targetHandler.obtainMessage(R.id.decode_failed, inteval,typeFlag)
	    			.sendToTarget();
	    	}
	    	return null;
	    }else return rawResult;
	}
	
	/**
	 * tell whether the QR code is monochrome or color.
	 * If "isSimple" is false, we use a slightly computationally heavy method: calculating the two 
	 * largest principle components of the sampled data, p1 and p2. if p1/p2 is large, then it is 
	 * monochrome; if p1/p2 is small, color.
	 * If "isSimple" is true, we use a simple method to make it faster: look at the the channel 
	 * differences, we make a reasonable assumption: at least the channel difference of the 
	 * monochrome QR codes will not vary much (small variance), and use it to differentiate color and monochrome
	 * however this assumption is workable only for colors near the eight corners of the color cube.
	 * but it can make the running from 16-30 ms to 3-7 ms using Nexus5.
	 * @param centerX the x coordinate of the center of the QR code
	 * @param centerY the y coordinate of the center of the QR code
	 * @param imageByte YUV data of the preview
	 * @param isSimple true using simple and fast method, false use more sophisticated but better method
	 * @return if is color true, otherwise false
	 */
	private static boolean isColor(int centerX, int centerY, int width, int height, byte[] imageByte, boolean isSimple) {
		int halfSampleSize = 25;
		int sampleArea = 4*halfSampleSize*halfSampleSize;
		
		// crop the central part and transfer it from YUV to RGB
		int[] sampledRGBData = YUVtoRGBConvertor.YUVtoRGBpixels(imageByte, width, height,
									centerX-halfSampleSize, centerY-halfSampleSize, 
									centerX+halfSampleSize, centerY+halfSampleSize);
		return isColor(sampledRGBData,sampleArea,isSimple);
	}	
	private static boolean isColor(int[] sampledRGBData, int sampleArea, boolean isSimple){
		double[][] sampleArray = new double[sampleArea][3];
		for (int i = 0; i<sampleArray.length; i++) {
			sampleArray[i][0] = (sampledRGBData[i] >> 16) & 0xff;
			sampleArray[i][1] = (sampledRGBData[i] >> 8) & 0xff;
			sampleArray[i][2] = sampledRGBData[i] & 0xff;
		}

		if (isSimple) {
			double[][] channelDiff = new double[3][sampleArea];
			for (int i=0; i<sampleArea; i++) {
				channelDiff[0][i] = Math.abs(sampleArray[i][0]-sampleArray[i][1]);
				channelDiff[1][i] = Math.abs(sampleArray[i][2]-sampleArray[i][1]);
				channelDiff[2][i] = Math.abs(sampleArray[i][0]-sampleArray[i][2]);
			}
			Variance variance = new Variance();
			double var1 = variance.evaluate(channelDiff[0]);
			double var2 = variance.evaluate(channelDiff[1]);
			double var3 = variance.evaluate(channelDiff[2]);
			Log.d(TAG, "Vars=["+var1+","+var2+","+var3+"]");
			if (var1 < 50 || var2 < 50 || var3 < 50)
				return false;
			else
				return true;
		} else {
			RealMatrix M=new Array2DRowRealMatrix(sampleArray);
			SingularValueDecomposition svd = new SingularValueDecomposition(M);
			double[] S= svd.getSingularValues();
			Log.d(TAG, "###S=["+S[0]+", "+S[1]+", "+S[2]+"]");
			if (S[0]/S[2] > 20)
				return false;
			else
				return true;
		}
	}
	
	/**
	* This function combines three partial results divided by structure appending into one QR code result.
	* Notice that the returned result may not be able to be encoded in one mono QR code
	*/
	private static Result combineColorResult(Result[] results){
		Log.d(TAG, "Enter the combine function");
		if (results == null || results.length < 1)
			return null;
		Log.d(TAG, "results array is not null");
		//Joining the results together
		ByteArrayOutputStream joinedRawBytes = new ByteArrayOutputStream();
		String joinedText = "";				
		ByteArrayOutputStream joinedByteSegment = new ByteArrayOutputStream();
		Result target = null;
		//First, arrange the results in order of the combined sequence number
		SparseArray<Result> partialResults = new SparseArray<Result>();
		int totalNumberOfBlock=0;				
		for(int i=0,iCount=results.length;i<iCount;i++){
			Result rawResult=results[i];
			if(rawResult == null){
				Log.d(TAG, "null result "+i);
				return null;
			}
			int seq=rawResult.getIntMetadata(ResultMetadataType.STRUCTURED_APPEND_SEQUENCE);
			int parity=rawResult.getIntMetadata(ResultMetadataType.STRUCTURED_APPEND_PARITY);
			//The result is not part of the structure append
			if(seq <0 || parity <0) {
				Log.d(TAG, "enter the no-append if");
				String ECLevels="";
				for(int j=0; j<3; j++){
					if(results[j] != null){
						joinedText += results[j].getText().trim();
						//Log.d(TAG, "result"+i+" "+joinedText);
						try {
							joinedRawBytes.write(results[j].getRawBytes());
							List<byte[]> byteSegs=results[j].getByteMetadata(ResultMetadataType.BYTE_SEGMENTS);
							if(byteSegs !=null && !byteSegs.isEmpty()) joinedByteSegment.write(byteSegs.get(0));
							Log.d(TAG, "write successfull");
						} catch (IOException e) { }
						ECLevels +=results[j].getStringMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL); 						
					}
				}
				target = new Result(joinedText,joinedRawBytes.toByteArray(),null,results[0].getBarcodeFormat());
				results[0].copyMetadata(target);
				if(ECLevels.length()>1) target.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, ECLevels);
				List<byte[]> joinedByteList = new ArrayList<byte[]>();
				joinedByteList.add(joinedByteSegment.toByteArray());
				target.putMetadata(ResultMetadataType.BYTE_SEGMENTS, joinedByteList);
				target.putMetadata(ResultMetadataType.STRUCTURED_APPEND_SEQUENCE, 0);
				target.putMetadata(ResultMetadataType.STRUCTURED_APPEND_PARITY, 0);
				return target;
			}
			//Read the total number of QR codes should be present
			int totalB = 0x0f & seq;
			totalNumberOfBlock = (totalNumberOfBlock <totalB)? totalB : totalNumberOfBlock;
			//Save the result into a sparseArray in the order of the sequence number
			Log.d(TAG, ""+(0x0f & (seq>>4)));
			Log.d(TAG, ""+(seq));
			partialResults.append(0x0f & (seq>>4), rawResult);
		}
		Log.d(TAG, "append finished!");
		//Does not have enough QR code results to combine one complete result
		if(totalNumberOfBlock > partialResults.size()) return null;
		Log.d(TAG, "has enough QR code results!");
		String ECLevels="";
		for(int i=0,iCount=partialResults.size();i<iCount;i++){
			Log.d(TAG,""+i);
			Result rawResult=partialResults.get(i);
			try{
				joinedRawBytes.write(rawResult.getRawBytes());						
				joinedText +=rawResult.getText().trim();			
				List<byte[]> byteSegs=rawResult.getByteMetadata(ResultMetadataType.BYTE_SEGMENTS);
				if(byteSegs !=null && !byteSegs.isEmpty()) joinedByteSegment.write(byteSegs.get(0));
				ECLevels += rawResult.getStringMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL);
			}catch (IOException e){ 
				Log.d(TAG, "IOexception");
				try{ 
					joinedRawBytes.close();
					joinedByteSegment.close();
					return null;
				}catch(IOException e2){ }
			}
		}
		Log.d(TAG, "group begin");
		//Now group the result into one QR code result
		if(joinedRawBytes.size() >0 && joinedText !=null){
			target = new Result(joinedText,joinedRawBytes.toByteArray(),null,results[0].getBarcodeFormat());
			results[0].copyMetadata(target);
			if(ECLevels.length()>1) target.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, ECLevels);
			List<byte[]> joinedByteList = new ArrayList<byte[]>();
			joinedByteList.add(joinedByteSegment.toByteArray());
			target.putMetadata(ResultMetadataType.BYTE_SEGMENTS, joinedByteList);
			target.putMetadata(ResultMetadataType.STRUCTURED_APPEND_SEQUENCE, 0);
			target.putMetadata(ResultMetadataType.STRUCTURED_APPEND_PARITY, 0);
			return target;
		}
		Log.d(TAG, "something wrong!");
		//Something goes wrong
		return null;
	}
	
	public static Result fileDecode(android.graphics.Bitmap bmp, Map<DecodeHintType,Object> hints, Context context, Classifier colorClassifier){
		Log.d(TAG, "Read RGB file, H " + String.valueOf(bmp.getHeight()) + ", W " + String.valueOf(bmp.getWidth()));
		int width = bmp.getWidth(), height = bmp.getHeight();
		//Check whether it is color QR code
		int halfSampleSize = 25,sampleSize = halfSampleSize<<1;
		if(width <100 || height<100) return null;
		int sampleArea = sampleSize*sampleSize;
		Bitmap centralBitmap = Bitmap.createBitmap(bmp, (width >>1) - halfSampleSize, (height>>1) - halfSampleSize, 
				sampleSize, sampleSize);
		int[] samplingSpace = new int[sampleArea];
		centralBitmap.getPixels(samplingSpace, 0, sampleSize, 0, 0, sampleSize, sampleSize);
		centralBitmap.recycle();
		boolean isColor = isColor(samplingSpace,sampleArea,false);
		samplingSpace=null;
		Result rawResult=null;
		if(!isColor){ 
			System.gc();//Monochrome
			byte[] imageByte = RGBtoVYUConvertor.getNV21(bmp.getWidth(), bmp.getHeight(), bmp);
			Log.d(TAG,"Read "+imageByte.length+" bytes of data");
		    MultiFormatReader reader=new MultiFormatReader();
		    reader.setHints(hints);	    
			//First, use the quick and dirty method to do the decoding
		    rawResult=decodeOnce(imageByte, width, height,reader,hints);
		    if(rawResult !=null) return rawResult;
		    System.gc();
		    //If the dirty method is failed, try to scale it down to 708 pixels and do an unsharp mask
		    Log.d(TAG, "Decoding failed, possible formats :"+hints.get(DecodeHintType.POSSIBLE_FORMATS).toString());
		    double scale=(width<height)? (708.0/(double) width) : (708.0/(double) height);
		    Bitmap image = Bitmap.createScaledBitmap(bmp, (int) (((double) width)*scale), (int) (((double) height)*scale), false);
		    bmp.recycle();
		    System.gc();
		    Log.d(TAG, "Read size-reduced image, H " + String.valueOf(image.getHeight()) + ", W " + String.valueOf(image.getWidth()));
		    imageByte = RGBtoVYUConvertor.getNV21(image.getWidth(), image.getHeight(), image);
		    Log.d(TAG,"Read "+imageByte.length+" bytes of data");
			rawResult=decodeOnce(imageByte,image.getWidth(), image.getHeight(),reader,hints);			
		}else{
			//Color
			samplingSpace = new int[width * height];
			bmp.getPixels(samplingSpace, 0, width, 0, 0, width, height);			
			Log.d(TAG,"Read "+samplingSpace.length+" pixels of data for color QR code scanning");
			RGBColorWrapper colorWrapper =  new RGBColorWrapper(samplingSpace, width, height, 0, 0, width, height, 
					false, colorClassifier, hints);			
			ColorQRCodeReader colorQRCodeReader = new ColorQRCodeReader(colorWrapper);
			System.gc();
			Result[] results = null;
			try {
				hints.put(DecodeHintType.Need_Successful_DataBlocks, null);
				// hints.remove(DecodeHintType.Need_Successful_DataBlocks);
				results = colorQRCodeReader.decode(hints, null);
			} catch (FormatException e) {
			} catch (ChecksumException e) {
			} catch (NotFoundException e) {
			}
			if(results ==null) return null;
			
			Log.d(TAG, "Color QR code results decoded!");
			// check hints, see if all three channels are decoded
			int decodedChannelNum = 0;
			Result[] newResults = new Result[3];
			newResults[0] = (results[0] != null) ? results[0]
					: (Result) hints.get(DecodeHintType.REG_CHANNEL_DECODED);
			if (newResults[0] != null && newResults[0].getRawBytes() != null)
				decodedChannelNum++;
			newResults[1] = (results[1] != null) ? results[1]
					: (Result) hints.get(DecodeHintType.GREEN_CHANNEL_DECODED);
			if (newResults[1] != null && newResults[1].getRawBytes() != null)
				decodedChannelNum++;
			newResults[2] = (results[2] != null) ? results[2]
					: (Result) hints.get(DecodeHintType.BLUE_CHANNEL_DECODED);
			if (newResults[2] != null && newResults[2].getRawBytes() != null)
				decodedChannelNum++;
			if(decodedChannelNum == 3){// all channels are decoded
				Log.d(TAG, "###all channels decoded!");
				rawResult = combineColorResult(newResults);
			}
		}
		return rawResult;	
	}
	/**
	 * Decode the image once using the given MultiFormatReader and hints type
	 * If a decoding is not successful, the MultiFormatReader will be reset and update hints according to the scanning result
	 */
	public static Result decodeOnce(byte[] imageByte, int width, int height, MultiFormatReader reader, Map<DecodeHintType,Object> hints){
		// Reduce imageByte to only have luminance (make it grayscale) 
    	byte[] lumImageByte=Arrays.copyOf(imageByte, (width*height));
	    PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(lumImageByte, 
	    		width, height, 0, 0, width, height, false);
	    if(source == null || reader ==null) return null;
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		Result rawResult = null;
		try{
			rawResult = reader.decodeWithState(bitmap);
		}catch(ReaderException re){ }
		//Scanning is successful
		if(rawResult !=null && rawResult.getRawBytes() !=null) return rawResult;
		//If it is not successful but it can detect some of the data blocks, then use the result to help further scanning
		if(rawResult !=null && rawResult.getDataBlocks() !=null){				
			if(rawResult.getBarcodeFormat() !=null) 
				hints.put( DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(rawResult.getBarcodeFormat()) );
			hints.put(DecodeHintType.Need_Successful_DataBlocks,rawResult.getDataBlocks());
			reader.reset();
			reader.setHints(hints);
		}else reader.reset();
		System.gc();
	    //Try the global histogram method
		bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
		try{
			rawResult = reader.decodeWithState(bitmap);
		}catch(ReaderException re){ }
		if(rawResult !=null && rawResult.getRawBytes() !=null) return rawResult;
		if(rawResult !=null && rawResult.getDataBlocks() !=null){				
			if(rawResult.getBarcodeFormat() !=null) 
				hints.put( DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(rawResult.getBarcodeFormat()) );
			hints.put(DecodeHintType.Need_Successful_DataBlocks,rawResult.getDataBlocks());
			reader.reset();
			reader.setHints(hints);
		}else reader.reset();
		System.gc();
		return null;
	}
	
	// Convenience wrapper for logging the detected point
	public class DetectResult {
		public String imageYUVName;
		public ResultPoint[] results;
	
		public DetectResult(String imageYUVName, ResultPoint[] results) {
			this.imageYUVName = imageYUVName;
			this.results = results;
		}
		
		public DetectResult(String imageYUVName) {
			this.imageYUVName = imageYUVName;
			this.results = null;
		}
	}
	
	// Builds the name of the image based on time and result
	private String buildImageYUVName(Result result) {
		StringBuilder strBld = new StringBuilder();
		strBld.append(new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(Calendar.getInstance().getTime()));   	
    	
    	if (result != null) {
    		if(result.getRawBytes() !=null) {
    			strBld.append("_complete.yuv");
    		} else {
    			strBld.append("_detect.yuv");
    		}
    	} else {
    		strBld.append("_fail.yuv");
    	}
    	return strBld.toString();
	}
		
	// method to save images to temp directory
	private void saveImage(byte[] imageByte, String imageYUVName, int width, int height, Parameters param) {
    	//Save the camera preview
    	if(!imageYUVName.isEmpty()){
    		try{
    			// ensure output directory
    			if (SendService.analyticsDir.mkdirs() || SendService.analyticsDir.isDirectory())
    			{
    				// convert image preview format YUV to RGB
    				int format = param.getPreviewFormat();
    				if (format != ImageFormat.NV21) {
    					Log.e(TAG, "Previewing unsupported format " + String.valueOf(format));
    				}else {
    					// No need to convert to rgb image
    					File outputFile = new File(SendService.analyticsDir, imageYUVName);
    					BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile));
    					bos.write(imageByte);
    					bos.flush();
    					bos.close();
    	
    					String imageName = imageYUVName.substring(0, imageYUVName.indexOf(".yuv")) + ".jpg"; 
    					File imgFile = new File(SendService.analyticsDir, imageName);
    					Size size = param.getPreviewSize();
    					createImageColour(imageByte, size.width, size.height, imgFile);
    				}
    			}else {
    				throw new IOException("Could not create analytics directory");
    			}
			} catch (FileNotFoundException e) {
				Log.e(TAG, e.getMessage());
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			}
    	}
	}	
	
	// saves byte array to image
	public static void createImageGray(byte[] imageByte, int w, int h, File output) throws IOException {
		//Log.d(TAG, "Creating image with W: " + String.valueOf(w) + ", H:" + String.valueOf(h));
		int[] pixels = YUVtoRGBConvertor.applyGrayScale(imageByte, w, h);
		Bitmap image = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);    			
		
		FileOutputStream outStream = new FileOutputStream(output);
		image.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
		outStream.close();
		image.recycle();
		Log.d(TAG, "Wrote output " + output.getName());
	}
	
	// saves byte array to image
	public static void createImageColour(byte[] imageByte, int w, int h, File output) throws IOException {
		//Log.d(TAG, "Creating image with W: " + String.valueOf(w) + ", H:" + String.valueOf(h));
		byte[] pixels = YUVtoRGBConvertor.YUVtoRGB(imageByte, w, h);

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inMutable = true;
		options.inPurgeable = true;
		Bitmap bitmap;
		bitmap = BitmapFactory.decodeByteArray(pixels, 0, pixels.length, options); 
		
		FileOutputStream outStream = new FileOutputStream(output);
		bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
		outStream.close();

		bitmap.recycle();
		bitmap = null;
		System.gc();
		
		Log.d(TAG, "Wrote output " + output.getName());
	}
}
