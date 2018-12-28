/**
 * 
 * Copyright (C) 2012 Solon in CUHK
 *
 *
 */

package edu.cuhk.ie.authbarcode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bson.BSON;
import org.bson.BSONObject;
import org.bson.BasicBSONDecoder;
import org.bson.BasicBSONObject;

/**
 * This class handle the lists of input and form them into one with digital signature
 * And return a ByteArray so as to become a Authenticated2Dbarcode
 * @author solon li
 *
 */
public class AuthBarcodeObjBSON extends Auth2DbarcodeDecoder{
	private static final String TAG = AuthBarcodeObjBSON.class.getSimpleName();
	
//Global variables
	//Bad practice: override the variables with other types 
	protected BasicBSONObject dataArray;
	protected BasicBSONObject signedMessage=null;
	
	//private String header = "Content-Type: signed-data";
	private static final Map<String,String> MessageJSONIndex = new HashMap<String,String>(){
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

	{
		put("header","AuthBarcodeVer");
		//put("compressedData","compressed-data");
		put("signature","pkcs7-signature");
		put("issuer","From");
		put("signMethod","micalg");
		put("encodingType","Content-Encoding");
		put("contentType","Content-Type");
		put("compressMethod","compress-method");
		put("data","data");
		put("date","date");
		put("certificate","pkcs7-digitalcerts");
		put("sender","DFrom");
	}};
	
//Constructor
	public AuthBarcodeObjBSON(PrivateKey signer, X509Certificate Cert) {
		super(signer, Cert);
		dataArray=new BasicBSONObject(); 
	}

//Override Functions
	public String getSignatureMethod(){
		if(signedMessage!=null){
			try {
				return (String) signedMessage.get(MessageJSONIndex.get("signMethod"));
			} catch(Exception e){ }
		}
		return (signMethod.length()>0)? signMethod:"No algorithm selected";
	}
	/**
	 * For the time being, we ignore the format
	 */
	public void insertData(String id, String format, Object value, boolean overwrite) throws Exception{
		//if(id.length()<=0||format.length()<=0||value== null) throw new Exception("wrong input");
		if(id.length()<=0||value== null) throw new Exception("wrong input");
		if(!overwrite&&dataArray.containsField(id)) throw new Exception("id already exists");
		signedMessage=null;
		dataArray.put(id, value);	
	}
	public void insertData(String id, String format, Object value) throws Exception{
		try{
			insertData(id, format, value, false);
		} catch (Exception e) {
			throw new Exception(e);
		}
	}
	public void insertData(String id, Object value) throws Exception{
		try{
			insertData(id, "", value, false);
		} catch (Exception e) {
			throw new Exception(e);
		}
	}
	public Object getDataById(String id){
		if(id.length()<=0 || dataArray.isEmpty() || dataArray.get(id)==null) {
			//Log("Cannot find the value for id:"+( (id.length()<=0)? "No valid id":id) );
			return null;
		}
		return dataArray.get(id); 
	}
	public String getFormatById(String id){
		//Hard code two case checking: text or image
		Object object=this.getDataById(id);
		if(object ==null) return null;
		if(object instanceof String) return "text/";
		if(object.getClass().getSimpleName().startsWith("byte")) return "image/";
		return null;
	}
	public Set<String> getKeySet(){
		return (dataArray!=null && !dataArray.isEmpty())? dataArray.keySet():null;
	}
	public int getDataCount(){
		return (dataArray!=null)? dataArray.size():0;
	}

	
//Implementing functions encoding part
	/**
	 * compress and sign the data. Notice that if either compression or signature method or data is changed, you must call this again before calling getSignature, getCompressedData or getMessage()
	 * @return if the signature generated successfully
	 * @throws Exception exception in the process
	 */
	public void compressAndSignData() throws Exception{
		/*The signature should sign on a BasicBSONObject with 4 entries (except headers)
		 * 1)issuer, the identity of the singer
		 * 2)signMethod
		 * 3)data, as a BasicBSONObject created by entries in dataArray
		 * 4)Date
		 * The return should be saved inside signedMessage
		 * data can be gotten by getDataByte method
		 * Optionally, there are:
		 * 1) Sender
		 * 2) Digital certificate
		 */
		/* Inside the signedMessage, there should be the above entries with 1 entry: 
		 * 1)signature, the signature signs on the whole data entry
		 */
		signedMessage=null;
		byte[] uncompressedData,compressedData,unsignedMessage,signature;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if(dataArray.isEmpty()){
			Log("No data to be inserted. No message created.");
		}
		//Step 0: compress the data
		uncompressedData=BSON.encode(dataArray);
		if(compressMethod !=""){
			switch(supportedCompressionAlgorithm.indexOf(compressMethod)){
			case 0:
				//ZLIB
				Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
				DeflaterOutputStream dos = new DeflaterOutputStream(baos,deflater);
				dos.write(uncompressedData, 0, uncompressedData.length);
				dos.finish();
				break;
			case 1:
				//GZIP
				GZIPOutputStream gos = new GZIPOutputStream(baos);
				gos.write(uncompressedData, 0, uncompressedData.length);
				gos.finish();
				break;
			default:
				throw new Exception("Compression method is not supported. Method index: "+supportedCompressionAlgorithm.indexOf(compressMethod));
			}
			compressedData=(baos.size() > 0)? baos.toByteArray():uncompressedData;
		}
		else compressedData=uncompressedData; //If no compression method is selected, then just use the uncompressed data

		//Step 1: prepare the message
		//BasicBSONObject dataBSON=new BasicBSONObject(MessageJSONIndex.get("data"),dataArray);
		BasicBSONObject dataBSON=new BasicBSONObject(MessageJSONIndex.get("header"), "0.1");
		dataBSON.put(MessageJSONIndex.get("issuer"), getIssuerName());
		dataBSON.put(MessageJSONIndex.get("signMethod"), signMethod);
		dataBSON.put(MessageJSONIndex.get("date"), new Date());
		dataBSON.put(MessageJSONIndex.get("data"),compressedData);
		String senderName=getSenderName();
		if(senderName !=null && !senderName.isEmpty()) dataBSON.put(MessageJSONIndex.get("sender"), senderName);
		/*Set<String> dataKey=dataArray.keySet();
		for(String str : dataKey){
			dataBSON.put(MessageJSONIndex.get("data")+str, dataArray.get(str));
		}
		*/
		unsignedMessage=BSON.encode(dataBSON);
		if(unsignedMessage.length<=0) throw new Exception("Cannot get the data but not error shown.");
		//Log("The compressed and joined data with header total byte size: "+unsignedMessage.length);
		//Step 2: generate signature
		signature=createSignature(unsignedMessage, this.senderKey, signMethod);
		if(signature==null || signature.length<=0) throw new Exception("Cannot create the signature.");
		//Log("The signature size: "+signature.length);
		
		//dataBSON.put(MessageJSONIndex.get("signature"), signature);
		signedMessage=new BasicBSONObject(dataBSON);
		signedMessage.put(MessageJSONIndex.get("signature"), signature);
		
		if(isIncludeCert && senderCert !=null) signedMessage.put(MessageJSONIndex.get("certificate"), senderCert.getEncoded());
		Log("The whole message size:"+BSON.encode(signedMessage).length);
	}
	public byte[] getSignature(){		
		if(signedMessage==null) return null;
		return (byte[]) signedMessage.get(MessageJSONIndex.get("signature"));
	}
	public byte[] getCompressedData(){
		if(signedMessage==null) return null;
		return BSON.encode((BSONObject) signedMessage.get(MessageJSONIndex.get("data")));
	}
	public String getSignedMessageString(){
		if(signedMessage==null) return null;
		return signedMessage.toString();
	}
	public byte[] getSignedMessageByte(){
		byte[] message=null;
		try{
			if(signedMessage==null) return null;
			message=BSON.encode(signedMessage);
			byte[] compressedMsg=defaultCompress(message);
			//Return the compressed byte array if and only if compression really gives an advantage to the size (hard code 20 bytes here)
			return ( compressedMsg !=null && compressedMsg.length > 1 && compressedMsg.length < (message.length-20) )?
					compressedMsg:
					message;
		} catch (Exception e) {
			Log("Cannot return the message. Details:"+e);
			return message;
		}
	}
	public Date getMessageDate(){
		if(signedMessage==null) return null;
		return signedMessage.getDate(MessageJSONIndex.get("date"));
	}

/*
 * Below are the functions on decoding part
 */
	public static AuthBarcodeObjBSON getSupportedDecoder(byte[] str){
		BasicBSONDecoder decoder = new BasicBSONDecoder();
		try{
			BasicBSONObject signedObject=(BasicBSONObject) decoder.readObject(str);
			if(signedObject!=null) {
				AuthBarcodeObjBSON resultObject=new AuthBarcodeObjBSON(null,null);
				resultObject.setUndecodedByte(str);
				return resultObject;
			}
		} catch(Exception e){ }
		return null;
	}
	
	public boolean decodeBarcodeString(String str, KeyStore trustStore) throws Exception{
		if(undecodedByte==null) throw new Exception("This format does not support string input yet.");
		else return decodeBarcodeByte(undecodedByte, trustStore);
	}
	
	//The format of received data should follows the one stated in compressAndSignData()	
	public boolean decodeBarcodeByte(byte[] str, KeyStore trustStore) throws Exception{
		signedMessage=null;
		BasicBSONDecoder decoder = new BasicBSONDecoder();
		try{
			signedMessage=(BasicBSONObject) decoder.readObject(str);
		} catch(Exception e){ 
			throw new Exception("Format not supported");
		}
		if(signedMessage ==null) throw new Exception("Format not supported");
		//Make sure the data fit our requirement
		//if(!dataJSON.getString(MessageJSONIndex.get("header")).equals("0.1")) throw new Exception("Unsupported message version");
		
		BasicBSONObject dataBSON=new BasicBSONObject(signedMessage);
		//dataBSON.remove(MessageJSONIndex.get("signature"));

		byte[] unsignedMessage, signature;
//Getting the data from the barcode
		String issuer=dataBSON.getString(MessageJSONIndex.get("issuer"));
		signature=(byte[]) dataBSON.get(MessageJSONIndex.get("signature"));
		dataBSON.remove(MessageJSONIndex.get("signature"));
		String signMethod=dataBSON.getString(MessageJSONIndex.get("signMethod"));
		if(signature.length<1 || signMethod.length()<1) throw new Exception("Cannot get the data from barcode");
		//If there exists a digital certificate, verify the certificate and use it to verify the digital signature
		//If not, just use the one found from the trust store
		X509Certificate issuerCert=null;
		if(dataBSON.containsField(MessageJSONIndex.get("certificate"))){
			byte[] certByte=(byte[]) dataBSON.get(MessageJSONIndex.get("certificate"));
			dataBSON.remove(MessageJSONIndex.get("certificate"));
			issuerCert=getVerifiedCertificate(certByte, trustStore, issuer);
		} else issuerCert=getVerifiedCertificate(null, trustStore, issuer);
		String senderName=(dataBSON.containsField(MessageJSONIndex.get("sender")))? 
				dataBSON.getString(MessageJSONIndex.get("sender")) : "";
		if(senderName !=null && !senderName.isEmpty()) setSenderName(senderName);
		unsignedMessage=BSON.encode(dataBSON);
//Part1: verify the signature
		if(issuerCert==null){
			Log("No digital certificate found to verify the 2D barcode content");
			this.isVerified=false;
		} else this.isVerified=verifySignature(unsignedMessage, signature, signMethod, issuerCert);
		if(this.isVerified) setIssuerName(getCertificateSubjectName(issuerCert));
		else setIssuerName(issuer);
		setIssuerEmail(issuerCert);
//Part 2: reconstruct the dataArray
		/*dataArray=new BasicBSONObject();
		Set<String> dataKey=signedMessage.keySet();
		String prefix=MessageJSONIndex.get("data");
		for(String key : dataKey){
			if(key.startsWith(prefix) && key.length()>prefix.length()){
				dataArray.put(key.substring(prefix.length()), signedMessage.get(key));
			}
		}*/
		//First, try if the data is saved as BSONObject directly
		dataArray=null;
		Object dataObj=signedMessage.get(MessageJSONIndex.get("data"));
		if(dataObj instanceof BasicBSONObject) dataArray=(BasicBSONObject) dataObj;
		if(dataArray !=null) return true;
		//try if it is saved as byte array			
		if(dataObj instanceof byte[]){
			try{
				dataArray=(BasicBSONObject) decoder.readObject((byte[]) dataObj);
			} catch(Exception e){ }
			//Then try to decompress it and parse to dataArray
			if(dataArray !=null) return true;
			byte[] compressedData=(byte[]) dataObj;
			try{
				//try the default ZLIB decompression first
				byte[] uncompressedData=defaultDecompress(compressedData);
				dataArray=(BasicBSONObject) decoder.readObject(uncompressedData);
			} catch(Exception e){ }
			if(dataArray !=null) return true;
			//Then try the gzip decompression
			ByteArrayInputStream cis=new ByteArrayInputStream(compressedData);
			GZIPInputStream ios = new GZIPInputStream(cis);
			ByteArrayOutputStream baos=new ByteArrayOutputStream();
			byte[] buffer = new byte[1000];
	        int len;
	        while((len = ios.read(buffer)) > 0) {
	            baos.write(buffer, 0, len);
	        }
			try{
				if(baos.size() > 0) dataArray=(BasicBSONObject) decoder.readObject(baos.toByteArray());
			} catch(Exception e) { }
		}
		return (dataArray!=null)? true:false;
	}
	
	static protected void Log(String message){
		//System.out.println(TAG+": "+message);
		//android.util.Log.d(TAG, message);
	}
}