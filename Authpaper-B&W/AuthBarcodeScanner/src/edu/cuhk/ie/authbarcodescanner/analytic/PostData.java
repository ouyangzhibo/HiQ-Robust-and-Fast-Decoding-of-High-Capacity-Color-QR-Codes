/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.ProtocolException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import edu.cuhk.ie.authbarcodescanner.android.SendService;
import edu.cuhk.ie.authbarcodescanner.android.result.webViewHandler;

public class PostData {
	private static final String TAG = PostData.class.getSimpleName();
	
	// context
	Context mContext;
	
	// DEBUG
	// Disable send if DEBUG_MODE enabled
	private boolean DEBUG_MODE = false;
	
	// HTTP clients 
	HttpClient httpClient;
	HttpPost httpPost;
	
	// post content and subject
	private String postRecvURL;
	private String postScanStatURL;
	private String _subject; 
	private String _content; 
	private ArrayList<Uri> _attachment;
	private String _fileAttach;
	private boolean hasAttachment = false;
	
	
	// flag to differentiate between different new user, new camera, camera stat etc
	private int _type;
	// flag to indicate if mail sent or sent failed
	private boolean _postSent;	

	public PostData(Context context) {
		this.mContext = context;
		// url to handle data
		postRecvURL = "https://authpaper.in/analytics/android_recv.php";
		postScanStatURL = "https://authpaper.in/analytics/android_scanstat.php";		
		// Development only
		httpClient = getNewHttpClient();		
		// post header
		httpPost = new HttpPost(postRecvURL);
	}
	
	// Set callback type
	public void setType(int type) {
		this._type = type;
	}
	public int getPostType() {
		return _type;
	}
	
	// Set subject so server can recognise message type
	public void setSubject (String subject) {
		this._subject = subject;
	}
	
	// Form content, typically JSON string
	public void setContent (String content) {
		this._content = content;
	}
	
	// Attachment list
	public void setAttach (ArrayList<Uri> attachmentList) {
		this._attachment = attachmentList;
		if(this._attachment != null && this._attachment.size() > 0) 
			this.hasAttachment = true;		
		else this.hasAttachment = false;
	}
	
	// Name of file to be sent as binary data
	public void setFileAttach (String file) {
		this._fileAttach = file;
	}
	
	// Sent status
	public void setPostSent(boolean mailStatus) {
		this._postSent = mailStatus;
	}	
	public boolean getPostSent() {
		return _postSent;
	}
	
	private byte[] readFile(File file) throws IOException {
		RandomAccessFile f = new RandomAccessFile(file, "r");
		try {
			long length = f.length();
			int int_len = (int) length;
			if(length != int_len)
				throw new IOException("File size >= 2GB");
			byte[] data = new byte[int_len];
			f.readFully(data);
			return data;
		} finally {
			f.close();
		}
	}
	
	// Common method to process server response
	public String processResponse(HttpResponse response) {
		try {
			InputStream inStream = response.getEntity().getContent();
			InputStreamReader insReader = new InputStreamReader(inStream);
			BufferedReader bReader = new BufferedReader(insReader);
			StringBuilder strBld = new StringBuilder();
			
			String strChunk = null;
			while((strChunk = bReader.readLine()) != null) {
				strBld.append(strChunk);
				strBld.append("\n");
			}
			insReader.close();
			
			Log.d(TAG, "---------------");
			Log.d(TAG, response.getStatusLine().toString());
			Log.d(TAG, "---------------");
		
			return strBld.toString();
		}
		catch (IllegalStateException e) {
			Log.e(TAG, "Processing encountered illegal state exception");
			return e.getMessage();
		}
		catch(IOException e) {
			Log.e(TAG, "Processing encountered io exception");
			return e.getMessage();
		}
	}

	public boolean send() {
		if (DEBUG_MODE) {
			return true;
		}
		// prepare then send data
		ArrayList<NameValuePair> output = new ArrayList<NameValuePair>();
		// encode topic
		output.add(new BasicNameValuePair("subject", _subject));
		// encode data
		output.add(new BasicNameValuePair("content", _content));
		
		// request server reply with id for attachments
		if (this.hasAttachment) {
			output.add(new BasicNameValuePair("attachment", String.valueOf(this._attachment.size())));
			Log.d(TAG, "Att size " + String.valueOf(this._attachment.size()));
		}
		
		String response = sendValuePair(output);
		Log.d(TAG, "Response for " + _subject);
		Log.d(TAG, "---------------");	
		Log.d(TAG, response);
		Log.d(TAG, "---------------");
		
		JSONObject jsonResponse;
		try{
			jsonResponse = new JSONObject(response);
			boolean status = jsonResponse.has("status") ? jsonResponse.getBoolean("status") : false;
			
			// if message has attachments, send these now
			if (this.hasAttachment)
			{
				// feedback attachments
				if (this._subject.equals(SendService.T_FEEDBACK)) 
				{
					boolean imgStatus = sendImages(jsonResponse.getInt("fbId"));
					Log.d(TAG, "Overall image status " + String.valueOf(imgStatus));			
				}
			}
			return status;
		} catch (JSONException e) {
	        Log.e(TAG, "JSON Error parsing data " + e.getMessage());
	        e.printStackTrace();
	    }
		return false;
	}
	
	
	// Method to send extra scan stats to server
	public boolean sendExtraStat() {
		Log.d(TAG, "Sending extra scan stats");
		// get filename from uri list then try send file
		if (SendService.analyticsDir.mkdirs() || SendService.analyticsDir.isDirectory()){
			File zipFile = new File(SendService.analyticsDir, _fileAttach);
			//File zipFile = new File(SendService.testDir, "test5mb.zip");
			//File zipFile = new File(SendService.testDir, "test500kb.zip");
			//String response = sendFile(zipFile);
			String response = sendFileOctet(zipFile);
			Log.d(TAG, "Response sendExtraStat");
			Log.d(TAG, "---------------");	
			Log.d(TAG, response);		
				
			JSONObject jsonResponse;
			try{
				jsonResponse = new JSONObject(response);		
				boolean status = jsonResponse.has("status") ? jsonResponse.getBoolean("status") : false;
				return status;
			} catch (JSONException e) {
		        Log.e(TAG, "JSON Error parsing data " + e.toString());
		        e.printStackTrace();
		    }
		}else Log.e(TAG, "Expected file missing");		
		return false;
	}	
	

	private String sendFileOctet(File file) {
		HttpsURLConnection connection = webViewHandler.trustSocketFactory.getSSLConnection(mContext, postScanStatURL);
				
		if (connection == null) {
			Log.e(TAG, "Failed to open connection");
			return "Failed to open connection";
		}
		
		DataOutputStream outputStream = null;
		
		// Buffers to read in file
		int bytesRead, bytesAvailable, bufferSize;
		byte[] buffer;
		int maxBufferSize = 1 * 1024 * 1024;
		
		// Allow inputs and outputs
		connection.setDoInput(true);
		connection.setDoOutput(true);
		connection.setUseCaches(false);
		connection.setChunkedStreamingMode(maxBufferSize);
		connection.setRequestProperty("Accept-Charset", "UTF-8");
		connection.setRequestProperty("Content-Type", "application/zip");
		connection.setRequestProperty("Content-Length", String.valueOf(file.length()));
		connection.setRequestProperty("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
		connection.setRequestProperty("X_FILE_NAME", file.getName());
		try {
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Connection", "Keep-Alive");
			connection.connect();
			
			// Get path to send data to server
			outputStream = new DataOutputStream(connection.getOutputStream());

			FileInputStream fInStream = new FileInputStream(file);
			bytesAvailable = fInStream.available();
			int total = bytesAvailable;
			long length = file.length();
			
			Log.d(TAG, "Total " + String.valueOf(total));
			Log.d(TAG, "Content Length " + String.valueOf(length));
			
			bufferSize = Math.min(bytesAvailable, maxBufferSize);
			buffer = new byte[bufferSize];
			
			// Read file
			Log.d(TAG, "Preparing to write " + file.getName());
			bytesRead = fInStream.read(buffer, 0, bufferSize);
			int written = bytesRead;
			
			while(bytesRead > 0) {
				Log.d(TAG, "Writing chunk " + String.valueOf(written) + "/" + String.valueOf(total));
				outputStream.write(buffer, 0, bufferSize);
				bytesAvailable = fInStream.available();
				bufferSize = Math.min(bytesAvailable, maxBufferSize);
				bytesRead = fInStream.read(buffer, 0, bufferSize);
				written += bytesRead;
				Log.d(TAG, "Remaining " + String.valueOf(bytesAvailable) + " bytes, done " + String.valueOf(written) + "/" + String.valueOf(total));			
			}			
			Log.d(TAG, "POST complete");
			
			// Get server response
			int respCode = connection.getResponseCode();
			String respMsg = connection.getResponseMessage();
			Log.d(TAG, String.valueOf(respCode) + " " + respMsg);			
			
			// Read server message
			InputStream inputStream = null;
			inputStream = connection.getInputStream();			
			StringBuffer sb = new StringBuffer();
			int ch;
			while ((ch = inputStream.read()) != -1) {
				sb.append((char) ch);
			}

			fInStream.close();
			outputStream.flush();
			outputStream.close();
			
			return sb.toString();			
		}
		catch (ProtocolException e) {
			Log.e(TAG, "Unrecognised protocol");
			e.printStackTrace();
			return "ProtocolException";
		}
		catch (IOException e)
		{
			Log.d(TAG, "Could not get input/output stream");
			e.printStackTrace();
			return "IOException";
		}
	}

	private String sendValuePair(ArrayList<NameValuePair> output) {	
		try {			
			httpPost.setEntity(new UrlEncodedFormEntity(output));
			HttpResponse response = httpClient.execute(httpPost);
			String responseStr = processResponse(response);
			return responseStr;
		}
	    catch (UnsupportedEncodingException e){
	    	Log.e(TAG, "VP UnsupportedEncodingException " + e.getMessage());
	    	e.printStackTrace();
	    	return "UnsupportedEncodingException";
	    } 
		catch (IOException e) {
			Log.e(TAG, "VP IOException " + e.getMessage());
			e.printStackTrace();
			return "IOException";
		}
	}
	
	// Method to send feedback images to server
		private boolean sendImages(int fbId) {
			Log.d(TAG, "Sending images");
			boolean overallStatus = true;
			
			ArrayList<NameValuePair> output = new ArrayList<NameValuePair>();
			output.add(new BasicNameValuePair("subject", "Attachment"));
			output.add(new BasicNameValuePair("fb_id", String.valueOf(fbId)));
			
			String encodedImage;
			byte[] img_bytes;
			// Store filename and image in JSON like string
			// Do not use json(hashmap) as it can run out of memory if user has posted many pictures
			for(Uri uri : _attachment) {
				String selectedImagePath = UriHelper.getPath(mContext, uri);
				if (selectedImagePath != null ) {		
					File img_file = new File(selectedImagePath);
					try {
						img_bytes = readFile(img_file);
						encodedImage = Base64.encodeToString(img_bytes, Base64.DEFAULT);
					} catch (IOException e) {
						Log.e(TAG, "Error converting to image file to byte array");
						e.printStackTrace();
						continue;
					}
					// use string builder to build json format
					StringBuilder sb = new StringBuilder();
					sb.append("{\"").append(selectedImagePath).append("\":\"").append(encodedImage).append("\"}");
					String sbToJSONStr = "";
					try {
						// encode to json format
						JSONObject sbToJSON = new JSONObject(sb.toString());
						sbToJSONStr = sbToJSON.toString();
						output.add(new BasicNameValuePair("Images", sbToJSONStr));
						String response = sendValuePair(output);
						
						Log.d(TAG, "---------------");
						Log.d(TAG, "Sending Image " + selectedImagePath);
		
						try{
							JSONObject jsonResponse = new JSONObject(response);
							boolean status = jsonResponse.has("status") ? jsonResponse.getBoolean("status") : false;
							
							Log.d(TAG, "---------------");				
							Log.d(TAG, "Response");
							Log.d(TAG, "---------------");					
							Log.d(TAG, "Image sent " + String.valueOf(status));
							Log.d(TAG, "---------------");
							
							overallStatus = overallStatus ? status : false;
						} catch (JSONException e) {
					        Log.e("JSON Parser", "Error parsing data " + e.toString());
					    }		
						int index = output.indexOf(new BasicNameValuePair("Images", sbToJSONStr));
						Log.d(TAG, "Index " + String.valueOf(index));
						output.remove(index);
					} catch (Exception e) {
					    Log.e(TAG, "Malformed JSON String");
					}				
				}
			}
			Log.d(TAG, "Image sent overall " + String.valueOf(overallStatus));
			return overallStatus;
		}	
	
	// NOTE: INSECURE - Trust Manager accepts all certificates
	// Use this to connect to development server only as that has no certificate 
	TrustManager[] trustAllCerts = new TrustManager[] {
		new X509TrustManager() {
			
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
			
			public void checkServerTrusted(X509Certificate[] chain, String authType) {
			}
			
			public void checkClientTrusted(X509Certificate[] chain, String authType) {
			}
		}
	};
	
	// Bypass server certificates
	public class NullHostNameVerifier implements HostnameVerifier {
		public boolean verify(String hostname, SSLSession session) {
			Log.d(TAG, "Approving certificate for " + hostname);
	        return true;
		}
	}
	

	// NOTE: INSECURE - HTTP client to trust all certificates
	// Use this to connect to development server only as that has no certificate 
	public HttpClient getNewHttpClient() {
	    try {
	        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
	        keystore.load(null, null);        
	        
	        MySSLSocketFactory sf = new MySSLSocketFactory(keystore);
	        sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

	        HttpParams params = new BasicHttpParams();
	        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
	        HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

	        SchemeRegistry registry = new SchemeRegistry();
	        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
	        registry.register(new Scheme("https", sf, 443));

	        ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

	        return new DefaultHttpClient(ccm, params);
	    } catch (Exception e) {
	        return new DefaultHttpClient();
	    }
	}
}
