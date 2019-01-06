/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.camera;

import java.util.ArrayList;
import java.util.List;

import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;

import edu.cuhk.ie.authbarcodescanner.android.R;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class CameraOverlay extends View implements ResultPointCallback{
	private static final String TAG=CameraOverlay.class.getSimpleName();
	
	//Static variables
	private static final long ANIMATION_DELAY = 100L;
	private static final int SHOW_LIMIT = 3;
	private static final int CURRENT_POINT_OPACITY = 0xA0;
  	private static final int MAX_POINTS = 4;
  	private static final int MAX_LINES = 5;
  	private static final int POINT_SIZE = 8;
  	private static final float FOCUS_AREA_SIZE=0.2f;//In ratio

  	private CameraManager cameraManager;
  	private final Paint paint, linePaint;
  	private Rect drawingRect=null;
  	private float scaleX=0, scaleY=0;
  	private int canvasWidth=0, canvasHeight=0;
  	private Point lastFrameCenter=null;
  	private final int maskColor, resultPointColor, resultAreaColor;
  	private int pointShowCount=0, lineShowCount=0,areaShowCount=0;
  	private List<ResultPoint> resultPoints;
  	private List<ResultPoint> lastResultPoints;
  	private List<ResultPoint[]> resultLines;
  	private Drawable focusIcon=null;

	// This constructor is used when the class is built from an XML resource.
	public CameraOverlay(Context context, AttributeSet attrs) {
		super(context, attrs);
		// Initialize the paint config instead of onDraw()
		
		//maskColor = resources.getColor(R.color.viewfinder_mask);
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		linePaint = new Paint(paint);
		linePaint.setStyle(Paint.Style.STROKE);
		linePaint.setAlpha(CURRENT_POINT_OPACITY);
		linePaint.setStrokeWidth(POINT_SIZE);
		Resources resources = getResources();
		maskColor=resources.getColor(R.color.overlay_mask);
		resultPointColor=resources.getColor(R.color.overlay_points);		
		resultAreaColor=resources.getColor(R.color.overlay_frame);
		
		resultPoints = new ArrayList<ResultPoint>(MAX_POINTS+1);
		lastResultPoints = null;
		resultLines = new ArrayList<ResultPoint[]>(MAX_LINES+1);
		focusIcon = context.getResources().getDrawable(R.drawable.view_focus_vector);
		this.setOnTouchListener(new onTouchListener());
	}
	@Override
	public void foundPossibleResultPoint(ResultPoint arg0) {
		List<ResultPoint> points = resultPoints;
		synchronized (points) {
			Point lastFrameC=getlastFrameCenter();
			if(lastFrameC !=null) arg0=rotate90Clock(arg0,lastFrameC);
			points.add(arg0);
			int size = points.size();
			if (size > MAX_POINTS) {
				// trim it
				points.subList(0, size - MAX_POINTS / 2).clear();
			}
			pointShowCount=0;
		}
	}
	@Override
	public void findCodeBoundLine(ResultPoint arg0, ResultPoint arg1) {
		List<ResultPoint[]> lines = resultLines;
	    synchronized (lines) {
	    	Point lastFrameC=getlastFrameCenter();
			if(lastFrameC !=null) {
				arg0=rotate90Clock(arg0,lastFrameC);
				arg1=rotate90Clock(arg1,lastFrameC);
			}
	    	lines.add(new ResultPoint[]{arg0, arg1});
	    	int size = lines.size();
	    	if (size > MAX_LINES) {
	    		lines.subList(0, size - MAX_LINES / 2).clear();
	    	}
	    	lineShowCount=0;
	    }
	}
	@Override
	public void findCodePresent(ResultPoint arg0, ResultPoint arg1,
			ResultPoint arg2, ResultPoint arg3) {

	}
	
	public void setCameraManager(CameraManager cameraManager) {
	    this.cameraManager = cameraManager;
	}
	
	public void startDrawing(){
		invalidate();
	}
	/*public synchronized boolean isCodeDetected(){
		return !resultLines.isEmpty();
	}*/
	
	public void onDraw(Canvas canvas) {
		if(cameraManager ==null) return; // Configuration of camera is not ready yet
		int width = canvas.getWidth();
	    int height = canvas.getHeight();
		calulateDrawingRect(width,height);
		//Mark the undecode area
		Rect frame=this.drawingRect;
		if(frame==null) return;
	    paint.setAlpha(CURRENT_POINT_OPACITY);
	    paint.setColor(maskColor);
	    canvas.drawRect(0, 0, width, frame.top, paint);
	    canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
	    canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
	    canvas.drawRect(0, frame.bottom + 1, width, height, paint);
	    int frameLeft = frame.left;
	    int frameTop = frame.top;
	    
	    //Draw the focus area on the scanning area
	    Rect focusArea=cameraManager.getFocusArea();
        if(focusArea !=null && focusArea.left>0 && focusArea.top>0 && focusArea.right>0 && focusArea.bottom>0){
        	int focusLeft=(int) (frameLeft + focusArea.left*scaleX);
        	int focusTop=(int) (frameTop + focusArea.top*scaleY);
        	int focusRight=(int) (frameLeft + focusArea.right*scaleX);
        	int focusBottom=(int) (frameTop + focusArea.bottom*scaleY);
        	focusIcon.setBounds(focusLeft, focusTop, focusRight, focusBottom);
        	focusIcon.draw(canvas);
        } 
        	    
	    //Draw result points on the scanning area
	    List<ResultPoint> currentPossible = resultPoints;
	    List<ResultPoint> currentLast = lastResultPoints;
	    if (!currentPossible.isEmpty()) {
	    	//resultPoints = new ArrayList<ResultPoint>(5);
	    	lastResultPoints = currentPossible;
	        paint.setAlpha(CURRENT_POINT_OPACITY/4);
	        paint.setColor(resultPointColor);
	        synchronized (currentPossible) {
	          for (ResultPoint point : currentPossible) {
	            canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
	                              frameTop + (int) (point.getY() * scaleY),
	                              POINT_SIZE, paint);
	          }
	        }
	        pointShowCount++;
	        if(pointShowCount >SHOW_LIMIT) resultPoints.clear();
	      }
	      if (currentLast != null) {
	        synchronized (currentLast) {
	          float radius = POINT_SIZE / 2.0f;
	          for (ResultPoint point : currentLast) {
	            canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
	                              frameTop + (int) (point.getY() * scaleY),
	                              radius, paint);
	          }
	        }
	      }
	      
	    //Draw result lines on the scanning area
	      List<ResultPoint[]> currentLine = resultLines;
	      if(!currentLine.isEmpty()){
	    	  //linePaint.setColor(resultLineColor);
	          synchronized (currentLine) {
	              /*for (ResultPoint[] point : currentLine) {
	            	if(point ==null || point.length !=2 || point[0]==null || point[1]==null) continue;
	                canvas.drawLine(frameLeft + (int) (point[0].getX()*scaleX), frameTop + (int) (point[0].getY()*scaleY), 
	                				frameLeft + (int) (point[1].getX()*scaleX), frameTop + (int) (point[1].getY()*scaleY),
	                				linePaint);
	              }*/
	              
	        	  paint.setAlpha(CURRENT_POINT_OPACITY);
	  	          paint.setColor(resultAreaColor);
	        	  //Draw a cover on all lines instead
	        	  float lineLeft=0,lineRight=0,lineTop=0,lineBottom=0;
	        	  for (ResultPoint[] point : currentLine) {
		            	if(point ==null || point.length !=2 || point[0] ==null || point[1] ==null) continue;
		            	float p0X=point[0].getX(),p1X=point[1].getX();
		            	float p0Y=point[0].getY(),p1Y=point[1].getY();
		            	lineLeft = (lineLeft > 0 && lineLeft < p0X && lineLeft < p1X)? lineLeft
		            				: (p0X < p1X)? p0X : p1X ;
		            	lineRight = (lineRight > 0 && lineRight > p0X && lineRight > p1X)? lineRight
	            				: (p0X > p1X)? p0X : p1X ;
		            	lineTop = (lineTop > 0 && lineTop < p0Y && lineTop < p1Y)? lineTop
	            				: (p0Y < p1Y)? p0Y : p1Y;
		            	lineBottom = (lineBottom > 0 && lineBottom > p0Y && lineBottom > p1Y)? lineBottom
	            				: (p0Y > p1Y)? p0Y : p1Y;
		          }
    			  canvas.drawRect(frameLeft+(int) (lineLeft*scaleX) - POINT_SIZE
    					  , frameTop+(int) (lineTop*scaleY) - POINT_SIZE
    					  , frameLeft+(int) (lineRight*scaleX) + POINT_SIZE
    					  , frameTop+(int) (lineBottom*scaleY) + POINT_SIZE
    					  ,paint);
	          }
	          lineShowCount++;
	          if(lineShowCount >SHOW_LIMIT) currentLine.clear();
	      }	    
	      //call this method again after ANIMATION_DELAY
	      postInvalidateDelayed(ANIMATION_DELAY,
                  frame.left - POINT_SIZE,
                  frame.top - POINT_SIZE,
                  frame.right + POINT_SIZE,
                  frame.bottom + POINT_SIZE);
	}
	
	private void calulateDrawingRect(int width, int height){
		if(canvasWidth==width && canvasHeight==height 
				&& this.drawingRect !=null && this.scaleX >0 && this.scaleY >0) return;
		Point camResolution = cameraManager.getCameraResolution();
	    Rect previewFrame = cameraManager.getFramingRectInPreview();    
	    if(camResolution ==null || previewFrame ==null) return;
	    canvasWidth=width;
	    canvasHeight=height;
	    lastFrameCenter=new Point(previewFrame.centerX(), previewFrame.centerY());
	    //float scaleX = this.getWidth() / (float) previewFrame.width();
	    //float scaleY = this.getHeight() / (float) previewFrame.height();
	    this.scaleX = width / (float) camResolution.x;
	    this.scaleY = height / (float) camResolution.y;
	    this.drawingRect=new Rect((int) (previewFrame.left*scaleX), (int) (previewFrame.top*scaleY), 
	    		(int) (previewFrame.right*scaleX), (int) (previewFrame.bottom*scaleY) );
	}
	/**
	 * Get the width and height of the preview frame
	 */
	private synchronized Point getlastFrameCenter(){
		if(lastFrameCenter !=null) return lastFrameCenter;
		else{
			Rect previewFrame = cameraManager.getFramingRectInPreview();
			if(previewFrame ==null) return null;
			lastFrameCenter=new Point(previewFrame.centerX(), previewFrame.centerY());
			return lastFrameCenter;
		}
	}
	/**
	 * Rotate the point clockwisely by 90 degrees. It is due to the fact that result points from zxing library will rotate it by 90 degree anti-clockwisely 
	 */
	public static ResultPoint rotate90Clock(ResultPoint p, Point lastFrameCenter){
		int centerY=lastFrameCenter.y, centerX=lastFrameCenter.x;
		float xFromCenter = (p.getX() - centerX) / (centerX);
		float yFromCenter = (centerY - p.getY()) / (centerY);
		if(android.os.Build.MODEL.contains("Nexus 5X")){
			//Hard code fix for Nexus 5X, rotate 90 anti-clockwise --> (x,y) to (-y, x)
			float tmp=xFromCenter;
			xFromCenter=-yFromCenter;
			yFromCenter=tmp;			
		}else{
			//Rotate 90 clockwise --> (x,y) to (y, -x)
			float tmp=xFromCenter;
			xFromCenter=yFromCenter;
			yFromCenter=-tmp;			
		}
		return new ResultPoint( xFromCenter*centerX + centerX ,  centerY-centerY*yFromCenter );
	}
	
	private class onTouchListener implements View.OnTouchListener{		
		@Override
		public boolean onTouch(View v, MotionEvent event) {			
			Rect focusRect = calculateFocusAreaonCam(event.getX(), event.getY());	
			cameraManager.changeFocusArea(focusRect);
			return false;
		}
		private Rect calculateFocusAreaonCam(float x, float y){
			float sX=(scaleX>0)? scaleX:1, sY=(scaleY>0)? scaleY:1;
			float rectWidthRadius = (FOCUS_AREA_SIZE*canvasWidth)/(2*sX);
			float rectHeightRadius = (FOCUS_AREA_SIZE*canvasHeight)/(2*sY);
			x = x/sX;y=y/sY;
			float scaledCanvaWidth=canvasWidth/sX, scaledCanvaHeight=canvasHeight/sY;
			int left = (x>rectWidthRadius)? (int) (x-rectWidthRadius):0;
			int top = (y>rectHeightRadius)? (int) (y-rectHeightRadius):0;
			int right = (int) ( (x+rectWidthRadius <scaledCanvaWidth)? x+rectWidthRadius : scaledCanvaWidth );
			int bottom = (int) ( (y+rectHeightRadius <scaledCanvaHeight)?  y+rectHeightRadius : scaledCanvaHeight );
		    return new Rect(left, top, right, bottom);
		}
	}
}