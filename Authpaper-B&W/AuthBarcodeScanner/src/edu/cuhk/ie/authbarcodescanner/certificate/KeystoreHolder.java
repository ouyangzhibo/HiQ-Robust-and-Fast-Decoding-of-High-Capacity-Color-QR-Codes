/**
 * 
 * Copyright (C) 2014 Solon in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.certificate;

import java.security.KeyStore;

import edu.cuhk.ie.authbarcodescanner.android.Log;

// convenience method to make keystore available to different activities
public class KeystoreHolder {
	private final String TAG = "KeystoreHolder";
	private static KeystoreHolder instance;
	private KeyStore keystore;
	public boolean keySet = false;
	
	public static void initInstance() {
		if(instance == null) instance = new KeystoreHolder();
	}	
	public static KeystoreHolder getInstance() {
		return instance;
	}
	private KeystoreHolder(){
		keystore = null;
	}
	public void setKeystore(KeyStore keystore) {
		Log.d(TAG, "Setting key store");
		this.keystore = keystore;
		keySet = true;
	}
	public KeyStore getKeystore() {
		Log.d(TAG, "Getting key store");
		return keystore;
	}
}
