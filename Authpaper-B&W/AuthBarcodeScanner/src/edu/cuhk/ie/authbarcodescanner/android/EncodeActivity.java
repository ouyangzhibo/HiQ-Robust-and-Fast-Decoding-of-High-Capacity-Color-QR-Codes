/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.util.List;

import edu.cuhk.ie.authbarcodescanner.android.R;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.MenuItem;


public class EncodeActivity extends fragmentCallback{
	private static final String TAG=EncodeActivity.class.getSimpleName();
	private static final int containerID=R.id.encodeContainer;
	private static final int menuID=R.menu.encode;
		
	private static final String historyStoreName = "historyStore.db";
	private static final int historyStoreVersion = 1;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//Set display
	    setContentView(R.layout.activity_encode);
	    init(savedInstanceState,containerID, menuID, new EncodeFragment());   
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		switch(id){
			case R.id.action_encodeHistory:
				moveToFragment("", HistoryFragment.getInstance(true));
				return true;			
			case R.id.action_scanner:
				//startActivity(new Intent(this, MainActivity.class));
				this.finish();
				return true;
			case R.id.action_feedback:
				startActivity(new Intent(this, FeedbackActivity.class));
				return true;
			case R.id.action_returnEncode:
				FragmentManager manager = getSupportFragmentManager(); 
				if(manager.getBackStackEntryCount() > 0){
					manager.popBackStack(manager.getBackStackEntryAt(0).getId(),
							FragmentManager.POP_BACK_STACK_INCLUSIVE);
				}
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
	@Override
	public void onResume(){
		super.onResume();
		startKeyStore();
		HistoryFragment.setEncodeInstance(this, historyStoreName, historyStoreVersion);
	}
	@Override
	public void onPause(){
		super.onPause();
		saveKeyStore();
		HistoryFragment history = HistoryFragment.getInstance(true);
		if(history !=null) history.closeDB();
	}
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
}