/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.certificate;

import java.util.ArrayList;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import edu.cuhk.ie.authbarcodescanner.android.R;

public class KeystoreAdapter extends ArrayAdapter<CertificateDbEntry> {
	private static final String TAG = KeystoreAdapter.class.getSimpleName();
	
	private Context context;
	private final ArrayList<CertificateDbEntry> entries;
	
	public KeystoreAdapter(Context context, ArrayList<CertificateDbEntry> certList) {
		super(context, R.layout.fragment_certificate, certList);
		Log.d(TAG, "creating new adapter");
		this.context = context;
		this.entries = certList;		
	}
	
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		if (convertView == null) {
			convertView = View.inflate(context, R.layout.listitem_cert, null);
			holder = new ViewHolder();
			holder.title = (TextView) convertView.findViewById(R.id.cert_title);
			holder.date_expire = (TextView) convertView.findViewById(R.id.cert_date_expire);
			convertView.setTag(holder);
		}
		else {
			holder = (ViewHolder) convertView.getTag();
		}
		if (position < 0 || position > entries.size()) return null;
		CertificateDbEntry entry = entries.get(position);

		holder.title.setText(entry.getAlias());
		holder.date_expire.setText(entry.getDateExpire());
		
		return convertView;
	}
	
	private static class ViewHolder {
		public TextView title, date_expire, date_issue;
	}
}
