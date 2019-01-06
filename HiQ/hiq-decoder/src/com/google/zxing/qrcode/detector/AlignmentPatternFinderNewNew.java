/*
 Copyright (C) 2015 Elky Sing 
 */

package com.google.zxing.qrcode.detector;

import java.util.ArrayList;
import java.util.List;

import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.BitMatrix;

/**
 * An alignment finder class
 * This is based on the Alignment finder class from ZXing with additional checking in find() function. 
 * Changed by elky for bigger alignment pattern: here we enlarge the alignment pattern's size to two times as the original one
 * @author elky sing
 *
 */
final class AlignmentPatternFinderNewNew{

	private final static int returnThreshold=8;
	protected final BitMatrix image;
	protected final List<potentialAlignP> possibleCenters;
	protected final int startX;
	protected final int startY;
	protected final int width;
	protected final int height;
	protected final float moduleSize;
	protected final int[] crossCheckStateCount;
	protected final ResultPointCallback resultPointCallback;
	  
	  private class potentialAlignP{
		  public final AlignmentPattern pattern;
		  public final int count;
		  potentialAlignP(AlignmentPattern pattern, int count){
			  this.pattern=pattern;
			  this.count=count;
		  }
	  }

	  /**
	   * <p>Creates a finder that will look in a portion of the whole image.</p>
	   *
	   * @param image image to search
	   * @param startX left column from which to start searching
	   * @param startY top row from which to start searching
	   * @param width width of region to search
	   * @param height height of region to search
	   * @param moduleSize estimated module size so far
	   */
	  AlignmentPatternFinderNewNew(BitMatrix image,
	                         int startX,
	                         int startY,
	                         int width,
	                         int height,
	                         float moduleSize,
	                         ResultPointCallback resultPointCallback) {
	    this.image = image;
	    this.possibleCenters = new ArrayList<>(5);
	    this.startX = startX;
	    this.startY = startY;
	    this.width = width;
	    this.height = height;
	    this.moduleSize = moduleSize;
	    this.crossCheckStateCount = new int[3];
	    this.resultPointCallback = resultPointCallback;
	  }

	  /**
	   * <p>This method attempts to find the bottom-right alignment pattern in the image. It is a bit messy since
	   * it's pretty performance-critical and so is written to be fast foremost.</p>
	   *
	   * @return {@link AlignmentPattern} if found
	   * @throws NotFoundException if not found
	   */
	  AlignmentPattern find() throws NotFoundException {
	    int startX = this.startX;
	    int height = this.height;
	    int maxJ = startX + width;
	    int middleI = this.startY + (height >> 1);
	    // We are looking for black/white/black modules in 1:1:1 ratio;
	    // this tracks the number of black/white/black modules seen so far
	    // the ratio remains unchanged, but each of which is 3 times of the estimated module size
	    // so we don't need to change the code below for finding the 1:1:1 pattern
	    int[] stateCount = new int[3];
	    for (int iGen = 0; iGen < height; iGen++) {
	      // Search from middle outwards
	      int i = middleI + ((iGen & 0x01) == 0 ? (iGen + 1) >> 1 : -((iGen + 1) >> 1));
	      stateCount[0] = 0;
	      stateCount[1] = 0;
	      stateCount[2] = 0;
	      int j = startX;
	      // Burn off leading white pixels before anything else; if we start in the middle of
	      // a white run, it doesn't make sense to count its length, since we don't know if the
	      // white run continued to the left of the start point
	      while (j < maxJ && !image.get(j, i)) {
	        j++;
	      }
	      int currentState = 0;
	      while (j < maxJ) {
	        if (image.get(j, i)) {
	          // Black pixel
	          if (currentState == 1) { // Counting black pixels
	            stateCount[currentState]++;
	          } else { // Counting white pixels
	            if (currentState == 2) { // A winner?
	              if (foundPatternCross(stateCount)) { // Yes
	                AlignmentPattern confirmed = handlePossibleCenter(stateCount, i, j);
	                if (confirmed != null) {
	                  return confirmed;
	                }
	              }
	              stateCount[0] = stateCount[2];
	              stateCount[1] = 1;
	              stateCount[2] = 0;
	              currentState = 1;
	            } else {
	              stateCount[++currentState]++;
	            }
	          }
	        } else { // White pixel
	          if (currentState == 1) { // Counting black pixels
	            currentState++;
	          }
	          stateCount[currentState]++;
	        }
	        j++;
	      }
	      if (foundPatternCross(stateCount)) {
	        AlignmentPattern confirmed = handlePossibleCenter(stateCount, i, maxJ);
	        if (confirmed != null) {
	          return confirmed;
	        }
	      }

	    }

	    if(!possibleCenters.isEmpty()){
	    	int mostLikelyCenter=0;
	    	//Search through the list to find the most likely one
	    	for(int i=0,maxCount=0,length=possibleCenters.size();i<length;i++){
	    		int count=possibleCenters.get(i).count;
	    		if(count >= returnThreshold)
	    			return possibleCenters.get(i).pattern;
	    		if(count >maxCount) {
	    			maxCount=count;
	    			mostLikelyCenter=i;
	    		}
	    	}
	    	return possibleCenters.get(mostLikelyCenter).pattern;
	    }
	    throw NotFoundException.getNotFoundInstance();
	  }

	  /**
	   * Given a count of black/white/black pixels just seen and an end position,
	   * figures the location of the center of this black/white/black run.
	   */
	  private static float centerFromEnd(int[] stateCount, int end) {
	    return (float) (end - stateCount[2]) - stateCount[1] / 2.0f;
	  }

	  /**
	   * @param stateCount count of black/white/black pixels just read
	   * @return true iff the proportions of the counts is close enough to the 1/1/1 ratios
	   *         used by alignment patterns to be considered a match
	   *         but each component shoule be changed to 3 times of the module size
	   */
	  private boolean foundPatternCross(int[] stateCount) {
	    float moduleSize = this.moduleSize;
	    moduleSize = 3 * moduleSize;//add by elky
	    float maxVariance = moduleSize / 2.0f;
	    for (int i = 0; i < 3; i++) {
	      if (Math.abs(moduleSize - stateCount[i]) >= maxVariance) {
	        return false;
	      }
	    }
	    return true;
	  }

	  /**
	   * <p>After a horizontal scan finds a potential alignment pattern, this method
	   * "cross-checks" by scanning down vertically through the center of the possible
	   * alignment pattern to see if the same proportion is detected.</p>
	   *
	   * @param startI row where an alignment pattern was detected
	   * @param centerJ center of the section that appears to cross an alignment pattern
	   * @param maxCount maximum reasonable number of modules that should be
	   * observed in any reading state, based on the results of the horizontal scan
	   * @return vertical center of alignment pattern, or {@link Float#NaN} if not found
	   */
	  private float crossCheckVertical(int startI, int centerJ, int maxCount,
	      int originalStateCountTotal) {
	    BitMatrix image = this.image;

	    int maxI = image.getHeight();
	    int[] stateCount = crossCheckStateCount;
	    stateCount[0] = 0;
	    stateCount[1] = 0;
	    stateCount[2] = 0;

	    // Start counting up from center
	    int i = startI;
	    while (i >= 0 && image.get(centerJ, i) && stateCount[1] <= maxCount) {
	      stateCount[1]++;
	      i--;
	    }
	    // If already too many modules in this state or ran off the edge:
	    if (i < 0 || stateCount[1] > maxCount) {
	      return Float.NaN;
	    }
	    while (i >= 0 && !image.get(centerJ, i) && stateCount[0] <= maxCount) {
	      stateCount[0]++;
	      i--;
	    }
	    if (stateCount[0] > maxCount) {
	      return Float.NaN;
	    }

	    // Now also count down from center
	    i = startI + 1;
	    while (i < maxI && image.get(centerJ, i) && stateCount[1] <= maxCount) {
	      stateCount[1]++;
	      i++;
	    }
	    if (i == maxI || stateCount[1] > maxCount) {
	      return Float.NaN;
	    }
	    while (i < maxI && !image.get(centerJ, i) && stateCount[2] <= maxCount) {
	      stateCount[2]++;
	      i++;
	    }
	    if (stateCount[2] > maxCount) {
	      return Float.NaN;
	    }

	    int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2];
	    if (5 * Math.abs(stateCountTotal - originalStateCountTotal) >= 2 * originalStateCountTotal) {
	      return Float.NaN;
	    }

	    return foundPatternCross(stateCount) ? centerFromEnd(stateCount, i) : Float.NaN;
	  }
	  /**
	   * Same as crossCheckVertical but work on horizontal direction
	   * @param centerI
	   * @param centerJ
	   * @param maxCount
	   * @param originalStateCountTotal
	   * @return
	   */
	  private float crosscrossCheckHorizontal(int centerI, int centerJ, int maxCount,
		      int originalStateCountTotal) {
	    BitMatrix image = this.image;

	    int maxJ = image.getWidth();
	    int[] stateCount = crossCheckStateCount;
	    stateCount[0] = 0;
	    stateCount[1] = 0;
	    stateCount[2] = 0;
	    
	    // Start counting left from center
	    int j = centerJ;
	    while(j >= 0 && image.get(j, centerI) && stateCount[1] <= maxCount) {
	      stateCount[1]++;
	      j--;
	    }
	    if(j < 0 || stateCount[1] > maxCount) return Float.NaN;
	    while(j >= 0 && !image.get(j, centerI) && stateCount[0] <= maxCount) {
	      stateCount[0]++;
	      j--;
	    }
	    if(stateCount[0] > maxCount) return Float.NaN;
	    //Then counting right
	    j = centerJ + 1;
	    while(j < maxJ && image.get(j, centerI) && stateCount[1] <= maxCount) {
	      stateCount[1]++;
	      j++;
	    }
	    if(j >= maxJ || stateCount[1] > maxCount) return Float.NaN;
	    while(j < maxJ && !image.get(j, centerI) && stateCount[2] <= maxCount) {
	      stateCount[2]++;
	      j++;
	    }
	    if(stateCount[2] > maxCount) return Float.NaN;
	    int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2];
	    if (5 * Math.abs(stateCountTotal - originalStateCountTotal) >= 2 * originalStateCountTotal) {
	      return Float.NaN;
	    }
	    return foundPatternCross(stateCount) ? centerFromEnd(stateCount, j) : Float.NaN;
	  }

	  /**
	   * <p>This is called when a horizontal scan finds a possible alignment pattern. It will
	   * cross check with a vertical scan, and if successful, will see if this pattern had been
	   * found on a previous horizontal scan. If so, we consider it confirmed and conclude we have
	   * found the alignment pattern.</p>
	   *
	   * @param stateCount reading state module counts from horizontal scan
	   * @param i row where alignment pattern may be found
	   * @param j end of possible alignment pattern in row
	   * @return {@link AlignmentPattern} if we have found the same pattern twice, or null if not
	   */
	  private AlignmentPattern handlePossibleCenter(int[] stateCount, int i, int j) {
	    int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2];
	    float centerJ = centerFromEnd(stateCount, j);
	    float centerI = crossCheckVertical(i, (int) centerJ, 2 * stateCount[1], stateCountTotal);
	    centerJ = crosscrossCheckHorizontal((int) centerI, (int) centerJ, 2 * stateCount[1], stateCountTotal);
	    if(!Float.isNaN(centerI) && !Float.isNaN(centerJ)) {
	      //float estimatedModuleSize = (float) (stateCount[0] + stateCount[1] + stateCount[2]) / 3.0f;
	      //should be "/9.0f" since the size has been changed to 3 times as the original
	      float estimatedModuleSize = (float) (stateCount[0] + stateCount[1] + stateCount[2]) / 9.0f;
	      for(int a=0,length=possibleCenters.size();a<length;a++){
	    	  AlignmentPattern center=possibleCenters.get(a).pattern;
	    	  // Look for about the same center and module size:
	    	  if(center.aboutEquals(estimatedModuleSize, centerI, centerJ)){
	    		int oldCount=possibleCenters.get(a).count+1;
	    		if(oldCount >returnThreshold) 
	    			return center.combineEstimate(centerI, centerJ, estimatedModuleSize);
	        	possibleCenters.set(a, new potentialAlignP(
	        			center.combineEstimate(centerI, centerJ, estimatedModuleSize)
	        			,oldCount));
	    	  }
	      }
	      // Hadn't found this before; save it
	      AlignmentPattern point = new AlignmentPattern(centerJ, centerI, estimatedModuleSize);
	      possibleCenters.add(new potentialAlignP(point,1));
	    }
	    return null;
	  }

}