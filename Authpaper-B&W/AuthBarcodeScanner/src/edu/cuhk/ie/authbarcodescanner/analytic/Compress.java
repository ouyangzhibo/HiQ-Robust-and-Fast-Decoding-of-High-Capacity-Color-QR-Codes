/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import edu.cuhk.ie.authbarcodescanner.android.Log;
import edu.cuhk.ie.authbarcodescanner.android.SendService;

public class Compress {
	private static final String TAG = Compress.class.getSimpleName();
	
	private static final int BUFFER = 1024;
	private ArrayList<String> inputFileList;
	private String zipfile;
	private String extension;
	
	public Compress(ArrayList<String> inputFileList, String filename, String extension) {
		this.inputFileList = inputFileList;
		this.zipfile = filename;
		this.extension = extension;
	}
	
	// Adds file to zip. File must be in analytics dir, so only add the file's name
	public void addFile(File file) {
		inputFileList.add(file.getName());
	}
	
	// Compress the files from inputFileList into zip file
	public boolean zip() {
		try {
			// ensure output directory
			if (SendService.analyticsDir.mkdirs() || SendService.analyticsDir.isDirectory()) {
				String outFile = zipfile + extension.replace(".", "_") + ".zip";
				Log.d(TAG, "Compressing " + String.valueOf(inputFileList.size()) + " images to "+ outFile);		

		    	File zipFile = new File(SendService.analyticsDir, outFile);
		    	
		    	BufferedInputStream input = null;
		    	FileOutputStream outStream = new FileOutputStream(zipFile);
		    	ZipOutputStream zipStream = new ZipOutputStream(new BufferedOutputStream(outStream));
		    	byte data[] = new byte[BUFFER];
		    	
		    	String fileWithExt = "";
		    	for(String inFileName : inputFileList) {
					// don't replace file extension if already supplied
					if (inFileName.indexOf(".") != -1) {
						fileWithExt = inFileName;
					}
					else {
						fileWithExt = inFileName + this.extension;
					}
		    		
		    		File inFile = new File(SendService.analyticsDir, fileWithExt);
		    		
		    		FileInputStream inStream = new FileInputStream(inFile);
		    		input = new BufferedInputStream(inStream, BUFFER);
		    		ZipEntry zipEntry = new ZipEntry(fileWithExt);
		    		zipStream.putNextEntry(zipEntry);
		    		int count;
		    		while((count = input.read(data, 0, BUFFER)) != -1) {
		    			zipStream.write(data, 0, count);
		    		}
		    		input.close();
		    	}
		    	zipStream.close();
				// remove original files
		    	// return removeImageFiles() ? true : false;
		    	Log.d(TAG, "Compression complete");
		    	return true;
			}
			else {
				throw new IOException("Failed to create temp directory");
			}
		}
		catch(IOException e) {
			Log.e(TAG, "IOException");
			e.printStackTrace();
		}		
		catch(Exception e) {
			Log.e(TAG, "Exception");
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean removeImageFiles() {
		Log.d(TAG, "Clearing " + this.extension + " files");
		try {
			if (SendService.analyticsDir.mkdirs() || SendService.analyticsDir.isDirectory()) {
				for (String filename : inputFileList) {
					String fileWithExt = filename + this.extension;
					File image = new File(SendService.analyticsDir, fileWithExt);
					if (image.exists()) {
						boolean status = image.delete();
						if (!status) {
							throw new IOException("Failed to delete " + fileWithExt);
						}						
					}
				}
				return true;
			}
			else {
				throw new IOException("Failed to find temp directory");
			}			
		}
		catch(IOException e) {
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
		}
		catch(Exception e) {
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
		}
		return false;
	}
}
