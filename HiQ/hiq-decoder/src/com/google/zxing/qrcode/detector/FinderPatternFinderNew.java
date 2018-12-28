/*
 * Copyright (C) 2013 Solon Li
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
import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.BitMatrix;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class attempts to find finder patterns in a QR Code. 
 * The basic operation depends on FinderPatternFinder from Sean. However some modifications are made to reduce false-negative
 * This class is thread-safe but not reentrant. Each thread must allocate its own object.
 *
 * @author Solon Li
 * 
 */
public class FinderPatternFinderNew extends FinderPatternFinder {
  private static final int INTEGER_MATH_SHIFT = 8;
  
  private static final int MIN_MODULES = 2; // Assume each module takes at least 2 pixels
  private static final int MAX_module_num = 177; // support up to version 40 
  private static final int MIN_module_num = 21; // support down to version 1 
  private static final int MAX_gap_module = 1; //Max number of gaps (in pixels) acceptable inside a module
  private static final float MAX_degree_error = 20; //tan value of 89 - 93 in degree

  public FinderPatternFinderNew(BitMatrix image) {
    this(image, null);
  }

  public FinderPatternFinderNew(BitMatrix image, ResultPointCallback resultPointCallback) {
    super(image, resultPointCallback);
  }

 
  FinderPatternInfo find(Map<DecodeHintType,?> hints) throws NotFoundException {
	if(image ==null){
		NotFoundException error=NotFoundException.getNotFoundInstance();
    	error.setErrorMessage("No image taken to decode QR code");
        throw error;
	}
//TODO: check work in this function    
	boolean tryHarder = hints != null && hints.containsKey(DecodeHintType.TRY_HARDER);
    int maxI = image.getHeight();
    int maxJ = image.getWidth();
    int minModuleSize = (hints != null && hints.containsKey(DecodeHintType.MINIMUM_MODULE_SIZE))? 
    						(Integer) hints.get(DecodeHintType.MINIMUM_MODULE_SIZE):0;
   
    // Assume that the maximum version QR Code we support takes at least 50% of height of image. As center takes 3 modules, 
    //iSKip tells the smallest number of pixels the center could be, so skip this often. 
    //When trying harder, look for center regardless of how dense they are.
//TODO: Will division has problem? 
    int iSkip = (minModuleSize ==0)?  (maxI / MAX_module_num) : minModuleSize;
    if(iSkip <=0){
    	NotFoundException error=NotFoundException.getNotFoundInstance();
    	error.setErrorMessage("Error on image when locating finder patterns.");
        throw error;
    }
    
    if (iSkip < MIN_MODULES || tryHarder) {
      iSkip = MIN_MODULES;
    }
//TODO: Will division has problem?    
    int Max_plain_line_length = (maxI / MIN_module_num) << 2;
    
    // We are looking for black/white/black/white/black modules in 1:1:3:1:1 ratio;
    boolean done = false;    
    for (int i = iSkip - 1; i < maxI && !done; i += iSkip) {
		int j=0;
		while(j<maxJ){
			int[] stateCount = new int[5];
			int tmp=0;
			//First, move to nearest right-hand side black pixels
			while(j<maxJ && !image.get(j, i)) j++;
			//check if there is any black/white/black/white/black line	
			tmp=countPlain(i,j,maxJ,true,true);
			j += (tmp > 0)? tmp:( (maxJ-j)<iSkip )? (maxJ-j):iSkip;
			if(tmp > Max_plain_line_length || tmp <=0) continue;
			stateCount[0]=tmp;
			tmp=countPlain(i,j,maxJ,false,true);
			j += (tmp > 0)? tmp:( (maxJ-j)<iSkip )? (maxJ-j):iSkip;
			if(tmp > Max_plain_line_length || tmp <=0) continue;
			stateCount[1]=tmp;
			tmp=countPlain(i,j,maxJ,true,true);
			j += (tmp > 0)? tmp:( (maxJ-j)<iSkip )? (maxJ-j):iSkip;
			if(tmp > Max_plain_line_length || tmp <=0) continue;
			stateCount[2]=tmp;
			tmp=countPlain(i,j,maxJ,false,true);
			j += (tmp > 0)? tmp:( (maxJ-j)<iSkip )? (maxJ-j):iSkip;
			if(tmp > Max_plain_line_length || tmp <=0) continue;
			stateCount[3]=tmp;
			tmp=countPlain(i,j,maxJ,true,true);
			j += (tmp > 0)? tmp:( (maxJ-j)<iSkip )? (maxJ-j):iSkip;
			if(tmp > Max_plain_line_length || tmp <=0) continue;
			stateCount[4]=tmp;
			//Check if it is 1:1:3:1:1
			if (foundPatternCross(stateCount)) { 
				handlePossibleCenterNew(stateCount, i, j);
				continue;
			}
			//Check if later three is 1:1:3
			if(foundPartialPattern(stateCount)){
				//If yes, then extend the search and see if there is a 1:1:3:1:1 line
				stateCount[0] = stateCount[2];
		        stateCount[1] = stateCount[3];
		        stateCount[2] = stateCount[4];
		        stateCount[3]=countPlain(i,j,maxJ,false,true);
		  	    j += (stateCount[3] > 0)? stateCount[3]:1;
			    if(stateCount[3] > Max_plain_line_length || stateCount[3] <=0) continue;
			    stateCount[4]=countPlain(i,j,maxJ,true,true);
		  	    j += (stateCount[4] > 0)? stateCount[4]:1;
			    if(stateCount[4] > Max_plain_line_length || stateCount[4] <=0) continue;
			    if (foundPatternCross(stateCount)) {
			    	handlePossibleCenterNew(stateCount, i, j);
			    	continue;
			    }
			}
			//After each search, all possible finder pattern is found, so we restart the search again.
			//However, the last black section of the search should be passed to next loop
			j -= stateCount[4];
		}
    }
    
    FinderPattern[] patternInfo = selectBestPatternsNew(minModuleSize);
    if(resultPointCallback != null){
    	for(int i=0;i<patternInfo.length;i++){
    		FinderPattern point=patternInfo[i];
		    resultPointCallback.foundPossibleResultPoint(point);
		}
    }
    return new FinderPatternInfo(patternInfo);
  }

  /**
   * Find the length of plain line starting at (i,j) 
   * @param i
   * @param j
   * @param width max value of j or i, depending on searching horizontally or vertically respectively
   * @param skipCount approximation of length of center in pixels
   * @param isBlack counting black or white plain line
   * @param isHorizontal is searching horizontally
   * @return
   */
  private int countPlain(int i, int j, int width, boolean isBlack, boolean isHorizontal){
	  if(i >= image.getHeight() || j >= image.getWidth()) return 0;
	  width = (isHorizontal)? (width<image.getWidth())? width:image.getWidth() : (width<image.getHeight())? width:image.getHeight();
	  boolean isCounting = (image.get(j, i) ^ !isBlack);
	  if( (isHorizontal && width>image.getWidth()) || ((!isHorizontal && width>image.getHeight())) ) return 0;
	  int plainCount=0, gapCount=0, limit=(isHorizontal)? width-j : width-i;
	  while(isCounting && (plainCount+gapCount)<limit) {
		  if( (isHorizontal && (image.get(j+plainCount+gapCount, i) ^ !isBlack)) || (!isHorizontal && (image.get(j, i+plainCount+gapCount) ^ !isBlack)) ){
			  if(gapCount>0) {
				  plainCount += gapCount;
				  gapCount=0;
			  }
			  plainCount++;  
		  }
		  else{
			  gapCount++;
			  if(gapCount > MAX_gap_module) isCounting=false;
		  }
	  }
	  return plainCount;
  }
  
  /**
   * @param stateCount count of black/white/black/white/black pixels just read
   * @return true iff the proportions of the counts is close enough to the 1/1/3/1/1 ratios
   *         used by finder patterns to be considered a match
   */
  protected static boolean foundPatternCross(int[] stateCount) {
    int totalModuleSize = 0;
    for (int i = 0; i < 5; i++) {
      int count = stateCount[i];
      if (count == 0) {
        return false;
      }
      totalModuleSize += count;
    }
    if (totalModuleSize < 7) {
      return false;
    }
    int moduleSize = (totalModuleSize << INTEGER_MATH_SHIFT) / 7;
    int maxVariance = moduleSize / 2;
    // Allow less than 50% variance from 1-1-3-1-1 proportions
    return Math.abs(moduleSize - (stateCount[0] << INTEGER_MATH_SHIFT)) < maxVariance &&
        Math.abs(moduleSize - (stateCount[1] << INTEGER_MATH_SHIFT)) < maxVariance &&
        Math.abs(3 * moduleSize - (stateCount[2] << INTEGER_MATH_SHIFT)) < 3 * maxVariance &&
        Math.abs(moduleSize - (stateCount[3] << INTEGER_MATH_SHIFT)) < maxVariance &&
        Math.abs(moduleSize - (stateCount[4] << INTEGER_MATH_SHIFT)) < maxVariance;
  }
  /**
   * @param stateCount count of black/white/black/white/black pixels just read
   * @return true iff the proportions of the last three counts is close enough to the 1/1/3 ratios
   *         used by finder patterns to be considered a match
   */
  private static boolean foundPartialPattern(int[] stateCount) {
	int totalModuleSize = 0;
	for (int i = 2; i < 5; i++) {
	  int count = stateCount[i];
	  if (count == 0) {
	    return false;
	  }
	  totalModuleSize += count;
	}
	if (totalModuleSize < 5) {
	    return false;
	  }
	  int moduleSize = (totalModuleSize << INTEGER_MATH_SHIFT) / 5;
	  int maxVariance = moduleSize / 2;
	  // Allow less than 50% variance from 1-1-3 proportions
	  return 
	      Math.abs(moduleSize - (stateCount[2] << INTEGER_MATH_SHIFT)) < maxVariance &&
	      Math.abs(moduleSize - (stateCount[3] << INTEGER_MATH_SHIFT)) < maxVariance &&
	      Math.abs(3 * moduleSize - (stateCount[4] << INTEGER_MATH_SHIFT)) < 3 * maxVariance;
  }
  
  /**
   * This is called when a horizontal scan finds a possible finder pattern. It will
   * cross check with a vertical scan, and if successful, cross-cross-check with a sticker horizontal scan to confirm the center. 
   * This is needed primarily to locate the real horizontal center of the pattern in cases of extreme skew.
   *
   * If that succeeds the finder pattern location is added to the list possible Center.
   *
   * @param stateCount reading state module counts from horizontal scan
   * @param i row where finder pattern may be found
   * @param j end of possible finder pattern in row
   * @return true if a finder pattern candidate was found this time
   */
  private boolean handlePossibleCenterNew(int[] stateCount, int i, int j) {
	  int finderLength = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] + stateCount[4];
	  float centerX = j - (finderLength /2);	  
	  float centerY = crossCheckVertical(i, (int) centerX, stateCount[2], finderLength);
	    if (!Float.isNaN(centerX)) {
	      // Re-cross check
	      centerX = crossCheckHorizontal((int) centerX, (int) centerY, stateCount[2], finderLength);
	      if (!Float.isNaN(centerX)) {
	        float estimatedModuleSize = (float) finderLength / 7.0f;
	        boolean found = false;
			  for (int index = 0; index < possibleCenters.size(); index++) {
				  FinderPattern center = possibleCenters.get(index);
				  // Look for about the same center and module size:
				  if (center.aboutEquals(estimatedModuleSize, centerY, centerX)) {
				    possibleCenters.set(index, center.combineEstimate(centerY, centerX, estimatedModuleSize));
				    found = true;
				    break;
				  }
			  }
			  if (!found) { 
				  FinderPattern point = new FinderPattern(centerX, centerY, estimatedModuleSize);
				  possibleCenters.add(point);
			  }
			  return true;
	      }
	    }
	  return false;
  }
  
  /** Created by Solon
   * @return 3 FinderPatterns from list of candidates so that they form a nearly-right-angle triangle
   * @throws NotFoundException if 3 such finder patterns do not exist
   */
  private FinderPattern[] selectBestPatternsNew(int minModuleSize) throws NotFoundException {
	  //Filter impossible centers by information of min module size
	if(minModuleSize>0 && possibleCenters.size() > 3) {
		for (int i = 0; i < possibleCenters.size() && possibleCenters.size() > 3; i++) {
		    FinderPattern center = possibleCenters.get(i);
		    if(center.getEstimatedModuleSize() < minModuleSize){
		    	possibleCenters.remove(i);
			      i--;
		    }
		}
	}

	int startSize = possibleCenters.size();
    if (startSize < 3) {
      // Couldn't find enough finder patterns
    	NotFoundException error=NotFoundException.getNotFoundInstance();
    	/*String errorMsg="";
    	for (FinderPattern center : possibleCenters) {
    		errorMsg += "One center found with x "+center.getX()+" and y "+center.getY()+"\n";
    	}
    	if(errorMsg.isEmpty()) errorMsg="no center has found";
    	error.setErrorMessage("There are no enough finder patterns we can find. FinderPatternFinder. \n Details:" + errorMsg);
    	*/
    	error.setErrorMessage("There are no enough finder patterns we can find. FinderPatternFinder. ");
        throw error;
    }
    
    FinderPattern[] patternInfo=null;
    // Filter outlier possibilities by whether they can form a right angle
	boolean done=false;
	int[] candidateIndex=null;
	for (int i = 0, length=possibleCenters.size(); i < length && !done; i++) {
	    float[][] slopeList=new float[length][2];
		FinderPattern baseCenter = possibleCenters.get(i);
		for (int j = 0; j < length && !done; j++) {
			FinderPattern tmpCenter = possibleCenters.get(j);
			//Let slope of vertical line be 10000
			float tmpSlope = ( ((tmpCenter.getX()-baseCenter.getX()) >1) || ((tmpCenter.getX()-baseCenter.getX()) <-1) )? 
					(tmpCenter.getY()-baseCenter.getY()) / (tmpCenter.getX()-baseCenter.getX()):10000;
			float tmpDist = ( (tmpCenter.getX()-baseCenter.getX())*(tmpCenter.getX()-baseCenter.getX()) ) 
							+ ( (tmpCenter.getY()-baseCenter.getY())*(tmpCenter.getY()-baseCenter.getY()) );
			int kCount=(j<(length-1))? j:(length-1);
			for(int k=0;k<kCount && !done;k++){
				//The distance should be at least 15 pixels in length (QR code version 1)
				if(slopeList[k][1] <15 || tmpDist <15) continue; 
				float slopeProduct=(slopeList[k][0]-tmpSlope)/((slopeList[k][0]*tmpSlope)+1);
				//Numbers are arbitrary
				if( (   ( tmpSlope>=10000 && (slopeList[k][0]<1/MAX_degree_error && slopeList[k][0]>-1/MAX_degree_error) ) ||
						( slopeList[k][0]>=10000 && (tmpSlope<1/MAX_degree_error && tmpSlope>-1/MAX_degree_error) ) ||
						( slopeProduct >MAX_degree_error || slopeProduct <-MAX_degree_error )
					)
					&& ( Math.abs(slopeList[k][1]-tmpDist) < 3*(slopeList[k][1]/4) ) //TODO: I still do not know why I need to set this error to be unacceptably high  
					&& (tmpCenter.getEstimatedModuleSize() < 1.3*baseCenter.getEstimatedModuleSize() 
							&& tmpCenter.getEstimatedModuleSize() > 0
					   ) 
					){
					int[] tmpCandidate={k,i,j,Math.round(tmpDist+slopeList[k][1]) >>1};
					//Hard code: use the triple which has largest distance
					if(candidateIndex ==null || candidateIndex.length != 4 || candidateIndex[3] < tmpCandidate[3]) 
						candidateIndex=tmpCandidate;
				}
			}
			slopeList[j][0]=tmpSlope;
			slopeList[j][1]=(tmpCenter.getEstimatedModuleSize() < 1.3*baseCenter.getEstimatedModuleSize() 
							&& tmpCenter.getEstimatedModuleSize() > 0)? tmpDist:0;
		}
	}
	if(candidateIndex ==null || candidateIndex.length != 4 || candidateIndex[3] <=0){
		// Couldn't find suitable finder pattern set
		NotFoundException error=NotFoundException.getNotFoundInstance();
    	error.setErrorMessage("There are no enough finder patterns we can find. FinderPatternFinder. ");
        throw error;
	}
	//Here we use the concept of connected component to calculate the position of each finder pattern
	patternInfo=new FinderPattern[]{
							        possibleCenters.get(candidateIndex[0]),
							        possibleCenters.get(candidateIndex[1]),
							        possibleCenters.get(candidateIndex[2])
	    							};
	ResultPoint.orderBestPatterns(patternInfo);
	patternInfo[0]=finderRefinement(patternInfo[0]);
	patternInfo[1]=finderRefinement(patternInfo[1]);
	patternInfo[2]=finderRefinement(patternInfo[2]);
	return patternInfo;
  }
  /**
   * Here we use the black points in the black square of finder pattern to give a better approximation on center of that finder pattern
   * @param finder
   * @return
   */
  private FinderPattern finderRefinement(FinderPattern finder){
	  int blockRadius= (int) Math.ceil(2.5*finder.getEstimatedModuleSize()); 
	  int baseX=Math.round(finder.getX()), baseY=Math.round(finder.getY());
	  float Xcount=0,Ycount=0,labelCount=0;
	  if( (baseX-blockRadius <0) || (baseY-blockRadius <0) || (baseX+blockRadius >=image.getWidth()) 
			  || (baseY+blockRadius >=image.getHeight()) ) return finder;
	  int[][] connectedLabel=new int[2*blockRadius+1][2*blockRadius+1];
	  for(int i=0,j=0;i<(2*blockRadius+1);){
		  connectedLabel[i][j]=0;
		  j++;
		  if(j>2*blockRadius){
			  i++;
			  j=0;
		  }
	  }
	  
	  int label=1, offsetX=baseX-blockRadius, offsetY=baseY-blockRadius;
	  for(int i=0,j=0;i<(2*blockRadius+1);){
		  if(image.get(i+offsetX,j+offsetY)){
			  if(i>0 && j>0 && connectedLabel[i-1][j-1] >0) 
				  connectedLabel[i][j]=connectedLabel[i-1][j-1];
			  if(j>0 && connectedLabel[i][j-1] >0 
					  && (connectedLabel[i][j]==0 || connectedLabel[i][j]>connectedLabel[i][j-1]) ) 
				  connectedLabel[i][j]=connectedLabel[i][j-1];
			  if(i>0 && connectedLabel[i-1][j] >0 
					  && (connectedLabel[i][j]==0 || connectedLabel[i][j]>connectedLabel[i-1][j]) ) 
				  connectedLabel[i][j]=connectedLabel[i-1][j];
			  
			  if(i>1 && j>1 && connectedLabel[i-2][j-2] >0 
					  && (connectedLabel[i][j]==0 || connectedLabel[i][j]>connectedLabel[i-2][j-2]) ) 
				  connectedLabel[i][j]=connectedLabel[i-2][j-2];
			  
			  if(i>0 && j>1 && connectedLabel[i-1][j-2] >0 
					  && (connectedLabel[i][j]==0 || connectedLabel[i][j]>connectedLabel[i-1][j-2]) ) 
				  connectedLabel[i][j]=connectedLabel[i-1][j-2];
			  if(j>1 && connectedLabel[i][j-2] >0 
					  && (connectedLabel[i][j]==0 || connectedLabel[i][j]>connectedLabel[i][j-2]) ) 
				  connectedLabel[i][j]=connectedLabel[i][j-2];
			  
			  if(i>1 && j>0 && connectedLabel[i-2][j-1] >0 
					  && (connectedLabel[i][j]==0 || connectedLabel[i][j]>connectedLabel[i-2][j-1]) ) 
				  connectedLabel[i][j]=connectedLabel[i-2][j-1];
			  if(i>1 && connectedLabel[i-2][j] >0 
					  && (connectedLabel[i][j]==0 || connectedLabel[i][j]>connectedLabel[i-2][j]) ) 
				  connectedLabel[i][j]=connectedLabel[i-2][j];
			  
			  if(connectedLabel[i][j]==0){
				  connectedLabel[i][j]=label;
				  label++;
			  }
		  }
		  else{
			  if(i>0 && j>0 && connectedLabel[i-1][j-1] >0 
					  && connectedLabel[i][j-1]==connectedLabel[i-1][j-1] && connectedLabel[i-1][j]==connectedLabel[i-1][j-1])
				  connectedLabel[i][j]=connectedLabel[i-1][j-1];
		  }
		  
		  j++;
		  if(j>2*blockRadius){
			  i++;
			  j=0;
		  }
	  }
	  label=connectedLabel[blockRadius][blockRadius];
	  for(int i=0,j=0;i<(2*blockRadius+1);){
		  if(connectedLabel[i][j]==label){
			  Xcount+=(i+offsetX);
			  Ycount+=(j+offsetY);
			  labelCount++;
		  }
		  j++;
		  if(j>2*blockRadius){
			  i++;
			  j=0;
		  }
	  }
	  if(Xcount<=0 || Ycount<=0 || labelCount<=0) return finder;
	  else return new FinderPattern( Xcount/labelCount, Ycount/labelCount, (float) Math.sqrt(labelCount) ); 
  }

}
