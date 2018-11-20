package edu.cuhk.ie.authbarcode.templateHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A container that stores a document template with related data like input fields, fields type, CSS file, HTML format template, etc.
 * Constructing this object should be done by the Parsers in the same package
 * @author solon li
 *
 */
public class documentTemplateItem {
	//TODO: Distinguish between the entries that should be shown on the template and those not shown
	public enum entryType {
		/**
		 * The type of the specified entry should be text, encoded in UTF-8
		 */
		text,
		/**
		 * The type of the specified entry should be multiline text, encoded in UTF-8
		 */
		textArea,
		/**
		 * The type of the specified entry should be a single JPEG image
		 */
		JPEGImage,
		/**
		 * The type of the specified entry should be a single PNG image
		 */
		PNGImage
	}

	private static final String TAG = documentTemplateItem.class.getSimpleName();
	private final String id;
	private final String source;
	public String sourceCSS="";
	private String document="";
	private Map<String, entryType> documentEntryTypes = new LinkedHashMap<String, entryType>(); 
/*	= new TreeMap<String, entryType>(
			new Comparator<String>() {
			    @Override public int compare(String a, String b) {
			    	return a.compareToIgnoreCase(b);
			    }
			  }
			);
*/	
	public documentTemplateItem(String id, String sourceStr){
		this.id=id;
		this.source=sourceStr;
	}
	
	/**
	 * Create an document template entry by inserting the source file and the list of entries in the template 
	 * @param sourceStr the source of the document (may not be the template)
	 * @param entries the list of entries. Key of each entry is its ID in the template and the value indicates the entry type. 
	 */
/*	public documentTemplateItem(String id, String sourceStr, Map<String, entryType> entries){	
		this.id=id;
		this.source=sourceStr;
		insertTypes(entries);
	}
*/
	/**
	 * Get id (defined in object construction) of this template 
	 * @return
	 */
	public String getID(){
		return id;
	}
	/**
	 * Get the original document source of this template
	 * @return
	 */
	public String getSource(){
		return source;
	}
	/**
	 * Get the document template which is parsed to HTML format
	 * @return
	 */
	public String getTemplate(){
		return document;
	}
	/**
	 * Set the HTML format of this document template
	 * @param documentHTML
	 */
	public void setTemplate(String documentHTML){
		this.document=documentHTML;
	}
	
	public boolean insertType(String id, entryType type){
		try{
			//TODO: check the document template that really have that node in this id and the type is matched
			//But there may be a binary attachment that is not displayed in the document.....
			documentEntryTypes.put(id, type);
			return true;
		} catch(Exception e){
			Log("Cannot insert the entry with key: "+id+" and type: "+type.toString());
			return false;
		}
	}
	
	public boolean insertTypes(Map<String, entryType> entries){
		Map<String, entryType> previousEntries = new LinkedHashMap<String, entryType>();
		previousEntries.putAll(documentEntryTypes);
	//	boolean isInsertionOK=false;
	//	for(Map.Entry<String, entryType> entry: entries.entrySet()){
	//		isInsertionOK=insertType(entry.getKey(),entry.getValue());
	//		if(!isInsertionOK){
	//			Log("Cannot insert the array of entries into the documentEntries");
	//			documentEntryTypes=previousEntries;
	//			return false;
	//		}
	//	}
	//	return true;
	
		//TODO: once the checking feature is done on insertType, modify this code to fit the checking.
		try{
			documentEntryTypes.putAll(entries);
			return true;
		} catch(Exception e){
			Log("Cannot insert the array of entries into the documentEntries");
			documentEntryTypes=previousEntries;
			return false;
		}
	}
	
	public entryType getType(String id){
		return documentEntryTypes.get(id);
	}
	
	public int getEntrySize(){
		return documentEntryTypes.size();
	}
	
	public Set<Map.Entry<String, entryType>> entrySet(){
		return documentEntryTypes.entrySet();
	}

	private void Log(String message){
		System.out.println(TAG+": "+message);
	}
}