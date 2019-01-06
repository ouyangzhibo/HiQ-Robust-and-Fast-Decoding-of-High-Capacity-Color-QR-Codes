/*
 Copyright (C) 2014 Marco in MobiTeC, CUHK 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.ViewParent;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import edu.cuhk.ie.authbarcodescanner.analytic.FeedbackAttachAdapter;
import edu.cuhk.ie.authbarcodescanner.analytic.GetUserFeedback;



/**
 * This fragment handles the user feedback interaction
 */
public class FeedbackFragment extends StandardFragment {
	private static final String TAG=FeedbackFragment.class.getSimpleName();
	private static final int layoutXML=R.layout.fragment_feedback;
	
	// user interface	
	private Map<Integer, String> allBtn;
	
	private CheckBox cb_anonymous;
	private CheckBox cb_hardware;
	private EditText et_input, email_input;
	private ImageButton btn_attach;
	
	private ListView lv_attachment;
	private TextView attach_empty;
	private FeedbackAttachAdapter adapter;
	
	// attachment callback
	private static final int RESULT_IMAGE = 1;
	private ArrayList<Uri> attachmentList = new ArrayList<Uri>();
	private final String attachKey = "ATT_LIST_KEY";
	

	// feedback data to send
	private String fbButton = "None";
	private String fbText = "";

	// references to menu item status
	private int btn_send_id = R.id.fb_menu_send;
	private int btn_sent = R.string.fb_menu_send;
	private int btn_sending = R.string.fb_menu_sending;
	
	public FeedbackFragment() { 
		setLayout(layoutXML);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		
		allBtn = new HashMap<Integer, String>();
		allBtn.put(R.id.fb_bad, "Dislike");
		allBtn.put(R.id.fb_good, "Like");
		allBtn.put(R.id.fb_info, "Info");
		
		if (savedInstanceState != null) {
			Log.d(TAG, "Bundle detected");
			if(savedInstanceState.containsKey(attachKey)) 
			{
				String[] array_str = savedInstanceState.getStringArray(attachKey);
				Log.d(TAG, "Loading attachments from bundle");
				for(String str : array_str) {
					attachmentList.add(Uri.parse(str));
				} 
			}
		}		
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// convert uri array to string array		
		if (attachmentList.size() > 0) {
			Log.d(TAG, "Saving state to bundle");			
			String[] array_str = new String[attachmentList.size()];
			for(int i = 0; i < attachmentList.size(); i++) {
				array_str[i] = attachmentList.get(i).toString();
			}
			outState.putStringArray(attachKey, array_str);
		}
	}
	
	@Override 
	public void onDestroyView() {
		super.onDestroyView();
		Log.d(TAG, "onDestroyView");
	}
	
	
	@Override 
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
	}

	@Override
	public void onResume(){
		super.onResume();
		Log.d(TAG, "onResume");
		// references to view objects		
		cb_anonymous = (CheckBox) getView().findViewById(R.id.fb_cb_anon);
		email_input = (EditText) getView().findViewById(R.id.fb_email);
		if(!edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer && cb_anonymous !=null){
			ViewParent layout = cb_anonymous.getParent();
			if(layout !=null) ((ViewGroup) layout).removeView(cb_anonymous);	
		}
		if(edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer && email_input !=null){
			ViewParent layout = email_input.getParent();			
			if(layout !=null) ((ViewGroup) layout).removeView(email_input);			
		}
		cb_hardware = (CheckBox) getView().findViewById(R.id.fb_cb_stat);
		et_input = (EditText) getView().findViewById(R.id.fb_input);
		btn_attach = (ImageButton) getView().findViewById(R.id.fb_attach);
		lv_attachment = (ListView) getView().findViewById(R.id.attach_listView);
		attach_empty = (TextView) getView().findViewById(R.id.attach_empty);
		
		// init list view
		Log.d(TAG, "onResume List has " + String.valueOf(attachmentList.size()) + " items");
		
		adapter = new FeedbackAttachAdapter(this.context, attachmentList);
		lv_attachment.setAdapter(adapter);
		lv_attachment.setEmptyView(attach_empty);
		lv_attachment.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		lv_attachment.setMultiChoiceModeListener(new MultiChoiceListener());
		
		//set button click listeners
		for(int v_id : allBtn.keySet()) {
			getView().findViewById(v_id).setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					activateButton(v);
				}
			});
		}
		
		btn_attach.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addAttachment();
			}
		});
		UIRefresh();		
	}
	
	public void UIRefresh() {
		Log.d(TAG, "refreshing");
		Log.d(TAG, "UIrefresh Contains " + String.valueOf(attachmentList.size()) + " items");
		for (Uri uri : attachmentList) {
			Log.d(TAG, uri.toString());
		}
		try {
			ListAdapter la = lv_attachment.getAdapter();
			((BaseAdapter) la).notifyDataSetChanged();			
		}
		catch (Exception e) {
			Log.e(TAG, "Error getting adapter " + e.toString());
			e.printStackTrace();
		}
	}
	
	
	
	//Opens up image picker for attaching to feedback
	private void addAttachment() {
		Log.d(TAG, "addAttachment List has " + String.valueOf(attachmentList.size()) + " items");
		Intent imgIntent = new Intent(Intent.ACTION_GET_CONTENT);
		imgIntent.setType("image/*");
		imgIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);		
		startActivityForResult(Intent.createChooser(imgIntent,"Select Picture"), RESULT_IMAGE);
	}
	
	// handle image after selection
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Log.d(TAG, "onActivityResult List has " + String.valueOf(attachmentList.size()) + " items");
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == RESULT_IMAGE) {
				Uri selectedImageUri = data.getData();
				// don't add if online resource
				boolean offline = true;
				if (selectedImageUri.toString().toLowerCase().contains("http".toLowerCase())) {
					offline = false;
				}
				if (offline) {
					// don't re-add if existing
					Log.d(TAG, "Trying to add new image uri");
					Log.d(TAG, "Attachment List has " + String.valueOf(attachmentList.size()) + " items");
					for(Uri uri : attachmentList) {
						Log.d(TAG, uri.toString());
					}
					Log.d(TAG, "New URI " + selectedImageUri.toString());
					Log.d(TAG, "AlreadyExists " + String.valueOf(attachmentList.contains(selectedImageUri)));
					
					if (!attachmentList.contains(selectedImageUri)) {
						//String imgUrl = selectedImageUri.toString();
						attachmentList.add(selectedImageUri);
						Log.d(TAG, "Attachment list now contains");
						for(Uri uri : attachmentList) {
							Log.d(TAG, uri.toString());
						}
					}
					else {
						Toast.makeText(context, "This attachment has already been added", Toast.LENGTH_SHORT).show();
					}	
				}
				else {
					Toast.makeText(context, context.getText(R.string.gen_error_res_online), Toast.LENGTH_SHORT).show();
				}
			}
		}
	}
	
	// package form information to send. retrieves extra information as required
	public void packDataAndSend() {
		Log.d(TAG, "Checking data to send");
		fbText = et_input.getText().toString();
		if (fbText.isEmpty() || isBlank(fbText)) {
			// request user fill in something
			Toast.makeText(getActivity(), getActivity().getString(R.string.fb_empty), Toast.LENGTH_SHORT).show();
			return;
		}
		GetUserFeedback getFBTask = new GetUserFeedback(getActivity(), this);
		JSONObject fbJson = new JSONObject();
		try {
			fbJson.put("fb_stat", fbButton);
			fbJson.put("fb_text", fbText);
		}
		catch (JSONException e) {
			Log.e(TAG, "Error converting to JSON " + e.toString());
			e.printStackTrace();
		}		
		getFBTask.setFeedback(fbJson);
		//If user provides another email, then use it
		if(email_input !=null){
			String userEmail=email_input.getText().toString();
			if(!userEmail.isEmpty() && !isBlank(userEmail)) 
				getFBTask.setUserEmail(userEmail);
		}		
		//Negate the cb_anonymous so that getFBTask will not include the user email when clicked
		boolean isIncludeUserData=(cb_anonymous ==null)? true : !cb_anonymous.isChecked();  
		Boolean[] reqStats = new Boolean[] { isIncludeUserData, cb_hardware.isChecked() };
		// if sending user info, no need to send hardware info as well
		//if (reqStats[0]) reqStats[1] = false;
		getFBTask.execute(reqStats);		
	}
	
	// make call to send feedback via service
	public void sendFeedbackInfo(JSONObject jsonObj) {
		// update ui when feedback is being sent 
		// Message msg = actHandler.obtainMessage(fragmentCallback.UPDATE_MENU_TITLE);
		// msg.obj = new TitleOptions(btn_send_id, btn_sending, false);
		// msg.sendToTarget();		
		
		super.fragmentCallback.updateMenuTitle(btn_send_id, btn_sending, false);
		try {
			String mail_body = jsonObj.toString(4);
			Log.d(TAG, "Feedback Info: " + mail_body);
			super.fragmentCallback.initPostData(SendService.T_FEEDBACK, mail_body, SendService.USER_FEEDBACK, attachmentList, false);		
		} catch (JSONException e) {
			Log.e(TAG, "Error converting JSON to string " + e.toString());
			e.printStackTrace();
		}		
	}
	
	// update title and disable button
	public void mailSentCallback(boolean mailSent) {
		// Message msg = actHandler.obtainMessage(fragmentCallback.UPDATE_MENU_TITLE);
		// msg.obj = new TitleOptions(btn_send_id, btn_sent, true);
		// msg.sendToTarget();		
		super.fragmentCallback.updateMenuTitle(btn_send_id, btn_sent, true);
		Log.d(TAG, "Mail sent " + String.valueOf(mailSent));
		if (mailSent) {
			Toast.makeText(context, "Thank you, your feedback has been submitted", Toast.LENGTH_SHORT).show();
			resetForm();
		}
		else {
			Toast.makeText(context, "Your feedback could not be sent, please try again later" , Toast.LENGTH_SHORT).show();
		}
	}	
	
	// checks if string is blank only
    public static boolean isBlank(String string) {
        if (string == null || string.length() == 0)
            return true;

        int l = string.length();
        for (int i = 0; i < l; i++) {
            if (!Character.isWhitespace(string.codePointAt(i)))
                return false;
        }
        return true;
    }
	
	// set background to active for clicked button
	private void activateButton(View v) {
		deactivateOtherButton(v);
		v.setBackgroundColor(
			getResources().getColor(android.R.color.holo_blue_light)
		);
		v.setBackground(
			getResources().getDrawable(R.drawable.btn_bkg_active)
		);
		fbButton = allBtn.get(v.getId());
		Log.d(TAG, "Selected " + fbButton);
	}
	
	private void deactivateOtherButton(View v) {
		int sel_id = v.getId();
		for(int v_id : allBtn.keySet()) {
			if (v_id != sel_id) {
				getView().findViewById(v_id).setBackgroundColor(
					getResources().getColor(android.R.color.transparent)
				);
				getView().findViewById(v_id).setBackground(
						getResources().getDrawable(R.drawable.btn_background)
				);
			}
		}
	}
	
	// reset form
	public void resetForm() {
		// clear checkbox
		if(cb_anonymous !=null) cb_anonymous.setChecked(false);
		cb_hardware.setChecked(true);
		// clear text
		if(email_input !=null) email_input.setText(null);
		et_input.setText(null);
		
		// deactivate all buttons
		for(int v_id : allBtn.keySet()) {
			getView().findViewById(v_id).setBackgroundColor(
				getResources().getColor(android.R.color.transparent)
			);
			getView().findViewById(v_id).setBackground(
					getResources().getDrawable(R.drawable.btn_background)
			);
		}
		fbButton = "None";
		fbText = "";
		
		//clear attachments
		attachmentList.clear();
		UIRefresh();
	}
	
	// remove attachments
	private void onDeleteSelected(List<Uri> delList) {
		for(Uri uri : delList) {
			int index = attachmentList.lastIndexOf(uri);
			if (index != -1) {
				attachmentList.remove(index);
			}
		}
		UIRefresh();
	}
	
	// toggle attachment deletes
	private class MultiChoiceListener implements MultiChoiceModeListener {
		
		//show contextual action bar
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// show delete menu
		    mode.getMenuInflater().inflate(R.menu.delete_selected, menu);
	        mode.setTitle(R.string.title_delete);
	        return true;
		}
		
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			// hide or show menu items based on number of records selected
			int checkedCount = lv_attachment.getCheckedItemCount();	
			mode.setTitle(getText(R.string.title_item_selected) + " " + Integer.toString(checkedCount));
			return true;
		}
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			// delete option
		    switch (item.getItemId()) {
		        case R.id.delete:
		      	  	onDeleteSelected(adapter.getSelectedList());
		            mode.finish();
		            return true;
		        default:
		            return false;
		    }
		}
		
		@Override
		public void onDestroyActionMode(ActionMode mode) {			
			adapter.removeSelection();
		}

		@Override
		public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
	        adapter.toggleSelection(position);	        
	        //update menu 
        	mode.invalidate();	
		}
	}
}