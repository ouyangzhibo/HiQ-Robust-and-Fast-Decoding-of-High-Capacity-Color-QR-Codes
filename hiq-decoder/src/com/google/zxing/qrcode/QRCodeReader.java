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

package com.google.zxing.qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LogCallback;
import com.google.zxing.NotFoundException;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.qrcode.decoder.DataBlock;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.decoder.QRCodeDecoderMetaData;
import com.google.zxing.qrcode.detector.Detector;
import com.google.zxing.qrcode.detector.DetectorLargeAlign;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * This implementation can detect and decode QR Codes in an image.
 *
 * @author Sean Owen
 * @author Solon Li Add new method extractDetectResult and modified some old code
 */
public class QRCodeReader implements Reader {

  private static final ResultPoint[] NO_POINTS = new ResultPoint[0];

  private final Decoder decoder = new Decoder();
  
  private ResultPointCallback resultPointCallback;
  private LogCallback logCallback;

  protected final Decoder getDecoder() {
    return decoder;
  }

  /**
   * Locates and decodes a QR code in an image.
   *
   * @return a String representing the content encoded by the QR code
   * @throws NotFoundException if a QR code cannot be found
   * @throws FormatException if a QR code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  @Override
  public Result decode(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
    return decode(image, null);
  }

  @Override
  public final Result decode(BinaryBitmap image, Map<DecodeHintType,?> hints)
      throws NotFoundException, ChecksumException, FormatException {
	resultPointCallback = (hints ==null || !hints.containsKey(DecodeHintType.NEED_RESULT_POINT_CALLBACK))? 
			null:(ResultPointCallback) hints.get(DecodeHintType.NEED_RESULT_POINT_CALLBACK);
	if(resultPointCallback !=null) resultPointCallback.findCodePresent(null, null, null, null);
	logCallback = (hints ==null || !hints.containsKey(DecodeHintType.NEED_LOG_CALLBACK))? 
			null:(LogCallback) hints.get(DecodeHintType.NEED_LOG_CALLBACK);
    DecoderResult decoderResult=null;
    ResultPoint[] points=null;
    BitMatrix rawBits=null;
    boolean isLargeAlign=false;
    //String errorString="";    
    if(logCallback !=null) logCallback.LogMsg("Start decoding",false);
    if(hints != null && hints.containsKey(DecodeHintType.PURE_BARCODE)) {      
      BitMatrix bits = extractPureBits(image.getBlackMatrix());
      if(logCallback !=null) logCallback.LogMsg("Finish extracting, start decoding pure bits",true);
      decoderResult = decoder.decode(bits, hints);
      if(logCallback !=null) logCallback.LogMsg("Finish decoding pure bits",true);
      points = NO_POINTS;
    }else{
    	//Try the normal detection method first, if it fails, try the new method    	
    	boolean isDecodeEnlargedQR = (hints !=null && hints.containsKey(DecodeHintType.OnlyScanEnlarged_OR_normal))?
    			(Boolean) hints.get(DecodeHintType.OnlyScanEnlarged_OR_normal) : true;
    	boolean isDecodeNormalQR = (hints !=null && hints.containsKey(DecodeHintType.OnlyScanEnlarged_OR_normal))?
    			!isDecodeEnlargedQR : true;
    	DetectorResult detectorResult = (isDecodeNormalQR)? extractDetectResult(image, hints, logCallback) : null;
    	if(logCallback !=null) logCallback.LogMsg("Finish extracting detect result ",true);
    	if(detectorResult !=null){
	    	try{
				decoderResult = decoder.decode(detectorResult.getBits(), hints);
				if(logCallback !=null) logCallback.LogMsg("Finish decoding detect result ",true);
			}catch(FormatException | ChecksumException e2){ }
    	}
    	//The new method
  		if(decoderResult ==null || decoderResult.getRawBytes() ==null){
  			//Input the data blocks recovered using the normal method to the new method
  			if(decoderResult !=null && decoderResult.getDataBlocks() != null){  				
  				DataBlock[] newBlocks=decoderResult.getDataBlocks();
  				DataBlock[] backupblocks = (DataBlock[]) hints.get(DecodeHintType.Need_Successful_DataBlocks);
  	  			if(backupblocks != null && newBlocks.length == backupblocks.length){
  	  				//Merge two blocks
  	  				for(int i=0,l=backupblocks.length;i<l;i++){
  	  					if(backupblocks[i] ==null && newBlocks[i] !=null) backupblocks[i]=newBlocks[i];
  	  				}
  	  			}else if(backupblocks ==null) backupblocks=newBlocks;
  	  			Map<DecodeHintType, Object> newHints = new EnumMap<DecodeHintType,Object>(DecodeHintType.class);
  	  			newHints.putAll(hints);
  	  			newHints.put(DecodeHintType.Need_Successful_DataBlocks,backupblocks);
  	  			hints = newHints;
  	  			if(logCallback !=null) logCallback.LogMsg("Finish packaging the datablock for next scanning",true);
  			}else if(logCallback !=null) logCallback.LogMsg("No datablock decoded for next scanning",true);
  			//If the old method can recover part of the QR code, then trust that it can detect QR codes with correct finder patterns
  			if(isDecodeEnlargedQR){  				
  				DetectorResult detectorResultNew = new DetectorLargeAlign(image.getBlackMatrix()).detect(hints);
  				if(logCallback !=null) logCallback.LogMsg("Finish detecting enlarged alignments on the QR code.",true);
  				//Re-detect using the correct dimension value
  				detectorResultNew = (detectorResultNew ==null)? null :
  						new DetectorLargeAlign(image.getBlackMatrix())
	  		  			.detect(detectorResultNew,Decoder.checkDimension(detectorResultNew.getBits()));
  				if(logCallback !=null) logCallback.LogMsg("Finish redetecting using correct dimension.",true);
	  	  	  	if(detectorResultNew !=null){
	  	  	  		detectorResult = detectorResultNew;
	  	  	  		try{
	  	  	  			decoderResult = decoder.decode(detectorResultNew.getBits(), hints);
	  	  	  			isLargeAlign=true;	  	  	  			
	  	  	  		}catch(FormatException | ChecksumException e2){ }
	  	  	  	}
	  	  	  	if(logCallback !=null) logCallback.LogMsg("Finish decoding the QR code again using the new result.",true);
	  		}
  		}
  	  	points = detectorResult.getPoints();
  	  	rawBits = detectorResult.getBits().clone();
  	  	if(resultPointCallback !=null){
  	  		resultPointCallback.findCodePresent(points[0], points[1], points[2], (points.length >=4 && points[3] !=null)? points[3]:null);
  	  	}
  	  	 
    }    
    //If nothing is detected, throw not found exception
    if(decoderResult ==null && points ==null && rawBits ==null) throw NotFoundException.getNotFoundInstance();
    //If a QR code is detected but all data blocks failed, which maybe because the misdetection or the wrong transformation
    if(decoderResult==null)
    	return new Result("", null, points, BarcodeFormat.QR_CODE, 0, rawBits,null);
    //If a QR code is decoded and some data blocks are found, set the back up data blocks in result
	if(decoderResult.getRawBytes() == null && decoderResult.getDataBlocks() != null)
    	return new Result("", null, points, BarcodeFormat.QR_CODE, 0, rawBits,decoderResult.getDataBlocks()); 	
	//decoded successfully, the result is the full result
	// If the code was mirrored: swap the bottom-left and the top-right points.
    if (decoderResult.getOther() instanceof QRCodeDecoderMetaData) {
      ((QRCodeDecoderMetaData) decoderResult.getOther()).applyMirroredCorrection(points);
    }
    Result result = new Result(decoderResult.getText(), decoderResult.getRawBytes(), points, BarcodeFormat.QR_CODE, 
    							System.currentTimeMillis(), rawBits,decoderResult.getDataBlocks());
    List<byte[]> byteSegments = decoderResult.getByteSegments();
    if(byteSegments != null) result.putMetadata(ResultMetadataType.BYTE_SEGMENTS, byteSegments);
    String ecLevel = decoderResult.getECLevel();
    if (ecLevel != null)
      result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, ecLevel); 
	if(decoderResult.hasStructuredAppend()) {
      result.putMetadata(ResultMetadataType.STRUCTURED_APPEND_SEQUENCE,
                         decoderResult.getStructuredAppendSequenceNumber());
      result.putMetadata(ResultMetadataType.STRUCTURED_APPEND_PARITY,
                         decoderResult.getStructuredAppendParity());
    }
	if(isLargeAlign) result.putMetadata(ResultMetadataType.Enlarged_Alignment, "true");
	Integer erasure=decoderResult.getErasures();
	if(erasure !=null && erasure.intValue() ==1) result.putMetadata(ResultMetadataType.Shuffled_Codeword, "true");
    return result;
  }

  @Override
  public void reset() {
    // do nothing
  }
  /**
   * Only perform the detection part of the decode process and return the BitMatrix
   * @param image
   * @param hints
   * @return
   */
  public static DetectorResult extractDetectResult(BinaryBitmap image, Map<DecodeHintType,?> hints, LogCallback logCallback)
		  throws NotFoundException, ChecksumException, FormatException {
	  if(logCallback !=null) logCallback.LogMsg("Extract Detect Result. Start getting black matrix",false,1);
	  BitMatrix matrix=image.getBlackMatrix();
	  if(logCallback !=null) logCallback.LogMsg("Extract Detect Result. Finish getting black matrix, Start detection",true,1);
	  DetectorResult detectorResult = new Detector(matrix).detect(hints);
	  if(logCallback !=null) logCallback.LogMsg("Extract Detect Result. Finish detection",true,1);
      if(detectorResult==null){
    	  FormatException error=FormatException.getFormatInstance();
    	    error.setErrorMessage("Nothing detected. But no exception thrown.");
    	    throw error;
      }
      //Check the dimension, if it is wrong. use the correct one to build detector result again      
      int correctDimension=Decoder.checkDimension(detectorResult.getBits());      
      if(correctDimension >0){
    	  DetectorResult detectorResultNew = new Detector(image.getBlackMatrix()).detect(detectorResult,correctDimension);
    	  detectorResult=(detectorResultNew !=null)? detectorResultNew:detectorResult;
      } //TODO:Rescan if it returns -1
      if(logCallback !=null) logCallback.LogMsg("Extract Detect Result. Finish redetection using correct dimension",true,1);
      return detectorResult;    
  }

  /**
   * This method detects a code in a "pure" image -- that is, pure monochrome image
   * which contains only an unrotated, unskewed, image of a code, with some white border
   * around it. This is a specialized method that works exceptionally fast in this special
   * case.
   *
   * @see com.google.zxing.datamatrix.DataMatrixReader#extractPureBits(BitMatrix)
   */
  public static BitMatrix extractPureBits(BitMatrix image) throws NotFoundException {

    int[] leftTopBlack = image.getTopLeftOnBit();
    int[] rightBottomBlack = image.getBottomRightOnBit();
    if (leftTopBlack == null || rightBottomBlack == null) {
      throw NotFoundException.getNotFoundInstance();
    }

    float moduleSize = moduleSize(leftTopBlack, image);
    moduleSize=(moduleSize>0)? moduleSize:-moduleSize;

    int top = leftTopBlack[1];
    int bottom = rightBottomBlack[1];
    int left = leftTopBlack[0];
    int right = rightBottomBlack[0];

   // Sanity check!
    if (left >= right || top >= bottom) {
      throw NotFoundException.getNotFoundInstance();
    }
    if (bottom - top != right - left) {
      // Special case, where bottom-right module wasn't black so we found something else in the last row
      // Assume it's a square, so use height as the width
      right = left + (bottom - top);
    }

    int matrixWidth = Math.round((right - left + 1) / moduleSize);
    int matrixHeight = Math.round((bottom - top + 1) / moduleSize);
    if (matrixWidth <= 0 || matrixHeight <= 0) {
      throw NotFoundException.getNotFoundInstance();
    }
    if (matrixHeight != matrixWidth) {
      // Only possibly decode square regions
      throw NotFoundException.getNotFoundInstance();
    }

    // Push in the "border" by half the module width so that we start
    // sampling in the middle of the module. Just in case the image is a
    // little off, this will help recover.
    int nudge = Math.round(moduleSize / 2.0f);
    top += nudge;
    left += nudge;

	// But careful that this does not sample off the edge
    int nudgedTooFarRight = left + (int) ((matrixWidth - 1) * moduleSize) - (right - 1);
    if (nudgedTooFarRight > 0) {
      if (nudgedTooFarRight > nudge) {
        // Neither way fits; abort
        throw NotFoundException.getNotFoundInstance();
      }
      left -= nudgedTooFarRight;
    }
    int nudgedTooFarDown = top + (int) ((matrixHeight - 1) * moduleSize) - (bottom - 1);
    if (nudgedTooFarDown > 0) {
      if (nudgedTooFarDown > nudge) {
        // Neither way fits; abort
        throw NotFoundException.getNotFoundInstance();
      }
      top -= nudgedTooFarDown;
    }
	
    // Now just read off the bits
    int imgWidth=image.getWidth(), imgHeight=image.getHeight();
    BitMatrix bits = new BitMatrix(matrixWidth, matrixHeight);
    for (int y = 0; y < matrixHeight; y++) {
      int iOffset = top + (int) (y * moduleSize);
      for (int x = 0; x < matrixWidth; x++) {
    	int offset=left + (int) (x * moduleSize);
        if (offset<imgWidth && iOffset<imgHeight && image.get(offset, iOffset)) {
          bits.set(x, y);
        }
      }
    }
    return bits;
  }

  private static float moduleSize(int[] leftTopBlack, BitMatrix image) throws NotFoundException {
    int height = image.getHeight();
    int width = image.getWidth();
    int x = leftTopBlack[0];
    int y = leftTopBlack[1];
    boolean inBlack = true;
    int transitions = 0;
    while (x < width && y < height) {
      if (inBlack != image.get(x, y)) {
        if (++transitions == 5) {
          break;
        }
        inBlack = !inBlack;
      }
      x++;
      y++;
    }
    if (x == width || y == height) {
      throw NotFoundException.getNotFoundInstance();
    }
    return (x - leftTopBlack[0]) / 7.0f;
  }

}
