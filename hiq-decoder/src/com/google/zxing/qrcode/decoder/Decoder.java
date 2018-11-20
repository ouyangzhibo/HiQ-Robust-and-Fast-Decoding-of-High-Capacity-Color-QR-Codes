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

package com.google.zxing.qrcode.decoder;

import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.google.zxing.common.reedsolomon.ReedSolomonException;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * <p>The main class which implements QR Code decoding -- as opposed to locating and extracting
 * the QR Code from an image.</p>
 *
 * @author Sean Owen
 * @author Solon Li Add some message content on each exception, and add feature for error corrections on whole data 
 */
public final class Decoder {

  private final ReedSolomonDecoder rsDecoder;
  private com.google.zxing.LogCallback logCallback;

  public Decoder() {
    rsDecoder = new ReedSolomonDecoder(GenericGF.QR_CODE_FIELD_256);
  }

  public DecoderResult decode(boolean[][] image) throws ChecksumException, FormatException {
    return decode(image, null);
  }

  /**
   * <p>Convenience method that can decode a QR Code represented as a 2D array of booleans.
   * "true" is taken to mean a black module.</p>
   *
   * @param image booleans representing white/black QR Code modules
   * @return text and bytes encoded within the QR Code
   * @throws FormatException if the QR Code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  public DecoderResult decode(boolean[][] image, Map<DecodeHintType,?> hints)
      throws ChecksumException, FormatException {
    int dimension = image.length;
    BitMatrix bits = new BitMatrix(dimension);
    for (int i = 0; i < dimension; i++) {
      for (int j = 0; j < dimension; j++) {
        if (image[i][j]) {
          bits.set(j, i);
        }
      }
    }
    return decode(bits, hints, true);
  }

  public DecoderResult decode(BitMatrix bits) throws ChecksumException, FormatException {
    return decode(bits, null, true);
  }

  /**
   * <p>Decodes a QR Code represented as a {@link BitMatrix}. A 1 or "true" is taken to mean a black module.</p>
   * If a QR code is partially decoded, a Result object with null text, rawByte, ecLevel but non-null data blocks will be returned.
   * @param bits booleans representing white/black QR Code modules
   * @return text and bytes encoded within the QR Code
   * @throws FormatException if the QR Code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  public DecoderResult decode(BitMatrix bits, Map<DecodeHintType,?> hints)
	      throws FormatException, ChecksumException {
	 return this.decode(bits, hints, true); 
  }
  public DecoderResult decode(BitMatrix bits, Map<DecodeHintType,?> hints, boolean isCumulateBlock)
	  throws FormatException, ChecksumException {
	  return this.decode(bits, hints, isCumulateBlock, null);
  }
  public DecoderResult decode(BitMatrix bits, Map<DecodeHintType,?> hints, DataBlock[] datablock)
	  throws FormatException, ChecksumException {
	  return this.decode(bits, hints, true, datablock);
  }
  public DecoderResult decode(BitMatrix bits, Map<DecodeHintType,?> hints, boolean isCumulateBlock, 
		  DataBlock[] datablock) throws FormatException, ChecksumException {
	  
	logCallback = (hints ==null || !hints.containsKey(DecodeHintType.NEED_LOG_CALLBACK))? 
				null:(com.google.zxing.LogCallback) hints.get(DecodeHintType.NEED_LOG_CALLBACK);	
    // Construct a parser and read version, error-correction level
    BitMatrixParser parser = new BitMatrixParser(bits);//when parser fails, maybe is because of wrong version info.
    FormatException fe = null;
    ChecksumException ce = null;
    if(logCallback !=null) logCallback.LogMsg("Decode Function, Finish parsing to bitmatrix, start error corrections",false,2);
    try {
      DecoderResult result = decode(parser, hints, isCumulateBlock, datablock);
      if(logCallback !=null) logCallback.LogMsg("Decode Function, Finish error corrections, Returning",true,2);
      return result;
    }catch(FormatException e){
    	fe=e;
    }catch(ChecksumException e) {
      ce = e;
    }
    if(logCallback !=null) logCallback.LogMsg("Decode Function, Error corrections failed, Reverting the bitmatrix",true,2);
    try{
   // Revert the bit matrix
      parser.remask();
      // Will be attempting a mirrored reading of the version and format info.
      parser.setMirror(true);
      // Preemptively read the version.
      parser.readVersion();
      // Preemptively read the format information.
      parser.readFormatInformation();
      /*
       * Since we're here, this means we have successfully detected some kind
       * of version and format information when mirrored. This is a good sign,
       * that the QR code may be mirrored, and we should try once more with a
       * mirrored content.
       */
      // Prepare for a mirrored reading.
      parser.mirror();
      DecoderResult result = decode(parser, hints, isCumulateBlock, datablock);
      // Success! Notify the caller that the code was mirrored.
      if(result !=null) result.setOther(new QRCodeDecoderMetaData(true));
      if(logCallback !=null) logCallback.LogMsg("Decode Function, Finish error corrections again",true,2);
      return result;
    } catch (FormatException | ChecksumException e) {
      if(logCallback !=null) logCallback.LogMsg("Decode Function, Failed reverting and error correction again",true,2);
      // Throw the exception from the original reading
      if(fe !=null) throw fe;
      if(ce != null) throw ce;
      throw e;
    }
  }
  private DecoderResult decode(BitMatrixParser parser, Map<DecodeHintType,?> hints, boolean isCumulateBlock, 
		  DataBlock[] backupblocks) throws FormatException, ChecksumException {
	  if(logCallback !=null) logCallback.LogMsg("Error Correction Function start",false,3);
	  // Read codewords
	  boolean randomizeFlag = (hints !=null && hints.containsKey(DecodeHintType.Shuffled_Codeword))?
			  (Boolean) hints.get(DecodeHintType.Shuffled_Codeword) : false;
	  byte[] codewords = parser.readCodewords();
	  Version version = parser.readVersion();
	  ErrorCorrectionLevel ecLevel = parser.readFormatInformation().getErrorCorrectionLevel();
	  if(randomizeFlag){		  
		  codewords=com.google.zxing.qrcode.decoder.BlockShifting.groupingToByte(codewords,version,ecLevel);
		  if(logCallback !=null) logCallback.LogMsg("Error Correction Function, Finish resuffling bits",true,3);
	  }
	  try{
		  DecoderResult result=decode(codewords,version,ecLevel,hints,isCumulateBlock,backupblocks);
		  if(randomizeFlag && result !=null) result.setErasures(1);
		  return result;
	  }catch(ChecksumException e2){}
	  return null;
  }

  private DecoderResult decode(byte[] codewords, Version version, ErrorCorrectionLevel ecLevel, Map<DecodeHintType,?> hints, 
		  boolean isCumulateBlock, DataBlock[] backupblocks) throws FormatException, ChecksumException {
    // Separate into data blocks	
    DataBlock[] dataBlocks = DataBlock.getDataBlocks(codewords, version, ecLevel);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();	
	if(isCumulateBlock){
		if(backupblocks == null && hints.containsKey(DecodeHintType.Need_Successful_DataBlocks)) 
			backupblocks=(DataBlock[]) hints.get(DecodeHintType.Need_Successful_DataBlocks);//to get the data blocks in the hints
		if(backupblocks == null) backupblocks=new DataBlock[dataBlocks.length];
		//backupblocks=new DataBlock[dataBlocks.length];
	}
    // Error-correct and copy data blocks together into a stream of bytes
	int successBlockCount=0;
	if(logCallback !=null) logCallback.LogMsg("Error Correction Function, Start ECC correcting",true,3);
    for(int counterB=0, Count=dataBlocks.length;counterB<Count;counterB++){
    	DataBlock dataBlock = dataBlocks[counterB];
    	byte[] codewordBytes = dataBlock.getCodewords();
		int numDataCodewords = dataBlock.getNumDataCodewords();
		try{
    	  correctErrors(codewordBytes, numDataCodewords);
    	  if(isCumulateBlock && backupblocks !=null && counterB <backupblocks.length)    		  
    		  backupblocks[counterB] = new DataBlock(numDataCodewords,codewordBytes);
		}catch(ChecksumException rse){
			if(isCumulateBlock && backupblocks !=null && counterB <backupblocks.length 
					&& backupblocks[counterB] !=null){
				codewordBytes = backupblocks[counterB].getCodewords();
				numDataCodewords = backupblocks[counterB].getNumDataCodewords();
			}else continue;
		}		
		successBlockCount++;
		baos.write(codewordBytes, 0, numDataCodewords);   
    }
    if(logCallback !=null) logCallback.LogMsg("Error Correction Function, Finish ECC correcting, number of blocks:"+dataBlocks.length
    		+" , number of success "+successBlockCount,true,3);
	//only if all data blocks fail, throw rse.        
    if(successBlockCount <dataBlocks.length){
    	if(!isCumulateBlock || successBlockCount<1 || backupblocks ==null){
    		ChecksumException rse=ChecksumException.getChecksumInstance();
      		rse.setErrorMessage("No data block is correct or recorded.");
      		throw rse;
    	} 		    		
		//if the data blocks are not fully backed up
		return new DecoderResult(null,null,null,null,backupblocks);
    }
	
    //Decode the contents of that stream of bytes
	try{
		//return DecodedBitStreamParser.decode(resultBytes, version, ecLevel, hints);
		return DecodedBitStreamParser.decode(baos.toByteArray(), version, ecLevel, hints);
	}catch(FormatException e2){
    	FormatException error=FormatException.getFormatInstance();
      	error.setErrorMessage((e2.getErrorMessage()!="")?e2.getErrorMessage():"Something wrong in parsing byte to result.");
        throw error;
    }
  }


  /**
   * <p>Given data and error-correction codewords received, possibly corrupted by errors, attempts to
   * correct the errors in-place using Reed-Solomon error correction.</p>
   *
   * @param codewordBytes data and error correction codewords
   * @param numDataCodewords number of codewords that are data bytes
   * @throws ChecksumException if error correction fails
   */
  public void correctErrors(byte[] codewordBytes, int numDataCodewords) throws ChecksumException {
    int numCodewords = codewordBytes.length;
    // First read into an array of ints
    int[] codewordsInts = new int[numCodewords];
    for (int i = 0; i < numCodewords; i++) {
      codewordsInts[i] = codewordBytes[i] & 0xFF;
    }
    int numECCodewords = codewordBytes.length - numDataCodewords;
    try {
      rsDecoder.decode(codewordsInts, numECCodewords);
    } catch (ReedSolomonException ignored) {
      throw ChecksumException.getChecksumInstance();
    }
    // Copy back into array of bytes -- only need to worry about the bytes that were data
    // We don't care about errors in the error-correction codewords    
    for (int i = 0; i < numDataCodewords; i++) {
      codewordBytes[i] = (byte) codewordsInts[i];
    }
  }
  
  /**
   * Given an BitMatrix, check its dimension and whether we can correctly decode it.
   * If we can correctly decode the QR code, return 0;
   * changed by elky: even if we can correctly decode the QR code, we still return the correct dimension instead of 0,
   * since I will use new method to correct the transformation matrix then (only use in high dimension QR code)
   * If not, return the correct dimension or -1 if we cannot get the correct dimension 
   */
  public static int checkDimension(BitMatrix bits){
	  BitMatrixParser parser=null;
	  try{
		  parser = new BitMatrixParser(bits);
	  }catch(FormatException e){}
	  if(parser ==null) return -1;
	  try{
		  int decodedDimension=parser.readVersion().getDimensionForVersion();
		  parser.readFormatInformation();
		  return decodedDimension;		  
	  }catch(FormatException e1){ 
		  try{
	    	parser.remask();
	    	parser.setMirror(true);
	    	int decodedDimension=parser.readVersion().getDimensionForVersion();
	    	parser.readFormatInformation();
	    	return decodedDimension;	    	
		  }catch(FormatException e2){ 
	    	return -1;
		  }
	 } 
  }
}
