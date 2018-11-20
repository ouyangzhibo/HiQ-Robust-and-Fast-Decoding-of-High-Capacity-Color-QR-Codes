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

package com.google.zxing.qrcode.encoder;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.Version;

/**
 * This class provides functions to enlarge the alignment patterns along the diagonal and anti-diagonal of a QR code in order to increase decoding robustness
 * @author elky writing the basic code
 * @author solon li putting it into a separate class and add boundary checking
 *
 */
class MatrixUtilLargerAlign extends MatrixUtil {
	
	  private static final int[][] NEW_POSITION_DETECTION_PATTERN =  {
		  {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
		  {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
	      {1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1},
	      {1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1},
	      {1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1},
	      {1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1},
	      {1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1},
	      {1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1},
	      {1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1},
	      {1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1},
	      {1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1},
	      {1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1},
	      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
	      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
	  };
	  private static final int[][] BR_POSITION_ADJUSTMENT_PATTERN = {
		  {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
		  {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
		  {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
	      {1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1},
	      {1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1},
	      {1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1},
	      {1, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1},
	      {1, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1},
	      {1, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1},
	      {1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1},
	      {1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1},
	      {1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1},
	      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
	      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
	      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
	  };
	  private static final int[][] BIGGER_POSITION_ADJUSTMENT_PATTERN = {
	      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
	      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
	      {1, 1, 0, 0, 0, 0, 0, 0, 1, 1},
	      {1, 1, 0, 0, 0, 0, 0, 0, 1, 1},
	      {1, 1, 0, 0, 1, 1, 0, 0, 1, 1},
	      {1, 1, 0, 0, 1, 1, 0, 0, 1, 1},
	      {1, 1, 0, 0, 0, 0, 0, 0, 1, 1},
	      {1, 1, 0, 0, 0, 0, 0, 0, 1, 1},
	      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
	      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
	  };

	  private MatrixUtilLargerAlign() {
		super(); //do nothing
	  }
	  
	  static void rewriteFinderPattern(ByteMatrix matrix) throws WriterException {
		  embedNewPositionDetectionPatternsAndSeparators(matrix);
	  }
	  
	  static void rewriteBRAlignmentPattern(ByteMatrix matrix, int dimension) throws WriterException {
		  embedBRPositionAdjustmentPattern(dimension - 15,dimension - 24, matrix);
	  }
	  
	  static void rewriteAlignmentPattern(ByteMatrix matrix,Version version) throws WriterException {
		  RewritePositionAdjustmentPatterns(version,matrix);
	  }
	  
	  private static void embedNewHorizontalSeparationPattern(int xStart,int yStart, 
			  ByteMatrix matrix) throws WriterException {
		if(matrix.getWidth() < xStart+15) return;
		for (int x = 0; x < 15; ++x) {
			//if (!isEmpty(matrix.get(xStart + x, yStart))) {
			//throw new WriterException();
			//}
			matrix.set(xStart + x, yStart, 0);
		}
	  }
	  private static void embedNewVerticalSeparationPattern(int xStart,int yStart,
				ByteMatrix matrix) throws WriterException {
		  if(matrix.getHeight() < yStart+14) return;
		  for (int y = 0; y < 14; ++y) {
			  //if (!isEmpty(matrix.get(xStart, yStart + y))) {
			  //throw new WriterException();
			  //}
			  matrix.set(xStart, yStart + y, 0);
		  }
	  }
	  private static void embedBRPositionAdjustmentPattern(int xStart, int yStart, ByteMatrix matrix) {
		  if(matrix.getHeight() < yStart+15 || matrix.getWidth() < xStart+15) return;
		  for(int y = 0; y < 15; ++y) {
			  for(int x = 0; x < 15; ++x) {
				  matrix.set(xStart + x, yStart + y, BR_POSITION_ADJUSTMENT_PATTERN[y][x]);
			  }
		  }
	  }
	  
	  private static void embedBiggerPositionAdjustmentPattern(int xStart, int yStart, ByteMatrix matrix) {
		  if(matrix.getHeight() < yStart+16 || matrix.getWidth() < xStart+16) return;
		  for (int y = 0; y < 10; ++y) {
			  for (int x = 0; x < 10; ++x) {
		        matrix.set(xStart + x, yStart + y, BIGGER_POSITION_ADJUSTMENT_PATTERN[y][x]);
		      }
		  }
	  }
	  private static void embedNewPositionDetectionPattern(int xStart, int yStart, ByteMatrix matrix) {		  
		  for (int y = 0; y < 14; ++y) {
		      for (int x = 0; x < 14; ++x) {
		        matrix.set(xStart + x, yStart + y, NEW_POSITION_DETECTION_PATTERN[y][x]);
		      }
		    }
	  }
	  
	  private static void embedNewPositionDetectionPatternsAndSeparators(ByteMatrix matrix) throws WriterException {
		  // rewrite three new squares at corners.
		  int pdpWidth = NEW_POSITION_DETECTION_PATTERN[0].length;
		  // Left top corner
		  embedNewPositionDetectionPattern(0, 0, matrix);
		  // Right top corner
		  embedNewPositionDetectionPattern(matrix.getWidth() - pdpWidth, 0, matrix);
		  // Left Bottom corner
		  embedNewPositionDetectionPattern(0, matrix.getWidth() - pdpWidth, matrix);
		  
		  // Embed horizontal separation patterns around the squares.
		  int hspWidth = 15;
		  // Left top corner.
		  embedNewHorizontalSeparationPattern(0, hspWidth - 1, matrix);
		  // Right top corner.
		  embedNewHorizontalSeparationPattern(matrix.getWidth() - hspWidth,
		        hspWidth - 1, matrix);
		  // Left bottom corner.
		  embedNewHorizontalSeparationPattern(0, matrix.getWidth() - hspWidth, matrix);
		  
		  // Embed vertical separation patterns around the squares.
		  int vspSize = 14;
		  // Left top corner.
		  embedNewVerticalSeparationPattern(vspSize, 0, matrix);
		  // Right top corner.
		  embedNewVerticalSeparationPattern(matrix.getHeight() - vspSize - 1, 0, matrix);
		  // Left bottom corner.
		  embedNewVerticalSeparationPattern(vspSize, matrix.getHeight() - vspSize,
		        matrix);
	  }
	  
	  // Embed position adjustment patterns if need be.
	  /*private static void maybeEmbedPositionAdjustmentPatterns(Version version, ByteMatrix matrix) {
	    if (version.getVersionNumber() < 2) {  // The patterns appear if version >= 2
	      return;
	    }
	    int index = version.getVersionNumber() - 1;
	    int[] coordinates = POSITION_ADJUSTMENT_PATTERN_COORDINATE_TABLE[index];
	    int numCoordinates = POSITION_ADJUSTMENT_PATTERN_COORDINATE_TABLE[index].length;
	    for (int i = 0; i < numCoordinates; ++i) {
	      for (int j = 0; j < numCoordinates; ++j) {
	        int y = coordinates[i];
	        int x = coordinates[j];
	        if (x == -1 || y == -1) {
	          continue;
	        }
	        // If the cell is unset, we embed the position adjustment pattern here.
	        if (isEmpty(matrix.get(x, y))) {
	          // -2 is necessary since the x/y coordinates point to the center of the pattern, not the
	          // left top corner.
	          //choose 9 of the alignment patterns to enlarge them, which will be used for calculating the transformation matrix
	          //later in decoding process (version 40), just harcode them is ok, I think
	          // at first we only care about the version 40
	          if((x == 30 && y == 30)||(x == 58 && y == 58)||(x == 86 && y == 86)||(x == 114 && y == 58)||(x == 142 && y == 30)||(x == 30 && y == 142)||(x == 58 && y == 114)||(x == 114 && y == 114)||(x == 142 && y == 142))
	        	  embedBiggerPositionAdjustmentPattern(x - 4, y - 4, matrix);
	          else
	        	  embedPositionAdjustmentPattern(x - 2, y - 2, matrix);
	        }
	      }
	    }
	  }
	  */
	  private static void RewritePositionAdjustmentPatterns(Version version, ByteMatrix matrix) {
		    if (version.getVersionNumber() < 2) {  // The patterns appear if version >= 2
		      return;
		    }
		    int index = version.getVersionNumber() - 1;
		    int[] coordinates = POSITION_ADJUSTMENT_PATTERN_COORDINATE_TABLE[index];
		    int numCoordinates = POSITION_ADJUSTMENT_PATTERN_COORDINATE_TABLE[index].length;
		    
		    
		    int versionNum=version.getVersionNumber();
		    //Divide alignment encoding into different cases:
		    int caseNum = (versionNum > 6 && versionNum < 14)? 1 // for V7~V13, we need to enlarge 1 inside alignment pattern
		    		: (versionNum > 13 && versionNum < 21)? 2  // for V14~V20, we need to enlarge 4 inside alignment patterns
		    		: (versionNum > 20 && versionNum < 28)? 3 // for V21~V27, we need to enlarge 5 inside alignment patterns
		    		: (versionNum > 27 && versionNum < 35)? 4 // for V28~V34. we need to enlarge 8 inside alignment patterns
		    		: (versionNum > 34 && versionNum < 41)? 5 // for V35~V40, we need to enlarge 9 inside alignment patterns
		    		: 0; //For other cases, no need to enlarge any inside alignment pattern
		    for (int i = 0; i < numCoordinates; ++i) {
	  	      for (int j = 0; j < numCoordinates; ++j) {
	  	        int y = coordinates[i];
	  	        int x = coordinates[j];
	  	        if(x == -1 || y == -1) continue;
	  	        if(i ==0 || j==0) continue;
	  	        //check the POSITION_ADJUSTMENT_PATTERN_COORDINATE_TABLE for correct coordinates
	  	        if( (i==j && caseNum >0 && i<=caseNum) //along the diagonal
	  	        	//Along the anti-diagonal
	  	        	|| (i!=j && i==1 && j==caseNum) || (i!=j && j==1 && i==caseNum)
	  	        	|| (i!=j && caseNum >3 && i == 2 && j == (caseNum-1))
	  	        	|| (i!=j && i == (caseNum-1) && j == 2) )
	  	        	embedBiggerPositionAdjustmentPattern(x - 4, y - 4, matrix);
	  	        //Enlarge the bottom-right alignment pattern, no matter what version it is
	  	        /*if((x == 30 && y == 30)||(x == 58 && y == 58)||(x == 86 && y == 86)
	  	        		||(x == 114 && y == 58)||(x == 142 && y == 30)||(x == 30 && y == 142)
	  	        		||(x == 58 && y == 114)||(x == 114 && y == 114)||(x == 142 && y == 142))
	  	        	embedBiggerPositionAdjustmentPattern(x - 4, y - 4, matrix);*/
	  	      }
		    }
	  }

}