package desktopCreator;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;

import edu.cuhk.ie.authbarcode.Auth2DbarcodeDecoder;
import edu.cuhk.ie.authbarcode.AuthBarcodeObjBSON;
import edu.cuhk.ie.authbarcode.AuthBarcodePlainText;


/**
 * This class stores the private key, digital certificate and other variables/functions related to digital signature creation
 * @author Solon li 2014
 *
 */
public class KeyCertContainer{
	private static final String TAG = KeyCertContainer.class.getSimpleName();
	
	private PrivateKey senderKey=null;
	private String signatureAlgorithm=null;
	private X509Certificate senderCert=null;
	
	static {
		Security.addProvider(new BouncyCastleProvider());
	}
	public KeyCertContainer(){
		
	}
	
	
//Part 2: saving and parsing the private key and digital certificate 	
	/**
	 * Given a PrivateKeyInfo or a PEMEncryptedKeyPair with password, extract the private key and save it
	 * TODO: Create a database to save the frequently used private key and its password. 
	 */
	public boolean savePrivateKey(PEMEncryptedKeyPair key, String password){
		if(key ==null || password ==null || password.isEmpty()) return false;
		PEMDecryptorProvider decryptionProv = new JcePEMDecryptorProviderBuilder().build(password.toCharArray());
		PEMKeyPair decryptedKeyPair;
		try {
			decryptedKeyPair = key.decryptKeyPair(decryptionProv);
			PrivateKeyInfo keyInfo = decryptedKeyPair.getPrivateKeyInfo();
			return savePrivateKey(keyInfo);
		} catch (IOException e) { }
		return false;
	}
	public boolean savePrivateKey(PrivateKeyInfo keyInfo){
		if(keyInfo ==null) return false;
		JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
	    try {
			senderKey=converter.getPrivateKey(keyInfo);
			if(senderKey!=null){
				signatureAlgorithm=null;
				String keyType=senderKey.getAlgorithm();
				signatureAlgorithm=getAvailableSignAlgo(senderKey, keyType);
				if(signatureAlgorithm!=null) return true;
			}
		} catch (IOException | InvalidKeyException | NoSuchAlgorithmException e) { 
			Log("Error on getting the private key for signature : "+e.getMessage());
		}
	    if(signatureAlgorithm==null) senderKey=null;
	    return false;
	}
	public boolean savePrivateKey(PrivateKey key, String algorithm){
		if(key ==null || algorithm ==null || algorithm.isEmpty()) return false;
		senderKey=key;
		signatureAlgorithm=null;
		if(algorithm.compareTo("EC")==0){
			try {
				signatureAlgorithm=getAvailableSignAlgo(senderKey, "ECDSA");
				if(signatureAlgorithm!=null) return true;
			} catch (InvalidKeyException | NoSuchAlgorithmException e) { 
				Log("Error on getting the private key for signature : "+e.getMessage());
			}
		}
		if(signatureAlgorithm==null) senderKey=null;
	    return false;
	}
	private static String getAvailableSignAlgo(PrivateKey privateKey, String keyType) throws InvalidKeyException, NoSuchAlgorithmException{
		if(keyType==null || keyType.isEmpty()) return null;
		Signature signedObj=null;
		//Select the suitable signature algorithm with largest possible SHA hash function
		//TODO: A little better way to do it?
		if(keyType.compareTo("RSA")==0 || keyType.compareTo("DSA")==0 || keyType.compareTo("ECDSA")==0) {
			try {
				signedObj=Signature.getInstance("SHA512with"+keyType);
				signedObj.initSign(privateKey);
			} catch (NoSuchAlgorithmException | InvalidKeyException e) {
				try{
					signedObj=Signature.getInstance("SHA384with"+keyType);
					signedObj.initSign(privateKey);
				} catch (NoSuchAlgorithmException | InvalidKeyException e1) {
					try{
						signedObj=Signature.getInstance("SHA256with"+keyType);
						signedObj.initSign(privateKey);
					} catch (NoSuchAlgorithmException | InvalidKeyException e2) {
						try{
							signedObj=Signature.getInstance("SHA1with"+keyType);
							signedObj.initSign(privateKey);
						} catch (NoSuchAlgorithmException | InvalidKeyException e3) { }
					}
				}
			}
		}
		if(signedObj==null){
			signedObj=Signature.getInstance(keyType);
			signedObj.initSign(privateKey);
		}
		return (signedObj!=null)? signedObj.getAlgorithm():null;
	}
	public PrivateKey getPrivateKey(){
		return senderKey;
	}
	public String getSignatureAlgorithm(){
		return signatureAlgorithm;
	}
	public boolean saveCertificate(List<X509CertificateHolder> certList){
		if(certList ==null || certList.isEmpty()) return false;
		//Here we did not verify the digital certificate, we only take the first certificate as the issuer certificate
		//TODO: verify the selected digital certificate (which may be a chain of certificate) and make sure it matches the selected private key
		try {
			senderCert=new JcaX509CertificateConverter().getCertificate(certList.get(0));
			return (senderCert !=null)? true:false;
		} catch (CertificateException e2) { }
		return false;
	}
	public X509Certificate getCertificate(){
		return senderCert;
	}
	public String getCertificateSubjectName(){
		return (senderCert!=null)? Auth2DbarcodeDecoder.getCertificateSubjectName(senderCert):"";
/*		if(senderCert !=null){
			String certName=Auth2DbarcodeDecoder.getCertificateSubjectName(senderCert);
			if(certName.compareTo(getCertificateSubjectDN())==0 && senderCert.getExtensionValue("2.5.29.17") !=null ){
				//There exists subject alternative name but the default parser cannot read it, then get it manually
				certName="";
				byte[] octets=ASN1OctetString.getInstance(senderCert.getExtensionValue("2.5.29.17")).getOctets();
				try{
					Enumeration it = DERSequence.getInstance(ASN1Primitive.fromByteArray(octets)).getObjects();
					while(it.hasMoreElements()){
						GeneralName genName = GeneralName.getInstance(it.nextElement());
						if(genName.getTagNo() == GeneralName.otherName || genName.getTagNo() == GeneralName.uniformResourceIdentifier){
							certName=genName.getName().toString();
							break;
						}
					}
				}catch (IOException e2){ }
			}
			if(certName!=null && !certName.isEmpty()) return certName;
		}
		//if everything failed, just return the subject DN
		Log("No subject alternative name is found");
		return getCertificateSubjectDN();
*/
	}
	/**
	 * Get the DN of the subject of the selected digital certificate
	 * @return
	 */
	public String getCertificateSubjectDN(){
		return senderCert.getSubjectX500Principal().getName();
	}
	/**
	 * Get the DN of the issuer of the digital certificate
	 * @return
	 */
	public String getCertificateIssuerDN(){
		return senderCert.getIssuerX500Principal().getName();
	}
	
	
//Part 3: giving an Autheenticated 2D barcode creation object 	
	public Auth2DbarcodeDecoder getBarcodeCreatorObj(boolean isTextOnly){
		if(getPrivateKey() ==null || getSignatureAlgorithm() ==null || getCertificate()==null) return null;
		Auth2DbarcodeDecoder barcode = (!isTextOnly)? 
				new AuthBarcodeObjBSON(getPrivateKey(), getCertificate()):  //If the user input data consists of non-text content 
				new AuthBarcodePlainText(getPrivateKey(), getCertificate()); //If the user input data consists of text only
		try {
			if(barcode.setSignatureMethod(getSignatureAlgorithm(),"SC")) {
				barcode.setCompressMethod("ZLIB");
				return barcode;
			}
		} catch (NoSuchAlgorithmException e) { }
		return null;
	}
	
	private void Log(String message){
		System.out.println(TAG+": "+message);
	}
}