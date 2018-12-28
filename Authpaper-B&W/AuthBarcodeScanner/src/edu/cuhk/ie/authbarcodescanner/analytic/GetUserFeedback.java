/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import android.widget.Toast;
import edu.cuhk.ie.authbarcodescanner.android.FeedbackFragment;
import edu.cuhk.ie.authbarcodescanner.android.R;

public class GetUserFeedback extends AsyncTask<Boolean[], Void, Void> {
	private static final String TAG = GetUserFeedback.class.getSimpleName();
	
	//Task parameters
	private boolean taskSuccess = false;
	private JSONObject jsonObj = new JSONObject();
	private String userEmail=null;
	
	//context settings
	private Context mContext; 
	private FeedbackFragment fbFrag;
	
	public GetUserFeedback(Context context, FeedbackFragment frag) {
		this.mContext = context;
		this.fbFrag = frag;
	}
	
	public void setFeedback(JSONObject fbJson) {
		jsonObj = fbJson;
	}
	public void setUserEmail(String email){
		userEmail=email;
	}
	
	@Override
	protected Void doInBackground(Boolean[]... params) {
		Boolean getUserId = params[0][0];		
		Boolean getHWInfo = params[0][1];
		try {
			GetUserInfo getUI = new GetUserInfo(mContext);
			getUI.setInitJson(jsonObj);
			jsonObj = getUI.getInfo(getUserId, getHWInfo);
			taskSuccess = true;
		} catch (Exception e) {
			Log.e(TAG, "There was an error getting feedback");
			e.printStackTrace();
			taskSuccess = false;
		}		
		return null;
	}

	
	@Override
	protected void onPostExecute(Void v) {
		if (taskSuccess) {
			if(userEmail !=null && !userEmail.isEmpty()){
				try {
					jsonObj.put("user_email", userEmail);
				}catch(JSONException e){ }
			}
			Toast.makeText(mContext, mContext.getString(R.string.fb_sending), Toast.LENGTH_SHORT).show();
			fbFrag.sendFeedbackInfo(jsonObj);
		}
	}	

}
