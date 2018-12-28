/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.certificate;

import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ExpandableListView;
import android.widget.TextView;
import edu.cuhk.ie.authbarcodescanner.android.R;
import edu.cuhk.ie.authbarcodescanner.android.fragmentCallback;

public class CertificateDetailActivity extends fragmentCallback {
	private static final String TAG = CertificateDetailActivity.class.getSimpleName();
	public static final String CERT_ISSUED_TO = "it";
	public static final String CERT_ISSUED_TO_FULL = "it_f";
	public static final String CERT_ISSUED_BY = "ib";
	public static final String CERT_ISSUED_BY_FULL = "ib_f";
	public static final String CERT_DATE_ISS = "di";
	public static final String CERT_DATE_EXP = "de";
	public static final String CERT_PUB_KEY = "pk";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//Set display
	    setContentView(R.layout.activity_certificate_detail);

		// get details from intent
		Intent i = getIntent();
		if (i == null) {
			this.finish();
		}
		String issued_to = i.getStringExtra(CERT_ISSUED_TO);
		String issued_by = i.getStringExtra(CERT_ISSUED_BY);
		String issued_to_full = i.getStringExtra(CERT_ISSUED_TO_FULL);
		String issued_by_full = i.getStringExtra(CERT_ISSUED_BY_FULL);		
		String date_issued = new SimpleDateFormat("dd-MM-yyyy").format(new Date(i.getLongExtra(CERT_DATE_ISS, 0)));
		String date_expire = new SimpleDateFormat("dd-MM-yyyy").format(new Date(i.getLongExtra(CERT_DATE_EXP, 0)));
		String publicKey = i.getStringExtra(CERT_PUB_KEY);

		// store data in list and hashmap for expandable list
		List<String> listDataHeader = new ArrayList<String>();
		listDataHeader.add(issued_to);
		listDataHeader.add(issued_by);
		
		HashMap<String, String> listDataChild = new HashMap<String, String>();
		listDataChild.put(issued_to, issued_to_full);
		listDataChild.put(issued_by, issued_by_full);
		
	    // get reference to layout objects and set display		
		CertExpListAdapter listAdapter = new CertExpListAdapter(this, listDataHeader, listDataChild);
		ExpandableListView expListView = (ExpandableListView) findViewById(R.id.expLV);		
		expListView.setAdapter(listAdapter);
		
	    ((TextView) findViewById(R.id.cert_date_expire)).setText(date_expire);
	    ((TextView) findViewById(R.id.cert_date_issued)).setText(date_issued);
	    ((TextView) findViewById(R.id.pubkey)).setText(publicKey);
	}
	
	/*
	 * Unimplemented super class functions
	 */	
	@Override
	public KeyStore startKeyStore(){return null;}
	@Override
	protected boolean saveKeyStore(){return false;}
	@Override
	public void onReturnResult(int requestCode, int resultCode, Intent data){ }
	
	
}
