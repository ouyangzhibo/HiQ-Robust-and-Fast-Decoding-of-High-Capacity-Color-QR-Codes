/**
 * 
 * Copyright (C) 2015 Marco in MobiTeC, CUHK
 *
 */
package edu.cuhk.ie.authbarcodescanner.analytic;

import android.graphics.Bitmap;

public class RGBtoVYUConvertor {

	// From: http://stackoverflow.com/questions/5960247/convert-bitmap-array-to-yuv-ycbcr-nv21
	public static byte [] getNV21(int inputWidth, int inputHeight, Bitmap scaled) {
	        int [] argb = new int[inputWidth * inputHeight];
	        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);
	        int yuvSize=inputWidth*inputHeight*3;
	        byte [] yuv = new byte[(yuvSize %2 ==0)? yuvSize>>1 : (yuvSize>>1)+1]; //Divide by two and round up the result
	        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);
	        //scaled.recycle();
	        return yuv;
	    }

	public static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
	        final int frameSize = width * height;

	        int yIndex = 0;
	        int uvIndex =  width * height;
	        int limit=yuv420sp.length;
	        int a, R, G, B, Y,U, V;
	        double Yd,Ud, Vd;
	        int index = 0;
	        for (int j = 0; j < height; j++) {
	            for (int i = 0; i < width; i++) {

	                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
	                R = (argb[index] & 0xff0000) >> 16;
	                G = (argb[index] & 0xff00) >> 8;
	                B = (argb[index] & 0xff) >> 0;

	                /*
	                // well known RGB to YUV algorithm
	                Y = ( (  66 * R + 129 * G +  25 * B + 128) >> 8) +  16;
	                U = ( ( -38 * R -  74 * G + 112 * B + 128) >> 8) + 128;
	                V = ( ( 112 * R -  94 * G -  18 * B + 128) >> 8) + 128;
	                */
	                // Solon's algorithm
	                Yd = (0.299 * R) + (0.587 * G) + (0.114 * B);
	                Ud = (-0.14713 * R) + ((-0.28886) * G) + (0.436 * B);
	                Vd = (0.615 * R) + ((-0.51499) * G) + ((-0.10001) * B);
	                
	                Y = (int) Yd;
	                U = (int) Ud + 128;
	                V = (int) Vd + 128;
	                
					if(yIndex >=frameSize){
	                	yuv420sp=null; //Something wrong on the array size, return with null
	                	return;
	                }
	                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
	                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
	                //    pixel AND every other scanline.
	                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
	                if( j % 2 == 0 && index % 2 == 0){
	                	if(uvIndex >=(limit-1)){
	                		yuv420sp=null; //Something wrong on the array size, return with null
		                	return;
	                	}
	                    yuv420sp[uvIndex++] = (byte)((V<0) ? 0 : ((V > 255) ? 255 : V));
	                    yuv420sp[uvIndex++] = (byte)((U<0) ? 0 : ((U > 255) ? 255 : U));
	                }
	                index ++;
	            }
	        }
	    }	
	
}
