/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import java.io.IOException;
import android.os.AsyncTask;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;

import edu.cuhk.ie.authbarcodescanner.android.LoginActivity;
import edu.cuhk.ie.authbarcodescanner.android.Log;

public class GetOAuthTokenTask extends AsyncTask<Void, Void, String>{
	// async task to get user oauth details
	LoginActivity mActivity;
    String mScope;
    String mEmail;
    
    private static final String TAG = GetOAuthTokenTask.class.getSimpleName();

    public GetOAuthTokenTask(LoginActivity activity, String name, String scope) {
    	this.mActivity = activity;
        this.mScope = scope;
        this.mEmail = name;
    }

    @Override
    protected String doInBackground(Void... params) {
        try {
        	Log.d(TAG, "Fetching token");
            String token = fetchToken();
            if (token != null) {
            	Log.d(TAG, "Token " + token);
                return token;
            }
        } catch (IOException e) {
            // The fetchToken() method handles Google-specific exceptions,
            // so this indicates something went wrong at a higher level.
            // TODO handle exceptions
        }
        return null;
    }
    
    /**
     * Gets an authentication token from Google and handles any
     * GoogleAuthException that may occur.
     */
    protected String fetchToken() throws IOException {
        try {
            return GoogleAuthUtil.getToken(mActivity, mEmail, mScope);
        } catch (UserRecoverableAuthException userRecoverableException) {
            // GooglePlayServices.apk is either old, disabled, or not present
            // so we need to show the user some UI in the activity to recover.
        	//mActivity.handleException(userRecoverableException);
        } catch (GoogleAuthException fatalException) {
            // Some other type of unrecoverable exception has occurred.
            // Report and log the error as appropriate for your app.
        	//mActivity.handleException(fatalException);
        }
        return null;
    }
}
