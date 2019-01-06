/*
 Copyright (C) 2015 Marco in MobiTeC, CUHK 
 */
package edu.cuhk.ie.authbarcodescanner.android;


import java.io.IOException;
import java.util.HashSet;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.text.SpannableString;
import android.text.util.Linkify;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.plus.Plus;

import edu.cuhk.ie.authbarcodescanner.analytic.GetUserInfoTask;
import edu.cuhk.ie.authbarcodescanner.analytic.PostData;
import edu.cuhk.ie.authbarcodescanner.android.SendService.SendDataBinder;


/*************************************************
 * To disable Google+ log in  
 * - Set "noLogin" to true
 * - Use "defaultEmail" to set as the dummy email sent to the server
 * - This will remove the Google+ sign in button
 * - Splash Screen will show for 1 second before progressing as normal 
 * 
 * @author mtleung
 *
 *************************************************/
public class LoginActivity extends FragmentActivity implements
ConnectionCallbacks, OnConnectionFailedListener, View.OnClickListener {
	private static final String TAG = LoginActivity.class.getSimpleName();
	
	// Email to send to backend
	private static String defaultEmail = "Anonymous"; 
	public static boolean noLogin = (edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer)? false
			: true;//If deluxe version, it should be false, otherwise it will be true 
	
	String mEmail = ""; // Received from newChooseAccountIntent();
	
	/* Request code used to invoke sign in user interactions. */
	private static final int RC_SIGN_IN = 0;
	/* Request code used to get user email. */
	static final int RC_PICK_ACC = 1;		
	/*Request code used to get user token. */
	private static final int REQ_SIGN_IN_REQUIRED = 55664;
	/* Client used to interact with Google+ APIs. */
	private GoogleApiClient mGoogleApiClient;
	private boolean mIntentInProgress;
	private boolean mSignInClicked;
	
	/* Legitimate Play store installation codes */
	//private boolean playStoreDialogShown = false;
	    
    // Args to trigger events
    public static final String LOGOUT = "logout";
    public boolean logoutRequested = false;
    
	// send data service
	SendService mService;
	boolean mBound = false;
	public Handler sHandler; 
	boolean playStoreDialogShown =false; //Prevent multiple dialog is shown
	
	//SharedPreferences sharedPref;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle bundle = getIntent().getExtras();
	    if(bundle != null && bundle.containsKey(LOGOUT))
	    	logoutRequested = bundle.getBoolean(LOGOUT);
	    
		if(!isTaskRoot() && !logoutRequested){
			//Hardcode fix for proguard, if a user exit the app via HOME button and return, 
			//killing the newly-started activity will let user return to the last state
			finish();
	        return;
		}
		setContentView(R.layout.activity_login);
		
		// init send service
		Intent intent = new Intent(this, SendService.class);
		startService(intent);
		boolean serviceStatus = bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		Log.d(TAG, "Service status " + String.valueOf(serviceStatus));				
		
    	SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this); 
    	String oldEmail = sharedPref.getString(getString(R.string.pref_login_email), "");
    	
    	SharedPreferences.Editor editor = sharedPref.edit();
    	//if(!noLogin && !mEmail.isEmpty() && !mEmail.equals(defaultEmail)) gotoMainActivity();
    	mEmail = (noLogin)? defaultEmail : (oldEmail.equals(defaultEmail))? "":oldEmail;
    	if(!oldEmail.equals(mEmail)){
	        editor.putString(getString(R.string.pref_login_email), mEmail);
	        editor.commit();
		}
		Log.d(TAG, "Init Email is " + mEmail);
		// register click listener
		if (noLogin) {
			findViewById(R.id.sign_in_button).setVisibility(View.INVISIBLE);
			//findViewById(R.id.login_info).setVisibility(View.INVISIBLE);			
			((android.widget.TextView) findViewById(R.id.login_info))
				.setText(getString(R.string.login_info_free));
			// go to main activity after pausing one second
	    	new Handler().postDelayed(new Runnable() { 
	             public void run() { 
	            	 gotoMainActivity(); 
	             } 
	        }, 500); 
		} else {
			if(mEmail.isEmpty() || mEmail.equals(defaultEmail)) buildGoogleApiClient();
			else buildGoogleApiClient(mEmail);
			findViewById(R.id.sign_in_button).setOnClickListener(this);
		}
	}
	
	// goes to main activity
	private void gotoMainActivity() {
		// if user has logged in previously, then go straight to scanner activity
	    if(mEmail != null && !logoutRequested) {
	    	Log.d(TAG, "Short cut straight to scanner");
	    	if(installSourceLegitimate()){
	    		startActivity(new Intent(this, MainActivity.class));
	    		finish();
	    	}else{
				Log.d(TAG, "Short cut warning dialog");
				if (!playStoreDialogShown) {
					playStoreDialogShown = true;
			        final SpannableString s = new SpannableString(getText(R.string.login_gps_text));
			        Linkify.addLinks(s, Linkify.WEB_URLS);
			        final Context context=this;
			        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(R.string.login_gps_warning)
			        .setMessage(s).setPositiveButton(getText(R.string.gen_continue), new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							playStoreDialogShown = false;
							Log.d(TAG, "Starting from appWarningDialog");
							startActivity(new Intent(context, MainActivity.class));
				    		finish();
						}
					});
				    builder.show();
				}
	    	}
	    }		
	}
	
	private void buildGoogleApiClient() {
		mGoogleApiClient = new GoogleApiClient.Builder(this)
		.addConnectionCallbacks(this)
		.addOnConnectionFailedListener(this)
		.addApi(Plus.API)		
		.addScope(new Scope("profile"))
		.addScope(new Scope("https://www.googleapis.com/auth/plus.me"))
		.build();	
	}
	
	private void buildGoogleApiClient(String email) {
		mGoogleApiClient = new GoogleApiClient.Builder(this)
			.setAccountName(email)
			.addConnectionCallbacks(this)
			.addOnConnectionFailedListener(this)
			.addApi(Plus.API)
			.addScope(new Scope("profile"))
			.addScope(new Scope("https://www.googleapis.com/auth/plus.me"))
			.build();		
	}	
	
	private void pickUserAccount() {
		String[] accountTypes = new String[]{"com.google"};
		Intent intent = AccountPicker.newChooseAccountIntent(null, null,
	            accountTypes, false, null, null, null, null);
	    startActivityForResult(intent, RC_PICK_ACC);		
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		boolean isGettingTokenConsent=false;
		if(getIntent() !=null){
			boolean kk=getIntent().getBooleanExtra("k", false);
			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
			if(sharedPref.getBoolean("doesNotNeedToken", false) || kk){
				Log.d(TAG, "Getting token consent");
				SharedPreferences.Editor editor = sharedPref.edit();
				editor.remove("doesNotNeedToken");
    	        editor.commit();
				isGettingTokenConsent=true;
				return;
			}else Log.d(TAG, "No indicator of getting token consent");
		}else Log.d(TAG, "No intent");	
		Log.d(TAG, "isGettingToken:"+isGettingTokenConsent);
		if(isGettingTokenConsent) return;
		Log.d(TAG, "onStart");
		if (!noLogin){
			// test if google play service available
			int isAvailable = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());
			//Log.d(TAG, "Google Play Service Status " + Integer.toString(isAvailable));
			if(isAvailable == ConnectionResult.SUCCESS || GooglePlayServicesUtil.isUserRecoverableError(isAvailable)){
				if(mGoogleApiClient !=null) mGoogleApiClient.connect();
				
				SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
				if(sharedPref.getBoolean("isFirstRun", true)){
					Toast.makeText(this, "Please Login using your Google+ Account", Toast.LENGTH_SHORT).show();
				}				
			}else Toast.makeText(this, "Error: Google Play Service Not Available", Toast.LENGTH_SHORT).show();
		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		Log.d(TAG, "onStop");
		//clean up - disconnect google client
		if(!noLogin && mGoogleApiClient != null && mGoogleApiClient.isConnected())
			mGoogleApiClient.disconnect();
	}	
	
    /* onConnectionFailed is called when our Activity could not connect to Google
     * Play services.  onConnectionFailed indicates that the user needs to select
     * an account, grant permissions or resolve an error in order to sign in.
     */	
	@Override
	public void onConnectionFailed(ConnectionResult result) {
		Log.d(TAG, "onConnectionFailed");
		if(noLogin) gotoMainActivity();	
		if (mSignInClicked && !mIntentInProgress && result.hasResolution()) {
			if (result.getErrorCode() == ConnectionResult.SIGN_IN_REQUIRED && mEmail == "") {
				Log.d(TAG, "Picking user account");
				// force pick of user account
				pickUserAccount();
			} else {
				Log.d(TAG, "Progressing as normal");
				try {
				    mIntentInProgress = true;
				    result.startResolutionForResult(this, RC_SIGN_IN);
			    } catch (SendIntentException e) {
			    	// The intent was canceled before it was sent.  Return to the default
				    // state and attempt to connect to get an updated ConnectionResult.
				    mIntentInProgress = false;
				    mGoogleApiClient.connect();
				}
			}
		}
    }    
	
	@Override
	public void onConnectionSuspended(int cause) {
		Log.d(TAG, "onConnectionSuspended");
	    // The connection to Google Play services was lost for some reason.
	    // We call connect() to attempt to re-establish the connection or get a
	    // ConnectionResult that we can attempt to resolve.		
		mGoogleApiClient.connect();
	}

	
	/* onConnected is called when our Activity successfully connects to Google
	 * Play services.  onConnected indicates that an account was selected on the
	 * device, that the selected account has granted any requested permissions to
	 * our app and that we were able to establish a service connection to Google
	 * Play services.
	 */
	@Override
	public void onConnected(Bundle connectionHint) {
		if(noLogin) return; //If users does not need to perform login, we do not need to do anything
		// Reaching onConnected means we consider the user signed in.
	    Log.d(TAG, "Connected using " + mEmail);
	    if(logoutRequested){
	    	logoutUser();	    	
	    }else{
			//Check if the email is different. If yes, save email to preferences
	        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this); 
	        SharedPreferences.Editor editor = sharedPref.edit();
			String test = sharedPref.getString(getString(R.string.pref_login_email), "");
	        if(!test.equals(mEmail)){
	        	editor.putString(getString(R.string.pref_login_email), mEmail);
		        editor.commit();
				Log.d(TAG, "email pref set to " + mEmail);     
				// get user info if first run			
				initUserInfo();       
			}
			//Schedule an asynchronous task to get Google plus token 
	        //scheduleAsyncGetTokenTask();
			/*if(!sharedPref.getBoolean(getString(R.string.pref_loading_token), false)){
		        SharedPreferences.Editor editor2 = sharedPref.edit();
		        editor2.putBoolean(getString(R.string.pref_loading_token), true);
		        editor2.commit();*/
	        if(mEmail !=null && !mEmail.isEmpty()){
		        mSignInClicked=true;//As if the login button is clicked			
		        Toast.makeText(this, "Entering the App", Toast.LENGTH_LONG).show();				
				new getTokenTask().execute(mEmail);
			}	  
			//gotoMainActivity();
	    }
	}
	
	private class getTokenTask extends AsyncTask<String, Void, String> {
		@Override
		protected void onPreExecute(){
			Log.d(TAG,"Getting User Token");
		}
		@Override
        protected String doInBackground(String... params) {
            String accountName = params[0];
            String scopes = "oauth2:profile email";
            String token = null;
            try {
                token = GoogleAuthUtil.getToken(getApplicationContext(), accountName, scopes);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            } catch (UserRecoverableAuthException e) {
            	Intent intent=e.getIntent();
            	Log.d(TAG, intent.toString());
            	intent.putExtra("k", true);
            	//As extra does not work in some Android devices, we also use preference as extra indicator
            	SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this); 
            	SharedPreferences.Editor editor = sharedPref.edit();
            	editor.putBoolean("doesNotNeedToken", true);    	        
    	        editor.commit();
                startActivityForResult(intent, REQ_SIGN_IN_REQUIRED);
                return "k";
            } catch (GoogleAuthException e) {
                Log.e(TAG, e.getMessage());
            }
            return token;
		}
		
        @Override
        protected void onPostExecute(String token) {      	
            //save the google plus token in user preferences
        	Log.d(TAG,"Done Getting User Token");
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this); 
	        SharedPreferences.Editor editor = sharedPref.edit();
	        editor.putString(getString(R.string.pref_google_plus_token), token);
	        //editor.putBoolean(getString(R.string.pref_loading_token), false);
	        editor.commit();
	        //if(token.equals("k")) finish();
	        gotoMainActivity();
		}
	}
	
	public boolean installSourceLegitimate() {
		String installationSource = getPackageManager().getInstallerPackageName(getPackageName());
		Log.d(TAG, "Installation Source " + installationSource);
		// alert user if app not from Google play
		HashSet<String> playStoreURL = new HashSet<String>();
		playStoreURL.add("com.android.vending");
		return playStoreURL.contains(installationSource);
	}
	
	//log in button clicked
	@Override
	public void onClick(View v) {
		if(mSignInClicked) return; //Ignore clicking if it is already clicked
		if (v.getId() == R.id.sign_in_button && !mGoogleApiClient.isConnecting()) {
		    mSignInClicked = true;
		    mGoogleApiClient.connect();
		}
	} 
	
	@Override
	protected void onActivityResult(int requestCode, int responseCode, Intent intent) {
		Log.d(TAG, "onActivityResult");
		if (requestCode == RC_PICK_ACC) {
			Log.d(TAG, "Resolving account");
			if (responseCode == RESULT_OK) {
				mEmail = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                buildGoogleApiClient(mEmail);
                mGoogleApiClient.connect();
			}
		} else if (requestCode == RC_SIGN_IN) {
			Log.d(TAG, "Resolving sign in");
			if(responseCode != RESULT_OK) mSignInClicked = false;
			mIntentInProgress = false;
			if(!mGoogleApiClient.isConnected()){
				buildGoogleApiClient(mEmail);
				mGoogleApiClient.reconnect();
			}
		} else if (requestCode == REQ_SIGN_IN_REQUIRED && responseCode == RESULT_OK) {
			//Getting token is failed last time, but it should work now without further process
			Log.d(TAG, "getTokenTask fail last time and returned to LoginActivity");
        }
	}

	public void logoutUser(){
		//log out of current account
		Log.d(TAG, "logoutUser");
		if (mGoogleApiClient != null){
			if(mGoogleApiClient.isConnected()){
				Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
				Plus.AccountApi.revokeAccessAndDisconnect(mGoogleApiClient);
				mGoogleApiClient.disconnect();
				
				final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);	    	
		        Log.d(TAG, "Cleared default account");
				//remove preferences value to stop auto log in
		        SharedPreferences.Editor editor = sharedPref.edit();
		        mEmail = sharedPref.getString(getString(R.string.pref_login_email), null);
		        mEmail = "";
		        editor.putString(getString(R.string.pref_login_email), null);
		        editor.commit();
		        logoutRequested=false;
		        Log.d(TAG, "Preferences reset");
		        buildGoogleApiClient();
			}
		}
	}
	private String msgToSend=null;
	// callbacks for service binding
	private ServiceConnection mConnection = new ServiceConnection() {		
		@Override 
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.d(TAG, "service connected");
			SendDataBinder binder = (SendDataBinder) service;
			mService = binder.getService();
			mBound = true;
			sHandler = mService.getServiceHandler();	
			if(noLogin) initUserInfo();	
			if(msgToSend !=null && !msgToSend.isEmpty()) sendUserInfo(msgToSend);
			if(logoutRequested && !noLogin) logoutUser();
		}
		
		@Override 
		public void onServiceDisconnected(ComponentName className) {
			Log.d(TAG, "service disconnected");
			mBound = false;
			mService = null;
		}
	};
	
	// init send user info
	public void initUserInfo() {
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this); 
		Log.d(TAG, "UserInfo to be sent " + Boolean.toString(sharedPref.getBoolean(GetUserInfoTask.EMAIL_USERINFO, true)));
		if(sharedPref.getBoolean(GetUserInfoTask.EMAIL_USERINFO, true)) {
			// send user info when service is ready
	    	GetUserInfoTask getUserInfoTask = new GetUserInfoTask(this, this);
	    	Boolean[] reqStats = new Boolean[] { true, true };
	    	getUserInfoTask.execute(reqStats);
		}			
	}	

	// send user info callback
	public void sendUserInfo(JSONObject jsonObj) {
		try {
			String mail_body = jsonObj.toString(4);
			sendUserInfo(mail_body);			
		} catch (JSONException e) {
			Log.e(TAG, "Error converting JSON to string " + e.toString());
			//e.printStackTrace();
		}
	}
	private void sendUserInfo(String mail_body){
		//initPostData(, mail_body, SendService.INIT_USER_INFO);
		//sending data service via handler
		if (sHandler == null) {
			// try get handler if does not exist
			if(mService != null)
				sHandler = mService.getServiceHandler();	
			else Log.e(TAG, "Error getting send service");
		}
		if(sHandler == null){
			Log.e(TAG, "Failed to send message, handler does not exist");
			msgToSend=mail_body;
			return;
		}
		msgToSend=null;
		PostData pData = new PostData(this);
		pData.setSubject(SendService.T_NEW_USER);
		pData.setContent(mail_body);
		pData.setType(SendService.INIT_USER_INFO); // flag for post send action		
		Message msgPost = sHandler.obtainMessage(SendService.POST_NEW);
		msgPost.obj = pData;
		Log.d(TAG,  "Sending new post to handler");
		msgPost.sendToTarget();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		if (mBound) {
			// unbind service
			unbindService(mConnection);
			mBound = false;
		}
		// stop service
		Intent intent = new Intent(this, SendService.class);
		stopService(intent);		
	}	
}
