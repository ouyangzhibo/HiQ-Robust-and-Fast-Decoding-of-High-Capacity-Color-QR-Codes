/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.encode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import edu.cuhk.ie.authbarcodescanner.android.Log;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;


/**
 * This class transform a contact list content into a string
 */
public class MEcardEncoder{
	private static final String TAG = MEcardEncoder.class.getSimpleName();
	
	private static final char TERMINATOR = ';';
	
	@SuppressLint("InlinedApi")
	public static String showContactAsBarcode(Context context, Uri contactUri) {
		//TODO: How to get contact except name, phone, address and emails?
	    if(contactUri ==null) return null;     
	    ContentResolver resolver=context.getContentResolver();
	    Cursor cursor=null;
	    try { 
	      cursor = resolver.query(contactUri, null, null, null, null);
	    } catch (IllegalArgumentException e) { }
	    if(cursor ==null || !cursor.moveToFirst()) return null;

	    //First, name
	    String name="";
	    try{
	    	name=cursor.getString(cursor.getColumnIndex(
	    			Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
	    					ContactsContract.Contacts.DISPLAY_NAME_PRIMARY :
	    					ContactsContract.Contacts.DISPLAY_NAME
		    		));
		    name=trim(name);
	    } catch(Exception e2){ }
	    String id="";
	    try{
	    	 id=cursor.getString(cursor.getColumnIndex(
		    		ContactsContract.Contacts._ID));
	    } catch(Exception e2){ }
	    cursor.close();
	    
	    List<String> phones=new ArrayList<String>();
	    String address=null;
	    List<String> emails=new ArrayList<String>();
	    if(id !=null && !id.isEmpty()){
	    	//Then the phone number
	    	Cursor pCursor = resolver.query(
					    			ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
				                    null,
				                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + '=' + id,
				                    null,
				                    null);
	    	if(pCursor !=null && pCursor.moveToFirst()){
	    		try {
	    			int pNumCol = pCursor.getColumnIndex(
	    							ContactsContract.CommonDataKinds.Phone.NUMBER);
					for(int i=0;(i==0 || pCursor.moveToNext()) && i<5;i++){
						//Save at most three phones
						String number=pCursor.getString(pNumCol);
						if(number !=null && !number.isEmpty()) phones.add(trim(number));
					}  	          
	  	        } catch(Exception e2){ } 
	    		pCursor.close();
	    	}
	    	//Then address
	    	Cursor mCursor = resolver.query(
	    							ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
					                null,
					                ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID + '=' + id,
					                null,
					                null);
	    	if(mCursor !=null && mCursor.moveToFirst()){
	    		try {
					for(int i=0;(i==0 || mCursor.moveToNext()) && i<5;i++){
						address = mCursor.getString(mCursor.getColumnIndex(
										ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS));
						Log.d(TAG,address);
						if(address != null && !address.isEmpty()) break;
					}
	  	        } catch(Exception e2){ } 
	    		mCursor.close();
	    	}
	    	//Then emails
	    	 Cursor eCursor = resolver.query(
	    			 				 ContactsContract.CommonDataKinds.Email.CONTENT_URI,
				                     null,
				                     ContactsContract.CommonDataKinds.Email.CONTACT_ID + '=' + id,
				                     null,
				                     null);
	    	 if(eCursor !=null && eCursor.moveToFirst()){
	    		 try {
					for(int i=0;(i==0 || eCursor.moveToNext()) && i<5;i++){
						String email = eCursor.getString(eCursor.getColumnIndex(
										ContactsContract.CommonDataKinds.Email.DATA));
						Log.d(TAG,email);
						if(email != null && !email.isEmpty()) emails.add(trim(email));
					}
	    		 } catch(Exception e2){ } 
	    		 eCursor.close();
	    	 }
	    }
	    //If there is no name, we just return the telephone
	    if(name ==null && phones.size() >0) return "tel:"+phones.get(0);
	    //TODO: make it support VCard also
	    return encodeToString(name, address, phones, emails);
	}
	public static String trim(String data) {
		if(data ==null || data.isEmpty()) return null;
		data=data.replace("\n", " ");
		data=data.replace("\r", " ");
		return data;
	}
	
	private static String encodeToString(String name, String address, List<String> phones, 
			List<String> emails){
		StringBuilder content = new StringBuilder(100);
		content.append("MECARD:");
	    if(name !=null && !name.trim().isEmpty())
	    	content.append(MECARDFieldFormatter.format("N",name));
	    if(address !=null && !address.trim().isEmpty())
	    	content.append(MECARDFieldFormatter.format("ADR",address));
	    listAppend(content, "TEL", phones);
	    listAppend(content, "EMAIL", emails);
	    return content.append(TERMINATOR).toString();
	}
	private static void listAppend(StringBuilder content, String prefix, List<String> values){
		if(values !=null && values.size() >0){
	    	int count=values.size();
	    	List<String> previous = new ArrayList<String>(count);
	    	for(int i=0;i<count;i++){
	    		String phone=values.get(i);
	    		if(phone !=null && !phone.trim().isEmpty() && !previous.contains(phone)){
	    			previous.add(phone);
	    			content.append(MECARDFieldFormatter.format(prefix,phone));
	    		}
	    	}
	    }
	}
	
	private static class MECARDFieldFormatter{
		private static final Pattern RESERVED_MECARD_CHARS = Pattern.compile("([\\\\:;])");
		private static final Pattern NEWLINE = Pattern.compile("\\n");
		public static String format(String prefix, String value){
			return prefix + properReplace(value) + TERMINATOR;
		}
		private static CharSequence properReplace(String value) {
			return ':' + NEWLINE.matcher(RESERVED_MECARD_CHARS.matcher(value.trim())
					 .replaceAll("\\\\$1")).replaceAll("");
		}
	}
	
}