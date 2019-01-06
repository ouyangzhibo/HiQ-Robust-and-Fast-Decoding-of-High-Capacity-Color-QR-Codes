/*
 Copyright (C) 2015 Marco in MobiTeC, CUHK 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import edu.cuhk.ie.authbarcodescanner.analytic.Compress;
import edu.cuhk.ie.authbarcodescanner.analytic.PostData;
import edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.ZipSendInfo;

public class SendService extends Service {
	private static final String TAG = SendService.class.getSimpleName();
		
	// interface for clients to bind to
	private final IBinder mBinder = new SendDataBinder();
	
	// references to this services handler
	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	
	// messages understood by this service
	public final static int POST_NEW  = 10;
	public final static int POST_SENT = 11;
	public final static int MAIL_NEW  = 20;
	public final static int MAIL_SENT = 21;
	//public final static int IMG_SAVE = 30;
	public final static int IMG_ZIP = 31;
	public final static int IMG_CLEAR = 32;
	
	// recognised subject types
	public static final String T_NEW_USER = "NewUser";
	public static final String T_NEW_CAM = "NewCamera";
	public static final String T_SCANSTAT = "ScanStat";
	public static final String T_FEEDBACK = "UserFeedback";
	public static final String T_SCANEXTRA = "ScanExtra";
	
	//Return type flags
	public final static int INIT_USER_INFO = 0;
	public final static int INIT_CAMERA_INFO = 10;
	public final static int SCAN_INFO = 11;
	public final static int USER_FEEDBACK = 20;
	public final static int SCAN_EXTRA = 30;
	
	// preference flags
	public final static String POST_USERINFO = "ToBePosted";
	public final static String POST_CAMERA = "ToBePostedCamera";
	
	// directory for storing analytics data
	public final static File analyticsDir = new File(Environment.getExternalStorageDirectory().getPath() + "/AuthBarcodeScanner/temp/");
	public final static File testDir = new File(Environment.getExternalStorageDirectory().getPath() + "/AuthBarcodeScanner/manual/");
    //ArrayList<String> imgList = new ArrayList<String>();

	// message object to set title from service
	public static class TitleOptions {
		int menuItem;
		int menuTitle;
		boolean active;
		public TitleOptions(int menuItem, int menuTitle, boolean active) {
			this.menuItem = menuItem;
			this.menuTitle = menuTitle;
			this.active = active;			
		}
		public TitleOptions() {
			// TODO Auto-generated constructor stub
		}
	}	
	
	// Handler that receives messages from other thread
	public final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}
		@Override 
		public void handleMessage(Message msg) {
			Log.d(TAG, "Message received " + String.valueOf(msg.what));
			switch(msg.what) {
				case(POST_NEW):
					PostData pData = (PostData) msg.obj;
					sendPost(pData);
					break;
				case(POST_SENT):
					PostData sentPostData = (PostData) msg.obj;
					updatePrefs(sentPostData);
					break;
				case(IMG_ZIP):
					ZipSendInfo zipInfo = (ZipSendInfo) msg.obj;
					Compress compress = new Compress(zipInfo.fileList, zipInfo.zipName, zipInfo.fileExt);
					if (compress.zip()) {
						if(zipInfo.sendZip) {
							PostData postZip = new PostData(getBaseContext());
							String outFile = zipInfo.zipName + zipInfo.fileExt.replace(".", "_") + ".zip";
							postZip.setFileAttach(outFile);
							postZip.sendExtraStat();					
						}
						compress.removeImageFiles();
					}
					else {
						Log.e(TAG, "Failed to create zip");
					}
					break;
				case(IMG_CLEAR):
					// only called when exiting the app
					ArrayList<String> fileList = (ArrayList<String>) msg.obj;
					Compress compressJpg = new Compress(fileList, null, ".jpg");
					compressJpg.removeImageFiles();
					Compress compressYuv = new Compress(fileList, null, ".yuv");
					compressYuv.removeImageFiles();
					break;
				default:
					Log.e(TAG, "Unknown message received " + String.valueOf(msg.what));
					break;
			}
		}
	}
		
	// reads file into byte array. used for image differences
	public static byte[] readFileIntoByteArray(File file) throws IOException {
		FileInputStream inStream = new FileInputStream(file);
		byte[] data = new byte[(int) file.length()];
		inStream.read(data);
		inStream.close();
		return data;
	}
	
	// sends post
	public void sendPost(PostData pData) {
		Log.d(TAG, "sendPost");			
		boolean postSuccess = false;
		try {
			Boolean status = pData.send();
			Log.d(TAG, "Post sent status " + status.toString());
			postSuccess = status;
		} catch (Exception e) {
			Log.e(TAG, "There was an error sending the post");
			e.printStackTrace();
			postSuccess = false;
		}
		Message msg = mServiceHandler.obtainMessage(POST_SENT);
		pData.setPostSent(postSuccess);
		msg.obj = pData;
		Log.d(TAG, "Sending sent post status to handler");
		msg.sendToTarget();
	}
		
	// update status based on post/email type and status
	public void updatePrefs(PostData pData) {
		int type = pData.getPostType();
		boolean status = pData.getPostSent();
		updatePrefs(type, status);
	}
	
	public void updatePrefs(int type, boolean status) {
		if (type == USER_FEEDBACK) {
			Log.d(TAG, "broadcasting sent status back");
			// broadcast change to feedback activity
			Intent updateTitleIntent = new Intent("updateTitle");
			updateTitleIntent.putExtra("status", status);
			LocalBroadcastManager.getInstance(this).sendBroadcast(updateTitleIntent);
		}
		else if (status) {
			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
			switch(type) {
				case(INIT_USER_INFO):
					Log.d(TAG, "Setting user info to be sent to false");
					sharedPref.edit().putBoolean(POST_USERINFO, false).apply();	
					break;
				case(INIT_CAMERA_INFO):
					Log.d(TAG, "Setting camera info to be sent to false");
					sharedPref.edit().putBoolean(POST_CAMERA, false).apply();	
					break;
			}
		}	
	}
		
	// method to get handler to activity
	public ServiceHandler getServiceHandler() {
		Log.d(TAG, "returning handler");
		return mServiceHandler;
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "Creating service");
		// start new thread for service
		HandlerThread thread = new HandlerThread("SendServiceThread");
		thread.start();
		// get threads looper and use in this handler
		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);	
	}	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "Starting service");
		return START_NOT_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.d(TAG, "onBind");
		return mBinder;
	}
	
	@Override 
	public void onDestroy() {
		Log.d(TAG, "onDestroy");
	}
	
	public class SendDataBinder extends Binder {
		public SendService getService() {
			return SendService.this;
		}	
	}
}
