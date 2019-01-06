/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import java.io.ByteArrayOutputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

public class YUVtoRGBConvertor {
	
	public static byte[] YUVtoRGB(byte[] data, int width, int height) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, width, height, null);
		yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
		byte[] imageBytes = out.toByteArray();
		return imageBytes;
	}
	public static int[] YUVtoRGBpixels(byte[] yuvBytes, int width, int height){
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		YuvImage yuvImage = new YuvImage(yuvBytes, ImageFormat.NV21, width, height, null);
		yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
		byte[] imageBytes=out.toByteArray();
		Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
		int imgWidth=bitmap.getWidth(), imgHeight=bitmap.getHeight();
		yuvImage=null;		
		int[] RGBpixels = new int[imgWidth*imgHeight];
		bitmap.getPixels(RGBpixels, 0, imgWidth, 0, 0, imgWidth, imgHeight);		
		return RGBpixels;
	}
		
	public static int[] YUVtoRGBpixels(byte[] yuvBytes, int width, int height, int left, int top, int right, int bottom){
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		YuvImage yuvImage = new YuvImage(yuvBytes, ImageFormat.NV21, width, height, null);
		yuvImage.compressToJpeg(new Rect(left, top, right, bottom), 100, out);
		byte[] imageBytes=out.toByteArray();
		Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
		int imgWidth=bitmap.getWidth(), imgHeight=bitmap.getHeight();
		yuvImage=null;		
		int[] RGBpixels = new int[imgWidth*imgHeight];
		bitmap.getPixels(RGBpixels, 0, imgWidth, 0, 0, imgWidth, imgHeight);
		return RGBpixels;
	}
	
	/**
	 * Converts YUV420 NV21 to Y888 (RGB8888). The grayscale image still holds 3 bytes on the pixel.
	 * From: http://stackoverflow.com/questions/5272388/extract-black-and-white-image-from-android-cameras-nv21-format/12702836#12702836
	 * 
	 * @param pixels output array with the converted array to grayscale pixels
	 * @param data byte array on YUV420 NV21 format.
	 * @param width pixels width
	 * @param height pixels height
	 */
	public static int[] applyGrayScale(byte[] data, int width, int height) {
	    int p;
	    int size = width*height;
	    int[] pixels = new int[size];
	    
	    for(int i = 0; i < size; i++) {
	        p = data[i] & 0xFF;
	        pixels[i] = 0xff000000 | p<<16 | p<<8 | p;
	    }
	    return pixels;
	}	

	private static int convertYUVtoRGB(int y, int u, int v) {
	    int r,g,b;

	    r = y + (int)1.402f*v;
	    g = y - (int)(0.344f*u +0.714f*v);
	    b = y + (int)1.772f*u;
	    r = r>255? 255 : r<0 ? 0 : r;
	    g = g>255? 255 : g<0 ? 0 : g;
	    b = b>255? 255 : b<0 ? 0 : b;
	    return 0xff000000 | (b<<16) | (g<<8) | r;
	}
}
