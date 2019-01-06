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
import edu.cuhk.ie.authbarcodescanner.android.LoginActivity;

public class GetUserInfoTask extends AsyncTask<Boolean[], Void, Void> {
	/* async task to get user details for */
	private static final String TAG = GetUserInfoTask.class.getSimpleName();
	public final static String EMAIL_USERINFO = "ToBeEmailed";
	
	//Task parameters
	private boolean taskSuccess = false;
	private JSONObject jsonObj = new JSONObject();
	
	//context settings
	private Context mContext; 
	private LoginActivity activity;
	
	public GetUserInfoTask(Context context, LoginActivity activity) {
		this.mContext = context;
		this.activity = activity;
	}
	
	@Override
	protected void onPostExecute(Void v) {
		if (taskSuccess) {
			activity.sendUserInfo(jsonObj);
		}
	}
	
	@Override
	protected Void doInBackground(Boolean[]... params) {
		Log.d(TAG, "Retrieving User Information");
		try {
			GetUserInfo getUI = new GetUserInfo(mContext);
			jsonObj = getUI.getInfo(params[0][0], params[0][1]);
			taskSuccess = true;
		} catch (Exception e) {
			Log.e(TAG, "Error getting user information");
			e.printStackTrace();
			taskSuccess = false;
		}
		return null;
	}
}
