/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.certificate;

import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;
import edu.cuhk.ie.authbarcodescanner.android.R;

public class CertExpListAdapter extends BaseExpandableListAdapter {
	private static final String TAG = CertExpListAdapter.class.getSimpleName();
	
	private Context context;
	private List<String> listDataHeader;
	private HashMap<String, String> listDataChild;

	public CertExpListAdapter(Context context, List<String> listDataHeader, HashMap<String, String> listDataChild) {
		this.context = context;
		this.listDataHeader = listDataHeader;
		this.listDataChild = listDataChild;
	}
	
	@Override
	public View getGroupView(int groupPosition, boolean isExpanded,
			View convertView, ViewGroup parent) {
		String headerContent = (String) getGroup(groupPosition);
		if (convertView == null) {
			convertView = View.inflate(context, R.layout.listgroup_cert, null);
		}
		
		TextView title = (TextView) convertView.findViewById(R.id.lg_title);
		title.setText((groupPosition == 0) ? context.getText(R.string.lb_issued_to) : context.getText(R.string.lb_issued_by));
		
		TextView content = (TextView) convertView.findViewById(R.id.lg_content);
		content.setText(headerContent);
		
		return convertView;
	}

	@Override
	public View getChildView(int groupPosition, int childPosition,
			boolean isLastChild, View convertView, ViewGroup parent) {
		final String childText = (String) getChild(groupPosition, childPosition);
		if (convertView == null) {
			convertView = View.inflate(context, R.layout.listgroup_cert, null);
		}
		TextView title = (TextView) convertView.findViewById(R.id.lg_title);
		title.setText((groupPosition == 0) ? context.getText(R.string.lb_issued_to) : context.getText(R.string.lb_issued_by));
		
		TextView content = (TextView) convertView.findViewById(R.id.lg_content);
		content.setText(childText);		
		
		return convertView;
	}	
	
	
	@Override
	public int getGroupCount() {
		return this.listDataHeader.size();
	}

	@Override
	public int getChildrenCount(int groupPosition) {
		return 1;
	}

	@Override
	public Object getGroup(int groupPosition) {
		return this.listDataHeader.get(groupPosition);
	}

	@Override
	public Object getChild(int groupPosition, int childPosition) {
		return this.listDataChild.get(this.listDataHeader.get(groupPosition));
	}

	@Override
	public long getGroupId(int groupPosition) {
		return groupPosition;
	}

	@Override
	public long getChildId(int groupPosition, int childPosition) {
		return childPosition;
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isChildSelectable(int groupPosition, int childPosition) {
		return true;
	}

}
