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
 *
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
 *
 */

package com.google.zxing.common;

import java.util.ArrayList;
import java.util.List;

import com.google.zxing.NotFoundException;
import com.google.zxing.color.RGBColorWrapper;
import com.google.zxing.qrcode.detector.AlignmentPattern;
import com.google.zxing.qrcode.detector.FinderPattern;

/**
 * @author Sean Owen
 * 
 * Modified by Solon Li in late May 2013 
 * sampleGrid() using different classes of transform class are inserted
 * add some functions to increase accuracy on sampling points
 * sample five pixels instead of one on sampling one point 
 */
public final class DefaultGridSampler extends GridSampler {

  @Override
  public BitMatrix sampleGrid(BitMatrix image,
                              int dimensionX,
                              int dimensionY,
                              float p1ToX, float p1ToY,
                              float p2ToX, float p2ToY,
                              float p3ToX, float p3ToY,
                              float p4ToX, float p4ToY,
                              float p1FromX, float p1FromY,
                              float p2FromX, float p2FromY,
                              float p3FromX, float p3FromY,
                              float p4FromX, float p4FromY) throws NotFoundException {

    PerspectiveTransform transform = PerspectiveTransform.quadrilateralToQuadrilateral(
        p1ToX, p1ToY, p2ToX, p2ToY, p3ToX, p3ToY, p4ToX, p4ToY,
        p1FromX, p1FromY, p2FromX, p2FromY, p3FromX, p3FromY, p4FromX, p4FromY);

    return sampleGrid(image, dimensionX, dimensionY, transform);
  }

  @Override
  public BitMatrix sampleGrid(BitMatrix image,
                              int dimensionX,
                              int dimensionY,
                              PerspectiveTransform transform) throws NotFoundException {
    if (dimensionX <= 0 || dimensionY <= 0) {
    	NotFoundException error=NotFoundException.getNotFoundInstance();
    	error.setErrorMessage("Error on dimension in sampleGrid");
        throw error;   
    }
    BitMatrix bits = new BitMatrix(dimensionX, dimensionY);
    float[] points = new float[dimensionX << 1];
    for (int y = 0; y < dimensionY; y++) {
      int max = points.length;
      float iValue = (float) y + 0.5f;
      for (int x = 0; x < max; x += 2) {
        points[x] = (float) (x >> 1) + 0.5f;
        points[x + 1] = iValue;
      }
      try {
    	transform.transformPoints(points);
        // Quick check to see if points transformed to something inside the image;
        // sufficient to check the endpoints
        checkAndNudgePoints(image, points);
        for (int x = 0; x < max; x += 2) {
          //Here, instead of getting only one pixel to determine the sample point
          //We also check the pixels around it to reduce rounding error on pixel locations
          if (image.get((int) points[x], (int) points[x + 1])) {
            //Black(-ish) pixel
            bits.set(x >> 1, y);
          }
        }
      } catch (ArrayIndexOutOfBoundsException aioobe) {
	        // This feels wrong, but, sometimes if the finder patterns are misidentified, the resulting
	        // transform gets "twisted" such that it maps a straight line of points to a set of points
	        // whose endpoints are in bounds, but others are not. There is probably some mathematical
	        // way to detect this about the transformation that I don't know yet.
	        // This results in an ugly runtime exception despite our clever checks above -- can't have
	        // that. We could check each point's coordinates but that feels duplicative. We settle for
	        // catching and wrapping ArrayIndexOutOfBoundsException.
	    	NotFoundException error=NotFoundException.getNotFoundInstance();
	    	error.setErrorMessage(aioobe.getMessage()+" problem from array out of bound");
	    	throw error;
      } catch (NotFoundException e){
	    	NotFoundException error=NotFoundException.getNotFoundInstance();
	      	error.setErrorMessage(e.getErrorMessage()+"sampling image points are out of image bound");
	      	throw error;
      }
    }
    return bits;
  }
  
  public int[] getWhiteSamplePoints(int dimension, 
		  RGBColorWrapper colorWrapper, 
		  PerspectiveTransformGeneral transform,
		  float moduleSize) {
	  int numSmpPnts = 12;
	  int[] whiteSmpPnts = new int[2*numSmpPnts];
	  int[] whiteBitMatPnts = {1,1, 1,5, 5,1, 5,5
		  		, dimension-2,1, dimension-2,5, dimension-6,1, dimension-6,5
		  		, 1, dimension-2, 5,dimension-2, 1,dimension-6, 5,dimension-6
		  		};
//	  int[] whiteBitMatPnts = {0,0, dimension-1,0, 0, dimension-1, dimension-1, dimension-1};
	  
	  float[] points = new float[whiteBitMatPnts.length];
	  for (int i = 0; i < numSmpPnts; i++) {
		  points[i*2] = (float)whiteBitMatPnts[i*2] + 0.5f;
		  points[i*2+1] = (float)whiteBitMatPnts[i*2+1] + 0.5f;
	  }
	  transform.transformPoints(points);
	  try {
		checkAndNudgePoints(colorWrapper.getBlackMatrix(), points);
	  } catch (NotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	  }
	  
	// shift points to white blocks surrounding the qr code
//	  float shiftDist = 1.5f * moduleSize;
//	  int direFlag = 1;
//	  if (points[0] < points[6]) direFlag = -1;
//	  points[0] = points[0] + direFlag * shiftDist;
//	  points[6] = points[6] - direFlag * shiftDist;
//	  direFlag = 1;
//	  if (points[1] < points[7]) direFlag = -1;
//	  points[1] = points[1] + direFlag * shiftDist;
//	  points[7] = points[7] - direFlag * shiftDist;
//	  direFlag = 1;
//	  if (points[2] < points[4]) direFlag = -1;
//	  points[2] = points[2] + direFlag * shiftDist;
//	  points[4] = points[4] - direFlag * shiftDist;
//	  direFlag = 1;
//	  if (points[3] < points[5]) direFlag = -1;
//	  points[3] = points[3] + direFlag * shiftDist;
//	  points[5] = points[5] - direFlag * shiftDist;
	  
	  for (int i = 0; i < points.length; i++) 
		  whiteSmpPnts[i] = (int)(points[i] + 0.5f);
	  
	  return whiteSmpPnts;
	}
  
  public BitMatrix sampleGrid(BitMatrix image,
          int dimensionX,
          int dimensionY,
          PerspectiveTransformGeneral transform) throws NotFoundException {
		if (dimensionX <= 0 || dimensionY <= 0) {
			throw NotFoundException.getNotFoundInstance();      
		}
		BitMatrix bits = new BitMatrix(dimensionX, dimensionY);
		float[] points = new float[dimensionX << 1];
		for (int y = 0; y < dimensionY; y++) {
			int max = points.length;
			float iValue = (float) y+0.5f;
			for (int x = 0; x < max; x += 2) {
				points[x] = (float) (x >> 1)+0.5f;
				points[x + 1] = iValue;
			}
			try {
				transform.transformPoints(points);
				// Quick check to see if points transformed to something inside the image;
				// sufficient to check the endpoints
				checkAndNudgePoints(image, points);
/*				try{
					if(dimensionX>120) bestFitLine(points);
				}catch(Exception e){ }
*/				
				for (int x = 0; x < max; x += 2) {					
					int baseX=(int) (points[x]+0.5f);
					int baseY=(int) (points[x + 1]+0.5f);
					if (image.get(baseX, baseY)) {
						// Black(-ish) pixel
						bits.set(x >> 1, y);
					}
				}
			} catch (ArrayIndexOutOfBoundsException aioobe) {
		        // This feels wrong, but, sometimes if the finder patterns are misidentified, the resulting
		        // transform gets "twisted" such that it maps a straight line of points to a set of points
		        // whose endpoints are in bounds, but others are not. There is probably some mathematical
		        // way to detect this about the transformation that I don't know yet.
		        // This results in an ugly runtime exception despite our clever checks above -- can't have
		        // that. We could check each point's coordinates but that feels duplicative. We settle for
		        // catching and wrapping ArrayIndexOutOfBoundsException.
		    	NotFoundException error=NotFoundException.getNotFoundInstance();
		    	error.setErrorMessage(aioobe.getMessage()+" problem from array out of bound");
		    	throw error;
	        } catch (NotFoundException e){
		    	NotFoundException error=NotFoundException.getNotFoundInstance();
		      	error.setErrorMessage(e.getErrorMessage()+"sampling image points are out of image bound");
		      	throw error;
	        } 
		}
		return bits;
	}
  public BitMatrix sampleGridAffine(BitMatrix image, int dimensionX,int dimensionY,PerspectiveTransformGeneral transform) throws NotFoundException {
	  if (dimensionX <= 0 || dimensionY <= 0) {
		  throw NotFoundException.getNotFoundInstance();      
	  }
	  BitMatrix bits = new BitMatrix(dimensionX, dimensionY);
	  for (int y = 0; y < dimensionY; y++) {
		  float[] endPoint=new float[]{0.5f,y+0.5f,dimensionX-0.5f,y+0.5f};
		  transform.transformPoints(endPoint);
		  try {
				checkAndNudgePoints(image, endPoint);
	      } catch (NotFoundException e){
		    	NotFoundException error=NotFoundException.getNotFoundInstance();
		      	error.setErrorMessage(e.getErrorMessage()+"sampling image points are out of image bound");
		      	throw error;
	      }
		  int[] points=getLinePoint(endPoint,dimensionX);
		  if(points==null || points.length<dimensionX){
			  NotFoundException error=NotFoundException.getNotFoundInstance();
		      error.setErrorMessage("There is a problem in reading a line.");
		      throw error;
		  }
		  int max=points.length;
		  for (int x = 0; x < max; x += 2) {					
				if (image.get(points[x], points[x + 1])) {
					// Black(-ish) pixel
					bits.set(x >> 1, y);
				}
		  }
	  }
	  return bits;
  }
  /**
   * This method finds the locations of modules along the line connecting given end points
   * The line between points are created using Bresenham's algorithm with little modification
   * Reference: http://en.wikipedia.org/wiki/Bresenham's_line_algorithm
   * @param endPoint end point of the sampling line (should be 4 entries, or 2 points, only)
   * @param dimension number of modules along the line, endPoint included
   * @return
   */
  public static int[] getLinePoint(float[] endPoint, int dimensionX){
	  if(endPoint.length !=4 || dimensionX<=0) return null;
	  int x0=(int) (endPoint[0]+0.5f), y0=(int) (endPoint[1]+0.5f);
	  int x1=(int) (endPoint[2]+0.5f), y1=(int) (endPoint[3]+0.5f);
	  int dx=(x1>x0)? x1-x0:x0-x1, dy=(y1>y0)? y1-y0:y0-y1;
	  int sx=(x1>x0)? 1:(x1<x0)? -1:0, sy=(y1>y0)? 1:(y1<y0)? -1:0;
	  int error=dx-dy;
	  int counter=0, samplePoint=0;
	  float sampleP=0, samplingPeriod=(dx+dy)/(dimensionX-1.0f);
	  int[] sampledPoint=new int[dimensionX <<1];
	  int index=0;
	  if(samplingPeriod<=1) return null;
	  while(x0 !=x1 || y0!=y1){
		  //Do the work here
		  if(counter>=samplePoint){
			  if(index >=(dimensionX<<1)) return null;
			  sampledPoint[index]=x0;
			  sampledPoint[index+1]=y0;
			  index+=2;
			  
			  //counter=0;
			  sampleP+=samplingPeriod;
			  samplePoint= (int) (sampleP+0.5f);
			  //sampleP-=samplePoint;
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
	  //Do the work here
	  if(index <(dimensionX<<1)) {
		  sampledPoint[index]=x0;
		  sampledPoint[index+1]=y0;
		  index+=2;  
	  }
	  if(index !=(dimensionX<<1)) return null;
	  else return sampledPoint;
  }
  public BitMatrix sampleGrid(BitMatrix image,
          int dimensionX,
          int dimensionY,
          AffineTransform transform) throws NotFoundException {
		if (dimensionX <= 0 || dimensionY <= 0) {
			throw NotFoundException.getNotFoundInstance();      
		}
		BitMatrix bits = new BitMatrix(dimensionX, dimensionY);
		float[] points = new float[dimensionX << 1];
		for (int y = 0; y < dimensionY; y++) {
			int max = points.length;
			float iValue = (float) y;
			for (int x = 0; x < max; x += 2) {
				points[x] = (float) (x >> 1);
				points[x + 1] = iValue;
			}
			transform.transformPoints(points);
			// Quick check to see if points transformed to something inside the image;
			// sufficient to check the endpoints
			checkAndNudgePoints(image, points);
			try {
				for (int x = 0; x < max; x += 2) {
					if (image.get((int) points[x], (int) points[x + 1])) {
						// Black(-ish) pixel
						bits.set(x >> 1, y);
					}
				}
			} catch (ArrayIndexOutOfBoundsException aioobe) {
			// This feels wrong, but, sometimes if the finder patterns are misidentified, the resulting
			// transform gets "twisted" such that it maps a straight line of points to a set of points
			// whose endpoints are in bounds, but others are not. There is probably some mathematical
			// way to detect this about the transformation that I don't know yet.
			// This results in an ugly runtime exception despite our clever checks above -- can't have
			// that. We could check each point's coordinates but that feels duplicative. We settle for
			// catching and wrapping ArrayIndexOutOfBoundsException.
			throw NotFoundException.getNotFoundInstance();
			}
		}
		return bits;
	}
  public float[][] getSampledPoints(BitMatrix image,
          int dimensionX,
          int dimensionY,
          PerspectiveTransformGeneral transform) throws NotFoundException {
		if (dimensionX <= 0 || dimensionY <= 0) {
			throw NotFoundException.getNotFoundInstance();      
		}
		float[][] pos = new float[dimensionY][2*dimensionX];
		
		float[] points = new float[dimensionX << 1];
		for (int y = 0; y < dimensionY; y++) {
			int max = points.length;
			float iValue = (float) y+0.5f;
			for (int x = 0; x < max; x += 2) {
				points[x] = (float) (x >> 1)+0.5f;
				points[x + 1] = iValue;
			}
			transform.transformPoints(points);
			// Quick check to see if points transformed to something inside the image;
			// sufficient to check the endpoints
			checkAndNudgePoints(image, points);
			pos[y] = points.clone();
		}
		return pos;
	}
  /**
   * Create best fit line using least square method and use it to refine the values in points such that they form a better lines
   * Reference: http://hotmath.com/hotmath_help/topics/line-of-best-fit.html http://mathworld.wolfram.com/Point-LineDistance2-Dimensional.html
   * dimensions larger than 5000*5000 may create number overflow
   * The code is combination of bestFitLine() and refinedPoint() in Detector
   * @param points
   * if it is a vertical line, the slope will be Float.POSITIVE_INFINITY and y-intercept will be the x-coordinate of first entry in mainDragonal 
   */
  private static void bestFitLine(float[] points) throws Exception{
	  double mainX=0, mainY=0, mainXX=0, mainXY=0, mainN=0, mainM=0;
	  int length=points.length >>1;
	  for(int i=0, count=length<<1;i<count;i+=2){
			mainX+=points[i];
			mainY+=points[i+1];
			mainXX+=points[i] * points[i];
			mainXY+=points[i] * points[i+1];
			mainN++;
	  }
	  if(mainN< ((3*length) >>2) ) return;
	  mainM= (( (mainN*mainXX) - (mainX*mainX) ) !=0)? //Check if it is vertical line 
			( mainN*mainXY - (mainX*mainY) ) / ( (mainN*mainXX) - (mainX*mainX) ):Double.POSITIVE_INFINITY; //TODO: Value may be as high as 10M, number overflow is possible?
	  mainN=(mainM != Double.POSITIVE_INFINITY)? (mainY-mainM*mainX) / mainN : points[0]; //If it is a vertical line, use x-intercept
	  //Now refine the points according to the line equ.
	  //Given the line be y=mx+c, the closest point from (a,b) on line can be found by (x,y)= (0,c) + ( (a,b-c) dot (1,m) ) * (1,m) / sqrt(m^2 +1)
	  //=( (a+mb-mc) / (m^2+1) , ( (a+mb-mc)*m / (m^2+1) )+c ) = ( (a+mb-mc) / (m^2+1) , mx+c )
	  for(int i=0, count=length<<1;i<count;i+=2){
		  mainX=(mainM != Double.POSITIVE_INFINITY)? 
					(points[i]+mainM*points[i+1]-mainM*mainN) / ((mainM*mainM)+1) : mainN; //If vertical line, just modify the x coordinate
		  mainY=(mainM != Float.POSITIVE_INFINITY)?
					mainM*mainX+mainN:points[i+1];
		  points[i]=(float) mainX;
		  points[i+1]=(float) mainY;
	  }
  }

	@Override
	public BitMatrix[] colorSampleGrid(float[] white, RGBColorWrapper colorWrapper,
        int dimensionX,
        int dimensionY,
        PerspectiveTransformGeneral transform,
        int layerNum) throws NotFoundException {
		
//		long s = System.currentTimeMillis();
		
	  	if(colorWrapper == null) return null;
		if (dimensionX <= 0 || dimensionY <= 0) {
			throw NotFoundException.getNotFoundInstance();      
		}
		BitMatrix[] channelBits = new BitMatrix[layerNum];
		for (int i = 0; i < layerNum; i++)
			channelBits[i] = colorWrapper.getChannelHint(i)?new BitMatrix(dimensionX, dimensionY):null;
		
		// collect center coordinates
		float[] points = new float[dimensionX << 1];
		int[][][] coordinates = new int[dimensionX][dimensionY][2];
		int max = points.length;
		for (int y = 0; y < dimensionY; y++) {
			float iValue = (float) y+0.5f;
			for (int x = 0; x < max; x += 2) {
				points[x] = (float) (x >> 1)+0.5f;
				points[x + 1] = iValue;
			}
			try {
				transform.transformPoints(points);
				// Quick check to see if points transformed to something inside the image;
				// sufficient to check the endpoints
				checkAndNudgePoints(colorWrapper.getBlackMatrix(), points);			
				for (int x = 0; x < max; x += 2) {					
					coordinates[x>>1][y][0] = (int) (points[x]+0.5f);
					coordinates[x>>1][y][1] = (int) (points[x + 1]+0.5f);
					
					/* old code copy
					int baseX = (int) (points[x]+0.5f);
					int baseY = (int) (points[x + 1]+0.5f);
					boolean[] rgb = colorWrapper.colorClassify(baseX, baseY, white, colorWrapper.getChannelHint());
					for (int i=0; i<layerNum; i++)
						if (colorWrapper.getChannelHint(i) && rgb[i])//rgb[i]==0
							channelBits[i].set(x >> 1, y);
					*/
				}
			} catch (ArrayIndexOutOfBoundsException aioobe) {
		        // This feels wrong, but, sometimes if the finder patterns are misidentified, the resulting
		        // transform gets "twisted" such that it maps a straight line of points to a set of points
		        // whose endpoints are in bounds, but others are not. There is probably some mathematical
		        // way to detect this about the transformation that I don't know yet.
		        // This results in an ugly runtime exception despite our clever checks above -- can't have
		        // that. We could check each point's coordinates but that feels duplicative. We settle for
		        // catching and wrapping ArrayIndexOutOfBoundsException.
		    	NotFoundException error=NotFoundException.getNotFoundInstance();
		    	error.setErrorMessage(aioobe.getMessage()+" problem from array out of bound");
		    	throw error;
	        } catch (NotFoundException e){
		    	NotFoundException error=NotFoundException.getNotFoundInstance();
		      	error.setErrorMessage(e.getErrorMessage()+"sampling image points are out of image bound");
		      	throw error;
	        } 
		}
		
		// classify
		if (!colorWrapper.getClassifierCMIFlag()) { // without cross-module interference (CMI)
			for (int y = 0; y < dimensionY; y++) {
				for (int x = 0; x < dimensionX; x++) {
					int baseX=coordinates[x][y][0];
					int baseY=coordinates[x][y][1];
					boolean[] rgb = colorWrapper.colorClassify(baseX, baseY, white, colorWrapper.getChannelHint());
					for (int i=0; i<layerNum; i++)
						if (colorWrapper.getChannelHint(i) && rgb[i])//rgb[i]==0
							channelBits[i].set(x, y);
				}
			}
		}
		else { // with CMI
			for (int y = 0; y < dimensionY; y++) {
				for (int x = 0; x < dimensionX; x++) {
					int[] y1 = new int[]{0,0}; 
					int[] y2 = new int[]{0,0};
					int[] x1 = new int[]{0,0}; 
					int[] x2 = new int[]{0,0};
					if (y != 0) {
						y1[0] = coordinates[x][y-1][0];
						y1[1] = coordinates[x][y-1][1];
					}
					else if (y != dimensionY - 1) {
						y2[0] = coordinates[x][y+1][0];
						y2[1] = coordinates[x][y+1][1];
					}
					if (x != 0) {
						x1[0] = coordinates[x-1][y][0];
						x1[1] = coordinates[x-1][y][1];
					}
					else if (x != dimensionX - 1) {
						x2[0] = coordinates[x+1][y][0];
						x2[1] = coordinates[x+1][y][1];
					}
					
					int[] xList = new int[]{coordinates[x][y][0], x1[0], x2[0], y1[0], y2[0]};
					int[] yList = new int[]{coordinates[x][y][1], x1[1], x2[1], y1[1], y2[1]};
					boolean[] rgb = colorWrapper.colorClassify(xList, yList, white, colorWrapper.getChannelHint());
					for (int i=0; i<layerNum; i++)
						if (colorWrapper.getChannelHint(i) && rgb[i])//rgb[i]==0
							channelBits[i].set(x, y);
				}
			}
		}
		
//		System.out.println(""+(System.currentTimeMillis() - s));
		return channelBits;
	}
	
}
