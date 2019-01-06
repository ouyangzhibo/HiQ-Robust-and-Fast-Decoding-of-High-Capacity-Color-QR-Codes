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

public class titletag extends Plugin {
	  public titletag() {
	    super("titletag");
	  }
	  @Override
	  public void emit(StringBuilder out, List<String> lines, Map<String, String> params) {
	    out.append("<h3 style=\"text-align:center\">");
	    //process your plugin and return your text
    	Iterator<String> iterator=lines.iterator();
    	String inlineText="";
    	while(iterator.hasNext()) inlineText +=iterator.next()+"\n";
		try {
			//There should be no special tag inside this title tag
			String inlineHTML = new Markdown4jProcessor().process(inlineText);
			inlineHTML=inlineHTML.replaceAll("<p>?</p>", "");
			out.append(inlineHTML);
		} catch (IOException e) { }
	    out.append("</h3>\n");
	  }
}