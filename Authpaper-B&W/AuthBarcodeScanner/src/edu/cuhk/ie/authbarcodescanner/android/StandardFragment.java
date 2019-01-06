/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android;


import java.io.File;

import edu.cuhk.ie.authbarcodescanner.android.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;


/**
 * This fragment provides basic functions to the fragments.
 * Remember to call setLayout to set the fragment XML, otherwise it will show nothing 
 */
public class StandardFragment extends Fragment {
	public static final String TAG=StandardFragment.class.getSimpleName();
	
	protected Activity context=null;
	public fragmentCallback fragmentCallback=null;
	
	private File tempFolderRoot=null;
	private int layoutXML=-1;
	protected void setLayout(int layout){
		this.layoutXML=layout;
	}

	@Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try{
        	context = activity;
        	fragmentCallback = (fragmentCallback) activity;
        } catch (ClassCastException e) {
        	//Just crash the App
            throw new RuntimeException(getActivity().getClass().getSimpleName() 
            		+ " must implement fragmentCallback to use this fragment", e);
        }
    }
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		if(layoutXML ==-1) return null;
		View rootView = inflater.inflate(layoutXML, container,false);
		listTempFile();
		return rootView;
	}
	@Override
	public void onDestroy(){
		super.onDestroy();
		removeTempFile();
	}
	@Override
	public void onResume(){
		super.onResume();
	}

	/**
	 * Get access to the temporary folder
	 * @return
	 */
	protected File getTempFolder(){
		if(tempFolderRoot !=null) return tempFolderRoot;
			tempFolderRoot = new File(context.getFilesDir(), "ResultTemp");
			if (!tempFolderRoot.exists() && !tempFolderRoot.mkdirs()) {
				Log.w(TAG, "Couldn't make dir " + tempFolderRoot);
				tempFolderRoot=null;
			}
		return tempFolderRoot;
	}
	protected boolean removeTempFile(){
		Log.d(TAG, "Deleting temp files");
		if(tempFolderRoot ==null) return true;
		if(tempFolderRoot.isDirectory()){
			String[] children = tempFolderRoot.list();
	        for (int i = 0; i < children.length; i++) {
	            File childFile = new File(tempFolderRoot, children[i]);
	            Log.d(TAG, "Del: " + childFile.toString());
	            childFile.delete();
	        }
		}
		return tempFolderRoot.delete();		
	}
	
	protected void listTempFile() {
		Log.d(TAG, "Listing temp files");
		if (tempFolderRoot != null) {
			if(tempFolderRoot.isDirectory()){
				String[] children = tempFolderRoot.list();
		        for (int i = 0; i < children.length; i++) {
		            File childFile = new File(tempFolderRoot, children[i]);
		            Log.d(TAG, childFile.toString());
		        }				
			}
		}
	}
	
	protected void errorAndReturn(String message){
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
	    final fragmentCallback fragCallback = fragmentCallback;
	    builder.setTitle(getString(R.string.app_name));
	    builder.setMessage(message);
	    builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				fragCallback.onFatalErrorHappen(TAG);
			}
		});
	    builder.show();
	}
	
	protected void alert(String message){
		alert(message,false);
	}
	protected void alert(String message, boolean isLong){
		if(this.context ==null || message ==null || message.isEmpty()) return;
		Toast toast = Toast.makeText(context, message, (isLong)? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
		toast.show();
	}
	final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}	
}