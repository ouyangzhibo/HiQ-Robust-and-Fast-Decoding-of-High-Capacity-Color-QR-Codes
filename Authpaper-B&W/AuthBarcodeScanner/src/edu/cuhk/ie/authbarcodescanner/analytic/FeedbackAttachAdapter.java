/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import edu.cuhk.ie.authbarcodescanner.android.R;

public class FeedbackAttachAdapter extends ArrayAdapter<Uri>{
	private static final String TAG=FeedbackAttachAdapter.class.getSimpleName();
	private Context context;
	private final List<Uri> uriStrList;
	
	// uri	
	private String selectedImagePath;
	
	// track selected attachments for delete
	private ArrayList<Uri> mSelectedItem = new ArrayList<Uri>();

	public FeedbackAttachAdapter(Context context, List<Uri> data_list) {
		super(context, R.layout.fragment_attach_item, data_list);
		this.context = context;
		this.uriStrList = data_list;		
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.fragment_attach_item, parent, false);
		TextView filename = (TextView) rowView.findViewById(R.id.att_filename);
		ImageView preview = (ImageView) rowView.findViewById(R.id.att_preview);		

		Uri selectedImageUri = uriStrList.get(position);
		//Log.d(TAG, "URI: " + selectedImageUri.toString());
		selectedImagePath = UriHelper.getPath(context, selectedImageUri);
		//Log.d(TAG, "siPath: " + selectedImagePath);
		if (selectedImagePath != null ) {
			File f = new File(selectedImagePath);
			filename.setText(f.getName());
			// load bitmap efficiently
			loadBitmap(f.getPath(), preview);			
		}else Toast.makeText(context, "Error loading image", Toast.LENGTH_SHORT).show();
		
		return rowView;
	}
	
	// load bitmap efficiently
	public void loadBitmap(String filepath, ImageView imageView) {
	    if (cancelPotentialWork(filepath, imageView)) {
	        final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
	        Bitmap mPlaceHolderBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher);
	        final AsyncDrawable asyncDrawable =
	                new AsyncDrawable(context.getResources(), mPlaceHolderBitmap, task);
	        imageView.setImageDrawable(asyncDrawable);
	        task.execute(filepath);
	    }
	}
	
	// attempt to cancel task if image view is referenced again
	public static boolean cancelPotentialWork(String filepath, ImageView imageView) {
	    final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

	    if (bitmapWorkerTask != null) {
	        final String bitmapData = bitmapWorkerTask.data;
	        // If bitmapData is not yet set or it differs from the new data
	        if (bitmapData == "" || bitmapData != filepath) {
	            // Cancel previous task
	            bitmapWorkerTask.cancel(true);
	        }else return false;
	    }
	    // No task associated with the ImageView, or an existing task was cancelled
	    return true;
	}
	
	
	// retrives async task associated with image view
	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		   if (imageView != null) {
		       final Drawable drawable = imageView.getDrawable();
		       if (drawable instanceof AsyncDrawable) {
		           final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
		           return asyncDrawable.getBitmapWorkerTask();
		       }
		    }
		    return null;
		}	
	
	//async method for decoding bitmaps
	class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewRef;
		private String data;
		
	    public BitmapWorkerTask(ImageView imageView) {
	        // Use a WeakReference to ensure the ImageView can be garbage collected
	    	imageViewRef = new WeakReference<ImageView>(imageView);
	    }
	    
	    // Decode image in background.
	    @Override
	    protected Bitmap doInBackground(String... params) {
	        data = params[0];
	        return decodeSampledBitmap(context.getResources(), data, 50, 50);
	    } 
	    // Once complete, see if ImageView is still around and set bitmap.
	    @Override
	    protected void onPostExecute(Bitmap bitmap) {
	        if(isCancelled()) bitmap = null;
	        if (imageViewRef != null && bitmap != null) {
	            final ImageView imageView = imageViewRef.get();
	            final BitmapWorkerTask bitmapWorkerTask =
	                    getBitmapWorkerTask(imageView);
	            if(this == bitmapWorkerTask && imageView != null)
	                imageView.setImageBitmap(bitmap);
	        }
	    }	    
	}
	
	// manage references from async task to imageview
	static class AsyncDrawable extends BitmapDrawable {
	    private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

	    public AsyncDrawable(Resources res, Bitmap bitmap,
	            BitmapWorkerTask bitmapWorkerTask) {
	        super(res, bitmap);
	        bitmapWorkerTaskReference =
	            new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
	    }

	    public BitmapWorkerTask getBitmapWorkerTask() {
	        return bitmapWorkerTaskReference.get();
	    }
	}
	
	private Bitmap decodeSampledBitmap(Resources res, String path, int reqW, int reqH) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(path, options);
		//Calculate sample size
		options.inSampleSize = calculateInSampleSize(options, reqW, reqH);
		// decode
		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeFile(path, options);
	}
	
	// scale down sample size to reduce memory usage
	public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
    // Raw height and width of image
    final int height = options.outHeight;
    final int width = options.outWidth;
    int inSampleSize = 1;

    if (height > reqHeight || width > reqWidth) {

        final int halfHeight = height / 2;
        final int halfWidth = width / 2;

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while ((halfHeight / inSampleSize) > reqHeight
                && (halfWidth / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
        }
    }

    return inSampleSize;
}	
	
	public void toggleSelection(int position) {
		Uri uri = uriStrList.get(position);
		selectView(uri);
	}
	
	public void selectView(Uri uri) {
		if (mSelectedItem.contains(uri)) mSelectedItem.remove(uri);
		else mSelectedItem.add(uri);
	}	

	public void removeSelection() {
		mSelectedItem = new ArrayList<Uri>();
	}

	public List<Uri> getSelectedList() {
		return mSelectedItem;
	}		
}
