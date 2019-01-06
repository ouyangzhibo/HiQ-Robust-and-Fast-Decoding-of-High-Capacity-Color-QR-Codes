/*
 Copyright (C) 2014 Solon in MobiTeC, CUHK 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources.NotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.text.SpannableString;
import android.text.util.Linkify;
import android.view.KeyEvent;
import android.view.Menu;
import android.widget.Toast;
import com.google.zxing.ResultPoint;
import edu.cuhk.ie.authbarcodescanner.analytic.GetCameraInfoTask.CameraInfo;
import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;
import edu.cuhk.ie.authbarcodescanner.analytic.PostData;
import edu.cuhk.ie.authbarcodescanner.android.SendService.SendDataBinder;
import edu.cuhk.ie.authbarcodescanner.android.SendService.ServiceHandler;
import edu.cuhk.ie.authbarcodescanner.android.decodethread.DecodeThreadHandler.DetectResult;
import edu.cuhk.ie.authbarcodescanner.certificate.CertificateDbEntry;
import edu.cuhk.ie.authbarcodescanner.certificate.CertificateDbHelper;
import edu.cuhk.ie.authbarcodescanner.certificate.KeystoreHolder;

/**
 * This class provides callback functions to the fragments to access its parent activity.
 * This also implements common functions of the activities.
 * You can also set whether the scanner is in deluxe version by setting the isDeluxeVer boolean
 * Remember to call init() on onCreate() function to use
 * @author solon li
 *
 */
public abstract class fragmentCallback extends ActionBarActivity{
	private static final String TAG=fragmentCallback.class.getSimpleName();
	public static final boolean isDeluxeVer=false;
	
	private int containerID=-1;
	private int menuID=-1;
	public Menu menu;
	private int[] menuToDeleteInFree=null;
	protected boolean setMenuToDeleteInFree(int[] items){
		menuToDeleteInFree=items;
		return true;
	}
	
	// send data service
	SendService mService;
	boolean mBound = false;
	private ServiceHandler sHandler;
	boolean serviceStatus = false;
		
	// flag to send user data when service is ready
	boolean flagSendUserInfo = false;
	
	// array to track images if extra scan stats are enabled
	private ArrayList<String> imgList = new ArrayList<String>();
	private ArrayList<DetectResult> detectList = new ArrayList<DetectResult>();
	
	// log to track detected locations
	public final static File detectLog = new File(Environment.getExternalStorageDirectory().getPath() + "/AuthBarcodeScanner/temp/detectLog.txt");
	
	@Override 
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// start data sending service
		// start the send data service
		Log.d(TAG,  "onCreate");

		Intent intent = new Intent(this, SendService.class);
		startService(intent);
		serviceStatus = bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		Log.d(TAG, "Service status " + String.valueOf(serviceStatus));
		// init certificate database
		certDb = new CertificateDbHelper(this, CertificateDbHelper.DB_NAME, null, CertificateDbHelper.DB_VERSION);
		KeystoreHolder.initInstance();
	}

	protected void init(Bundle savedInstanceState, int containerID, 
			int menuID, Fragment firstFragment){
		this.containerID=containerID;
		this.menuID=menuID;
		if (savedInstanceState ==null) {
			//Add the first fragment into the container view
			this.getSupportFragmentManager().beginTransaction()
				.setCustomAnimations(R.anim.enter_from_right , R.anim.exit_to_left)
				.add(containerID, firstFragment).commit();
		}
		//TODO:if a intent is passed here, what should we do?
		//Show help on first launch
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		if(sharedPref.getBoolean("isFirstRun", true)){
			sharedPref.edit().putBoolean("isFirstRun", false).apply();
			openHelp();
		}
	}

	// send camera stats
	public void sendCameraStat(CameraInfo camInfo) {
		try {
			JSONObject jsonObj = camInfo.jsonObj;
			String title = camInfo.reqType == SendService.INIT_CAMERA_INFO ? SendService.T_NEW_CAM : SendService.T_SCANSTAT;
			String mail_body = jsonObj.toString(4);
			
			// If JSON has scan extra, zipped images need to be sent. Need to put file into ArrayList<uri> to trigger later response
			if (jsonObj.has("statExtraName") && jsonObj.has("statExtraPRN")) {
				Uri fileUri = Uri.parse(jsonObj.getString("statExtraPRN"));
				ArrayList<Uri> uriList = new ArrayList<Uri>();
				uriList.add(fileUri);
				initPostData(title, mail_body, camInfo.reqType, uriList, false);
				
				// write detection results to file to be zipped
				// blocking - may need to move if problematic
				writeDetectResult();				
				
				// send extra statistics if requested
				ArrayList<String> imgListToZip = (ArrayList<String>) imgList.clone();
				if (detectLog.exists()) {
					imgListToZip.add(detectLog.getName());
				}
				Log.d(TAG, "Zip list contents");
				for(String fn : imgListToZip) {
					Log.d(TAG, fn);
				}
				
				ZipSendInfo zipJPG = new ZipSendInfo(jsonObj.getString("statExtraPRN"), imgListToZip, ".jpg", true);
				zipImages(zipJPG);

				ZipSendInfo zipYUV = new ZipSendInfo(jsonObj.getString("statExtraPRN"), imgListToZip, ".yuv", false);
				zipImages(zipYUV);
				
				resetImageList();
			}
			else {
				initPostData(title, mail_body, camInfo.reqType, null, false);
			}
		} catch (JSONException e) {
			Log.e(TAG, "Error converting JSON to string " + e.toString());
			e.printStackTrace();
		}
	}

	/*
	 * Use analytics thread to zip up images then sends them
	 * Called after scan stat in queue 
	 */
	public void zipImages(ZipSendInfo zipInfo) {
		if (sHandler == null) {
			// try get handler if does not exist
			if (mService != null) {
				sHandler = mService.getServiceHandler();	
			}
			else {
				Log.e(TAG, "Error getting send service");
			}
		}
		if (sHandler == null) {
			Log.e(TAG, "Failed to send message, handler does not exist");
			return;
		}
		// request zip and send
		sHandler.obtainMessage(SendService.IMG_ZIP, zipInfo).sendToTarget();
	}
	
	// wrapper to pass image zip data to send service
	public class ZipSendInfo {
		public String zipName;
		public ArrayList<String> fileList;
		public String fileExt;
		public boolean sendZip;
		
		public ZipSendInfo(String zipName, ArrayList<String> fileList, String fileExt, Boolean sendZip) {
			this.zipName = zipName;
			this.fileList = fileList;
			this.fileExt = fileExt;
			this.sendZip = sendZip;
		}
	}
	
	// records name of image and yuv saved
	public void addImageToList(DetectResult result) {
		String imageYUVName = result.imageYUVName;
		
		// records file name with no extension
		String filename = imageYUVName.substring(0, imageYUVName.indexOf(".yuv"));
		this.imgList.add(filename);
		Log.d(TAG, "Recorded " + filename +" for a total of " + String.valueOf(this.imgList.size()) + " files");
		
		// records detect result
		if (result.results != null) {
			Log.d(TAG, "Logging detect result for " + result.imageYUVName);
			this.detectList.add(result);			
		}
	}

	// clears image history
	public void resetImageList() {
		this.imgList.clear();
		this.detectList.clear();
	}
	
	// write detect log file
	public void writeDetectResult() {
		try {
			FileOutputStream fos = new FileOutputStream(detectLog);
			BufferedOutputStream writer = new BufferedOutputStream(fos);
			
			// write header row if new file
			String outLine = "File,X,Y\n";
			writer.write(outLine.getBytes());
			
			for(DetectResult result : detectList) {
				for(ResultPoint resPt : result.results) {
					outLine = result.imageYUVName + "," + resPt.getX() + "," + resPt.getY() + "\n";
					writer.write(outLine.getBytes());
				}
			}
			writer.close();
			
		} catch (IOException e) {
			Log.e(TAG, "Error writing to detect log");
			e.printStackTrace();
		}		
	}
	
// async task to save keystore
	public class SaveKeyStoreTask extends AsyncTask<KeyStore, Void, Boolean> {
		@Override
		protected Boolean doInBackground(KeyStore... entry) {
			Log.d(TAG, "Saving key store");
			if(entry == null || entry.length < 1 || entry[0] == null) return false;
			try {
				KeyStore keystore = entry[0];
				FileOutputStream fos = openFileOutput(storeName, Context.MODE_PRIVATE);
				keystore.store(fos, "client1".toCharArray());
				fos.close();
				return true;
			} catch (FileNotFoundException e) { } 
			catch (KeyStoreException e) { }
			catch (NoSuchAlgorithmException e) { } 
			catch (CertificateException e) { } 
			catch (IOException e) { }
			return false;
		}
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if (!result) {
				Toast toast = Toast.makeText(getApplication(), "New digital certificates could not be stored.", Toast.LENGTH_SHORT);
				toast.show();				
			}
		}
	}	
	

/*
 * Functions to be called by fragments
 */
	private KeyStore trustKeyStore=null;
	private static final String storeName = "certStore.keystore";
	// certificate database
	private CertificateDbHelper certDb;
	//private static final String signerCert = "client1 public certificate";
	public KeyStore startKeyStore(){
		Log.d(TAG, "Starting key store");
		// if keystore already initiated, use keystore from keystore holder instead
		if (KeystoreHolder.getInstance().keySet) {
			Log.d(TAG, "Using keystore holder");
			trustKeyStore = KeystoreHolder.getInstance().getKeystore();
			return trustKeyStore;
		}
		//if(trustKeyStore !=null) return trustKeyStore;
		KeyStore trustStore=null;
	  	try {  
	  		try{
	  			if(android.os.Build.VERSION.SDK_INT <=android.os.Build.VERSION_CODES.KITKAT)
	  				trustStore = KeyStore.getInstance(KeyStore.getDefaultType(), "SC");
	  		}catch(java.security.NoSuchProviderException e2){ }
	  		if(trustStore ==null) trustStore = KeyStore.getInstance(KeyStore.getDefaultType(), "BC");
	  		try{
		  		//TODO: Security best practice. Do not make the password store here in plain text
		  		//However, generating hash password for each application from server when first start takes a lot of work.
		  		//I prefer to accept the risk :O)
	  			InputStream instream = openFileInput(storeName);
	  			//if(instream ==null) instream = this.getResources().openRawResource(R.raw.client1);
	  			//Let the keystore create a new empty store at the first time
	  			trustStore.load(instream, "client1".toCharArray());
	  			instream.close();
	  			trustKeyStore = trustStore;
	  			Log.d(TAG,  "Loaded exisitng keystore");
	  			return trustStore;
	  		} catch(FileNotFoundException e2){ 
	  			trustStore.load(null, "client1".toCharArray());
	  		}catch(java.io.IOException e2){
	  			Log.d(TAG,"Cannot load the internal keystore, starting a new one.");
	  			trustStore.load(null, "client1".toCharArray());
	  		}
	  	}catch (Exception e){
	  		Log.e(TAG, "Cannot load the key store. Details: "+e.getMessage());
	  		return null;
		}

	  	//Log.d(TAG, "Reading raw files into keystore");
	  	//Auth2DbarcodeDecoder.setDefaultCert(trustStore, this.getResources().openRawResource(R.raw.rootcert));
	  	java.lang.reflect.Field[] fields=R.raw.class.getFields();
	    for(int i=0; i < fields.length; i++){
    		try{
				boolean saved = Auth2DbarcodeDecoder.storeCertificate(trustStore, 
						this.getResources().openRawResource(fields[i].getInt(fields[i])));
				if (saved) {
					// save certificate to keystore
					CertificateDbEntry newEntry =  Auth2DbarcodeDecoder.getCertDbEntry();
					certDb.insertCert(newEntry.getAlias(), CertificateDbHelper.CERT_SYS, newEntry.getExpire(), newEntry.getIssued());
					Log.d(TAG, "Saved new certificate to store");
				}
    		}catch(NotFoundException e){ }
			catch(IllegalAccessException e){ }
			catch(IllegalArgumentException e){ }
	    }
	  	//Auth2DbarcodeDecoder.storeCertificate(trustStore, this.getResources().openRawResource(R.raw.democuhkcert));
	  	trustKeyStore=trustStore;
	  	return trustStore;
	}
	protected boolean saveKeyStore(){
		Log.d(TAG, "save key store");
		new fragmentCallback.SaveKeyStoreTask().execute(trustKeyStore);
		return true;
	}
	public abstract void onReturnResult(int requestCode, int resultCode, Intent data);
	
	public void onFatalErrorHappen(String tag) {	
		if(this.getSupportFragmentManager().getBackStackEntryCount() > 0 )
            this.getSupportFragmentManager().popBackStack();
		else{
			this.setResult(RESULT_CANCELED);
			this.finish();
		}
	}
	public void moveToFragment(String sourceTag, Fragment target){
		if(containerID ==-1) return;
		this.getSupportFragmentManager().beginTransaction()
		.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left)
			.replace(containerID, target)
			.addToBackStack(null)
			.commit();
	}

	public void moveBackFragment() {
		this.getSupportFragmentManager().beginTransaction()
		.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right)
		.commit();
		this.getSupportFragmentManager().popBackStackImmediate();
	}

	public void openHelp(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.about_title);
        final SpannableString s = new SpannableString(getText( 
        		(isDeluxeVer)? R.string.about_text_deluxe : R.string.about_text ));
        Linkify.addLinks(s, Linkify.WEB_URLS);
        builder.setMessage(s);
        builder.setNegativeButton("OK", null);
        builder.show();
	}
	public void showSetting(){
		startActivity(new Intent(this, SettingFragment.class));
	}
	
	/*
	 *  Update menu
	 */
	public void updateMenuTitle(int menuItem, int menuTitle, boolean active) {
		menu.findItem(menuItem).setTitle(menuTitle);
		menu.findItem(menuItem).setEnabled(active);
	}
	
/*
 * 	Activity related functions
 */	
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		if(menuID ==-1) return false;
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(menuID, menu);
		this.menu = menu;
		//Remove selected buttons in free version
		if(!isDeluxeVer){
			//menu.removeItem(R.id.action_logout);
			if(menuToDeleteInFree !=null){
				for(int i=0,l=menuToDeleteInFree.length;i<l;i++){
					if(menuToDeleteInFree[i] >0) menu.removeItem(menuToDeleteInFree[i]);
				}
			}
		}
		return true;
	}
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if( this.getSupportFragmentManager().getBackStackEntryCount() > 0 ){
                this.getSupportFragmentManager().popBackStack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }


	/* 
	 *  Send data service via handler
	 */
	public void initPostData(String subject, String content, int type, ArrayList<Uri> attachmentList, boolean email) {
		if (sHandler == null) {
			// try get handler if does not exist
			if (mService != null) {
				sHandler = mService.getServiceHandler();	
			}
			else {
				Log.e(TAG, "Error getting send service");
			}
		}
		if (sHandler == null) {
			Log.e(TAG, "Failed to send message, handler does not exist");
			return;
		}
		
		PostData pData = new PostData(this);
		pData.setSubject(subject);
		pData.setContent(content);
		pData.setAttach(attachmentList);
		pData.setType(type); // flag for post send action
		
		Message msgPost = sHandler.obtainMessage(SendService.POST_NEW);
		msgPost.obj = pData;
		Log.d(TAG,  "Sending new post to handler");
		msgPost.sendToTarget();		
	}
		
	// callbacks for service binding
	private ServiceConnection mConnection = new ServiceConnection() {		
		@Override 
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.d(TAG, "service connected");
			SendDataBinder binder = (SendDataBinder) service;
			mService = binder.getService();
			mBound = true;
			sHandler = mService.getServiceHandler();			
		}
		
		@Override 
		public void onServiceDisconnected(ComponentName className) {
			Log.d(TAG, "service disconnected");
			mBound = false;
			mService = null;
		}
	};

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		if (mBound) {
			// clear up temp images
			sHandler.obtainMessage(SendService.IMG_CLEAR, imgList).sendToTarget();		
			// unbind service
			unbindService(mConnection);
			mBound = false;
		}			
	}	
	
}
