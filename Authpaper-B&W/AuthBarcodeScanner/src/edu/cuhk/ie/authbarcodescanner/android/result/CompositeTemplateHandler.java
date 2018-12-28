/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.result;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;
import edu.cuhk.ie.authbarcode.SecretMessageHandler;
import edu.cuhk.ie.authbarcodescanner.android.R;
import edu.cuhk.ie.authbarcodescanner.android.ResultFragment;

/**
 * A class to handle 2D barcodes storing only the key data of a document and need to parse the formatted document out using predefined templates when displaying the data to users.
 * An object of this class is obtained by calling findSuitableHandler method
 * @author solon li
 *
 */
public class CompositeTemplateHandler{
	private final static String TAG = "CompositeTemplateHandler";
	private final Activity context;	
	private final String HTML,title;
	private String pillAppearance = "", fileLink="";
	
	public String getHTML(){return this.HTML;}
	public String getTitle(){return this.title;}
	public String getPillAppearance() { return this.pillAppearance; }
	public void setPillApperance(String pill){ this.pillAppearance=pill; } 
	public String getFileLink(){ return this.fileLink; }
	public void setFileLink(String link){ this.fileLink=link; }
		
	public static CompositeTemplateHandler findSuitableHandler(ResultFragment fragment, Auth2DbarcodeDecoder resultDecoder){
		//Here we try all predefined composite templates
		if(NamecardHandler.isFitContent(resultDecoder)) return NamecardHandler.getHandler(fragment, resultDecoder);
		if(WinterSchoolHandler.isFitContent(resultDecoder)) return WinterSchoolHandler.getHandler(fragment, resultDecoder);
		if(CSCITSchoolHandler.isFitContent(resultDecoder)) return CSCITSchoolHandler.getHandler(fragment, resultDecoder);		
		return null;
	}
	/**
	 * Construct a composite template object. 
	 * This object reads a document (main document) from the 2D barcode data (resultDecoder) and a document template from the asset folder (indicated by htmlPath).
	 * It then read the document line by line and input them into the document template. As a result, a formatted document will be created (accessible via getHtml()).
	 * 
	 * The indices of the input fields on the template are set in replaceKey. The order of indices indicates the order of the corresponding values on the main document.
	 * For each input field, the number of lines occupied on the main document and the header of the field are indicated by replaceCount and replaceHeader.
	 * 
	 * This object also read some of the data from the main document to form a "title" of this resultant document, according to the titleIndex.
	 * @param fragment the result fragment to display the scanned result
	 * @param resultDecoder 
	 * @param textIndex the index of the main document data stored in the resultDecoder
	 * @param htmlPath the relative path (based on the assets folder) to the document template source 
	 * @param replaceKey This cannot be null. 
	 * @param replaceHeader Only this field can be null. If no null, the order of headers should be same as that of replaceKey.
	 * @param replaceCount Share the same order as the replaceKey
	 * @param titleIndex The indices in the replaceKey which indicate what values on the main document are combined to be the "title" of the resultant document. The order of the indices indicate the order of data on the title. 
	 * @param headerToSkip The lines in the main document which we ignore when parsing the document.
	 */
	private CompositeTemplateHandler(ResultFragment fragment, Auth2DbarcodeDecoder resultDecoder, String textIndex, String htmlPath,
			String[] replaceKey, String[] replaceHeader,int[] replaceCount, int[] titleIndex,String[] headerToSkip){		
		this.context=fragment.getActivity();
		String rawHtmlStr;
		try {
			rawHtmlStr = new String(resultDecoder.getDataById(textIndex).toString().getBytes("UTF8"), "UTF8");
		} catch (UnsupportedEncodingException e1) {
			rawHtmlStr = "LLL";
		}
		String[] holderName=new String[titleIndex.length];
		try{
			//Create the content first
			BufferedReader in = new BufferedReader(
				new InputStreamReader(context.getAssets().open(htmlPath), "UTF-8") );
			StringBuilder buf=new StringBuilder();
			for(String tmp;(tmp=in.readLine()) != null;){						
				buf.append(tmp);
		    }
			in.close();			
			String realHtml=buf.toString();
			
			//Two cases : whether the number of lines are fixed by replaceCount or replace header is used
			String lines[] = rawHtmlStr.split("\\r?\\n");
			//Read the background color
			String backgroundColor="";
			if(!lines[0].isEmpty() && lines[0].startsWith("Back:")){
				backgroundColor=lines[0].replace("Back:", "");
			}
			String temStr="";
			int[] replaceLineCount =(replaceCount !=null)? replaceCount.clone() : null;
			for(int i=(backgroundColor.isEmpty())? 0:1,j=0,l=lines.length,m=replaceKey.length;i<l && j<m;i++){
				String line=lines[i],nextline=(i<l-1)? lines[i+1]:"";
				if( (!nextline.isEmpty() && line.isEmpty()) || containHeader(headerToSkip, line)) continue;
				if(!line.isEmpty()){
					if(replaceHeader !=null && line.startsWith(replaceHeader[j])) {
						if (replaceHeader[j].equals("a:")) {
							pillAppearance = line.replace(replaceHeader[j], "");
						} else {
							line=line.replace(replaceHeader[j], "");	
						}
						
					}
					temStr=(temStr.isEmpty())? line : temStr+"<br>"+line;
				}else{
					i++; //Both this line and next line are empty.
					continue;
				}
				boolean isFinalLine = (replaceLineCount !=null)? (replaceLineCount[j] <2) :
					( (!nextline.isEmpty() && j<m-1 && nextline.startsWith(replaceHeader[j+1]))
					|| (nextline.isEmpty() && !line.isEmpty()) );
				if(isFinalLine){
					//When the nextline belongs to the next field or this is already the last line
					//Check whether it should be part of the title and save it to holderName
					int holdI=-1;
					for(int a=0,b=titleIndex.length;a<b && holdI<0;a++){
						if(titleIndex[a]==j) holdI=a;
					}
					if(holdI>-1) holderName[holdI]=temStr;
					realHtml=realHtml.replace(replaceKey[j++], temStr);
					temStr="";
				}else if(replaceLineCount !=null) replaceLineCount[j]--;
			}
			rawHtmlStr=realHtml;
			if(!backgroundColor.isEmpty()){
				String[] indices={"body", "html"};
				for(int i=0,l=indices.length;i<l;i++){
					String pattern="("+indices[i]+"\\{)(.*[^\\}])(\\})";
					if(rawHtmlStr.matches(".*"+pattern+".*")) 
						rawHtmlStr=rawHtmlStr.replaceFirst(pattern, "$1background-color:"+backgroundColor+";$2$3");
				}
			}
		}catch(UnsupportedEncodingException e){						
		}catch(IOException e){						
		}
		this.HTML=rawHtmlStr;
		String title="";
		for(int i=0,l=holderName.length;i<l;i++){
			if(holderName[i] !=null && !holderName[i].isEmpty()) 
				title=(title.isEmpty())? holderName[i]:title+","+holderName[i];
		}
		this.title=(title.isEmpty())? this.HTML.substring(0, 20) : title;
	}
	private static boolean containHeader(String[] header, String string){
		for(int i=0,l=header.length;i<l;i++){
			if(string.startsWith(header[i])) return true;
		}
		return false;
	}

	private static class NamecardHandler{
		private static String textIndex="Namecard.md";
		private static String htmlPath="givenTemplate/namecard.html";
		private static String[] replaceKey={"$Organization#","$Name#","$EnglishName#","$Department#","$Address#","$Contact#"};
		private static int[] replaceLineCount={2,1,1,2,3,2};
		private static int[] titleIndex={1,0};
		private static String[] headerToSkip={"Namecard"};
		
		public static CompositeTemplateHandler getHandler(ResultFragment fragment,
				Auth2DbarcodeDecoder resultDecoder){
			return new CompositeTemplateHandler(fragment, resultDecoder, textIndex, htmlPath,replaceKey,null,
					replaceLineCount,titleIndex,headerToSkip);
		}
		public static boolean isFitContent(Auth2DbarcodeDecoder resultDecoder){
			if(resultDecoder.getDataById("text") !=null 
				&& resultDecoder.getDataById("text").toString().contains(textIndex)){
				try {
					resultDecoder.insertData(textIndex, "text/", 
						resultDecoder.getDataById("text").toString().replace(textIndex, ""));
				}catch(Exception e){}
			}
			return resultDecoder !=null //&& resultDecoder.isBarcodeVerified()
					//&& resultDecoder.getIssuerDisplayName().contains("cmli@ie.cuhk.edu.hk")
					&& resultDecoder.getDataById(textIndex) !=null;
		}
	}
	private static class WinterSchoolHandler{
		private static String textIndex="text";
		private static String htmlPath="givenTemplate/winterSchool.html";
		private static String[] replaceKey={"$Name#","$School#","$Team#"};
		private static int[] replaceLineCount={1,1,1};
		private static int[] titleIndex={0};
		private static String[] headerToSkip={"ITCSC-INC","http://www.itcsc.cuhk.edu.hk","The Chinese University of Hong Kong"};
		
		public static CompositeTemplateHandler getHandler(ResultFragment fragment,
				Auth2DbarcodeDecoder resultDecoder){
			return new CompositeTemplateHandler(fragment, resultDecoder, textIndex, htmlPath,replaceKey,null,
					replaceLineCount,titleIndex,headerToSkip);
		}
		public static boolean isFitContent(Auth2DbarcodeDecoder resultDecoder){
			return resultDecoder !=null && resultDecoder.isBarcodeVerified()
					&& resultDecoder.getIssuerDisplayName().contains("cmli@ie.cuhk.edu.hk")
					&& resultDecoder.getDataById(textIndex) !=null
					&& resultDecoder.getDataById(textIndex).toString().contains("ITCSC-INC Winter School 2015");
		}
	}
	public static class CSCITSchoolHandler{
		private static String textIndex="text";
		private static String htmlPath="givenTemplate/cscit.html";
		private static String[] replaceKey={"$Name#","$School#","$PosterTitle#","$PosterLink#"};		
		private static int[] replaceLineCount={1,1,1,1};
		private static int[] titleIndex={0,1};
		private static String[] headerToSkip={"Croucher Summer Course in Information Theory (CSCIT) 2015",
			"http://www.ie.cuhk.edu.hk/Croucher-summer-course-in-IT-2015"};
		
		public static CompositeTemplateHandler getHandler(ResultFragment fragment,
				Auth2DbarcodeDecoder resultDecoder){
			CompositeTemplateHandler newHandler= new CompositeTemplateHandler(fragment, resultDecoder, 
					textIndex, htmlPath,replaceKey,null,replaceLineCount,titleIndex,headerToSkip);
			//Hardcode, use the poster title as the subtitle and find the poster link as file link
			String fullTitle = newHandler.getTitle();
			String[] titles = fullTitle.split(",");
			if(titles.length <2) return newHandler;
			newHandler.setPillApperance(titles[titles.length-1]);
			String rawHtmlStr="";
			try{
				rawHtmlStr = new String(resultDecoder.getDataById(textIndex).toString().getBytes("UTF8"), "UTF8");
			} catch (UnsupportedEncodingException e1) {
				rawHtmlStr = "LLL";
			}
			String lines[] = rawHtmlStr.split("\\r?\\n");
			if(lines.length <1) return newHandler;
			newHandler.setFileLink(lines[lines.length-1]);
			return newHandler;

		}
		public static boolean isFitContent(Auth2DbarcodeDecoder resultDecoder){
			return resultDecoder !=null //&& resultDecoder.isBarcodeVerified()
					&& resultDecoder.getDataById(textIndex) !=null
					&& resultDecoder.getDataById("secretMsg") !=null
					&& resultDecoder.getDataById(textIndex).toString().contains("Croucher Summer Course in Information Theory (CSCIT) 2015");
		}
		
		public static List<Button> getButtons(final Context context, Auth2DbarcodeDecoder resultDecoder, final String fileLink, 
				String title){
			List<Button> buttons = new ArrayList<Button>();
			buttons.clear();
			
			//Create a download paper button
			//fileLink
			Button save_file_button=newButton(context, "Save Poster", R.drawable.result_save);				
			save_file_button.setOnClickListener(new View.OnClickListener() {		
				@Override
				public void onClick(View v) {
					String url =(!fileLink.startsWith("http://"))? "http://"+fileLink : fileLink;
					Intent intent=new Intent( Intent.ACTION_VIEW, Uri.parse(url));
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
					try{
						context.startActivity(Intent.createChooser(intent, "Select App to view the poster :"));		
				    }catch(ActivityNotFoundException e2){
				    	new AlertDialog.Builder(context).setTitle(R.string.app_name)
				        .setMessage("No application available for this action")
				        .setPositiveButton("OK", null).show();
				    }
				}
			});
			buttons.add(save_file_button);
			
			//Read the secret message (email)
			final String[] titles = title.split(",");
			if(titles.length <2) return buttons;
			String surname=titles[0];
			if(resultDecoder ==null) return buttons;
			Object secretObj=resultDecoder.getDataById("secretMsg");
			final SecretMessageHandler secretMsg=new SecretMessageHandler(secretObj.toString());
			final String secretStr=secretMsg.decryptMsg(surname);
			if(secretStr !=null && !secretStr.isEmpty()){
				Button contact_button=newButton(context, "Holder's email", R.drawable.result_add_contact);
				contact_button.setOnClickListener(new View.OnClickListener(){
					@Override
					public void onClick(View v){
						android.app.AlertDialog.Builder alertBuilder=new AlertDialog.Builder(context)
			            .setTitle("Holder's email encrypted in the 2D barcode")
			            .setMessage(titles[0] + "\n" + secretStr)
			        	.setPositiveButton("OK", null)
			    		.setNeutralButton("Save to Contact List", new DialogInterface.OnClickListener() {
			    			public void onClick(DialogInterface dialog, int whichButton){
			    				dialog.dismiss();
			    				// Only use the first name in the array, if present.
			    			    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT, ContactsContract.Contacts.CONTENT_URI);
			    			    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);	    			    
			    			    intent.putExtra(ContactsContract.Intents.Insert.NAME, titles[0]+" "+titles[1]);
			    			    Pattern p = Pattern.compile("[\\w=+\\-\\/][\\w='+\\-\\/\\.]*@[\\w\\-]+(\\.[\\w\\-]+)*(\\.[\\w]{2,6})");	
			    				Matcher m =p.matcher(secretStr);
			    				if(m.find()){		    		
			    					intent.putExtra(ContactsContract.Intents.Insert.EMAIL, m.group());
			    					String otherNotes=secretStr.replace(m.group(), "").trim();
			    					if(!otherNotes.isEmpty()) intent.putExtra(ContactsContract.Intents.Insert.NOTES, otherNotes);
			    				}else intent.putExtra(ContactsContract.Intents.Insert.NOTES, secretStr);
			    				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			    				try{
			    			    	context.startActivity(Intent.createChooser(intent, "Select App to do it :"));		
			    			    } catch (ActivityNotFoundException e2) {
			    			    	new AlertDialog.Builder(context).setTitle(R.string.app_name)
			    			    	.setMessage("No application available for this action").setPositiveButton("OK", null).show();
			    			    }
			    			}
			    		});
						alertBuilder.show();
					}
				});
				buttons.add(contact_button);
			}
			return buttons;
		}
		private static Button newButton(Context context, String textTitle, int drawableID){
			Button button = new Button(context);
			if(textTitle !=null && !textTitle.isEmpty()){
				button.setText(textTitle);
				button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
			}
			button.setBackgroundResource(0);
			//button.setMaxHeight(160);
			//button.setMaxWidth(160);
			if(drawableID !=0) button.setCompoundDrawablesWithIntrinsicBounds(0, drawableID, 0, 0);
			return button;
		}
	}
}