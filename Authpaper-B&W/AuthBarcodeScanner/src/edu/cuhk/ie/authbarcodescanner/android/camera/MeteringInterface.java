/*
 * Copyright (C) 2014 ZXing authors
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
 * Copyright (C) 2014 Solon in MobiTeC, CUHK
 * Add resetFocusArea to reset the focus area during decoding
 */
package edu.cuhk.ie.authbarcodescanner.android.camera;

import java.util.Collections;
import java.util.List;

import edu.cuhk.ie.authbarcodescanner.android.Log;
import android.graphics.Rect;
import android.hardware.Camera;

public final class MeteringInterface{

  private static final String TAG = MeteringInterface.class.getSimpleName();
  private static final int AREA_PER_1000 = 400;

  private MeteringInterface() {
  }

  public static boolean setFocusArea(Camera.Parameters parameters) {
	  try{
		  if(parameters.getMaxNumFocusAreas() > 0){
		      List<Camera.Area> middleArea = buildMiddleArea();
		      parameters.setFocusAreas(middleArea);
		      return true;
		  }
	  }catch(Exception e2){
		  Log.i(TAG, "Device does not support focus areas");
	  }
	  return false;
  }

  public static boolean setMetering(Camera.Parameters parameters) {
	try{
		if (parameters.getMaxNumMeteringAreas() > 0) {
		    List<Camera.Area> middleArea = buildMiddleArea();
		    parameters.setMeteringAreas(middleArea);
		    return true;
		}
	}catch(Exception e2){
		Log.i(TAG, "Device does not support metering areas");
	}
    return false;
  }
  /**
   * A static function to get the current focus area
   * @param camera
   * @return
   */
  public static Rect getFocusArea(Camera camera, int frameWidth, int frameHeight){
	  if(camera ==null || camera.getParameters().getMaxNumFocusAreas() <1
			  || frameHeight <100 || frameWidth <100) return null;
	  Camera.Parameters parameters = camera.getParameters();	  
	  List<Camera.Area> areas=parameters.getFocusAreas();
	  if(areas ==null || areas.size() <1) return null;
	  Rect newFocusRect=areas.get(0).rect;
	  return new Rect(toNorPoint(newFocusRect.left,frameWidth), toNorPoint(newFocusRect.top,frameHeight), 
			  toNorPoint(newFocusRect.right,frameWidth),toNorPoint(newFocusRect.bottom,frameHeight));	  
  }
  /**
   * A static function to change the focus area
   * @param camera
   * @param x
   * @param y
   * @param width
   * @param height
   * @return
   */
  public static boolean resetFocusArea(Camera camera, Rect focusRect, int frameWidth, int frameHeight){
	  if(camera ==null || camera.getParameters().getMaxNumFocusAreas() <1
			  || frameHeight <100 || frameWidth <100) return false;
	  Camera.Parameters parameters = camera.getParameters();
	  String focusMode=parameters.getFocusMode();
	  if(focusMode.compareToIgnoreCase(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)==0
			  || focusMode.compareToIgnoreCase(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)==0
			  || focusMode.compareToIgnoreCase(Camera.Parameters.FOCUS_MODE_EDOF)==0){
		  //Need to disable the continuous focusing to reset focus, set focus mode to marco
		  if(parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_MACRO)){
			  parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
		  }else parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
	  }
	  Rect newFocusRect=null;
	  if(focusRect ==null) newFocusRect=new Rect(-AREA_PER_1000, -AREA_PER_1000, AREA_PER_1000, AREA_PER_1000);
	  else{
		  //Change the focus area to center (0,0) and (-1000,-1000) at top left corner
		  newFocusRect=new Rect(toCamPoint(focusRect.left,frameWidth), toCamPoint(focusRect.top,frameHeight), 
				  toCamPoint(focusRect.right,frameWidth),toCamPoint(focusRect.bottom,frameHeight));
	  }
	  List<Camera.Area> focusArea=Collections.singletonList(new Camera.Area(newFocusRect,100));
	  parameters.setFocusAreas(focusArea);
	  if(parameters.getMaxNumMeteringAreas() > 0) parameters.setMeteringAreas(focusArea);
      camera.setParameters(parameters);
	  return true;
  }
  /**
   * Change a value from top left (0,0) to center (0,0) and (-1000,-1000) at top left corner
   * @param point
   * @param length
   * @return
   */
  private static int toCamPoint(int point, int length){ 
	  int tranferredpt = (point*2000)/length -1000;
	  return (tranferredpt >1000)? 1000 : (tranferredpt <-1000)? -1000:tranferredpt;
  }
  /**
   * Change a value from center (0,0) and top left (-1000,-1000) to top left (0,0) and center (length/2,length/2)
   * @param point
   * @param length
   * @return
   */
  private static int toNorPoint(int point, int length){
	  int tranferredpt=((point+1000)*length)/2000;
	  return (tranferredpt >length)? length : (tranferredpt <0)? 0:tranferredpt;
  }

  private static List<Camera.Area> buildMiddleArea() {
	// Here (0,0) is the center and (-1000,1000) is the top right hand corner
    return Collections.singletonList(
        new Camera.Area(new Rect(-AREA_PER_1000, -AREA_PER_1000, AREA_PER_1000, AREA_PER_1000), 1));
  }

}
