/*
 * Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.result;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.google.zxing.client.result.AddressBookParsedResult;
import com.google.zxing.client.result.CalendarParsedResult;
import com.google.zxing.client.result.EmailAddressParsedResult;
import com.google.zxing.client.result.GeoParsedResult;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.SMSParsedResult;
import com.google.zxing.client.result.TelParsedResult;
import com.google.zxing.client.result.URIParsedResult;

import edu.cuhk.ie.authbarcodescanner.android.StandardButton;
import edu.cuhk.ie.authbarcodescanner.android.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

/**
 * This object parses the display content and suitable handling buttons of a barcode result based on its type
 * @author solon li
 *
 */
public class TextBarcodeHandler{
	protected final String displayContent;
	protected final List<Button> buttons;
	protected final Activity context;
	
	public TextBarcodeHandler(){
		this.displayContent=null;
		this.buttons=null;
		this.context=null;
	}
	public TextBarcodeHandler(Activity activity, String text){
		this.displayContent=text;
		this.context=activity;
		this.buttons=new ArrayList<Button>();
		insertBaseLineButtons();
	}
	
	
	public TextBarcodeHandler(Activity activity, AddressBookParsedResult result){
		//Create display message
		StringBuilder contents = new StringBuilder(100);
	    ParsedResult.maybeAppend(result.getNames(), contents);	    

	    String pronunciation = result.getPronunciation();
	    if (pronunciation != null && !pronunciation.isEmpty()) {
	      contents.append("\n(");
	      contents.append(pronunciation);
	      contents.append(')');
	    }

	    ParsedResult.maybeAppend(result.getTitle(), contents);
	    ParsedResult.maybeAppend(result.getOrg(), contents);
	    ParsedResult.maybeAppend(result.getAddresses(), contents);
	    String[] numbers = result.getPhoneNumbers();
	    if (numbers != null) {
	      for (String number : numbers) {
	        if (number != null) {
	          ParsedResult.maybeAppend(PhoneNumberUtils.formatNumber(number), contents);
	        }
	      }
	    }
	    ParsedResult.maybeAppend(result.getEmails(), contents);
	    ParsedResult.maybeAppend(result.getURLs(), contents);

	    String birthday = result.getBirthday();
	    if (birthday != null && !birthday.isEmpty()) ParsedResult.maybeAppend(birthday, contents);
	    ParsedResult.maybeAppend(result.getNote(), contents);
	    
	    this.displayContent=contents.toString();
	    this.context=activity;
		this.buttons=new ArrayList<Button>();
		//insert the add contact button
		
		//No field for URL, nicknames, birthday and geo; use notes
	    StringBuilder aggregatedNotes = new StringBuilder();
	    if(result.getNote() !=null && !result.getNote().isEmpty()) 
	    	aggregatedNotes.append('\n').append(result.getNote());
	    if(result.getURLs() != null){
	    	String[] urls=result.getURLs();
	    	for(int i=0;i<urls.length;i++){
	    		if (!urls[i].isEmpty()) aggregatedNotes.append('\n').append(urls[i]);
	    	}
	    }
	    if(result.getNicknames() != null) {
	    	String[] nicknames=result.getNicknames();
	    	for(int i=0;i<nicknames.length;i++){
	    		if (!nicknames[i].isEmpty()) aggregatedNotes.append('\n').append(nicknames[i]);
	    	}
	    }
	    if(result.getGeo() != null){
	    	String[] geo=result.getGeo();
	    	for(int i=0;i<geo.length;i++){
	    		if (!geo[i].isEmpty()) aggregatedNotes.append('\n').append(geo[i]);
	    	}
	    }
	    if(birthday !=null && !birthday.isEmpty()) aggregatedNotes.append('\n').append(birthday);
	    
	    String notes=(aggregatedNotes.length()>1)? aggregatedNotes.substring(1):null;
		
		
		insertAddContactButton(result.getNames(),result.getPronunciation(),result.getPhoneNumbers(),
				result.getEmails(),notes,result.getInstantMessenger(),result.getAddresses(),
				result.getOrg(),result.getTitle());
		
		insertBaseLineButtons();
	}
	
	
	public TextBarcodeHandler(Activity activity, CalendarParsedResult result){
		//Create display message
		StringBuilder contents = new StringBuilder(100);
	    ParsedResult.maybeAppend(result.getSummary(), contents);
	    Date start = result.getStart();
	    ParsedResult.maybeAppend(start.toString(), contents);
	    Date end = result.getEnd();
	    ParsedResult.maybeAppend(end.toString(), contents);
	    ParsedResult.maybeAppend(result.getLocation(), contents);
	    ParsedResult.maybeAppend(result.getOrganizer(), contents);
	    ParsedResult.maybeAppend(result.getAttendees(), contents);
	    ParsedResult.maybeAppend(result.getDescription(), contents);
	    
	    this.displayContent=contents.toString();
	    this.context=activity;
		this.buttons=new ArrayList<Button>();
		//TODO: Add calendar event button
		insertBaseLineButtons();	
	}
	
	
	public TextBarcodeHandler(Activity activity, final EmailAddressParsedResult result){
		this.displayContent=result.getDisplayResult();
		this.context=activity;
		this.buttons=new ArrayList<Button>();
		//insert the send email button
		Button send_email_button=StandardButton.resultButton(this.context, R.string.button_send, R.drawable.result_mail);
		send_email_button.setOnClickListener(new View.OnClickListener() {		
			@Override
			public void onClick(View v) {
				String uri="mailto:" + result.getMailtoURI();
				String email=result.getEmailAddress();
				String subject=result.getSubject();
				String body=result.getBody();
				Intent intent = new Intent(Intent.ACTION_SEND, Uri.parse(uri));
				if (email != null && !email.isEmpty()) 
					intent.putExtra(Intent.EXTRA_EMAIL, new String[] {email});
				if (subject != null && !subject.isEmpty()) 
					intent.putExtra(Intent.EXTRA_SUBJECT, subject);
				if (body != null && !body.isEmpty()) 
					intent.putExtra(Intent.EXTRA_TEXT, body);
				intent.setType("text/plain");
				// Exit the app once the email is sent
				intent.putExtra("compose_mode", true);
				launchIntent(intent);
			}
		});
		this.buttons.add(send_email_button);
		//insert the add contact button
		if(result.getEmailAddress() !=null && !result.getEmailAddress().isEmpty())
			insertAddContactButton(null, null, null, new String[] {result.getEmailAddress()},
					null,null,null,null,null);
		insertBaseLineButtons();
	}
	
	
	public TextBarcodeHandler(Activity activity, final SMSParsedResult result){
		//If it does not have a number to send SMS, just treat the result as normal text
		if(result.getNumbers() ==null){
			this.displayContent=result.getDisplayResult();
			this.context=activity;
			this.buttons=new ArrayList<Button>();
			insertBaseLineButtons();
			return;
		}
		//Create display message
		String[] rawNumbers = result.getNumbers();
	    String[] formattedNumbers = new String[rawNumbers.length];
	    for (int i = 0; i < rawNumbers.length; i++) {
	      formattedNumbers[i] = PhoneNumberUtils.formatNumber(rawNumbers[i]);
	    }
	    StringBuilder contents = new StringBuilder(50);
	    ParsedResult.maybeAppend(formattedNumbers, contents);
	    ParsedResult.maybeAppend(result.getSubject(), contents);
	    ParsedResult.maybeAppend(result.getBody(), contents);
	    
	    this.displayContent=contents.toString();
	    this.context=activity;
		this.buttons=new ArrayList<Button>();
		
		//Create a button to send the SMS
		Button send_sms_button=StandardButton.resultButton(this.context, R.string.button_send, R.drawable.result_sms);
		send_sms_button.setOnClickListener(new View.OnClickListener() {		
			@Override
			public void onClick(View v) {
				String[] rawNumbers = result.getNumbers();
				String receipts="";
				for(int i=0;i<rawNumbers.length;i++){
					if(!rawNumbers[i].isEmpty()) receipts += rawNumbers[i]+";";
				}
				if(!receipts.isEmpty()) receipts=receipts.substring(0, receipts.length() - 1);
				Uri sendSmsTo = Uri.parse("smsto:" + receipts);
				Intent intent = new Intent(android.content.Intent.ACTION_SENDTO, sendSmsTo);
				intent.putExtra("sms_body", result.getSubject()+""+result.getBody());
				// Exit the app once the SMS is sent
			    intent.putExtra("compose_mode", true);
			    launchIntent(intent);
			}
		});
		this.buttons.add(send_sms_button);
		
		insertBaseLineButtons();
	}
	
	
	public TextBarcodeHandler(Activity activity, TelParsedResult result){
		this.displayContent=result.getDisplayResult();
		this.context=activity;
		this.buttons=new ArrayList<Button>();
		//Create dial button
		final String telURI=result.getTelURI();
		if(telURI !=null && !telURI.isEmpty()){
			Button dial_button=StandardButton.resultButton(this.context, R.string.button_dial, R.drawable.result_dial);
			dial_button.setOnClickListener(new View.OnClickListener() {		
				@Override
				public void onClick(View v) {
					launchIntent(new Intent(Intent.ACTION_DIAL, Uri.parse(telURI)));
				}
			});
			this.buttons.add(dial_button);
			//insert the add contact button
			insertAddContactButton(null, null, new String[]{telURI.replace("tel:", "")},
						null, null,null,null,null,null);
		}
		
		insertBaseLineButtons();
	}
	
	
	/**
	 * A constructor for results which only consists of a URI only
	 * @param activity
	 * @param displayContent
	 * @param url
	 */
	public TextBarcodeHandler(Activity activity, String displayContent, final String url){
		this.displayContent=displayContent;
		this.context=activity;
		this.buttons=new ArrayList<Button>();
		//Create visit button
		if(url !=null && !url.isEmpty()){
			Button visit_button=StandardButton.resultButton(this.context, R.string.button_visitPage, R.drawable.result_browser);
			visit_button.setOnClickListener(new View.OnClickListener() {		
				@Override
				public void onClick(View v) {
					String resultURL=url;
					if (resultURL.startsWith("HTTP://")) resultURL = "http" + resultURL.substring(4);
					else if (resultURL.startsWith("HTTPS://")) resultURL = "https" + resultURL.substring(5);
					launchIntent( new Intent( Intent.ACTION_VIEW, Uri.parse(resultURL) ) );
				}
			});
			this.buttons.add(visit_button);
		}
		
		insertBaseLineButtons();
	}
	public TextBarcodeHandler(Activity activity, URIParsedResult result){
		this(activity, result.getDisplayResult(), result.getURI());
	}
	public TextBarcodeHandler(Activity activity, GeoParsedResult result){
		this(activity, result.getDisplayResult(), result.getGeoURI());
	}
	
	
	private void insertBaseLineButtons(){
		//Copy text
		Button copy_text_button=StandardButton.resultButton(this.context, R.string.button_copyText, R.drawable.result_copy);
		copy_text_button.setOnClickListener(new View.OnClickListener() {		
			@Override
			public void onClick(View v) {
				int sdk = android.os.Build.VERSION.SDK_INT;
				if(sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
				    android.text.ClipboardManager clipboard = 
				    		(android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
				    clipboard.setText(displayContent);
				} else {
				    android.content.ClipboardManager clipboard = 
				    		(android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE); 
				    android.content.ClipData clip = android.content.ClipData.newPlainText("barcode data",displayContent);
				    clipboard.setPrimaryClip(clip);
				}
				Toast toast = Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT);
				toast.show();
			}
		});
		this.buttons.add(copy_text_button);
				
		//Share the content
		Button share_button=StandardButton.resultButton(this.context, R.string.button_shareText, R.drawable.result_share);
		share_button.setOnClickListener(new View.OnClickListener() {		
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.putExtra(Intent.EXTRA_TEXT, displayContent);
				intent.setType("text/plain");
			    launchIntent(intent);
			}
		});
		this.buttons.add(share_button);
		
	}
	/**
	 * Add contact
	 * @param names
	 * @param pronunciation
	 * @param phoneNumbers
	 * @param emails
	 * @param note
	 * @param instantMessenger
	 * @param address
	 * @param org
	 * @param title
	 */
	private void insertAddContactButton(final String[] names,
							            final String pronunciation,
							            final String[] phoneNumbers,
							            final String[] emails,
							            final String note,
							            final String instantMessenger,
							            final String[] addresses,
							            final String org,
							            final String title){
		Button add_contact_button=StandardButton.resultButton(this.context, R.string.button_addContact, 
				R.drawable.result_add_contact);
		add_contact_button.setOnClickListener(new View.OnClickListener() {		
			@Override
			public void onClick(View v) {
				// Only use the first name in the array, if present.
			    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT, ContactsContract.Contacts.CONTENT_URI);
			    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);			    
			    if(names !=null && names.length >0 && !names[0].isEmpty())
			    	intent.putExtra(ContactsContract.Intents.Insert.NAME, names[0]);
			    
			    if(pronunciation !=null && !pronunciation.isEmpty())
			    	intent.putExtra(ContactsContract.Intents.Insert.PHONETIC_NAME, pronunciation);
			    
			    if(phoneNumbers !=null && phoneNumbers.length >0 && !phoneNumbers[0].isEmpty()){
			    	intent.putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumbers[0]);			    	
			    	for(int i=1,j=0,l=phoneNumbers.length;i<l && j<2;i++){
			    		if(!phoneNumbers[i].isEmpty()){
			    			if(j==0) 
			    				intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, phoneNumbers[i]);
			    			else if(j==1) 
			    				intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_PHONE, phoneNumbers[i]);
			    			j++;
			    		}			    			
			    	}
			    }
			    
			    if(emails !=null && emails.length >0 && !emails[0].isEmpty()){
			    	intent.putExtra(ContactsContract.Intents.Insert.EMAIL, emails[0]);
			    	for(int i=1,j=0,l=emails.length;i<l && j<2;i++){
			    		if(!emails[i].isEmpty()){
			    			if(j==0) intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL, emails[i]);
			    			else if(j==1) intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_EMAIL, emails[i]);
			    			j++;
			    		}
			    	}
			    }
			    
			    if(instantMessenger !=null && !instantMessenger.isEmpty())
			    	intent.putExtra(ContactsContract.Intents.Insert.IM_HANDLE, instantMessenger);
			    if(addresses !=null && addresses.length >0 && !addresses[0].isEmpty()){
			    	intent.putExtra(ContactsContract.Intents.Insert.POSTAL, addresses[0]);
			    }
			    if(org !=null && !org.isEmpty())
			    	intent.putExtra(ContactsContract.Intents.Insert.COMPANY, org);
			    if(title !=null && !title.isEmpty())
			    	intent.putExtra(ContactsContract.Intents.Insert.JOB_TITLE, title);
			    if(note !=null && !note.isEmpty()) 
			    	intent.putExtra(ContactsContract.Intents.Insert.NOTES, note);
			    
			    launchIntent(intent);
			}
		});
		this.buttons.add(add_contact_button);
	}
	
	private void launchIntent(Intent intent){
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		try{
	    	context.startActivity(Intent.createChooser(intent, "Select App to do it :"));		
	    } catch (ActivityNotFoundException e2) {
	    	AlertDialog.Builder builder = new AlertDialog.Builder(context);
	        builder.setTitle(R.string.app_name);
	        builder.setMessage("No application available for this action");
	        builder.setPositiveButton("OK", null);
	        builder.show();
	    }
	}
	
	/**
	 * Get the text content in UTF-8 encoding, return raw text if it cannot be encoded in UTF-8 
	 * @return
	 */
	public String getTextContent(){
		String contents = this.displayContent.replace("\r","");
		try {
			contents=new String(contents.getBytes("UTF-8"), "UTF-8");
		} catch (java.io.UnsupportedEncodingException e2) { }
		return contents;
	}
	public List<Button> getButtons(){
		if(this.buttons ==null || this.buttons.isEmpty()) return null;
		return this.buttons;
	}
}