/*
 Copyright (C) 2014 Solon in MobiTeC, CUHK 
 */
package edu.cuhk.ie.authbarcodescanner.android;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import edu.cuhk.ie.authbarcodescanner.android.R;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfDocument.Page;
import android.graphics.pdf.PdfDocument.PageInfo;
import android.os.Build;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.printservice.PrintJob;
import android.util.TypedValue;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * A static class that provide centralized setting on dynamically created buttons 
 * as well as common onclick functions like save bitmap, paint document, open file, etc.
 * @author solon li
 *
 */
public class StandardButton{
	private static final String TAG=StandardButton.class.getSimpleName();
	
	public StandardButton(){}
	/**
	 * Create a standard button in result fragment
	 * @param context 
	 * @param textResID
	 * @param drawableID, optional input 0 if the button does not have any image
	 * @return
	 */
	public static Button resultButton(Context context, int textResID, int drawableID){
		Button button = new Button(context);
		if(textResID !=0){
			button.setText(textResID);
			button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
		}
		button.setBackgroundResource(0);
		//button.setMaxHeight(160);
		//button.setMaxWidth(160);
		if(drawableID !=0) button.setCompoundDrawablesWithIntrinsicBounds(0, drawableID, 0, 0);
		return button;
	}
	public static void appendButtons(Activity context, LinearLayout buttonList, List<Button> buttons){
		if(buttons ==null || buttons.size() <1) return;
		int width=0;
		try{
			Point outSize = new Point();
			context.getWindowManager().getDefaultDisplay().getSize(outSize);
			width=outSize.x;
		} catch(NoSuchMethodError e2){
			width=context.getWindowManager().getDefaultDisplay().getWidth();
		}
		float buttonWidth = width/( buttons.size() + buttonList.getChildCount() );
		for(Button button : buttons){			
			//button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
			button.setWidth((int) (buttonWidth+0.5));
			buttonList.addView(button);
		}
	}
	
	
	public static File saveBitmap(Context context, Bitmap barcodeImage, String folderName, String fileName){
		return saveBitmap(context, barcodeImage, folderName, fileName, false);
	}
	public static File saveBitmap(Context context, Bitmap barcodeImage, String folderName, 
			String fileName, boolean isInternal){
		if(context ==null || barcodeImage ==null || folderName ==null || fileName ==null) return null;
		File barcodeFile = openFile(context, folderName, fileName, ".png", isInternal);
		if(barcodeFile ==null) return null;
	    FileOutputStream fos = null;
	    try {
	      fos = new FileOutputStream(barcodeFile);
	      barcodeImage.compress(Bitmap.CompressFormat.PNG, 0, fos);
	    } catch (FileNotFoundException e2) {return null;} 
	    finally {
	      if (fos != null) {
	        try {
	          fos.close();
	          return barcodeFile;
	        } catch (IOException ioe) { }
	      }
	    }
	    return barcodeFile;
	}
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static boolean paintwebview(fragmentCallback context, WebView resultWebView, String fileName){
		
		// Get a PrintManager instance
	    PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);

	    // Get a print adapter instance
	    PrintDocumentAdapter printAdapter = resultWebView.createPrintDocumentAdapter();

	    // Create a print job with name and adapter instance
	    String jobName = context.getString(R.string.app_name)+"_"+fileName;
	    android.print.PrintJob printJob = printManager.print(jobName, printAdapter,
	            new PrintAttributes.Builder().build());	    
	    // Save the job object for later status checking
	    //mPrintJobs.add(printJob);
	    return true;
	}
	public static File paintImageToPDF(Context context, Bitmap bitmap, String folderName, String fileName){
		return paintImageToPDF(context, bitmap, folderName, fileName, false);
	}	
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static File paintImageToPDF(Context context, Bitmap bitmap, String folderName, 
			String fileName, boolean isInternal){
		if(context ==null || bitmap ==null || fileName ==null 
				|| android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) return null;
		try{
			// Get the print manager.
			PrintedPdfDocument pdf = new PrintedPdfDocument(context, 
									new PrintAttributes.Builder()
										.setColorMode(PrintAttributes.COLOR_MODE_COLOR)
										.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
										.setMinMargins( new PrintAttributes.Margins(1000,1000,1000,1000) )
										.setResolution( new PrintAttributes.Resolution("Local", "Standard", 1200, 1200)) 
										.build());
			PdfDocument.Page page = pdf.startPage(0);
			Canvas canvas = page.getCanvas();
	        /*PageInfo pageInfo = new PageInfo.Builder(resultWebView.getMeasuredWidth(), 
	        		resultWebView.getContentHeight(), 1).create();
	        Page page = pdf.startPage(pageInfo);
	        resultWebView.draw(page.getCanvas());
	        pdf.finishPage(page);*/
	
            // Set page margin
            int titleBaseLine = 36;
            int leftMargin = 27;
            int canvasWidth = canvas.getWidth() - (2*leftMargin);
            int canvasHeight = canvas.getHeight() - (2*titleBaseLine);
            int bitmapHeight = bitmap.getHeight();
            int bitmapWidth = bitmap.getWidth();
            double scale = ((float) canvasWidth) / bitmapWidth;
            int scaledHeight = (int) (bitmapHeight * scale);
            if(scaledHeight < canvasHeight){
            	canvas.drawBitmap(bitmap, new Rect(0,0,bitmapWidth,bitmapHeight),
            			new Rect(leftMargin, titleBaseLine, canvasWidth + leftMargin, scaledHeight + titleBaseLine),
            			null);
            	pdf.finishPage(page);
            }else{
            	for(int i=0, lastDrawedHeight = 0; lastDrawedHeight < bitmapHeight;i++){
            		PdfDocument.Page tempPage = (i==0)? page : pdf.startPage(i);
        			Canvas tempCanvas = (i==0)? canvas : tempPage.getCanvas();
            		int drawingBottom = (int) ( lastDrawedHeight + (canvasHeight / scale) );
            		int canvasBottom = canvasHeight;
            		if(drawingBottom > bitmapHeight) {
            			canvasBottom = (int) ( (bitmapHeight - lastDrawedHeight) * scale );
            			drawingBottom = bitmapHeight;
            		}
            		tempCanvas.drawBitmap(bitmap, new Rect(0,lastDrawedHeight,bitmapWidth,drawingBottom),
                			new Rect(leftMargin, titleBaseLine, canvasWidth + leftMargin, canvasBottom + titleBaseLine),
                			null);
            		pdf.finishPage(tempPage);
            		lastDrawedHeight = drawingBottom;
            	}
            }
            
            File pdfFile = openFile(context, folderName, fileName, ".pdf", isInternal);
            if(pdfFile ==null) return null;
            try {
		        pdf.writeTo(new FileOutputStream(pdfFile));
		    } catch (IOException e2) {return null;} 
            finally {
		        pdf.close();
		        pdf = null;
		    }
            return pdfFile;
		}catch(Exception e2){
			return null;
		}
	}
	public static File openFile(Context context, String folderName, String fileName, String postfix, 
			boolean isInternal){
		return openFile(context,folderName,fileName,postfix,isInternal,true);
	}
	/**
	 * Open a file object for reading a file
	 * @param context
	 * @param folderName
	 * @param fileName
	 * @param postfix
	 * @param isInternal
	 * @return
	 */
	public static File openReadFile(Context context, String folderName, String fileName, String postfix, 
			boolean isInternal){
		try{
			File bsRoot = (!isInternal)? 
					new File(Environment.getExternalStorageDirectory(), context.getString(R.string.app_name))
					:context.getFilesDir();
		    File barcodesRoot = new File(bsRoot, folderName);
		    if (!barcodesRoot.exists() && !barcodesRoot.mkdirs()) return null;
		    File barcodeFile = new File(barcodesRoot, fileName + postfix);	    
		    return barcodeFile;
		}catch(Exception e2){
			return null;
		}
	}
	/**
	 * Open a empty file object for writing, if there is a file on the same path + name, it will be deleted
	 * @param context
	 * @param folderName
	 * @param fileName
	 * @param postfix
	 * @param isInternal
	 * @param avoidDuplicate 
	 * @return
	 */
	public static File openFile(Context context, String folderName, String fileName, String postfix, 
			boolean isInternal, boolean avoidDuplicate){
		try{
			File bsRoot = (!isInternal)? 
					new File(Environment.getExternalStorageDirectory(), context.getString(R.string.app_name))
					:context.getFilesDir();
		    File barcodesRoot = new File(bsRoot, folderName);
		    if (!barcodesRoot.exists() && !barcodesRoot.mkdirs()) return null;
		    File barcodeFile = new File(barcodesRoot, fileName + postfix);
		    if(avoidDuplicate){
		    	int counter=0;
		    	while(barcodeFile.exists()){
		    		barcodeFile = new File(barcodesRoot, fileName + "" + counter + postfix);
		    		counter++;
		    	}
		    }
		    barcodeFile.delete();
		    return barcodeFile;
		}catch(Exception e2){
			return null;
		}
	}
	
}
