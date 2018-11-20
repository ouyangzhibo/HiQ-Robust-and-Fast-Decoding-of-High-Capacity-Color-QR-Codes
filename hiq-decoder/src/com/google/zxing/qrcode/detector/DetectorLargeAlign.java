/*
 * Copyright 2015 Solon and Elky
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
package com.google.zxing.qrcode.detector;

import java.util.Map;

import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.ReaderException;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.PerspectiveTransformGeneral;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.decoder.Version;

/**
 * This class extends the Detector class for QR code with enlarged alignment patterns along the diagonal and anti-diagonal, 
 * as well as one more enlarged alignment pattern on the top of the old bottom-right alignment pattern
 * @author elky creator  
 * @author solon li divide it from Detector, and make it works only on new QR code (old QR code will be handled by the original Detector class) 
 *
 */
public class DetectorLargeAlign extends Detector {

	private static final float alignRadiusBR=16; //arbitrary constant on searching bottom right alignment patterns, since this one is
    											 //much bigger!
	
	public DetectorLargeAlign(BitMatrix image) {
		super(image);
	}
	
	public DetectorResult detect(Map<DecodeHintType,?> hints) throws NotFoundException, FormatException {    
		resultPointCallback = (hints ==null || !hints.containsKey(DecodeHintType.NEED_RESULT_POINT_CALLBACK))? 
				null : (ResultPointCallback) hints.get(DecodeHintType.NEED_RESULT_POINT_CALLBACK);
		FinderPatternInfo info=getFinderPatternInfo(image,hints,resultPointCallback);
	    return processFinderPatternInfoNewNew(info);
	}
	
	/**
	 * calculate bigger alignment patterns in the high-density codes, return their
	 * center positions
	 * 
	 * @param topLeft
	 * @param topRight
	 * @param bottomLeft
	 * @param length
	 * @param moduleSize
	 * @param versionNum
	 * @param dimension
	 * @return the bigger alignment patterns in the QR code
	 */
	private AlignmentPattern[] findBiggerAlignPatterns(FinderPattern topLeft,
			FinderPattern topRight, FinderPattern bottomLeft, int length,
			float moduleSize, int versionNum, int dimension) {
		// compute the scope and the step in x,y directions
		int diagonalNum = length - 2;
		int biggerAlignNum = 0;
		if((versionNum > 34 && versionNum < 41)||(versionNum > 20 && versionNum < 28)||(versionNum > 6 && versionNum < 14))
		{
			biggerAlignNum = 2*diagonalNum - 1; //V35~V40, we need to find 9 diagonal alignment patterns, length = 7;
			                                    //V21~V27, we need to find 5 diagonal alignment patterns, length = 5;
			                                    //V7~V13, we need to find 1 diagonal alignment patterns, length = 3;
		} else if((versionNum > 27 && versionNum < 35)||(versionNum > 13 && versionNum < 21))
		{
			biggerAlignNum = 2*diagonalNum;//V28~V34, we need to find 8 diagonal alignment patterns, length = 6;
			                               //V14~V20, we need to find 4 diagonal alignment patterns, length = 4;
		} 
		//biggerAlignNum = 2*diagonalNum - 1;
		if(biggerAlignNum == 0) return null;
		
		AlignmentPattern[] alignPtns = new AlignmentPattern[biggerAlignNum];
		
		// AlignmentPattern notfound = new AlignmentPattern(0, 0, 0);
		int modulesBetweenFPCenters = dimension - 7;
		float bottomRightX = topRight.getX() - topLeft.getX()
				+ bottomLeft.getX();
		float bottomRightY = topRight.getY() - topLeft.getY()
				+ bottomLeft.getY();

		// Estimate that alignment pattern is closer by 3 modules
		// from "bottom right" to known top left location
		float correctionToTopLeft = 3.0f / (float) modulesBetweenFPCenters;
		int estAlignmentX = (int) (topLeft.getX() + correctionToTopLeft
				* (bottomRightX - topLeft.getX()));
		int estAlignmentY = (int) (topLeft.getY() + correctionToTopLeft
				* (bottomRightY - topLeft.getY()));
		int horizonalMarkerX = estAlignmentX, horizonalMarkerY = estAlignmentY;

		float step1 = (dimension - 14) / (float) (length - 1);
		float step2 = step1;
		if (versionNum == 30) {
			step1 = 20;
			step2 = 26;
		} else if (versionNum == 40) {
			step1 = 24;
			step2 = 28;
		} else if(versionNum == 24) {
			step1 = 22;
			step2 = 26;
		} else if(versionNum == 39){
			step1 = 20;
			step2 = 28;
		} else if(versionNum == 37){
			step1 = 22;
			step2 = 26;
		} else if(versionNum == 36){
			step1 = 18;
			step2 = 26;
		} else if(versionNum == 33){
			step1 = 24;
			step2 = 28;
		} else if(versionNum == 32){
			step1 = 28;
			step2 = 26;
		} else if(versionNum == 31){
			step1 = 24;
			step2 = 26;
		} else if(versionNum == 28){
			step1 = 20;
			step2 = 24;
		} else if(versionNum == 26){
			step1 = 24;
			step2 = 28;
		} else if(versionNum == 22){
			step1 = 20;
			step2 = 24;
		} else if(versionNum == 19){
			step1 = 24;
			step2 = 28;
		} else if(versionNum == 18){
			step1 = 24;
			step2 = 26;
		} else if(versionNum == 16){
			step1 = 20;
			step2 = 24;
		} else if(versionNum == 15){
			step1 = 20;
			step2 = 22;
		} 
		float horizontalX1 = (topRight.getX() - topLeft.getX()) * step1
				/ (float) modulesBetweenFPCenters;
		float horizontalX2 = (topRight.getX() - topLeft.getX()) * step2
				/ (float) modulesBetweenFPCenters;
		float horizontalY1 = (topRight.getY() - topLeft.getY()) * step1
				/ (float) modulesBetweenFPCenters;
		float horizontalY2 = (topRight.getY() - topLeft.getY()) * step2
				/ (float) modulesBetweenFPCenters;
		float verticalX1 = (bottomLeft.getX() - topLeft.getX()) * step1
				/ (float) modulesBetweenFPCenters;
		float verticalX2 = (bottomLeft.getX() - topLeft.getX()) * step2
				/ (float) modulesBetweenFPCenters;
		float verticalY1 = (bottomLeft.getY() - topLeft.getY()) * step1
				/ (float) modulesBetweenFPCenters;
		float verticalY2 = (bottomLeft.getY() - topLeft.getY()) * step2
				/ (float) modulesBetweenFPCenters;
		
		int count = 0;
		for (int i = 0; i < length; i++) {
			float stepX, stepY;
			if (i != 0) {
				if (i == 1) {
					stepX = horizontalX1;
					stepY = horizontalY1;
				} else {
					stepX = horizontalX2;
					stepY = horizontalY2;
				}
				estAlignmentX = (int) (horizonalMarkerX + stepX);
				estAlignmentY = (int) (horizonalMarkerY + stepY);
				horizonalMarkerX = estAlignmentX;
				horizonalMarkerY = estAlignmentY;
			}
			for (int j = 0; j < length; j++) {
				if ((i == 0 && j == length - 1) || (i == 0 && j == length - 2)
						|| (i == length - 1 && j == 0)
						|| (i == length - 1 && j == length - 1)) {
					continue; // finderpattern, step over
				}

				if (j != 0 || (j == 0 && i == 0)) {
					if (j == 0) {
						stepX = verticalX1;
						stepY = verticalY1;
					} else {
						stepX = verticalX2;
						stepY = verticalY2;
					}
					estAlignmentX = (int) (estAlignmentX + stepX);
					estAlignmentY = (int) (estAlignmentY + stepY);
				}
				// Kind of arbitrary -- expand search radius before giving up
				//use the bigger alignment patterns as constraint
				// for V35~V40, we need to find 9 diagonal alignment patterns 
				if(versionNum > 34 && versionNum < 41)
				{
					if((i == 1 && j == 1)||(i == 1 && j == 5)||(i == 2 && j == 2)||(i == 2 && j == 4)
							||(i == 3 && j == 3)||(i == 4 && j == 2)||(i == 4 && j == 4)||(i == 5 && j == 1)||(i == 5 && j == 5))
					{
						for (int k = 4; k <= 8; k <<= 1) {
							try {
								AlignmentPattern temp = findInsideAlignmentInRegion(
										moduleSize, estAlignmentX, estAlignmentY,
										(float) k);
//								// check color constraint
//								if (this.colorWrapper != null) {
//									if (!checkColorConstraint(temp.getX(), temp.getY(),
//											modulesBetweenFPCenters, moduleSize,
//											topLeft, topRight, bottomLeft)) {
//										continue;
//									}
//								}
								alignPtns[count] = temp;
								break;
							} catch (NotFoundException re) {
								// try next round
								alignPtns[count] = null;
							}
						}
						count++;
					}
				} else if(versionNum > 27 && versionNum < 35) // for V28~V34. we need to enlarge 8 inside alignment patterns 
				{
					if((i == 1 && j == 1)||(i == 2 && j == 2)||(i == 3 && j == 3)||(i == 4 && j == 4)||(i == 1 && j == 4)||(i == 2 && j == 3)||(i == 3 && j == 2)||(i == 4 && j == 1))
					{
						for (int k = 4; k <= 8; k <<= 1) {
							try {
								AlignmentPattern temp = findInsideAlignmentInRegion(
										moduleSize, estAlignmentX, estAlignmentY,
										(float) k);
//								// check color constraint
//								if (this.colorWrapper != null) {
//									if (!checkColorConstraint(temp.getX(), temp.getY(),
//											modulesBetweenFPCenters, moduleSize,
//											topLeft, topRight, bottomLeft)) {
//										continue;
//									}
//								}
								alignPtns[count] = temp;
								break;
							} catch (NotFoundException re) {
								// try next round
								alignPtns[count] = null;
							}
						}
						count++;
					}
				} else if(versionNum > 20 && versionNum < 28) // for V21~V27, we need to enlarge 5 inside alignment patterns 
				{
					if((i == 1 && j == 1)||(i == 2 && j == 2)||(i == 3 && j == 3)||(i == 1 && j == 3)||(i == 3 && j == 1))
					{
						for (int k = 4; k <= 8; k <<= 1) {
							try {
								AlignmentPattern temp = findInsideAlignmentInRegion(
										moduleSize, estAlignmentX, estAlignmentY,
										(float) k);
//								// check color constraint
//								if (this.colorWrapper != null) {
//									if (!checkColorConstraint(temp.getX(), temp.getY(),
//											modulesBetweenFPCenters, moduleSize,
//											topLeft, topRight, bottomLeft)) {
//										continue;
//									}
//								}
								alignPtns[count] = temp;
								break;
							} catch (NotFoundException re) {
								// try next round
								alignPtns[count] = null;
							}
						}
						count++;
					}
				} else if(versionNum > 13 && versionNum < 21) // for V14~V20, we need to enlarge 4 inside alignment patterns
				{
					if((i == 1 && j == 1)||(i == 2 && j == 2)||(i == 1 && j == 2)||(i == 2 && j == 1))
					{
						for (int k = 4; k <= 8; k <<= 1) {
							try {
								AlignmentPattern temp = findInsideAlignmentInRegion(
										moduleSize, estAlignmentX, estAlignmentY,
										(float) k);
//								// check color constraint
//								if (this.colorWrapper != null) {
//									if (!checkColorConstraint(temp.getX(), temp.getY(),
//											modulesBetweenFPCenters, moduleSize,
//											topLeft, topRight, bottomLeft)) {
//										continue;
//									}
//								}
								alignPtns[count] = temp;
								break;
							} catch (NotFoundException re) {
								// try next round
								alignPtns[count] = null;
							}
						}
						count++;
					}
				} else if(versionNum > 6 && versionNum < 14) // for V7~V13, we need to enlarge 1 inside alignment pattern
				{
					if(i == 1 && j == 1)
					{
						for (int k = 4; k <= 8; k <<= 1) {
							try {
								AlignmentPattern temp = findInsideAlignmentInRegion(
										moduleSize, estAlignmentX, estAlignmentY,
										(float) k);
//								// check color constraint
//								if (this.colorWrapper != null) {
//									if (!checkColorConstraint(temp.getX(), temp.getY(),
//											modulesBetweenFPCenters, moduleSize,
//											topLeft, topRight, bottomLeft)) {
//										continue;
//									}
//								}
								alignPtns[count] = temp;
								break;
							} catch (NotFoundException re) {
								// try next round
								alignPtns[count] = null;
							}
						}
						count++;
					}
				} 
				
				
			}
//			for (int j = 0; j < length; j++) {
//				if ((i == 0 && j == length - 1) || (i == 0 && j == length - 2)
//						|| (i == length - 1 && j == 0)
//						|| (i == length - 1 && j == length - 1)) {
//					continue; // finderpattern, step over
//				}
//
//				if (j != 0 || (j == 0 && i == 0)) {
//					if (j == 0) {
//						stepX = verticalX1;
//						stepY = verticalY1;
//					} else {
//						stepX = verticalX2;
//						stepY = verticalY2;
//					}
//					estAlignmentX = (int) (estAlignmentX + stepX);
//					estAlignmentY = (int) (estAlignmentY + stepY);
//				}
//				// Kind of arbitrary -- expand search radius before giving up
//				for (int k = 4; k <= 8; k <<= 1) {
//					try {
//						AlignmentPattern temp = findInsideAlignmentInRegion(
//								moduleSize, estAlignmentX, estAlignmentY,
//								(float) k);
////						// check color constraint
////						if (this.colorWrapper != null) {
////							if (!checkColorConstraint(temp.getX(), temp.getY(),
////									modulesBetweenFPCenters, moduleSize,
////									topLeft, topRight, bottomLeft)) {
////								continue;
////							}
////						}
//						alignPtns[count] = temp;
//						break;
//					} catch (NotFoundException re) {
//						// try next round
//						alignPtns[count] = null;
//					}
//				}
//				count++;
//			}
		}
		return alignPtns;
	}
  
	
	public static PerspectiveTransformGeneral createTransformGeneralNew(
			ResultPoint topLeft, ResultPoint topRight, ResultPoint bottomLeft,
			ResultPoint[] alignmentPatterns, ResultPoint alignmentPattern,int alignementIndex,
			int dimension,int versionNum) {
		// set x, coordinates in the real world image
		int numAP = alignmentPatterns.length;
		int nonNullAPNum = 0;
		for (int i = 0; i < numAP; i++) {
			if (alignmentPatterns[i] != null) {
				nonNullAPNum++;
			}
		}
		float[] x = new float[2 * (4 + nonNullAPNum)];
		x[0] = topLeft.getX();
		x[1] = topLeft.getY();
		x[2] = topRight.getX();
		x[3] = topRight.getY();
		x[4] = bottomLeft.getX();
		x[5] = bottomLeft.getY();
		if (alignmentPattern != null) {
			x[6] = alignmentPattern.getX();
			x[7] = alignmentPattern.getY();
		} else {
			// Don't have an alignment pattern, just make up the bottom-right
			// point
			x[6] = (topRight.getX() - topLeft.getX())
					+ bottomLeft.getX();
			x[7] = (topRight.getY() - topLeft.getY())
					+ bottomLeft.getY();
		}
		//x[6] = alignmentPattern.getX();
		//x[7] = alignmentPattern.getY();
		for (int i = 0, j = 0; j < numAP; j++) {
			if (alignmentPatterns[j] != null) {
				x[8 + i * 2] = alignmentPatterns[j].getX();
				x[9 + i * 2] = alignmentPatterns[j].getY();
				i++;
			}
		}

		// set u, coordinates in the bitmaps (virtual image)
		float dimMinusThree = (float) dimension - 3.5f;
		float sourceBottomRightX, sourceBottomRightY;
		//sourceBottomRightX = sourceBottomRightY = dimMinusThree - 3.0f;
		sourceBottomRightX = dimension - 7.5f;
		sourceBottomRightY = dimension - 16.5f;
		float[] u = new float[2 * (4 + nonNullAPNum)];
		u[0] = 3.5f;
		u[1] = 3.5f;// topleft
		u[2] = dimMinusThree;
		u[3] = 3.5f;// topRight
		u[4] = 3.5f;
		u[5] = dimMinusThree;// bottomLeft
		u[6] = sourceBottomRightX;
		u[7] = sourceBottomRightY;// alignment

		//int length = (int) Math.sqrt(numAP + 4);
		int length = alignementIndex; //the number of alignment patterns horizontally OR vertically
		float step1 = (dimension - 13) / (float) (length - 1);
		float step2 = step1;
		if (dimension == 137) {
			step1 = 20;
			step2 = 26;
		} else if (dimension == 177) {
			step1 = 24;
			step2 = 28;
		} else if (dimension == 113) {
			step1 = 22;
			step2 = 26;
		} else if (dimension == 173) {
			step1 = 20;
			step2 = 28;
		} else if(dimension == 165){
			step1 = 22;
			step2 = 26;
		} else if(dimension == 161){
			step1 = 18;
			step2 = 26;
		} else if(dimension == 149){
			step1 = 24;
			step2 = 28;
		} else if(dimension == 145){
			step1 = 28;
			step2 = 26;
		} else if(dimension == 141){
			step1 = 24;
			step2 = 26;
		} else if(dimension == 129){
			step1 = 20;
			step2 = 24;
		} else if(dimension == 121){
			step1 = 24;
			step2 = 28;
		} else if(dimension == 105){
			step1 = 20;
			step2 = 24;
		} else if(dimension == 93){
			step1 = 24;
			step2 = 28;
		} else if(dimension == 89){
			step1 = 24;
			step2 = 26;
		} else if(dimension == 81){
			step1 = 20;
			step2 = 24;
		} else if(dimension == 77){
			step1 = 20;
			step2 = 22;
		} 
		//Add by Solon to simplify the code
		int caseNum = (versionNum > 6 && versionNum < 14)? 1 // for V7~V13, we need to enlarge 1 inside alignment pattern
	    		: (versionNum > 13 && versionNum < 21)? 2  // for V14~V20, we need to enlarge 4 inside alignment patterns
	    		: (versionNum > 20 && versionNum < 28)? 3 // for V21~V27, we need to enlarge 5 inside alignment patterns
	    		: (versionNum > 27 && versionNum < 35)? 4 // for V28~V34. we need to enlarge 8 inside alignment patterns
	    		: (versionNum > 34 && versionNum < 41)? 5 // for V35~V40, we need to enlarge 9 inside alignment patterns
	    		: 0; //For other cases, no need to enlarge any inside alignment pattern
		
		int k = 0, m = 0;
		for (int i = 0; i < length; i++) {
			for (int j = 0; j < length; j++) {
				if ((i == 0 && j == length - 1) || (i == 0 && j == 0)
						|| (i == length - 1 && j == 0)
						|| (i == length - 1 && j == length - 1)) {
					continue; // finderpattern, step over
				}
//				if((i == 1 && j == 1)||(i == 1 && j == 5)||(i == 2 && j == 2)||(i == 2 && j == 4)
//						||(i == 3 && j == 3)||(i == 4 && j == 2)||(i == 4 && j == 4)||(i == 5 && j == 1)||(i == 5 && j == 5))
//				{
//					if (alignmentPatterns[m] != null) {
//						if (i >= 1) {
//							u[8 + k * 2] = 6.5f + step1 + (i - 1) * step2 + 0.5f;//should add 0.5f since I enlarge the size to 2 times
//						} else {
//							u[8 + k * 2] = 6.5f;
//						}
//						if (j >= 1) {
//							u[9 + k * 2] = 6.5f + step1 + (j - 1) * step2 + 0.5f;
//						} else {
//							u[9 + k * 2] = 6.5f;
//						}
//						k++;
//					}
//					m++;
//				}
				
				if( (i==j && caseNum >0 && i<=caseNum) //along the diagonal
		  	        	//Along the anti-diagonal
		  	        	|| (i!=j && caseNum >0 && i==1 && j==caseNum) || (i!=j && caseNum >0 && j==1 && i==caseNum)
		  	        	|| (i!=j && caseNum >3 && i == 2 && j == (caseNum-1))
		  	        	|| (i!=j && caseNum >3 && i == (caseNum-1) && j == 2) ){
					if (alignmentPatterns[m] != null) {
						if (i >= 1) {
							u[8 + k * 2] = 6.5f + step1 + (i - 1) * step2 + 0.5f;//should add 0.5f since I enlarge the size to 2 times
						} else {
							u[8 + k * 2] = 6.5f;
						}
						if (j >= 1) {
							u[9 + k * 2] = 6.5f + step1 + (j - 1) * step2 + 0.5f;
						} else {
							u[9 + k * 2] = 6.5f;
						}
						k++;
					}
					m++;
				}
			}
		}

		return PerspectiveTransformGeneral.getTransform(u, x);
	}
	
  protected final DetectorResult processFinderPatternInfoNewNew(FinderPatternInfo info)
	      throws NotFoundException, FormatException {

		FinderPattern topLeft = info.getTopLeft();
		FinderPattern topRight = info.getTopRight();
		FinderPattern bottomLeft = info.getBottomLeft();
	    NotFoundException error=NotFoundException.getNotFoundInstance();
	    AlignmentPattern alignmentPattern = null;
	    AlignmentPattern oldalignmentPattern = null;
	    BitMatrix bits=null;
	    BitMatrix correctBits=null;
		int dimension=0, modulesBetweenFPCenters=0;
		int oldDimension=0;
		float oldModuleSize = 0;
		float moduleSize =calculateModuleSize(topLeft, topRight, bottomLeft);
	    PerspectiveTransformGeneral transform=null;
	    
	    if(moduleSize < 1.0f){
	        error.setErrorMessage(error.getErrorMessage()+"Cannot calculate module size. What we get is:"+moduleSize);
	        throw error;
	    }
		try{
	        dimension = computeDimension(topLeft, topRight, bottomLeft, moduleSize);
	        Version provisionalVersion = Version.getProvisionalVersionForDimension(dimension);
	        modulesBetweenFPCenters = provisionalVersion.getDimensionForVersion() - 7;
	        // Anything above version 1 has an alignment pattern
	        int alignementIndex = provisionalVersion.getAlignmentPatternCenters().length;
	        if(alignementIndex > 0) {
	        	float blX=bottomLeft.getX(), blY=bottomLeft.getY(), 
	    				tlX=topLeft.getX(), tlY=topLeft.getY(),
	    				trX=topRight.getX(), trY=topRight.getY();
				// Guess where a "bottom right" finder pattern would have been
				float bottomRightX = trX-tlX+blX;
				float bottomRightY = trY-tlY+blY;
				// Estimate that alignment pattern is closer by 3 modules
				// from "bottom right" to known top left location
				float correctionToTopLeft = 1.0f - 3.0f / (float) modulesBetweenFPCenters;
				int estAlignmentX = (int) (tlX + correctionToTopLeft * (bottomRightX - tlX));
				int estAlignmentY = (int) (tlY + correctionToTopLeft * (bottomRightY - tlY));
				//added by elky for the bigger bottom-right alignment pattern detection
				//the estimated location of the new alignment pattern should be changed
				//the X should minus (one module*modulesize) pixels
				//the Y should minus (10 module*modulesize pixels
				int BigestAlignmentX = (int)(estAlignmentX - moduleSize);
				int BigestAlignmentY = (int)(estAlignmentY - 10.0f * moduleSize);
				//in case that the QR code image is reversed
				int tranestAlignmentX = (int)(estAlignmentX - 10.0f * moduleSize);
				int tranestAlignmentY = (int)(estAlignmentY - moduleSize);
			
				if(alignementIndex > 5) {
					//For version 28 or above, we need a better way to get the dimension value : using the timing pattern 
					//Number of alignment patterns along anti-diagonal, excluding finder patterns
					int diagonalAlignNum=alignementIndex-2; 
					//Number of intervals between alignment/finder patterns along the anti-diagonal
					int alignIntevalNum=alignementIndex-1; 
					
					AlignmentPattern[] antiDiagonal=new AlignmentPattern[diagonalAlignNum];
					float antiDiaX=(trX -blX )/alignIntevalNum;
					float antiDiaY=(trY -blY )/alignIntevalNum;
					
					AlignmentPattern[] topLine=new AlignmentPattern[diagonalAlignNum];
					float topLineX=(trX-tlX) /alignIntevalNum;
					float topLineY=(trY-tlY) /alignIntevalNum;
					float topCorrectionX=(blX-tlX)*3.0f/modulesBetweenFPCenters;
					float topCorrectionY=(blY-tlY)*3.0f/modulesBetweenFPCenters;
					
					AlignmentPattern[] leftLine=new AlignmentPattern[diagonalAlignNum];
					float leftLineX=(blX-tlX) /alignIntevalNum;
					float leftLineY=(blY-tlY) /alignIntevalNum;
					float leftCorrectionX=(trX-tlX)*3.0f/modulesBetweenFPCenters;
					float leftCorrectionY=(trY-tlY)*3.0f/modulesBetweenFPCenters;
					
					for(int i=0;i<diagonalAlignNum;i++){
						int step=i+1;
						int antiAlignmentX=(int) (blX + (step*antiDiaX));
						int antiAlignmentY=(int) (blY + (step*antiDiaY));
						int topAlignmentX=(int) (tlX + (step*topLineX) +topCorrectionX);
						int topAlignmentY=(int) (tlY + (step*topLineY) +topCorrectionY);
						int leftAlignmentX=(int) (tlX + (step*leftLineX) +leftCorrectionX);
						int leftAlignmentY=(int) (tlY + (step*leftLineY) +leftCorrectionY);
						
						//Detecting enlarged alignment patterns along the anti-diagonal
						try{
							antiDiagonal[i] = findInsideAlignmentInRegion(moduleSize,antiAlignmentX,antiAlignmentY,alignRadius);
						}catch(NotFoundException re){ }
						try{
							topLine[i] = findAlignmentInRegion(moduleSize,topAlignmentX,topAlignmentY,alignRadius);
						}catch(NotFoundException re){ }
						try{
							leftLine[i] = findAlignmentInRegion(moduleSize,leftAlignmentX,leftAlignmentY,alignRadius);
						}catch(NotFoundException re){}
					}
					try{
					  float[] topLineEqu=bestFitLine(topLine);
					  if(topLineEqu ==null) throw new Exception();
					  float[] leftLineEqu=bestFitLine(leftLine);
					  if(leftLineEqu ==null) throw new Exception();
					  float[] antiDiaEqu=bestFitLine(antiDiagonal);
					  if(antiDiaEqu ==null) throw new Exception();;
					  //Use line of the alignments to find start and end of the timing patterns and use it to estimate correct dimension  
					  ResultPoint topLeftP=intersectionPoint(topLineEqu,leftLineEqu);
					  ResultPoint topRightP=intersectionPoint(topLineEqu,antiDiaEqu);
					  ResultPoint bottomLeftP=intersectionPoint(antiDiaEqu,leftLineEqu);
					  int[] topDimensionAndModule=countTimingPattern(topLeftP,topRightP);
					  int[] leftDimensionAndModule=countTimingPattern(topLeftP,bottomLeftP);
					  int topDimension=topDimensionAndModule[0]+13;
					  int topModule=topDimensionAndModule[1];
					  int leftDimension=leftDimensionAndModule[0]+13;
					  int leftModule=leftDimensionAndModule[1];
					  int checkBit=(topDimension &0x03);
					  topDimension=(checkBit ==1)? topDimension:(checkBit ==0)? topDimension+1
							  		:(checkBit ==2)? topDimension-1:1000;
					  checkBit=(leftDimension &0x03);
					  leftDimension=(checkBit ==1)? leftDimension:(checkBit ==0)? leftDimension+1
							  		:(checkBit ==2)? leftDimension-1:1000;
					  int newDimension=(topDimension<leftDimension)? topDimension:(leftDimension<1000)? leftDimension:dimension;
					  float newModuleSize=(topDimension<leftDimension)? topModule:(leftDimension<1000)?leftModule:moduleSize;
					  newDimension=(newDimension<21)? 21:(newDimension>177)? 177:newDimension;
					  int NewestAlignmentX = (int)(estAlignmentX - newModuleSize);
					  int NewestAlignmentY = (int)(estAlignmentY - 10.0f * newModuleSize);
					  //in case that the image is reversed
					  int tranNewestAlignmentX = (int)(estAlignmentX - 10.0f * newModuleSize);
					  int tranNewestAlignmentY = (int)(estAlignmentY - newModuleSize);
					  
					  //Now detect the enlarged bottom-right alignment pattern again using the correct dimension and module size 
					  //elky: the code above is only used to correct the dimension and module size? If so, they have no need
					  //to be changed at all! (only for the bottom-right alignment pattern detection)
					  //sometimes this method can obtain too abnormal module size, i.e. >100
					  if(newModuleSize < 100){						  
						  alignmentPattern = findBRAlignment(newModuleSize, new int[]{NewestAlignmentX,tranNewestAlignmentX}
						  		, new int[]{NewestAlignmentY,tranNewestAlignmentY}, new float[]{alignRadiusBR/2.0f,alignRadiusBR});
						  if(alignmentPattern !=null){
							  //Use the new dimension and module size value only if we can detect an alignment pattern correctly
							  //first back up the old ones, since we are not sure that the new one is correct. After we do checking, 
							  //and find the new ones are wrong, we can come back to use the old one and do transformation again.
							  oldDimension = dimension;
							  oldModuleSize = moduleSize;
							  dimension=newDimension;
							  moduleSize=newModuleSize;
							  //only when detect the alignment pattern using this method, do transformation
							  //use the old transformation calculation here first, since the old method is less relied on the correct
							  //dimension, and after the transformation, the later on correction can at least get the correct dimension
							  //information from the reconstructed matrix
							  transform = createTransformBiggerAlignment(topLeft, topRight, bottomLeft, alignmentPattern, dimension);
						  }
					  }
					}catch(Exception e){ }
				} //End of getAlignmentPatternCenters().length > 5 if statement
				//If no alignment is detected, just use the original data to do the detection
				if(alignmentPattern==null){
					alignmentPattern = findBRAlignment(moduleSize, new int[]{BigestAlignmentX,tranestAlignmentX}
			  		, new int[]{BigestAlignmentY,tranestAlignmentY}, new float[]{alignRadiusBR/2.0f,alignRadiusBR});
				}
				//If no enlarged bottom-right alignment pattern is detected, detect the original one instead
				if(alignmentPattern==null){
					try{
						oldalignmentPattern = findAlignmentInRegion(moduleSize,estAlignmentX,estAlignmentY,alignRadius);
					}catch(NotFoundException re){ }
				}
	         } //End of alignementIndex > 0 if statement

	        if(transform ==null){
	        	transform = (alignmentPattern==null && oldalignmentPattern != null)?
	        		createTransformGeneral(topLeft, topRight, bottomLeft, oldalignmentPattern, dimension)
	        		:createTransformBiggerAlignment(topLeft, topRight, bottomLeft, alignmentPattern, dimension);
	        }
	        bits = (transform !=null)? sampleGrid(image, transform, dimension):null;
	        
	        //check if the new dimension is significantly wrong and if we can use the old dimension
	        int correctDimension=Decoder.checkDimension(bits);
	        if(correctDimension == -1)//the reconstructed bit matrix cannot even help us figure out the dimension pattern
	        {
	        	//that the dimension is significantly wrong
	        	if(oldDimension != 0)
	        		//if this significantly wrong reconstructed bit matrix is obtained from the "new dimension"
	        		//use the old dimension to try again (with old transformation method first)
	        	{
	        		PerspectiveTransformGeneral correctTransform = (alignmentPattern==null && oldalignmentPattern != null)?
	        				createTransformGeneral(topLeft, topRight, bottomLeft, oldalignmentPattern, oldDimension)
	        				:createTransformBiggerAlignment(topLeft, topRight, bottomLeft, alignmentPattern, oldDimension);
	        		correctBits = (correctTransform !=null)? sampleGrid(image, correctTransform, oldDimension):null;
	        		moduleSize = oldModuleSize;

	        	}
	        }
	        
	    }catch(ReaderException e){
	    	//The error on finder patterns is too large to build a sample grid, so here we modify the finder pattern and search again
	    	if(moduleSize <1.0f) 
	    		error.setErrorMessage(error.getErrorMessage()+" and problem on module size. Detector");
	    	else if(dimension ==0 || (dimension &0x03) !=1)
	    		error.setErrorMessage(error.getErrorMessage()+" and problem on getting dimension. Detector");
	    	else if(transform ==null)
	    		error.setErrorMessage(error.getErrorMessage()+" or there is something wrong on transform. Detector");
	    	//Something wrong on transformation. 
	    	else if(bits ==null) 
	    		error.setErrorMessage(error.getErrorMessage()+" or there is something wrong on sampling. Detector");
	    	throw error;
	    }
		
		if(bits ==null || dimension ==0 || modulesBetweenFPCenters==0 || moduleSize<=1) {
			error.setErrorMessage(error.getErrorMessage()+" all we can do is starting the next round. Detector");
			throw error; 
		}
		
		//comment for desktop testing
		if(resultPointCallback !=null){
			resultPointCallback.findCodeBoundLine(topLeft, topRight);
		    resultPointCallback.findCodeBoundLine(topLeft, bottomLeft);
		    if(alignmentPattern !=null) {
		    	resultPointCallback.foundPossibleResultPoint(alignmentPattern);
		    	resultPointCallback.findCodeBoundLine(topLeft, alignmentPattern);
		    }
		}
		//here in the return result, we need to distinguish the old and new version alignment pattern
		ResultPoint[] points = (alignmentPattern == null)? 
			( (oldalignmentPattern == null)? new ResultPoint[]{bottomLeft, topLeft, topRight}
			: new ResultPoint[]{bottomLeft, topLeft, topRight, oldalignmentPattern} )
			: new ResultPoint[]{bottomLeft, topLeft, topRight, alignmentPattern};
    
		bits = 	(correctBits != null)? correctBits : bits;
		return (alignmentPattern==null)? new DetectorResult(bits, points,moduleSize,false)
			: new DetectorResult(bits, points,moduleSize,true);
	  }
 
  /**
   * createTransformGeneral function with alignmentPattern becomes the enlarged one
   * @param topLeft
   * @param topRight
   * @param bottomLeft
   * @param alignmentPattern
   * @param dimension
   * @return
   */
  public static PerspectiveTransformGeneral createTransformBiggerAlignment(ResultPoint topLeft,
          ResultPoint topRight,
          ResultPoint bottomLeft,
          ResultPoint alignmentPattern,
          int dimension) {
		float dimMinusThree = (float) dimension - 3.5f;
		float bottomRightX;
		float bottomRightY;
		float sourceBottomRightX;
		float sourceBottomRightY;
		if (alignmentPattern != null) {
			bottomRightX = alignmentPattern.getX();
			bottomRightY = alignmentPattern.getY();
			
			//sourceBottomRightX = sourceBottomRightY = dimMinusThree - 3.0f;
			//edit by elky: the larger alignment pattern's location in the bitmatrix has been changed to (dim-7.5,dim-16.5)
			sourceBottomRightX = (float) dimension - 7.5f;
			sourceBottomRightY = (float) dimension - 16.5f;
		}else{
			// Don't have an alignment pattern, just make up the bottom-right point
			bottomRightX = (topRight.getX() - topLeft.getX()) + bottomLeft.getX();
			bottomRightY = (topRight.getY() - topLeft.getY()) + bottomLeft.getY();
			sourceBottomRightX = sourceBottomRightY = dimMinusThree;
		}
		return PerspectiveTransformGeneral.getTransform(
			3.5f,
			3.5f,
			dimMinusThree,
			3.5f,
			sourceBottomRightX,
			sourceBottomRightY,
			3.5f,
			dimMinusThree,
			(int) (topLeft.getX()+0.5f),
			(int) (topLeft.getY()+0.5f),
			(int) (topRight.getX()+0.5f),
			(int) (topRight.getY()+0.5f),
			(int) (bottomRightX+0.5f),
			(int) (bottomRightY+0.5f),
			(int) (bottomLeft.getX()+0.5f),
			(int) (bottomLeft.getY()+0.5f)
		);
  }
  /**
   * Loop though different setting of estAlignmentX, estAlignmentY and allowanceFactor to find the bottom-right alignment pattern   
   * @param overallEstModuleSize
   * @param estAlignmentX
   * @param estAlignmentY
   * @param allowanceFactor
   * @return
   * @throws NotFoundException
   */
  protected AlignmentPattern findBRAlignment(float overallEstModuleSize,
          int[] estAlignmentX, int[] estAlignmentY,
          float[] allowanceFactor) throws NotFoundException {
	  if(estAlignmentX.length <1 || estAlignmentY.length <1 || allowanceFactor.length <1) return null;
	  int count = (estAlignmentX.length < estAlignmentY.length)? estAlignmentX.length : estAlignmentY.length;
	  int allowanceCount = allowanceFactor.length;
	  for(int i=0;i<count;i++){
		  for(int j=0;j<allowanceCount;j++){
			  try{
				  AlignmentPattern alignmentPattern = findEnlargedAlignment(this.image,overallEstModuleSize,
						  estAlignmentX[i],estAlignmentY[i],allowanceFactor[j],null,true);
				  if(alignmentPattern !=null) return alignmentPattern;
			  }catch(NotFoundException re){ }
		  }
	  }
	  return null;
  }
  protected AlignmentPattern findInsideAlignmentInRegion(float overallEstModuleSize,
			int estAlignmentX, int estAlignmentY,
			float allowanceFactor) throws NotFoundException {
	return findEnlargedAlignment(this.image,overallEstModuleSize,estAlignmentX,estAlignmentY,allowanceFactor,null,false);
  }
  
  /**
   * This function finds an enlarged alignment pattern in the given region
   * @param image
   * @param overallEstModuleSize
   * @param estAlignmentX
   * @param estAlignmentY
   * @param allowanceFactor
   * @param resultPointCallback
   * @param isBottomRightPattern Indicate whether looking for a bottom-right pattern or patterns along the diagonal / anti-diagonal
   * @return
   * @throws NotFoundException
   */
  protected static AlignmentPattern findEnlargedAlignment(BitMatrix image, float overallEstModuleSize,
          int estAlignmentX, int estAlignmentY, float allowanceFactor,
          ResultPointCallback resultPointCallback, boolean isBottomRightPattern) throws NotFoundException {
	  	int allowance = (int) (allowanceFactor * overallEstModuleSize);
		int imgMaxRight=image.getWidth() - 1, imgMaxBottom=image.getHeight() - 1;
		int alignmentAreaLeftX = (estAlignmentX - allowance >0)? 
		estAlignmentX - allowance:0;
		int alignmentAreaRightX = (estAlignmentX + allowance <imgMaxRight)? 
		estAlignmentX + allowance : imgMaxRight;
		int searchWidth = alignmentAreaRightX - alignmentAreaLeftX;
		int alignmentAreaTopY = (estAlignmentY - allowance >0)? 
		estAlignmentY - allowance:0;
		int alignmentAreaBottomY = (estAlignmentY + allowance <imgMaxBottom)? 
		estAlignmentY + allowance : imgMaxBottom;
		int searchHeight = alignmentAreaBottomY - alignmentAreaTopY; 
		
		return (isBottomRightPattern)? 
				new AlignmentPatternFinderNewNew(
						image,
						alignmentAreaLeftX,
						alignmentAreaTopY,
						searchWidth,
						searchHeight,
						overallEstModuleSize,
						resultPointCallback).find()
				: new InsideAlignmentPatternFinder(
						image,
						alignmentAreaLeftX,
						alignmentAreaTopY,
						searchWidth,
						searchHeight,
						overallEstModuleSize,
						resultPointCallback).find();	
  }

	public DetectorResult detect(DetectorResult previousResult, int newDimension){
		  ResultPoint[] points=previousResult.getPoints();
		  ResultPoint topLeft,bottomLeft,topRight,alignmentPattern;
		  if(points.length <4) return previousResult;
		  alignmentPattern=points[3];
		  topLeft=points[1];
		  topRight=points[2];
		  bottomLeft=points[0];
	
		  int[] alignments=null;
		  try{
			  alignments=Version.getProvisionalVersionForDimension(newDimension).getAlignmentPatternCenters();
		  } catch(ReaderException e){ }
		  if(alignments==null) return previousResult;
		  int alignementIndex=alignments.length;
		  
		  try{
			  Version provisionalVersion = Version.getProvisionalVersionForDimension(newDimension);
			  AlignmentPattern[] alignPtns = findBiggerAlignPatterns((FinderPattern)topLeft, (FinderPattern)topRight, (FinderPattern)bottomLeft,
						alignementIndex, previousResult.getModuleSize(),
						provisionalVersion.getVersionNumber(), newDimension);
			  //for version < 7, no bigger inside alignment pattern, use back the old transform method
			  //Otherwise, use the new transformation method
			  PerspectiveTransformGeneral  transform = (alignPtns != null)?
					  createTransformGeneralNew(topLeft, topRight,bottomLeft, alignPtns, alignmentPattern, 
							  alignementIndex,newDimension,provisionalVersion.getVersionNumber())
			  		  : createTransformGeneral(topLeft, topRight, bottomLeft, alignmentPattern, newDimension);
			  BitMatrix bits = (transform !=null)? sampleGrid(image, transform, newDimension):null;
			  if(bits !=null && (alignPtns !=null || previousResult.isEnlargedQRcode())) 
				  return new DetectorResult(bits,points,previousResult.getModuleSize(),true);
			  return (bits !=null)? new DetectorResult(bits,points,previousResult.getModuleSize(),false)
			  	  :previousResult;
		  }catch(ReaderException e){ }
		  return previousResult;
	}
  
}