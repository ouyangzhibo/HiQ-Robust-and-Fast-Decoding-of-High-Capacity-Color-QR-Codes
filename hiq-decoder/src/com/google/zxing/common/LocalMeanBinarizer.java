/*
 * Copyright (C) 2012 Solon Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.common;

import com.google.zxing.Binarizer;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;

/**
 * This class implements a local thresholding algorithm similar to HybridBinarizer from zxing. 
 * But this binarizer is designed for data-dense 2D barcode (at least 121 modules for QR code) in high resolution image.
 * For image with lower resolution, GlobalHistogram or HybridBinarizer should be used instead.
 * 
 * This class extends GlobalHistogramBinarizer. So histogram approach is still used for reading 1D barcode.
 * For methods related to reading 2D barcode, i.e. getBlackMatrix(), new approach is used.
 *    
 * @author solon li
 * First written on March 2013
 */

public final class LocalMeanBinarizer extends GlobalHistogramBinarizer{

	//This class use 32*32 blocks to calculate the mean as threshold. 
	//If the block is within plain region (difference between max and min is small), use mean of thresholds nearby instead.
	//If new threshold is similar with old one, extend the search region and calculate again.
	//If new threshold is still similar with old one, then use the global threshold instead. 
	private final static int BLOCK_SIZE_POWER=5;
	private final static int BLOCK_SIZE=1 << BLOCK_SIZE_POWER; //This value must be smaller than MIN_DIMENSION
	private final static int Min_DIFFERENCE=64;
	private final static int MIN_DIMENSION=605; //Assume each module has 3*3 pixels, QR code with 121 modules should have at least 605*605 pixels
	
	private BitMatrix bitMatrix;
	
	public LocalMeanBinarizer(LuminanceSource source) {
		super(source);
		bitMatrix=null;
	}
	
	@Override
	public Binarizer createBinarizer(LuminanceSource source){
		return new LocalMeanBinarizer(source);
	}
	
	@Override
	public BitMatrix getBlackMatrix() throws NotFoundException {
		if(bitMatrix != null) return bitMatrix;
		
		LuminanceSource source = getLuminanceSource();
		int width = source.getWidth();
	    int height = source.getHeight();
	    if (width >= MIN_DIMENSION && height >= MIN_DIMENSION) {
	      byte[] luminances = source.getMatrix();
	      //Divide the image into blocks of 32*32. Include the round off 
	      int blockWidthNum = width >> BLOCK_SIZE_POWER;
	      if ((width & (BLOCK_SIZE-1)) != 0) blockWidthNum++;
	      int blockHeightNum = height >> BLOCK_SIZE_POWER;
	      if ((height & (BLOCK_SIZE-1)) != 0) blockHeightNum++;
	      int[][] blockThreshold=calculateThresholdInBlock(luminances, width, height, blockWidthNum, blockHeightNum);
	      bitMatrix = getBinaryMatrix(luminances, width, height, blockThreshold, blockWidthNum, blockHeightNum);
	    }
	    else bitMatrix=super.getBlackMatrix();
	    return bitMatrix;
	}

	private int[][] calculateThresholdInBlock(byte[] luminances, int width, int height, int BWnum, int BHnum) throws NotFoundException{
		int[][] meanIuminance=new int[BWnum][BHnum];
		int globalMean=0, globalDivider=BWnum*BHnum;
		boolean isColRounded=((width & (BLOCK_SIZE-1)) != 0)? true:false, isRowRounded=((height & (BLOCK_SIZE-1)) != 0)? true:false;
		//Beware of integer overflow here. Push the offset to the block on next row
		int rowStride=(width-BWnum) << BLOCK_SIZE_POWER;

		//First part of the algorithm is getting the mean luminance for each block
		int testCounter=0;
		for(int row=0, column=0, Boffset=0, globalSum=0;row<BHnum;){
			int meanThreshold=0, Bmax=0, Bmin=255;
			//Beware of the corner case 
			int windowRightBound=((column < BWnum-1) || !isColRounded)? BLOCK_SIZE:(width & (BLOCK_SIZE-1)), windowBottomBound=((row < BHnum-1) || !isRowRounded)? BLOCK_SIZE:(height & (BLOCK_SIZE-1));
			int BtestCounter=0;
			
			for(int i=0,j=0,offset=Boffset,tmp=0;j<windowBottomBound;){
				tmp=(luminances[offset] & 0xFF);
				meanThreshold += tmp;
				if(tmp>Bmax) Bmax=tmp;
				if(tmp<Bmin) Bmin=tmp;
				
				offset++;
				i++;
				if(i >= windowRightBound){
					i=0;
					j++;
					offset+=(width-windowRightBound);
				}
				BtestCounter++;
			}
			
			// a ^ b means a XOR b bitwisely
			if(((column < BWnum-1) && (row < BHnum-1)) || (!isColRounded && !isRowRounded)){
				if((BtestCounter ^ 1 << 10) != 0){ 
					NotFoundException error=NotFoundException.getNotFoundInstance();
			    	error.setErrorMessage("Error in counting loop on the image in block row "+row+" and column "+column+" with BtestCounter as "+BtestCounter);
			        throw error;
				}
				//Calculate the mean and do round off to integer
				meanThreshold=((meanThreshold >> 9 & 0x1) ==1)? (meanThreshold >> 10)+1:(meanThreshold >> 10);	
			}
			else{
				windowRightBound=windowRightBound*windowBottomBound;
				if(BtestCounter != windowRightBound){ 
					NotFoundException error=NotFoundException.getNotFoundInstance();
			    	error.setErrorMessage("Error in counting loop on the image boundary in block row "+row+" and column "+column+" with BtestCounter as "+BtestCounter);
			        throw error;
				}
				//Calculate the mean and do round off to integer
				meanThreshold=Math.round(meanThreshold / windowRightBound);
			}
			globalSum += meanThreshold;
			meanIuminance[column][row]=((Bmax-Bmin)<Min_DIFFERENCE)? -meanThreshold:meanThreshold;
			
			//Moving to the next block
			Boffset+=BLOCK_SIZE;
			column++;
			if(column >= BWnum){
				//divide the mean per row so that the division will not handle too large number. If the globalSum still larger than integer bound, then integer overflow will occur in width*height 
				globalMean += Math.round(globalSum / globalDivider);
				globalSum=0;
				column=0;
				row++;
				Boffset+=rowStride;
			}
			testCounter++;
		}
		if(testCounter != BWnum*BHnum){
			NotFoundException error=NotFoundException.getNotFoundInstance();
	    	error.setErrorMessage("Error in counting blocks on the image with BWnum is "+BWnum+" BHnum is "+BHnum+" and testCounter as "+testCounter);
	        throw error;
		}
		
		//Second part of the algorithm is updating the mean value of plain region by nearby blocks such that it is good to be threshold.
		testCounter=0;
		for(int row=0, column=0;row<BHnum;){
			if(meanIuminance[column][row]<0) 
				meanIuminance[column][row]=getNewMean(meanIuminance, BHnum, BWnum, -meanIuminance[column][row], column, row, 1, globalMean);
			//Moving to the next block
			column++;
			if(column >= BWnum){
				column=0;
				row++;
			}
			testCounter++;
		}
		if(testCounter != BWnum*BHnum){
			NotFoundException error=NotFoundException.getNotFoundInstance();
	    	error.setErrorMessage("Error in updating thresholds in blocks after counting on the image with BWnum is "+BWnum+" BHnum is "+BHnum+" and testCounter as "+testCounter);
	        throw error;
		}
		return meanIuminance;
	}
	
	private static int getNewMean(int[][] m, int BHnum, int BWnum, int oldMean, int x, int y, int radius, int globalMean){
		return getNewMean(m, BHnum, BWnum, oldMean, x, y, radius, 1, globalMean);
	}
	private final static int MAX_BLOCK_Radius=6; //at most 13*13 blocks are used
	private final static int Mean_DIFFERENCE=Min_DIFFERENCE >> 1;
	/**
	 * Calculate the adjusted mean given the old mean and radius. 
	 * Adjusted mean is mean of the values in m around (x,y) within radius, the value in (x,y) is normally not included.
	 * 
	 */
	private static int getNewMean(int[][] m, int BHnum, int BWnum, int oldMean, int x, int y, int radius, int base, int globalMean){
		if(oldMean < 0) oldMean=-oldMean;
		if(x < 0 || y < 0 || x >= BWnum || y >= BHnum || m.length != BWnum || m[0].length != BHnum) return oldMean;
		//if largest radius cannot go out of the plain region, use global mean instead.
		if(radius > MAX_BLOCK_Radius || radius < 1) return globalMean;
		
		int newMean=0; //divide 4 by default
		//We do it in this way, calculate the mean of the boundary of the blocks with given radius and use average of it and old mean new mean, if it is different from the original one.
		//If not, use the average as old mean, increase radius by 1, and pass to the same function again
		//TODO: simplify the following
		int divider=(radius << 3)+base;
		if(y >= radius && x >= radius && x < (BWnum-radius) && y < (BHnum-radius)){
			for(int i=-radius;i<radius;i++)
				newMean+= simpleAbs(m[x-radius][y+i]) + simpleAbs(m[x+i][y+radius]) + simpleAbs(m[x+radius][y+(i+1)]) + simpleAbs(m[x+(i+1)][y-radius]);  
			newMean=Math.round((newMean+oldMean*base) / divider);
		} else newMean=oldMean;
		if(simpleAbs(newMean-oldMean)<Mean_DIFFERENCE) return getNewMean(m,BHnum,BWnum,newMean,x,y,++radius,divider,globalMean);
		return newMean;
	}
	private static int simpleAbs(int a){
		return (a<0)? -a:a;
	}
	
	private static BitMatrix getBinaryMatrix(byte[] luminances, int width, int height, int[][] m, int BWnum, int BHnum) throws NotFoundException{
		BitMatrix binaryMatrix=new BitMatrix(width, height);
		
		//Beware of integer overflow
		int totalLength=width*height;
		if(luminances.length != totalLength || m.length != BWnum || m[0].length != BHnum) {
			NotFoundException e = NotFoundException.getNotFoundInstance();
	  		e.setErrorMessage("Cannot pass getBinaryMatrix initial checking with details totalLength : "+totalLength
	  							+" luminances length"+luminances.length+" mLength"+m.length+" m0Length"+m[0].length
	  							+" BWnum"+BWnum+" BHnum"+BHnum);
	  		throw e;
			//return null;
		}
		int successCounter=0;
		for(int row=0,rowCount=0,column=0,colCount=0,i=0,j=0,offset=0;(j<height && row<BHnum && offset<totalLength);){
			//Default is white, only the pixels lower or equal to the threshold will become black
			if ((luminances[offset] & 0xFF) <= m[column][row]) {
				//successCounter++;
				binaryMatrix.set(i,j);
			}
			//move to next pixels. If it belongs to another block then move it to next block
			offset++;
			i++;
			colCount++;
			if(i >= width){
				//Move to next row
				i=0;
				j++;
				rowCount++;
				column=0;
				colCount=0;
			}
			if(colCount >= BLOCK_SIZE){
				column++; 
				colCount=0;
			}
			if((rowCount >= BLOCK_SIZE) || (column >= BWnum)){
				column=0;
				colCount=0;
				rowCount=0;
				row++;
			}
			successCounter++;
		}
		if(successCounter != totalLength){
			NotFoundException error=NotFoundException.getNotFoundInstance();
		  	error.setErrorMessage("Error in creating the binary Matrix. Number of pixels is "+successCounter+" But the width*height is "+width*height);
		      throw error;	
		}
		return binaryMatrix;
	}
}