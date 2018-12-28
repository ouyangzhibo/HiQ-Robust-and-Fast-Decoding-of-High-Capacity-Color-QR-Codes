/**
 * 
 * Copyright (C) 2014 Solon in CUHK
 *
 *
 */
package edu.cuhk.ie.authbarcode.templateHandler.mdPlugin;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.markdown4j.Markdown4jProcessor;
import org.markdown4j.Plugin;

public class tagList {
	public tagList(){
		
	}
	public String processMD(String input) throws IOException{
		String innerHTML = new Markdown4jProcessor()
				.registerPlugins(new HTMLtag())
				.registerPlugins(new titletag())
				.registerPlugins(new TABLEtag())
				.registerPlugins(new TDtag())
				.process(input);
		return innerHTML.replaceAll("<p>?</p>", "");
	}
	public static void insertHTML(String tag, StringBuilder out, List<String> lines, Map<String, String> params){
		//Check if there is any content inside this tag
	    boolean isBlock = !lines.isEmpty();
	    String resultString = "<"+tag+" ";
    	try{
    		//insert other attributes defined 
	    	for(Map.Entry<String, String> entry: params.entrySet()){
				String key=entry.getKey(), value=entry.getValue();
	    		if(key.compareTo("tag") !=0 && value !=null && !value.isEmpty())
	    			resultString +=""+tagList.escapeHTML(key)+"="+tagList.escapeHTML(value)+" ";
			}
	    	//It is very rare, but it may really throw exception......
    	} catch(Exception e){ }
	    
	    resultString +=">\n";
	    //process your plugin and return your text
	    out.append(resultString);
	    if(isBlock){
	    	Iterator<String> iterator=lines.iterator();
	    	String inlineText="";
			while(iterator.hasNext())
				//Change \%%% to %%%
				inlineText +=iterator.next().replace("\\%%%", "%%%")+"\n";
			try {
				String inlineHTML = new tagList().processMD(inlineText);
				//String inlineHTML = new Markdown4jProcessor().process(inlineText);
				out.append(inlineHTML);
			} catch (IOException e) { }
	    }
	    out.append("</"+tag+">\n");
	  }
	/**
	 * escape the html text according to the OWASP recommendation
	 * https://www.owasp.org/index.php/XSS_%28Cross_Site_Scripting%29_Prevention_Cheat_Sheet
	 * @param input
	 * @return
	 */
	public static String escapeHTML(String input){
		return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&#x27;").replace("/", "&#x2F;");
	}
	
	
/**
 * The simple plugins	
 * @author Solon Li
 *
 */
	private class TDtag extends Plugin {
		  public TDtag() {
		    //bind your plugin with id
		    super("TDtag");
		  }
		  @Override
		  public void emit(StringBuilder out, List<String> lines, Map<String, String> params) {
			  	//read params and manage default value
			    //Check if there is any content inside this tag
			    tagList.insertHTML("td", out, lines, params);
		  }
	}
	private class TABLEtag extends Plugin {
		  public TABLEtag() {
		    super("TABLEtag");
		  }
		  @Override
		  public void emit(StringBuilder out, List<String> lines, Map<String, String> params) {
		    tagList.insertHTML("table", out, lines, params);
		  }
	}
	private class HTMLtag extends Plugin {
		  public HTMLtag() {
		    super("HTMLtag");
		  }
		  @Override
		  public void emit(StringBuilder out, List<String> lines, Map<String, String> params) {
			//Here, we just let it null
		    String tag = tagList.escapeHTML(params.get("tag"));
		    if(tag ==null) return;
		    tagList.insertHTML(tag, out, lines, params);
		  }
	}
}
	


