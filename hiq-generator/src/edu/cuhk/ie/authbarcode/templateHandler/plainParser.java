package edu.cuhk.ie.authbarcode.templateHandler;

import java.io.File;
import java.util.List;

import javafx.scene.control.TextInputControl;
import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;

/**
 * A parser that create documentTemplateItem with empty template and one text input   
 * @author solon li
 *
 */
public class plainParser{
	public static String ID="plain text";
	public static documentTemplateItem parseDoc(){
		String html="<pre id='input' style='font-size:16px'></pre><img id='authCode' src='authCode.png' style=\"width:40%\"></img>";
		documentTemplateItem docItem = new documentTemplateItem(ID,html);
		docItem.insertType("input", documentTemplateItem.entryType.textArea);
		docItem.setTemplate(html);
		return docItem;
	}
	/**
	 * Create a documentTempalteItem with empty template and one text input
	 * This function is done to override the old one. Nothing will be done on the input, just passing null will be fine.
	 * @param file
	 * @return
	 */
	public static documentTemplateItem parseDoc(File file){
		return parseDoc();
	}
	
	public static void parseHTML(documentTemplateItem currentTemplate, List<TextInputControl> inputFields, Auth2DbarcodeDecoder barcodeObj) throws Exception{
		//There is only one input
		TextInputControl inputField=inputFields.get(0);
		barcodeObj.insertData(ID, "text/plain",inputField.getText());
	}
}