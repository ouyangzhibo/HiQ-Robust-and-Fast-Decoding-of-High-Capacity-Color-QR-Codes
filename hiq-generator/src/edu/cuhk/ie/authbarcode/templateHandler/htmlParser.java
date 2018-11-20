package edu.cuhk.ie.authbarcode.templateHandler;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javafx.scene.control.TextInputControl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;


/**
 * A parser that parse a HTML template file into a documentTemplateItem (template + input fields), 
 * it also accepts a HTML template with input data (according to the input fields) and put the necessary data into the AuthBarcodeObjBSON
 * This class acts as an interface between other platforms and the document template or web view of the document template  
 * @author solon li
 *
 */

public class htmlParser{
	//private static final String TAG = htmlParser.class.getSimpleName();
	public static documentTemplateItem parseDoc(File file){
		try{
			Document doc = Jsoup.parse(file, "UTF-8");
			String fileName=file.getName();
	    	documentTemplateItem docItem = new documentTemplateItem(fileName,doc.html());
	    	Elements elements=doc.select("strong[id]"); // strong with id
	    	for(Element ele : elements)
	    		docItem.insertType(ele.id(), documentTemplateItem.entryType.text);
	    	elements=doc.select("img[id][src~=(?i)\\.(?:jpe?g|png)$]");
	    	for(Element ele : elements){
	    		if(ele.id().compareTo("authCode") ==0) continue;
	    		String type=ele.attr("src").toLowerCase(Locale.ENGLISH);
	    		if(type.endsWith("png")) docItem.insertType(ele.id(), documentTemplateItem.entryType.PNGImage);
	    		else if(type.endsWith("jpg") || type.endsWith("jpeg")) docItem.insertType(ele.id(), documentTemplateItem.entryType.JPEGImage);
	    	}
	    	docItem.setTemplate(doc.html());
	    	return docItem;
		} catch(IOException e){
			return null;
		}
	}
	public static void parseHTML(documentTemplateItem currentTemplate, List<TextInputControl> inputFields, Auth2DbarcodeDecoder barcodeObj) throws Exception{
		Document document=Jsoup.parse( currentTemplate.getSource() );
		Iterator<TextInputControl> iterator=inputFields.iterator();
		while(iterator.hasNext()){
			TextInputControl inputField=iterator.next();
			if(inputField.getText().isEmpty()) throw new Exception("Input field : "+inputField.getPromptText()+" is empty.");
			//For id with space, we need [ ]
			Element node=document.select("[id="+inputField.getPromptText()+"]").first();
			//if(node ==null) continue;
			if(node ==null) throw new Exception("Input field : "+inputField.getPromptText()+" cannot be found on the template.");
			//Ensure the data in the document fits the input and save the image along with the document
			node.html("");
			String filePath=node.attr("src");
			if(filePath ==null || filePath.isEmpty()) node.appendText(inputField.getText());
			else{
				try{
					String imageSrc=inputField.getText();
					byte[] photoByte=readImageFromPath(imageSrc);
					barcodeObj.insertData(inputField.getPromptText(), "image/jpeg", photoByte);
					node.attr("src", "");
				} catch(Exception e1){ 
					throw new Exception("Image in field "+inputField.getPromptText()+" cannot be found.");
				}
			}
		}
		Element node=document.select("#authCode").first();
		node.attr("src", "");
		String indexHTML=document.html();
		if(indexHTML==null) throw new Exception("No index file is created.");
		barcodeObj.insertData("index.html", "text/plain",indexHTML);
	}
	public static void set2DbarcodeImage(org.w3c.dom.Document doc, File tempFile){
		org.w3c.dom.Element authCodeNode=doc.getElementById("authCode");
		if(authCodeNode !=null) authCodeNode.setAttribute("src", parseImagePath(tempFile));
	}
	public static void set2DbarcodeImage(org.w3c.dom.Document doc, String filePath){
		org.w3c.dom.Element authCodeNode=doc.getElementById("authCode");
		if(authCodeNode !=null) authCodeNode.setAttribute("src", filePath);
	}
	/**
	 * return the image path in a way that webview can display it
	 * @param file
	 * @return
	 */
	public static String parseImagePath(File file) throws SecurityException{
		if(file ==null) return null;
		return file.toURI().toString();
	}
	/**
	 * Reading a small image from the path created by parseImagePath
	 * @param imageSrc
	 * @return
	 */
	public static byte[] readImageFromPath(String imageSrc) throws IOException, OutOfMemoryError, SecurityException {
		return Files.readAllBytes( Paths.get( URI.create(imageSrc) ) );
	}
	/**
	 * escape the html text according to the OWASP recommendation
	 * https://www.owasp.org/index.php/XSS_%28Cross_Site_Scripting%29_Prevention_Cheat_Sheet
	 * @param input
	 * @return
	 */
	@SuppressWarnings("unused")
	private static String escapeHTML(String input){
		return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&#x27;").replace("/", "&#x2F;");
	}
}