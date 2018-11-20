/*
 * Copyright 2008 ZXing authors
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

import com.google.zxing.aztec.AztecWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.oned.CodaBarWriter;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.Code39Writer;
import com.google.zxing.oned.EAN13Writer;
import com.google.zxing.oned.EAN8Writer;
import com.google.zxing.oned.ITFWriter;
import com.google.zxing.oned.UPCAWriter;
import com.google.zxing.pdf417.PDF417Writer;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Map;

/**
 * This is a factory class which finds the appropriate Writer subclass for the BarcodeFormat
 * requested and encodes the barcode with the supplied contents.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
/**
 * Modified by Solon in 2012 to support binary data input to create barcode
 * @author Solon Li
 */
public final class MultiFormatWriter implements Writer {

  @Override
  public BitMatrix encode(String contents,
                          BarcodeFormat format,
                          int width,
                          int height) throws WriterException {
    return encode(contents, format, width, height, null);
  }

  @Override
  public BitMatrix encode(String contents,
                          BarcodeFormat format,
                          int width, int height,
                          Map<EncodeHintType,?> hints) throws WriterException {

    Writer writer;
    switch (format) {
      case EAN_8:
        writer = new EAN8Writer();
        break;
      case EAN_13:
        writer = new EAN13Writer();
        break;
      case UPC_A:
        writer = new UPCAWriter();
        break;
      case QR_CODE:
        writer = new QRCodeWriter();
        break;
      case CODE_39:
        writer = new Code39Writer();
        break;
      case CODE_128:
        writer = new Code128Writer();
        break;
      case ITF:
        writer = new ITFWriter();
        break;
      case PDF_417:
        writer = new PDF417Writer();
        break;
      case CODABAR:
	      writer = new CodaBarWriter();
        break;
	  case DATA_MATRIX:
        writer = new DataMatrixWriter();
        break;
      case AZTEC:
        writer = new AztecWriter();
        break;
      default:
        throw new IllegalArgumentException("No encoder available for format " + format);
    }
    return writer.encode(contents, format, width, height, hints);
  }
  
  public BitMatrix encode(byte[] array,
		  				  String fileType,
                          BarcodeFormat format,
                          int width,
                          int height) throws WriterException {
    return encode(array, fileType, format, width, height, null);
  }

  //TODO: extend this feature to other barcode formats and make it as a default method in Writer Class.
  /**
   * Please notice that this function only works on QR code only for the time being. from Solon Li, 2012 
   * @param array: the data array of the file
   * @param format: the format of the barcode, currently only BarcodeFormat.QR_CODE is accepted
   * @param width: width of the barcode
   * @param height: height of the barcode
   * @param hints: configuration on the barcode
   * @return
   * @throws WriterException
   */
  public BitMatrix encode(byte[] array,
		  				  String fileType,
                          BarcodeFormat format,
                          int width, int height,
                          Map<EncodeHintType,?> hints) throws WriterException {
    //Writer writer;
	if(!format.equals(BarcodeFormat.QR_CODE)) throw new IllegalArgumentException("No encoder available for format " + format);
	QRCodeWriter writer= new QRCodeWriter();
    return writer.encode(array, fileType, format, width, height, hints);
  }
  

}
