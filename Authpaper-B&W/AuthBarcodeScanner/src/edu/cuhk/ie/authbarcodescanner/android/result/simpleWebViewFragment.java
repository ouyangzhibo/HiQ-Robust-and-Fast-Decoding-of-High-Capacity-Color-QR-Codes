/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.result;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;
import edu.cuhk.ie.authbarcodescanner.android.R;

/**
 * This fragment shows a simple webview with a title to display local web page content
 */
public class simpleWebViewFragment extends Fragment{
	private static final String TAG=simpleWebViewFragment.class.getSimpleName();	
	
	private Activity context=null;	
	private String title="", html="", baseURL="";
	
	@Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        context = activity;        
    }	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		//Insert the fragment_result.xml into the container
		View rootView = inflater.inflate(R.layout.fragment_result_incinfo,container,false);
		return rootView;
	}
	public void setContent(String title, String baseURL, String html){
		this.title=title;
		this.baseURL=baseURL;
		this.html=html;
	}
	@Override
	public void onResume(){
		super.onResume();
		TextView titleTextView = (TextView) getView().findViewById(R.id.inc_titleTextView);
		titleTextView.setText(title);
		//Reduce text size when there are too many words
		if(title.length()>120)
			titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 
					(int) (titleTextView.getTextSize()*0.5));
		else if(title.length()>60)
			titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 
					(int) (titleTextView.getTextSize()*0.8));
		
		WebView resultWebView = (WebView) getView().findViewById(R.id.inc_content_webview);		
		webViewHandler.displayWebpage(resultWebView, baseURL, html);
	}
	protected void alert(String message){
		if(this.context ==null || message ==null || message.isEmpty()) return;
		Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
		toast.show();
	}
}