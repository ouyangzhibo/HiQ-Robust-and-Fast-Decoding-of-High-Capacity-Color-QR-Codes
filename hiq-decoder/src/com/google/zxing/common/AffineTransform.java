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

import java.util.Arrays;

/**
 * This class implements an affine transform in two dimensions. 
 * Given three source and destination points respectively, it will compute the transformation implied between them. 
 * Some codes (architecture, constructors and functions transformPoints, times) are copied from PerspectiveTransform.java with modification
 * The algorithm is based on section 2.1.3 of R.A. van der Stelt's 
 * "DIGITAL IMAGE WARPING abstract of Digital Image Warping, George Wolberg, IEEE Computer Society Press", written in 10 Oct, 1996
 * I get this doc from http://rvdstelt.home.xs4all.nl/docs/warping.pdf
 * However, here I use (u,v) to represent the positions on transformed image and (x,y) on original images
 * 
 * @author Solon Li
 */

public final class AffineTransform {
	  //row first, then column
	  private final float a11;
	  private final float a12;
	  private final float a13;
	  private final float a21;
	  private final float a22;
	  private final float a23;
	  private final float a31;
	  private final float a32;
	  private final float a33;
	  
	  private AffineTransform(float a11, float a12, float a13,
              float a21, float a22, float a23,
              float a31, float a32, float a33) {
		  this.a11 = a11;
		  this.a12 = a12;
		  this.a13 = a13;
		  this.a21 = a21;
		  this.a22 = a22;
		  this.a23 = a23;
		  this.a31 = a31;
		  this.a32 = a32;
		  this.a33 = a33;
	  }
	  
	  /**
	   * Build an affine transformation matrix based on the correspondence (u,v) <-> (x,y) of three points
	   * It is enough as the last column is always 0,0,1 (It may be row depends on your definition. Here I follow the reference) 
	   * @param u0,v0 u1,v1 u2,v2 positions of the points on transformed image
	   * @param x0,y0 x1,y1 x2,y2 their positions on the original image respectively 
	   * @return affine transformation that map points from u,v space into x,y space
	   */
	  public static AffineTransform getAffineMatrix(float u0, float v0,
						              float u1, float v1,
						              float u2, float v2,
						              float x0, float y0,
						              float x1, float y1,
						              float x2, float y2) {
		  //Here we mark position as row first, then column to match the reference. The last line of this function will transpose the matrix to match the definition of AffineTransform
		  float[] M1= new float[]{v1-v2,v2-v0,v0-v1,
				  					u2-u1,u0-u2,u1-u0,
				  					u1*v2 - u2*v1, u2*v0-u0*v2, u0*v1 - u1*v0 //fix a tepo in reference, a33 should be u0*v1 - u1*v0
		  						 };
		  float[] M2= new float[]{x0,y0,1,x1,y1,1,x2,y2,1};
		  float detU=u0*M1[0] + v0*M1[3] + M1[6];
		  if(detU ==0) return null; //Three points collinear
		  M1=matrixMultiplication(M1,M2,3);
		  M1=matrixScalarMultiplication(1/detU,M1);
		  return new AffineTransform(M1[0],M1[1],M1[2],
				  						M1[3],M1[4],M1[5],
				  						M1[6],M1[7],M1[8]
				  					);
				  					
/*		  return new AffineTransform(M1[0],M1[3],M1[6],
									M1[1],M1[4],M1[7],
									M1[2],M1[5],M1[8]
				);
*/		  
	  }
	  
	  public static PerspectiveTransformGeneral toPerspectiveTransform(AffineTransform source){
		  return new PerspectiveTransformGeneral(source.a11,source.a12,source.a13,
				  							source.a21,source.a22,source.a23,
				  							source.a31, source.a32,source.a33);
	  }
	  /**
	   * Transform the points from target coordinate (u,v) to coordinates on original image (x,y) 
	   * @param points
	   */
	  public void transformPoints(float[] points) {
		  float a11 = this.a11;
		  float a12 = this.a12;
		  float a21 = this.a21;
		  float a22 = this.a22;
		  float a31 = this.a31;
		  float a32 = this.a32;
		  for (int i = 0, max = points.length; i < max; i += 2) {
			  float x = points[i];
		      float y = points[i + 1];
		      //Here depends on setting in getTransform
		      points[i] = (a11 * x + a21 * y + a31);
		      points[i + 1] = (a12 * x + a22 * y + a32);
		  }
	  }
	  /**
	   * Transform the points from target coordinate (x,y) back to coordinate on the received image (u,v) 
	   * @param points
	   */
	  public void transformPoints(float[] xValues, float[] yValues) {
		  //As transformation does not involve the last column in affine transformation, we do not need to consider a13,a23,a33
		  float a11 = this.a11;
		  float a12 = this.a12;
		  float a21 = this.a21;
		  float a22 = this.a22;
		  float a31 = this.a31;
		  float a32 = this.a32;
		  int n = (xValues.length <= yValues.length)? xValues.length:yValues.length;
		  for (int i = 0; i < n; i ++) {
		      float x = xValues[i];
		      float y = yValues[i];
		      xValues[i] = (a11 * x + a21 * y + a31);
		      yValues[i] = (a12 * x + a22 * y + a32);
		  }
	  }
	  public AffineTransform times(AffineTransform other) {
		    return new AffineTransform(a11 * other.a11 + a12 * other.a21 + a13 * other.a31,
		        a11 * other.a12 + a12 * other.a22 + a13 * other.a32,
		        a11 * other.a13 + a12 * other.a23 + a13 * other.a33,
		        a21 * other.a11 + a22 * other.a21 + a23 * other.a31,
		        a21 * other.a12 + a22 * other.a22 + a23 * other.a32,
		        a21 * other.a13 + a22 * other.a23 + a23 * other.a33,
		        a31 * other.a11 + a32 * other.a21 + a33 * other.a31,
		        a31 * other.a12 + a32 * other.a22 + a33 * other.a32,
		        a31 * other.a13 + a32 * other.a23 + a33 * other.a33);
	  }
	  /**multiply two matrix in float array, saved row by row
	   */
	  //Convenient method, yet to optimize
	  public static float[] matrixMultiplication(float[] foreMatrix, float[] backMatrix, int dimension){
		  int length=dimension*dimension;
		  if(foreMatrix.length != length || backMatrix.length != length ) return null;
		  float[] resultM=new float[length];
		  for(int i=0;i<dimension;i++){
			  float[] row=Arrays.copyOfRange(foreMatrix, i*dimension, (i+1)*dimension);
			  for(int j=0;j<dimension;j++){
				  int index=i*dimension+j;
				  resultM[index]=0;
				  for(int k=0;k<dimension;k++)
					  resultM[index] += row[k]*backMatrix[k*dimension+j];
			  }
		  }
		  return resultM;
	  }
	  /**
	   * multiple the matrix by given scalar
	   */
	  private static float[] matrixScalarMultiplication(float scalar, float[] m){
		  float[] result=new float[m.length];
		  for(int i=0,length=m.length;i<length;i++){
			  result[i]=m[i]*scalar;
		  }
		  return result;
	  }
	  private static AffineTransform transpose(AffineTransform m) {
		  return new AffineTransform(m.a11,m.a21,m.a31,m.a12,m.a22,m.a32,m.a13,m.a23,m.a33);
	  }
}