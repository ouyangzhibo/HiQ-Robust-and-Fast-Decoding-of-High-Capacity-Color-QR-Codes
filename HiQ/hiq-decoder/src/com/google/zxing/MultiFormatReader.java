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

import com.google.zxing.aztec.AztecReader;
import com.google.zxing.datamatrix.DataMatrixReader;
import com.google.zxing.maxicode.MaxiCodeReader;
import com.google.zxing.oned.MultiFormatOneDReader;
import com.google.zxing.pdf417.PDF417Reader;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * MultiFormatReader is a convenience class and the main entry point into the library for most uses.
 * By default it attempts to decode all barcode formats that the library supports. Optionally, you
 * can provide a hints object to request different behavior, for example only decoding QR codes.
 *
 * @author Sean Owen
 * @author dswitkin@google.com (Daniel Switkin)
 * 
 * Modified by Solon
 * throw exception as an indicator of the decode stage
 */
public final class MultiFormatReader implements Reader {
	
	private static final Map<BarcodeFormat, Reader> formatTo2DReaderMapping 
	= new HashMap<BarcodeFormat, Reader>(){
	//private static final long serialVersionUID = 5493248573551591352L;
	{
		put(BarcodeFormat.QR_CODE, new QRCodeReader());
		put(BarcodeFormat.DATA_MATRIX, new DataMatrixReader());
		put(BarcodeFormat.AZTEC, new AztecReader());
		put(BarcodeFormat.PDF_417, new PDF417Reader());
		put(BarcodeFormat.MAXICODE, new MaxiCodeReader());
	}};
	private static final BarcodeFormat[] oneDformats = 
		{BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.EAN_13,
		BarcodeFormat.EAN_8, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93,
		BarcodeFormat.CODE_128, BarcodeFormat.ITF, BarcodeFormat.RSS_14,
		BarcodeFormat.RSS_EXPANDED};
		

  private Map<DecodeHintType,?> hints;
  private Reader[] readers;

  /**
   * This version of decode honors the intent of Reader.decode(BinaryBitmap) in that it
   * passes null as a hint to the decoders. However, that makes it inefficient to call repeatedly.
   * Use setHints() followed by decodeWithState() for continuous scan applications.
   *
   * @param image The pixel data to decode
   * @return The contents of the image
   * @throws NotFoundException Any errors which occurred
   */
  @Override
  public Result decode(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
    setHints(null);
    return decodeInternal(image);
  }

  /**
   * Decode an image using the hints provided. Does not honor existing state.
   *
   * @param image The pixel data to decode
   * @param hints The hints to use, clearing the previous state.
   * @return The contents of the image
   * @throws NotFoundException Any errors which occurred
   */
  @Override
  public Result decode(BinaryBitmap image, Map<DecodeHintType,?> hints) throws NotFoundException, ChecksumException, FormatException {
    setHints(hints);
    return decodeInternal(image);
  }

  /**
   * Decode an image using the state set up by calling setHints() previously. Continuous scan
   * clients will get a <b>large</b> speed increase by using this instead of decode().
   *
   * @param image The pixel data to decode
   * @return The contents of the image
   * @throws NotFoundException Any errors which occurred
   */
  public Result decodeWithState(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
    // Make sure to set up the default state so we don't crash
    if (readers == null) setHints(null);
    return decodeInternal(image);
  }

  /**
   * This method adds state to the MultiFormatReader. By setting the hints once, subsequent calls
   * to decodeWithState(image) can reuse the same set of readers without reallocating memory. This
   * is important for performance in continuous scan clients.
   *
   * @param hints The set of hints to use for subsequent calls to decode(image)
   */
  public void setHints(Map<DecodeHintType,?> hints) {
    this.hints = hints;

    boolean tryHarder = hints != null && hints.containsKey(DecodeHintType.TRY_HARDER);
    @SuppressWarnings("unchecked")
	Collection<BarcodeFormat> formats =
        (hints == null)? null :         	
        	(Collection<BarcodeFormat>) hints.get(DecodeHintType.POSSIBLE_FORMATS);
    Collection<Reader> readers = new ArrayList<Reader>();
    if (formats != null) {
      boolean addOneDReader = false;
      if(formats.size()>1){
    	  for(int i=0;i<oneDformats.length && !addOneDReader;i++){
        	  if(formats.contains(oneDformats[i])) addOneDReader=true;
          }  
      }
      
      // Put 1D readers upfront in "normal" mode
      if (addOneDReader && !tryHarder) {
        readers.add(new MultiFormatOneDReader(hints));
      }
      for(Map.Entry<BarcodeFormat, Reader> entry : formatTo2DReaderMapping.entrySet()) {
    	  if(formats.contains(entry.getKey())) readers.add(entry.getValue());
      }
      // At end in "try harder" mode
      if (addOneDReader && tryHarder) {
        readers.add(new MultiFormatOneDReader(hints));
      }
    }
    if (readers.isEmpty()) readers.add(new QRCodeReader());
    this.readers = readers.toArray(new Reader[readers.size()]);
  }

  @Override
  public void reset() {
    if (readers != null) {
      for (Reader reader : readers) {
        reader.reset();
      }
    }
  }

  private Result decodeInternal(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
	int state=0;
	//Class<? extends Reader> successReaderType=null;
	Reader successReaderType=null;
	//TODO: make the readers return interested points even if they cannot decode it
	if (readers != null) {
      for (Reader reader : readers) {
        try {
          return reader.decode(image, hints);
        } catch(NotFoundException re){
        	//We cannot confirm such barcode is present by NotFoundException
        } catch(FormatException re){
        	if(state <=1){
        		state=2;
        		//successReaderType=reader.getClass();
        		successReaderType=reader;        		
        	}
        }catch(ChecksumException re){
        	if(state <=2){
        		state=3;
        		//successReaderType=reader.getClass();
        		successReaderType=reader;        		        		
        	}				
        }catch(Exception e2){
        	//Some strange exceptions throw, just continue         	
        }
      }    
    }
	if(state>1 && successReaderType!=null){
		//means can be detected but can not be decoded(format error or all data blocks fail or some other errors).
		Class<? extends Reader> type=successReaderType.getClass();
		for(Map.Entry<BarcodeFormat, Reader> entry : formatTo2DReaderMapping.entrySet()) {
			if(entry.getValue().getClass() == type) 
				return new Result(""+state,null,null,entry.getKey(),null);
	    }
	}
	return null;
  }
  
  public Result decodeQRcode(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
	  return new QRCodeReader().decode(image, hints);
  }

}
