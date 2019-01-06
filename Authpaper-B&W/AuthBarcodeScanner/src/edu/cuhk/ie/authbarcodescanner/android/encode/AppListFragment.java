/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.encode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.cuhk.ie.authbarcodescanner.android.EncodeFragment;
import edu.cuhk.ie.authbarcodescanner.android.fragmentCallback;
import edu.cuhk.ie.authbarcodescanner.android.R;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 * This fragment handles listing and selecting an application.
 */
public class AppListFragment extends Fragment {
	private static final String TAG=AppListFragment.class.getSimpleName();	
	private static final String[] BLACKLIST = {
	      "com.android.",
	      "android",
	      "com.htc",
	      "com.samung"
	  };
	
	private Activity context=null;
	private fragmentCallback fragmentCallback=null;
	private listAppTask listAppTask=null;
	
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
		View rootView = inflater.inflate(R.layout.fragment_applist, container,false);
		return rootView;
	}
	@Override
	public void onResume(){
		super.onResume();
		ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.applist_progressbar);
		progressBar.setVisibility(View.VISIBLE);
		listAppTask=new listAppTask();
		listAppTask.execute();
	}
	public void onPause(){
		super.onPause();
		if(listAppTask !=null && (listAppTask.getStatus()==AsyncTask.Status.PENDING 
			|| listAppTask.getStatus()==AsyncTask.Status.RUNNING) )
			listAppTask.cancel(true);
	}
	public void returnResult(String packageName){
		if(packageName !=null && !packageName.isEmpty()){
			Fragment target = getTargetFragment();
			int requestCode =  getTargetRequestCode();
			if(target !=null){
				Intent intent = new Intent();
			    intent.putExtra(EncodeFragment.result_text_index, 
			    		"market://details?id=" + packageName);
			    fragmentCallback.onReturnResult(requestCode, EncodeFragment.result_OK, intent);
			}
		} else alert("Selected application is not valid.");
	}
	private class listAppTask extends AsyncTask<Void,Void,List<AppItem>>{
		@Override
		protected List<AppItem> doInBackground(Void... params) {
			List<AppItem> items = new ArrayList<AppItem>();
		    PackageManager pm = context.getPackageManager();
		    Iterable<ApplicationInfo> appInfos = pm.getInstalledApplications(0);
		    for (PackageItemInfo appInfo : appInfos) {
				String name = appInfo.packageName;
				if(isBlackList(name)) continue;
				CharSequence label = appInfo.loadLabel(pm);
				if(label !=null)
					items.add( new AppItem(name,label.toString(),appInfo.loadIcon(pm)) );
		    }
		    Collections.sort(items);
		    return items;
		}
		private boolean isBlackList(String name){
			if(name==null || name.isEmpty()) return true;
			for(String prefix : BLACKLIST)
	          if(name.startsWith(prefix)) return true;
			return false;
		}
		@Override
		protected void onPostExecute(final List<AppItem> results){
			AppListAdapter adapter = new AppListAdapter(context, results);
			ListView listView = (ListView) getView().findViewById(R.id.applist_listView);
			listView.setAdapter(adapter);
			listView.setOnItemClickListener(adapter);
			getView().findViewById(R.id.applist_progressbar).setVisibility(View.GONE);
		}
	}
	private class AppListAdapter extends ArrayAdapter<AppItem> implements 
		OnItemClickListener{
		private final Context context;
		private final List<AppItem> entries;
		
		public AppListAdapter(Context source, List<AppItem> entries){
			super(source, R.layout.fragment_applist_item, entries);
			this.context = source;
			this.entries=entries;
		}
		public View getView(int position, View convertView, ViewGroup parent){
			if(convertView ==null) 
				convertView=View.inflate(context,R.layout.fragment_applist_item, null);
			TextView view=(TextView) convertView.findViewById(R.id.applist_firstLine);
			if(position <0 || position >= entries.size()) return null;			
			AppItem entry = entries.get(position);
			view.setText(entry.label);
			Drawable icon = entry.icon;
			if(icon != null){
				icon.setBounds(0, 0, 128, 128);
				view.setCompoundDrawables(icon,null,null,null);
			}
			return convertView;
		}
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			if(position <0 || position >= entries.size()) return;
			AppItem entry = entries.get(position);
			returnResult(entry.packageName);
		}
	}
	private static class AppItem implements Comparable<AppItem> {
		public final String packageName;
		public final String label;
		public final Drawable icon;
		AppItem(String packageName, String label, Drawable icon) {
			this.packageName = packageName;
    		this.label = label;
    		this.icon = icon;
		}
		@Override
		public int compareTo(AppItem another) {
			return label.compareTo(another.label);
		}
	}
	private void alert(String message){
		if(this.context ==null || message ==null || message.isEmpty()) return;
		Toast toast = Toast.makeText(this.context, message, Toast.LENGTH_SHORT);
		toast.show();
	}
}