/**
 * 
 * Copyright (C) 2014 Solon in CUHK
*/

package edu.cuhk.ie.authbarcode.templateHandler;

import java.io.IOException;
import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;
import edu.cuhk.ie.authbarcode.templateHandler.mdPlugin.tagList;

/**
 * A parser that handle the Auth2DbarcodeDecoder object prepared by mdParser class and output a complete HTML with proper links / inlines to other contents (images) 
 * @author solon li
 *
 */
public class mdDeparser extends htmlDeparser {
	public final String TAG=mdDeparser.class.getSimpleName();
	private static String ID="index.md";
	private static String CSS="index.css";
	
	public mdDeparser(org.jsoup.nodes.Document doc){ 
		super(doc, "alt");
	}
	public static mdDeparser tryDeparse(Auth2DbarcodeDecoder barcodeObj){
		org.jsoup.nodes.Document htmlDoc=detectDoc(barcodeObj);
		if(htmlDoc ==null || htmlDoc.html().isEmpty()) return null;
		return new mdDeparser(htmlDoc);
	}
	private static org.jsoup.nodes.Document detectDoc(Auth2DbarcodeDecoder barcodeObj){
		//barcodeObj.insertData("index.md", "text/plain",sourceMD);
		String sourceMD = (String) barcodeObj.getDataById(ID);
		//This object does not contain the file we expect
		if(sourceMD ==null || sourceMD.isEmpty()) return null;
		try {
			String innerHTML = new tagList().processMD(sourceMD);
			org.jsoup.nodes.Document htmlDoc=prepareHTMLString(innerHTML);
			String sourceCSS = (String) barcodeObj.getDataById(CSS);
			if(sourceCSS !=null && htmlDoc !=null){
				org.jsoup.nodes.Element headEle=htmlDoc.head();
				//Append CSS
				headEle.appendElement("style").attr("media", "screen").attr("type","text/css").text(sourceCSS);
			}
			htmlDoc.body().append("<p></p>");
			return htmlDoc;
		} catch (IOException e) { }
		return null;
	}
}