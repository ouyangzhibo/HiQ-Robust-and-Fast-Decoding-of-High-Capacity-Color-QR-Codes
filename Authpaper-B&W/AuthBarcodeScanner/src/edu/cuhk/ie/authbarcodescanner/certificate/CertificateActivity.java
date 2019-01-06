/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.certificate;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Base64;
import android.view.MenuItem;
import android.view.Window;
import android.widget.Toast;
import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;
import edu.cuhk.ie.authbarcodescanner.analytic.UriHelper;
import edu.cuhk.ie.authbarcodescanner.android.DownloadResultReceiver;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import edu.cuhk.ie.authbarcodescanner.android.R;
import edu.cuhk.ie.authbarcodescanner.android.StandardButton;
import edu.cuhk.ie.authbarcodescanner.android.UpdateDigitalCertService;
import edu.cuhk.ie.authbarcodescanner.android.fragmentCallback;

public class CertificateActivity extends fragmentCallback implements 
	CertificateFragment.fragmentKeystoreListener, DownloadResultReceiver.Receiver {
	private static final String TAG = CertificateActivity.class.getSimpleName();
	private static final String updateURL="https://authpaper.net/updateDigitalCert.php";
	
	private KeyStore keystore;
	
	// layout
	private static final int containerID=R.id.certificateContainer;
	private static final int menuID=R.menu.certificate;
	private static final int[] menuToDeleteInFree={R.id.cert_add};
	private static final String PREFS_NAME = "DigitalCert";
	CertificateFragment certFrag;
	private boolean certDialogShown = false; 
	
	// certificate database
	private CertificateDbHelper certDb;
	
	// user input callback
	private static final int RESULT_CERT = 509;	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
		//Set display		
	    setContentView(R.layout.activity_certificate);
	    certFrag = new CertificateFragment();
	    init(savedInstanceState,containerID, menuID, certFrag);
	    setMenuToDeleteInFree(menuToDeleteInFree);

		// init database
		certDb = new CertificateDbHelper(this, CertificateDbHelper.DB_NAME, null, 1);
	    certFrag.setDatabase(certDb);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle presses on the action bar items
	    switch (item.getItemId()) {
	        case R.id.cert_add:
	        	if(isDeluxeVer) certAdd();
	            return true;
			case R.id.action_updateCert:	
				Toast.makeText(this, "Connecting authpaper server for certificate updates",
						Toast.LENGTH_SHORT).show();
				//get last update time from sharedPreferences
				SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE); 
				Long lastUpdateTime = prefs.getLong("lastUpdateTime", 0);
				//Long lastUpdateTime = (long) 1000000000;
				
				/* Starting Download Service */
				DownloadResultReceiver mReceiver = new DownloadResultReceiver(new Handler());
				mReceiver.setReceiver(this);
				Intent intent = new Intent(Intent.ACTION_SYNC, null, this, UpdateDigitalCertService.class);

				
				/* Send url and lastUpdateTime to Download IntentService */
				intent.putExtra("url", updateURL);
				//intent.putExtra("url", "http://192.168.64.75/authpaper/updateDigitalCert.php");
				intent.putExtra("receiver", mReceiver);
				intent.putExtra("lastUpdateTime", lastUpdateTime);
				                
                /* Call Download Service */
				startService(intent);
				//Log.d(TAG, "service called");
				
				
				return true;
	    }
	    return false;
	}
	
	// launch file browser to get certificate
	private void certAdd() {
		Log.d(TAG, "Launching file browser to get cert");
		Intent crtIntent = new Intent();
		crtIntent.setType("application/x-x509-ca-cert");
		crtIntent.setAction(Intent.ACTION_GET_CONTENT);
		crtIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
		startActivityForResult(Intent.createChooser(crtIntent,"Select Certificate"), RESULT_CERT);		
	}
	
	// check key store aliases
	public void checkKeystore() {
		try {
			Enumeration<String> str = keystore.aliases();
			Log.d(TAG, "Listing aliases " + keystore.size());
			while(str.hasMoreElements()){
				Log.d(TAG, str.nextElement());
			}
		} catch (KeyStoreException e) {
			Log.e(TAG, "Failed to load key store");
			e.printStackTrace();
		}		
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "certificate chosen");
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == RESULT_CERT) {
				Uri selectedCrtUri = data.getData();
				// don't add if online resource
				boolean offline = true;
				if (selectedCrtUri.toString().toLowerCase().contains("http".toLowerCase())) {
					offline = false;
				}
				if (offline) {
					
					String selectedCertPath = UriHelper.getPath(this, selectedCrtUri);
					Log.d(TAG, "Trying to get new certificate from uri");
					Log.d(TAG, "URI: " + selectedCrtUri.toString());					
					Log.d(TAG, "scPath: " + selectedCertPath);
					
					if (selectedCertPath != null ) {
						saveCertificateFromPath(selectedCertPath);
					}
					else {
						Toast.makeText(this, "Error loading certificate file", Toast.LENGTH_SHORT).show();
						return;
					}					
				}
				else {
					Toast.makeText(this, getText(R.string.gen_error_res_online), Toast.LENGTH_SHORT).show();
				}
			}
		}
	}
	public void saveCertificateFromPath(String certPath){
		saveCertificateFromPath(certPath,false);
	}
	public void saveCertificateFromPath(String certPath, boolean isUpdatingSys){
		Log.d(TAG,certPath);
		File certFile = new File(certPath);
		X509Certificate newCert = null;
		InputStream certInputStream = null;
	
		if (certFile.exists()) {
			try {
				// get x509 cert for comparison					
				certInputStream = new BufferedInputStream(new FileInputStream(certFile));
				newCert = Auth2DbarcodeDecoder.getCertificate(certInputStream);
			} catch (FileNotFoundException e) {
				Log.e(TAG, "Failed to convert file to file input stream. File not found");
				e.printStackTrace();
				return;
			}						
		}
		else {
			Log.e(TAG, "File does not exist, check the URI");
			return;
		}
		// don't re-add if existing
		if (newCert != null) {
			// test on issuer name
			// do not allow replace CERT_SYS
			// if match with existing CERT_USER
			//   if old certificate is expired, replace with new
			//   else confirm replacement					
			
			String alias = Auth2DbarcodeDecoder.getCertificateSubjectCN(newCert);
			CertificateDbEntry dbEntry = certDb.getCertificateByAlias(alias);
			if(dbEntry ==null){
				// new certificate, if updating system cert is allowed, set the new cert as system cert 
				saveCertificate(newCert, false, isUpdatingSys);
				return;
			}
			String certType = dbEntry.getCertType();
			boolean systemCert = certType.equals(CertificateDbHelper.CERT_SYS) ? true : false;
			if(systemCert){
				//system cert, if updating system cert then perform the action, else exit with error message  
				if(isUpdatingSys) saveCertificate(newCert, true, isUpdatingSys);
				else Toast.makeText(this, getText(R.string.cert_toast_denied), Toast.LENGTH_SHORT).show();							
				return;
			}
			//compare new and old user cert to confirm and replace 
			X509Certificate existCert = Auth2DbarcodeDecoder.getCertificate(keystore, alias);
			String retrieved = Auth2DbarcodeDecoder.getCertificateSubjectCN(existCert);
			Log.d(TAG, "retrieved cert for " + retrieved);							
			
			// else warn that user is replacing existing valid cert
			// test certificate dates
			Date newExpDate = newCert.getNotAfter();
			Date oldExpDate = existCert.getNotAfter();
			int dateCompare = newExpDate.compareTo(oldExpDate);

			// test public key
			PublicKey newPubKey = newCert.getPublicKey();
			PublicKey existPubKey = existCert.getPublicKey();						
			boolean samePubKey = newPubKey.equals(existPubKey);
			buildCertWarningDialog(newCert, dateCompare, samePubKey);							
		}
	
	}
	// insert or update certificate
	private void saveCertificate(X509Certificate inputCert, boolean update, boolean isSystem){
		// save certificate to database
		CertificateDbEntry newEntry = Auth2DbarcodeDecoder.convertCrtToCrtDb(inputCert);
		CertificateDbEntry oldEntry = null;
		if (update) {
			Log.d(TAG, "Updating old certificate in db");
			// update old record
			oldEntry=certDb.getCertificateByAlias(newEntry.getAlias());
			int edited = certDb.updateCertDate(newEntry.getAlias(), 
					(isSystem)? CertificateDbHelper.CERT_SYS : CertificateDbHelper.CERT_USER,
					newEntry.getIssued(), newEntry.getExpire());
			if (edited != 1) {
				Log.e(TAG, "Error updating certificate, returned " + String.valueOf(edited));
				return;
			}
		}
		else {
			Log.d(TAG, "Inserting new certificate in db");
			long inserted = certDb.insertCert(newEntry.getAlias(), 
					(isSystem)? CertificateDbHelper.CERT_SYS : CertificateDbHelper.CERT_USER, 
					newEntry.getExpire(), newEntry.getIssued());
			if (inserted < 0) {
				Log.e(TAG, "Error inserting certificate, returned " + String.valueOf(inserted));
				return;
			}
		}
		// certificate has been saved in database, try insert into keystore
		boolean saved;
		try {
			saved = Auth2DbarcodeDecoder.storeCertificate(keystore, inputCert);
			checkKeystore();
		} catch (KeyStoreException e) {
			// TODO Auto-generated catch block
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
			saved = false;
		}
		if (saved) {
			Log.d(TAG, "Saving certificate to database and keystore");
			// callback to fragment to update list view
			Log.d(TAG, "callback to update listview");
			certFrag.refreshUI();			
		}
		else{
			Log.e(TAG, "Failed to save certificate to keystore. Deleting record from database");			
			int deleted = certDb.deleteCertByName(newEntry.getAlias());
			if (deleted == 1) {
				Log.d(TAG, "Record deleted");					
			}
			else {
				Log.e(TAG, "Deleted " + String.valueOf(deleted) + " expecting 1");
			}
			//If update, add back the old record
			if(update && oldEntry !=null) 
				certDb.insertCert(oldEntry.getAlias(), oldEntry.getCertType(), oldEntry.getExpire(), oldEntry.getIssued());
		}
	}

	// warning to be shown if certificate already exists and not newer than existing
	public void buildCertWarningDialog(final X509Certificate newerCert, int dateCompare, boolean samePubKey) {
		if (!certDialogShown) {
			// build warning message
			StringBuilder strBld = new StringBuilder();
			strBld.append(getText(R.string.cert_warning_dialog));
			strBld.append("\nCertificate issued to : "+Auth2DbarcodeDecoder.getCertificateSubjectCN(newerCert));
			strBld.append("\nCertificate issued by : "+Auth2DbarcodeDecoder.getCertificateIssuerCN(newerCert));
			strBld.append("\nNote:");
			// 0 : same
			// + : newer
			// - : older			
			if (dateCompare > 0) {
				strBld.append("\n- New certificate has later expiry date");
			}
			else if (dateCompare < 0) {
				strBld.append("\n- New certificate has earlier expiry date.");
			} else if (dateCompare == 0) {
				strBld.append("\n- Certificates have the same expiry date.");
			}		
			if (!samePubKey) {
				strBld.append("\n- New certificate has a different public key.");
			}
			certDialogShown = true;
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
	        builder.setTitle(R.string.gen_warning);
	        builder.setMessage(strBld.toString());
	        builder.setPositiveButton(getText(R.string.gen_continue), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					saveCertificate(newerCert, true, false);
					certDialogShown = false;
				}
			});
	        builder.setNegativeButton(getText(R.string.gen_cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
					certDialogShown = false;
				}
			});		    
	        builder.show();
		}
	}	
	
	@Override
	public void onReturnResult(int requestCode, int resultCode, Intent data) {
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
	
	//Receive results from download service
	@Override
    public void onReceiveResult(int resultCode, Bundle resultData) {		
		switch (resultCode) {
            case UpdateDigitalCertService.STATUS_RUNNING:
                setProgressBarIndeterminateVisibility(true);
                break;
            case UpdateDigitalCertService.STATUS_FINISHED:
            	Toast.makeText(this, "Updating the local certificate storage", Toast.LENGTH_SHORT).show();
                /* Hide progress & extract result from bundle */
                setProgressBarIndeterminateVisibility(false);
                List<String> newCrts = resultData.getStringArrayList("newCrts");
                List<String> removedCrts = resultData.getStringArrayList("removedCrts");
                //Save each new certificate from external storage
                for(Iterator<String> i = newCrts.iterator(); i.hasNext(); ) {
                    String item = i.next();
                    Log.d(TAG, "Saving Cert:" + item);
                    saveCertificateFromPath(StandardButton.openReadFile(this, "certificate", item, "", false).toString(),true);
                }                
                //Delete each removed certificate
                if(removedCrts.size() > 0)
                	certFrag.deleteDigitalCertFromFilenames(removedCrts);
                certFrag.refreshUI();
				//Update the last update time in shared preference
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putLong("lastUpdateTime", System.currentTimeMillis()/1000);
                editor.commit();
                break;
            case UpdateDigitalCertService.STATUS_NOUpdate:
            	Toast.makeText(this, "The storage is up to date.",
            			Toast.LENGTH_LONG).show();
            	setProgressBarIndeterminateVisibility(false);
            	break;
            case UpdateDigitalCertService.STATUS_ERROR:
            	Toast.makeText(this, "Cannot retrieve data from the online database. Please try again later",
            			Toast.LENGTH_LONG).show();
            	setProgressBarIndeterminateVisibility(false);
                /* Handle the error */
            	Log.d(TAG, "Service error on updating certificate : " + resultData.getString(Intent.EXTRA_TEXT));
                //String error = resultData.getString(Intent.EXTRA_TEXT);
                //Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                break;
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		// get keystore
		keystore = KeystoreHolder.getInstance().getKeystore();
	}

	@Override
	public void onPause() {
		super.onPause();
		// save keystore		
		KeystoreHolder.getInstance().setKeystore(keystore);
		// update file
		saveKeyStore();
	}

	@Override
	protected boolean saveKeyStore() {
		// same implementation as main activity
		// need to save here in case user edits keystore but does not return to main activity 
		Log.d(TAG, "trying to save key store");
		new fragmentCallback.SaveKeyStoreTask().execute(keystore);
		return true;
	}
	
	// remove values from keystore
	@Override
	public boolean removeFromKeystore(String alias) {
		boolean keyRemove = Auth2DbarcodeDecoder.removeCertificate(keystore, alias);
		return keyRemove;
	}	
	
	// open certificate details when item clicked in list
	@Override
	public void listItemClicked(String alias) {
		X509Certificate cert = Auth2DbarcodeDecoder.getCertificate(keystore, alias);
		if (cert != null) {
			Intent i = new Intent(this, CertificateDetailActivity.class);
			i.putExtra(CertificateDetailActivity.CERT_ISSUED_TO, Auth2DbarcodeDecoder.getCertificateSubjectCN(cert));
			i.putExtra(CertificateDetailActivity.CERT_ISSUED_TO_FULL, cert.getSubjectX500Principal().getName());
			i.putExtra(CertificateDetailActivity.CERT_ISSUED_BY, Auth2DbarcodeDecoder.getCertificateIssuerCN(cert));
			i.putExtra(CertificateDetailActivity.CERT_ISSUED_BY_FULL, cert.getIssuerX500Principal().getName());
			
			i.putExtra(CertificateDetailActivity.CERT_DATE_ISS, cert.getNotBefore().getTime());
			i.putExtra(CertificateDetailActivity.CERT_DATE_EXP, cert.getNotAfter().getTime());
			
			byte[] pkBytes = cert.getPublicKey().getEncoded();
			//Hard-code: remove the first 23 header byte
			if(pkBytes.length > 23) pkBytes=java.util.Arrays.copyOfRange(pkBytes, 23, pkBytes.length);
			String pkStr = Base64.encodeToString(pkBytes, Base64.DEFAULT);			
			i.putExtra(CertificateDetailActivity.CERT_PUB_KEY, pkStr);
			
			startActivity(i);
		}
		else {
			Toast.makeText(this, "Failed to retrieve certificate", Toast.LENGTH_SHORT).show();
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
}
