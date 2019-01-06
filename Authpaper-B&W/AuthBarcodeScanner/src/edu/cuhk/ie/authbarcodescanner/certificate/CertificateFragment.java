/**
 * 
 * Copyright (C) 2014 Solon in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.certificate;


import java.util.ArrayList;
import java.util.Arrays;



import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import edu.cuhk.ie.authbarcodescanner.android.R;
import edu.cuhk.ie.authbarcodescanner.android.StandardFragment;

public class CertificateFragment extends StandardFragment {
	private static final String TAG=CertificateFragment.class.getSimpleName();
	private static final int layoutXML=R.layout.fragment_certificate;
	
	// callback to get keystore from activity
	fragmentKeystoreListener mCallback;
	
	// database
	private CertificateDbHelper certDb;
	private CertificateDbEntry[] certArray;
	
	//array to track selected entries
	private ArrayList<CertificateDbEntry> mSelectedEntries = new ArrayList<CertificateDbEntry>();	
	private ActionMode mActionMode;
	
	// layout items
	private ListView lv_certificate;
	private TextView cert_empty;
	private KeystoreAdapter adapter;	
	
	public CertificateFragment() { 
		setLayout(layoutXML);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState){
		super.onActivityCreated(savedInstanceState);
		lv_certificate = (ListView) getView().findViewById(R.id.cert_listView);
		cert_empty = (TextView) getView().findViewById(R.id.cert_empty);
		
		certArray = (certDb == null) ? new CertificateDbEntry[0] : certDb.getCertList(-1);
		ArrayList<CertificateDbEntry> certList = new ArrayList<CertificateDbEntry>();
		if (certArray != null) {
			certList.addAll(Arrays.asList(certArray));	
		}
		adapter = new KeystoreAdapter(this.context, certList);
		lv_certificate.setAdapter(adapter);
		lv_certificate.setEmptyView(cert_empty);
		//Does not support deletion in the free version
		if(edu.cuhk.ie.authbarcodescanner.android.fragmentCallback.isDeluxeVer){
			lv_certificate.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
			lv_certificate.setMultiChoiceModeListener(new MultiChoiceListener());
		}else lv_certificate.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		lv_certificate.setOnItemClickListener(new ListItemClickListener());
	}
	
	public void refreshUI() {
		Log.d(TAG, "refreshing ui");
		certArray = (this.certDb ==null)? new CertificateDbEntry[0] : this.certDb.getCertList(-1);
		ArrayList<CertificateDbEntry> certList = new ArrayList<CertificateDbEntry>();
		certList.addAll(Arrays.asList(certArray));
		
		adapter.clear();
		adapter.addAll(certList);
		adapter.notifyDataSetChanged();
		//((BaseAdapter) lv_certificate.getAdapter()).notifyDataSetChanged();
	}
	
	public void setDatabase(CertificateDbHelper db) {
		Log.d(TAG, "setting database");
		this.certDb = db;
	}	
	
	public void toggleSelection(int position) {
		CertificateDbEntry entry = certArray[position];
		selectView(entry);
	}
	
	public void selectView(CertificateDbEntry entry) {
		if(mSelectedEntries.contains(entry)) 
			mSelectedEntries.remove(entry);
		else mSelectedEntries.add(entry);
	}	
	
	public void removeSelection() {
		mSelectedEntries = new ArrayList<CertificateDbEntry>();
	}	
	
	public ArrayList<CertificateDbEntry> getSelectedList() {
		return mSelectedEntries;
	}
	
	private class ListItemClickListener implements AdapterView.OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			CertificateDbEntry certDb = (CertificateDbEntry) lv_certificate.getItemAtPosition(position);
			mCallback.listItemClicked(certDb.getAlias());
		}	
	}
		
	// action bar to handle multi deletes
	private class MultiChoiceListener implements MultiChoiceModeListener {
		
		//show contextual action bar
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			Log.d(TAG, "CAB");
			// show delete menu
		    mode.getMenuInflater().inflate(R.menu.delete_selected, menu);
	        mode.setTitle(R.string.title_delete);
	        return true;
		}
		
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			// hide or show menu items based on number of records selected
			int checkedCount = lv_certificate.getCheckedItemCount();	
			mode.setTitle(getText(R.string.title_item_selected) + " " + Integer.toString(checkedCount));
			return true;
		}
		@Override	
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			// delete option
		    switch (item.getItemId()) {
		        case R.id.delete:
		      	  	onDeleteSelected(getSelectedList());
		            mode.finish();
		            return true;		
		        default:
		            return false;
		    }
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mActionMode = null;
			removeSelection();
		}

		@Override
		public void onItemCheckedStateChanged(ActionMode mode, int position,
				long id, boolean checked) {
	        toggleSelection(position);
	        //update menu 
        	mode.invalidate();	
		}
	}

	public void onDeleteSelected(final ArrayList<CertificateDbEntry> selectedList) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this.context);
	    builder.setTitle("Confirm delete");
	    builder.setMessage("Confirm delete selected " + Integer.toString(selectedList.size()) + " certificates? This cannot be undone.");
	    builder.setPositiveButton(getText(R.string.gen_continue), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				//async delete
				new DeleteTask(false).execute(selectedList.toArray(new CertificateDbEntry[0]));
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
	
	// interface to get keystore from activity
	public interface fragmentKeystoreListener {
		public boolean removeFromKeystore(String alias);
		public void listItemClicked(String alias);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
        try {
            mCallback = (fragmentKeystoreListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement fragmentKeystoreListener");
        }
	}
	
	public void deleteDigitalCertFromFilenames(List<String> removedCrts){
		int certArrayLength = certArray.length;
		int removedCrtsLength = removedCrts.size();
		if(removedCrtsLength > 0){
			//Search for every matched certificate from certArray and add them to toBeDeletedCertyList
			ArrayList<CertificateDbEntry> toBeDeletedCertyList = new ArrayList<CertificateDbEntry>();
			for(int j=0; j<removedCrtsLength; j++){
				Log.d(TAG, "Search certArray for alias:! " + removedCrts.get(j));
				for(int i=0; i<certArrayLength; i++){
					Log.d(TAG, "Steps: " + certArray[i].getAlias());
					if( certArray[i].getAlias().contains(removedCrts.get(j).replace(".crt", "") ) ){
						Log.d(TAG, "Alias Matched! " + certArray[i].getAlias());
						toBeDeletedCertyList.add(certArray[i]);
						break;
					}
				}
			}
			//async delete
			new DeleteTask(true).execute(toBeDeletedCertyList.toArray(new CertificateDbEntry[0]));
		}
	}

	private class DeleteTask extends AsyncTask<CertificateDbEntry, Void, Integer> {
		private boolean isDeleteSys=false;
		public DeleteTask(boolean isDeleteSysCert){
			this.isDeleteSys=isDeleteSysCert;
		}
		@Override
		protected Integer doInBackground(CertificateDbEntry... entry) {
			if(entry == null || entry.length < 1 || entry[0] ==null) return 0;
	    	 int numOfDeletedRecords=0;
	    	 // delete certificate from keystore then delete from database
	    	 
	    	 for(int i = 0; i < entry.length; i++){
	    		 if(isDeleteSys || entry[i].getCertType().equals(CertificateDbHelper.CERT_USER)) {
	    			 boolean keyRemove = mCallback.removeFromKeystore(entry[i].getAlias());
	    			 if (keyRemove) {
		    			 //int deleted = certDb.deleteCert(String.valueOf(entry[i].getID()));
						int deleted = certDb.deleteCert(String.valueOf(entry[i].getID()), entry[i].getCertType());
		    			 if(deleted != 1) Log.e(TAG, "Deleted more than one certificate entry");		    			 
		    			 numOfDeletedRecords += deleted;
	    			 }
	    		 }
	    	 }
	    	 return numOfDeletedRecords;
		}
		protected void onPostExecute(Integer result){
	    	 alert( (result >0)? result.intValue()+" selected records deleted" 
	    			 : (isDeleteSys)? "Cannot delete the selected records"
	    			 : "Cannot delete certificates from authpaper server");
	    	 refreshUI();
	     }
	}
}
