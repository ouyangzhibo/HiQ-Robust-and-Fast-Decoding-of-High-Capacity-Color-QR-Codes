/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ParsedResultType;
import com.google.zxing.client.result.ResultParser;

import edu.cuhk.ie.authbarcodescanner.android.encode.AppListFragment;
import edu.cuhk.ie.authbarcodescanner.android.encode.MEcardEncoder;
import edu.cuhk.ie.authbarcodescanner.android.encode.TextInputFragment;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

/**
 * This fragment handles the creation of a 2D barcode.
 */
public class EncodeFragment extends StandardFragment {
	private static final String TAG=EncodeFragment.class.getSimpleName();
	private static final int layoutXML=R.layout.fragment_encode;
	
	private static final int ACTION_CONTACT = 1;
	private static final int ACTION_APP = 2;
	private static final int ACTION_TEXT = 3;
	private static final int ACTION_CLIP = 4;
	
	public static final String result_text_index = "result";
	public static final int result_OK = 0;
	
	private boolean isBackFromIntent=false;
	private boolean isSignQRCode=false;
	private boolean isCreatingQR=false;
	private static final int[] menuToDelete={R.id.action_returnEncode};
	
	public EncodeFragment() { 
		setLayout(layoutXML);
	}
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		this.setHasOptionsMenu(true);
	}
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		//Hard code to make create menu function works properly after proguard
	    super.onCreateOptionsMenu(menu, inflater);
	    if(menuToDelete !=null){
			for(int i=0,l=menuToDelete.length;i<l;i++){
				if(menuToDelete[i] >0) menu.removeItem(menuToDelete[i]);
			}
		}
	}
	
	@Override
	public void onResume(){
		super.onResume();
		if(isBackFromIntent){
			//If it comes back from Result Fragment showing the intent result, then just end this fragment
			this.fragmentCallback.onFatalErrorHappen(TAG);
			return;
		}
		Intent intent = getActivity().getIntent();
		if(intent !=null) handleIntent(intent);
		getView().findViewById(R.id.encode_shareContact)
			.setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(View v){
				if(isCreatingQR) return;
				Intent intent = new Intent(Intent.ACTION_PICK, 
						ContactsContract.Contacts.CONTENT_URI);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
				startActivityForResult(intent, ACTION_CONTACT);
		    }
		});
		getView().findViewById(R.id.encode_shareClipboardMessage)
			.setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(isCreatingQR) return;
				ClipboardManager clipManager = 
						(ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
				if(clipManager !=null) {
					ClipData clip = clipManager.getPrimaryClip();
					if(clip !=null && clip.getItemCount() >0 
							&& clip.getItemAt(0).coerceToText(context) !=null) {
						createQRcode(clip.getItemAt(0).coerceToText(context).toString());
					} else alert("No text is found in clipboard");
				} else alert("Cannot access the clipboard");
		    }
		});
		//The options that we need another fragment
		final Fragment sourceFrag=this;
		getView().findViewById(R.id.encode_shareApplication)
			.setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(isCreatingQR) return;
				AppListFragment appFragment = new AppListFragment();
				appFragment.setTargetFragment(sourceFrag, ACTION_APP);
				fragmentCallback.moveToFragment(TAG, appFragment);
		    }
		});
		getView().findViewById(R.id.encode_textField)
			.setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(isCreatingQR) return;
				TextInputFragment appFragment = new TextInputFragment();
				appFragment.setTargetFragment(sourceFrag, ACTION_TEXT);
				fragmentCallback.moveToFragment(TAG, appFragment);
		    }
		});		
		TextView shareAuthpaper = (TextView) getView().findViewById(R.id.encode_shareAuthPaper);
		if(edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer) 
			shareAuthpaper.setText(R.string.encode_shareAuthPaperPaid);
		shareAuthpaper.setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(isCreatingQR) return;
				AlertDialog.Builder builder = new AlertDialog.Builder(context);
		        builder.setTitle("Online service");
		        builder.setMessage("This service needs to be done on our website :\n"
		        		+ "http://www.authpaper.net/" + "\n Visit Now?");
		        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
								Uri.parse("http://www.authpaper.net/"));        
						startActivity(browserIntent);
					}
				});
		        builder.setNegativeButton("Cancel", null);
		        builder.show();
		    }
		});
		if(!edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer){
			View isSign=getView().findViewById(R.id.encode_signMsg);
			if(isSign !=null){
				ViewParent layout = isSign.getParent();
				if(layout !=null) ((ViewGroup) layout).removeView(isSign);
			}
		}else{
			View isSign=getView().findViewById(R.id.encode_signMsg);
			if(isSign !=null){
				((CheckBox) isSign).setOnCheckedChangeListener(new OnCheckedChangeListener(){
					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						if(isCreatingQR) return;
						isSignQRCode=isChecked;
					}
				});
			}
		}
	}
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data){
		if(data ==null) {
			alert("No valid data returned");
			return;
		}
		String result="";
		switch(requestCode){
			case ACTION_CONTACT: 
				try{
					result=MEcardEncoder.showContactAsBarcode(this.context, data.getData());
				}catch(Exception e2){
					alert("Cannot read contact. Please enable contact access in Setting -> Apps -> "
							+context.getString(R.string.app_name)+" -> Permission in order to use this feature.",true);
				}
				break;
			case ACTION_APP:
			case ACTION_TEXT:
				result=data.getStringExtra(result_text_index);
				break;
		}
		if(!result.isEmpty()) createQRcode(result);
	}

	private void createQRcode(String text) {
		if(text ==null || text.isEmpty()) {
			alert("No text is selected.");
			return;
		}
		Result result=null;
		if(edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer && isSignQRCode){			
				//Now check if there is any access token
				android.content.SharedPreferences sharedPref = android.preference.PreferenceManager
						.getDefaultSharedPreferences(context);
				String accessToken=sharedPref.getString(getString(R.string.pref_google_plus_token), null);
				if(accessToken ==null)
					alert("Cannot read / validate your Google+ acc for signing",true);
				else new fetchQRTask().execute(accessToken,text);
				return;
		}
		if(result ==null)
			result=new Result(text, null, null, BarcodeFormat.QR_CODE,null);
		moveToResult(result,text);
	}
	private void moveToResult(Result result,String text){
		ResultFragment resultFragment = new ResultFragment();
		//Save it into db
		HistoryFragment history = HistoryFragment.getInstance(true);
		if(history !=null){
			ParsedResult parsedResult = ResultParser.parseResult(result);
			ParsedResultType type=parsedResult.getType();
			int textLength = (text.length()<30)? text.length() : 30; 
			history.insertEntry(text.substring(0, textLength), ""+type, result);
		}
		//TODO: make it Parcelable (can be passed by set Arguments)
		resultFragment.setResult(result, true);
		resultFragment.isShowBarcodeFirst(true);
		this.fragmentCallback.moveToFragment(TAG, resultFragment);
	}
	
	private void handleIntent(Intent intent){
		String text =null;
		if(intent.hasExtra(Intent.EXTRA_STREAM)){
			Bundle bundle=intent.getExtras();
			if(bundle !=null){
				Uri uri = (Uri) bundle.getParcelable(Intent.EXTRA_STREAM);
				if(uri !=null){
					byte[] vcard;
				    try {
				    	InputStream stream = this.context.getContentResolver()
				    							.openInputStream(uri);
				    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
				    	byte[] buffer = new byte[2048];
				    	int bytesRead;
				    	while((bytesRead = stream.read(buffer)) >0)
				    		baos.write(buffer, 0, bytesRead);
				    	vcard = baos.toByteArray();
				    	text = new String(vcard, 0, vcard.length, "UTF-8");
				    	text = text.trim();
				    } catch (IOException ioe) {
				    	alert("Contact information detected, but no readable.");
				    }
				}
			}
		}
		if(text ==null || text.isEmpty()){
			// Notice from zxing: Google Maps shares both URL and details in one text, bummer!
			String[] tryList = new String[]{
									Intent.EXTRA_TEXT,
									"android.intent.extra.HTML_TEXT",
									Intent.EXTRA_SUBJECT,
									};
			for(int i=0, count=tryList.length;i<count;i++){
				text = intent.getStringExtra(tryList[i]);
				if(text !=null && !text.trim().isEmpty()) {
					text=text.trim();
					break;
				}
			}
			if(text ==null || text.isEmpty()){
				String[] emails = intent.getStringArrayExtra(Intent.EXTRA_EMAIL);
				if(emails !=null && emails.length >0 && emails[0]!=null) 
					text=emails[0].trim();
			}
		}
		if(text !=null && !text.isEmpty()){
			isBackFromIntent=true;
			createQRcode(text);
		}
	}
	private class fetchQRTask extends AsyncTask<String, Void, Result>{
		private String text="";
		@Override
		protected void onPreExecute(){
			isCreatingQR=true;
			alert("Sending data to the AuthPaper service for signing");
		}
		@Override
		protected Result doInBackground(String... params) {
			if(params.length !=2) return null;
			String accessToken=params[0],text=params[1];
			this.text=text;
			return FetchQRcodeService.fetchQRcodeResult(context,accessToken,text);
		}
		@Override 
		protected void onPostExecute(Result result){
			isCreatingQR=false;
			if(result ==null){
				alert("Cannot get the signed QR code from the server");
				return;
			}
			if(result.getBarcodeFormat() ==null){
				alert("Error on the QR code creation server : "+result.getText(),true);
				return;
			}
			moveToResult(result,text);
		}
	}
}