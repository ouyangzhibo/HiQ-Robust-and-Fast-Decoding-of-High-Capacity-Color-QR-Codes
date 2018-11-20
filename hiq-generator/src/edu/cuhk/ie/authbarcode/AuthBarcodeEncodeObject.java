/**
 * 
 * Copyright (C) 2012 Solon in CUHK
 *
 *
 */

package edu.cuhk.ie.authbarcode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;



import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;


/**@deprecated
 * This class handle the lists of input and form them into one with digital signature
 * And can return a ByteArray so as to become a Authenticated2Dbarcode
 * @author solon li
 *
 */
public class AuthBarcodeEncodeObject extends Auth2DbarcodeDecoder{
	//private static final String TAG = "testEmailActivityBarcodeObjectPart";
	
//Global variables
	
	//private String header = "Content-Type: signed-data";
	private static final Map<String,String> MessageJSONIndex = new HashMap<String,String>(){
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

	{
		put("header","Auth2Dbarcode-Version:");
		put("compressedData","application/pkcs7-mime/compressed-data");
		put("signature","application/pkcs7-signature");
		put("issuer","From: ");
		put("signMethod","micalg");
		put("encodingType","Content-Transfer-Encoding");
		put("contentType","Content-Type");
		put("compressMethod","comM");
		put("data","data");
		put("certificate","application/pkcs7-mime; name=smime.p7c; smime-type=certs-only");
	}};
	
//Constructor
	public AuthBarcodeEncodeObject(PrivateKey signer, X509Certificate Cert) {
		super(signer, Cert);
	}

//Override Functions
	public String getCompressMethod(){
		if(signedMessage!=null){
			try {
				JSONObject obj = signedMessage.getJSONObject(MessageJSONIndex.get("data"));
				return (String) obj.get(MessageJSONIndex.get("compressMethod"));
			} catch(Exception e){
				return (compressMethod.length()>0)? compressMethod:"No algorithm selected";
			}
		}
		else return (compressMethod.length()>0)? compressMethod:"No algorithm selected";
	}
	public String getSignatureMethod(){
		if(signedMessage!=null){
			try {
				JSONObject obj = signedMessage.getJSONObject(MessageJSONIndex.get("data"));
				return (String) obj.get(MessageJSONIndex.get("signMethod"));
			} catch(Exception e){
				return (signMethod.length()>0)? signMethod:"No algorithm selected";
			}
		}
		else return (signMethod.length()>0)? signMethod:"No algorithm selected";
	}
	public String getIssuerName(){
		if(signedMessage!=null){
			try {
				JSONObject obj = signedMessage.getJSONObject(MessageJSONIndex.get("data"));
				return (String) obj.get(MessageJSONIndex.get("issuer"));
			} catch(Exception e){
				return super.getIssuerName();
			}
		}
		return super.getIssuerName();
	}

	
//Implementing functions encoding part
	/**
	 * compress and sign the data. Notice that if either compression or signature method or data is changed, you must call this again before calling getSignature, getCompressedData or getMessage()
	 * @return if the signature generated successfully
	 * @throws Exception exception in the process
	 */
	public void compressAndSignData() throws Exception{
		/*The signature should sign on a JSON objects with 4 entries 
		 * 1)issuer, the identity of the singer
		 * 2)signMethod
		 * 3)compressMethod
		 * 4)dataByte, compressed data
		 * The return should be saved inside signedMessage
		 * data can be gotten by getDataByte method
		 */
		/* Inside the signedMessage, there should be only 2 entries: 
		 * 1)data
		 * 2)signature, the signature signs on the whole data entry
		 * Inside the data, it is also a JSON objects with 4 entries 
		 * 1)issuer, the identity of the signer
		 * 2)signMethod
		 * 3)compressMethod
		 * 4)data, compressed data
		 */
		signedMessage=null;
		byte[] uncompressedData,compressedData,unsignedMessage,signature;
		ByteArrayOutputStream baos;
		JSONObject dataJSON=new JSONObject();
		
		//Step 1: concat and compress the data
		uncompressedData=this.getDataByte();
		if(uncompressedData.length<=0) throw new Exception("Cannot get the data but not error shown.");
		Log("The uncompressed but joined total byte size: "+uncompressedData.length);
		
		baos = new ByteArrayOutputStream();
		//TODO: Make this compression method more generic
		if(!supportedCompressionAlgorithm.contains(compressMethod)) throw new Exception("Compression method is not supported. Method: "+compressMethod.toString());
		switch(supportedCompressionAlgorithm.indexOf(compressMethod)){
		case 0:
			//ZLIB
			Log("ZLIB is used");
			Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
			//deflater.setInput(uncompressedData);
			//deflater.finish();
			DeflaterOutputStream dos = new DeflaterOutputStream(baos,deflater);
			dos.write(uncompressedData, 0, uncompressedData.length);
			dos.finish();
			break;
		case 1:
			//GZIP
			Log("GZIP is used");
			GZIPOutputStream gos = new GZIPOutputStream(baos);
			gos.write(uncompressedData, 0, uncompressedData.length);
			gos.finish();
			break;
		default:
			throw new Exception("Compression method is not supported. Method index: "+supportedCompressionAlgorithm.indexOf(compressMethod));
		}
		if(baos.size() <= 0) throw new Exception("Nothing is compressed. Compress Method: "+compressMethod.toString());
		compressedData=baos.toByteArray();
		if(compressedData.length <= 0) throw new Exception("Cannot compress the data but not error shown.");
		//Log("The compressed total byte size: "+compressedData.length);
		
		//Step 2: prepare whole data part of the message
		dataJSON.putOpt(MessageJSONIndex.get("header"), "0.1");
		dataJSON.putOpt(MessageJSONIndex.get("issuer"), getIssuerName());
		dataJSON.putOpt(MessageJSONIndex.get("compressMethod"), compressMethod);
		dataJSON.putOpt(MessageJSONIndex.get("signMethod"), signMethod);
		dataJSON.putOpt(MessageJSONIndex.get("encodingType"), "UTF-8");
		JSONObject contentJSON=new JSONObject();
		contentJSON.putOpt(MessageJSONIndex.get("encodingType"), "base64");
		contentJSON.putOpt(MessageJSONIndex.get("data"), Base64.encodeBase64String(compressedData));
		dataJSON.putOpt(MessageJSONIndex.get("compressedData"), contentJSON);
		unsignedMessage=dataJSON.toString().getBytes();
		//Log("The compressed with header total byte size: "+unsignedMessage.length);

		//Step 3: generate signature
		if(signAgent==null){
			 signAgent = (signProvider != "")? Signature.getInstance(signMethod, signProvider):Signature.getInstance(signMethod);
		}
		if(signAgent == null) throw new Exception("Cannot initilize the signature generater"); 
		signAgent.initSign(senderKey);
		signAgent.update(unsignedMessage);
		signature=signAgent.sign();
		if(signature.length<=0) throw new Exception("Cannot create the signature but not error shown.");
		Log("The signature size: "+Base64.encodeBase64String(signature).length());
		
		//Final step: create the signMessage JSON object
		signedMessage=new JSONObject();
		signedMessage.putOpt(MessageJSONIndex.get("data"), dataJSON);
		JSONObject signObject=new JSONObject();
		signObject.putOpt(MessageJSONIndex.get("encodingType"), "base64");
		signObject.putOpt(MessageJSONIndex.get("data"), Base64.encodeBase64String(signature));
		signedMessage.putOpt(MessageJSONIndex.get("signature"), signObject);
		if(isIncludeCert && senderCert !=null) {
			try{
				JSONObject certObject=new JSONObject();
				certObject.putOpt(MessageJSONIndex.get("encodingType"), "base64");
				certObject.putOpt(MessageJSONIndex.get("data"), Base64.encodeBase64String(senderCert.getEncoded()));
				signedMessage.putOpt(MessageJSONIndex.get("certificate"), certObject);
			} catch(Exception e){}
		}
		//Log("The whole message size before encoding:"+signedMessage.toString().getBytes().length);
		//Log("The whole message size under Base64 encoding:"+Base64.encode(signedMessage.toString().getBytes(), Base64.DEFAULT).length);
	}
	public byte[] getSignature(){
		if(signedMessage==null) return null;
		try {
			JSONObject obj = signedMessage.getJSONObject(MessageJSONIndex.get("signature"));
			return Base64.decodeBase64(obj.getString(MessageJSONIndex.get("data")));
		} catch (Exception e) {
			return null;
		}
	}
	public byte[] getCompressedData(){
		if(signedMessage==null) return null;
		try {
			JSONObject obj = signedMessage.getJSONObject(MessageJSONIndex.get("data"));
			return Base64.decodeBase64(obj.getString(MessageJSONIndex.get("data")));
		} catch(Exception e){
			return null;
		}
	}
	public String getSignedMessageString(){
		if(signedMessage==null) return null;
		try {
			return signedMessage.toString(1);
		} catch (Exception e) {
			Log("Cannot return the message. Details:"+e);
			return null;
		}
	}
	public String getUnSignedMessageString(){
		if(signedMessage==null) return null;
		try {
			JSONObject signObject=signedMessage;
			if(signObject.has(MessageJSONIndex.get("signature"))) signObject.remove(MessageJSONIndex.get("signature"));
			JSONObject dataJSON=rebuildJSON(signObject.getJSONObject(MessageJSONIndex.get("data")));
			if(dataJSON.has(MessageJSONIndex.get("header"))) dataJSON.remove(MessageJSONIndex.get("header"));
			if(dataJSON.has(MessageJSONIndex.get("signMethod"))) dataJSON.remove(MessageJSONIndex.get("signMethod"));
			if(dataJSON.has(MessageJSONIndex.get("issuer"))) dataJSON.remove(MessageJSONIndex.get("issuer"));
			signObject.remove(MessageJSONIndex.get("data"));
			signObject.putOpt(MessageJSONIndex.get("data"), dataJSON);
			return signObject.toString(1);
		} catch (Exception e) {
			Log("Cannot return the message. Details:"+e);
			return null;
		}
	}
	
	
//Object unique functions encoding part
	/**
	 * Return the byte[] in JSON format of all data uncompressed, base64-encoded, excluding the headers and digital signature
	 * @return
	 * @throws JSONException 
	 */
	public byte[] getDataByte() throws JSONException{
		JSONObject data=new JSONObject();
		JSONObject tempJSON,tempEntry;
		
		String tempStr, tempFormat, tempEncoding;
		for (Map.Entry<String, JSONObject> entry : dataArray.entrySet()) {
			tempEntry = entry.getValue();
			tempFormat=tempEntry.getString("format");
			//TODO:Extend the format supported and make it less stupid
			if(tempFormat.startsWith("image/")){
				byte[] tempValue=(byte[]) tempEntry.get("value");
	            tempStr=Base64.encodeBase64String(tempValue);
	            tempEncoding="base64";
	            //Log("The byte size of image after become BASE64 string:"+tempStr.length());
			}
			else {
				tempStr=tempEntry.get("value").toString();
				tempEncoding="UTF-8";
			}
			
			tempJSON=new JSONObject();
			tempJSON.putOpt(MessageJSONIndex.get("contentType"), tempFormat);
			tempJSON.putOpt(MessageJSONIndex.get("encodingType"), tempEncoding);
			tempJSON.putOpt(MessageJSONIndex.get("data"), tempStr);
			//Log("The size of new insert object is: "+tempJSON.toString().getBytes().length);
		    data.putOpt(entry.getKey(), tempJSON);
		}
		//Log("Length of the JSON object: "+data.toString().length());
		if(data.length()>0) return data.toString().getBytes();
		else return null;
	}
	private boolean putDataFromByte(byte[] uncompressedData) throws Exception{
		this.dataArray=new HashMap<String, JSONObject>();
		JSONObject data=new JSONObject(new String(uncompressedData));
		Iterator<?> keys=data.keys();
		JSONObject tempJSON;
	    String index=null, tempEncoding,tempStr;
	    while(keys.hasNext()){
		    index=(String) keys.next();
		    tempJSON=data.getJSONObject(index);
		    tempEncoding=tempJSON.getString(MessageJSONIndex.get("encodingType"));
		  //TODO:Extend the format supported and make it less stupid
		    if(tempEncoding.equals("base64")){
		    	byte[] tempValue=Base64.decodeBase64(tempJSON.getString(MessageJSONIndex.get("data")));
		    	this.insertData(index, tempJSON.getString(MessageJSONIndex.get("contentType")), tempValue);
		    }
		    else{
		    	tempStr=tempJSON.getString(MessageJSONIndex.get("data"));
		    	this.insertData(index, tempJSON.getString(MessageJSONIndex.get("contentType")), tempStr);
		    }
	    }
		return true;
	}
	
/*
 * Below are the functions on decoding part
 */
	public static AuthBarcodeEncodeObject getSupportedDecoder(String str){
		//Be careful, do not call super(str);
		try{
			JSONObject signedObject=new JSONObject(str);
			if(signedObject!=null) return new AuthBarcodeEncodeObject(null,null);
		} catch(JSONException e){
			//The result is not AuthBarcodeEncodeObject
		}
		return null;
	}
	
	//The format of received data should follows the one stated in compressAndSignData()	
	public boolean decodeBarcodeString(String str, KeyStore trustStore) throws Exception{
			signedMessage=null;
			InflaterInputStream ios;
			ByteArrayInputStream cis;
			ByteArrayOutputStream baos;
			byte[] uncompressedData, compressedData, unsignedMessage, signature;
	//Getting the data from the barcode
			JSONObject contentJSON;
			JSONObject messageObject=new JSONObject(str);
			JSONObject dataJSON=rebuildJSON(messageObject.getJSONObject(MessageJSONIndex.get("data")));
			String compressMethod=dataJSON.getString(MessageJSONIndex.get("compressMethod"));

			//Make sure the data fit our requirement
			//if(!dataJSON.getString(MessageJSONIndex.get("header")).equals("0.1")) throw new Exception("Unsupported message version");
			//if(!dataJSON.getString(MessageJSONIndex.get("encodingType")).equals("UTF-8"))  throw new Exception("Unsupported encoding type on message");
			unsignedMessage=dataJSON.toString().getBytes();
			
			try{
				String issuer=dataJSON.getString(MessageJSONIndex.get("issuer"));
				X509Certificate senderCert=getVerifiedCertificate(null, trustStore,issuer);
				//TODO: reenable the check validity
				//senderCert.checkValidity();
				JSONObject signObject=messageObject.getJSONObject(MessageJSONIndex.get("signature"));
				if(!signObject.getString(MessageJSONIndex.get("encodingType")).equals("base64")) throw new Exception("Unsupported encoding type on signature");
				signature=Base64.decodeBase64(signObject.getString(MessageJSONIndex.get("data")));
				if(signature.length<1 || unsignedMessage.length<1) throw new Exception("Cannot get the data from barcode");
				String signMethod=dataJSON.getString(MessageJSONIndex.get("signMethod"));
				
		//If there exists a digital certificate, verify the certificate
				JSONObject certObject=messageObject.optJSONObject(MessageJSONIndex.get("certificate"));
				if(certObject !=null && certObject.getString(MessageJSONIndex.get("encodingType")).equals("base64")){
					byte[] certByte=Base64.decodeBase64(certObject.getString(MessageJSONIndex.get("data")));
					InputStream in = new ByteArrayInputStream(certByte);
					CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
					X509Certificate embededCert = (X509Certificate) certFactory.generateCertificate(in);
					//TODO: reenable the check validity
					//PublicKey certKey=senderCert.getPublicKey();
					//embededCert.checkValidity();
					//embededCert.verify(certKey);
					//If it is verified, use the embeded cert to do the verification
					senderCert=embededCert;
				}
		//Part 1: verify the signature
				if(!this.setCompressMethod(compressMethod)||!this.setSignatureMethod(signMethod, "SC")) throw new Exception("Unsupported compression or signature algorithm");
				if(signAgent==null){
					 signAgent = (signProvider != "")? Signature.getInstance(signMethod, signProvider):Signature.getInstance(signMethod);
				}
				if(signAgent == null) throw new Exception("Cannot initilize the signature generater"); 
				signAgent.initVerify(senderCert);
				signAgent.update(unsignedMessage);
				if(!signAgent.verify(signature)) throw new Exception("Signature of the barcode is not valid with method:"+signMethod);
				Log("The message is verified");
				this.isVerified=true;
			}catch(Exception e){
				Log("Problem on verification"+e.getMessage());
				this.isVerified=false;
			}
			
			
	//Part 2: no matter the signature is verified or not, decompress and ouput the data
			contentJSON=dataJSON.getJSONObject(MessageJSONIndex.get("compressedData"));
			if(!contentJSON.getString(MessageJSONIndex.get("encodingType")).equals("base64")) throw new Exception("Unsupported encoding type on compressed data");
			compressedData=Base64.decodeBase64(contentJSON.getString(MessageJSONIndex.get("data")));
			if(compressedData.length<1) throw new Exception("Cannot get data from barcode");
			
			cis=new ByteArrayInputStream(compressedData);
			//TODO: Make this compression method more generic
			if(!supportedCompressionAlgorithm.contains(compressMethod)) throw new Exception("Compression method is not supported. Method: "+compressMethod.toString());
			switch(supportedCompressionAlgorithm.indexOf(compressMethod)){
			case 0:
				//ZLIB
				Log("ZLIB is used");
				Inflater inflater = new Inflater();
				ios = new InflaterInputStream(cis,inflater);
				break;
			case 1:
				//GZIP
				Log("GZIP is used");
				ios = new GZIPInputStream(cis);
				break;
			default:
				throw new Exception("Compression method is not supported. Method index: "+supportedCompressionAlgorithm.indexOf(compressMethod));
			}
			baos=new ByteArrayOutputStream();
			byte[] buffer = new byte[1000];
	        int len;
	        while((len = ios.read(buffer)) > 0) {
	            baos.write(buffer, 0, len);
	        }
			if(baos.size() <= 0) throw new Exception("Nothing is decompressed.");
			uncompressedData=baos.toByteArray();
			if(uncompressedData.length <= 0) throw new Exception("Cannot decompress the data but not error shown.");
			//Log("The decompressed total byte size: "+uncompressedData.length);
	//Part 3: reconstruct the dataArray and the signedMessage
			try{
				if(putDataFromByte(uncompressedData)) {
					signedMessage=rebuildJSON(messageObject);
					return true;
				}
			} catch (Exception e){
				throw new Exception("Error when parsing the data into dataArray:"+e);
			}
			return false;
	}
	@Override
	public boolean decodeBarcodeByte(byte[] encodedArray, KeyStore store)
			throws Exception {
		return false;
	}

	@Override
	public Date getMessageDate() {
		// TODO Auto-generated method stub
		return null;
	}

	static protected void Log(String message){
		//System.out.println(TAG+": "+message);
	}
}