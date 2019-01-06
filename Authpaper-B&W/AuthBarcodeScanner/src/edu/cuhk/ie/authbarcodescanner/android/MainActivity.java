/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.security.Security;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import edu.cuhk.ie.authbarcodescanner.android.R;
import edu.cuhk.ie.authbarcodescanner.certificate.CertificateActivity;
import edu.cuhk.ie.authbarcodescanner.certificate.KeystoreHolder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends fragmentCallback{
	private static final String TAG=MainActivity.class.getSimpleName();
	private static final int containerID=R.id.container;
	private static final int menuID=R.menu.main;
	private static final int[] menuToDeleteInFree={R.id.action_logout};
	
	// history database
	private static final String historyStoreName = "historyStore.db";
	private static final int historyStoreVersion = 1;
	//public static final String PREFS_NAME = "DigitalCert";
	
	static {
		if(android.os.Build.VERSION.SDK_INT <=android.os.Build.VERSION_CODES.KITKAT)
			Security.addProvider(new BouncyCastleProvider());
		//Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//Keep screen on
		Window window = getWindow();
	    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		//Set display
		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
	    setContentView(R.layout.activity_main);	  
	    if(ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.CAMERA)
        != android.content.pm.PackageManager.PERMISSION_GRANTED){
			ActivityCompat.requestPermissions(this,
	                new String[]{android.Manifest.permission.CAMERA},111);			
		}else{
			Intent intent = getIntent();
		    if (intent.hasExtra("history")) {
		    	HistoryFragment.setInstance(this, historyStoreName, historyStoreVersion);
		    	init(savedInstanceState,containerID, menuID, HistoryFragment.getInstance());	    	
		    } else {
		    	init(savedInstanceState,containerID, menuID, new ScannerFragment());	
		    }
		}
		
	    setMenuToDeleteInFree(menuToDeleteInFree);
	    //ImageTestTask imgTest = new ImageTestTask();
	    //imgTest.execute(null, null, null);
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
	    switch (requestCode) {
	        case 111: {
	            // If request is cancelled, the result arrays are empty.
	            if(grantResults.length > 0
	                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
	            	startActivity(new Intent(this, MainActivity.class));
		    		finish();
	            }else{
	            	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	            	builder.setTitle(getString(R.string.app_name));
	        	    builder.setMessage("Cannot open camera. Please enable camera access in Setting -> Apps -> "
	    					+getString(R.string.app_name)+" -> Permission in order to use this app.");
	        	    builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
	        			@Override
	        			public void onClick(DialogInterface dialog, int which) {
	        				onFatalErrorHappen(TAG);
	        			}
	        		});
	        	    builder.show();
	            }
	            return;
	        }
	    }
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		switch(id){
			case R.id.action_history:
				moveToFragment("", HistoryFragment.getInstance());
				return true;
			case R.id.action_certificate:
				KeystoreHolder.getInstance().setKeystore(startKeyStore());
				startActivity(new Intent(this, CertificateActivity.class));
				return true;				
			case R.id.action_returnScanner:
				FragmentManager manager = getSupportFragmentManager(); 
				if(manager.getBackStackEntryCount() != 0){
					manager.popBackStack(manager.getBackStackEntryAt(0).getId(),
							FragmentManager.POP_BACK_STACK_INCLUSIVE);
				}
				return true;
			case R.id.action_feedback:
				startActivity(new Intent(this, FeedbackActivity.class));
				return true;
			case R.id.action_logout:
				if(isDeluxeVer) logoutUser();
				return true;
			case R.id.action_samples:
				moveToFragment("", new SampleFragment());
				return true;
			case R.id.action_about:
				openHelp();				
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
	@Override
	public void onResume(){
		super.onResume();
		startKeyStore();
		HistoryFragment.setInstance(this, historyStoreName, historyStoreVersion);
	}
	@Override
	public void onPause(){
		super.onPause();
		saveKeyStore();
		HistoryFragment history = HistoryFragment.getInstance();
		if(history !=null) history.closeDB();
	}
	@Override
	public void onReturnResult(int requestCode, int resultCode, Intent data){
		onFatalErrorHappen("");
	}
	
	private void logoutUser(){
		Intent loginIntent = new Intent(this, LoginActivity.class);
		loginIntent.putExtra(LoginActivity.LOGOUT, true);
		startActivity(loginIntent);
		finish();
	}
}
