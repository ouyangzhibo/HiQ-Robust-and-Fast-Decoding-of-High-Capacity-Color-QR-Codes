/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.Camera;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import edu.cuhk.ie.authbarcodescanner.android.R;
import edu.cuhk.ie.authbarcodescanner.android.camera.CameraConfigurationManager;
import edu.cuhk.ie.authbarcodescanner.android.camera.CameraManager;


/*
 * Helper class to get user information.
 * Wrap in async task or call from background thread 
 */

public class GetCameraInfo {
	private static final String TAG = GetCameraInfo.class.getSimpleName();
	Context mContext;
	
	// reference to camera objects
	private CameraManager camManager;
	private CameraConfigurationManager camConfigManager;
	private Camera camera;
	
	// output data
	private JSONObject jsonObj = new JSONObject();
	
	public GetCameraInfo(CameraManager camManager, Context context) {
		this.camManager = camManager;
		this.mContext = context;
	}
	
	public void setInitJson(JSONObject jsonObject) {
		this.jsonObj = jsonObject;
	}
	
	
	public JSONObject getCameraParameters() {
		camConfigManager = camManager.getCamConfiManager();
		camera = camManager.getCamera();
		try {
			Point resCam = null;
			Point resScr = null;
			boolean torch = false;
			String focusMode = null;
			boolean callFcMode = false;
			try {
				// tag with user information
		    	SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext); 
		    	String userEmail = sharedPref.getString(mContext.getString(R.string.pref_login_email), null);
				String userAndroidID = Secure.getString(mContext.getContentResolver(), Secure.ANDROID_ID);
				String osVersion = android.os.Build.VERSION.RELEASE;
				int osInt = android.os.Build.VERSION.SDK_INT;				
				
				resCam = camConfigManager.getCameraResolution();
				//Point resPic = configManager.getPictureResolution();
				resScr = camConfigManager.getScreenResolution();
				torch = camConfigManager.getTorchState(camera);
				int torchState = torch ? 1 : 0;
				focusMode = camera.getParameters().getFocusMode();	  
				callFcMode = CameraConfigurationManager.shouldCallAutoFocus(camera);
				int callFcModeState = callFcMode ? 1 : 0;
				try {
					jsonObj.put("user_email", userEmail);
					jsonObj.put("android_id", userAndroidID);
					jsonObj.put("android_sdk", Integer.toString(osInt));
					jsonObj.put("android_version", osVersion);	
					
					jsonObj.put("resCam", resCam);
					jsonObj.put("resCamX", resCam.x);
					jsonObj.put("resCamY", resCam.y);
					//jsonObj.put("resPic", resPic);
					jsonObj.put("resScr", resScr);
					jsonObj.put("resScrX", resScr.x);
					jsonObj.put("resScrY", resScr.y);
					jsonObj.put("torch", torchState);
					jsonObj.put("focusMode", focusMode);
					jsonObj.put("callFcMode", callFcModeState);
				}
				catch (Exception e) {
					Log.e(TAG, "Error converting to JSON " + e.toString());
				}				
			}
			catch (Exception e) {
				Log.e(TAG, "Error getting camera parameters " + e.toString());
			}
		}
		catch (Exception e) {
			Log.d(TAG, "Camera manager not working");
			e.printStackTrace();
		}
		Log.d(TAG, jsonObj.toString());
		return jsonObj;
	}			
	
	
}
