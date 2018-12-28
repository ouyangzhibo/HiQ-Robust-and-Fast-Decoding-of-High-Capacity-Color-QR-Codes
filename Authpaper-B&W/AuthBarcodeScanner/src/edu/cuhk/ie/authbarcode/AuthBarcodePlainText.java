/**
 * 
 * Copyright (C) 2014 Solon in CUHK
 *
 *
 */

package edu.cuhk.ie.authbarcode;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AuthBarcodePlainText extends Auth2DbarcodeDecoder{
	private static final String TAG = AuthBarcodePlainText.class.getSimpleName();
	
//Global variables	
	private static String encodingType="base64";
	private final SecureRandom RND; 
	private static String intraMessageDivider="\n--*#**#*--\n";
	private static String intraMessageDividerEscaped="\\n"+Pattern.quote("--*#**#*--")+"\\n";
	private static String messageDivider="\n--#*##*#--\n";
	private static String messageDividerEscaped="\\n"+Pattern.quote("--#*##*#--")+"\\n";
	private static String magicHeader="auth2dbarcode:";
	private static String[] middleWarningMessage =
		{"\n\n Download the scanner from http://authpaper.net/ to verify this message. \n\n",
		"AuthPaper from MobiTeC, CUHK.\nRead this by scanner from http://authpaper.net/\n",
		"\n Download scanner from http://AuthPaper.org to decode this authenticated QR code.\n © MobiTeC, CUHK\n"};
	
	private byte[] signature=null;
	private Date messageDate=null;
	private String unsignedMessage="";
	//Bad practice: override the variables with other types
	private String signedMessage="";
	//Constructor
	public AuthBarcodePlainText(PrivateKey signer, X509Certificate Cert) {
		super(signer, Cert);
		SecureRandom temp=null;
		try {
			temp = SecureRandom.getInstance("SHA1PRNG", "BC");
		} catch (NoSuchAlgorithmException e2){ } 
		catch (NoSuchProviderException e2) { }
		if(temp==null) temp = new SecureRandom();
		RND=temp;
		byte[] bytes=new byte[10];
		//Forces this SecureRandom object to seed itself
		RND.nextBytes(bytes);
	}
	
//Override functions
		@Override
		public void insertData(String id, String format, Object value, boolean overwrite) throws Exception{
			super.insertData(id, format, value, overwrite);
			reset();
		}
		public void reset(){
			signature=null;
			unsignedMessage="";
			messageDate=null;
			signedMessage="";
		}
		@Override
		public byte[] getSignature() {
			return signature;
		}
		@Override
		public byte[] getCompressedData() {
			return null;
		}
		@Override
		public String getSignedMessageString() {
			return signedMessage;
		}
		@Override
		public byte[] getSignedMessageByte(){
			try {
				return (signedMessage==null || signedMessage.isEmpty())? null:signedMessage.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				return null;
			}
		}
		@Override
		public Date getMessageDate() {
			return messageDate;
		}
		public String getunsignedMessageString(){
			if(!unsignedMessage.isEmpty()) return unsignedMessage;
			else{
				if(this.getDataCount() <1) return "";
				//Rebuild the unsignedMessage
				reset();
				for (String entry : this.getKeySet()) {
					Object value = this.getDataById(entry);
					if(value !=null){
						if(value instanceof String){
							if(!unsignedMessage.isEmpty()) unsignedMessage +=intraMessageDivider;
							unsignedMessage += ( (String) entry ).replace(intraMessageDivider, "#"+intraMessageDivider)
												+intraMessageDivider
												+( (String) value ).replace(intraMessageDivider, "#"+intraMessageDivider);
						}else if(value instanceof byte[]){
							if(!unsignedMessage.isEmpty()) unsignedMessage +=intraMessageDivider;
							unsignedMessage += ( (String) entry ).replace(intraMessageDivider, "#"+intraMessageDivider)
												+intraMessageDivider
												+base64Encode((byte[]) value).replace(intraMessageDivider, "#"+intraMessageDivider);
						}
						
					}
				}
				return unsignedMessage;
			}
		}
		/**
		 * Load the unsignedMsg into the object for later data retrieval 
		 * @param unsignedMsg
		 * @return
		 */
		private boolean parseunsignedMessageString(String unsignedMsg){
			String[] strSegment=unsignedMsg.split("(?<!#)"+intraMessageDividerEscaped);
			if( strSegment==null || strSegment.length<1 || ( strSegment.length !=1 && (strSegment.length & 0x1) >0 ) ) 
				return false;
			if(strSegment.length ==1){
				String value=strSegment[0].replace("#"+intraMessageDivider, intraMessageDivider);
				try {
					this.insertData("dummy", "text/plain", value);
				} catch (Exception e) { }
			} else {
				//check if isBase64 method exists
				/*boolean isBase64MethodExist=false;
				try{
					Class[] testClass=new Class[1];
					testClass[0] = String.class;
					if(org.apache.commons.codec.binary.Base64.class.getDeclaredMethod("isBase64", testClass) !=null)
						isBase64MethodExist=true;
				}catch(NoSuchMethodException e2){ }*/
				for(int i=0;i<(strSegment.length-1);i+=2){
					String index=strSegment[i].replace("#"+intraMessageDivider, intraMessageDivider);
					String value=strSegment[i+1].replace("#"+intraMessageDivider, intraMessageDivider);
					try{
						//TODO: how to support other format (e.g. sound)???
						/*if(isBase64MethodExist && org.apache.commons.codec.binary.Base64.isBase64(value))
							this.insertData(index, "image/png", base64Decode(value));*/
						if(Pattern.matches(
								"^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)$",value))
							this.insertData(index, "image/png", base64Decode(value));
						else if(isJSONString(value))
							this.insertData(index, "application/json", value);
						else this.insertData(index, "text/plain", value);
					}catch(Exception e){ }					
				}
			}
			unsignedMessage=unsignedMsg;
			return true;
		}
	private boolean isJSONString(String string){
		try{
	        new JSONObject(string);
	    }catch(JSONException e2){
	        try{
	        	new JSONArray(string);
	        }catch(JSONException e3){
	            return false;
	        }
	    }return true;
	}
//Encoding side
		/**
		 * Create digital signature of the saved data, the signed object can be found from getSignedMessageString() after calling this function 
		 */
	@Override
	public void compressAndSignData() throws Exception {
		/*
		 * This function works like 
		 * some middle messages
		 * --#*##*#--
		 * message in plain text
		 * --#*##*#--
		 * JSONObject (digital signature block)
		 */
		/*A digital signature block should have: 
		 * "signatureMethod" or sM
		 * "issuer" or i
		"pkcs7-signature" or s
		"date" or d
		"nonce" or n
		optional field:
		"pkcs7-digitalcerts" or c
		"From" or f
		*/
		String unsignedMsg=getunsignedMessageString();
		//Log("The input message size: "+unsignedMsg.getBytes("UTF-8").length);
		messageDate=new Date();
		int nonce=RND.nextInt();
		if(signMethod.compareTo("No algorithm selected")==0) throw new Exception("No digital signature method is selected");
		//Prepare the signature block
		JSONObject signatureObject=new JSONObject()
									.put("d", getSimpleDate(messageDate))
									.put("n", nonce)
									.put("sM", signMethod)
									.put("i", this.getIssuerName());
		String senderName=getSenderName();
		if(senderName !=null && !senderName.isEmpty()) signatureObject.put("f", senderName);
		String messageToSign=middleWarningMessage[2]+messageDivider
							+unsignedMsg.replace(messageDivider, "#"+messageDivider)+messageDivider
							+JSONtoStringSorted(signatureObject);
		//Create the digital signature
		signature=createSignature(messageToSign.getBytes("UTF-8"), this.senderKey, signMethod);
		if(signature ==null ||signature.length<=0) throw new Exception("Cannot create the signature.");
		String base64Signature=base64Encode(signature);
		//Package the message
		signatureObject.put("s", base64Signature);		
		if(this.isIncludeCert) {
			String Certificate=base64Encode(this.senderCert.getEncoded());
			signatureObject.put("c", Certificate);
		}
		signedMessage=middleWarningMessage[2]+messageDivider
					+unsignedMsg.replace(messageDivider, "#"+messageDivider)+messageDivider
					+JSONtoStringSorted(signatureObject);
		//Log("The signature size: "+base64Signature.getBytes("UTF-8").length);
		//Log("The signature block size: "+signatureObject.toString().getBytes("UTF-8").length);
		Log("The whole message size:"+signedMessage.getBytes("UTF-8").length);
	
/*		String testSignBlock=JSONtoStringSorted(signatureObject);
		Log("The uncompressed signblock size :"+testSignBlock.getBytes("UTF-8").length);
		//Custom dictionary, the later the more frequent
		String dictionary="{\"date\":\""
						 +",\"encodingType\":\"base64\",\"issuer\":\""
						 +",\"pkcs7-signature\":\""
						 +",\"signatureMethod\":\"SHA512withECDSA\"}"
						 +"pkcs7-\":\"";
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		deflater.setDictionary(dictionary.getBytes("UTF-8"));
		DeflaterOutputStream dos = new DeflaterOutputStream(baos,deflater);
		dos.write(testSignBlock.getBytes("UTF-8"), 0, testSignBlock.getBytes("UTF-8").length);
		dos.finish();
		byte[] compressedData=baos.toByteArray();
		baos.reset();
		String compressedString="";
		try{
			compressedString=new String(Base64.encode(compressedData), "UTF-8");
		} catch(UnsupportedEncodingException e2){
			compressedString=new String(Base64.encode(compressedData));
		}
		Log("Deflate with dictionary compressed signblock size:"+compressedData.length);
		Log("Deflate+Base64 with dictionary compressed signblock size:"+compressedString.getBytes("UTF-8").length);
		*/
	}
//Decoding side
	public static AuthBarcodePlainText getSupportedDecoder(byte[] encodedArray){
		String str="";
		try {
			str=new String(encodedArray, "UTF-8");
		} catch (UnsupportedEncodingException e2) {
			str=new String(encodedArray);
		}
		if(str !=null && !str.isEmpty()) return getSupportedDecoder(str);
		return null;
	}
	public static AuthBarcodePlainText getSupportedDecoder(String str){
		//We expect the message should be like this:
		/* message in plain text
		 * --#*##*#--
		 * JSONObject (digital signature block)
		 */
		try{
			//A regular expression learnt from the post: http://stackoverflow.com/questions/10781561/regex-to-match-specific-strings-without-a-given-prefix
			String[] strSegment=str.split( "(?<!#)"+messageDividerEscaped );
			//String[] strSegment=str.split( messageDividerEscaped );
			if(strSegment !=null && strSegment.length >1){
				JSONObject signatureBlock=new JSONObject(strSegment[strSegment.length-1]);
				if( (signatureBlock.has("date") || signatureBlock.has("d"))
					&& (signatureBlock.has("pkcs7-signature") || signatureBlock.has("s"))
					&& (signatureBlock.has("nonce") || signatureBlock.has("n"))
					&& (signatureBlock.has("signatureMethod") || signatureBlock.has("sM"))
					&& (signatureBlock.has("issuer") || signatureBlock.has("i")) ){
					//It fits our requirement, then create a dummy object to return it
					AuthBarcodePlainText decoder=new AuthBarcodePlainText(null,null);
					decoder.setUndecodedString(str);
					return decoder;
				}
			} else{
				//Second case is that it contains the message only without any signature
				AuthBarcodePlainText decoder=new AuthBarcodePlainText(null,null);
				String message=(str.startsWith(magicHeader))? str.substring(magicHeader.length()) : str ;
				if(decoder.parseunsignedMessageString(message)){
					decoder.setUndecodedString(str);
					return decoder;
				}
			}
		}catch(PatternSyntaxException e2){}
		catch(JSONException e2){}
		return null;
	}
	@Override
	public boolean decodeBarcodeByte(byte[] encodedArray, KeyStore store)
			throws Exception {
		String str="";
		try {
			str=new String(encodedArray, "UTF-8");
		} catch (UnsupportedEncodingException e2) {
			str=new String(encodedArray);
		}
		if(str !=null && !str.isEmpty()) return decodeBarcodeString(str,store);
		return false;
	}
	/**
	 * Verify the messages created using compressAndSignData() function
	 */
	@Override
	public boolean decodeBarcodeString(String messageString, KeyStore trustStore) throws Exception {
		//If this object contains unsigned data only, we just assume it is an not correctly signed message
		if(!getunsignedMessageString().isEmpty() && (signedMessage ==null || signedMessage.isEmpty()) ){
			this.isVerified=false;
			setIssuerName("Unknown Issuer");
			return true;
		}
		//We expect the message should be like this:
		/* message in plain text
		 * --#*##*#--
		 * JSONObject (digital signature block)
		 * or 
		 * message in plain text
		 * --#*##*#--
		 * middle message
		 * --#*##*#--
		 * JSONObject (digital signature block)
		 * 
		 * The middle message and message in plain text may be exchanged
		 */
		messageDate=null;
		signature=null;
		signedMessage="";
		signMethod="";
		String unsignedMsg="", messageToSign="", issuer="";
		byte[] embededCert=null;
	//Step 1: break down the message into blocks
		//remove the magic header
		if(messageString.startsWith(magicHeader)) messageString=messageString.substring(magicHeader.length());
		try{
			//A regular expression learn from the post: 
			//http://stackoverflow.com/questions/10781561/regex-to-match-specific-strings-without-a-given-prefix
			String[] strSegment=messageString.split( "(?<!#)"+messageDividerEscaped );
			if(strSegment !=null && strSegment.length >1){
				int strSegL=strSegment.length-1;
				unsignedMsg= strSegment[0];
				String middleMessage="";
				boolean isMiddleFront=false;
				//In case the middle message is placed before the content
				String trimMsg=strSegment[0].trim();
				for(int i=0,l=middleWarningMessage.length;i<l;i++){
					if( (middleWarningMessage[i].trim().compareTo(trimMsg) ==0) && strSegL >0){
						middleMessage=middleWarningMessage[i];
						unsignedMsg =strSegment[strSegL-1];
						isMiddleFront=true;
						break;
					}
				}
				JSONObject signatureBlock=new JSONObject(strSegment[strSegL]);				
				//Put the middle messages together
				if(strSegL >1){
					int length=(middleMessage.isEmpty())? strSegL : strSegL-1; 
					for(int i=1;i<length;i++){
						if(!middleMessage.isEmpty()) middleMessage +=messageDivider;
						//Somehow the split will remove the \n on the start and end of the middle message
						trimMsg=strSegment[i].trim();
						int p=0,l=middleWarningMessage.length;
						for(;p<l;p++){
							if(middleWarningMessage[p].trim().compareTo(trimMsg) ==0){								
								middleMessage +=middleWarningMessage[p];
								break;
							}
						}
						if(p>=l) middleMessage +=strSegment[i];
					}
				}
				if( (signatureBlock.has("date") || signatureBlock.has("d"))
					&& (signatureBlock.has("pkcs7-signature") || signatureBlock.has("s"))
					&& (signatureBlock.has("nonce") || signatureBlock.has("n"))
					&& (signatureBlock.has("signatureMethod") || signatureBlock.has("sM"))
					&& (signatureBlock.has("issuer") || signatureBlock.has("i")) ){
					String date = (signatureBlock.has("date"))? signatureBlock.getString("date") : signatureBlock.getString("d");
					messageDate=parseSimpleDate(date,null);		
					//according to "new String(Base64.encode(signature), "UTF-8");" in compressAndSignData()
					signature=base64Decode( (signatureBlock.has("pkcs7-signature"))?
						signatureBlock.getString("pkcs7-signature") : signatureBlock.getString("s"));
					signMethod=(signatureBlock.has("signatureMethod"))? 
						signatureBlock.getString("signatureMethod") : signatureBlock.getString("sM");
					issuer=(signatureBlock.has("issuer"))? signatureBlock.getString("issuer") : signatureBlock.getString("i");
					String senderName=(signatureBlock.has("From"))? signatureBlock.getString("From") 
						: (signatureBlock.has("f"))? signatureBlock.getString("f") : "";
					if(senderName !=null && !senderName.isEmpty()) setSenderName(senderName);
					if(!unsignedMsg.isEmpty() && messageDate!=null && signature!=null) {
						//according to the compressAndSignData()
						signatureBlock.remove("pkcs7-signature");
						signatureBlock.remove("s");
						if(signatureBlock.has("pkcs7-digitalcerts")){
							//new String(Base64.encode(this.senderCert.getEncoded()), "UTF-8");
							embededCert=base64Decode(signatureBlock.getString("pkcs7-digitalcerts"));
							signatureBlock.remove("pkcs7-digitalcerts");
						}
						if(signatureBlock.has("c")){
							embededCert=base64Decode(signatureBlock.getString("c"));
							signatureBlock.remove("c");
						}
						signedMessage=messageString;
						//according to the compressAndSignData()
						messageToSign=(isMiddleFront)?
								middleMessage+messageDivider+unsignedMsg+messageDivider+JSONtoStringSorted(signatureBlock)
								: (middleMessage.isEmpty())? unsignedMsg+messageDivider+JSONtoStringSorted(signatureBlock)
								: unsignedMsg+messageDivider+middleMessage+messageDivider+JSONtoStringSorted(signatureBlock);
					}
				}
			}
		}catch(NullPointerException e2){}
		catch(PatternSyntaxException e2){}
		catch(JSONException e2){}
		if(unsignedMsg.isEmpty() || messageToSign.isEmpty() || messageDate==null 
				|| signature==null || signedMessage.isEmpty() || issuer.isEmpty() || signMethod.isEmpty()) 
			throw new Exception("Unsupported data format.");
	//Step 2: verify the data using the digital signature
		X509Certificate issuerCert=getVerifiedCertificate(embededCert, trustStore, issuer);
		if(issuerCert==null){
			Log("No digital certificate found to verify the 2D barcode content");
			this.isVerified=false;
		} else{
			try{
				byte[] messagetoSignByte=messageToSign.getBytes("UTF-8");
				this.isVerified=verifySignature(messagetoSignByte, signature, signMethod, issuerCert);
				//TODO: For legacy usage
				if(!this.isVerified) {
					messagetoSignByte=(magicHeader+messageToSign).getBytes("UTF-8");
					this.isVerified=verifySignature(messagetoSignByte, signature, signMethod, issuerCert);
				}
			}catch(UnsupportedEncodingException e2){
				this.isVerified=false;
			}
		}
		if(this.isVerified) setIssuerName(getCertificateSubjectName(issuerCert));
		else setIssuerName(issuer);
		setIssuerEmail(issuerCert);
	//Step 3: reconstruct the dataArray back
		unsignedMsg=unsignedMsg.replace("#"+messageDivider, messageDivider);
		return parseunsignedMessageString(unsignedMsg);
	}
//Other functions	
	protected static Date parseSimpleDate(String date, Locale locale){
		try{
			return (locale ==null)? new SimpleDateFormat("MMM dd HH:mm:ss.SS zzzz yyyy").parse(date)
					: new SimpleDateFormat("MMM dd HH:mm:ss.SS zzzz yyyy",locale).parse(date);						
		}catch(ParseException e2){
			boolean isDoneLog=false;
			if(locale ==null){
				try{
					return new SimpleDateFormat("MMM dd HH:mm:ss.SS zzzz yyyy",Locale.ENGLISH).parse(date);
				}catch(ParseException e4){
					Log( e4.getMessage()+"using locale English");
					isDoneLog=true;
				}
			}
			if(!isDoneLog) Log( (locale==null)? e2.getMessage()+"using locale "+"default"
					:e2.getMessage()+"using locale "+locale.getDisplayName(locale));
		}
		//Hard fix for authpaper.biz, change the Time to Standard Time to make it work
		if(date.contains("Time") && !date.contains("Standard Time")) {
			date=date.replace("Time", "Standard Time");
			return parseSimpleDate(date,locale);
		}
		return null;
	}
	protected static String getSimpleDate(Date date){
		//Set the timezone
		//Thu Jan 16 12:26:01.54 GMT+8 2014 AD
		DateFormat formatter = new SimpleDateFormat("MMM dd HH:mm:ss.SS zzzz yyyy",Locale.ENGLISH);    
		formatter.setTimeZone(TimeZone.getDefault());
		return formatter.format(date);
	}
	protected static String JSONtoStringSorted(JSONObject json){
		@SuppressWarnings("unchecked")
		Iterator<String> keys=json.keys();
		List<String> keyList=new ArrayList<String>();
		while(keys.hasNext()) 
			keyList.add(keys.next());
		Collections.sort(keyList);
		
		String resultString="{";
		boolean isFirst=true;
		keys=keyList.iterator();
		while(keys.hasNext()){
			try {
				String index=keys.next();
				String value=valueToString(json.get(index));
				//insert into resultString only if both index and values are ready
				if(!isFirst) resultString +=',';
				//format index:value
				resultString +=JSONObject.quote(index) + ":" + value;
				if(isFirst) isFirst=false;
			} catch (JSONException e) {
				continue;
			}
		}
		resultString+='}';
		return resultString;
	}
	/**
	 * Change an object into a printable and properly escaped JSON Text which can be directly inserted into a JSON String as value
	 * @param value
	 * @return
	 * @throws JSONException
	 */
	public static String valueToString(Object value) throws JSONException {
        if (value == null || value.equals(null)) throw new JSONException("Null value input");
        if (value instanceof Number || value instanceof Boolean ) 
        	return value.toString();
        if (value instanceof JSONObject) 
            return JSONtoStringSorted((JSONObject) value);
        if (value instanceof Map ) 
            return JSONtoStringSorted( new JSONObject((Map) value) );
        if (value instanceof Collection) 
            return new JSONArray( (Collection) value).toString();
        if (value instanceof JSONArray){
        	return ((JSONArray) value).toString();
        }
        if (value.getClass().isArray()) {
        	return new JSONArray(Arrays.asList(value)).toString();
        }
        return JSONObject.quote(value.toString());
    }
	public static String base64Encode(byte[] bytes){
		try{
			try{
				return new String(org.apache.commons.codec.binary
						.Base64.encodeBase64(bytes), "UTF-8");
			} catch(UnsupportedEncodingException e2){			
				//return org.apache.commons.codec.binary.Base64
						//.encodeBase64String(bytes);
			}
			return new String(android.util.Base64.encode(bytes
					,android.util.Base64.NO_WRAP), "UTF-8");
		}catch(Exception e){ }
		return null;
	}
	public static byte[] base64Decode(String str){
		try{
			try{
				return org.apache.commons.codec.binary.Base64
						.decodeBase64(str.getBytes("UTF-8"));
			}catch(UnsupportedEncodingException e2){
				//return org.apache.commons.codec.binary.Base64
						//.decodeBase64(str);
			}
			return android.util.Base64.decode(str.getBytes("UTF-8")
				,android.util.Base64.NO_WRAP);
		}catch(Exception e){ }
		return null;
	}
	static protected void Log(String message){
		//System.out.println(TAG+": "+message);
		//android.util.Log.d(TAG, message);
	}
}