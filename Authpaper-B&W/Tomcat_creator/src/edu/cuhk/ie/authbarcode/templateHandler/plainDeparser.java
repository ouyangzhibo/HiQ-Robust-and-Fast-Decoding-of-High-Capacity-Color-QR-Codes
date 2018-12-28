/**
 * 
 * Copyright (C) 2014 Solon in CUHK
*/

package edu.cuhk.ie.authbarcode.templateHandler;


import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;

/**
 * 
 * Copyright (C) 2014 Solon in CUHK
*/


/**
 * A parser that handle the Auth2DbarcodeDecoder object prepared by plainParser class and output the plain text
 * Actually you can do the time thing by calling getunsignedMessageString() from the object and parse it into a HTML document 
 * @author solon li
 *
 */
public class plainDeparser extends htmlDeparser{
	public final String TAG=mdDeparser.class.getSimpleName();
	private static String ID="plain text";
	
	public plainDeparser(org.jsoup.nodes.Document doc){
		super(doc, ""); //There is no image in the plain text document
	}
	public static plainDeparser tryDeparse(Auth2DbarcodeDecoder barcodeObj){
		org.jsoup.nodes.Document htmlDoc=detectDoc(barcodeObj);
		if(htmlDoc ==null || htmlDoc.html().isEmpty()) return null;
		return new plainDeparser(htmlDoc);
	}
	private static org.jsoup.nodes.Document detectDoc(Auth2DbarcodeDecoder barcodeObj){
		String innerHTML = (String) barcodeObj.getDataById(ID);
		//This object does not contain the file we expect
		if(innerHTML ==null || innerHTML.isEmpty()) return null;
		String html="<pre style='font-size:32px'>"+escapeHTML(innerHTML)+"</pre>";
		return prepareHTMLString(html);
	}
}