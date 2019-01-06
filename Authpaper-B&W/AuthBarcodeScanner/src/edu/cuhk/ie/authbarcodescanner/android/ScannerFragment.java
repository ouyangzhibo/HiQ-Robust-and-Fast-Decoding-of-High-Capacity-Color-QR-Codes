/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewParent;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.color.Classifier;
import com.google.zxing.color.LDA;
import com.google.zxing.color.QDA;

import edu.cuhk.ie.authbarcodescanner.android.camera.CameraManager;
import edu.cuhk.ie.authbarcodescanner.android.camera.CameraOverlay;
import edu.cuhk.ie.authbarcodescanner.android.history.HistoryDbHelper;
import edu.cuhk.ie.authbarcodescanner.android.R;

/**
 * The 2D barcode scanning fragment activity, the details of decoding and camera are handled elsewhere
 */
public class ScannerFragment extends StandardFragment implements SurfaceHolder.Callback {
	public static final String TAG=ScannerFragment.class.getSimpleName();
	private static final int layoutXML=R.layout.fragment_main;
		
	//Static variables
	public static final Set<BarcodeFormat> oneDFormats=EnumSet.of(
			BarcodeFormat.UPC_A,BarcodeFormat.UPC_E,BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,BarcodeFormat.RSS_14,BarcodeFormat.RSS_EXPANDED,
            BarcodeFormat.CODE_39,BarcodeFormat.CODE_93,BarcodeFormat.CODE_128,BarcodeFormat.ITF,
            BarcodeFormat.CODABAR);
	public static final Set<BarcodeFormat> twoDFormats=EnumSet.of(
			BarcodeFormat.DATA_MATRIX,
			BarcodeFormat.AZTEC,BarcodeFormat.PDF_417);
	public static final Set<BarcodeFormat> defaultFormats=EnumSet.of(BarcodeFormat.QR_CODE);
	
	private CameraManager camManager=null;
	private SurfaceView surfaceView=null;
	private CameraOverlay camOverlay=null;
	private TextView feedbackMsg=null;
	
	private boolean hasSurface=false, isDecodingPaused=false;
	private ScannerFragmentHandler handler;
	private Collection<BarcodeFormat> defaultBarcodeFormat;
		
	private SparseArray<partialResult> partialResult;
	private int displayMessageCounter;
	
	private static final int RESULT_SCAN_LOCALFILE=3;
	private static final int[] menuToDelete={R.id.action_returnScanner};

	//private Classifier colorClassifier;
	private static final String MODELPATH_Nexus5 = "colorClassifier/model_nexus5.txt";
	private static final String MODELPATH_Print = "colorClassifier/model_print_alldevices.txt";
	private static final String MODELPATH_Screen = "colorClassifier/model_screen_alldevices.txt";
	
	private int scanColororBWorBoth=0;
	
	public ScannerFragment() {
		setLayout(layoutXML);
	}
	
	@Override
	public void onAttach(Activity activity) {
		// TODO Auto-generated method stub
		super.onAttach(activity);
	}
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		this.setHasOptionsMenu(true);
	}
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
		//Hard code to make create menu function works properly after proguard
	    super.onCreateOptionsMenu(menu, inflater);
	    if(menuToDelete !=null){
			for(int i=0,l=menuToDelete.length;i<l;i++){
				if(menuToDelete[i] >0) menu.removeItem(menuToDelete[i]);
			}
		}
	}
	public static Classifier loadQDA(Context context){
		//String path = (android.os.Build.MODEL.contains("Nexus 5"))? MODELPATH_Nexus5 : MODELPATH_Print;
		//String path = (android.os.Build.MODEL.contains("Nexus 5"))? MODELPATH_Nexus5 : MODELPATH_Screen;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);		
		String path = (prefs.getBoolean("IS_DECODE_SCREEN", false))? MODELPATH_Screen:MODELPATH_Print;		
		return loadQDA(context, path);
	}
	/**
	 * load classifier from model file 
	 * @return
	 */
	private static Classifier loadLDA(Context context, String path) {
		double[][] means = null;
		double[][] covMat = null;
		int[] groupList = null;
		double[] probList = null;
		
		try {
			InputStream in = context.getAssets().open(path);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			// read classnum and dimension
			String[] items = br.readLine().split(" ");
			int classNum = Integer.parseInt(items[0]);
			int dim = Integer.parseInt(items[1]);
			
			means = new double[classNum][dim];
			covMat = new double[dim][dim];
			groupList = new int[classNum];
			probList = new double[classNum];
			// read group list
			items = br.readLine().split(" ");
			for(int i=0; i<classNum; i++) 
				groupList[i] = Integer.parseInt(items[i]);
			// read probability list
			items = br.readLine().split(" ");
			for(int i=0; i<classNum; i++) 
				probList[i] = Double.parseDouble(items[i]);
			// read means
			for(int i=0; i<classNum; i++) {
				items = br.readLine().split(" ");
				for(int j=0; j<dim; j++) {
					means[i][j] = Double.parseDouble(items[j]);
				}
			}
			// read covMat
			for(int i=0; i<dim; i++) {
				items = br.readLine().split(" ");
				for(int j=0; j<dim; j++) {
					covMat[i][j] = Double.parseDouble(items[j]);
				}
			}
			br.close();
			in.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "model path wrong!");
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		Log.d(TAG, "finish loading!\n parameters: " + means[0][0] + " " + means[1][0] + "\n" + groupList.toString() + " " + covMat[0][0]);
		return new LDA(means, covMat, groupList, probList);
	}
	
	public static Classifier loadQDA(Context context, String path) {
		double[][] means = null;
		double[][][] covMat = null;
		int[] groupList = null;
		double[] probList = null;
		double[] dets = null;
		
		try {
			InputStream in = context.getAssets().open(path);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			// read classnum and dimension
			String[] items = br.readLine().split(" ");
			int classNum = Integer.parseInt(items[0]);
			int dim = Integer.parseInt(items[1]);
			
			means = new double[classNum][dim];
			covMat = new double[classNum][dim][dim];
			groupList = new int[classNum];
			probList = new double[classNum];
			dets = new double[classNum];
			// read group list
			items = br.readLine().split(" ");
			for(int i=0; i<classNum; i++) 
				groupList[i] = Integer.parseInt(items[i]);
			// read probability list
			items = br.readLine().split(" ");
			for(int i=0; i<classNum; i++) 
				probList[i] = Double.parseDouble(items[i]);
			// read means
			for(int i=0; i<classNum; i++) {
				items = br.readLine().split(" ");
				for(int j=0; j<dim; j++) {
					means[i][j] = Double.parseDouble(items[j]);
				}
			}
			// read covMat
			for(int k=0; k<classNum; k++) {
				for(int i=0; i<dim; i++) {
					items = br.readLine().split(" ");
					for(int j=0; j<dim; j++) {
						covMat[k][i][j] = Double.parseDouble(items[j]);
					}
				}
			}
			// read det of covMat
			items = br.readLine().split(" ");
			for(int i=0; i<classNum; i++) {
				dets[i] = Double.parseDouble(items[i]);
			}
			br.close();
			in.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "model path wrong!");
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		Log.d(TAG, "###finish loading!\n parameters: " + means[0][0] + " " + means[1][0] + "\n" + groupList.toString() + " " + covMat[0][0]);
		return new QDA(means, covMat, groupList, probList, dets);
	}
	
	public Classifier getColorClassifier(){
		//if(this.colorClassifier==null) this.colorClassifier = loadQDA(context);
		//return this.colorClassifier;
		return loadQDA(context);
	}
	
	@Override
	public void onResume(){
		super.onResume();		
		if(this.context ==null) return;
		//Do the initializations here so that it will be called after the help page is displayed
		camManager = new CameraManager(this.context);
		camOverlay = (CameraOverlay) getView().findViewById(R.id.view_cameraoverlay);
		camOverlay.setCameraManager(camManager);
				
		handler = null;
		surfaceView = (SurfaceView) getView().findViewById(R.id.view_camera);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// The activity was paused but not stopped, so the surface still exists.
			// surfaceCreated() won't be called, so init the camera here.
			initCamera(surfaceHolder);
		} else {
			// Install the callback and wait for surfaceCreated() to init the camera.
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		feedbackMsg = (TextView) getView().findViewById(R.id.view_feedbackmsg);
		
		//Set the buttons
		( (Button) getView().findViewById(R.id.view_openTorch) )
			.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					openTorch();
				}
			});
		//Disable file scanning for the free version
		Button localFileButton=(Button) getView().findViewById(R.id.view_localFile);
		if(edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer && localFileButton !=null){			
			localFileButton.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v){
					if(isDecodingPaused) alert("Not available as the app is decoding a local image.");
					else{
						if(!edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer) return;
						Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
						intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
						//intent.setData(Uri.fromFile(Environment.getExternalStorageDirectory()));
						intent.setType("*/*");
						intent.addCategory(Intent.CATEGORY_OPENABLE);
						startActivityForResult(Intent.createChooser(intent,null), RESULT_SCAN_LOCALFILE);						
					}
				}
			});
		}else if(localFileButton !=null){
			ViewParent layout = localFileButton.getParent();
			if(layout !=null) ((ViewGroup) layout).removeView(localFileButton);
		}
		( (Button) getView().findViewById(R.id.view_createQRcode) )
			.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v){
					startActivity(new Intent(context, EncodeActivity.class));
				}
		});
		( (Button) getView().findViewById(R.id.view_setting) )
			.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					fragmentCallback.showSetting();
				}
			});
		( (Button) getView().findViewById(R.id.view_scanqrcode) )
			.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					//0 means scan both color and black and white, 1 means scan BW only, 2 means color only
					scanColororBWorBoth=(scanColororBWorBoth+1) %3;
					Message message = handler.obtainMessage(R.id.scan_color);
					message.arg1=scanColororBWorBoth;
					message.sendToTarget();
				}
			});
		//To make the buttons distribute evenly, I need to remove them and re-add
		LinearLayout buttonList = (LinearLayout) getView().findViewById(R.id.view_buttonList);
		List<Button> dummyList = new ArrayList<Button>();
		for(int i=0, count=buttonList.getChildCount(); i<count; i++){
			View child=buttonList.getChildAt(i);
			if(child !=null && child instanceof Button)
				dummyList.add((Button) buttonList.getChildAt(i));
		}
		buttonList.removeAllViews();
		StandardButton.appendButtons(this.context, buttonList, dummyList);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
		defaultBarcodeFormat = EnumSet.copyOf(defaultFormats);
		if (prefs.getBoolean("DECODE_1D_BARCODE", false)) defaultBarcodeFormat.addAll(oneDFormats);
		if (prefs.getBoolean("DECODE_FURTHER_2D_BARCODE", false)) defaultBarcodeFormat.addAll(twoDFormats);
		partialResult=null;
		displayMessageCounter=0;
	}
	private void initCamera(SurfaceHolder surfaceHolder) {
		try {
			camManager.openDriver(surfaceHolder,surfaceView);			
			camOverlay.setLayoutParams(surfaceView.getLayoutParams());
			// Creates the handler and let it starts previewing
			 if(!isDecodingPaused) startHandler();			
		} catch (IOException ioe) {
			Log.w(TAG, ioe.toString()+" "+ioe.getMessage());
			errorAndReturn("Unexpected IO error on opening camera. "
					+ "Please close this App and restart again");
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.w(TAG, "Unexpected error initializing camera, likely not authorized. "+e.toString()+" "+e.getMessage());
			errorAndReturn("Cannot open camera. Please enable camera access in Setting -> Apps -> "
					+context.getString(R.string.app_name)+" -> Permission in order to use this app.");
		}
	}
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
			Log.e(TAG, "Surface created without holder, gracefully exiting");
			errorAndReturn("Unexpected Fatal error on drawing. "
					+ "Please close this App and restart again");
		}else if (!hasSurface) {
			hasSurface = true;
	      	initCamera(holder);
		}	
	}
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
	@Override
	public void surfaceDestroyed(SurfaceHolder holder){hasSurface = false;}
	private void startHandler(){
		if(handler ==null){
			//Setting the preference for decoding
			handler = new ScannerFragmentHandler(this, defaultBarcodeFormat, camManager, camOverlay, this.context);
			Message message = handler.obtainMessage(R.id.init_decode);
			message.sendToTarget();
			if(camOverlay !=null) camOverlay.setBackgroundColor(Color.TRANSPARENT);
		}else{
			//Resume decoding
			Message m = handler.obtainMessage(R.id.resume_decode);
			m.sendToTarget();
		}
		Message message = handler.obtainMessage(R.id.scan_color);
		message.arg1=scanColororBWorBoth;
		message.sendToTarget();
	}
	private void pauseHandler(){
		if(handler==null) return;		
		//Pause the original decoding, and pass the image to the decode thread
		Message message = handler.obtainMessage(R.id.pause_decode);
		message.sendToTarget();
		Log.d(TAG,"Scanning paused");		
	}
	/*
	 * Check the code in onResume and initCamera to write onPause()
	 */
	@Override
	public void onPause() {
		super.onPause();
		if (handler != null) {
			handler.quit();
			handler = null;
		}
		isTorchOpen=false;
		camManager.closeDriver();
		if (!hasSurface) {
			SurfaceView surfaceView = (SurfaceView) getView().findViewById(R.id.view_camera);			
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}
	}
	
/*
 * Functions called by the handler	
 */
	public void drawCameraOverlay(){
		if(feedbackMsg !=null) feedbackMsg.setVisibility(View.VISIBLE);
		if(camOverlay !=null) camOverlay.startDrawing();
	}
	/**
	 * Called by the handler when decode is successful
	 * @param rawResult
	 */
	public void handleDecodeResult(Result rawResult){
		boolean isDisplayResult=false;
		//Check if it is structure append code
		int seqNumber=rawResult.getIntMetadata(ResultMetadataType.STRUCTURED_APPEND_SEQUENCE);
		int parityNumber=rawResult.getIntMetadata(ResultMetadataType.STRUCTURED_APPEND_PARITY);
		if(seqNumber >0 && parityNumber >0){			
			partialResult tmpResult=new partialResult(rawResult, seqNumber, parityNumber);
			if(partialResult ==null) partialResult = new SparseArray<partialResult>();
			partialResult.put(tmpResult.seq, tmpResult);
			if(tmpResult.totalBlockNum > partialResult.size()){
				if(feedbackMsg !=null) feedbackMsg.setText("Structure appended code "+tmpResult.seq+" scanned. Please scan the next code.");
				Message message = handler.obtainMessage(R.id.start_decode);
				handler.sendMessageDelayed(message, 200);
				displayMessageCounter=4;
			}else{
				if(feedbackMsg !=null) feedbackMsg.setText("Merging structure appended codes into one.");
				//Joining the data in interest
				ByteArrayOutputStream joinedRawBytes = new ByteArrayOutputStream();
				String joinedText = "";				
				ByteArrayOutputStream joinedByteSegment = new ByteArrayOutputStream();
				for(int i=0,iCount=partialResult.size();i<iCount;i++){
					partialResult tmpR=partialResult.valueAt(i);
					if(tmpR ==null) continue;
					Result tmpRresult=tmpR.rawResult;
					try{
						joinedRawBytes.write(tmpRresult.getRawBytes());						
						joinedText +=tmpRresult.getText().trim();
						List<byte[]> byteSegs=tmpRresult.getByteMetadata(ResultMetadataType.BYTE_SEGMENTS);
						if(byteSegs !=null && !byteSegs.isEmpty()) joinedByteSegment.write(byteSegs.get(0));
					}catch (IOException e){ 
						try{ 
							joinedRawBytes.close();
							joinedByteSegment.close();
							return;
						}catch(IOException e2){ }
					}
				}
				//Now group the result into one QR code result
				if(joinedRawBytes.size()>0 && joinedText !=null){
					Result target = new Result(joinedText,joinedRawBytes.toByteArray(),null,rawResult.getBarcodeFormat(),null);
					rawResult.copyMetadata(target);
					if(joinedByteSegment.size()>0){
						List<byte[]> joinedByteList = new ArrayList<byte[]>();
						joinedByteList.add(joinedByteSegment.toByteArray());
						target.putMetadata(ResultMetadataType.BYTE_SEGMENTS, joinedByteList);
					}
					rawResult=target;
					isDisplayResult=true;
				}else{
					if(feedbackMsg !=null) 
						feedbackMsg.setText("Grouping the structure appended codes failed. Please re-scan all codes.");
					displayMessageCounter=2;
				}
			}
		}else isDisplayResult=true;
		if(isDisplayResult){
			if(feedbackMsg !=null) feedbackMsg.setText("Code scanned. Moving to the result page");
			ResultFragment resultFragment = new ResultFragment();
			//TODO: make it Parcelable (can be passed by set Arguments)
			resultFragment.setResult(rawResult, false);
			this.fragmentCallback.moveToFragment(TAG, resultFragment);
		}
	}
	private class partialResult{
		private final int seq;
		private final int totalBlockNum;
		private final int parity;
		private final Result rawResult;
		
		public partialResult(Result result, int sequenceNumber, int parity){
			this.rawResult=result;
			this.totalBlockNum = 0x0f & sequenceNumber;
			this.seq = 0x0f & (sequenceNumber>>4);
			this.parity = parity;
		}
	}
	public void handleDetectedBarcode(String codeType, String subflex){
		if(feedbackMsg !=null) {
			if(codeType==null)  {
				if(camOverlay !=null)
					feedbackMsg.setText(this.context.getString(R.string.msg_default_feedback)+" "+subflex);
			}
			else{
				codeType=codeType.replace("Reader", "");
				feedbackMsg.setText(codeType+" "+this.context.getString(
						R.string.msg_detected_feedback_header)+" "+subflex);
			}
		}
	}
	public void handleDetectedBarcode(String codeType, float progressPercent){		
		if(isDecodingPaused || displayMessageCounter >0){
			if(displayMessageCounter >0) displayMessageCounter--;
			return;
		}
		if(feedbackMsg ==null) return;
		codeType=codeType.replace("Reader", "");
		if(progressPercent <=0){
			feedbackMsg.setText(codeType+" "+this.context.getString(
					R.string.msg_detected_feedback_header));
		}else feedbackMsg.setText(codeType+" decoding. "+progressPercent+"% completed");
		
	}

	public void handleDetectedBarcode(String codeType, int failedChannels){		
		String decodeMsg = "Failed channels:";
		if(failedChannels % 2==1) decodeMsg+=" Cyan";
		if((failedChannels>>1) % 2==1) decodeMsg+=" Magenta";
		if((failedChannels>>2) % 2==1) decodeMsg+=" Yellow";	
		handleDetectedBarcode(codeType, decodeMsg);
	}
	
	public void handleDetectedBarcode(String codeType, int[] decodeProgress){		
		String decodeMsg = "Channel Decode Progress:";
		if(decodeProgress[0] >0) decodeMsg += (decodeProgress[0] >100)? " 1:completed" : " 1:"+decodeProgress[0]+"%";
		else decodeMsg += " 1:0%";
		if(decodeProgress[1] >0) decodeMsg += (decodeProgress[1] >100)? " 2:completed" : " 2:"+decodeProgress[1]+"%";
		else decodeMsg += " 2:0%";
		if(decodeProgress[2] >0) decodeMsg += (decodeProgress[2] >100)? " 3:completed" : " 3:"+decodeProgress[2]+"%";				
		else decodeMsg += " 3:0%";		
		handleDetectedBarcode(codeType, decodeMsg);
	}
	public void handleScanColororBW(int indicator){
		//0 means scan both color and black and white, 1 means scan BW only, 2 means color only
		scanColororBWorBoth=indicator;
		alert((scanColororBWorBoth==0)? "Scan both color and monotone 2D barcodes" 
				: (scanColororBWorBoth==1)? "Scan monotone 2D barcode only" : "Scan color QR codes only");
		( (Button) getView().findViewById(R.id.view_scanqrcode) )
		.setCompoundDrawablesWithIntrinsicBounds(0, 
				(scanColororBWorBoth==0)? R.drawable.view_scanbothqrcode 
				: (scanColororBWorBoth==1)? R.drawable.result_qrcode2 : R.drawable.result_colorqrcode
				, 0, 0);
	}
/*
 * Functions called by the buttons
 */
	private boolean isTorchOpen=false;
	private boolean openTorch(){
		if(camManager !=null && camManager.isOpen()){
			isTorchOpen = !isTorchOpen;
			camManager.setTorch(isTorchOpen);
			return true;
		}
		return false;
	}	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent){
		if(resultCode != android.app.Activity.RESULT_OK) return;		
		switch(requestCode){
      		case RESULT_SCAN_LOCALFILE:
      			if(!edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer) return;
      			alert("Scanning the selected image",true);
      			Log.d(TAG,"Start decoding the local file");	      			
      			String fileName=intent.getData().toString();
      			try{
					//InputStream in = new BufferedInputStream(new FileInputStream(imageFile));	      				
      				InputStream in = this.getActivity().getContentResolver().openInputStream(
      						Uri.parse(fileName));						
      				if(in ==null) throw new IOException();
					new ScanFileTask().execute(in);
			    }catch(IOException e){ 
			    	alert("Cannot read the selected image. Please enable SD card access in Setting -> Apps -> "
					+context.getString(R.string.app_name)+" -> Permission in order to use this feature.");
					return;
			    }
      		break;
      	default:
      		Log.d(TAG,"Invalid data returned by other activities");
		}
	}

	private class ScanFileTask extends AsyncTask<InputStream, Integer, Result> {
	    protected void onPreExecute(){
	    	pauseHandler();
	    	if(feedbackMsg !=null) feedbackMsg.setText("Decoding Selected Image");
	    	isDecodingPaused=true;
	    }	    
		protected Result doInBackground(InputStream... instreams){
			//Set hints
			Map<DecodeHintType,Object> hints = new EnumMap<DecodeHintType,Object>(DecodeHintType.class);
  			hints.put(DecodeHintType.POSSIBLE_FORMATS, defaultBarcodeFormat);
  			hints.put(DecodeHintType.TRY_HARDER, true);
  			hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, camOverlay);  			
  			//Start decoding
  			InputStream in = instreams[0];
  			android.graphics.Bitmap bmp = BitmapFactory.decodeStream(in);  			
  			if(bmp ==null) return null;
  			Classifier colorClassifier=loadQDA(context);
  			Result rawResult=edu.cuhk.ie.authbarcodescanner.android.decodethread
  					.DecodeThreadHandler.fileDecode(bmp,hints,context,colorClassifier);  
  			if(rawResult ==null){
  				//Try shuffle bits
  				hints.put(DecodeHintType.Shuffled_Codeword, true);
  				rawResult=edu.cuhk.ie.authbarcodescanner.android.decodethread
  	  					.DecodeThreadHandler.fileDecode(bmp,hints,context,colorClassifier);
  			}
  			bmp.recycle();
  			return rawResult;
	    }
	    protected void onPostExecute(Result rawResult){
	    	if(rawResult !=null){
  				if(rawResult.getRawBytes() !=null){
  					int seqNumber=rawResult.getIntMetadata(ResultMetadataType.STRUCTURED_APPEND_SEQUENCE);
  					int parityNumber=rawResult.getIntMetadata(ResultMetadataType.STRUCTURED_APPEND_PARITY);
  					if(seqNumber >0 || parityNumber >0){//Hard-code prevent handle the result as structure append code
  						rawResult.putMetadata(ResultMetadataType.STRUCTURED_APPEND_SEQUENCE, 0);
  						rawResult.putMetadata(ResultMetadataType.STRUCTURED_APPEND_PARITY, 0);
  					}
  					handleDecodeResult(rawResult);
  				}
  				else alert(rawResult.getBarcodeFormat().toString()
  						+" is detected, but cannot extract the data inside",true);
  			}else alert("No 2D barcode is detected.",true);
	    	startHandler();
	    	isDecodingPaused=false;
	    }
	}
/*
 * Other functions	
 */
	public void LogMsg(String message) {
		if(message !=null && !message.isEmpty()) Log.d(TAG, message);
	}
}