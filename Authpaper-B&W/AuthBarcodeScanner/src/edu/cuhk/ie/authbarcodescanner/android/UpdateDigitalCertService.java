/*
 Copyright (C) 2015 Ken in MobiTeC, CUHK 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import edu.cuhk.ie.authbarcodescanner.android.result.webViewHandler.trustSocketFactory;
import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.TextUtils;

public class UpdateDigitalCertService extends IntentService {

	public static final int STATUS_RUNNING = 0;
    public static final int STATUS_FINISHED = 1;
    public static final int STATUS_ERROR = 2;
    public static final int STATUS_NOUpdate = 3;
    
	private static final String TAG = "UPDATECERT";

	public UpdateDigitalCertService(){
		super("UpdateDigitalCertService");
		Log.d(TAG, "Service Created");
	}
	
	@Override
    protected void onHandleIntent(Intent intent) {
		
		Log.d(TAG, "Service Started!");
		
        final ResultReceiver receiver = intent.getParcelableExtra("receiver");
       
        //get url and lastUpdateTime
        String url = intent.getStringExtra("url");
        Long lastUpdateTime = intent.getLongExtra("lastUpdateTime", 0);
        ArrayList<String> newCrts = new ArrayList<String>();
        ArrayList<String> removedCrts = new ArrayList<String>();
        
        Bundle bundle = new Bundle();

        if (!TextUtils.isEmpty(url)) {
            /* Update UI: Download Service is Running */
            receiver.send(STATUS_RUNNING, Bundle.EMPTY);

            try {
            	//Fetch a list of updated cert files
            	JSONArray results = checkUpdate(url, lastUpdateTime);
            	
            	int length = results.length();
            	for(int i = 0; i < length; i++){
                    //Log.d("debugTest",Integer.toString(i));  
                    JSONObject oj = results.getJSONObject(i);  
                    String certFilename = oj.getString("certFilename");
                    String lastAction = oj.getString("lastAction");
                    
                    if( lastAction.equals("+") ){ 
                    	//Download new cert
                    	downloadDigitalCert(url, certFilename);
                    	//Add new cert filename to newCrts list
                    	newCrts.add(parseCrtFilename(certFilename)); 
                    	Log.d(TAG, "Add: " + certFilename);
                    }
                    else if( lastAction.equals("-") ){ 
                    	//Add removed cert filename to removedCrts list
                    	removedCrts.add(certFilename); 
                    	Log.d(TAG, "Remove: " + certFilename);
                    }
                }  
                
                /* Sending result back to activity */
                if (null != results && results.length() > 0) {
                    bundle.putStringArrayList("newCrts", newCrts);
                    bundle.putStringArrayList("removedCrts", removedCrts);
                    receiver.send(STATUS_FINISHED, bundle);
                }else{
                	bundle.putString(Intent.EXTRA_TEXT, "No update");
                	receiver.send(STATUS_NOUpdate, bundle);
                }
            } catch (Exception e) {
                /* Sending error message back to activity */
                bundle.putString(Intent.EXTRA_TEXT, e.toString());
                receiver.send(STATUS_ERROR, bundle);
            }
        }
        Log.d(TAG, "Service Stopping!");
        this.stopSelf();
    }

	private JSONArray checkUpdate(String requestUrl, long lastUpdateTime) throws IOException, DownloadException {
		InputStream inputStream = null;
        JSONArray results = null;
        /* forming the java.net.URL object for fetching a list of updated cert files */
        HttpURLConnection urlConnection = trustSocketFactory.getSSLConnection(
        		this.getApplicationContext(),requestUrl + "?action=checkUpdate&lastUpdateTime=" + lastUpdateTime);
        if(urlConnection ==null) throw new DownloadException("Fail to connect the server");
        /* optional request header */
        urlConnection.setRequestProperty("Content-Type", "application/json");
        /* optional request header */
        urlConnection.setRequestProperty("Accept", "application/json");
        /* for Get request */
        urlConnection.setRequestMethod("GET");
        int statusCode = urlConnection.getResponseCode();
        /* 200 represents HTTP OK */
        if (statusCode == 200) {
            inputStream = new BufferedInputStream(urlConnection.getInputStream());
            String response = convertInputStreamToString(inputStream);
            Log.d(TAG, response);
			try {
				results = new JSONArray(response);
				Log.d(TAG, "JSON fetched!");
			} catch (JSONException e) {
				throw new DownloadException("No valid result");
			}
            return results;
        }else throw new DownloadException("Failed to fetch data!!");
    }
	
	private void downloadDigitalCert(String requestUrl, String certFilename){
		try {			
			/* forming the java.net.URL object for downloading cert file */
			HttpURLConnection urlConnection = trustSocketFactory.getSSLConnection(
	        		this.getApplicationContext(),requestUrl + "?action=downloadCrt&certFilename=" + certFilename);
	        if(urlConnection ==null) Log.d(TAG, "Fail to connect the server for cert : " + certFilename);
	        //set up some things on the connection
	        urlConnection.setRequestMethod("GET");
	        urlConnection.setDoOutput(true);
	        //and connect!
	        urlConnection.connect();
	        //set the path where we want to save the file
	        //create a new file, specifying the path, and the filename
	        //which we want to save the file as.
	        File file=StandardButton.openFile(this, "certificate", parseCrtFilename(certFilename), "", false, false);
	        if(file ==null){
	        	android.util.Log.d(TAG, "Cannot save the certificates as access to SD card is denied.");
	        	return;
	        }
	        //this will be used to write the downloaded data into the file we created
	        FileOutputStream fileOutput = new FileOutputStream(file);
	        //this will be used in reading the data from the internet
	        InputStream inputStream = urlConnection.getInputStream();
	        Log.d(TAG, "Start download: " + certFilename);
	        Log.d(TAG, "Start download: " + parseCrtFilename(certFilename));
	        //create a buffer...
	        byte[] buffer = new byte[1024];
	        int bufferLength = 0; //used to store a temporary size of the buffer
	        //now, read through the input buffer and write the contents to the file
	        while ( (bufferLength = inputStream.read(buffer)) > 0 ) {
	                //add the data in the buffer to the file in the file output stream (the file on the sd card
	                fileOutput.write(buffer, 0, bufferLength);
	        }
	        //close the output stream when done
	        fileOutput.close();
	        Log.d(TAG, "Finished downloading Cert:" + parseCrtFilename(certFilename) );
		}catch(MalformedURLException e){ }		        
		catch(IOException e){ }
	}

    public static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while ((line = bufferedReader.readLine()) != null) result += line;
        /* Close Stream */
        if(null != inputStream) inputStream.close();
        return result;
    }
    
    private String parseCrtFilename(String certFilename) {
    	return certFilename.replace("@", "_at_").replace('.', '_').replace("_crt", ".crt");
    }

    public class DownloadException extends Exception {
		private static final long serialVersionUID = 7484326323031623596L;
		public DownloadException(String message) {
            super(message);
        }
        public DownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }	
}
