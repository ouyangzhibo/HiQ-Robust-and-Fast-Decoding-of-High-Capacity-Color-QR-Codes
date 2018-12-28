/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import android.content.Context;
import android.os.AsyncTask;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.ZipSendInfo;

public class ZipSendTask extends AsyncTask<ZipSendInfo, Void, Void> {
	private static final String TAG = ZipSendTask.class.getSimpleName();
	
	// context settings
	private Context mContext; 	
	
	public ZipSendTask(Context context) {
		this.mContext = context;
	}
	
	@Override
	protected Void doInBackground(ZipSendInfo... zipInfo) {			
		Compress compress = new Compress(zipInfo[0].fileList, zipInfo[0].zipName, zipInfo[0].fileExt);
		if (compress.zip()) {
			Log.d(TAG, "Zip complete: " + zipInfo[0].zipName);
			compress.removeImageFiles();
			if (zipInfo[0].sendZip)
			{
				Log.d(TAG, "Sending Zip");
				PostData postZip = new PostData(mContext);
				postZip.setFileAttach(zipInfo[0].zipName);
				postZip.sendExtraStat();	
			}
		}
		else {
			Log.e(TAG, "Failed to create zip");
		}
		
		return null;
	}
}

