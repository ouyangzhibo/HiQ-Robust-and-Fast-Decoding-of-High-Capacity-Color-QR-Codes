/**
 * Copyright 2015 Solon Li 2015 from dapcode
 * 
 * Do not use without explicit approval from the copyright holder
 */
package com.google.zxing.qrcode.detector;

import com.google.zxing.ResultPoint;

/**
 * Representation of a refinded finder pattern, which is a special formatted square on the corners of a QR code (except bottom right).
 * The alignment of the pattern is represented by the slope of the pattern.
 * A square area packaging the pattern is also encapsulated to avoid duplicate detections
 * @author Solon Li
 */
public final class FinderPatternRestrictive{

  private final int centerX,centerY,top,left,bottom,right;
  private final float moduleSize, slope;

  FinderPatternRestrictive(int x, int y, int[] area, float moduleSize, float slope){
    this.centerX=x;
	this.centerY=y;
	this.top=area[0];
	this.left=area[1];
	this.bottom=area[2];
	this.right=area[3];
	this.moduleSize=moduleSize;
	this.slope=slope;	
  }
  public final int getX() {
    return centerX;
  }  
  public final int getY() {
    return centerY;
  }
  public final float getModuleSize(){
	return moduleSize;
  }
  public final float getSlope(){
	return slope;
  }
  public final int[] getCoveringSquare(){
	return new int[]{left,top,right,bottom};
  }
  public final int[] getBottomRight(){
	return new int[]{right,bottom};
  }
  public boolean isCovered(int[] square){
	if(square.length !=4 || square[0]<0 || square[1]<0 || square[2]<0 || square[3]<0)
		return false;
	if(this.left<=square[0] && this.right>=square[2] && this.top<=square[1] 
		&& this.bottom>=square[3]) return true;
	if(MathUtils.distance(this.left, this.top, square[0], square[1]) < 3*moduleSize
		&& MathUtils.distance(this.right, this.bottom, square[2], square[3]) < 3*moduleSize)
		return true;
  }
  /**
   * Combine the patterns if one of the input patterns covers another.
   * Return false if not
   */
  public static FinderPatternRestrictive combinePattern(FinderPatternRestrictive a, 
	FinderPatternRestrictive b){
	if(a.isCovered(b.getCoveringSquare()) || b.isCovered(a.getCoveringSquare())){
		//Use the bigger one
		int centerX=(a.left+a.right+b.left+b.right)>>2;
		int centerY=(a.top+a.bottom+b.top+b.bottom)>>2;
		int moduleSize=(a.getModuleSize()+b.getModuleSize())>>1;
		int slope=(a.getSlope()+b.getSlope())>>1;
		int[] square=(a.isCovered(b))? a.getCoveringSquare():b.getCoveringSquare();
		return new FinderPatternRestrictive(centerX,centerY,square,moduleSize,slope);
	}
	return false;
  }
  //Max difference in 5 degrees, 10 pixels
  private static float slopeLimit=0.06, moduleLimit=10, cosineLimit=0.01, blackWhiteLimit=8;
  
  /**
   * Check if input pattern triple can form an isosceles right triangle and they are connected 
   * by a timing pattern (i.e. if the triple should be the corners of one QR code)
   */
  public static boolean isFormingQRcode(FinderPatternRestrictive A, 
	FinderPatternRestrictive B, FinderPatternRestrictive C, BitMatrix image){
	if(!isSimilarSlope(A.getSlope(),B.getSlope(),C.getSlope())) return false;
	if(!isSimilarModule(A.getModuleSize(),B.getModuleSize(),C.getModuleSize())) return false;
	//At least 14 modules between center of finder patterns
	float minD=196*A.getModuleSize()*A.getModuleSize();
	int xA=A.getX(),xB=B.getX(),xC=C.getX();
	int yA=A.getY(),yB=B.getY(),yC=C.getY();
	
	int[] vAB=new int[]{xA-xB,yA-yB}, vBC=new int[]{xB-xC,yB-yC},vAC=new int[]{xA-xC,yA-yC};
	float dAB=(vAB[0]*vAB[0]) + (vAB[1]*vAB[1]),dBC=(vBC[0]*vBC[0]) + (vBC[1]*vBC[1])
	float dAC=(vAC[0]*vAC[0]) + (vAC[1]*vAC[1]);
	if(dAB < minD || dBC < minD || dAC < minD) return false;
	FinderPatternRestrictive topLeft=null,topRight=null,bottomLeft=null;
	//use vectors with smaller distances
	if(dAB<dAC){
		if(dBC<dAC){
			//AC longest
			float dotBCAB = (vBC[0]*vAB[0]) + (vBC[1]*vAB[1]);
			dotBCAB = dotBCAB*dotBCAB;
			//return false if they cannot form right angle
			if((dotBCAB/(dBC*dAB)) >cosineLimit) return false;
			topLeft=B;
			//use cross product to see which one is topRight, note that AB means vector B to A
			if( ((vBC[0]*vAB[1]) - (vBC[1]*vAB[0])) >0 ){
				topRight=A;
				bottomLeft=C;
			}else{
				topRight=C;
				bottomLeft=A;
			}
		}
		if(dBC>dAC){
			//BC longest
			float dotACAB = (vAC[0]*vAB[0]) + (vAC[1]*vAB[1]);
			dotACAB = dotACAB*dotACAB;
			//return false if they cannot form right angle
			if((dotACAB/(dAC*dAB)) >cosineLimit) return false;
			topLeft=A;
			//use cross product to see which one is topRight, note that AB means vector B to A
			if( ((vAC[0]*vAB[1]) - (vAC[1]*vAB[0])) >0 ){
				topRight=C;
				bottomLeft=B;
			}else{
				topRight=B;
				bottomLeft=C;
			}
		}
	}else{
		if(dBC<dAB){
			//AB longest
			float dotACBC = (vAC[0]*vBC[0]) + (vAC[1]*vBC[1]);
			dotACBC = dotACBC*dotACBC;
			//return false if they cannot form right angle
			if((dotACBC/(dAC*dBC)) >cosineLimit) return false;
			topLeft=C;
			//use cross product to see which one is topRight, note that AB means vector B to A
			if( ((vAC[0]*vBC[1]) - (vAC[1]*vBC[0])) >0 ){
				topRight=A;
				bottomLeft=B;
			}else{
				topRight=B;
				bottomLeft=A;
			}
		}
		if(dBC>dAB){
			//BC longest
			float dotACAB = (vAC[0]*vAB[0]) + (vAC[1]*vAB[1]);
			dotACAB = dotACAB*dotACAB;
			//return false if they cannot form right angle
			if((dotACAB/(dAC*dAB)) >cosineLimit) return false;
			topLeft=A;
			//use cross product to see which one is topRight, note that AB means vector B to A
			if( ((vAC[0]*vAB[1]) - (vAC[1]*vAB[0])) >0 ){
				topRight=C;
				bottomLeft=B;
			}else{
				topRight=B;
				bottomLeft=C;
			}
		}
	}
	if(topLeft ==null || topRight ==null || bottomLeft ==null) return false;
	//Although the lines connecting centers of finder patterns are not timing pattern
	//we can still count it to see if the regions between finder patterns are plain (out of QR code)
	int[] modules=countTimingPattern(image,topLeft,topRight);
	if(modules ==null || modules[0] <blackWhiteLimit) return false;
	modules=countTimingPattern(image,topLeft,bottomLeft);
	if(modules ==null || modules[0] <blackWhiteLimit) return false;
	//modules=countTimingPattern(image,topRight,bottomLeft);
	//if(modules ==null || modules[0] <blackWhiteLimit) return false;
	return true;
  }
  private static boolean isSimilarSlope(float slope1, float slope2, float slope3){
	return ( (slope1-slope2)<slopeLimit || (slope2-slope1)<slopeLimit )? 
		(( (slope3-slope2)<slopeLimit || (slope2-slope3)<slopeLimit )? true:false) : false;
  }
  private static boolean isSimilarModule(float slope1, float slope2, float slope3){
	return ( (slope1-slope2)<moduleLimit || (slope2-slope1)<moduleLimit )? 
		(( (slope3-slope2)<moduleLimit || (slope2-slope3)<moduleLimit )? true:false) : false;
  }
  /**
   * This method counts number of black and white modules between two points.
   * It begins in a black region, and keeps going until it finds white, then black, then white again.
   * 
   * This is used to scan the timing pattern between corner of two finder patterns and get the dimension
   * The line between points are created using Bresenham's algorithm with little modification
   * Reference: http://en.wikipedia.org/wiki/Bresenham's_line_algorithm    
   */
  private static int[] countTimingPattern(BitMatrix image, FinderPatternRestrictive startP,
	FinderPatternRestrictive endP){
	  //We use the code in simplification section of Bresenham's line algorithm Wiki page
	  int x0=(int) (startP.getX()+0.5f), y0=(int) (startP.getY()+0.5f);
	  int x1=(int) (endP.getX()+0.5f), y1=(int) (endP.getY()+0.5f);
	  int dx=(x1>x0)? x1-x0:x0-x1, dy=(y1>y0)? y1-y0:y0-y1;
	  int sx=(x1>x0)? 1:(x1<x0)? -1:0, sy=(y1>y0)? 1:(y1<y0)? -1:0;
	  int error=dx-dy;
	  int counter=0;
	  
	  boolean isCountBlack=(image.get(x0,y0));
	  int moduleCount=(image.get(x0,y0))? 0:1;
	  while(x0 !=x1 || y0!=y1){
		  if(isCountBlack ^ image.get(x0, y0)){
			  moduleCount++;
			  isCountBlack=image.get(x0, y0);
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

}