/**
 * 
 * Copyright (C) 2014 Solon in CUHK
 *
 *
 */

package edu.cuhk.ie.authbarcode;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

import com.google.zxing.EncodeHintType;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.fileTypeECI;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.EncoderLargerAlign;
import com.google.zxing.qrcode.encoder.QRCode;

/**
 * This class does the work of encoding the data into a QR barcode.
 * The code is based on the QRCodeEncoder class in the ZXing library
 *
 * @author Solon 2014
 * 
 */
public final class QRCodeCreator {
  private static final String TAG = QRCodeCreator.class.getSimpleName();
  private static final int WHITE = 0xFFFFFFFF;
  private static final int BLACK = 0xFF000000;
  /*
   * We avoid the usage of QR code above version 30, 
   * so we use error correction L if the content is above 1,370 bytes, M otherwise.
   * In other cases, we use error correction M to increase the successful scanning probability 
   * If it is larger than 2,953 bytes, the data capacity of QR code, we report an error.
   */
  private static final int dataCapacityM=1370;
  private static final int dataCapacity=2953;
  private static final String defaultEncoding="UTF-8";
    
  private final byte[] content; //If not binary data is present, it will save the byte array of message (i.e. this object must not null)
  private final String message;
  private final int dimension;
  private final String fileType;
  private final boolean isPlainTextOnly;
  private ErrorCorrectionLevel correctLevel=null;
  public boolean isLargeAlign=false,isShuffle=false; 
  //Information about the BitMatrix
  private int bitDimension=0;
  
  private int codeContentSize=0;
  private BufferedImage createdBarcode=null;
  
  public QRCodeCreator(byte[] inputData, int imageDimension){
	  message=null;
	  isPlainTextOnly=false;
	  if(inputData==null || inputData.length<1) {
		  content=null;
		  dimension=0;
		  fileType=null;
		  return;
	  }
	  content=inputData;
	  dimension=(imageDimension>=177)? imageDimension:177;
	  //fileType="multipart/auth2dbarcode";
	  fileType=fileTypeECI.auth2dbarcode.name();
  }
  
  public QRCodeCreator(String inputData, int imageDimension){
	  isPlainTextOnly=true;
	  fileType=null;
	  if(inputData==null || inputData.isEmpty()) {
		  content=null;
		  message=null;
		  dimension=0;
		  return;
	  }
	  content=inputData.getBytes();
	  message=inputData;
	  dimension=(imageDimension>=177)? imageDimension:177;
	  //fileType=CharacterSetECI.UTF8.name();
  }
  
  public QRCodeCreator(String inputData, byte[] inputByte, int imageDimension){
	  isPlainTextOnly=false;
	  if(inputData==null || inputData.isEmpty() || inputByte==null || inputByte.length<1) {
		  content=null;
		  message=null;
		  dimension=0;
		  fileType=null;
		  return;
	  }
	  content=inputByte;
	  message=inputData;
	  dimension=(imageDimension>=177)? imageDimension:177;
	  fileType=fileTypeECI.auth2dbarcode.name();
  }
  
  public void setECLevel(ErrorCorrectionLevel errorCorrectionLevel){
	  this.correctLevel=errorCorrectionLevel;
  }
  public ErrorCorrectionLevel getECLevel(){
	  return this.correctLevel;
  }
 
  private BitMatrix encodeBitMatrix() throws Exception{
	  codeContentSize=0;
	  if(content==null) throw new Exception("No data is inserted to create QR code.");
	  if( (message ==null || message.isEmpty()) && isPlainTextOnly) throw new Exception("No text data is inserted to create QR code.");
	  if(content.length > dataCapacity) throw new Exception("Input data is more than data capacity of QR code");
	  if(this.correctLevel ==null){
		  if(content.length > dataCapacityM) this.correctLevel = ErrorCorrectionLevel.L;
		  else this.correctLevel = ErrorCorrectionLevel.M;  
	  }
/*	  QRCodeWriter writer = new QRCodeWriter();
	  Map<EncodeHintType,Object> hints = new EnumMap<EncodeHintType,Object>(EncodeHintType.class);
      hints.put(EncodeHintType.ERROR_CORRECTION, correctLevel);
      //For text mode, include the text encoding 
      if(message!=null && !message.isEmpty()) hints.put(EncodeHintType.CHARACTER_SET, defaultEncoding);
      BitMatrix matrix=(message==null || message.isEmpty())?
    		  writer.encode(content, fileType, format, dimension, dimension, hints):
    		  writer.encode(message, format, dimension, dimension, hints);
      codeContentSize=matrix.getHeight()*matrix.getWidth();
      return matrix;
*/
      //Four cases: message==null and isPlainTextOnly==true ==> error
      //message ==null and isPlainTextOnly==false ==> only binary data
      //message !=null and isPlainTextOnly==true ==> only plain text
      //message !=null and isPlainTextOnly==false ==> contains both binary data and string  
      QRCode code = null;
      Map<EncodeHintType,Object> hints = new EnumMap<EncodeHintType,Object>(EncodeHintType.class);
      hints.put(EncodeHintType.MARGIN, 0);
      if(isLargeAlign) hints.put(EncodeHintType.QR_LargerAlign, true);
      if(isShuffle) hints.put(EncodeHintType.QR_ShuffleCodeword, "true");
      if(message == null || message.isEmpty())  
    	  code = Encoder.encode(content, fileType, this.correctLevel, hints);
      else{
    	  hints.put(EncodeHintType.CHARACTER_SET, defaultEncoding);
    	  if(isPlainTextOnly) 
    		  code=(isLargeAlign)? EncoderLargerAlign.encode(message, this.correctLevel, hints)
    				  : Encoder.encode(message, this.correctLevel, hints);
    	  //else return null;
    	  //TODO: Problem: currently scanners assume there is only one data in the QR code. 
    	  //If we want to create QR code with mixing mode of content, why not save them all into BSON format in the first place? 
    	  else code = (isLargeAlign)? EncoderLargerAlign.encode(message, content, fileType, this.correctLevel, hints)
    			  : Encoder.encode(message, content, fileType, this.correctLevel, hints);
      }
      if(code==null) throw new WriterException("Unexpected error on creating QRcode.");
      BitMatrix matrix=renderResult(code, dimension, dimension);
      if(matrix ==null) throw new WriterException("Unexpected errors.");
      ByteMatrix rawMatrix = code.getMatrix();
      bitDimension=rawMatrix.getWidth();
      codeContentSize=rawMatrix.getHeight()*rawMatrix.getWidth();
      return matrix;     
  }
  public static BitMatrix renderResult(QRCode code, int width, int height){
	  ByteMatrix input=code.getMatrix();
	  return renderResult(input, width,height);
  }
  private static final int QUIET_ZONE_SIZE = 4;
/**
 * Change the given QR code into a BitMatrix, this function is copied from the zxing library
 * Note that the input matrix uses 0 == white, 1 == black, while the output matrix uses
 * 0 == black, 255 == white (i.e. an 8 bit greyscale bitmap).
 * @param code
 * @param width
 * @param height
 * @return
 */
 public static BitMatrix renderResult(ByteMatrix input, int width, int height) {
   if (input == null) {
     throw new IllegalStateException();
   }  
   int inputWidth = input.getWidth();
   int inputHeight = input.getHeight();
   int qrWidth = inputWidth + (QUIET_ZONE_SIZE <<1);
   int qrHeight = inputHeight + (QUIET_ZONE_SIZE <<1);
   int outputWidth = Math.max(width, qrWidth);
   int outputHeight = Math.max(height, qrHeight);

   float multipleFloat = Math.min(outputWidth / (float) qrWidth, outputHeight / (float) qrHeight);
   //Enlarge the outputWidth / outputHeight so that the QR code image takes at least 70% of the space
   int multiple = (int) multipleFloat;
   if(multiple < 0.7*multipleFloat) multiple++;
   outputWidth = multiple * qrWidth;
   outputHeight = multiple * qrHeight;
   
   // Padding includes both the quiet zone and the extra white pixels to accommodate the requested
   // dimensions. For example, if input is 25x25 the QR will be 33x33 including the quiet zone.
   // If the requested size is 200x160, the multiple will be 4, for a QR of 132x132. These will
   // handle all the padding from 100x100 (the actual QR) up to 200x160.
   int leftPadding = (outputWidth - (inputWidth * multiple)) / 2;
   int topPadding = (outputHeight - (inputHeight * multiple)) / 2;

   BitMatrix output = new BitMatrix(outputWidth, outputHeight);

   for (int inputY = 0, outputY = topPadding; inputY < inputHeight; inputY++, outputY += multiple) {
     // Write the contents of this row of the barcode
     for (int inputX = 0, outputX = leftPadding; inputX < inputWidth; inputX++, outputX += multiple) {
       if (input.get(inputX, inputY) == 1) {
         output.setRegion(outputX, outputY, multiple, multiple);
       }
     }
   }
   return output;
 }
  
  /**
   * Create a bufferedImage of the QR code containing the inserted data.
   * If the implementation platform does not supported bufferedImage, modify the function to fit in the implementation platform
   * @return
   * @throws Exception
   */
  public BufferedImage encodeAsBitmap() throws Exception{
	  BitMatrix result = encodeBitMatrix();	  
      if(result==null) return null;
      int width = result.getWidth();
      int height = result.getHeight();
      int[] pixels = new int[width * height];
      for (int y = 0; y < height; y++) {
        int offset = y * width;
        for (int x = 0; x < width; x++) {
          pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
        }
      }

      BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
      bitmap.setRGB(0, 0, width, height, pixels, 0, width);
      createdBarcode=bitmap;
      return createdBarcode;    
  }
  /**
   * Get the number of modules (including the function patterns but not quiet zone) of the created 2D barcode.
   * If no 2D barcode is created, return 0
   * Note that it may not equal to the dimensions of the created 2D barcode image
   * @return
   */
  public int getNumberOfBit(){
	  return codeContentSize;
  }
  public int getCodeDimension(){
	  return bitDimension;
  }
  
}