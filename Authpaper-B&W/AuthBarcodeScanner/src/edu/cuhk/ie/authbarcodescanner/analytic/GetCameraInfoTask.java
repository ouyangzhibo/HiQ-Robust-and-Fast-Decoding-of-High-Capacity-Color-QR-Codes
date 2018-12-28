/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import edu.cuhk.ie.authbarcodescanner.android.ScannerFragmentHandler;
import edu.cuhk.ie.authbarcodescanner.android.SendService;
import edu.cuhk.ie.authbarcodescanner.android.fragmentCallback;
import edu.cuhk.ie.authbarcodescanner.android.camera.CameraManager;

public class GetCameraInfoTask extends AsyncTask<Void, Void, Void> {
	private static final String TAG = GetCameraInfoTask.class.getSimpleName();
	
	//Task parameters
	private boolean taskSuccess = false;
	private JSONObject jsonObj = new JSONObject();
	private CameraInfo camInfo = new CameraInfo();
	private boolean requestCamera = true;
	
	// context settings
	private Context mContext; 	
	private fragmentCallback fCallback;
	
	// camera manager
	private CameraManager camManager;
	
	public GetCameraInfoTask(ScannerFragmentHandler handler, CameraManager camManager, Context context) {
		this.camManager = camManager;
		this.mContext = context;
		camInfo.setType(SendService.INIT_CAMERA_INFO);
		fCallback = handler.activity.fragmentCallback;
	}
	
	public void addScanDetails(JSONObject jsonScan) {
		this.jsonObj = jsonScan;
		camInfo.setType(SendService.SCAN_INFO);
	}
	
	public void setRequestCamDetails(Boolean requestCamera) {
		this.requestCamera= requestCamera; 
	}

	@Override
	protected Void doInBackground(Void... v) {
		Log.d(TAG, "Retrieving Camera Information");
		if (requestCamera) {
			try {
				GetCameraInfo getCameraInfo = new GetCameraInfo(camManager, mContext);
				getCameraInfo.setInitJson(jsonObj);
				jsonObj = getCameraInfo.getCameraParameters();
				taskSuccess = true;
			} catch (Exception e) {
				Log.e(TAG, "Error converting to JSON " + e.toString());
				e.printStackTrace();
				taskSuccess = false;
			}
		}
		else {
			taskSuccess = true;
		}
		return null;
	}
	
	@Override
	protected void onPostExecute(Void v) {
		if (taskSuccess) {
			camInfo.setJson(jsonObj);
			fCallback.sendCameraStat(camInfo);
			//handler.sendCameraMail(camInfo);
		}
		else {
			Log.d(TAG, "There was an error getting camera info. Email not sent");
		}
	}	
	
	public class CameraInfo {
		public int reqType; 
		public JSONObject jsonObj;
		
		public void setType(int reqType) {
			this.reqType = reqType;
		}
		
		public void setJson(JSONObject jsonObj) {
			this.jsonObj = jsonObj;
		}
	}
}
