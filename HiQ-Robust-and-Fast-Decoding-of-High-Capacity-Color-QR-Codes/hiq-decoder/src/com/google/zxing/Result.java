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

package com.google.zxing;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.pdf417.PDF417ResultMetadata;
import com.google.zxing.qrcode.decoder.DataBlock;

/**
 * <p>Encapsulates the result of decoding a barcode within an image.</p>
 *
 * @author Sean Owen
 * Updated by Solon in Nov 2014, divide resultMetadata into three different map to avoid object casting
 * Hence making the porting to C++ possible.
 */
public final class Result {

  private final String text;
  private final byte[] rawBytes;
  private ResultPoint[] resultPoints;
  private final BarcodeFormat format;
  
  //private Map<ResultMetadataType, Object> resultMetadata;
  private Map<ResultMetadataType, Integer> resultIntMetadata;
  private Map<ResultMetadataType, String> resultStringMetadata;
  private Map<ResultMetadataType, List<byte[]>> resultByteMetadata;  
  private PDF417ResultMetadata pdf417ResultMetadata=null;
  private final long timestamp;
  private final BitMatrix undecodedMatrix;
  //add by elky, to record the successful data blocks 
  private DataBlock[] datablocks;

  public Result(String text,
          		byte[] rawBytes,
          		ResultPoint[] resultPoints,
          		BarcodeFormat format) {
	  this(text, rawBytes, resultPoints, format, System.currentTimeMillis(),null);
  }
  public Result(String text,
                byte[] rawBytes,
                ResultPoint[] resultPoints,
                BarcodeFormat format,
				DataBlock[] datablocks) {
    this(text, rawBytes, resultPoints, format, System.currentTimeMillis(),datablocks);
  }

  public Result(String text,
                byte[] rawBytes,
                ResultPoint[] resultPoints,
                BarcodeFormat format,
                long timestamp,
				DataBlock[] datablocks) {
    this.text = text;
    this.rawBytes = rawBytes;
    this.resultPoints = resultPoints;
    this.format = format;
    //this.resultMetadata = null;
    this.resultIntMetadata=null;
    this.resultStringMetadata=null;
    this.resultByteMetadata=null;
    this.timestamp = timestamp;
    this.undecodedMatrix=null;
	//add by elky
    this.datablocks = datablocks;
  }
  
  public Result(String text,
          byte[] rawBytes,
          ResultPoint[] resultPoints,
          BarcodeFormat format,
          long timestamp,
          BitMatrix bits,
		  DataBlock[] datablocks) {
	this.text = text;
	this.rawBytes = rawBytes;
	this.resultPoints = resultPoints;
	this.format = format;
	//this.resultMetadata = null;
	this.resultIntMetadata=null;
    this.resultStringMetadata=null;
    this.resultByteMetadata=null;
	this.timestamp = timestamp;
	this.undecodedMatrix=bits;
	//add by elky
    this.datablocks = datablocks;
  }
  
  /**
   * @return raw text encoded by the barcode
   */
  public String getText() {
    return text;
  }

  /**
   * @return raw bytes encoded by the barcode, if applicable, otherwise {@code null}
   */
  public byte[] getRawBytes() {
    return rawBytes;
  }

  /**
   * @return points related to the barcode in the image. These are typically points
   *         identifying finder patterns or the corners of the barcode. The exact meaning is
   *         specific to the type of barcode that was decoded.
   */
  public ResultPoint[] getResultPoints() {
    return resultPoints;
  }

  /**
   * @return successful corrected data blocks.
   */
  public DataBlock[] getDataBlocks() {
	  return datablocks;
  }
  
  /**
   * @return {@link BarcodeFormat} representing the format of the barcode that was decoded
   */
  public BarcodeFormat getBarcodeFormat() {
    return format;
  }

  /**
   * @return {@link Map} mapping {@link ResultMetadataType} keys to values. May be
   *   {@code null}. This contains optional metadata about what was detected about the barcode,
   *   like orientation.
   */
  /*public Map<ResultMetadataType,Object> getResultMetadata() {
    return resultMetadata;
  }*/    
  /*public void putMetadata(ResultMetadataType type, Object value) {
    if (resultMetadata == null) {
      resultMetadata = new EnumMap<ResultMetadataType,Object>(ResultMetadataType.class);
    }
    resultMetadata.put(type, value);
  }*/
  /*public void putAllMetadata(Map<ResultMetadataType,Object> metadata) {	
	  if (metadata != null) {
	    if (resultMetadata == null) {
	      resultMetadata = metadata;
	    } else {
	      resultMetadata.putAll(metadata);
	    }
	  }
	}*/
  public void putMetadata(ResultMetadataType type, int value) {
	  if(resultIntMetadata == null)
		  resultIntMetadata = new EnumMap<ResultMetadataType,Integer>(ResultMetadataType.class);
	  try{
		  resultIntMetadata.put(type, value);  
	  }catch(Exception e2){ }
  }
  /**
   * get the integer value of the specified metadata, return -100 if the metadata is not found 
   * @param type
   * @return
   */
  public int getIntMetadata(ResultMetadataType type){
	  return (type ==null || resultIntMetadata == null || !resultIntMetadata.containsKey(type))? 
			  -100 : resultIntMetadata.get(type);
  }
  public void putMetadata(ResultMetadataType type, String value) {
	  if(resultStringMetadata == null)
		  resultStringMetadata = new EnumMap<ResultMetadataType,String>(ResultMetadataType.class);
	  try{
		  resultStringMetadata.put(type, value);
	  }catch(Exception e2){ }
  }
  public String getStringMetadata(ResultMetadataType type){
	  return (resultStringMetadata == null)? null : resultStringMetadata.get(type);
  }
  public void putMetadata(ResultMetadataType type, List<byte[]> value) {
	  if(resultByteMetadata == null)
		  resultByteMetadata = new EnumMap<ResultMetadataType,List<byte[]>>(ResultMetadataType.class);
	  try{
		  resultByteMetadata.put(type, value);
	  }catch(Exception e2){ }
  }
  public List<byte[]> getByteMetadata(ResultMetadataType type){
	  return (resultByteMetadata == null)? null : resultByteMetadata.get(type);
  }
  
  public void putMetadata(PDF417ResultMetadata data){
	  if(data ==null) return;
	  pdf417ResultMetadata=data;
  }
  public PDF417ResultMetadata getPDF417Metadata(){
	  return pdf417ResultMetadata;
  }  
  
  /**
   * Move all metadata in this object to the target object
   * @param target
   */
  public void copyMetadata(Result target){
	  if(resultIntMetadata != null) target.putAllIntMetadata(resultIntMetadata);
	  if(resultStringMetadata != null) target.putAllStringMetadata(resultStringMetadata);
	  if(resultByteMetadata != null) target.putAllByteMetadata(resultByteMetadata);
	  if(pdf417ResultMetadata !=null) target.putMetadata(pdf417ResultMetadata);
  }
  public void copyMetadata(Map<ResultMetadataType, Integer> intTarget, 
		  Map<ResultMetadataType, String> strTarget, 
		  Map<ResultMetadataType, List<byte[]>> byteTarget,
		  PDF417ResultMetadata pdfTarget){
	  if(intTarget !=null && resultIntMetadata != null) intTarget.putAll(resultIntMetadata);
	  if(strTarget !=null && resultStringMetadata != null) strTarget.putAll(resultStringMetadata);
	  if(byteTarget !=null && resultByteMetadata != null) byteTarget.putAll(resultByteMetadata);
	  if(pdfTarget !=null && pdf417ResultMetadata !=null) pdf417ResultMetadata.cloneTo(pdfTarget);
		  //pdfTarget=pdf417ResultMetadata;
	  
  }
  public void putAllIntMetadata(Map<ResultMetadataType,Integer> source){
	  if(source ==null || source.isEmpty()) return;
	  if(resultIntMetadata == null)
		  resultIntMetadata = new EnumMap<ResultMetadataType,Integer>(ResultMetadataType.class);
	  try{
		  resultIntMetadata.putAll(source);
	  }catch(Exception e2){ }
  }
  public void putAllStringMetadata(Map<ResultMetadataType,String> source){
	  if(source ==null || source.isEmpty()) return;
	  if(resultStringMetadata == null)
		  resultStringMetadata = new EnumMap<ResultMetadataType,String>(ResultMetadataType.class);
	  try{
		  resultStringMetadata.putAll(source);
	  }catch(Exception e2){ }
  }
  public void putAllByteMetadata(Map<ResultMetadataType,List<byte[]>> source){
	  if(source ==null || source.isEmpty()) return;
	  if(resultByteMetadata == null)
		  resultByteMetadata = new EnumMap<ResultMetadataType,List<byte[]>>(ResultMetadataType.class);
	  try{
		  resultByteMetadata.putAll(source);
	  }catch(Exception e2){ }
  }

  public void addResultPoints(ResultPoint[] newPoints) {
    ResultPoint[] oldPoints = resultPoints;
    if (oldPoints == null) {
      resultPoints = newPoints;
    } else if (newPoints != null && newPoints.length > 0) {
      ResultPoint[] allPoints = new ResultPoint[oldPoints.length + newPoints.length];
      System.arraycopy(oldPoints, 0, allPoints, 0, oldPoints.length);
      System.arraycopy(newPoints, 0, allPoints, oldPoints.length, newPoints.length);
      resultPoints = allPoints;
    }
  }

  public long getTimestamp() {
    return timestamp;
  }
  
  public BitMatrix getundecodedMatrix(){
	  return undecodedMatrix;
  }

  @Override
  public String toString() {
    return text;
  }

}
