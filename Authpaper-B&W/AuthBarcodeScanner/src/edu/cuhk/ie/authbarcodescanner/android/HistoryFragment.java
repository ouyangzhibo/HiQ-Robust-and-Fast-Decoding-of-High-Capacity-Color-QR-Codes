/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import edu.cuhk.ie.authbarcode.serializableResult;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.google.zxing.Result;

import edu.cuhk.ie.authbarcodescanner.android.history.HistoryDbEntry;
import edu.cuhk.ie.authbarcodescanner.android.history.HistoryDbHelper;
import edu.cuhk.ie.authbarcodescanner.android.history.HistoryFragAdapter;
import edu.cuhk.ie.authbarcodescanner.android.result.webViewHandler.trustSocketFactory;

/**
 * This fragment lists out the history of scanned 2D barcodes and control the access to the history database. 
 * Please get access to this class via getInstance()
 */
public class HistoryFragment extends StandardFragment{
	private static final String TAG=HistoryFragment.class.getSimpleName();
	private static final int layoutXML=R.layout.fragment_history;
	private static int numberOfDisplayEntry=-1;
	private static HistoryFragment instance=null, encodeInstance=null;
	private int syncMenuID=-1, visitMenuID=-1;
	private static final int[] menuToDelete={R.id.action_history,R.id.action_encodeHistory};
	private static final String uploadURL="https://authpaper.net/scannerHistory.php";
	private static final String PREFS_NAME = "HistoryRecord";
	private static final String ScanUpName = "lastScanUpTime", EncodeUpName = "lastEncodeUpTime", 
			ScanChangeName = "lastScanChangeTime", EncodeChangeName = "lastEncodeChangeTime"; 
	
	private ListView listView; 
	private String previousTitle;
	
	//array to track selected entries
	private ArrayList<HistoryDbEntry> mSelectedEntries = new ArrayList<HistoryDbEntry>();
	private boolean singleSelect = true;
	private ActionMode mActionMode;
	
	//private class to pass edit status to history result
	private class EntryDetail {
		HistoryDbEntry entry;
		String title;
	}

	//DB related variables
	private HistoryDbHelper db;
	private HistoryFragAdapter adapter;
	private final boolean showEncodeHistory;
	
	//Hardcode for CUPP
	public Result getLatestEntry(){
		HistoryDbEntry[] entries=(this.db ==null)? null : this.db.getLatestEntries(1);
		if(entries ==null || entries.length <1) return null;
		Result rawResult = (entries[0] ==null)? null: (entries[0].getRawResult() !=null)? entries[0].getRawResult(): 
				getResult(context, entries[0].getResultFilePath());
		if(rawResult==null) return null;
		return rawResult;
	}
	//Singleton and DB related functions
	public synchronized static HistoryFragment getInstance(){
		return getInstance(false);
	}
	public synchronized static HistoryFragment getInstance(boolean isEncodeHistory){
		if(!isEncodeHistory && instance !=null) return instance;
		if(isEncodeHistory && encodeInstance !=null) return encodeInstance;
		return null;
	}
	public static void setInstance(Activity context, String DBname, int DBversion){
		instance = new HistoryFragment();
		//TODO: make it as setArguments
		instance.setContext(context, 
				new HistoryDbHelper(context, DBname, null, DBversion, false));
	}
	public static void setEncodeInstance(Activity context, String DBname, int DBversion){
		encodeInstance = new HistoryFragment(true);
		encodeInstance.setContext(context, 
				new HistoryDbHelper(context, DBname, null, DBversion, true));
	}
	public HistoryFragment(){
		showEncodeHistory=false;
		setLayout(layoutXML);
	}
	public HistoryFragment(boolean isShowingEncodeHistory){
		setLayout(layoutXML);
		showEncodeHistory=isShowingEncodeHistory;
	}
	public void setContext(Activity context, HistoryDbHelper db){
		this.context=context;
		this.db=db;
		new OpenDBTask().execute();
	}
	
	private class OpenDBTask extends AsyncTask<Void, Void, Boolean> {
	     protected Boolean doInBackground(Void... dummy) {
	    	 try{
	 			if(db !=null) db.getWritableDatabase();
	 			return true;
	 		}catch(Exception e2){ }
	    	 return false;
	     }
	     protected void onPostExecute(Boolean result) {
	         if(!result) alert("Cannot access scanning history",true);
	     }
	 }
	public void closeDB(){
		if(this.db !=null) this.db.close();
	}

	public long insertEntry(String description, String type, Result result){
		try {
			InsertTask inTask = new InsertTask();
			inTask.execute(new HistoryDbEntry(0,System.currentTimeMillis(),
										description, type, result));
			Long newId = inTask.get();
			return newId;
		} catch (Exception ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
			return -1;
		}
		
	}
	private class InsertTask extends AsyncTask<HistoryDbEntry, Void, Long> {
	     protected Long doInBackground(HistoryDbEntry... entries) {
	    	 if(entries ==null || entries.length<1 || entries[0] ==null) return (long) -1;
	    	 HistoryDbEntry record = entries[0];
	    	 String rawResultPath=storeResult(context, record.getRawResult());
	    	 if (db ==null || rawResultPath==null) {
	    		 return (long) -1;
	    	 }else{
		    	 long newRowId = db.insertEntry(record.getTime(),record.getDescription(),
				 			record.getType(),rawResultPath); 
		    	 return newRowId;
	    	 }
	     }
	     protected void onPostExecute(Long newRowId){
	    	 if(newRowId < 0) alert("Cannot save the scanned 2D barcode");
	    	 onHistoryChanged();
	    	 return;
	     }
	}
	
	public HistoryDbEntry findEntry(long id) {
		HistoryDbEntry entry;
		try {
			FindTask findTask = new FindTask();
			findTask.execute(id);
			entry = findTask.get();
		} catch (Exception ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
			return null;
		}
		return entry;
	}
	private class FindTask extends AsyncTask<Long, Void, HistoryDbEntry> {
		protected HistoryDbEntry doInBackground(Long... id) {
			//Log.d(TAG, "Looking up " + String.valueOf(id[0]));
			if (id[0] < 0) return null;
			long recordId = id[0];
			if (db == null) return null;
			HistoryDbEntry entry = db.findEntry(recordId);
			return entry;
		}
		
	     protected void onPostExecute(HistoryDbEntry entry){
	    	 if(isAdded() && entry == null) alert(getString(R.string.edit_dialog_error));
	     }
	}
	
	
	public void updateEntry(String title, HistoryDbEntry entry) {
		EntryDetail editEntry = new EntryDetail();
		editEntry.title = title;
		editEntry.entry = entry;
		new UpdateTask().execute(editEntry);
		
	}
	private class UpdateTask extends AsyncTask<EntryDetail, Void, Boolean> {
		protected Boolean doInBackground(EntryDetail... editEntry) {
			if(editEntry == null || editEntry.length < 1 || editEntry[0] ==null) return false;
			HistoryDbEntry entry = editEntry[0].entry;
			String newDesc = editEntry[0].title;
			int updateCount = db.updateEntry(entry.getID(), newDesc);
			return (updateCount ==1)? true:false;			
		}
		
	     protected void onPostExecute(Boolean result){
	    	 if(isAdded()){
		    	 if(!result) alert(getString(R.string.edit_dialog_error));
		    	 else{		    	 
	    			 alert(getString(R.string.edit_dialog_saved));
	    			 onHistoryChanged();
		    	 } 
	    	 }
	    	 //UIrefresh();
	     }
	}
	
	private class DeleteTask extends AsyncTask<HistoryDbEntry, Void, Integer> {
	     protected Integer doInBackground(HistoryDbEntry... entry) {
	    	 if(entry == null || entry.length < 1 || entry[0] ==null) return 0;
	    	 
	    	 int numOfDeletedRecords=0;
	    	 // for each db entry, delete db entry then delete file at filepath
	    	 for(int i = 0; i < entry.length; i++){
	    		 //HistoryDbEntry entry = delList.get(i);
	    		 //new DeleteTask().execute(entry);
	    		 HistoryDbEntry entryDelete = entry[i];
	    		 if(entryDelete.getID() <0) continue;
	    		 
	    		 String filePath = entryDelete.getResultFilePath();
	    		 boolean isRecordDeleted=db.deleteEntry(entryDelete.getID());
		    	 File file = new File(filePath);
		    	 if(isRecordDeleted && file !=null && file.exists()) file.delete();
		    	 if(isRecordDeleted) numOfDeletedRecords++;
	    	 }
    		 return numOfDeletedRecords; 	    		 
	     }
	     protected void onPostExecute(Integer result){
	    	 alert( (result >0)? result.intValue()+" selected records deleted" 
	    			 : "Cannot delete the selected records");
	    	 onHistoryChanged();
	    	 UIrefresh();
	     }
	}
	public static String storeResult(Context context, Result result){
		if(context ==null || result ==null) return null;
   	 	serializableResult serialResult = new serializableResult(result);
   	 	java.io.File file=StandardButton.openFile(context, "Scanned barcode", "file", ".javaObj", true);   	 
   	 	if(file ==null) return null;
   	 	ObjectOutputStream fos = null;
   	 	String filePath =null;
	    try{
	      fos = new ObjectOutputStream(new FileOutputStream(file));
	      fos.writeObject(serialResult);
	      filePath = file.getAbsolutePath();
	    }catch(IOException e2){ 
	    	Log.d(TAG, "Something wrong on store result : "+e2.toString()+e2.getMessage());
	    }
    	try{
    		if(fos !=null) fos.close();
    	}catch(IOException ioe){ }
	    return filePath;
	}
	public static Result getResult(Context context, String filePath){
		if(context ==null || filePath ==null) return null;
		File file = new File(filePath);
		if(file ==null || !file.exists()) return null;
	    ObjectInputStream fis = null;	    
	    Result result=null;
	    try{	    	
	    	fis = new ObjectInputStream(new FileInputStream(file));	    	
	    	//TODO: if we update the structure of serializableResult, how to read back the old objects?
	    	//If the object structure, somehow the reading will throw IOException when calling readOject()
	    	Object object = fis.readObject();
	    	serializableResult serialResult = (serializableResult) object;
	    	result=serializableResult.getResultFromSerializableResult(serialResult);	    	
		}catch(IOException e2){ }
	    catch(RuntimeException e2){ } 
	    catch(ClassNotFoundException e2){ }	    
	    try{
    		if(fis !=null) fis.close();
    	}catch(IOException ioe){ }
	    return result;
	}	
	
//Display related functions	
	@Override
	public void onResume(){
		super.onResume();		
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
			ActionBar actionBar = this.context.getActionBar();			
			if(actionBar !=null){
				previousTitle=(actionBar.getTitle() !=null)? actionBar.getTitle().toString() : null;
				actionBar.setTitle( (this.db ==null || !this.db.isEncode)? "Scanning History":"Encode History");
			}else{
				android.support.v7.app.ActionBar aBar = this.fragmentCallback.getSupportActionBar();
				if(aBar !=null){
					previousTitle=(aBar.getTitle() !=null)? aBar.getTitle().toString() : null;	
					aBar.setTitle( (this.db ==null || !this.db.isEncode)? "Scanning History":"Encode History");
				}
			}
		}
		UIrefresh();
	}
	@Override
	public void onPause(){
		super.onPause();			
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
			ActionBar actionBar = this.context.getActionBar();			
			if(actionBar !=null) actionBar.setTitle(previousTitle);
			else{
				android.support.v7.app.ActionBar aBar = this.fragmentCallback.getSupportActionBar();
				if(aBar !=null) aBar.setTitle(previousTitle);			
			}
		}
	}
	private void UIrefresh(){
		ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.history_progressbar);
		progressBar.setVisibility(View.VISIBLE);
		HistoryDbEntry[] entries=(this.db ==null)? null : this.db.getLatestEntries(numberOfDisplayEntry);
		if(entries ==null || entries.length <1) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this.context);
		    final fragmentCallback fragCallback = fragmentCallback;
		    builder.setTitle(getString(R.string.app_name));
		    builder.setMessage("No history is available");
		    builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					fragCallback.onFatalErrorHappen(TAG);
				}
			});
		    builder.show();
		    return;
		}

		adapter = new HistoryFragAdapter(this.context, entries, this);
		listView = (ListView) getView().findViewById(R.id.history_listView);
		listView.setAdapter(adapter);
		//listView.setOnItemLongClickListener(adapter);
		listView.setOnItemClickListener(adapter);
		listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		listView.setMultiChoiceModeListener(new MultiChoiceListener());
		progressBar.setVisibility(View.GONE);
	}
	
	public void onHistoryEntrySelected(HistoryDbEntry entry){
		// show details if in single select mode
		if (singleSelect) {
			alert("Loading the selected record");
			//To make sure the alert comes before the AsyncTask
			try{
			Thread.sleep(100);
			} catch (InterruptedException e) {}				

			EntryDetail historyEntry = new EntryDetail();
			historyEntry.entry = entry;
			new showHistoryTask().execute(historyEntry);	
		}
		// else long select will be handled by onItemCheckedStateChanged
	}
	private class showHistoryTask extends AsyncTask<EntryDetail, Void, ResultFragment>{
	     protected ResultFragment doInBackground(EntryDetail... entries) {
	    	 if(entries ==null || entries.length<1 || entries[0].entry ==null) return null;
	    	 HistoryDbEntry entry = entries[0].entry;
	    	 Result rawResult = (entry ==null)? null: (entry.getRawResult() !=null)? entry.getRawResult(): 
	 								getResult(context, entry.getResultFilePath());
	    	 if(rawResult==null) return null;
	    	 
	    	 ResultFragment resultFragment = new ResultFragment();
	    	 resultFragment.setEntry(entry,showEncodeHistory);

	    	 //TODO: make it Parcelable (can be passed by set Arguments)
	    	 resultFragment.setResult(rawResult, true);
	    	 return resultFragment;
	     }

	     protected void onPostExecute(ResultFragment result){
	    	 if(result ==null) alert("Cannot locate the selected record.");
	    	 else if(fragmentCallback !=null) 
	    		 fragmentCallback.moveToFragment(TAG, result);
	     }
	}
	
	public void onHistoryEntryLongSelected(final HistoryDbEntry entry, int position){
		if(entry == null) alert("No item is selected");
		else{              			
			singleSelect = false;
			mSelectedEntries.add(entry);
			listView.setItemChecked(position, true);
			if(mActionMode==null) 
				mActionMode = getActivity().startActionMode(new MultiChoiceListener());			
			// set to multi choice for delete
			if(listView.getChoiceMode() != ListView.CHOICE_MODE_MULTIPLE_MODAL){
				listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
				singleSelect = false;
				mSelectedEntries.add(entry);
				listView.setItemChecked(position, true);
			}
		}
	}
		
	// delete selected items
	public void onDeleteSelected(final ArrayList<HistoryDbEntry> delList) {
		final int checkedCount = listView.getCheckedItemCount();
		AlertDialog.Builder builder = new AlertDialog.Builder(this.context);
	    builder.setTitle("Confirm delete");
	    builder.setMessage("Confirm delete selected " + Integer.toString(checkedCount) + " items");
	    builder.setPositiveButton(getText(R.string.gen_continue), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				//async delete
				new DeleteTask().execute(delList.toArray(new HistoryDbEntry[0]));
			}
		});
	    builder.setNegativeButton(getText(R.string.gen_cancel), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
	    builder.show();
		//show delete button.	
	}

	// set to single select mode after multi select finished
	public void setSingleSelect() {
		singleSelect = true;
		listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		listView.setSelection(-1);
		mSelectedEntries.clear();		
	}
	
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
			int checkedCount = listView.getCheckedItemCount();
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
			mActionMode = null;
			adapter.removeSelection();
		}

		@Override
		public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked){
			// update counter in header
	        adapter.toggleSelection(position);	        
	        //update menu 
        	mode.invalidate();	
		}
	}
	
	//Add a menu item to perform the history upload
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		//if(edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer)
			this.setHasOptionsMenu(true);
	}
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
	    super.onCreateOptionsMenu(menu, inflater);
	    if(menuToDelete !=null){
			for(int i=0,l=menuToDelete.length;i<l;i++){
				if(menuToDelete[i] >0) menu.removeItem(menuToDelete[i]);
			}
		}
	    if(!edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer) return;
	    MenuItem item=menu.add(R.string.gen_upload);	
	    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);	
	    item.setNumericShortcut('1');
	    syncMenuID=item.getItemId();
	    MenuItem item2=menu.add(R.string.history_visit);	
	    item2.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
	    item2.setNumericShortcut('2');
	    visitMenuID=item2.getItemId();
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		if(!edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer) return false;
		int id = item.getItemId();
		char shortcut=item.getNumericShortcut();		
		//As item in different menus/action bar may share the same id, we need to use other method to distinguish them
		if(id ==visitMenuID && shortcut =='2'){
			Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
					Uri.parse(uploadURL));        
			startActivity(browserIntent);
			return true;
		}
		if(id ==syncMenuID && shortcut =='1'){
			AlertDialog.Builder builder = new AlertDialog.Builder(this.context);
			builder.setTitle(getString(R.string.gen_upload)+" History");
			builder.setMessage(R.string.confirm_history_upload);		
			builder.setPositiveButton(getString(R.string.gen_continue), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which){
					SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 
							android.content.Context.MODE_PRIVATE); 
					Long lastUpdateTime = prefs.getLong((db.isEncode)? EncodeUpName : ScanUpName, 0);
					Long lastChangeTime = prefs.getLong((db.isEncode)? EncodeChangeName : ScanChangeName, 0);
					Log.d(TAG,"Last update time:"+lastUpdateTime);
					if(lastChangeTime >lastUpdateTime){
						//TODO: How to validate the Google+ account?
						SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
						String userEmail = sharedPref.getString(getString(R.string.pref_login_email), null);
						alert("Start uploading");
						new uploadHistoryTask().execute(new String[] { userEmail, ""+lastUpdateTime });
					}else alert("The history on server is up to date");
					dialog.dismiss();					
				}
			});
		    builder.setNegativeButton(getString(R.string.gen_cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
					//hide keyboard
				}
			});
		    //show keyboard
		    builder.create().show();
		    return true;
		}
		return false;
	}
	private void onHistoryChanged(){
		if(!edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer) return;
		//Update the last update time in shared preference
	    SharedPreferences.Editor editor = this.context.getSharedPreferences(PREFS_NAME, 
	    		android.content.Context.MODE_PRIVATE).edit();
	    //Divide the time by 1000 to reduce the size of time, it is OK as long as it is used here only
	    if(this.db.isEncode) editor.putLong(EncodeChangeName, System.currentTimeMillis()/1000);
	    else editor.putLong(ScanChangeName, System.currentTimeMillis()/1000);
	    editor.commit();
	}
	
	//Upload the whole history to the server
	private class uploadHistoryTask extends AsyncTask<String, Void, Boolean>{
	     protected Boolean doInBackground(String... userEmailAndLastUpdateTime){
	    	 if(userEmailAndLastUpdateTime ==null || userEmailAndLastUpdateTime.length<1 
	    			 || userEmailAndLastUpdateTime[0].isEmpty()) return false;
	    	 String userEmail = userEmailAndLastUpdateTime[0];
	    	 Long lastUpdateTime = Long.parseLong(userEmailAndLastUpdateTime[1]);
	    	 //Read records from the DB and package it as a JSON array
	    	 JSONArray inputs=new JSONArray();		
	    	 HistoryDbEntry[] entries=db.getEntriesLaterThan(lastUpdateTime); //Upload all records
	    	 if(entries ==null || entries.length <1) return false; //No record to update
	    	 for(int i=0,l=entries.length;i<l;i++){
	    		 HistoryDbEntry entry=entries[i];
	    		 if(entry !=null){
	    			 try{
	    				 JSONObject ent = new JSONObject();
		    			 ent.putOpt("id", entry.getID());
		    			 ent.putOpt("date", entry.getDate() );
		    			 Log.d(TAG,i+"Entry time: "+entry.getTime());
		    			 ent.putOpt("title", entry.getDescription());
		    			 ent.putOpt("type", entry.getType());
		    			 ent.putOpt("handset", android.os.Build.BRAND + '-' + android.os.Build.MODEL);
		    			 Result rawResult = (entry.getRawResult() !=null)? entry.getRawResult(): 
								getResult(context, entry.getResultFilePath());
		    			 ent.putOpt("result", serializableResult.toJSONString(new serializableResult(rawResult)));
		    			 if(ent.length()>3) inputs.put(i, ent);
	    			 }catch(JSONException e2){ }//Ignore the content we cannot insert
	    		 }
	    	 }
	         //Upload the data
	         javax.net.ssl.HttpsURLConnection urlConnection = trustSocketFactory.getSSLConnection(context, 
	        		 (db.isEncode)? uploadURL + "?action=uploadResult&isEncode=True&email=" + userEmail 
	        		  : uploadURL + "?action=uploadResult&email=" + userEmail);
	         if(urlConnection ==null){
	        	 Log.d(TAG,"Cannot post data to the server");
	        	 return false;
	         }
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
		        	 InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
		        	 String response=UpdateDigitalCertService.convertInputStreamToString(inputStream);
		        	 //Log.d(TAG,response);
		        	 if(response.compareTo("while(1):{\"result\":\"success\"}") ==0) return true;
		        	 return false;
		             //String response = convertInputStreamToString(inputStream);
		         }
	         }catch (IOException e) {
	        	 Log.d(TAG,"Cannot post data to the server");
	        	 return false;
	         }
	         urlConnection.disconnect();
	    	 return false;
	     }

	     protected void onPostExecute(Boolean isDone){
	    	if(!isDone){
	    		alert("Cannot upload record to the server");
	    		return;
	    	}
	    	SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, 
	 	    		android.content.Context.MODE_PRIVATE).edit();
		    //Divide the time by 1000 to reduce the size of time, it is OK as long as it is used here only
	 	    if(db.isEncode) editor.putLong(EncodeUpName, System.currentTimeMillis()/1000);
	 	    else editor.putLong(ScanUpName, System.currentTimeMillis()/1000);
	 	    editor.commit();
	    	alert("History is uploaded to the server.");
	     }
	}
}