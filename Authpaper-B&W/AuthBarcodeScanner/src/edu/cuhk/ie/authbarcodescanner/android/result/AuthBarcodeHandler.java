/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.result;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;
import edu.cuhk.ie.authbarcode.templateHandler.htmlDeparser;
import edu.cuhk.ie.authbarcode.templateHandler.mdDeparser;
import edu.cuhk.ie.authbarcodescanner.android.R;
import edu.cuhk.ie.authbarcodescanner.android.StandardButton;

public class AuthBarcodeHandler{
	private static final String TAG=AuthBarcodeHandler.class.getSimpleName();
	private Activity context;
	private final boolean isHTML;
	private final String HTML;
	private final List<Bitmap> images;
	protected final List<Button> buttons;
	private final String title;
	private final Map<String, String> imgFilename;
	
	public AuthBarcodeHandler(Activity activity, Auth2DbarcodeDecoder resultDecoder, Bitmap barcodeImage, 
			final File tempFolder, Map<String, String> imgFilename){
		this.context = activity;
		htmlDeparser formatDocument=htmlDeparser.tryDeparse(resultDecoder);
		if(formatDocument ==null) formatDocument=mdDeparser.tryDeparse(resultDecoder);
		if(formatDocument !=null){
			//putting the images into the html and load it to web view
			htmlDeparser.setImageNames(imgFilename);
			this.HTML=htmlDeparser.parseHTMLfromBarcode(formatDocument, resultDecoder, barcodeImage, tempFolder);
			this.isHTML=true;
			this.imgFilename = htmlDeparser.getImageNames();
			this.images=null;
			Elements elements=formatDocument.document.body().select("*");
			String titleFrag="";
			for(Element element : elements){
				titleFrag+=element.ownText();
				if(titleFrag.length()>20) break;
			}
			this.title=titleFrag;
		}else{
			//Show the result without formatting
			String textMessage="";
			this.imgFilename = null;
			this.images=new ArrayList<Bitmap>();
			for(String entry : resultDecoder.getKeySet()) {
				Object value = resultDecoder.getDataById(entry);
				String type = resultDecoder.getFormatById(entry);
				if(value==null || type==null || type.isEmpty()) continue;
				if(type.startsWith("text/") && value !=null){
					//Show all the text content
					textMessage += (!textMessage.isEmpty())? "<br>"+value.toString():value.toString();						
				}else if(type.startsWith("image/") && value !=null 
						&& value.getClass().getSimpleName().startsWith("byte")){
					//If it is an image, include it into the imageView. 
					//If there is more than one image, we will pick the one we found last (always override)
					byte[] fileByte = (byte[]) value;
	    			Bitmap fileImage = BitmapFactory.decodeByteArray(fileByte, 0, fileByte.length);
	    			if(fileImage !=null) this.images.add(fileImage);
	    		}
			}
			this.HTML=textMessage;
			this.isHTML=false;
			this.title=this.HTML;
		}
		//reset the buttons
		this.buttons=new ArrayList<Button>();
		if(this.isHTML){
			//TODO: Move the save file button from result fragment to here
			this.buttons.clear();
		}else{
			if(!this.images.isEmpty() && this.images.get(0) !=null){
				Button save_image_button=StandardButton.resultButton(this.context, R.string.button_saveFile, R.drawable.result_save);
				save_image_button.setOnClickListener(new View.OnClickListener() {		
					@Override
					public void onClick(View v) {
						File imageFile=saveBitmap(images.get(0));
						Toast toast = Toast.makeText(context, 
								(imageFile !=null)? "Image is saved in "+imageFile.getAbsolutePath() 
										: "Cannot save the image. Please enable SD card access in Setting -> Apps -> "
										+context.getString(R.string.app_name)+" -> Permission in order to use this feature.", 
								Toast.LENGTH_SHORT);
						toast.show();
					}
				});
				this.buttons.add(save_image_button);
			}
			//Use back the original handler
			TextBarcodeHandler handler= ResultHandler.returnHandler(activity, 
				new com.google.zxing.Result(this.HTML,null,null,null,null));
			List<Button> buttons=handler.getButtons();
			if(buttons !=null) this.buttons.addAll(buttons);
		}
		
	}
	
	public String getHTML(){
		return this.HTML;
	}
	public boolean isHTML(){
		return this.isHTML;
	}
	public String getTitle(){
		return this.title;
	}
	public Bitmap[] getImages(){
		if(this.images ==null || this.images.isEmpty()) return null;
		return this.images.toArray(new Bitmap[this.images.size()]);
	}
	public List<Button> getButtons(){
		if(this.buttons ==null || this.buttons.isEmpty()) return null;
		return this.buttons;
	}
	
	public Map<String, String> getImgNames() {
		return imgFilename;
	}
	
	private static File saveBitmap(Bitmap img){
		try{
			File bsRoot = new File(Environment.getExternalStorageDirectory(), "AuthBarcodeScanner");
		    File barcodesRoot = new File(bsRoot, "Barcodes");
		    if (!barcodesRoot.exists() && !barcodesRoot.mkdirs()) return null;
		    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		    String name = "Barcode_" + timeStamp;
		    File barcodeFile;
		    barcodeFile = new File(barcodesRoot, name + ".png");
		    int counter=0;
	    	while(barcodeFile.exists()){
	    		barcodeFile = new File(barcodesRoot, name + "" + counter + ".png");
	    		counter++;
	    	}
		    barcodeFile.delete();
		    FileOutputStream fos = null;
		    try {
		      fos = new FileOutputStream(barcodeFile);
		      img.compress(Bitmap.CompressFormat.PNG, 0, fos);
		    } catch (FileNotFoundException fnfe) {return null;} 
		    finally {
		      if (fos != null) {
		        try {
		          fos.close();
		          return barcodeFile;
		        } catch (IOException ioe) {
		          // do nothing
		        }
		      }
		    }
		    return barcodeFile;
		}catch(Exception e2){
			return null;
		}
	}
}