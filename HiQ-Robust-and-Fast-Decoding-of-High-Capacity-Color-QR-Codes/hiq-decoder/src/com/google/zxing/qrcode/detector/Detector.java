/*
 * Copyright 2007 ZXing authors
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
 * Copyright 2012 Solon Li
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

import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.ReaderException;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.GridSampler;
import com.google.zxing.common.PerspectiveTransformGeneral;
import com.google.zxing.common.detector.MathUtils;
import com.google.zxing.qrcode.decoder.Version;

import java.util.Map;

/**
 * <p>Encapsulates logic that can detect a QR Code in an image, even if the QR Code
 * is rotated or skewed, or partially obscured.</p>
 *
 * @author Sean Owen
 * 
 * Modified by Solon to use new algorithm in building bitMatrix
 */
public class Detector {

	protected static final float alignRadius=8; //arbitrary constant on searching alignment patterns
	protected final BitMatrix image;
	protected ResultPointCallback resultPointCallback;
	
	public Detector(BitMatrix image) {
		this.image = image;
	}

	protected final BitMatrix getImage() {
		return image;
	}

	protected final ResultPointCallback getResultPointCallback() {
		return resultPointCallback;
	}

  /**
   * <p>Detects a QR Code in an image, simply.</p>
   *
   * @return {@link DetectorResult} encapsulating results of detecting a QR Code
   * @throws NotFoundException if no QR Code can be found
   */
  public DetectorResult detect() throws NotFoundException, FormatException {
    return detect(null);
  }

  /**
   * <p>Detects a QR Code in an image, simply.</p>
   *
   * @param hints optional hints to detector
   * @return {@link NotFoundException} encapsulating results of detecting a QR Code
   * @throws NotFoundException if QR Code cannot be found
   * @throws FormatException if a QR Code cannot be decoded
   */
  public DetectorResult detect(Map<DecodeHintType,?> hints) throws NotFoundException, FormatException {
	resultPointCallback = (hints ==null || !hints.containsKey(DecodeHintType.NEED_RESULT_POINT_CALLBACK))? 
			null:(ResultPointCallback) hints.get(DecodeHintType.NEED_RESULT_POINT_CALLBACK);
    FinderPatternInfo info=getFinderPatternInfo(image,hints,resultPointCallback);
    return processFinderPatternInfoNew(info);//this function finds the bottom-right alignment pattern, even if the above function cannot find 
                                             //enough finder patterns

    //return processFinderPatternInfo(info);
  }
  
  protected static final FinderPatternInfo getFinderPatternInfo(BitMatrix image, Map<DecodeHintType,?> hints,
		  ResultPointCallback resultPointCallback) throws NotFoundException, FormatException{
	  	NotFoundException error=NotFoundException.getNotFoundInstance();
	    error.setErrorMessage("");
	    FinderPatternFinder finder = new FinderPatternFinderNew(image, resultPointCallback);
	    //FinderPatternFinder finder = new FinderPatternFinder(image, resultPointCallback);
	    FinderPatternInfo info=null;
	    try{
	    	info = finder.find(hints);
	    }catch(NotFoundException e){
	    	if(e.getErrorMessage()==null ||e.getErrorMessage()=="") {
	    		error.setErrorMessage("Cannot find finder infomation. Detector");
	    		throw error;
	    	}else throw e;//cannot find enough finder patterns
	    }
	    if(info == null){
	    	//If new method fail, use old one
	    	try{
	    		finder = new FinderPatternFinder(image, null);
	        	info = finder.find(hints);
	        }catch(NotFoundException e){
	        	throw error;
	        }
	    }
	    error.setErrorMessage("");
	    return info;
  }

  protected DetectorResult processFinderPatternInfoNew(FinderPatternInfo info)
      throws NotFoundException, FormatException {

	FinderPattern topLeft = info.getTopLeft();
	FinderPattern topRight = info.getTopRight();
	FinderPattern bottomLeft = info.getBottomLeft();
    NotFoundException error=NotFoundException.getNotFoundInstance();
    AlignmentPattern alignmentPattern = null;
    BitMatrix bits=null;
	int dimension=0, modulesBetweenFPCenters=0;
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
					
					try{
						antiDiagonal[i] = findAlignmentInRegion(moduleSize,antiAlignmentX,antiAlignmentY,alignRadius);
					}catch(NotFoundException re){}
					try{
						topLine[i] = findAlignmentInRegion(moduleSize,topAlignmentX,topAlignmentY,alignRadius);
					}catch(NotFoundException re){}
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
				  
				  //Now detect the alignment pattern again using the correct dimension and module size 
				  try {
					  alignmentPattern = findAlignmentInRegion(newModuleSize,estAlignmentX,estAlignmentY,alignRadius/2.0f);
				  }catch(NotFoundException re){ 
					  try{
						  alignmentPattern = findAlignmentInRegion(newModuleSize,estAlignmentX,estAlignmentY,alignRadius);
					  }catch(NotFoundException re2){}
				  }
				  if(alignmentPattern !=null){
					  //Use the new dimension and module size value only if we can detect an alignment pattern correctly
					  dimension=newDimension;
					  moduleSize=newModuleSize;
				  }
				}catch(Exception e){ }
				transform = createTransformGeneral(topLeft, topRight, bottomLeft, alignmentPattern, dimension);
//		        if(transform !=null && alignementIndex >=5)
//					alignmentErrorCorrection(provisionalVersion.getAlignmentPatternCenters(), transform, image);
				
			} //End of getAlignmentPatternCenters().length > 5 if statement
			if(alignmentPattern==null){
				try{
					alignmentPattern = findAlignmentInRegion(moduleSize,estAlignmentX,estAlignmentY,alignRadius/2.0f);
				}catch(NotFoundException re){
					//try next round
					try{
						alignmentPattern = findAlignmentInRegion(moduleSize,estAlignmentX,estAlignmentY,alignRadius);
					}catch(NotFoundException re2){
						try{
							alignmentPattern = findAlignmentInRegion(moduleSize,estAlignmentX,estAlignmentY,alignRadius*2.0f);
						}catch(NotFoundException re3){ }
					}
				}
			}
        } //End of alignementIndex > 0 if statement

        if(transform ==null) 
        	transform = createTransformGeneral(topLeft, topRight, bottomLeft, alignmentPattern, dimension);
        bits = (transform !=null)? sampleGrid(image, transform, dimension):null;
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
	if(resultPointCallback !=null){
		resultPointCallback.findCodeBoundLine(topLeft, topRight);
	    resultPointCallback.findCodeBoundLine(topLeft, bottomLeft);
	    if(alignmentPattern !=null) {
	    	resultPointCallback.foundPossibleResultPoint(alignmentPattern);
	    	resultPointCallback.findCodeBoundLine(topLeft, alignmentPattern);
	    }
	}
    ResultPoint[] points = (alignmentPattern == null)? new ResultPoint[]{bottomLeft, topLeft, topRight}
    						:new ResultPoint[]{bottomLeft, topLeft, topRight, alignmentPattern};
    
    return new DetectorResult(bits, points);
  }
  
  protected final DetectorResult processFinderPatternInfo(FinderPatternInfo info)
	      throws NotFoundException, FormatException {
    FinderPattern topLeft = info.getTopLeft();
    FinderPattern topRight = info.getTopRight();
    FinderPattern bottomLeft = info.getBottomLeft();

    float moduleSize = calculateModuleSize(topLeft, topRight, bottomLeft);
    if (moduleSize < 1.0f) {
      throw NotFoundException.getNotFoundInstance();
    }
    int dimension = computeDimension(topLeft, topRight, bottomLeft, moduleSize);
    Version provisionalVersion = Version.getProvisionalVersionForDimension(dimension);
    int modulesBetweenFPCenters = provisionalVersion.getDimensionForVersion() - 7;

    AlignmentPattern alignmentPattern = null;
    // Anything above version 1 has an alignment pattern
    if (provisionalVersion.getAlignmentPatternCenters().length > 0) {

      // Guess where a "bottom right" finder pattern would have been
      float bottomRightX = topRight.getX() - topLeft.getX() + bottomLeft.getX();
      float bottomRightY = topRight.getY() - topLeft.getY() + bottomLeft.getY();

      // Estimate that alignment pattern is closer by 3 modules
      // from "bottom right" to known top left location
      float correctionToTopLeft = 1.0f - 3.0f / (float) modulesBetweenFPCenters;
      int estAlignmentX = (int) (topLeft.getX() + correctionToTopLeft * (bottomRightX - topLeft.getX()));
      int estAlignmentY = (int) (topLeft.getY() + correctionToTopLeft * (bottomRightY - topLeft.getY()));

      // Kind of arbitrary -- expand search radius before giving up
      for (int i = 4; i <= 16; i <<= 1) {
        try {
          alignmentPattern = findAlignmentInRegion(moduleSize,estAlignmentX,estAlignmentY,(float) i);
          break;
        } catch (NotFoundException re) {
          // try next round
        }
      }
      // If we didn't find alignment pattern... well try anyway without it
    }

    //PerspectiveTransform transform =
    PerspectiveTransformGeneral transform =
    		createTransformGeneral(topLeft, topRight, bottomLeft, alignmentPattern, dimension);

    BitMatrix bits = sampleGrid(image, transform, dimension);

    ResultPoint[] points;
    if (alignmentPattern == null) {
      points = new ResultPoint[]{bottomLeft, topLeft, topRight};
    } else {
      points = new ResultPoint[]{bottomLeft, topLeft, topRight, alignmentPattern};
    }
    return new DetectorResult(bits, points);
    //return null;
  }
  
  public static PerspectiveTransformGeneral createTransformGeneral(ResultPoint topLeft,
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
      sourceBottomRightX = sourceBottomRightY = dimMinusThree - 3.0f;
    } else {
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
  
  protected static BitMatrix sampleGrid(BitMatrix image,
                                      PerspectiveTransformGeneral transform,
                                      int dimension) throws NotFoundException {

    GridSampler sampler = GridSampler.getInstance();
    try{
    	//return sampler.sampleGridAffine(image, dimension, dimension, transform);
    	return sampler.sampleGrid(image, dimension, dimension, transform);
    } catch(NotFoundException e){
    	NotFoundException error=NotFoundException.getNotFoundInstance();
    	error.setErrorMessage((e.getErrorMessage()!="")? e.getErrorMessage():"Something wrong in sample Grid. Detector");
        throw error;
    }
  }

  /**
   * <p>Computes the dimension (number of modules on a size) of the QR Code based on the position
   * of the finder patterns and estimated module size.</p>
   */
  protected static int computeDimension(ResultPoint topLeft,
                                        ResultPoint topRight,
                                        ResultPoint bottomLeft,
                                        float moduleSize) throws NotFoundException {
    int tltrCentersDimension = MathUtils.round(ResultPoint.distance(topLeft, topRight) / moduleSize);
    int tlblCentersDimension = MathUtils.round(ResultPoint.distance(topLeft, bottomLeft) / moduleSize);
    int dimension = ((tltrCentersDimension + tlblCentersDimension) >> 1) + 7;
    switch (dimension & 0x03) { // mod 4
      case 0:
        dimension++;
        break;
        // 1? it is correct.
      case 2:
        dimension--;
        break;
      case 3:
    	//TODO: A hard fix: assume lower, then hope we can get the correction dimension in decoding phase
    	dimension -=2;
/*    	NotFoundException error=NotFoundException.getNotFoundInstance();
      	error.setErrorMessage("Something wrong on dimension. Detector. Details: tltrCentersDimension:"+tltrCentersDimension+" and tlblCentersDimension:"+tlblCentersDimension+" and dimension:"+dimension+" and moduleSize:"+moduleSize);
        throw error;
*/        
    }
    return (dimension<21)? 21:(dimension>177)? 177:dimension;
  }

  /**
   * <p>Computes an average estimated module size based on estimated derived from the positions
   * of the three finder patterns.</p>
   */
  protected final float calculateModuleSize(ResultPoint topLeft,
                                      ResultPoint topRight,
                                      ResultPoint bottomLeft) {
    // Take the average
    return (calculateModuleSizeOneWay(topLeft, topRight) +
        calculateModuleSizeOneWay(topLeft, bottomLeft)) / 2.0f;
  }

  /**
   * <p>Estimates module size based on two finder patterns -- it uses
   * {@link #sizeOfBlackWhiteBlackRunBothWays(int, int, int, int)} to figure the
   * width of each, measuring along the axis between their centers.</p>
   */
  private float calculateModuleSizeOneWay(ResultPoint pattern, ResultPoint otherPattern) {
    float moduleSizeEst1 = sizeOfBlackWhiteBlackRunBothWays((int) pattern.getX(),
        (int) pattern.getY(),
        (int) otherPattern.getX(),
        (int) otherPattern.getY());
    float moduleSizeEst2 = sizeOfBlackWhiteBlackRunBothWays((int) otherPattern.getX(),
        (int) otherPattern.getY(),
        (int) pattern.getX(),
        (int) pattern.getY());
    if (Float.isNaN(moduleSizeEst1)) {
      return moduleSizeEst2 / 7.0f;
    }
    if (Float.isNaN(moduleSizeEst2)) {
      return moduleSizeEst1 / 7.0f;
    }
    // Average them, and divide by 7 since we've counted the width of 3 black modules,
    // and 1 white and 1 black module on either side. Ergo, divide sum by 14.
    return (moduleSizeEst1 + moduleSizeEst2) / 14.0f;
  }

  /**
   * See {@link #sizeOfBlackWhiteBlackRun(int, int, int, int)}; computes the total width of
   * a finder pattern by looking for a black-white-black run from the center in the direction
   * of another point (another finder pattern center), and in the opposite direction too.</p>
   * 
   * Modified by Solon, use another method in counting black-white-black run instead of sizeOfBlackWhiteBlackRun()
   */
  private float sizeOfBlackWhiteBlackRunBothWays(int fromX, int fromY, int toX, int toY) {

    float result = sizeOfBlackWhiteBlackRun(fromX, fromY, toX, toY);

    // Now count other way -- don't run off image though of course
    float scale = 1.0f;
    int otherToX = fromX - (toX - fromX);
    if (otherToX < 0) {
      scale = (float) fromX / (float) (fromX - otherToX);
      otherToX = 0;
    } else if (otherToX >= image.getWidth()) {
      scale = (float) (image.getWidth() - 1 - fromX) / (float) (otherToX - fromX);
      otherToX = image.getWidth() - 1;
    }
    int otherToY = (int) (fromY - (toY - fromY) * scale);

    scale = 1.0f;
    if (otherToY < 0) {
      scale = (float) fromY / (float) (fromY - otherToY);
      otherToY = 0;
    } else if (otherToY >= image.getHeight()) {
      scale = (float) (image.getHeight() - 1 - fromY) / (float) (otherToY - fromY);
      otherToY = image.getHeight() - 1;
    }
    otherToX = (int) (fromX + (otherToX - fromX) * scale);

    result += sizeOfBlackWhiteBlackRun(fromX, fromY, otherToX, otherToY);

    // Middle pixel is double-counted this way; subtract 1
    return result - 1.0f;
  }

  /**
   * <p>This method traces a line from a point in the image, in the direction towards another point.
   * It begins in a black region, and keeps going until it finds white, then black, then white again.
   * It reports the distance from the start to this point.</p>
   *
   * <p>This is used when figuring out how wide a finder pattern is, when the finder pattern
   * may be skewed or rotated.</p>
   */
  private float sizeOfBlackWhiteBlackRun(int fromX, int fromY, int toX, int toY) {
    // Mild variant of Bresenham's algorithm;
    // see http://en.wikipedia.org/wiki/Bresenham's_line_algorithm
    boolean steep = Math.abs(toY - fromY) > Math.abs(toX - fromX);
    if (steep) {
      int temp = fromX;
      fromX = fromY;
      fromY = temp;
      temp = toX;
      toX = toY;
      toY = temp;
    }

    int dx = Math.abs(toX - fromX);
    int dy = Math.abs(toY - fromY);
    int error = -dx >> 1;
    int xstep = fromX < toX ? 1 : -1;
    int ystep = fromY < toY ? 1 : -1;

    // In black pixels, looking for white, first or second time.
    int state = 0;
    // Loop up until x == toX, but not beyond
    int xLimit = toX + xstep;
    for (int x = fromX, y = fromY; x != xLimit; x += xstep) {
      int realX = steep ? y : x;
      int realY = steep ? x : y;

      // Does current pixel mean we have moved white to black or vice versa?
      // Scanning black in state 0,2 and white in state 1, so if we find the wrong
      // color, advance to next state or end if we are in state 2 already
      if ((state == 1) == image.get(realX, realY)) {
        if (state == 2) {
        	return MathUtils.distance(x, y, fromX, fromY);
        }
        state++;
      }

      error += dy;
      if (error > 0) {
        if (y == toY) {
          break;
        }
        y += ystep;
        error -= dx;
      }
    }
    // Found black-white-black; give the benefit of the doubt that the next pixel outside the image
    // is "white" so this last point at (toX+xStep,toY) is the right ending. This is really a
    // small approximation; (toX+xStep,toY+yStep) might be really correct. Ignore this.
    if (state == 2) {
    	return MathUtils.distance(toX + xstep, toY, fromX, fromY);
    }
    // else we didn't find even black-white-black; no estimate is really possible
    return Float.NaN;
  }

  /**
   * <p>Attempts to locate an alignment pattern in a limited region of the image, which is
   * guessed to contain it. This method uses {@link AlignmentPattern}.</p>
   *
   * @param overallEstModuleSize estimated module size so far
   * @param estAlignmentX x coordinate of center of area probably containing alignment pattern
   * @param estAlignmentY y coordinate of above
   * @param allowanceFactor number of modules in all directions to search from the center
   * @return {@link AlignmentPattern} if found, or null otherwise
   * @throws NotFoundException if an unexpected error occurs during detection
   */
  protected AlignmentPattern findAlignmentInRegion(float overallEstModuleSize,
							               int estAlignmentX,
							               int estAlignmentY,
							               float allowanceFactor) throws NotFoundException {
	  return findAlignmentInRegion(this.image,overallEstModuleSize,estAlignmentX,estAlignmentY,allowanceFactor,null);
  }
  
  protected static AlignmentPattern findAlignmentInRegion(BitMatrix image,
		  										   float overallEstModuleSize,
                                                   int estAlignmentX,
                                                   int estAlignmentY,
                                                   float allowanceFactor,
                                                   ResultPointCallback resultPointCallback)
      throws NotFoundException {
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

    
    //AlignmentPatternFinder alignmentFinder = new AlignmentPatternFinder(
    AlignmentPatternFinderNew alignmentFinder = new AlignmentPatternFinderNew(
            image,
            alignmentAreaLeftX,
            alignmentAreaTopY,
            searchWidth,
            searchHeight,
            overallEstModuleSize,
            resultPointCallback);
            //null);
    return alignmentFinder.find();//if cannot find the bottom-right alignment pattern ,throw NotFoundException
  }
  protected static AlignmentPattern findAlignmentInRegionOld(BitMatrix image,
													   float overallEstModuleSize,
													   int estAlignmentX,
													   int estAlignmentY,
													   float allowanceFactor)
      throws NotFoundException {
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
		
	  AlignmentPatternFinder alignmentFinder =
		new AlignmentPatternFinder(
			image,
			alignmentAreaLeftX,
			alignmentAreaTopY,
			searchWidth,
			searchHeight,
			overallEstModuleSize,
			//resultPointCallback);
			null);
	  return alignmentFinder.find();
  }
  
  /**
   * This method counts number of black and white modules between two points.
   * It begins in a black region, and keeps going until it finds white, then black, then white again.
   * 
   * This is used to scan the timing pattern between corner of two finder patterns and get the dimension
   * The line between points are created using Bresenham's algorithm with little modification
   * Reference: http://en.wikipedia.org/wiki/Bresenham's_line_algorithm    
   */
  protected int[] countTimingPattern(ResultPoint startP, ResultPoint endP){
	  //We use the code in simplification section of Bresenham's line algorithm Wiki page
	  BitMatrix image=this.image;
	  int x0=(int) (startP.getX()+0.5f), y0=(int) (startP.getY()+0.5f);
	  int x1=(int) (endP.getX()+0.5f), y1=(int) (endP.getY()+0.5f);
	  int dx=(x1>x0)? x1-x0:x0-x1, dy=(y1>y0)? y1-y0:y0-y1;
	  int sx=(x1>x0)? 1:(x1<x0)? -1:0, sy=(y1>y0)? 1:(y1<y0)? -1:0;
	  int error=dx-dy;
	  int counter=0;
	  
	  boolean isCountBlack=(image.get(x0,y0));
	  int moduleCount=(isCountBlack)? 0:1;
	  while(x0 !=x1 || y0!=y1){
		  boolean pixel =image.get(x0, y0);
		  if(isCountBlack ^ pixel){
			  moduleCount++;
			  isCountBlack=pixel;
		  }
		  int e2=2*error; //Note that here we cannot use bit shift as error may be negative
		  if(e2 > -dy){
			  error -=dy;
			  x0 +=sx;
			  counter++;
		  }
		  if((x0 ==x1 && y0==y1)) break;
		  if(e2<dx){
			  error +=dx;
			  y0+=sy;
			  counter++;
		  }
	  }
	  if(isCountBlack ^ image.get(x0, y0)){
		  moduleCount++;
		  isCountBlack=image.get(x0, y0);
	  }
	    
	  return new int[]{moduleCount, counter/moduleCount};
  }
  
  /**
   * Create best fit line using least square method and use it to change the value of finder patterns with helps of vector dot product
   * Reference: http://hotmath.com/hotmath_help/topics/line-of-best-fit.html http://mathworld.wolfram.com/Point-LineDistance2-Dimensional.html
   * Images larger than 5000*5000 may create number overflow
   * @param mainDiagonal list of alignment pattern along the line
   * @return float[] first entry is slope and second entry is y-intercept
   * if it is a vertical line, the slope will be Float.POSITIVE_INFINITY and y-intercept will be the x-coordinate of first entry in mainDragonal 
   */
  protected static float[] bestFitLine(AlignmentPattern[] mainDiagonal) throws Exception{
	  if(mainDiagonal == null || mainDiagonal.length <3) return null;
	  int length=mainDiagonal.length;
	  float mainX=0, mainY=0, mainXX=0, mainXY=0, mainN=0, mainM=0;
	  for(int i=0;i<length;i++){
			AlignmentPattern temp=mainDiagonal[i];
			if(temp !=null){
				float x=temp.getX(), y=temp.getY();
				mainX +=x;
				mainY +=y;
				mainXX += x*x;
				mainXY += x*y;
				mainN++;
			}
	  }
	  if(mainN< ((3*length) >>2) ) return null; 
	  mainM = (mainN*mainXX) - (mainX*mainX);
	  mainM= (mainM !=0)? //Check if it is vertical line 
				( mainN*mainXY - (mainX*mainY) ) / mainM
				:Float.POSITIVE_INFINITY; //TODO: Value may be as high as 10M, number overflow is possible?
	  mainN=(mainM != Float.POSITIVE_INFINITY)? (mainY-mainM*mainX) / mainN 
				: mainDiagonal[0].getX(); //If it is a vertical line, use x-intercept
	  
/*	  //filter the most outlined point
	  mainX=0; mainY=0;
	  for(int i=0;i<length;i++){
			AlignmentPattern temp=mainDiagonal[i];
			if(temp !=null){
				float verDist=(mainM != Float.POSITIVE_INFINITY)? 
						mainM*temp.getX()+mainN-temp.getY():temp.getX()-mainN;
				if(verDist >mainY || verDist <-mainY){
					mainX=i;
					mainY=(verDist>0)? verDist:-verDist;
				}
			}
	  }
	  if(mainY >0 && mainX<length && mainX>=0) mainDiagonal[(int) mainX]=null;
	  //Do the fitting again
	  mainX=0; 
	  mainY=0; 
	  mainXX=0; 
	  mainXY=0; 
	  mainN=0; 
	  mainM=0;
	  for(int i=0;i<length;i++){
		AlignmentPattern temp=mainDiagonal[i];
		if(temp !=null){
			float x=temp.getX(), y=temp.getY();
			mainX +=x;
			mainY +=y;
			mainXX += x*x;
			mainXY += x*y;
			mainN++;
		}
	  }
	  mainM = (mainN*mainXX) - (mainX*mainX);
	  mainM= (mainM !=0)? //Check if it is vertical line 
				( mainN*mainXY - (mainX*mainY) ) / mainM
				:Float.POSITIVE_INFINITY; //TODO: Value may be as high as 10M, number overflow is possible?
	  mainN=(mainM != Float.POSITIVE_INFINITY)? (mainY-mainM*mainX) / mainN 
				: mainDiagonal[0].getX(); //If it is a vertical line, use x-intercept
*/
	  return new float[]{mainM,mainN};
	  
  }
  
  /**
   * Find the intersection point of two lines, return null if not exist. The two lines should be output of bestFitLine()
   * @param line0
   * @param line1
   * @return
   */
  protected static ResultPoint intersectionPoint(float[] line0,float[] line1){	  
	  if( (line0[0] == Float.POSITIVE_INFINITY && line1[0] == Float.POSITIVE_INFINITY) 
			  || (line0[0]-line1[0])==0 ) return null;
	  //Given y=m1x+c1 y=m2x+c2 x should be (c2-c1)/(m1-m2)  
	  float x=(line0[0] == Float.POSITIVE_INFINITY)? line0[1]:
		  		(line1[0] == Float.POSITIVE_INFINITY)? line1[1]:
		  			(line1[1]-line0[1])/(line0[0]-line1[0]);
	  float y=(line0[0] == Float.POSITIVE_INFINITY)? line1[0]*x+line1[1]:line0[0]*x+line0[1];
	  return new ResultPoint(x,y);
  }
  
  protected static void alignmentErrorCorrection(int[] alignments, PerspectiveTransformGeneral transform, BitMatrix image){
	int alignementIndex=alignments.length;
	if(alignementIndex <5) return;
	//This difference in different versions varies between 20-28. we add 4 by try and error
	int errRadius=((alignments[1]-alignments[0]) >>1)+4; 
	for(int i=0;i<alignementIndex;i++){
		for(int j=0;j<alignementIndex;j++){
			if( (i==0 && (j==0 || j==alignementIndex-1)) || 
				(i==alignementIndex-1 && j==0) ) continue;
			float x=alignments[i]+0.5f, y=alignments[j]+0.5f;
			int[] errVector=checkAlignment(x,y,transform,image);
			if(errVector !=null && (errVector[0] !=0 || errVector[1] !=0) ) 
				transform.addErrorCorrection(x,y,errVector[0],errVector[1],errRadius);
		}
	}
	transform.fixErrorCorrection();
  }
  /**
   * Given a potential alignment position on transformed image,check if we can find the alignment in original image.
   * If not, find the error vector from nearest true alignment in the original image
   * If no alignment pattern is found nearby, return null
   * @param x
   * @param y
   * @param transform
   * @param image
   * @return
   */
  private static int[] checkAlignment(float x,float y, PerspectiveTransformGeneral transform, BitMatrix image){
	  int width=image.getWidth(), height=image.getHeight();
	  float[] poAlign=new float[]{x-2,y-2,x-2,y-1,x-2,y,x-2,y+1,x-2,y+2,
									x-1,y-2,x-1,y-1,x-1,y,x-1,y+1,x-1,y+2,
									x,y-2,x,y-1,x,y,x,y+1,x,y+2,
									x+1,y-2,x+1,y-1,x+1,y,x+1,y+1,x+1,y+2,
									x+2,y-2,x+2,y-1,x+2,y,x+2,y+1,x+2,y+2
								};
	  boolean[] stAlign=new boolean[]{true,true,true,true,true,
										true,false,false,false,true,
										true,false,true,false,true,
										true,false,false,false,true,
										true,true,true,true,true
									};
	  transform.transformPoints(poAlign);
	  int[] errVector=new int[]{0,0};
	  //search anti-clockwisely
	  int[] poErrVector=new int[]{0,1, 1,1, 1,0, 1,-1, 0,-1, -1,-1, -1,0, -1,1,
			  						//2 pixels
			  						0,2, 1,2, 2,2, 2,1, 2,0, 2,-1, 2,-2, 1,-2,
			  						0,-2, -1,-2, -2,-2, -2,-1, -2,0, -2,1, -2,2, -1,2,
			  						//3 pixels
			  						0,3, 1,3, 2,3, 3,3, 3,2, 3,1, 3,0, 3,-1, 3,-2, 3,-3, 2,-3, 1,-3,
			  						0,-3, -1,-3, -2,-3, -3,-3, -3,-2, -3,-1, -3,0, -3,1, -3,2, -3,3, -2,3, -1,3,
			  					/*	//4 pixels
			  						0,4, 1,4, 2,4, 3,4, 4,4, 4,3, 4,2, 4,1, 4,0, 4,-1, 4,-2, 4,-3, 4,-4, 3,-4, 2,-4, 1,-4, 
			  						0,-4, -1,-4, -2,-4, -3,-4, -4,-4, -4,-3, -4,-2, -4,-1, -4,0, -4,1, -4,2, -4,3, -4,4, -3,4, -2,4, -1,4,
			  						*/ 
	  							};
	  int errL=poErrVector.length, count=poAlign.length;
	  for(int k=0,errC=0;k<count && errC<errL;k+=2){
		  float baseI=poAlign[k]+0.5f, baseJ=poAlign[k+1]+0.5f;
		  int i=(int) (baseI+errVector[0]), j=(int) (baseJ+errVector[1]);
		  boolean reference =stAlign[k>>1];
		//Part of alignement pattern is out of image, how can we find error vector=.= 
		  //(It should not possible as at least 6 modules around any alignment are data modules and should not outside of image
		  if(i<0 || j<0 || i>=width || j>=height) return null; 
		  if(image.get(i,j) !=reference) {
			  if(errC >=errL) return null; //We have tried every possible error vector w/o success, just return null
			  while((image.get(i,j) !=reference) && errC<errL){
				  i=(int) (baseI+poErrVector[errC]); 
				  j=(int) (baseJ+poErrVector[errC+1]);
				  if(i<0 || j<0 || i>=width || j>=height) return null; //Part of alignment pattern is out of image
				  errC+=2;
			  }
			  if((image.get(i,j) !=reference) && errC>=errL) return null;
			  errVector[0]=poErrVector[errC-2];
			  errVector[1]=poErrVector[errC-1];
		  }		   
	  }
	  return errVector;
  }
  
  /**
   * Based on information about the QR code, build the result again.
   * The code in this function depends on those in detect() and processFinderPatternInfoNew/processFinderPatternInfo
   * @param previousResult
   * @param newDimension
   * @return
   */
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
		  PerspectiveTransformGeneral transform = 
				  createTransformGeneral(topLeft, topRight, bottomLeft, alignmentPattern, newDimension);
		  //After refining the dimension, we use the reference alignment patterns to provide local corrections on "transform"
		  //Adding reference alignment patterns
		  if(transform !=null && alignementIndex >=5)
			alignmentErrorCorrection(alignments, transform, image);
		  BitMatrix bits = (transform !=null)? sampleGrid(image, transform, newDimension):null;
		  if(bits !=null) return new DetectorResult(bits,points);
	  } catch(ReaderException e){ }
	  return previousResult;
  }

}
