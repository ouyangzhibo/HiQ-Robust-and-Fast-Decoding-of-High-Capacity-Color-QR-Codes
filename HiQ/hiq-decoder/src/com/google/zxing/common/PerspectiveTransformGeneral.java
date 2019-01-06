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
 */

package com.google.zxing.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ListIterator;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPoint;

/**
 * This class is modifications of PerspectiveTransform class in zxing.
 * There are mainly three changes: first is changing the numbering of entry to row first, column second
 * Second is changing the way to create transformation matrix. 
 * Here we will have two methods to generate transformation matrix. One is use given four entries and solve a system of linear equations
 * Another is given a set (>4) of entries and solve the overdetermined system of linear equations.
 * References of the algorithm:  http://alumni.media.mit.edu/~cwren/interpolator/  http://www.robots.ox.ac.uk/~vgg/presentations/bmvc97/criminispaper/node3.html
 * The third change is minor, use double instead of float on entries.
 * Here I use (u,v) to represent the positions on transformed image and (x,y) on original images
 * 
 * @author Solon Li
 */
public final class PerspectiveTransformGeneral {
	//row first, then column
  private final double a11;
  private final double a12;
  private final double a13;
  private final double a21;
  private final double a22;
  private final double a23;
  private final double a31;
  private final double a32;
  private final double a33;
  
  private final boolean isMultiple;
  private final PerspectiveTransformGeneral[] transformMatrix;
  private final int sourceBoundX,sourceBoundY;
  
  //private final static float scaleUpFactor=1024;

  public PerspectiveTransformGeneral(double a11, double a12, double a13,
                               double a21, double a22, double a23,
                               double a31, double a32, double a33) {
    this.a11 = a11;
    this.a12 = a12;
    this.a13 = a13;
    this.a21 = a21;
    this.a22 = a22;
    this.a23 = a23;
    this.a31 = a31;
    this.a32 = a32;
    this.a33 = a33;
    this.isMultiple=false;
    this.transformMatrix=null;
    this.sourceBoundX=0;
    this.sourceBoundY=0;
  }
  //For the time being, we only support dividing them into 4 matrices
  private PerspectiveTransformGeneral(PerspectiveTransformGeneral[] transformMatrix, int sourceBoundX, int sourceBoundY) {
	  isMultiple=true;
	  this.sourceBoundX=sourceBoundX;
	  this.sourceBoundY=sourceBoundY;
	  this.transformMatrix=(transformMatrix !=null && transformMatrix.length ==5 && transformMatrix[4] !=null)? transformMatrix:null;
	  this.a11=this.a12=this.a13=this.a21=this.a22=this.a23=this.a31=this.a32=this.a33=0;
  }
  
  /**
   * Build a perspective transformation matrix based on the correspondence (u,v) <-> (x,y) of four points
   * @param u0,v0 u1,v1 u2,v2 u3,v3 positions of the points on transformed image
   * @param x0,y0 x1,y1 x2,y2 x3,y3 their positions on the original image respectively 
   * @return perspective transformation that map points from u,v space into x,y space
   */
  public static PerspectiveTransformGeneral getTransform(float u0, float v0,
												          float u1, float v1,
												          float u2, float v2,
												          float u3, float v3,
												          float x0, float y0,
												          float x1, float y1,
												          float x2, float y2,
												          float x3, float y3) {
	  /* Given matrix A=(a,b,c
	   * 				d,e,f
	   * 				g,h,1)  
	   * and sample point U=(u,v) and X=(x,y) s.t. AU=X. We can modify the equation to: 
	   * (u1, v1, 1, 0, 0, 0, -xu, -xv  * (a,b,c,d,e,f,g,h)^t = (x,y)^t
	   *  0, 0, 0, u1, v1, 1, -yu, -yv) 
	   *  With four points, we can extend the above matrix into a 8*8 matrix M such that getting M^-1 * (x0,y0,x1,y1,x2,y2,x3,y3)^t = (a,b,c,d,e,f,g,h)^t
	   * The problem is how to do the matrix inversion. We do it using Apache Common Math library. 
	   * If necessary, we can reduce the calculation complexity by Ma=x, M^t*M*a=M^t*X, a=(M^t*M)^-1 *M^t *X. 
	   * Here the upper left 6*6 submatrix of (M^t*M) is already factorized. It should shorten the time to get inverse
	   */
	  //x0=x0*scaleUpFactor;x1=x1*scaleUpFactor;x2=x2*scaleUpFactor;x3=x3*scaleUpFactor;
	  //y0=y0*scaleUpFactor;y1=y1*scaleUpFactor;y2=y2*scaleUpFactor;y3=y3*scaleUpFactor;
	  double[][] A=new double[][]{ {u0, v0, 1, 0, 0, 0, -(x0*u0), -(x0*v0)},
			  						{0, 0, 0, u0, v0, 1, -(y0*u0), -(y0*v0)},
			  						{u1, v1, 1, 0, 0, 0, -(x1*u1), -(x1*v1)},
			  						{0, 0, 0, u1, v1, 1, -(y1*u1), -(y1*v1)},
			  						{u2, v2, 1, 0, 0, 0, -(x2*u2), -(x2*v2)},
			  						{0, 0, 0, u2, v2, 1, -(y2*u2), -(y2*v2)},
			  						{u3, v3, 1, 0, 0, 0, -(x3*u3), -(x3*v3)},
			  						{0, 0, 0, u3, v3, 1, -(y3*u3), -(y3*v3)}
	  								};
	  try{
		  RealMatrix M=new Array2DRowRealMatrix(A);
		  RealVector constants = new ArrayRealVector(new double[]{x0,y0,x1,y1,x2,y2,x3,y3});
		  //RealMatrix Mt=M.transpose();
		  //M=Mt.multiply(M);
		  DecompositionSolver solver=new QRDecomposition(M).getSolver();
		  //constants = solver.solve(Mt.operate(constants));
		  constants = solver.solve(constants);
		  if(constants ==null || constants.getDimension() !=8) return null;
		  double[] R1=constants.toArray();
		  return new PerspectiveTransformGeneral(R1[0],R1[1],R1[2],
													R1[3],R1[4],R1[5],
													R1[6],R1[7],1
				  								);
	  } catch(Exception e){ return null; }
  }
  /**
   * Build a perspective transformation matrix based on the correspondence (u,v) <-> (x,y) of more than 4 points
   * @param float[] u positions of the points on transformed image. The values saved in this way: u[0] x coordinate of first point, u[1] y coordinate of the first point, u[2] x coordinate of the second  point......
   * @param float[] x positions of the points on original image. Structure same as u. The length of u and x should be the same and the points are matched accordingly 
   * @return perspective transformation that map points from u,v space into x,y space
   */
  public static PerspectiveTransformGeneral getTransform(float[] u, float[] x) {
	  /* Given matrix A=(a,b,c
	   * 				d,e,f
	   * 				g,h,1)  
	   * and sample point U=(u,v) and X=(x,y) s.t. AU=X. We can modify the equation to: 
	   * (u1, v1, 1, 0, 0, 0, -xu, -xv  * (a,b,c,d,e,f,g,h)^t = (x,y)^t
	   *  0, 0, 0, u1, v1, 1, -yu, -yv) 
	   *  With n points, we can extend the above matrix into a 2n*8 matrix M such that getting M^-1 * (x0,y0,x1,y1,x2,y2,x3,y3)^t = (a,b,c,d,e,f,g,h)^t
	   * The problem is how to do the matrix inversion (which should not exists as M is not square). We do it by QR decomposition using Apache Common Math library. 
	   * If necessary, we can reduce the calculation complexity by Ma=x, M^t*M*a=M^t*X, a=(M^t*M)^-1 *M^t *X. 
	   */
	  if(u.length != x.length || (u.length &0x01) !=0 || u.length<4) return null;
/*	  for(int i=0;i<x.length;i++){
		  x[i]=x[i]*scaleUpFactor;
	  }
*/	  
	  int length=u.length;
	  double[][] A=new double[length][8];
	  double[] b=new double[length];
	  for(int i=0;i<length;i+=2){
		A[i]=new double[]{u[i], u[i+1], 1, 0, 0, 0, -(x[i]*u[i]), -(x[i]*u[i+1])};
		A[i+1]=new double[]{0, 0, 0, u[i], u[i+1], 1, -(x[i+1]*u[i]), -(x[i+1]*u[i+1])};
		b[i]=x[i];
		b[i+1]=x[i+1];
	  }
	  try{
		  RealMatrix M=new Array2DRowRealMatrix(A);
		  RealVector constants = new ArrayRealVector(b);
		  //RealMatrix Mt=M.transpose();
		  //M=Mt.multiply(M);
		  DecompositionSolver solver=new QRDecomposition(M).getSolver();
		  //constants = solver.solve(Mt.operate(constants));
		  constants = solver.solve(constants);
		  if(constants ==null || constants.getDimension() !=8) return null;
		  double[] R1=constants.toArray();
		  return new PerspectiveTransformGeneral(R1[0],R1[1],R1[2],
													R1[3],R1[4],R1[5],
													R1[6],R1[7],1
				  								);
	  } catch(Exception e){ return null; }
  }
  
  
  /**
   * Divide the transform area into blocks, for each block, build a perspective transformation matrix based on the correspondence (u,v) <-> (x,y) of four points
   * Only support dividing the area into 4 squares for the time being
   * @param sourcePoint set of (u,v), or position on transformed image They should be arranged row by row, and have 2* (numOfBlockOnOneSide+1)^2 entries in total 
   * @param imagePoint set of (x,y), or position on original image. Arranged row by row, there should have (numOfBlockOnOneSide+1)^2 entries, corresponds to the entries in sourcePoint 
   * @param numOfBlockOnOneSide number of blocks divided on one side. 
   * @return perspective transformation that map points from u,v space into x,y space
   */
  public static PerspectiveTransformGeneral getTransform(float[] sourcePoint, ResultPoint[] imagePoint, int numOfBlockOnOneSide, int sourceDimension) {
	  if(numOfBlockOnOneSide <2) numOfBlockOnOneSide=2;
	  int offset=(numOfBlockOnOneSide+1), arrayLength=offset*offset;
	  if(sourcePoint.length != (arrayLength<<1) || imagePoint.length !=arrayLength || sourceDimension <arrayLength) return null;
	  PerspectiveTransformGeneral[] transformMatrix=new PerspectiveTransformGeneral[numOfBlockOnOneSide*numOfBlockOnOneSide+1];
	  for(int i=0,j=0;i<numOfBlockOnOneSide;){
		  int index=i*(numOfBlockOnOneSide+1)+j;
		  int sourceIndex=index<<1;
		  transformMatrix[i*(numOfBlockOnOneSide)+j]=
				  getTransform(sourcePoint[sourceIndex], sourcePoint[sourceIndex+1],
						  		sourcePoint[sourceIndex+2], sourcePoint[sourceIndex+3],
						  		sourcePoint[sourceIndex+(offset<<1)],sourcePoint[sourceIndex+(offset<<1)+1],
						  		sourcePoint[sourceIndex+(offset<<1)+2],sourcePoint[sourceIndex+(offset<<1)+3],
						  		imagePoint[index].getX(),imagePoint[index].getY(),
						  		imagePoint[index+1].getX(),imagePoint[index+1].getY(),
						  		imagePoint[index+offset].getX(),imagePoint[index+offset].getY(),
						  		imagePoint[index+offset+1].getX(),imagePoint[index+offset+1].getY()
						  	  );
		  j++;
		  if(j>=numOfBlockOnOneSide){
			  i++;
			  j=0;
		  }
	  }
	  //The last entry is the matrix for whole QR code. It takes the points on four corners of the QR code
	  int index=numOfBlockOnOneSide*numOfBlockOnOneSide;
	  transformMatrix[index]=
			  getTransform(sourcePoint[0], sourcePoint[1], //topLeft
				  		sourcePoint[numOfBlockOnOneSide<<1], sourcePoint[(numOfBlockOnOneSide<<1)+1], //topRight
				  		sourcePoint[(index+numOfBlockOnOneSide)<<1],sourcePoint[((index+numOfBlockOnOneSide)<<1)+1], //bottomLeft. index equals to n*(n+1)
				  		sourcePoint[(arrayLength<<1)-2],sourcePoint[(arrayLength<<1)-1], //bottomRight
				  		imagePoint[0].getX(),imagePoint[0].getY(),
				  		imagePoint[numOfBlockOnOneSide].getX(),imagePoint[numOfBlockOnOneSide].getY(),
				  		imagePoint[index+numOfBlockOnOneSide].getX(),imagePoint[index+numOfBlockOnOneSide].getY(),
				  		imagePoint[arrayLength-1].getX(),imagePoint[arrayLength-1].getY()
				  	  );
	  return new PerspectiveTransformGeneral(transformMatrix,sourceDimension/numOfBlockOnOneSide,sourceDimension/numOfBlockOnOneSide);
  }

  /**
   * Transform the points from target coordinate (u,v) to coordinates on original image (x,y) 
   * Assuming the points are sorted from left to right, from top to bottom 
   * @param points
   */
  public void transformPoints(float[] points) {
	if(this.isMultiple){
		transformPointsMul(points);
		return;
	}
    int max = points.length;
    double a11 = this.a11;
    double a12 = this.a12;
    double a13 = this.a13;
    double a21 = this.a21;
    double a22 = this.a22;
    double a23 = this.a23;
    double a31 = this.a31;
    double a32 = this.a32;
    double a33 = this.a33;
    
    int[] correction=getErrorCorrection(points);
    for (int i = 0; i < max; i += 2) {
      double x = points[i];
      double y = points[i + 1];
      //Here depends on setting in getTransform
      double denominator = a31 * x + a32 * y + a33;
      points[i] = (float) ((a11 * x + a12 * y + a13) / denominator);
      points[i + 1] =  (float) ((a21 * x + a22 * y + a23) / denominator);
    }
    if(correction !=null){
    	for(int i=0;i<max;i++) 
    		points[i] +=correction[i];
    }
    /*for(int i=0;i<max;i++){
    	points[i]=points[i]/scaleUpFactor;
	}*/
  }
  private void transformPointsMul(float[] points){
	if(!this.isMultiple){
		transformPoints(points);
		return;
	}
	//transformMatrix[4].transformPoints(points);	
	int max = points.length;
	int bound0=0,bound1=0,bound2=0,bound3=0;
	//TODO: It looks stupid. Any way to make it better? Also, it does not work when the points are not arranged from left to right, from top to bottom
	for (int i = 0; i < max; i += 2) {
	    if(points[i]<sourceBoundX && points[i+1]<sourceBoundY) bound0=i+2;
	    else if(points[i]>=sourceBoundX && points[i+1]<sourceBoundY) bound1=i+2;
	    else if(points[i]<sourceBoundX && points[i+1]>=sourceBoundY) bound2=i+2;
	    else if(points[i]>=sourceBoundX && points[i+1]>=sourceBoundY) bound3=i+2;
	}
	bound1=(bound1>=bound0)? bound1:bound0;
	bound2=(bound2>=bound1)? bound2:bound1;
	bound3=(bound3>=bound2)? bound3:bound2;
	if(bound0>0){
		float[] subPoints=Arrays.copyOfRange(points, 0, bound0);
		if(transformMatrix[0] !=null) transformMatrix[0].transformPoints(subPoints);
		else transformMatrix[4].transformPoints(subPoints);
		//transformMatrix[4].transformPoints(subPoints);
		for(int i=0;i<subPoints.length;i++)
			points[i]=subPoints[i];
	}
	if(bound1>bound0){
		float[] subPoints=Arrays.copyOfRange(points, bound0, bound1);
		if(transformMatrix[1] !=null) transformMatrix[1].transformPoints(subPoints);
		else transformMatrix[4].transformPoints(subPoints);
		//transformMatrix[4].transformPoints(subPoints);
		for(int i=0;i<subPoints.length;i++)
			points[bound0+i]=subPoints[i];
	}
	if(bound2>bound1){
		float[] subPoints=Arrays.copyOfRange(points, bound1, bound2);
		if(transformMatrix[2] !=null) transformMatrix[2].transformPoints(subPoints);
		else transformMatrix[4].transformPoints(subPoints);
		//transformMatrix[4].transformPoints(subPoints);
		for(int i=0;i<subPoints.length;i++)
			points[bound1+i]=subPoints[i];
	}
	if(bound3>bound2){
		float[] subPoints=Arrays.copyOfRange(points, bound2, bound3);
		if(transformMatrix[3] !=null) transformMatrix[3].transformPoints(subPoints);
		else transformMatrix[4].transformPoints(subPoints);
		//transformMatrix[4].transformPoints(subPoints);
		for(int i=0;i<subPoints.length;i++)
			points[bound2+i]=subPoints[i];
	}
  }

  /** Convenience method, not optimized for performance. */
  public void transformPoints(float[] xValues, float[] yValues) {
    if(this.isMultiple){
    	transformPointsMul(xValues,yValues);
		return;
	}
	int n = (xValues.length <= yValues.length)? xValues.length:yValues.length;
    for (int i = 0; i < n; i ++) {
      float x = xValues[i];
      float y = yValues[i];
      double denominator = a31 * x + a32 * y + a33;
      xValues[i] = (float) ((a11 * x + a12 * y + a13) / denominator);
      yValues[i] = (float) ((a21 * x + a22 * y + a23) / denominator);
    }
    /*for(int i=0;i<n;i++){
    	xValues[i]=xValues[i]/scaleUpFactor;
    	yValues[i]=yValues[i]/scaleUpFactor;
	}*/
  }
  private void transformPointsMul(float[] xValues, float[] yValues){
	if(!this.isMultiple){
		transformPoints(xValues,yValues);
		return;
	}
	transformMatrix[4].transformPoints(xValues, yValues);
  }


  public PerspectiveTransformGeneral times(PerspectiveTransformGeneral other) {
	    return new PerspectiveTransformGeneral(a11 * other.a11 + a12 * other.a21 + a13 * other.a31,
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
  public static double[] matrixMultiplication(double[] foreMatrix, double[] backMatrix, int dimension){
	  int length=dimension*dimension;
	  if(foreMatrix.length != length || backMatrix.length != length ) return null;
	  double[] resultM=new double[length];
	  for(int i=0;i<dimension;i++){
		  double[] row=Arrays.copyOfRange(foreMatrix, i*dimension, (i+1)*dimension);
		  for(int j=0;j<dimension;j++){
			  int index=i*dimension+j;
			  resultM[index]=0;
			  for(int k=0;k<dimension;k++)
				  resultM[index] += row[k]*backMatrix[k*dimension+j];
		  }
	  }
	  return resultM;
  }
  
  public static double[] transpose(double[] source, int dimension){
	  int length=dimension*dimension;
	  if(source.length != length) return null;
	  double[] resultM=new double[length];
	  for(int i=0;i<dimension;i++){
		  double[] row=Arrays.copyOfRange(source, i*dimension, (i+1)*dimension);
		  for(int j=0;j<dimension;j++)
			  resultM[j*dimension+i]=row[j];
	  }
	  return resultM;
  }
  private ArrayList<int[]> errorVector=new ArrayList<int[]>();
  private int largestY=-1;
  private int[] errCorrection=null;
  public void addErrorCorrection(float x, float y,int errVectorX,int errVectorY,int errRadius){
	  //Put y as main index as transform normally take points row by row
	  int[] e=new int[]{(int) ( ((y-errRadius) >0)? (y-errRadius):0 ), (int) (y+errRadius),(int) ( ((x-errRadius) >0)? (x-errRadius):0 ), (int) (x+errRadius),errVectorY,errVectorX}; 
	  boolean isInserted=false;
	  if(e[0]>largestY){
		  errorVector.add(e);
		  largestY=e[0];
		  isInserted=true;
	  }
	  if(!isInserted && !errorVector.isEmpty()){
		  ListIterator<int[]> it=errorVector.listIterator();
		  while(it.hasNext()){
			  int[] arr=it.next();
			  if(arr[0]>e[0] || (arr[0]==e[0] && arr[2]>=e[2]) ) {
				  errorVector.add(it.previousIndex(), e);
				  isInserted=true;
				  break;
			  }
		  }
	  }
	  if(!isInserted) { //The given element should have max y among entries in errorVector now
		  errorVector.add(e);
		  largestY=e[0];
	  }
  }
  public void fixErrorCorrection(){
	  if(errorVector.isEmpty()) {
		  errCorrection=null;
		  return;
	  }
	  int length=errorVector.size()*6;
	  errCorrection=new int[length];
	  ListIterator<int[]> it=errorVector.listIterator();
	  int i=0;
	  while(it.hasNext() && i<length){
		  int[] arr=it.next();
		  errCorrection[i]=arr[0];
		  errCorrection[i+1]=arr[1];
		  errCorrection[i+2]=arr[2];
		  errCorrection[i+3]=arr[3];
		  errCorrection[i+4]=arr[4];
		  errCorrection[i+5]=arr[5];
		  i+=6;
	  }
  }
  //TODO: Optimize this function by modifying this and addErrorCorrection()
  private int[] getErrorCorrection(float[] points){
	  if(errCorrection==null) return null;
	  int max=points.length;
	  int[] error=new int[max];
	  int previousJ=0,dj=6,length=errCorrection.length;
	  for (int i = 0; i < max; i += 2) {
	      double x = points[i];
	      double y = points[i + 1];
	      error[i+1]=0;
 		  error[i]=0;
	      dj=(errCorrection[previousJ]<y || (errCorrection[previousJ]==y && errCorrection[previousJ+2]<=x))? 6:-6;
	      for(int j=previousJ;(j>-1 && j<length);j+=dj){
	    	  if(errCorrection[j]<=y && errCorrection[j+1]>=y && 
	    		 errCorrection[j+2]<=x && errCorrection[j+3]>=x){
	    		 error[i+1]=errCorrection[j+4];
	    		 error[i]=errCorrection[j+5];
	    		 previousJ=j;
	    		 break;
	    	  }
	    	  if(errCorrection[j]>y && dj>0) break;
	      }
	      if(previousJ >=length) previousJ=0;
	  }
	  return error;
  }
  

}
