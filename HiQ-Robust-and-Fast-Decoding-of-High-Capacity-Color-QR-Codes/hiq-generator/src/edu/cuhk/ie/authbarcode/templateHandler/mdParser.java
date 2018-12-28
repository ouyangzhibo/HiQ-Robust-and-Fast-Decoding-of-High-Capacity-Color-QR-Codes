package edu.cuhk.ie.authbarcode.templateHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javafx.scene.control.TextInputControl;
import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;
import edu.cuhk.ie.authbarcode.templateHandler.mdPlugin.tagList;


/**
 * A parser that parse a md template file into a documentTemplateItem (template + input fields), 
 * it also accepts a md template with input data (according to the input fields) and put the necessary data into the AuthBarcodeObjBSON
 * @author solon li
 *
 */

public class mdParser extends htmlParser{
	//private static final String TAG = mdParser.class.getSimpleName();
	public static documentTemplateItem parseDoc(File file){
		try{
			byte[] textByte=Files.readAllBytes( file.toPath() );
	    	//String html = new Markdown4jProcessor().process(new String(textByte,"UTF-8"));
	    	//Using markdown4j extension feature: registering custom tag
	    	String sourceMD=new String(textByte,"UTF-8");
	    	String html = new tagList().processMD(sourceMD);	    	
	    	Document doc = Jsoup.parse(html);
	    	Element head=doc.head();
	    	head.appendElement("meta").attr("charset","utf-8");
	    	String fileName=file.getName();
	    	//Using the sourceMD as the source
	    	documentTemplateItem docItem = new documentTemplateItem(fileName,sourceMD);
	    	try{
	    		String CSSfile=file.getPath()+".css";
	    		String CSS=new String(Files.readAllBytes(Paths.get(CSSfile)),"UTF-8");
	    		head.appendElement("style").attr("media", "screen").attr("type","text/css").text(CSS);
	    		docItem.sourceCSS=CSS;
	    		//If we cannot find the CSS file, then just leave it
	    	} catch(IOException e2){ }
	    	
	    	Elements elements=doc.select("pre, strong, img[src~=(?i)\\.(?:jpe?g|png)$]");
	    	for(Element ele : elements){
	    		if(ele.tagName().compareTo("img")==0){ //image tags
	    			String title=ele.attr("alt");
		    		ele.attr("id", title);
		    		if(ele.id().compareTo("authCode") ==0) continue;
		    		String type=ele.attr("src").toLowerCase(Locale.ENGLISH);
		    		if(type.endsWith("png")) docItem.insertType(title, documentTemplateItem.entryType.PNGImage);
		    		else if(type.endsWith("jpg") || type.endsWith("jpeg")) docItem.insertType(title, documentTemplateItem.entryType.JPEGImage);
	    		} else if(ele.tagName().compareTo("pre")==0){ //pre tags (Multi-line text inputs)
	    			docItem.insertType(ele.text(), documentTemplateItem.entryType.textArea);
	    			ele.attr("id", ele.text());
	    		} else{ //strong tags (One line text input)
	    			docItem.insertType(ele.text(), documentTemplateItem.entryType.text);
		    		ele.attr("id", ele.text());
	    		}
	    	}
	    	docItem.setTemplate(doc.html());
	    	return docItem;
		} catch(IOException e){
			return null;
		}
	}
	
	public static void parseHTML(documentTemplateItem currentTemplate, List<TextInputControl> inputFields, Auth2DbarcodeDecoder barcodeObj) throws Exception{
		String sourceMD=currentTemplate.getSource();
		Iterator<TextInputControl> iterator=inputFields.iterator();
		while(iterator.hasNext()){
			TextInputControl inputField=iterator.next();
			if(inputField.getText().isEmpty()) throw new Exception("Input field : "+inputField.getPromptText()+" is empty.");
			if( !sourceMD.contains("**"+inputField.getPromptText()+"**") 
					&& !sourceMD.matches("(?s).*<strong[^>]*>"+inputField.getPromptText()+"</strong>.*")
				&& !sourceMD.contains("!["+inputField.getPromptText()+"]") 
				//&& !sourceMD.contains("<pre>"+inputField.getPromptText()+"</pre>") ) 
				&& !sourceMD.matches("(?s).*<pre[^>]*>"+inputField.getPromptText()+"</pre>.*" ) )
			throw new Exception("Input field : "+inputField.getPromptText()+" cannot be found on the template.");
			
			sourceMD=sourceMD.replace("**"+inputField.getPromptText()+"**", "**"+escapeMD( inputField.getText() )+"**");
			sourceMD=sourceMD.replaceFirst("<strong([^>]*)>"+inputField.getPromptText()+"</strong>", 
					"<strong$1>"+escapeMD( inputField.getText() )+"</strong>");
			sourceMD=sourceMD.replaceFirst("<pre([^>]*)>"+inputField.getPromptText()+"</pre>", 
					"<pre$1>"+escapeMD( inputField.getText() )+"</pre>");
			//sourceMD=sourceMD.replace("<pre>"+inputField.getPromptText()+"</pre>", "<pre>"+escapeMD( inputField.getText() )+"</pre>");
			//If it is an image field
			if(sourceMD.contains("!["+inputField.getPromptText()+"]")){
				try{
					String imageSrc=inputField.getText();
					byte[] photoByte=readImageFromPath(imageSrc);
					barcodeObj.insertData(inputField.getPromptText(), "image/jpeg", photoByte);
				} catch(Exception e1){ 
					throw new Exception("Image in field "+inputField.getPromptText()+" cannot be found.");
				}
			}
		}
		if(sourceMD==null) throw new Exception("No index file is created.");
		barcodeObj.insertData("index.md", "text/plain",sourceMD);
		if(!currentTemplate.sourceCSS.isEmpty()) barcodeObj.insertData("index.css", "text/plain",currentTemplate.sourceCSS);
	}
	
	private static String escapeMD(String input){
		return input.replace("\\", "\\\\").replace("'", "\\'").replace("*", "\\*").replace("_", "\\_")
				.replace("{", "\\{").replace("}", "\\}").replace("[", "\\[").replace("]", "\\]").replace("(", "\\(")
				.replace(")", "\\)").replace("#", "\\#").replace("+", "\\+").replace("-", "\\-").replace(".", "\\.")
				.replace("!", "\\!");
	}
}