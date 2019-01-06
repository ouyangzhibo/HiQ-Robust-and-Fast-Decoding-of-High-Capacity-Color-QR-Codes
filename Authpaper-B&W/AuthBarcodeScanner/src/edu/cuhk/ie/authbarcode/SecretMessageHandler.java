/*
 * Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcode;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.crypto.PBEParametersGenerator;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.spongycastle.crypto.params.KeyParameter;

public class SecretMessageHandler{
	public static final String TAG=SecretMessageHandler.class.getSimpleName();
	private static String messageBorder="#--#";
	private String salt;
	private byte[] iv;
	private byte[] ciphertext;
	
	public SecretMessageHandler(String secretObj){		
		if(secretObj !=null && !secretObj.isEmpty()){
			try{
				JSONObject secretJSON=new JSONObject(secretObj);
				String cipherStr=(secretJSON.has("ciphertext"))? secretJSON.getString("ciphertext") 
						: (secretJSON.has("c"))? secretJSON.getString("c") :"";
				String ivStr=(secretJSON.has("iv"))? secretJSON.getString("iv") 
						: (secretJSON.has("i"))? secretJSON.getString("i") :"";
				String Salt=(secretJSON.has("salt"))? secretJSON.getString("salt") 
					: (secretJSON.has("s"))? secretJSON.getString("s") :"";				
				if(!cipherStr.isEmpty() && !ivStr.isEmpty() && !Salt.isEmpty()){
					byte[] cipherByte=AuthBarcodePlainText.base64Decode(cipherStr);
					byte[] ivByte=AuthBarcodePlainText.base64Decode(ivStr);
					if(cipherByte !=null && ivByte !=null){
						ciphertext=cipherByte;
						iv=ivByte;
						salt=Salt;							
						return;
					}
				}
			}catch(JSONException e){}
		}		
		salt=null;
		iv=null;
		ciphertext=null;
	}
	
	public boolean isSecretExist(){		
		return (salt !=null && !salt.isEmpty() && ciphertext !=null && ciphertext.length>0);
	}
	
	public String decryptMsg(String password){
		/* The code to do the encryption
		 * if(!$secretMsg || !$secretMsgPin){
			//Code copied from php.net
			$secretSalt=keyMag\pwSalt();
			$secretKey=pack('H*',hash_pbkdf2("sha256", $secretMsgPin, $secretSalt, 1000));
			//Encrypt using AES-256 / CBC / ZeroBytePadding
			$iv_size = mcrypt_get_iv_size(MCRYPT_RIJNDAEL_128, MCRYPT_MODE_CBC);
			$iv = mcrypt_create_iv($iv_size, MCRYPT_RAND);
			//Add header and tail to avoid ending with zeros
			//TODO:How to properly prevent padding oracle attacks?
			$ciphertext = mcrypt_encrypt(MCRYPT_RIJNDAEL_128, $key,
				"#--#$secretMsg#--#", MCRYPT_MODE_CBC, $iv);
			if($ciphertext){
				$ciphertext = $iv . $ciphertext;
				$ciphertext = base64_encode($ciphertext);
				if($submitData) $submitData['secretMsg']=json_encode(
					array('salt' => $secretSalt,'ciphertext' => $ciphertext));
			}
		}
		 */
		
    	//First derive the secret key back
    	byte[] secretKeyByte=null;	    	
		try{
			SecretKeyFactory secretKeyFactory =SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256", "BC");
			KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 1000, 256);
			SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
			secretKeyByte=secretKey.getEncoded();
		}catch(NoSuchProviderException e){ }
		catch(NoSuchAlgorithmException e){ }
		catch(InvalidKeySpecException e){ }
		if(secretKeyByte ==null){
			PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator(new SHA256Digest());
			generator.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password.toCharArray()), 
					salt.getBytes(), 1000);
			KeyParameter key = (KeyParameter) generator.generateDerivedParameters(256);
			secretKeyByte=key.getKey();
		}
	    if(secretKeyByte ==null) return null;
		try{			
		    //Then try to decrypt the message
			SecretKeySpec skeySpec = new SecretKeySpec(secretKeyByte, "AES");
			Cipher cipher = null;
			try{
	        	cipher=Cipher.getInstance("AES/CBC/ZeroBytePadding", "SC");
	        }catch (NoSuchAlgorithmException e){	        	
	        	cipher=Cipher.getInstance("AES/CBC/ZeroBytePadding");
			}catch (NoSuchProviderException e){	        	
	        	cipher=Cipher.getInstance("AES/CBC/ZeroBytePadding");
			}
	        cipher.init(Cipher.DECRYPT_MODE, skeySpec, new IvParameterSpec(iv));
	        byte[] decrypted = cipher.doFinal(ciphertext);
	        String secretStr=new String(decrypted);
	        if(secretStr.startsWith(messageBorder) && secretStr.endsWith(messageBorder)
	        	&& secretStr.length() > (2*messageBorder.length()) )
	        	return secretStr.substring(messageBorder.length(), 
	        			secretStr.length()-messageBorder.length());
	        else return null;
		} catch (IllegalBlockSizeException e) {
		} catch (BadPaddingException e) {			
		} catch (NoSuchPaddingException e) {
		} catch (InvalidKeyException e) {			
		} catch (InvalidAlgorithmParameterException e){
		} catch (NoSuchAlgorithmException e){	
			android.util.Log.d(TAG, "NoSuchEncryptionAlgo");
		}
		return null;
	}
	
}