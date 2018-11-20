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

import com.google.zxing.ResultPoint;
import com.google.zxing.qrcode.detector.AlignmentPattern;
import com.google.zxing.qrcode.detector.FinderPattern;

/**
 * <p>Encapsulates the result of detecting a barcode in an image. This includes the raw
 * matrix of black/white pixels corresponding to the barcode, and possibly points of interest
 * in the image, like the location of finder patterns or corners of the barcode in the image.</p>
 *
 * @author Sean Owen
 */
public class DetectorResult {

  private final BitMatrix bits;
  private final ResultPoint[] points;
  private FinderPattern[] patterns;// modified by ZBYANG
  private float moduleSize; // modified by ZBYANG
  private AlignmentPattern[] alignmentPatterns;
  private PerspectiveTransformGeneral transform;
  private BitMatrix[] channelBits;
  private final boolean isEnlargedQRcode;//flag is used to distinguish the new and old version alignment pattern
  private float[][] samplePoints = null;
  
  public float[][] getSamplePoints() {
	return samplePoints;
  }

  public void setSamplePoints(float[][] samplePoints) {
	this.samplePoints = samplePoints;
  }

public DetectorResult(BitMatrix bits, ResultPoint[] points) {
    this.bits = bits;
    this.points = points;
    this.moduleSize =0;
    this.isEnlargedQRcode = false;
  }
  
  public DetectorResult(BitMatrix bits, 
		  				ResultPoint[] points, 
		  				FinderPattern[] patterns,
		  				AlignmentPattern[] alignmentPatterns,
		  				float moduleSize,
		  				PerspectiveTransformGeneral transform) {
    this.bits = bits;
    this.points = points;
    this.patterns = patterns;
    this.moduleSize = moduleSize;
    this.alignmentPatterns = alignmentPatterns;
    this.transform = transform;
	this.isEnlargedQRcode = false;
  }
  
  public DetectorResult(BitMatrix bits, ResultPoint[] points,float moduleSize,boolean flag) {
    this.bits = bits;	
    this.points = points;
    this.moduleSize = moduleSize;
    this.isEnlargedQRcode = flag;
  }
  
  public final BitMatrix getBits() {
    return bits;
  }
  
  public final ResultPoint[] getPoints() {
    return points;
  }
  
  public FinderPattern[] getPatterns() {
		return patterns;
  }
  
  public AlignmentPattern[] getAlignmentPatterns() {
		return alignmentPatterns;
  }
  
  public float getModuleSize() {
	  return moduleSize;
  }
  public PerspectiveTransformGeneral getTransform() {
	return transform;
  }
  
  public BitMatrix getChannelBits(int channel) {
	  return channelBits!=null?channelBits[channel]:null;
  }
  public BitMatrix[] getChannelBits() {
	  return channelBits;
  }
  public void setChannelBits(BitMatrix[] channelBits) {
	  this.channelBits = channelBits;
  }
  public final boolean isEnlargedQRcode(){
	  return isEnlargedQRcode;
  }
}