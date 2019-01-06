/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.result;

import java.util.List;

import android.app.Activity;
import android.widget.Button;

import com.google.zxing.Result;
import com.google.zxing.client.result.AddressBookParsedResult;
import com.google.zxing.client.result.CalendarParsedResult;
import com.google.zxing.client.result.EmailAddressParsedResult;
import com.google.zxing.client.result.GeoParsedResult;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.SMSParsedResult;
import com.google.zxing.client.result.TelParsedResult;
import com.google.zxing.client.result.URIParsedResult;
import com.google.zxing.client.result.ParsedResultType;
import com.google.zxing.client.result.ResultParser;

/**
 * Parse the result and create a list of buttons to handle the result based on the parsed type
 * @author solon li
 *
 */
public class ResultHandler {
	private final ParsedResultType resultType;
	private final TextBarcodeHandler barcodeHandler;
	
	public ResultHandler(){ 		
		this.resultType=null;
		this.barcodeHandler=null;
	};
	
	public ResultHandler(Activity activity, Result rawResult){
		ParsedResult parsedResult = ResultParser.parseResult(rawResult);
		this.resultType=parsedResult.getType();
		this.barcodeHandler=returnHandler(activity, rawResult);
	}
	public static TextBarcodeHandler returnHandler(Activity activity, Result rawResult){
		ParsedResult parsedResult = ResultParser.parseResult(rawResult);
		ParsedResultType resultType=parsedResult.getType();
		switch (resultType) {
			case ADDRESSBOOK:
				return new TextBarcodeHandler(activity, (AddressBookParsedResult) parsedResult);
			case CALENDAR:
				return new TextBarcodeHandler(activity, (CalendarParsedResult) parsedResult);
			case EMAIL_ADDRESS:
				return new TextBarcodeHandler(activity, (EmailAddressParsedResult) parsedResult);			
			case SMS:
				return new TextBarcodeHandler(activity, (SMSParsedResult) parsedResult);			
			case TEL:
				return new TextBarcodeHandler(activity, (TelParsedResult) parsedResult);			
			case URI:
				return new TextBarcodeHandler(activity, (URIParsedResult) parsedResult);			
			case GEO:
				return new TextBarcodeHandler(activity, (GeoParsedResult) parsedResult);						
			case AUTH2DBARCODE:
				return null;			
			case WIFI:
			case PRODUCT:
			case ISBN:
			case TEXT:	
			case VIN:
			default: 			
				return new TextBarcodeHandler(activity, rawResult.getText());			
	    }
	}
	public ParsedResultType getType(){
		return this.resultType;
	}
	public List<Button> getButtons(){
		return (this.barcodeHandler !=null)? this.barcodeHandler.getButtons() : null;
	}	
	public String getText(){
		return (this.barcodeHandler !=null)? this.barcodeHandler.getTextContent() : null;
	}	
}
