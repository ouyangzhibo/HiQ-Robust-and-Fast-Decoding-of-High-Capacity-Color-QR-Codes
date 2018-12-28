/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import edu.cuhk.ie.authbarcodescanner.android.SendService;
import edu.cuhk.ie.authbarcodescanner.android.decodethread.DecodeThreadHandler;

public class ImageTestTask extends AsyncTask<Void, Void, Void> {
	private static final String TAG = ImageTestTask.class.getSimpleName();
	
	@Override
	protected Void doInBackground(Void... v) {
		
		// check if difference does anything
		try {
			Log.d(TAG, "Testing images");
			File image1 = new File(SendService.testDir, "sample1.yuv");
			byte[] imgByte1 = SendService.readFileIntoByteArray(image1);

			Log.d(TAG, "YUV length " + String.valueOf(imgByte1.length));
			
			File imageFile = new File(SendService.testDir, "sample1.jpg");
			createImage(imgByte1, imageFile);
			File outputFile = new File(SendService.testDir, "sample1.hex");
			createHex(imgByte1, outputFile);
			
			
			BitmapFactory.Options option = new BitmapFactory.Options();
			option.inPreferredConfig = Bitmap.Config.ARGB_8888;
			Bitmap bmp = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), option);
			Log.d(TAG, "Read RGB file, H " + String.valueOf(bmp.getHeight()) + ", W " + String.valueOf(bmp.getWidth()));
			
			byte[] restored = RGBtoVYUConvertor.getNV21(bmp.getWidth(), bmp.getHeight(), bmp);
			
			outputFile = new File(SendService.testDir, "check.yuv");
			createYUV(restored, outputFile);
			
			outputFile = new File(SendService.testDir, "check.hex");
			createHex(restored, outputFile);

			imageFile = new File(SendService.testDir, "check.jpg");
			createImage(restored, imageFile);
		}
		catch (IOException e) {
			Log.e(TAG, "Failed to read into byte array");
			e.printStackTrace();
		}    					
		return null;
	}
	
	private void createImage(byte[] imgByte, File output)
	{
		try {
			DecodeThreadHandler.createImageColour(imgByte, 1920, 1080, output);
			//SendService.createImageGray(imgByte, 1920, 1080, output);
		} catch (IOException e) {
			Log.e(TAG, "Failed to create image");
			e.printStackTrace();
		}		
	}
	
	private void createYUV(byte[] imgByte, File output)
	{
		try {
			Log.i(TAG,"Creating YUV");
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(output));
			bos.write(imgByte);
			bos.flush();
			bos.close();
			Log.i(TAG,"Created YUV");
		} catch (FileNotFoundException e) {
			Log.e(TAG, "FileNotFoundException");
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(TAG, "IOEx");
			e.printStackTrace();		
	
		}
	}
	
	private void createHex(byte[] imgByte, File output) 
	{
		Log.i(TAG,"Creating HEX");
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < 100; i++) {
			sb.append(String.format("%02X ", imgByte[i]));
		}
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(output));
			bw.write(sb.toString());
			bw.flush();
			bw.close();			
			Log.i(TAG,"Created HEX");
		} catch (IOException e) {
			Log.e(TAG, "IOException");
			e.printStackTrace();
		}
	}
}
