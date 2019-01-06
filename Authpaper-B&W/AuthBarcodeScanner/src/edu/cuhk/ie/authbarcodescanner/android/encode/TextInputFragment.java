/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.encode;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import edu.cuhk.ie.authbarcodescanner.android.EncodeFragment;
import edu.cuhk.ie.authbarcodescanner.android.fragmentCallback;
import edu.cuhk.ie.authbarcodescanner.android.R;

/**
 * This fragment shows a simple text box input for 2D barcode creation
 */
public class TextInputFragment extends Fragment {
	private static final String TAG=TextInputFragment.class.getSimpleName();	
	
	private Activity context=null;
	private fragmentCallback fragmentCallback=null;
	
	@Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try{
        	context = activity;
        	fragmentCallback = (fragmentCallback) activity;
        } catch (ClassCastException e) {
            throw new RuntimeException(getActivity().getClass().getSimpleName() 
            		+ " must implement fragmentCallback to use this fragment", e);
        }
    }	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		//Insert the fragment_result.xml into the container
		View rootView = inflater.inflate(R.layout.fragment_textinput,container,false);
		return rootView;
	}
	@Override
	public void onResume(){
		super.onResume();
		Button createQRcodeButton = (Button) getView().findViewById(R.id.encode_textField_button);
		createQRcodeButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				EditText textInput = (EditText) getView().findViewById(R.id.encode_textField_text);
				Editable text = textInput.getText();
				if(text !=null) {
					String resultText=text.toString();
					if(resultText ==null || resultText.isEmpty()) 
						alert("Please input text to be inserted into the QR code");
					else{
						Fragment target = getTargetFragment();
						int requestCode =  getTargetRequestCode();
						if(target !=null){
							Intent intent = new Intent();
						    intent.putExtra(EncodeFragment.result_text_index, resultText);
						    fragmentCallback.onReturnResult(requestCode, EncodeFragment.result_OK, intent);
						}
					}
				}
			}
		});
	}
	private void alert(String message){
		if(this.context ==null || message ==null || message.isEmpty()) return;
		Toast toast = Toast.makeText(this.context, message, Toast.LENGTH_SHORT);
		toast.show();
	}
}