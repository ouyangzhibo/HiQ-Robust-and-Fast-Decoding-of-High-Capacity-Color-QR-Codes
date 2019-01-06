/*
 Copyright (C) 2015 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.graphics.drawable.BitmapDrawable;
import edu.cuhk.ie.authbarcodescanner.android.R;


public class SampleFragment extends StandardFragment{
	private static final String TAG=SampleFragment.class.getSimpleName();
	private static final int layoutXML=R.layout.fragment_samples;
	
	public SampleFragment(){		
		setLayout(layoutXML);
	}
	@Override
	public void onResume(){
		super.onResume();
		//Start working here		
		setButton(R.id.sample_visitcard_button,R.id.sample_visitcard_image);
		setButton(R.id.sample_maxcontent_button,R.id.sample_maxcontent_image);
		setButton(R.id.sample_certificate_button,R.id.sample_certificate_image);
		setButton(R.id.sample_invoice_button,R.id.sample_invoice_image);		
	}
	
	private void setButton(int buttonID, int imageID){
		Button visitcardButton = (Button) getView().findViewById(buttonID);
		ImageView visitcardImage = (ImageView) getView().findViewById(imageID);	
		Bitmap visitcardBitmap = null;
		try{
			visitcardBitmap = (imageID ==R.id.sample_maxcontent_image)?
					android.graphics.BitmapFactory.decodeStream(context.getAssets().open("maxcontent.png")):
					((BitmapDrawable) visitcardImage.getDrawable()).getBitmap();
		}catch(Exception e2){ }
		if(visitcardBitmap ==null) visitcardBitmap = ((BitmapDrawable) visitcardImage.getDrawable()).getBitmap(); 
		//Bitmap visitcardBitmap = BitmapFactory.decodeResource(getResources(), imageID);
		visitcardButton.setOnClickListener(new sampleClickListener(visitcardBitmap));
	}
	
	private class sampleClickListener extends AsyncTask<Boolean, Integer, ResultFragment> 
		implements View.OnClickListener {
		private Bitmap img=null;
		
		public sampleClickListener(Bitmap img){
			this.img=img;
		}
		
		@Override
		public void onClick(View arg0) {
			//Make a dummy boolean input so that it will not call the execute in onclicklistener
			this.execute(true); 
		}
		protected void onPreExecute(){
	    	alert("Loading the record");
	    }
		protected ResultFragment doInBackground(Boolean... vv){
			Map<DecodeHintType,Object> hints = new EnumMap<DecodeHintType,Object>(DecodeHintType.class);
			hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(BarcodeFormat.QR_CODE));
			hints.put(DecodeHintType.TRY_HARDER, true);
			Result rawResult=edu.cuhk.ie.authbarcodescanner.android.decodethread
					.DecodeThreadHandler.fileDecode(img,hints,context,ScannerFragment.loadQDA(context));
  			ResultFragment resultFragment = new ResultFragment();
  			resultFragment.setResult(rawResult, true);  			 
  			return resultFragment;
	    }
		protected void onPostExecute(ResultFragment resultFragment){
			if(fragmentCallback !=null) 
 				 fragmentCallback.moveToFragment(TAG, resultFragment);
	    }
	}	
}