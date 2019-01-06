/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.history;

import java.util.ArrayList;

import edu.cuhk.ie.authbarcodescanner.android.HistoryFragment;
import edu.cuhk.ie.authbarcodescanner.android.R;
import android.content.Context;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class HistoryFragAdapter extends ArrayAdapter<HistoryDbEntry> implements 
		OnItemClickListener, OnItemLongClickListener {
	private static final String TAG=HistoryFragAdapter.class.getSimpleName();
	
	private final Context context;
	private final HistoryDbEntry[] entries;
	private final HistoryFragment callback;
	private ArrayList<HistoryDbEntry> mSelectedItemsIds;
	
	public HistoryFragAdapter(Context source, HistoryDbEntry[] entries, HistoryFragment callback){
		super(source, R.layout.fragment_history_item, entries);
		this.context = source;
		this.entries=entries;
		this.callback=callback;
		mSelectedItemsIds = new ArrayList<HistoryDbEntry>();
	}
	
	public View getView(int position, View convertView, ViewGroup parent){
		ViewHolder holder;
		if(convertView ==null){
			convertView=View.inflate(context,R.layout.fragment_history_item, null);
			holder=new ViewHolder();
			holder.title = (TextView) convertView.findViewById(R.id.history_firstLine);
			holder.type = (TextView) convertView.findViewById(R.id.history_secondLine);
			holder.date = (TextView) convertView.findViewById(R.id.history_date);
			convertView.setTag(holder);
		}else holder = (ViewHolder) convertView.getTag(); 
		if(position <0 || position >= entries.length) return null;
		HistoryDbEntry entry = entries[position];
		holder.title.setText(entry.getDescription());
		holder.type.setText(entry.getType());
		holder.date.setText(entry.getDate());
		return convertView;
	}
	private static class ViewHolder {
	    public TextView title, type, date;
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id){
		if(position <0 || position >= entries.length) return;
		HistoryDbEntry entry = entries[position];
		if(callback !=null) callback.onHistoryEntrySelected(entry);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id){
		if(position <0 || position >= entries.length) return false;
		// sends data for delete dialog
		HistoryDbEntry entry = entries[position];
		if(callback !=null) callback.onHistoryEntryLongSelected(entry, position);
		return true;
	}
	
	public void toggleSelection(int position) {
		HistoryDbEntry entry = entries[position];
		selectView(entry);
	}
	
	public void removeSelection() {
		mSelectedItemsIds = new ArrayList<HistoryDbEntry>();
		notifyDataSetChanged();
	}
	
	public void selectView(HistoryDbEntry entry) {
		if(mSelectedItemsIds.contains(entry)) 		
			mSelectedItemsIds.remove(entry);
		else mSelectedItemsIds.add(entry);		
		notifyDataSetChanged();
	}
	
	public ArrayList<HistoryDbEntry> getSelectedList() {
		return mSelectedItemsIds;
	}
}