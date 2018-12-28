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
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AuthBarcodePlainText extends Auth2DbarcodeDecoder{
	//private static final String TAG = AuthBarcodePlainText.class.getSimpleName();
	
//Global variables	
	private static String encodingType="base64";
	private final SecureRandom RND; 
	private static String intraMessageDivider="\n--*#**#*--\n";
	private static String intraMessageDividerEscaped="\\n"+Pattern.quote("--*#**#*--")+"\\n";
	private static String messageDivider="\n--#*##*#--\n";
	private static String messageDividerEscaped="\\n"+Pattern.quote("--#*##*#--")+"\\n";
	private static String magicHeader="auth2dbarcode:";
	
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
					if(value !=null && value instanceof String) {
						if(!unsignedMessage.isEmpty()) unsignedMessage +=intraMessageDivider;
						unsignedMessage += ( (String) entry ).replace(intraMessageDivider, "#"+intraMessageDivider)
											+intraMessageDivider
											+( (String) value ).replace(intraMessageDivider, "#"+intraMessageDivider);
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
			if( strSegment==null || strSegment.length<1 || ( strSegment.length !=1 && (strSegment.length & 0x1) >0 ) ) return false;
			if(strSegment.length ==1){
				String value=strSegment[0].replace("#"+intraMessageDivider, intraMessageDivider);
				try {
					this.insertData("dummy", "text/plain", value);
				} catch (Exception e) { }
			} else {
				for(int i=0;i<(strSegment.length-1);i+=2){
					String index=strSegment[i].replace("#"+intraMessageDivider, intraMessageDivider);
					String value=strSegment[i+1].replace("#"+intraMessageDivider, intraMessageDivider);
					try {
						this.insertData(index, "text/plain", value);
					} catch (Exception e) { }
				}
			}
			unsignedMessage=unsignedMsg;
			return true;
		}
//Encoding side
		/**
		 * Create digital signature of the saved data, the signed object can be found from getSignedMessageString() after calling this function 
		 */
	@Override
	public void compressAndSignData() throws Exception {
		/*
		 * This function works like 
		 * message in plain text
		 * --#*##*#--
		 * JSONObject (digital signature block)
		 */
		/*A digital signature block should have: 
		 * "signatureMethod"
		 * "issuer"
		"encodingType"
		"pkcs7-signature"
		"date"
		"nonce"
		optional field:
		"pkcs7-digitalcerts"
		"From" 
		*/
		String unsignedMsg=getunsignedMessageString();
		//Log("The input message size: "+unsignedMsg.getBytes("UTF-8").length);
		messageDate=new Date();
		//Set the timezone
		//Thu Jan 16 12:26:01.54 GMT+8 2014 AD
		DateFormat formatter = new SimpleDateFormat("MMM dd HH:mm:ss.SS zzzz yyyy");    
		formatter.setTimeZone(TimeZone.getDefault());
		String signMethod=this.getSignatureMethod();
		int nonce=RND.nextInt();
		if(signMethod.compareTo("No algorithm selected")==0) throw new Exception("No digital signature method is selected");
		//Prepare the signature block
		JSONObject signatureObject=new JSONObject().put("encodingType", encodingType)
									.put("date", formatter.format(messageDate))
									.put("nonce", nonce)
									.put("signatureMethod", signMethod)
									.put("issuer", this.getIssuerName());
		String senderName=getSenderName();
		if(senderName !=null && !senderName.isEmpty()) signatureObject.put("From", senderName);
		String messageToSign=unsignedMsg.replace(messageDivider, "#"+messageDivider)+messageDivider
							+JSONtoStringSorted(signatureObject);
		//Create the digital signature
		signature=createSignature(messageToSign.getBytes("UTF-8"), this.senderKey, signMethod);
		if(signature ==null ||signature.length<=0) throw new Exception("Cannot create the signature.");
		String base64Signature="";
		try{
			base64Signature=new String(Base64.encodeBase64(signature), "UTF-8");
		} catch(UnsupportedEncodingException e2){
			base64Signature=Base64.encodeBase64String(signature);
		}
		//Package the message
		signatureObject.put("pkcs7-signature", base64Signature);		
		if(this.isIncludeCert) {
			String Certificate="";
			try{
				Certificate=new String(Base64.encodeBase64(this.senderCert.getEncoded()), "UTF-8");
			} catch(UnsupportedEncodingException e2){
				Certificate=Base64.encodeBase64String(this.senderCert.getEncoded());
			}
			signatureObject.put("pkcs7-digitalcerts", Certificate);
		}
		signedMessage=unsignedMsg.replace(messageDivider, "#"+messageDivider)+messageDivider+JSONtoStringSorted(signatureObject);
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
				JSONObject signatureBlock=new JSONObject(strSegment[1]);
				if(signatureBlock.has("encodingType") && signatureBlock.has("date") && signatureBlock.has("pkcs7-signature")
				   && signatureBlock.has("nonce") && signatureBlock.has("signatureMethod") && signatureBlock.has("issuer")){
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
			//A regular expression learn from the post: http://stackoverflow.com/questions/10781561/regex-to-match-specific-strings-without-a-given-prefix
			String[] strSegment=messageString.split( "(?<!#)"+messageDividerEscaped );
			if(strSegment !=null && strSegment.length >1){
				unsignedMsg=strSegment[0];
				JSONObject signatureBlock=new JSONObject(strSegment[1]);
				if(signatureBlock.has("encodingType") && signatureBlock.has("date") && signatureBlock.has("pkcs7-signature")
				   && signatureBlock.has("nonce") && signatureBlock.has("signatureMethod") && signatureBlock.has("issuer")){
					try{
						messageDate=new SimpleDateFormat("MMM dd HH:mm:ss.SS zzzz yyyy").parse(signatureBlock.getString("date"));
					} catch(ParseException e2){
						messageDate=new SimpleDateFormat("EEE MMM dd HH:mm:ss.SS zzzz yyyy G").parse(signatureBlock.getString("date"));
					}
					//according to "new String(Base64.encode(signature), "UTF-8");" in compressAndSignData()
					signature=Base64.decodeBase64(signatureBlock.getString("pkcs7-signature").getBytes("UTF-8"));
					issuer=signatureBlock.getString("issuer");
					signMethod=signatureBlock.getString("signatureMethod");
					String senderName=(signatureBlock.has("From"))? signatureBlock.getString("From") : "";
					if(senderName !=null && !senderName.isEmpty()) setSenderName(senderName);
					if(!unsignedMsg.isEmpty() && messageDate!=null && signature!=null) {
						//according to the compressAndSignData()
						signatureBlock.remove("pkcs7-signature");
						if(signatureBlock.has("pkcs7-digitalcerts")){
							//new String(Base64.encode(this.senderCert.getEncoded()), "UTF-8");
							embededCert=Base64.decodeBase64(signatureBlock.getString("pkcs7-digitalcerts").getBytes("UTF-8"));
							signatureBlock.remove("pkcs7-digitalcerts");
						}
						signedMessage=messageString;
						//according to the compressAndSignData()
						messageToSign=unsignedMsg+messageDivider+JSONtoStringSorted(signatureBlock);
					}
				}
			}
		}catch(NullPointerException e2){}
		catch(PatternSyntaxException e2){}
		catch(JSONException e2){}
		catch(ParseException e2){}
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

	//Step 3: reconstruct the dataArray back
		unsignedMsg=unsignedMsg.replace("#"+messageDivider, messageDivider);
		return parseunsignedMessageString(unsignedMsg);
	}
//Other functions	
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
	@SuppressWarnings("unchecked")
	public static String valueToString(Object value) throws JSONException {
        if (value == null || value.equals(null)) throw new JSONException("Null value input");
        if (value instanceof Number || value instanceof Boolean ) 
        	return value.toString();
        if (value instanceof JSONObject) 
            return JSONtoStringSorted((JSONObject) value);
        if (value instanceof Map ) 
            return JSONtoStringSorted( new JSONObject((Map<?, ?>) value) );
        if (value instanceof Collection) 
            return new JSONArray( (Collection<Object>) value).toString();
        if (value instanceof JSONArray){
        	return ((JSONArray) value).toString();
        }
        if (value.getClass().isArray()) {
        	return new JSONArray(Arrays.asList(value)).toString();
        }
        return JSONObject.quote(value.toString());
    }
	static protected void Log(String message){
		//System.out.println(TAG+": "+message);
	}
}