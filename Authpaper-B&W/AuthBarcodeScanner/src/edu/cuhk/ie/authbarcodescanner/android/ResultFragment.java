/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.client.result.Auth2DBarcodeParsedResult;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ParsedResultType;
import com.google.zxing.client.result.ResultParser;

import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;
import edu.cuhk.ie.authbarcode.SecretMessageHandler;
import edu.cuhk.ie.authbarcodescanner.android.history.HistoryDbEntry;
import edu.cuhk.ie.authbarcodescanner.android.result.AuthBarcodeHandler;
import edu.cuhk.ie.authbarcodescanner.android.result.CompositeTemplateHandler;
import edu.cuhk.ie.authbarcodescanner.android.result.LightBarcodeEncoder;
import edu.cuhk.ie.authbarcodescanner.android.result.ResultHandler;
import edu.cuhk.ie.authbarcodescanner.android.result.webViewHandler;
import edu.cuhk.ie.authbarcodescanner.certificate.CertificateDbEntry;
import edu.cuhk.ie.authbarcodescanner.certificate.CertificateDbHelper;

/**
 * This fragment handles the 2D barcode scanning result, verify the content and display to users
 */
public class ResultFragment extends StandardFragment {
	public static final String TAG=ResultFragment.class.getSimpleName();
	private static final int layoutXML=R.layout.fragment_result;

	private Result rawResult;
		
	private boolean isRecordSaved=false, isShowBarcodeFirst=false;
	private Bitmap barcodeImage=null;
	private List<Button> previousButtonList=null;
	private String previousTitle;
	private String previousTitleFull;
	private int menuItemId;
	private static final int[] menuToDelete={R.id.action_logout};
	
	// list of img id and image file names used in this view
	private Map<String, String> imgFilename = null;
	
	// reference to history entry
	private HistoryDbEntry entry;
	private boolean isEncodeHistory=false;
	
	// OpenGL references
	private GLSurfaceView glSurfaceView;
	private boolean rendererSet = false;
	//private String pillDesc = "";
	@SuppressLint("NewApi")
	public ResultFragment() { 
		setLayout(layoutXML);
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
			WebView.enableSlowWholeDocumentDraw();
	}
	
	public void setEntry(HistoryDbEntry dbEntry, boolean isEncode){
		this.entry = dbEntry;		
		this.isEncodeHistory = isEncode;
	}

	public void setResult(Result rawResult, boolean isSaved){
		this.rawResult=rawResult;
		isRecordSaved=isSaved;
	}
	public void isShowBarcodeFirst(boolean isShow){
		isShowBarcodeFirst=isShow;
	}
	
	/**
	 * Do the real thing here
	 */
	@Override
	public void onResume(){
		super.onResume();
		//If no scanning result is returned
		if(rawResult == null){
		    errorAndReturn("No result is scanned.");
		    return;
		}
		//First parse the result
		//Hardcode, subtitle indicates whether the scanner scans CSCIT badges
		String headerText=null, textToShow=null;
		Bitmap imageToShow=null;
		List<Button> buttonToShow=new ArrayList<Button>();
		LinearLayout buttonList = (LinearLayout) getView().findViewById(R.id.result_buttonList);
		buttonToShow.add((Button) buttonList.getChildAt(0));
		
		ParsedResult result=ResultParser.parseResult(rawResult);
		//Hardcode fix to a sample
		if(rawResult.getText().startsWith("binary:") 
			&& rawResult.getByteMetadata(ResultMetadataType.BYTE_SEGMENTS) !=null){
			result = new Auth2DBarcodeParsedResult(rawResult.getByteMetadata(ResultMetadataType.BYTE_SEGMENTS));
		}                                                                                                                                                                 
		//create the QR code image and display it
		ImageView resultCodeImage = (ImageView) getView().findViewById(R.id.result_qrcode_image);
		Button showQRcodeButton = (Button) getView().findViewById(R.id.result_showQRcodeButton);
		if(showQRcodeButton ==null){
			showQRcodeButton=StandardButton.resultButton(this.context,R.string.button_codeimage,R.drawable.result_qrcode);
			showQRcodeButton.setId(R.id.result_showQRcodeButton);
		}
		barcodeImage=LightBarcodeEncoder.reconstructBarcode(result, rawResult);
		if(barcodeImage ==null){
			alert("The data is too large to be reconstructed as one 2D barcode.",true);
			barcodeImage=BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
		}

		resultCodeImage.setImageBitmap(barcodeImage);
		showQRcodeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onChangeResultView();
			}
		});
		//Make sure it is showing the result when parsing the result
		previousButtonList=null;
		if(resultCodeImage.getVisibility() ==View.VISIBLE) onChangeResultView();
		boolean isHTML=false;
		//Handle the result according to its type
		TextView issuerTextView = (TextView) getView().findViewById(R.id.result_issuerTextView);
		if(result.getType() !=ParsedResultType.AUTH2DBARCODE){
			//It is not authenticated
			isVerifiedView(issuerTextView,false,"","","");
			ResultHandler handler = new ResultHandler(this.context, rawResult);
			//imageToShow=handler.getImage();
			headerText=getString(R.string.msg_default_content_header) + handler.getType();
			textToShow=handler.getText();
			buttonToShow.addAll(handler.getButtons());
		}else{
			String fileType = result.getFileType();
    		if(fileType==null) {
    			errorAndReturn("Content not supported.");
    			return;
    		}
    		Auth2DbarcodeDecoder resultDecoder=null;
    		if(fileType.indexOf(String.class.getSimpleName()) != -1){
    			resultDecoder=Auth2DbarcodeDecoder.getSupportedDecoderString(result.getFileString());
    		}else if(fileType.indexOf(Byte.class.getSimpleName()) != -1)
    			resultDecoder=Auth2DbarcodeDecoder.getSupportedDecoderByte(result.getFileByte());
    		if(resultDecoder==null){
    			errorAndReturn("Content not supported.");
    			return;
    		}
    		//Get the keystore from the main activity for data verification 
			KeyStore store=this.fragmentCallback.startKeyStore();
			if(store==null) alert("Cannot access the key store to verify the message",true);
			try {
				if( (resultDecoder.isDecodingString() 
							&& !resultDecoder.decodeBarcodeString(resultDecoder.getUndecodedString(),store))
					||(!resultDecoder.isDecodingString() 
							&& !resultDecoder.decodeBarcodeByte(resultDecoder.getUndecodedByte(),store)) ){
					Log.d(TAG,"Cannot decode the auth2dbarcode");
					throw new Exception("Format not supported");
				}
				if(resultDecoder.getDataCount() <1) throw new Exception("Cannot read the data");
			} catch (Exception e1) {
				errorAndReturn("The Authenticated 2D barcode is not supported. "+e1.getMessage());
				return;
			}
    		//Log.d(TAG,"Displaying the data.");
    		boolean isVerified=resultDecoder.isBarcodeVerified();
    		//If the digital certificate is not stored in the keyStore, we alert a message to user to trust it or not
    		if(resultDecoder.getSenderCert() !=null){
    			if(edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer)
    				handleNewCertificate(isVerified, resultDecoder, store);
    			else alert("Saving new certificate is not included in this version",true);
    		}
			//Log.d(TAG, Displaying the barcode content);
			headerText="AuthPaper";
			
			String title="";
			//Check if it is using composite template
			CompositeTemplateHandler compositeHeader=CompositeTemplateHandler.findSuitableHandler(this,resultDecoder);
			if(compositeHeader !=null){
				try{
					resultDecoder.insertData("index.html", "text/plain", compositeHeader.getHTML());
				}catch(Exception e){ }
				title=compositeHeader.getTitle();
				//pillDesc = compositeHeader.getPillAppearance();
			}
			//Parse the html file
			File tempFolder = getTempFolder();
			AuthBarcodeHandler handler = new AuthBarcodeHandler(this.context, resultDecoder, barcodeImage, tempFolder, imgFilename);
			// store the list of temp image names created so it can find it later
			if (imgFilename == null) {
				imgFilename = handler.getImgNames();	
			}
			isVerifiedView(issuerTextView, isVerified, resultDecoder.getIssuerDisplayName()
					,resultDecoder.getSenderName(), handler.getHTML());
			if(handler.isHTML()){
				isHTML=true;
				//Log.d(TAG, handler.getHTML());
				final WebView resultWebView = (WebView) getView().findViewById(R.id.result_content_webview);
				String rawHtmlStr = handler.getHTML();
				webViewHandler.displayWebpage(resultWebView, "file://"+tempFolder.getAbsolutePath(), rawHtmlStr);
				Button save_file_button=StandardButton.resultButton(this.context, R.string.button_saveFile, 
						R.drawable.result_save);
				save_file_button.setOnClickListener(new View.OnClickListener() {		
					@Override
					public void onClick(View v) {
						if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT){
							alert("Using system painting");
							StandardButton.paintwebview(fragmentCallback, resultWebView, "Document");
							return;
						}
						//force the webview to zoomout so that the save button can save the complete document
						for(int i = 0; i<10 && resultWebView.zoomOut(); i++){ }
						Bitmap viewShot = Bitmap.createBitmap(resultWebView.getMeasuredWidth(), 
				        		resultWebView.getContentHeight(), Config.ARGB_8888);
				        Canvas canvas = new Canvas(viewShot); 
				        resultWebView.draw(canvas);
						boolean isPDF=false;									        
						//File savedPDFFile=StandardButton.paintImageToPDF(context,viewShot, 
							//		"Scanned_Document","Document");
						//if(savedPDFFile ==null || savedPDFFile.getAbsoluteFile() ==null){
							//Default way failed, use the depreciated way
							File savedPDFFile=StandardButton.saveBitmap(context, viewShot, 
									"Scanned_Document", "Document");
						//}else isPDF=true;
						if(savedPDFFile !=null && savedPDFFile.getAbsolutePath() !=null){
							String path=savedPDFFile.getAbsolutePath();
							if(path.contains("/0/")) path=path.substring(path.lastIndexOf("/0/")+3);
							if(isPDF) alert("Saved as PDF in SD card : "+path);
							else alert("Painting not supported. Saved as image saved in SD card : "+path);
						} else alert("Cannot save the result into SD card. Please enable SD card access in Setting -> Apps -> "
								+context.getString(R.string.app_name)+" -> Permission in order to use this feature.");
					}
				});
				buttonToShow.add(save_file_button);
				textToShow=(!title.isEmpty())? title:handler.getTitle();
			}else{ 
				//If the content is not HTML
				Bitmap[] images=handler.getImages();
				if(images !=null && images.length >0 && images[0] !=null)
					imageToShow=images[0];				
				textToShow=handler.getHTML();
				buttonToShow.addAll(handler.getButtons());
				//TODO: remove this legacy demo hard code
				String issuerInfo=resultDecoder.getIssuerName();
				//Log.d(TAG,issuerInfo);
				if(isVerified && issuerInfo.compareTo("auth2dbarcode")==0){
					if(textToShow.contains("Li Chak Man") 
							&& textToShow.contains("Identity Card")
							&& imageToShow==null){
						imageToShow = BitmapFactory.decodeResource(getResources(),
						          R.drawable.solonphotosmall);
					}
					if(textToShow.contains("Degree") && imageToShow==null){
						imageToShow = BitmapFactory.decodeResource(getResources(),
						          R.drawable.iconcuhksmall);
					}
				}
			}
			//Given that it is AUTH2DBARCODE, test if it contains any secret message
			Object secretObj=resultDecoder.getDataById("secretMsg");
			if(secretObj !=null){				
				final SecretMessageHandler secretMsg=new SecretMessageHandler(secretObj.toString());
				if(secretMsg.isSecretExist()){
					Button read_secret_button=StandardButton.resultButton(this.context, 
							R.string.button_readSecret, R.drawable.result_secret);
					read_secret_button.setOnClickListener(new promptForPasswordClickListener(this.context,secretMsg));
					buttonToShow.add(read_secret_button);
				}
			}
		}
		//Display the result
		//First, the title bar
		int textLength = (textToShow.length()<30)? textToShow.length() : 30;
		String titleText = textToShow.substring(0, textLength);
		saveTitle();
		setTitle(titleText);
		TextView resultTextHeaderView = (TextView) getView().findViewById(R.id.result_content_text_header);
		resultTextHeaderView.setText(headerText);
		TextView resultTextView = (TextView) getView().findViewById(R.id.result_content_text);
		if(!isHTML && textToShow !=null){
			resultTextView.setText(textToShow);
			resultTextView.setVisibility(View.VISIBLE);
			resultTextView.setTextSize(28);
		} else resultTextView.setVisibility(View.GONE);
		ImageView resultImageView = (ImageView) getView().findViewById(R.id.result_content_image);
		if(imageToShow !=null){
			resultImageView.setImageBitmap(Bitmap.createScaledBitmap(
					imageToShow, imageToShow.getWidth()*2, imageToShow.getHeight()*2, false
			));
			resultImageView.setVisibility(View.VISIBLE);
		} else resultImageView.setVisibility(View.GONE);
		buttonList.removeAllViews();
		StandardButton.appendButtons(this.context, buttonList, buttonToShow);
		//If we should show the barcode first, then we change view here.
		if(isShowBarcodeFirst) onChangeResultView();
		//Make sure the record only saves once
		HistoryFragment historyFragment = HistoryFragment.getInstance(isEncodeHistory);
		if(!isRecordSaved && historyFragment !=null){
			textLength = (textToShow.length()<30)? textToShow.length() : 30;
			String newTitle = textToShow.substring(0, textLength);
			long newId = historyFragment.insertEntry(newTitle, headerText, rawResult);
			if(newId != -1) entry = historyFragment.findEntry(newId);	
			
		}
		isRecordSaved=true;
		
		//Update the title bar
		String title = "";
		if(entry != null) title = entry.getDescription();			
		if(title.isEmpty()) title = textToShow;
		
		textLength = (title.length()<30)? title.length() : 30;
		titleText = title.substring(0, textLength);
		previousTitleFull = title;
		setTitle(titleText);
		System.gc();
	}
	@Override
	public void onPause(){
		super.onPause();
		//Change back the title
		setTitle(previousTitle);
		if(rendererSet) glSurfaceView.onPause();		
	}	

/*
 * Create a custom menu
 */
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		this.setHasOptionsMenu(true);
	}
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    // TODO Add your menu entries here
	    super.onCreateOptionsMenu(menu, inflater);
	    if(menuToDelete !=null){
			for(int i=0,l=menuToDelete.length;i<l;i++){
				if(menuToDelete[i] >0) menu.removeItem(menuToDelete[i]);
			}
		}
	    MenuItem item=menu.add(R.string.action_changeRecordTitle);
	    menuItemId=item.getItemId();
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		int id = item.getItemId();
		if(id ==menuItemId){
			AlertDialog.Builder builder = new AlertDialog.Builder(this.context);
			AlertDialog dialog;
			builder.setTitle(getString(R.string.edit_dialog_title));
			
	  	    final EditText inputDesc = new EditText(this.context);
	  	    inputDesc.setGravity(Gravity.TOP);
	  	    inputDesc.setLines(3);
	  	    inputDesc.setText(previousTitleFull);
		    builder.setView(inputDesc);
		    	    
			builder.setPositiveButton(getString(R.string.gen_save), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String newDesc = inputDesc.getText().toString();
					if (newDesc != previousTitleFull && !newDesc.isEmpty()) {
						HistoryFragment historyFragment = HistoryFragment.getInstance(isEncodeHistory);
						Log.d(TAG,"Updating title with new name : "+newDesc+" and old name "+previousTitleFull);
						historyFragment.updateEntry(newDesc, entry);
						
						int textLength = (newDesc.length()<30)? newDesc.length() : 30;
						String titleText = newDesc.substring(0, textLength);
						previousTitleFull = newDesc;
						setTitle(titleText);						
					}
					dialog.dismiss();					
				}
			});
		    builder.setNegativeButton(getString(R.string.gen_cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
					//hide keyboard
				}
			});
		    //show keyboard
		    dialog = builder.create();
		    dialog.getWindow().setSoftInputMode(
		    		WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		    dialog.show();			
		}
		return false;
	}
	
/*
 * UI related functions
 * 
 */
	private void saveTitle(){
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
			ActionBar actionBar = this.context.getActionBar();			
			if(actionBar !=null)
				previousTitle=(actionBar.getSubtitle() !=null)? actionBar.getSubtitle().toString() : null;
			else{
				android.support.v7.app.ActionBar aBar = this.fragmentCallback.getSupportActionBar();
				if(aBar !=null){
					previousTitle=(aBar.getSubtitle() !=null)? aBar.getSubtitle().toString() : null;					
				}
			}
		}
	}
	private void setTitle(String title){
		String titleText=(title ==null || title.isEmpty())? "" : "Saved as "+title; 
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
			ActionBar actionBar = this.context.getActionBar();			
			if(actionBar !=null) actionBar.setSubtitle(titleText);
			else{
				android.support.v7.app.ActionBar aBar = this.fragmentCallback.getSupportActionBar();
				if(aBar !=null) aBar.setSubtitle(titleText);				
			}
		}
	}
	private void onChangeResultView(){
		LinearLayout resultView = (LinearLayout) getView().findViewById(R.id.result_resultView);
		ImageView resultCodeImage = (ImageView) getView().findViewById(R.id.result_qrcode_image);
		Button showQRcodeButton = (Button) getView().findViewById(R.id.result_showQRcodeButton);
		if(showQRcodeButton ==null){
			showQRcodeButton=StandardButton.resultButton(this.context,R.string.button_codeimage,R.drawable.result_qrcode);
			showQRcodeButton.setId(R.id.result_showQRcodeButton);
			showQRcodeButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onChangeResultView();
				}
			});
		}
		boolean isShowingCode=(resultCodeImage.getVisibility() ==View.VISIBLE);
		//Change the view
		resultView.setVisibility( (isShowingCode)? View.VISIBLE : View.GONE);
		resultCodeImage.setVisibility((isShowingCode)? View.GONE : View.VISIBLE);
		showQRcodeButton.setCompoundDrawablesWithIntrinsicBounds(0, 
				(isShowingCode)? R.drawable.result_qrcode : R.drawable.result_text, 0, 0);
		showQRcodeButton.setText((isShowingCode)? R.string.button_codeimage : R.string.button_codeimage_alter);
		if(previousButtonList ==null){
			previousButtonList = new ArrayList<Button>();
			Button saveCodeButton=StandardButton.resultButton(this.context, R.string.button_saveFile, 
					R.drawable.result_save);
			saveCodeButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					File savedImageFile=StandardButton.saveBitmap(context, barcodeImage, 
							"Scanned_Barcode", "Barcode");
					if(savedImageFile !=null && savedImageFile.getAbsolutePath() !=null){
						String path=savedImageFile.getAbsolutePath();
						if(path.contains("/0/")) path=path.substring(path.lastIndexOf("/0/")+3);
						alert("Image saved in SD card : "+path);
					} else alert("Cannot save image to the SD card. Please enable SD card access in Setting -> Apps -> "
							+context.getString(R.string.app_name)+" -> Permission in order to use this feature.");
				}
			});
			previousButtonList.add(showQRcodeButton);
			previousButtonList.add(saveCodeButton);
		}
		LinearLayout buttonList = (LinearLayout) getView().findViewById(R.id.result_buttonList);
		List<Button> dummyList = new ArrayList<Button>();
		for(int i=0, count=buttonList.getChildCount(); i<count; i++){
			if(buttonList.getChildAt(i) instanceof Button)
				dummyList.add((Button) buttonList.getChildAt(i));
		}
		buttonList.removeAllViews();
		//buttonList.addView(showQRcodeButton);
		StandardButton.appendButtons(this.context, buttonList, previousButtonList);
		previousButtonList.clear();
		previousButtonList.addAll(dummyList);
	}
	
	private void handleNewCertificate(boolean isVerified, Auth2DbarcodeDecoder resultDecoder, final KeyStore store){
		final X509Certificate newCertificate=resultDecoder.getSenderCert();
		final Context context=this.context;		
		//First, check if any certificate under the same name exists
		//It should not be possible as this function is called only if a new cert is found, but things happen
		CertificateDbEntry newEntry = Auth2DbarcodeDecoder.convertCrtToCrtDb(newCertificate);
		CertificateDbHelper certDb = new CertificateDbHelper(context, CertificateDbHelper.DB_NAME, null, 1);
		boolean isReCert=false;
		if(newEntry !=null && certDb !=null){
			String alias=newEntry.getAlias();
			try{
				if(store.getCertificate(alias) !=null){
					if(certDb.isCertificateExist(alias, CertificateDbHelper.CERT_SYS)){
						alert("Cannot store the embedded certificate due to a colision with system certificates.",true);
						return;
					}
					if(certDb.isCertificateExist(alias, CertificateDbHelper.CERT_USER)){
						X509Certificate oldCert=(X509Certificate) store.getCertificate(alias);
						if(oldCert !=null && oldCert.getEncoded() == newCertificate.getEncoded()){
							Log.d(TAG,"The new certificate is same as the old one");
							return;
						}
						isReCert=true;
					}
				}
			}catch(KeyStoreException e){
			}catch(CertificateEncodingException e){ 
			}
		}
		final boolean isReplacingCert=isReCert;
		//Save the digital certificate
		String alertTitle=null,alertMsg=null;
		if(isVerified && !isReplacingCert){
			if(saveCertificate(context,newCertificate,store,isReplacingCert)) 
				alert("New digital certificate is saved",true);
			else alert("Failed to save the new certificate. It may due to the error on key store.",true);
			return;
		}else if(isVerified && isReplacingCert){
			String subjectDN=(newEntry !=null)? newEntry.getAlias() 
        			: Auth2DbarcodeDecoder.getCertificateSubjectCN(newCertificate);
			alertTitle="Replacing certificate by the embedded one?";
			alertMsg="A certificate in the storage shares the same holder with the embedded certificate. \n"
        			+"Both certificates are trusted by the local certificate storage. \n"
        			+"Should we replace the old certificate with the embedded one? \n"
        			+"Holder of the certificates :\n"
        			+subjectDN;
		}else if(!isVerified && !isReplacingCert){
			alertTitle="Unverifiable digital certificate found";
			alertMsg="Save this certificate (Trust all data from this certificate holder afterwards)?\n"
        			+"Advice: Do not save it unless you confirm it is trustworthy.\n"
					+"Holder of this certificate (not verified) :\n"+newCertificate.getSubjectX500Principal().getName()
					+"\n Issuer :\n"+newCertificate.getIssuerX500Principal().getName();
		}else{
			alertTitle="Replacing certificate by the embedded (unverifiable) one?";
			alertMsg="A certificate in the storage shares the same holder with the embedded certificate. \n"
					+"But the embedded certificate cannot be verified by the local certificate storage. \n"
					+"Should we replace the trusted one by this unverifiable one? \n"
					+"Suggestion: No.\n"
					+"Holder of this certificate :\n"+newCertificate.getSubjectX500Principal().getName()
					+"Issuer :\n"+newCertificate.getIssuerX500Principal().getName();
		}
		if(alertTitle !=null && alertMsg !=null){
			final AlertDialog.Builder builder = new AlertDialog.Builder(this.context);
        	builder.setTitle(alertTitle);
        	builder.setMessage(alertMsg);
        	builder.setPositiveButton("Save it", new DialogInterface.OnClickListener(){
        	    @Override
        	    public void onClick(DialogInterface dialogInterface, int i) {
        	    	if(saveCertificate(context,newCertificate,store,isReplacingCert)) 
        	    		alert("New digital certificate is saved",true);
        			else alert("Failed to save the new certificate. It may due to the error on key store.",true);
        	    }
        	  });
        	builder.setNegativeButton("Decide later or No", null);
        	builder.show();
		}
	}
	/**
	 * A static method to store new digital certificate in the way CertificateActivity.java does
	 * @param newCertificate
	 * @param store
	 * @return
	 */
	private static boolean saveCertificate(Context context, X509Certificate inputCert, KeyStore store, boolean update){
		X509Certificate oldCert=null;
		CertificateDbEntry newEntry = Auth2DbarcodeDecoder.convertCrtToCrtDb(inputCert);
		if(newEntry ==null) return false;
		try{
			if(update) oldCert=Auth2DbarcodeDecoder.getCertificate(store, newEntry.getAlias());
			Auth2DbarcodeDecoder.storeCertificate(store, inputCert);
		}catch(KeyStoreException e){ 			
			return false;
		}
		//save certificate to keystore
		boolean isDone=true;
		CertificateDbHelper certDb = new CertificateDbHelper(context, CertificateDbHelper.DB_NAME, null, 1);		
		if(update){			
			int edited = certDb.updateCertDate(newEntry.getAlias(), null, newEntry.getIssued(), newEntry.getExpire());
			if(edited != 1){
				Log.e(TAG, "Error updating certificate, returned " + String.valueOf(edited));
				isDone=false;
			}
		}else{
			long inserted = certDb.insertCert(newEntry.getAlias(), CertificateDbHelper.CERT_USER, 
					newEntry.getExpire(), newEntry.getIssued());
			if(inserted < 0){
				Log.e(TAG, "Error inserting certificate, returned " + String.valueOf(inserted));
				isDone=false;
			}
		}
		if(!isDone){
			//In case something goes wrong, return to the previous state
			try{
				if(update && oldCert !=null) Auth2DbarcodeDecoder.storeCertificate(store, oldCert);
				else Auth2DbarcodeDecoder.removeCertificate(store, newEntry.getAlias());
			}catch(KeyStoreException e){ }
		}
		return isDone;
	}
		
	private void isVerifiedView(TextView issuerTextView, boolean isVerified, String issuerInfo, 
			String senderName, String textContent){
		//TODO:remove this hard code. The hard code is built for the old demos
		//Log.d(TAG, issuerInfo);
		if(isVerified && issuerInfo.contains("auth2dbarcode") && issuerInfo.contains("solon.android@gmail.com")){
			if(textContent.contains("���䤤��j��")) issuerInfo="���䤤��j��";
			if(textContent.contains("The Chinese University of Hong Kong") 
					&& textContent.contains("Chan, Tai Man")) 
				issuerInfo="The Chinese University of Hong Kong";
			if(textContent.contains("Li Chak Man") || textContent.contains("Identity Card")) 
				issuerInfo="Hong Kong Immigration Department";
			if(textContent.contains("Bank of China (Hong Kong) Limited") 
					&& textContent.contains("System Technology Media Services")){
				issuerInfo="Bank of China (Hong Kong) Limited";
				senderName="System Technology Media Services";
			}
			if(textContent.contains("Hong Kong Baptist Hospital") && textContent.contains("Li Siu Ming")){
				issuerInfo="Hong Kong Baptist Hospital";
				senderName="Li Siu Ming";
			}

			if(textContent.contains("�s�F�ٲ`�`����a�|�ȧ�")) issuerInfo="�s�F�ٲ`�`����a�|�ȧ�";
			if(textContent.contains("Hong Kong Examinations and Assessment Authority")) 
				issuerInfo="Hong Kong Examinations and Assessment Authority";
		}
		if(!isVerified && issuerInfo.contains("Li Siu Ming")
			&& textContent.contains("Hong Kong Baptist Hospital") && textContent.contains("Li Siu Ming")){
			isVerified=true;
			issuerInfo="Hong Kong Baptist Hospital";
			senderName="Li Siu Ming";
		}
		if(!isVerified && issuerInfo.contains("CUHK")
			&& textContent.contains("The Chinese University of Hong Kong") && textContent.contains("Chan, Tai Man")){
			isVerified=true;
			issuerInfo="The Chinese University of Hong Kong";
		}
		if(!isVerified && issuerInfo.contains("(cmli@ie.cuhk.edu.hk)")){
			isVerified=true;
			issuerInfo="AuthPaper Samples";
			//issuerInfo="Sample Bank";
		}
		
		if(isVerified && issuerInfo.compareTo("Hong Kong Immigration Department")==0)
			issuerInfo="Immigration Department";
		
		if(issuerInfo !=null && !issuerInfo.isEmpty()){
			//If no alternative name is found, return the CN+OU+O+L
			//TODO: remove this hard code
			int issuerLength=issuerInfo.length();
			String[] issuerFields={"CN=", "OU=", "O=", "L="}; //ST will not be shown
			String limiter=",";
			String issuer="";
			for(int i=0;i<issuerFields.length;i++){
				int fieldStartInt=issuerInfo.lastIndexOf(issuerFields[i], issuerLength-issuerFields[i].length());
				if(fieldStartInt <0) continue;
				int lastIndex=issuerInfo.indexOf(limiter, fieldStartInt);
				if(lastIndex > fieldStartInt+issuerFields[i].length())
					issuer += issuerInfo.substring(fieldStartInt+issuerFields[i].length(), lastIndex)+", ";
			}
			if(issuer.isEmpty()) issuer=issuerInfo;
			//If the certificate belongs to Facebook / Google+ account, remove the email
			Pattern p = Pattern.compile("\\([\\w=+\\-\\/][\\w='+\\-\\/\\.]*@[\\w\\-]+(\\.[\\w\\-]+)*(\\.[\\w]{2,6})\\)");
			if(issuer.startsWith("AuthPaper(for Google+ ") || issuer.startsWith("AuthPaper(for Facebook ")){
				Matcher m =p.matcher(issuer);
				if(m.find()) issuer=issuer.replace(m.group(), "");
			}
			
			String issuerMsg=(!isVerified)? getString(R.string.msg_notverified_sender_header) : "";
			if(senderName !=null && !senderName.isEmpty()) {
				// if issuer info is the same as sender name prefix, do not show sender name
				// sender name format: name (email address)
				if(!senderName.contains(issuerInfo)){
					//If issuerInfo already has an email, remove the one in the senderName
					Matcher m =p.matcher(senderName);
					if(m.find() && p.matcher(issuer).find()){						
						senderName=senderName.replace(m.group(), "");
					}
					issuerMsg += getString(R.string.msg_verified_sender_header)+senderName+"\n";
				}
			}
			issuerMsg += getString(R.string.msg_verified_issuer_header)+issuer;
			
			issuerTextView.setText(issuerMsg);
			//Reduce text size when there are too many words
			if(issuerMsg.length()>120)
				issuerTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 
						(int) (issuerTextView.getTextSize()*0.5));
			else if(issuerMsg.length()>60)
				issuerTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 
						(int) (issuerTextView.getTextSize()*0.8));
			
		} else issuerTextView.setText(getString(R.string.msg_default_issuer_text));
		
		issuerTextView.setTextColor(getResources().getColor( (isVerified)?
				R.color.result_issuerText_verified : R.color.result_issuerText_notVerified ));
	}
	
	
	private class promptForPasswordClickListener implements View.OnClickListener{
		final Context context;
		final SecretMessageHandler secretMsg;		
		public promptForPasswordClickListener(Context context, SecretMessageHandler secretMsg){
			this.context=context;
			this.secretMsg=secretMsg;			
		}
		@Override
		public void onClick(View v){
			final EditText input = new EditText(context);
			new AlertDialog.Builder(context)
		    .setTitle("Password needed")
		    .setMessage("Input PIN to read the secret message.")
		    .setView(input)		    
		    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int whichButton) {
		            String pw = input.getText().toString();
		            if(pw.isEmpty()){
		            	alert("No password is provided");
		            	return;
		            }
		            final String secretStr=secretMsg.decryptMsg(pw);
		            if(secretStr !=null && !secretStr.isEmpty()){
		            	dialog.dismiss();
		            	handleSecretString(secretStr);
		            }else alert("Incorrect password");
		        }
		    }).setNegativeButton("Cancel", null).show();
		}
		private void handleSecretString(final String secretStr){
			android.app.AlertDialog.Builder alertBuilder=new AlertDialog.Builder(context)
	        	.setNegativeButton("Copy to clipbox",new DialogInterface.OnClickListener(){
	    			public void onClick(DialogInterface dialog, int whichButton){
	    				android.content.ClipboardManager clipboard = 
	    					(android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE); 
					    android.content.ClipData clip = android.content.ClipData.newPlainText("barcode data",secretStr);
					    clipboard.setPrimaryClip(clip);
					    alert("Copied to clipboard");					    
						dialog.dismiss();
	    			}
	    		}).setPositiveButton("OK", null);
	    	alertBuilder.setTitle("Secret Message").setMessage(secretStr).show();
		}
	}	
}