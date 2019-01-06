/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.result;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import edu.cuhk.ie.authbarcodescanner.android.R;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import edu.cuhk.ie.authbarcodescanner.android.Log;

/**
 * This class handles the settings of webview which shows AuthPaper in html code
 */
public class webViewHandler{
	public static final String TAG=webViewHandler.class.getSimpleName();
	
	@SuppressLint("NewApi")
	public static void displayWebpage(final WebView resultWebView,final String baseURL, final String content){
		resultWebView.setVisibility(View.VISIBLE);	
		boolean isOldDevice = ( (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT)
				|| (android.os.Build.MODEL.contains("Galaxy Nexus"))
				|| (android.os.Build.MODEL.contains("MI 3W")) ); //Hard code for Galaxy Nexus and XiaoMi 3
		//resultWebView.setInitialScale(1);
		WebSettings settings = resultWebView.getSettings(); 
		settings.setUseWideViewPort(true);
		settings.setLoadWithOverviewMode(true);		
		settings.setJavaScriptEnabled(false);
		resultWebView.setScrollBarStyle(WebView.SCROLLBARS_INSIDE_OVERLAY);
		//resultWebView.setScrollbarFadingEnabled(false);
		//resultWebView.setInitialScale(300);
		if(!isOldDevice){
			settings.setAllowFileAccessFromFileURLs(false);
			settings.setAllowUniversalAccessFromFileURLs(false);
		}
		Document doc = Jsoup.parse(content, "UTF-8");
		Element element=doc.select("style").first();
    	if(element !=null) element.html(element.html().replace("display:none", "opacity:0.8"));
    	//Hard-code some field to make it work on Android phones
    	String rawHTML=doc.html().replace("<meta charset=\"utf-8\" />", 
    			"<meta charset=\"utf-8\"/><meta name=\"viewport\" content=\"width=device-width\">")
    			.replaceAll("<body>", "<body><div id=\"body\">").replace("</body>", "</div></body>")
    			.replaceAll("<style(.+)>(.+)body\\{","<style$1>$2#body\\{")
    			.replaceAll("media=\"screen\"","media=\"all\"");
    	Matcher m = Pattern.compile("<style(.+)>(.+)#body\\{([^}]+)width:(\\d{2})em\\}")
    			.matcher(rawHTML);
		int searchIndex=0;
		while(searchIndex >=0 && searchIndex < rawHTML.length() && m.find(searchIndex)){			
    		int oldWidth=Integer.parseInt(m.group(4));
    		oldWidth+=2;
    		rawHTML = rawHTML.substring(0, m.start())
    			+"<style"+m.group(1)+">"+m.group(2)+"#body{"+m.group(3)+"width:"+oldWidth+"em}" 
    			+rawHTML.substring(m.end());
    		searchIndex=m.end();
    		m.reset(rawHTML);
    	}
    	
    	final String html=rawHTML;
    	//Log.d(TAG,html);
		if(!isOldDevice)
			resultWebView.loadDataWithBaseURL(baseURL, html, "text/html; charset=UTF-8", null, null);
    	else resultWebView.loadDataWithBaseURL(baseURL, html, "text/html","UTF-8", null);		
		resultWebView.setWebViewClient(new WebViewClient(){
			@Override
			public void onPageFinished(WebView view, String url) {
				WebSettings settings = resultWebView.getSettings(); 
				settings.setDisplayZoomControls(false);
				settings.setSupportZoom(true);
				settings.setBuiltInZoomControls(true); 
			}
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				if(Pattern.compile("^https?://(www.google.com|www.ie.cuhk.edu.hk|authpaper.in|authpaper.net)").matcher(url).find()){
				    Intent intent=new Intent( Intent.ACTION_VIEW, Uri.parse(url));
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
					try{
						view.getContext().startActivity(Intent.createChooser(intent, "Select App to visit the website :"));		
				    }catch(ActivityNotFoundException e2){
				    	new AlertDialog.Builder(view.getContext()).setTitle(R.string.app_name)
				        .setMessage("No application available for this action")
				        .setPositiveButton("OK", null).show();
				    }
					return true;
				}
				new AlertDialog.Builder(view.getContext()).setTitle(R.string.app_name)
		        .setMessage("Sorry, loading external pages are not allowed")
		        .setPositiveButton("OK", null).show();
	            //Disallow loading any page
	            return true;
	        }
			@Override
			public WebResourceResponse shouldInterceptRequest(WebView view, String url){
				//Disallow getting any external resources, except linking back to the main server
				//Hard-code fix on adding space issue
				url=url.replaceAll("^(https?): //", "$1://");
				url=url.replaceAll("^(https?)://%20//", "$1://");
				//Check if it is going to get an image from our server
				String regularExp="^https?://(localhost|authpaper\\.in|www.authpaper\\.net|www.authpaper\\.biz)/userIcons/[a-zA-Z0-9_/-]*\\.(png|jpe?g)$";
				Matcher m = Pattern.compile(regularExp).matcher(url);				
				if(m.find()){					
					//Read an external data
					InputStream iconStream=null;
					String localURL=url.replaceFirst("^https?://(localhost|authpaper\\.in|www.authpaper\\.net|www.authpaper\\.biz)/userIcons/", "");
					//File tempFolderRoot = new File(new File(Environment.getExternalStorageDirectory(), 
							//view.getContext().getString(R.string.app_name)), "userIcons");
					File tempFolderRoot = new File(view.getContext().getFilesDir(), "userIcons");
					if(tempFolderRoot.exists() || tempFolderRoot.mkdirs()){
						File icon = new File(tempFolderRoot.getAbsolutePath()+"/"+localURL);
						if(icon.exists() && icon.isFile()){	
							Log.d(TAG,"Load icon from the internal storage");
							try{
								iconStream=new FileInputStream(icon);
								return new WebResourceResponse("image/*", "base64", iconStream);
							}catch(FileNotFoundException e){ }
						}
						//Create the missing parents if necessary
						if(icon.getParentFile() !=null) icon.getParentFile().mkdirs();
						Log.d(TAG,"Loading icon from the Internet");
						//Download it from the Internet							
						InputStream stream=trustSocketFactory.setUpSSLConnection(view.getContext(),url);		
						try{
							FileOutputStream fos = new FileOutputStream(icon);
							Bitmap theIcon=BitmapFactory.decodeStream(stream);
							if(theIcon !=null) theIcon.compress(Bitmap.CompressFormat.PNG, 0, fos);
						}catch(FileNotFoundException e2){ }
						try{
							if(stream !=null) stream.close();
						}catch(IOException e2){ }
						try{
							iconStream=new FileInputStream(icon);
							return new WebResourceResponse("image/*", "base64", iconStream);
						}catch(FileNotFoundException e){ }
					}
				}
				if(!url.startsWith("file://"))
					return new WebResourceResponse("text/plain", "UTF-8", 
							new StringBufferInputStream("Loading external resources is not allowed."));
				else return super.shouldInterceptRequest(view, url);
			}
		});
	}
	/**
	 * This class creates a HTTPS connection that can connect to the authpaper server (authpaper.biz / authpaper.in / authpaper.net) 
	 * using the certificates in the assets folder
	 * @author solon li
	 *
	 */
	public static class trustSocketFactory{
		private static SSLContext ssl=null;
		private static HostnameVerifier hostnameVerifier = new HostnameVerifier() {
		    @Override
		    public boolean verify(String hostname, SSLSession session){
		        HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
		        String host=(hostname.startsWith("authpaper.biz"))? "authpaper.net" : hostname;
		        return hv.verify(host, session);
		    }
		};
		
		private static void setSSL(Context context){			
			// Create a KeyStore containing our trusted CAs			
			try {
				CertificateFactory cf = CertificateFactory.getInstance("X.509");
				String keyStoreType = KeyStore.getDefaultType();
				KeyStore keyStore = KeyStore.getInstance(keyStoreType);
				keyStore.load(null, null);
				//Read the CA certs one by one
				AssetManager assets = context.getAssets();
				String[] trustedCerts=assets.list("trustedservercert");
				for(int i=0,length=trustedCerts.length;i<length;i++){
					InputStream certStream=assets.open("trustedservercert/"+trustedCerts[i]);
					try{
						Certificate cert=cf.generateCertificate(certStream);
						if(cert !=null) keyStore.setCertificateEntry(trustedCerts[i], cert);
					}finally{
						certStream.close();
					}
				}
				// Create a TrustManager that trusts the CAs in our KeyStore
				String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
				TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
				tmf.init(keyStore);
				ssl = SSLContext.getInstance("TLS");
				ssl.init(null, tmf.getTrustManagers(), null);
			}catch(CertificateException e){ 
			}catch(KeyStoreException e){
			}catch(NoSuchAlgorithmException e){ 
			}catch(IOException e){ 
			}catch (KeyManagementException e){
			}
		}
		public static InputStream setUpSSLConnection(Context context, String url){
			try{
				HttpsURLConnection connection = getSSLConnection(context, url);
				connection.setDoInput(true);
				connection.connect();
				return connection.getInputStream();
			}catch(MalformedURLException e){
			}catch(IOException e){
				Log.d(TAG,"Cannot read icon"+e.getMessage());
			}
			return null;
		}
		
		public static HttpsURLConnection getSSLConnection(Context context, String url) {
			if(ssl ==null) setSSL(context);
			try{
				HttpsURLConnection connection = (HttpsURLConnection) (new URL(url)).openConnection();
				connection.setSSLSocketFactory(ssl.getSocketFactory());
				connection.setHostnameVerifier(hostnameVerifier);
				return connection;
			}catch(MalformedURLException e){
			}catch(IOException e){
				Log.d(TAG,"Cannot read icon"+e.getMessage());
			}
			return null;
		}
	}
	
}