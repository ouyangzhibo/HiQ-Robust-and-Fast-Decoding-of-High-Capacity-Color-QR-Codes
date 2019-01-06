package com.google.zxing.client.j2se;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.color.RGBColorWrapper;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.ColorQRCodeReader;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.detector.AlignmentPattern;
import com.google.zxing.qrcode.detector.Detector;
import com.google.zxing.qrcode.detector.DetectorColor;
import com.google.zxing.qrcode.detector.FinderPattern;
import com.google.zxing.qrcode.detector.FinderPatternInfo;

public class DecoderTest {

	public static void main(String[] args) throws IOException {
		int[] stats = new int[]{0,0,0};
		for(int i=0; i<100; i++) {
			System.out.println("round "+(i+1));
			boolean[] rst = runTest();
			String a = "";
			for (int j=0; j<rst.length; j++) {
				if(rst[j]) {
					a += j+" "; 
					stats[j] += 1;
				}
			}
		    System.out.println(a);
		}
		System.out.println("stats: "+stats[0]+" "+stats[1]+" "+stats[2]);
		
//		File file = new File("2177.jpg");
//		BufferedImage bufferedImage = null;
//        try {
//            bufferedImage = ImageIO.read(file);  
//        } catch (IOException e) {  
//        	e.printStackTrace();  
//        }
//        detect(bufferedImage);
//        decodePureCQR(bufferedImage);
	}
	
	public static boolean[] runTest() {

//		File file = new File("1.jpg");
		File file = new File("/Users/ouyangzhibo/gitlab/colorqrcode/data/CUHK-CQRC/captured/17.JPG");
		BufferedImage bufferedImage = null;
        try {
            bufferedImage = ImageIO.read(file);  
        } catch (IOException e) {  
        	e.printStackTrace();  
        }
        int width = bufferedImage.getWidth();
	    int height = bufferedImage.getHeight();
	    int[] pixels = new int[width * height];
	    bufferedImage.getRGB(0, 0, width, height, pixels, 0, width);
	    int layerNum = 3;	    
	    RGBColorWrapper colorWrapper = new RGBColorWrapper(pixels, height, width, layerNum);
	    ColorQRCodeReader reader = new ColorQRCodeReader(colorWrapper,layerNum);
	    Result[] results = null;
	    try {
			results = reader.decode(null, null);
		} catch (FormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ChecksumException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    boolean[] rst = new boolean[results.length];
	    for(int i=0; i<rst.length; i++) 
	    	if (results[i] != null)
	    		rst[i] = true;
	    	else
	    		rst[i] = false;
	    return rst;
	}

	public static DecoderResult[] decodePureCQR(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
	    int height = bufferedImage.getHeight();
	    int[] pixels = new int[width * height];
	    bufferedImage.getRGB(0, 0, width, height, pixels, 0, width);
	    int layerNum = 3;
	    RGBColorWrapper colorWrapper = new RGBColorWrapper(pixels, height, width, layerNum, true);
	    DetectorResult detectorResult  = null;
	    try {
	    	DetectorColor detector = new DetectorColor(colorWrapper.getBlackMatrix(), colorWrapper, layerNum);
	    	detectorResult = detector.detect(null);
	    	int dimension = detectorResult.getBits().getWidth();		
			int correctDimension = Decoder.checkDimension(detectorResult.getBits());
			if (correctDimension > 0 && correctDimension != dimension) {
				dimension = correctDimension;
			}
			DetectorResult detectorResultNew = detector.detect(detectorResult, dimension);
			detectorResult = (detectorResultNew != null) ? detectorResultNew : detectorResult;
		} catch (NotFoundException | FormatException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
		}
	    DecoderResult[] decoderResultList = new DecoderResult[layerNum];
	    if (detectorResult != null) {
		    Decoder decoder = new Decoder();
		    if(detectorResult.getChannelBits()!=null) {//new method
				for (int i = 0; i < layerNum; i++) {
					if (colorWrapper.getChannelHint(i)) {
						try{
							decoderResultList[i] = decoder.decode(detectorResult.getChannelBits(i), null, false);
						}catch(FormatException | ChecksumException e){
							decoderResultList[i] = null;
						}
					}else {
						decoderResultList[i] = null;
					}
				}
			}
	    }
	    return decoderResultList;
	}
	
	public static DetectorResult detect(BufferedImage bufferedImage) {
		int width = bufferedImage.getWidth();
	    int height = bufferedImage.getHeight();
	    int[] pixels = new int[width * height];
	    bufferedImage.getRGB(0, 0, width, height, pixels, 0, width);
	    int layerNum = 3;
	    RGBColorWrapper colorWrapper = new RGBColorWrapper(pixels, height, width, layerNum);
	    DetectorResult detectorResult  = null;
	    try {
	    	DetectorColor detector = new DetectorColor(colorWrapper.getBlackMatrix(), colorWrapper, layerNum);
	    	detectorResult = detector.detect(null);
	    	int dimension = detectorResult.getBits().getWidth();		
			int correctDimension = Decoder.checkDimension(detectorResult.getBits());
			if (correctDimension > 0 && correctDimension != dimension 
					&& Math.abs(correctDimension - dimension) < 30) {
				dimension = correctDimension;
			}
			DetectorResult detectorResultNew = detector.detectWithoutClassify(detectorResult, dimension);
			detectorResult = (detectorResultNew != null) ? detectorResultNew : detectorResult;
		} catch (NotFoundException | FormatException e) {
		}
	    return detectorResult;
	}
}
