/**
 * 
 * Copyright (C) 2012 Solon in CUHK
 *
 *
 */
package edu.cuhk.ie.authbarcode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.asn1.ASN1InputStream;
import org.spongycastle.asn1.ASN1OctetString;
import org.spongycastle.asn1.ASN1Primitive;
import org.spongycastle.asn1.ASN1String;
import org.spongycastle.asn1.DERSequence;
import org.spongycastle.asn1.x509.GeneralName;
import org.json.JSONException;
import org.json.JSONObject;

import edu.cuhk.ie.authbarcodescanner.android.Log;
import edu.cuhk.ie.authbarcodescanner.certificate.CertificateDbEntry;

public abstract class Auth2DbarcodeDecoder {
//Static variables
	private static final String TAG = "Auth2DbarcodeDecoder";
	
	public static final List<String> supportedCompressionAlgorithm = Arrays.asList("ZLIB","GZIP");
	public static final Map<String, String> dataFormatWithType = new HashMap<String,String>(){
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

	{
		put("text/", String.class.getName());
		put("image/","Byte[]");
		put("application/","JSON");
	}};
	static {
		if(android.os.Build.VERSION.SDK_INT <=android.os.Build.VERSION_CODES.KITKAT)
			Security.addProvider(new BouncyCastleProvider());
		//Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
	}
	
	// db entry to be returned after successful save
	static CertificateDbEntry certDbEntry;
	
//Global variables
	protected final PrivateKey senderKey;
	protected X509Certificate senderCert;
	public boolean isIncludeCert=false;
	private String issuer, issuerEmail=null;
	protected String compressMethod="",signMethod="",signProvider="";
	protected Signature signAgent=null;
	
	protected Map<String,JSONObject> dataArray;
	protected JSONObject signedMessage=null;
	
	public Auth2DbarcodeDecoder(PrivateKey signer, X509Certificate Cert){
		dataArray=new HashMap<String, JSONObject>();
		//We should do some checking here
		if(signer==null||Cert==null) Log.d(TAG, "Invalid key or certificate");
		senderKey=(signer!=null)? signer:null;
		senderCert=(Cert!=null)? Cert:null;
		//How to get the signer identity from the certificate?
		//newCertificate.getSubjectX500Principal().getName()
		issuer=(Cert==null || getCertificateSubjectCN(Cert)==null)? "":getCertificateSubjectCN(Cert);
	}
	public String getIssuerName(){ return issuer; }
	public void setIssuerName(String newIssuer) { if(newIssuer !=null && !newIssuer.isEmpty()) issuer=newIssuer; }
	public String getIssuerDisplayName(){
		//If the issuer already has an email, or there is no issuer in the certificate, then no need to append one
		Matcher m = Pattern.compile("\\([\\w=+\\-\\/][\\w='+\\-\\/\\.]*@[\\w\\-]+(\\.[\\w\\-]+)*(\\.[\\w]{2,6})\\)").matcher(issuer);		 
		return (!m.find() && issuerEmail !=null)? issuer+" ("+issuerEmail+")" : issuer;		
	}
	public void setIssuerEmail(String newEmail){ if(newEmail !=null && !newEmail.isEmpty()) issuerEmail=newEmail; }
	public void setIssuerEmail(X509Certificate cert){ if(cert !=null) setIssuerEmail(getCertificateEmail(cert)); }
	private String sender=null;
	public String getSenderName(){ return sender; }
	public void setSenderName(String name) { if(name !=null && !name.isEmpty()) sender=name; }
	
//Part 1: data handling functions
	/**
	 * Insert data
	 * @param id the id of this data
	 * @param format MIME type of the inserted data
	 * @param value The data. For text data, it should be String. For image data, it should be a bitmap
	 * @param overwrite whether to overwrite previous record
	 * @throws Exception if the insertion is not successful
	 */
	public void insertData(String id, String format, Object value, boolean overwrite) throws Exception{
		if(id.length()<=0||format.length()<=0||value== null) throw new Exception("wrong input");
		if(!overwrite&&dataArray.containsKey(id)) throw new Exception("id already exists");
		JSONObject temp=new JSONObject();
		//TODO: improve this checking
		boolean isSupported=false;
		for (Map.Entry<String, String> entry : dataFormatWithType.entrySet()) {
		    String key = entry.getKey();
		    if(format.startsWith(key)) {
		    	isSupported=true;
		    	break;
		    	//I do not know how to check the type of byte[]?
		    	/*if(value.getClass().getName().equals(entry.getValue())) break; 
		    	else throw new Exception("format and input do not match");*/
		    }
		}
		if(!isSupported) throw new Exception("Format not supported");		
		temp.putOpt("format",format);
		temp.putOpt("value", value);
		signedMessage=null;
		dataArray.put(id, temp);	
	}
	public void insertData(String id, String format, Object value) throws Exception{
		try{
			insertData(id, format, value, false);
		} catch (Exception e) {
			throw new Exception(e);
		}
	}
	public Set<String> getKeySet(){
		return (dataArray!=null && !dataArray.isEmpty())? dataArray.keySet():null;
	}
	public Object getDataById(String id){
		if(id.length()<=0 || !dataArray.containsKey(id)) return null;
		JSONObject temp=dataArray.get(id);
		try {
			return temp.get("value");
		} catch (JSONException e) {
			Log.d(TAG, "Cannot return data value. Detail:"+e);
		}
		return null;
	}
	public String getFormatById(String id){
		if(id.length()<=0 || !dataArray.containsKey(id)) return null;
		JSONObject temp=dataArray.get(id);
		try {
			return temp.getString("format");
		} catch (JSONException e) {
			Log.d(TAG, "Cannot return content type. Detail:"+e);
		}
		return null;
	}
	public int getDataCount(){
		return (dataArray!=null)? dataArray.size():0;
	}
	
//Part 2: compress and signature related functions
	public boolean setCompressMethod(String method){
		if(method.length() <= 0) return false;
		if(supportedCompressionAlgorithm.contains(method)) compressMethod=method;
		else return false;
		signedMessage=null;
		return true;
	}
	/**
	 * Set the signing method
	 * @param method name of Algorithm
	 * @param provider name of provider, input null if using the algorithm from default provider
	 * @return boolean, true if the sign algorithm is set successfully
	 * @throws NoSuchAlgorithmException
	 */
	public boolean setSignatureMethod(String method, String provider) throws NoSuchAlgorithmException{
		if(method.length() <= 0) return false;
		signAgent = null;
		Provider providerObj = (provider != null)? Security.getProvider(provider):null;
		signAgent = (providerObj != null)? Signature.getInstance(method, providerObj):Signature.getInstance(method);
		//If no exception throw, the algorithm exists
		signMethod=method;
		signProvider=provider;
		signedMessage=null;
		return true;
	}	
	public String getCompressMethod(){ return (compressMethod.length()>0)? compressMethod:"No algorithm selected"; }
	public String getSignatureMethod(){ return (signMethod.length()>0)? signMethod:"No algorithm selected"; }
	/**
	 * compress and sign the data. Notice that if either compression or signature method or data is changed, you must call this again before calling getSignature, getCompressedData or getMessage()
	 * @return if the signature generated successfully
	 * @throws Exception exception in the process
	 */
	abstract public void compressAndSignData() throws Exception;
	abstract public byte[] getSignature();
	abstract public byte[] getCompressedData();
	abstract public String getSignedMessageString();
	abstract public Date getMessageDate();
	/**
	 * Get the message with signature and is ready for barcode generation.
	 * @return the message in byte[], UTF-8 encoded
	 */
	public byte[] getSignedMessageByte() {
		try{
			String messageStr = getSignedMessageString();
			return defaultCompress(messageStr);
		} catch (Exception e) {
			Log.d(TAG, "Cannot return the message. Details:"+e);
			return null;
		}
	}
	
//Part 3: detecting the correct data unit type of Authenticated 2D barcode, optionally decompress it.
	public static final Map<String, String> subclassTypeWithName = new HashMap<String,String>(){
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

	{
		//put("AuthBarcodeEncodeObject", "Message in JSON Format");
		//put("customSMIMEObject","Message in SMIME Format");
		put("AuthBarcodePlainText","Message in plain text");
		put("AuthBarcodeObjBSON","Message in BSON Format");
	}};
	public String getType(){
		String type=subclassTypeWithName.get(this.getClass().getSimpleName());
		return (type==null)? "Unkonwn message format":type; 
	}
	protected boolean isDecodeString=true;
	public boolean isDecodingString(){ return isDecodeString;}
	private String undecodedString=null;
	public void setUndecodedString(String str){
		isDecodeString=true;
		undecodedString=str;
	}
	public String getUndecodedString(){ return undecodedString; }
	byte[] undecodedByte=null;
	public void setUndecodedByte(byte[] str){
		isDecodeString=false;
		undecodedByte=str;
	}
	public byte[] getUndecodedByte(){ return undecodedByte; }
//Check if the message format is supported
	public static Auth2DbarcodeDecoder getSupportedDecoderByte(byte[] encodedArray){
		//TODO: make this less hard code
		Log.d(TAG, "The message size is:"+encodedArray.length);
		byte[] temp=null;
		//String decompressedStr;
		try {
			temp=defaultDecompress(encodedArray);
			AuthBarcodeObjBSON resultObject=AuthBarcodeObjBSON.getSupportedDecoder(temp);
			if(resultObject!=null) return resultObject;
			//For legacy cases
			String decompressedStr=new String(temp);
			if(decompressedStr!=null) {
				Auth2DbarcodeDecoder tempResult=AuthBarcodeEncodeObject.getSupportedDecoder(decompressedStr);
				if(tempResult!=null) {
					tempResult.setUndecodedString(decompressedStr);
					return tempResult;
				}
			}
			AuthBarcodePlainText resultObj=AuthBarcodePlainText.getSupportedDecoder(temp);
			if(resultObj!=null) return resultObj;
		} catch (Exception e) {
			// It is not compressed
		}
		//TODO: Here we assume there are only two subclasses 
		//How to make it to support arbitrary number of subclasses? Or make it less hard code?
		AuthBarcodeObjBSON resultObject=AuthBarcodeObjBSON.getSupportedDecoder(encodedArray);
		if(resultObject!=null) return resultObject;
		AuthBarcodePlainText resultObj=AuthBarcodePlainText.getSupportedDecoder(encodedArray);
		if(resultObj!=null) return resultObj;
		return null;
	}
	/**
	 * Note that the subclasses should override this method
	 */
	public static Auth2DbarcodeDecoder getSupportedDecoderString(String str){
		//TODO: Here we assume there are only two subclasses 
		//How to make it to support arbitrary number of subclasses? Or make it less hard code?
		//For legacy reason
		/*resultObject=AuthBarcodeEncodeObject.getSupportedDecoder(str);
		if(resultObject!=null) {
			resultObject.setUndecodedString(str);
			return resultObject;
		}*/
		Auth2DbarcodeDecoder resultObject=AuthBarcodePlainText.getSupportedDecoder(str);
		if(resultObject!=null) return resultObject;
		return null;
	}
	/**
	 * Note that the subclasses should override this method
	 */
	public static Auth2DbarcodeDecoder getSupportedDecoder(String str){return null;}
	/**
	 * Note that the subclasses should override this method
	 */
	public static Auth2DbarcodeDecoder getSupportedDecoder(byte[] str){return null;}
	
	/*
	 * Perform default compression and stringify to a message
	 * This is pair with defaultDecompress
	 */
	protected static byte[] defaultCompress(byte[] message){
		if(message==null) return null;
		DeflaterOutputStream dos;
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	
    	Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		dos = new DeflaterOutputStream(baos,deflater);
		try {
			dos.write(message, 0, message.length);
			dos.finish();
		} catch (IOException e) { }

		/*GZIPOutputStream gos;
		try {
			gos = new GZIPOutputStream(baos);
			gos.write(message, 0, message.length);
			gos.finish();
		} catch (IOException e) { }*/
		
		if(baos.size() <= 1) {
			Log.d(TAG, "Message is created but not compressed.");
			return null;
		}
		return baos.toByteArray();
	}
	
	protected static byte[] defaultCompress(String messageStr){
		if(messageStr==null) return null;
    	byte[] message=messageStr.getBytes();
    	return defaultCompress(message);
	}
	/*
	 * Implement according to defaultCompress
	 * @param input
	 */
	protected static byte[] defaultDecompress(byte[] input) throws Exception{
		InflaterInputStream ios;
		ByteArrayInputStream cis=new ByteArrayInputStream(input);;
		ByteArrayOutputStream baos;
		//String temp;
		Inflater inflater = new Inflater();
		ios = new InflaterInputStream(cis,inflater);
		baos=new ByteArrayOutputStream();
		byte[] buffer = new byte[1000];
	    int len;
	    while((len = ios.read(buffer)) > 0) {
	        baos.write(buffer, 0, len);
	    }
		if(baos.size() <= 0) throw new Exception("Nothing is decompressed.");
		return baos.toByteArray();
		//temp=new String(baos.toByteArray());
		//return temp;
	}
	
//Part 4: decoding the Authenticated 2D barcode according to its type
	protected boolean isVerified=false;
	public boolean isBarcodeVerified(){
		return isVerified;
	}
	abstract public boolean decodeBarcodeByte(byte[] encodedArray, KeyStore store) throws Exception;
	abstract public boolean decodeBarcodeString(String str, KeyStore store) throws Exception;
	
	public static boolean storeCertificate(KeyStore store, X509Certificate cert) throws KeyStoreException{
		String alias=getCertificateSubjectCN(cert);
		store.setCertificateEntry(alias, cert);
		return true;
	}
	
//Part 5: Certificate related functions	
	//public static final String signerCert = "client1 public certificate";
	//public static boolean setDefaultCert(KeyStore trustStore, InputStream defaultCert){
		//return storeCertificate(trustStore, defaultCert, signerCert);
	//}
	public static boolean storeCertificate(KeyStore trustStore, InputStream inputCert){
		return storeCertificate(trustStore, inputCert, "");
	}
	
	// remove certificate from keystore given an alias
	public static boolean removeCertificate(KeyStore trustStore, String alias) {
		if(trustStore == null || alias == null) return false;
		try {
			trustStore.deleteEntry(alias);
			return true;
		} catch (KeyStoreException e) {
			//Log.e(TAG, e.getMessage());
			//e.printStackTrace();
			return false;
		}			
	}
	
	// store certificate from input stream
	public static boolean storeCertificate(KeyStore trustStore, InputStream inputCert, String alias){
		if(trustStore ==null || inputCert ==null) return false;
		X509Certificate cert = getCertificate(inputCert);
		if (cert != null) {
			if(alias ==null || alias.isEmpty()) alias=getCertificateSubjectCN(cert);
			if(alias ==null || alias.isEmpty()) return false;
			try {
				trustStore.setCertificateEntry(alias, cert);			
				// populate db entry
				certDbEntry = new CertificateDbEntry(0, alias, "", cert.getNotAfter().getTime(), cert.getNotBefore().getTime());
				return true;				
			} catch (KeyStoreException e) {
				//Log.e(TAG, e.getMessage());
				//e.printStackTrace();
				return false;
			}
		}
		else {
			return false;
		}
	}

	// get database record from certificate input stream
	public static CertificateDbEntry convertCrtToCrtDb(X509Certificate inputCert) {
		if (inputCert == null) return null;
		String alias = "";
		if(alias ==null || alias.isEmpty()) alias=getCertificateSubjectCN(inputCert);
		if(alias ==null || alias.isEmpty()) alias = "Unknown";
		CertificateDbEntry crtDbEntry = new CertificateDbEntry(0, alias, "", inputCert.getNotAfter().getTime(), inputCert.getNotBefore().getTime());
		return crtDbEntry;			
	}
	
	// get X509 certificate from certificate input stream
	public static X509Certificate getCertificate(InputStream inputCert) {
		try {
			CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
			X509Certificate cert = (X509Certificate) certFactory.generateCertificate(inputCert);
			return cert;
		}catch(CertificateException e2){
			Log.e(TAG, "Certificate error");
			Log.e(TAG, e2.getMessage());
			e2.printStackTrace();			
			return null;
		}		
	}
	
	/**
	 * Returns certificate information for LAST SAVED entry in keystore  
	 */
	public static CertificateDbEntry getCertDbEntry() {
		return certDbEntry;
	}
	
	public static X509Certificate getCertificate(KeyStore store, String subjectDN){
		/* TODO: how to find the appropriate certificate from the key store using the subjectDN data
		 */
		X509Certificate senderCert;
		if("C=HK,ST=Hong Kong,L=hong kong,O=solon test lab,OU=auth2dbarcode,CN=client1,E=badPeople@hotmail.com"
			.compareTo(subjectDN) ==0) subjectDN="client1(solon.android@gmail.com)";
		try{
			senderCert = (X509Certificate) store.getCertificate(subjectDN);			
			try{
				if(senderCert !=null) senderCert.checkValidity();
			}catch (CertificateExpiredException e){
				//Does not expire the default certificate for backward compatibility
				if(subjectDN.compareTo("client1(solon.android@gmail.com)") !=0)
					senderCert=null;
			} catch (CertificateNotYetValidException e) { }
			if(senderCert !=null) return senderCert;
			//It may be the case that the stored certificate has alias CN(email) but the subjectDN in the 2D barcode is CN
			//i.e. subjectDN+'('+email+')' : subjectDN Remove the appended email if exists
			String cnEmail="";
			Pattern p = Pattern.compile("\\([\\w=+\\-\\/][\\w='+\\-\\/\\.]*@[\\w\\-]+(\\.[\\w\\-]+)*(\\.[\\w]{2,6})\\)");
			Matcher m = p.matcher(subjectDN);
			if(m.find()){
				cnEmail=m.group();				
				subjectDN=m.replaceAll("");			
			}

			//If the subject DN is not used as alias, we need to loop though the key store to find the certificate
			Enumeration<String> aliases = store.aliases();
	        while(aliases.hasMoreElements()) {
	            String alias = (String) aliases.nextElement();
	            if(store.isCertificateEntry(alias)){
	            	X509Certificate potentialCert = (X509Certificate) store.getCertificate(alias);
	            	try {
						potentialCert.checkValidity();
					} catch (CertificateExpiredException e) {
						continue;
					} catch (CertificateNotYetValidException e) { }
	            	//TODO: Should be getCertificateSubjectCN(potentialCert).compareTo(subjectDN)==0, this is for compatibility reason 
	            	if(potentialCert.getSubjectX500Principal().getName().contains(subjectDN)){
	            		if(cnEmail.isEmpty()) return potentialCert;	            		
            			String certEmail=getCertificateEmail(potentialCert);
            			if(certEmail !=null && cnEmail.contains(certEmail)) return potentialCert;
	            	}
	            }
	        }
	        Log.d(TAG, "Fail to get a certificate, return null");
			//return (X509Certificate) store.getCertificate(signerCert);			
		} catch (KeyStoreException e) { } //Something wrong on the key store
		//catch (CertificateExpiredException e) { } 
		//catch (CertificateNotYetValidException e) { }
		return null;
	}
	public X509Certificate getSenderCert(){
		return this.senderCert;
	}
	/**
	 * Verify the digital certificate embedded in the 2D barcode (embededCert) using certificates in the given key store
	 * Or return the digital certificate in the given key store according to the given subjectDN (if embededCert is null or not verified)
	 * Embeded digital certificate will be saved into this.senderCert, if it is not disproved by certificates in the trustStore 
	 * @param embededCert
	 * @param trustStore
	 * @param subjectDN
	 * @return embededCert (if it is verified by the cert in trustStore) or the cert in trustStore identified by the subjectDN
	 * null if embededCert is null/not verified and also no digital cert specified by the subjectDN is found
	 */
	protected X509Certificate getVerifiedCertificate(byte[] embededCert, KeyStore trustStore, String subjectDN){
		//return getCertificate(trustStore,subjectDN);
		if(trustStore ==null) return null;
		if( embededCert==null && (subjectDN==null || subjectDN.isEmpty()) ) return null;
		X509Certificate issuerCert=null;
		if(subjectDN !=null && !subjectDN.isEmpty()) issuerCert=getCertificate(trustStore,subjectDN);
		//If no embeded cert, just return the one found in trustStore using subjectDN (may be null)
		if(embededCert==null) return issuerCert;
		//Log.d(TAG,"Use embedded cert");
		InputStream in = new ByteArrayInputStream(embededCert);
		try {
			CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
			X509Certificate cert = (X509Certificate) certFactory.generateCertificate(in);
			try{
				if(cert !=null) cert.checkValidity();
			}catch(CertificateExpiredException e) {
				//TODO: how to handle expired certificate?
				//cert=null;
			} catch (CertificateNotYetValidException e) { }
			try {
				//If the embeded cert is already stored in the key store, it is trusted. So just return it
				if(trustStore.getCertificateAlias(cert) !=null) return cert; 
			} catch (KeyStoreException e2) { }
			//Save the embededCert for later use, no matter it can be verified or not
			this.senderCert=cert;
			//Find the certificate of the embeded cert issuer from key store
			String issuerDN="";
			try{
				issuerDN= (cert !=null)? getCertificateIssuerCN(cert):subjectDN;
			}catch(Exception e3){
				//Self-signed certificate may not have this field.....
				issuerDN=subjectDN;
			}
			issuerCert=getCertificate(trustStore,issuerDN);
			if(issuerCert !=null){
				try{
					int pathLengthConstant=issuerCert.getBasicConstraints();
					if(pathLengthConstant <0 || pathLengthConstant > Integer.MAX_VALUE-1){
						//The embedded certificate is issued by a trusted non-CA party (issuerCert). It may be fake
						//TODO: Handle this case
						return null;
					}
					PublicKey certKey=issuerCert.getPublicKey();
					if(cert !=null) cert.verify(certKey);
					//Log.d(TAG,"Embedded cert verified");
					//After verifying, use the embedded cert to verify the content
					return cert;
				}catch (CertificateException e2) { }
				catch (InvalidKeyException e2) { }
				catch (NoSuchAlgorithmException e2) { }
				catch (NoSuchProviderException e2) { } 
				catch (SignatureException e2) { 
					//There is proper issuer certificate, but cannot verify the certificate. It may be fake.					
					this.senderCert=null;
				}
			}
		} catch (CertificateException e) { }
		return issuerCert;
	}
	/**
	 * Return the subject email of the input certificate if found, false otherwise.
	 * @param cert
	 * @return
	 */
	public static String getCertificateEmail(X509Certificate cert){
		if(cert ==null) return null;
		String subjectDN=cert.getSubjectX500Principal().getName();
		return getCertificateEmailFromName(subjectDN);
	}
	public static String getCertificateIssuerEmail(X509Certificate cert){
		if(cert ==null) return null;
		String subjectDN=cert.getIssuerX500Principal().getName();
		return getCertificateEmailFromName(subjectDN);
	}
	private static String getCertificateEmailFromName(String subjectDN){
		if(subjectDN ==null || subjectDN.isEmpty()) return null;
		String email="";
		//Log.d(TAG, "The subject is "+subjectDN);
		Pattern p = Pattern.compile("emailAddress=[\\w=+\\-\\/][\\w='+\\-\\/\\.]*@[\\w\\-]+(\\.[\\w\\-]+)*(\\.[\\w]{2,6})");
		Matcher m = p.matcher(subjectDN);
		while(email.isEmpty() && m.find()){ // Find each match in turn
			email = m.group(0);
		}		
		if(email.isEmpty()){
			//It is ASN 1 encoded
			p = Pattern.compile("1.2.840.113549.1.9.1=#(\\S+),");
			m = p.matcher(subjectDN);
			while(email.isEmpty() && m.find()){ // Find each match in turn
				email = m.group(1);
			}
			if(!email.isEmpty() && (email.length() %2) !=1){				
				try{
					//Hex to binary convertion
					int len = email.length();
				    byte[] data = new byte[len / 2];
				    for(int i=0; i<len; i+=2){
				        data[i/2] = (byte) ((Character.digit(email.charAt(i), 16) << 4)
				                             + Character.digit(email.charAt(i+1), 16));
				    }
					ASN1InputStream s = new ASN1InputStream(new ByteArrayInputStream(data));
				    ASN1String str=(ASN1String) s.readObject();
					email=str.getString();
					s.close();
				}catch(Exception e){ email=""; }
			}
		}
		return (email.isEmpty())? null : (email.startsWith("emailAddress="))? email.substring(13) : email;
	}
	/**
	 * Return the alternative name of the input certificate or subject CN if not found
	 * @param cert
	 * @return null if cert is null or the subject DN cannot be found
	 */
	public static String getCertificateSubjectName(X509Certificate cert){
		if(cert ==null) return null;
		try {
			Collection<List<?>> altNames = cert.getSubjectAlternativeNames();
			if(altNames !=null){
				for(List<?> item : altNames) {
	                //TODO: Question: What is the usage of the type of a alternative name???
					try{
						int type = Integer.parseInt(item.toArray()[0].toString());
						String value = item.toArray()[1].toString();
						//If it is saved as otherName or URI according to the X509Certificate standard, then return it
						if( (type ==GeneralName.otherName || type ==GeneralName.uniformResourceIdentifier) && 
								(value !=null && !value.isEmpty()) ) return value;
					} catch(NumberFormatException e2){ }
				}
			}
		} catch (CertificateParsingException e) { }
		//If the alternative names are not supported, read it manually
		if(cert.getExtensionValue("2.5.29.17") !=null){
			byte[] octets=ASN1OctetString.getInstance(cert.getExtensionValue("2.5.29.17")).getOctets();
			try{
				Enumeration<?> it = DERSequence.getInstance(ASN1Primitive.fromByteArray(octets)).getObjects();
				while(it.hasMoreElements()){
					GeneralName genName = GeneralName.getInstance(it.nextElement());
					int type = genName.getTagNo();
					String value = genName.getName().toString();
					//If it is saved as otherName or URI according to the X509Certificate standard, then return it
					if( (type ==GeneralName.otherName || type ==GeneralName.uniformResourceIdentifier) && 
							(value !=null && !value.isEmpty()) ) return value;
				}
			} catch (IOException e2){ }
		}
		//return cert.getSubjectX500Principal().getName();
		return getCertificateSubjectCN(cert);
	}
	/**
	 * Return the CN part of the subject of the input certificate or subject DN if not found
	 * @param cert
	 * @return null if cert is null or the subject DN cannot be found
	 */
	public static String getCertificateSubjectCN(X509Certificate cert){
		if(cert == null) return null;
		String subjectDN = cert.getSubjectX500Principal().getName();
		String email=getCertificateEmail(cert);
		subjectDN = extractCertificateCN(subjectDN, email);
		return subjectDN;
	}
	
	public static String getCertificateIssuerCN(X509Certificate cert) {
		if(cert == null) return null;
		String subjectDN = cert.getIssuerX500Principal().getName();		
		String email=getCertificateIssuerEmail(cert);
		subjectDN = extractCertificateCN(subjectDN, email);
		return subjectDN;
	}
	
	// common method to get issued to/by name out of cert
	private static String extractCertificateCN(String subjectDN, String email)
	{
		if(subjectDN ==null || subjectDN.isEmpty()) return null;
		String startField="CN=";
		int searchLength=subjectDN.length()-startField.length()-1;
		int startIndex=subjectDN.lastIndexOf(startField,searchLength); 
		//Cannot find the CN
		if(startIndex<0) return subjectDN;
		ArrayList<String> endFields = new ArrayList<String>();
		endFields.addAll(Arrays.asList(",OU=", ",O=", ",L=", ",ST="));	
		for(int i=0, endIndex=-1;i<endFields.size();i++){
			endIndex = subjectDN.lastIndexOf(endFields.get(i),searchLength);
			if(endIndex>startIndex){
				subjectDN =subjectDN.substring(startIndex+startField.length(), endIndex);
				return (email !=null)? subjectDN+'('+email+')' : subjectDN;				
			}
		}
		//If everything failed, return the subject DN
		return subjectDN;
	}
	
	
	
	protected static boolean verifySignature(byte[] messageToSign, byte[] signature, String signatureMethod, X509Certificate issuerCert){
		if(messageToSign==null || messageToSign.length<1 || signature ==null || signature.length<1 
				||signatureMethod ==null || signatureMethod.isEmpty() || issuerCert ==null) return false;
		try{
			Signature signatureAgent=getSignatureAgent(signatureMethod);
			signatureAgent.initVerify(issuerCert);
			signatureAgent.update(messageToSign);			
			if(signatureAgent.verify(signature)) return true;
			else return false;
		}catch (InvalidKeyException e2) { } 
		catch (SignatureException e2) { } 
		return false;
	}
	protected static byte[] createSignature(byte[] messageToSign, PrivateKey signerKey, String signatureMethod){
		if(messageToSign==null || messageToSign.length<1 || signerKey ==null
				||signatureMethod ==null || signatureMethod.isEmpty()) return null;
		try {
			Signature signatureAgent=getSignatureAgent(signatureMethod);
			signatureAgent.initSign(signerKey);
			signatureAgent.update(messageToSign);
			return signatureAgent.sign();
		}catch (InvalidKeyException e) {} 
		catch (SignatureException e) {} 
		return null;
	}
	private static Signature getSignatureAgent(String signatureMethod){
		if(signatureMethod==null || signatureMethod.isEmpty()) return null;
		Signature signatureAgent=null;
		try{
			signatureAgent=Signature.getInstance(signatureMethod, "BC");
		}catch(NoSuchProviderException e){ }
		catch(NoSuchAlgorithmException e){ }
		if(signatureAgent !=null) return signatureAgent;
		try {
			signatureAgent=Signature.getInstance(signatureMethod, "SC");					
		} catch (NoSuchProviderException e2){ }
		catch(NoSuchAlgorithmException e2){ }
		if(signatureAgent !=null) return signatureAgent;
		try {
			signatureAgent=Signature.getInstance(signatureMethod);
		} catch (NoSuchAlgorithmException e){ }
		return signatureAgent;
	}
	
//Other functions	
	protected static JSONObject rebuildJSON(JSONObject oldOne) throws JSONException{
	   JSONObject newOne=new JSONObject();
	   Iterator<?> keys=oldOne.keys();
	   String index=null;
	   while(keys.hasNext()){
		   index=(String) keys.next();
		   newOne.putOpt(index, oldOne.opt(index));
	   }
	   return (newOne.length()>0)? newOne:null;
	}
	
	//protected static void Log(String message){
		//System.out.println(TAG+": "+message);
		//android.util.Log.d(TAG, message);
	//}
}
