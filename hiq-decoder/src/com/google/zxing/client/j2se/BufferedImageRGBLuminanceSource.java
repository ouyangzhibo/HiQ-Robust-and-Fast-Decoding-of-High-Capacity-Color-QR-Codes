package com.google.zxing.client.j2se;

import java.awt.image.BufferedImage;

import com.google.zxing.RGBLuminanceSource;

public class BufferedImageRGBLuminanceSource {

	public RGBLuminanceSource getRGBLuminanceSource(BufferedImage bufferedImage) {
		
		int width = bufferedImage.getWidth();
	    int height = bufferedImage.getHeight();
	    int[] pixels = new int[width * height];
	    
	    bufferedImage.getRGB(0, 0, width, height, pixels, 0, width);
	    
	    return new RGBLuminanceSource(width, height, pixels);
	}
	
}
