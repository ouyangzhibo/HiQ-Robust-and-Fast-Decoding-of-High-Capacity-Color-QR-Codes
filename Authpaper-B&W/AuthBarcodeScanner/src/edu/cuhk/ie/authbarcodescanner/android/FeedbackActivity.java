/*
 Copyright (C) 2014 Marco in MobiTeC, CUHK 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.security.KeyStore;
import java.util.List;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.MenuItem;

public class FeedbackActivity extends fragmentCallback {
	private static final String TAG=FeedbackActivity.class.getSimpleName();
	private static final int containerID=R.id.feedbackContainer;
	private static final int menuID=R.menu.feedback;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		//Set display
	    setContentView(R.layout.activity_feedback);
	    init(savedInstanceState,containerID, menuID, new FeedbackFragment());
	    LocalBroadcastManager.getInstance(this).registerReceiver(ServiceMsgReceiver, new IntentFilter("updateTitle"));
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "on destroy");
		LocalBroadcastManager.getInstance(this).unregisterReceiver(ServiceMsgReceiver);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		switch(id){
			case R.id.fb_menu_send:
				sendFeedback();
				return true;		
			case R.id.fb_reset:
				resetForm();
				return true;
		}
		return false;
	}
	
	private void resetForm() {
		FragmentManager fManager = getSupportFragmentManager();
		FeedbackFragment feedbackFrag = (FeedbackFragment) fManager.findFragmentById(containerID);
		feedbackFrag.resetForm();
	}
	
	private void sendFeedback() {
		FragmentManager fManager = getSupportFragmentManager();
		FeedbackFragment feedbackFrag = (FeedbackFragment) fManager.findFragmentById(containerID);		
		feedbackFrag.packDataAndSend();
	}
	
	private BroadcastReceiver ServiceMsgReceiver = new BroadcastReceiver()  {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "received message");
			boolean mailSent = false;
			if (intent.hasExtra("status")) {
				mailSent = intent.getBooleanExtra("status", false);
				FragmentManager fManager = getSupportFragmentManager();
				FeedbackFragment feedbackFrag = (FeedbackFragment) fManager.findFragmentById(containerID);				
				feedbackFrag.mailSentCallback(mailSent);
			}
		}
	};
	
	
	@Override
	public void onReturnResult(int requestCode, int resultCode, Intent data){
		FragmentManager manager = this.getSupportFragmentManager();
		if(manager.getBackStackEntryCount() >0){
			manager.popBackStack();
			if(manager.getBackStackEntryCount() >0){
				Fragment targetFragment=manager.findFragmentById(manager.getBackStackEntryAt(
											manager.getBackStackEntryCount()-1).getId()
										);
				if(targetFragment ==null){
					//No more transaction to reverse, get the current fragment 
					//(should be the first one) to take the result 
					List<Fragment> frags=manager.getFragments();
					if(frags.size() >0) targetFragment=frags.get(0);
				}
				if(targetFragment !=null) 
					targetFragment.onActivityResult(requestCode, resultCode, data);
			}
		}
	}

	/*
	 * Unimplemented super class functions
	 */
	@Override
	public KeyStore startKeyStore() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected boolean saveKeyStore() {
		// TODO Auto-generated method stub
		return false;
	}	
	
}