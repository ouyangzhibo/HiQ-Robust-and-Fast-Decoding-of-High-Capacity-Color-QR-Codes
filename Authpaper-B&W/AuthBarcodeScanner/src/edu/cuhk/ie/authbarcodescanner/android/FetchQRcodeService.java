package edu.cuhk.ie.authbarcodescanner.android;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;

import edu.cuhk.ie.authbarcode.AuthBarcodePlainText;
import edu.cuhk.ie.authbarcodescanner.android.result.webViewHandler.trustSocketFactory;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.widget.Toast;

public class FetchQRcodeService{
	private static final String TAG = "FETCHQRCODE";	
	private static String uploadURL="https://www.authpaper.net/qrCreationExternal-process.php?action=signQR";
	public static Result fetchQRcodeResult(Context context,String accesstoken,String text){
		if(accesstoken ==null || text ==null || accesstoken.isEmpty() || text.isEmpty())
       	 	return null;
		JSONObject inputs = new JSONObject();
		try{
			inputs.putOpt("aT", accesstoken).putOpt("t", text);
		}catch(org.json.JSONException e){
       	 	return null;
		}
		//Upload the data
		javax.net.ssl.HttpsURLConnection urlConnection = trustSocketFactory.getSSLConnection(context, uploadURL);
        if(context ==null || urlConnection ==null) return null;    
        try{
       	 	//Package the data as a form and then upload
       	 	String lineEnd = "\r\n";
    		String twoHyphens = "--";
    		String boundary = "*****";
       	 	urlConnection.setDoInput(true);
       	 	urlConnection.setDoOutput(true);
       	 	urlConnection.setUseCaches(false);
       	 	urlConnection.setChunkedStreamingMode(0); //Use system default value
       	 	urlConnection.setRequestMethod("POST");
       	 	urlConnection.setRequestProperty("Accept-Charset", "UTF-8");
	        urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
	        urlConnection.setRequestProperty("Accept", "application/json");
	        urlConnection.setRequestProperty("Connection", "Keep-Alive");		         
	        urlConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
	        DataOutputStream outputStream = new DataOutputStream(urlConnection.getOutputStream());
			outputStream.writeBytes(twoHyphens + boundary + lineEnd);         		         
			outputStream.writeBytes("Content-Disposition: form-data; name=\"result\"" + lineEnd);
			outputStream.writeBytes(lineEnd);
			outputStream.write(inputs.toString().getBytes("UTF-8")); //To support UTF format				 
			//outputStream.writeBytes(inputs.toString());
			outputStream.writeBytes(lineEnd);
			outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
			outputStream.flush();
			outputStream.close();
	        int statusCode = urlConnection.getResponseCode();
	        /* 200 represents HTTP OK */
	        if(statusCode == 200){
	        	Log.d(TAG, "Get return from the server for signed QR code");
	        	 InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
	        	 String response=UpdateDigitalCertService.convertInputStreamToString(inputStream);
	        	 Log.d(TAG,response);
	        	 if(response ==null || response.isEmpty() || !response.startsWith("while(1);"))
	        		 return null;
	        	 try{
	        		 JSONObject result = new JSONObject(response.replace("while(1);", ""));
	        		 String codeStr=result.getString("authCode");
	        		 byte[] codeByte=AuthBarcodePlainText.base64Decode(codeStr);
	        		 if(codeByte ==null || codeByte.length <10) throw new JSONException("Cannot decode image");
	        		 Bitmap code=BitmapFactory.decodeByteArray(codeByte, 0, codeByte.length);
	        		 if(code ==null) throw new JSONException("Cannot decode image");
	        		 codeStr="";codeByte=null;
	        		 System.gc();
	        		 Log.d(TAG, "Start decoding the image from server");
	        		 Map<DecodeHintType,Object> hints = new EnumMap<DecodeHintType,Object>(DecodeHintType.class);
	       			 hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(BarcodeFormat.QR_CODE));
	       			 hints.put(DecodeHintType.TRY_HARDER, true);
	       			 Result rawResult=edu.cuhk.ie.authbarcodescanner.android.decodethread
	      					.DecodeThreadHandler.fileDecode(code,hints,context,ScannerFragment.loadQDA(context));  
	       			code.recycle();
	       			if(rawResult ==null)
	       				Log.d(TAG, "What can we do if the scanner cannot scan the QR code from server =_=");
	      			return rawResult;
	        	 }catch(JSONException e){
	        		 Log.d(TAG,"Strange result from server for signing QR codes"+response);
	        		 return new Result(response, null, null, null,null);
	        	 }
	        }else Log.d(TAG,"Cannot post data for signed QR code with error code : "+statusCode);
        }catch (IOException e) {
       	 	Log.d(TAG,"Cannot post data to the server");
       	 	return null;
        }
        urlConnection.disconnect();
   	 	return null;
	}

}
