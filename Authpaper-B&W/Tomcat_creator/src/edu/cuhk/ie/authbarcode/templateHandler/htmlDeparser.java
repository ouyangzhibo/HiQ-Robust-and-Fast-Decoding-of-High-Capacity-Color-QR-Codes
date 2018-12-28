/**
 * 
 * Copyright (C) 2014 Solon in CUHK
*/

package edu.cuhk.ie.authbarcode.templateHandler;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import javax.imageio.ImageIO;

import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.apache.commons.codec.binary.Base64;

import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;

/**
 * A parser that handle the Auth2DbarcodeDecoder object prepared by htmlParser class and output a complete HTML with proper links / inlines to other contents (images) 
 * @author solon li
 *
 */
public class htmlDeparser {
	public final String TAG=htmlDeparser.class.getSimpleName();
	private static String ID="index.html";
	private static String oldID="index";
	
	public final org.jsoup.nodes.Document document;
	public final String imgTag;
	public htmlDeparser(org.jsoup.nodes.Document doc){ 
		this.document=doc;
		this.imgTag="id";
	}
	public htmlDeparser(org.jsoup.nodes.Document doc, String imageTag){ 
		this.document=doc;
		this.imgTag=imageTag;
	}
//The methods that should be override	
	public static htmlDeparser tryDeparse(Auth2DbarcodeDecoder barcodeObj){
		org.jsoup.nodes.Document htmlDoc=detectDoc(barcodeObj);
		if(htmlDoc ==null || htmlDoc.html().isEmpty()) return null;
		return new htmlDeparser(htmlDoc);
	}
	private static org.jsoup.nodes.Document detectDoc(Auth2DbarcodeDecoder barcodeObj){
		String innerHTML = (String) barcodeObj.getDataById(ID);
		//This object does not contain the file we expect
		if(innerHTML ==null || innerHTML.isEmpty()) {
			innerHTML = (String) barcodeObj.getDataById(oldID);
			if(innerHTML ==null || innerHTML.isEmpty()) return null;
		}
		return prepareHTMLString(innerHTML);
	}

//The following are the static methods for subclasses / public	
	public static String parseHTMLfromBarcode(htmlDeparser depeparser, Auth2DbarcodeDecoder barcodeObj, BufferedImage barcode, File tempImagePath){
		try{
			org.jsoup.nodes.Document htmlDoc=depeparser.document;
			if(htmlDoc ==null || htmlDoc.html().isEmpty()) return null;
			htmlDoc=inlineImages(htmlDoc, depeparser.imgTag, barcodeObj, barcode, tempImagePath);
			//Log(depeparser,htmlDoc.html());
			return htmlDoc.html();
		}catch(Exception e2){ }
		return null;
	}
	public static String parseHTMLfromBarcodeLocally(htmlDeparser depeparser, Auth2DbarcodeDecoder barcodeObj, BufferedImage barcode){
		try{
			org.jsoup.nodes.Document htmlDoc=depeparser.document;
			if(htmlDoc ==null || htmlDoc.html().isEmpty()) return null;
			htmlDoc=inlineImagesInHTML(htmlDoc, depeparser.imgTag, barcodeObj, barcode);
			//Log(depeparser,htmlDoc.html());
			return htmlDoc.html();
		}catch(Exception e2){ }
		return null;
	}
		
	protected static org.jsoup.nodes.Document prepareHTMLString(String rawStr){
		try{
			rawStr=new String(rawStr.getBytes("UTF-8"), "UTF-8");
			org.jsoup.nodes.Document doc=Jsoup.parse(rawStr);
			org.jsoup.nodes.Element headEle=doc.head();
			headEle.appendElement("meta").attr("charset","utf-8");
			return doc;
		}catch(Exception e2){}
		return null;
	}
	
	/**
	 * Given the htmlDoc, embed the images in barcodeObj using the attribute idTag as image ID
	 * @param htmlDoc
	 * @param idTag
	 * @param barcodeObj
	 * @return htmlDoc
	 */
	protected static org.jsoup.nodes.Document inlineImages(org.jsoup.nodes.Document htmlDoc, String idTag, Auth2DbarcodeDecoder barcodeObj, BufferedImage barcode, File tempImagePath){
		if(htmlDoc ==null || idTag==null || idTag.isEmpty() || barcodeObj==null) return htmlDoc;
		//Elements elements=htmlDoc.select("meta[charset]");
		//String encodingMethod = (elements !=null && !elements.isEmpty())? elements.first().attr("charset") : "UTF-8";
		//Insert the image into the index.html
		Elements elements=htmlDoc.select("img");
		for(org.jsoup.nodes.Element ele : elements){
			if(ele.attr("src") !=null && !ele.attr("src").isEmpty()) 
				continue; //There are images embedded already
			String imgID=ele.attr(idTag);
			if(ele.attr("id") ==null || ele.attr("id").isEmpty()) ele.attr("id",imgID);
			if(imgID ==null || imgID.isEmpty()) continue;
			try{
				File tempImage=File.createTempFile(imgID, ".png", tempImagePath);
				tempImage.deleteOnExit();
				FileOutputStream buffer=new FileOutputStream(tempImage, false);

				if(imgID.compareTo("authCode") ==0) ImageIO.write(barcode, "PNG", buffer);
				else{
					byte[] imageByte = (byte[]) barcodeObj.getDataById(imgID);
					//Check that the byte[] really represent an image
					//BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageByte));
					buffer.write(imageByte);
					//Bitmap img = BitmapFactory.decodeByteArray(imageByte, 0, imageByte.length);
					//if(img !=null) img.compress(CompressFormat.PNG, 100, buffer);
				}
				buffer.flush();
				buffer.close();
				//Here we point the src to the temp image
				//ele.attr("src", tempImage.getName());
				ele.attr("src", "file://"+tempImage.getAbsolutePath());
				//Here we inline the image into the webpage
				//String imgToString = new String(Base64.encode(imageByte), encodingMethod);
				//ele.attr("src", "data:image/png;base64," + imgToString);
			}catch(Exception e2){continue;}
		}
		return htmlDoc;
	}
	
	/**
	 * Given the htmlDoc, embed the images in barcodeObj using the attribute idTag as image ID
	 * @param htmlDoc
	 * @param idTag
	 * @param barcodeObj
	 * @return htmlDoc
	 */
	protected static org.jsoup.nodes.Document inlineImagesInHTML(org.jsoup.nodes.Document htmlDoc, String idTag, Auth2DbarcodeDecoder barcodeObj, BufferedImage barcode){
		if(htmlDoc ==null || idTag==null || idTag.isEmpty() || barcodeObj==null) return htmlDoc;
		//Elements elements=htmlDoc.select("meta[charset]");
		//String encodingMethod = (elements !=null && !elements.isEmpty())? elements.first().attr("charset") : "UTF-8";
		//Insert the image into the index.html
		Elements elements=htmlDoc.select("img");
		for(org.jsoup.nodes.Element ele : elements){
			if(ele.attr("src") !=null && !ele.attr("src").isEmpty()) 
				continue; //There are images embedded already
			String imgID=ele.attr(idTag);
			if(ele.attr("id") ==null || ele.attr("id").isEmpty()) ele.attr("id",imgID);
			if(imgID ==null || imgID.isEmpty()) continue;
			try{
				byte[] imageByte = null;
				String imageType = "png";
				if(imgID.compareTo("authCode") ==0){
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					ImageIO.write( barcode, "PNG", baos );
					baos.flush();
					imageByte = baos.toByteArray();					
				}else{
					imageByte = (byte[]) barcodeObj.getDataById(imgID);
					if(barcodeObj.getFormatById(imgID) !=null) 
						imageType=barcodeObj.getFormatById(imgID).replace("image/", "");
				}
				//Here we inline the image into the webpage
				String imgToString = Base64.encodeBase64String(imageByte);
				ele.attr("src", "data:image/"+imageType+";base64," + imgToString);
			}catch(Exception e2){continue;}
		}
		return htmlDoc;
	}
	
	/**
	 * escape the html text according to the OWASP recommendation
	 * https://www.owasp.org/index.php/XSS_%28Cross_Site_Scripting%29_Prevention_Cheat_Sheet
	 * @param input
	 * @return
	 */
	protected static String escapeHTML(String input){
		return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&#x27;").replace("/", "&#x2F;");
	}
	
	protected static void Log(htmlDeparser depeparser, String message){
		//android.util.Log.d(depeparser.TAG,message);
	}
}