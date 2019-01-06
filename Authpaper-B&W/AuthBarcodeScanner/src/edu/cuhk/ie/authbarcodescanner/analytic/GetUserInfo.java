/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import org.json.JSONException;
import org.json.JSONObject;

import edu.cuhk.ie.authbarcodescanner.android.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import edu.cuhk.ie.authbarcodescanner.android.Log;

/*
 * Helper class to get user information.
 * Wrap in async task or call from background thread 
 */
  
public class GetUserInfo {
	private final static String TAG = GetUserInfo.class.getSimpleName();
	Context mContext;
	JSONObject jsonObj = new JSONObject();
	
	public GetUserInfo(Context context) {
		this.mContext = context;
	}
	
	public void setInitJson(JSONObject jsonObject) {
		this.jsonObj = jsonObject;
	}
	
	public JSONObject getInfo(boolean reqUserInfo, boolean reqHWInfo) {
		String userEmail = "";
		String userAndroidID = "";
		int osInt = 0;
		String osVersion = ""; 
		String hwBoard = "";
		String hwBoot = "";
		String hwBrand = "";
		String hwDevice = "";
		String hwHardware = "";
		String hwManu = "";
		String hwModel = "";
		String hwProduct = "";
		String hwSerial = "";		

		if (reqUserInfo) {
	    	SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext); 
	    	userEmail = sharedPref.getString(mContext.getString(R.string.pref_login_email), null);
			userAndroidID = Secure.getString(mContext.getContentResolver(), Secure.ANDROID_ID);
			osVersion = android.os.Build.VERSION.RELEASE;
			osInt = android.os.Build.VERSION.SDK_INT;			
		}
		if (reqHWInfo) {
			// hardware information
			hwBoard = android.os.Build.BOARD;
			hwBoot = android.os.Build.BOOTLOADER;
			hwBrand = android.os.Build.BRAND;
			hwDevice = android.os.Build.DEVICE;
			hwHardware = android.os.Build.HARDWARE;
			hwManu = android.os.Build.MANUFACTURER;
			hwModel = android.os.Build.MODEL;
			hwProduct = android.os.Build.PRODUCT;
			hwSerial = android.os.Build.SERIAL;
		}
		// determine where app was installed from
		PackageManager pm = mContext.getPackageManager();
		String installer = pm.getInstallerPackageName(mContext.getPackageName());
		
		try {
			if (reqUserInfo) {
				jsonObj.put("user_email", userEmail);
				jsonObj.put("android_id", userAndroidID);
				jsonObj.put("android_sdk", Integer.toString(osInt));
				jsonObj.put("android_version", osVersion);				
			}
			if (reqHWInfo) {
				jsonObj.put("hw_product", hwProduct);
				jsonObj.put("hw_brand", hwBrand);
				jsonObj.put("hw_model", hwModel);
				jsonObj.put("hw_manu", hwManu);
				jsonObj.put("hw_device", hwDevice);
				jsonObj.put("hw_boot", hwBoot);
				jsonObj.put("hw_hardware", hwHardware);
				jsonObj.put("hw_board", hwBoard);
				jsonObj.put("hw_serial", hwSerial);				
			}
			jsonObj.put("install_from", installer);

			
		} catch (JSONException e) {
			Log.e(TAG, "Error converting to JSON " + e.toString());
			e.printStackTrace();
		}
		
		return jsonObj;
	}
}
