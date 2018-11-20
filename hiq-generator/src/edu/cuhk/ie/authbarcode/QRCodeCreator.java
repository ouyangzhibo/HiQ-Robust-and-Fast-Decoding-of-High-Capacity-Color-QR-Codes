/**
 * 
 * Copyright (C) 2014 Solon in CUHK
 *
 *
 */

package edu.cuhk.ie.authbarcode;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.fileTypeECI;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Version;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;



/**
 * This class does the work of encoding the data into a QR barcode.
 * The code is based on the QRCodeEncoder class in the ZXing library
 *
 * @author Solon 2014
 * 
 */
public final class QRCodeCreator {
  //private static final String TAG = QRCodeCreator.class.getSimpleName();
  private static final int WHITE = 0xFFFFFFFF;
  private static final int BLACK = 0xFF000000;
  private static final int[] RED3= {0,1,1};
  private static final int[] GREEN3= {1,0,1};
  private static final int[] BLUE3= {1,1,0};
  private static final int[] CYAN3= {1,0,0};
  private static final int[] MAGENTA3= {0,1,0};
  private static final int[] YELLOW3= {0,0,1};
  private static final int[] BLUE2 = {0, 1};
  private static final int[] GREEN2 = {1, 0};
  private static final int[] RED2 = {1, 1};
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
  private byte[] QRAppendByte=null;
  
  private int codeContentSize=0;
  private BufferedImage createdBarcode=null;
  
  private final int channelIdx; //indicator of the channel. 0 stands for monochrome; 1 stands for Red; 2 stands for Green; 3 stands for Blue.
  private BitMatrix bitMatrix = null;
  
  
  public QRCodeCreator(byte[] inputData, int imageDimension, int channel){
	  message=null;
	  channelIdx = channel;
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
  
  public QRCodeCreator(String inputData, int imageDimension, int channel){
	  channelIdx = channel;
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
  
  public QRCodeCreator(String inputData, byte[] inputByte, int imageDimension, int channel){
	  channelIdx = channel;
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
  public void setQRQppend(byte seq, byte parity){
	  this.QRAppendByte = new byte[]{seq, parity};
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
      if(QRAppendByte !=null) hints.put(EncodeHintType.QR_StructureAppend, this.QRAppendByte);
      if(message == null || message.isEmpty())  
    	  code = Encoder.encode(content, fileType, this.correctLevel, hints);
      else{
    	  hints.put(EncodeHintType.CHARACTER_SET, defaultEncoding);
    	  if(isPlainTextOnly)
    		  code = Encoder.encode(message, this.correctLevel, hints);
    	  //else return null;
    	  //TODO: Problem: currently scanners assume there is only one data in the QR code. 
    	  //If we want to create QR code with mixing mode of content, why not save them all into BSON format in the first place? 
    	  else code = Encoder.encode(message, content, fileType, this.correctLevel, hints);
      }
      if(code==null) throw new WriterException("Unexpected error on creating QRcode.");
      
      // coloring patterns according to the channel indicator
      ByteMatrix bitMatrix = code.getMatrix();
      BitMatrix matrix=renderResult(repaintPatterns(bitMatrix, code.getVersion()), dimension, dimension);
      //BitMatrix matrix=renderResult(bitMatrix, dimension, dimension);
      if(matrix ==null) throw new WriterException("Unexpected errors.");
      ByteMatrix rawMatrix = code.getMatrix();
      codeContentSize=rawMatrix.getHeight()*rawMatrix.getWidth();
      this.bitMatrix = matrix;
      return matrix;     
  }
  /**
   * 
   * @param input
   * @param versionInfo
   * @return
   * @author Zhibo Yang
   */
  private ByteMatrix repaintPatterns(ByteMatrix input, Version versionInfo) {
	  ByteMatrix output = input;
	  if(this.channelIdx == 0) return output;
	  int width = output.getWidth()-1;
	  int height = output.getHeight()-1;
	  int[] alignCenters = versionInfo.getAlignmentPatternCenters();
	  int idx = this.channelIdx-1;
	  //coloring finder patterns
	  for(int i=0; i<7; i++){
		  //left-top
		  output.set(0, i, GREEN3[idx]);
		  output.set(i, 0, GREEN3[idx]);
		  output.set(6, i, GREEN3[idx]);
		  output.set(i, 6, GREEN3[idx]);
		  //right-top
		  output.set(width-0, i, RED3[idx]);
		  output.set(width-i, 0, RED3[idx]);
		  output.set(width-6, i, RED3[idx]);
		  output.set(width-i, 6, RED3[idx]);
		  //left-bottom
		  output.set(0, height-i, BLUE3[idx]);
		  output.set(i, height-0, BLUE3[idx]);
		  output.set(6, height-i, BLUE3[idx]);
		  output.set(i, height-6, BLUE3[idx]);
	  }
	  for(int x=2; x<5; x++){
		  for(int y=2; y<5; y++){
			  output.set(x, y, MAGENTA3[idx]);//left-top
			  output.set(width-x, y, CYAN3[idx]);//right-top
			  output.set(x, height-y, YELLOW3[idx]);//left-bottom
		  }
	  }
	  //coloring alignment patterns
	  /**/
	  int[][] COLORSEQ = {BLUE3, GREEN3, RED3, YELLOW3, MAGENTA3, CYAN3};
	  int length = alignCenters.length;
	  int k = 0;
	  for(int i=0; i<length; i++){
		  for(int j=0; j<length; j++){
			  if(i==0 && j==0 || i==0 && j==length-1 || i==length-1 && j==0 || i==length-1 && j==length-1)
				  continue;
			  int colorIdx = k % 6;
			  
			  output.set(alignCenters[i], alignCenters[j], COLORSEQ[colorIdx][idx]);
			  for(int x=-2; x<3; x++){
				  output.set(alignCenters[i]+x, alignCenters[j]-2, COLORSEQ[colorIdx][idx]);
				  output.set(alignCenters[i]+x, alignCenters[j]+2, COLORSEQ[colorIdx][idx]);
				  output.set(alignCenters[i]-2, alignCenters[j]+x, COLORSEQ[colorIdx][idx]);
				  output.set(alignCenters[i]+2, alignCenters[j]+x, COLORSEQ[colorIdx][idx]);
			  }
			  k++;
		  }
	  }
	  
	  return output;
  }
  
  public static ByteMatrix repaintPatternsStatic(ByteMatrix input, Version versionInfo, int layerIdx, int layerNum) {
	  ByteMatrix output = input;
	  if(layerIdx == 0) return output;
	  int width = output.getWidth()-1;
	  int height = output.getHeight()-1;
	  int[] alignCenters = versionInfo.getAlignmentPatternCenters();
	  
	  int idx = layerIdx-1;
	  //coloring finder patterns
	  if (layerNum == 4){
		  if (layerIdx == 1){
			  for(int i=0; i<7; i++){
				  //left-top
				  output.set(0, i, 0);
				  output.set(i, 0, 0);
				  output.set(6, i, 0);
				  output.set(i, 6, 0);
				  //right-top
				  output.set(width-0, i, 0);
				  output.set(width-i, 0, 0);
				  output.set(width-6, i, 0);
				  output.set(width-i, 6, 0);
				  //left-bottom
				  output.set(0, height-i, 0);
				  output.set(i, height-0, 0);
				  output.set(6, height-i, 0);
				  output.set(i, height-6, 0);
			  }
			  for(int x=2; x<5; x++){
				  for(int y=2; y<5; y++){
					  output.set(x, y, 0);//left-top
					  output.set(width-x, y, 0);//right-top
					  output.set(x, height-y, 0);//left-bottom
				  }
			  }
		  } else{
			  idx -= 1;
			  layerNum = 3;			  
		  }
			  
	  }
	  if (layerNum == 3) {
		  for(int i=0; i<7; i++){
			  //left-top
			  output.set(0, i, GREEN3[idx]);
			  output.set(i, 0, GREEN3[idx]);
			  output.set(6, i, GREEN3[idx]);
			  output.set(i, 6, GREEN3[idx]);
			  //right-top
			  output.set(width-0, i, RED3[idx]);
			  output.set(width-i, 0, RED3[idx]);
			  output.set(width-6, i, RED3[idx]);
			  output.set(width-i, 6, RED3[idx]);
			  //left-bottom
			  output.set(0, height-i, BLUE3[idx]);
			  output.set(i, height-0, BLUE3[idx]);
			  output.set(6, height-i, BLUE3[idx]);
			  output.set(i, height-6, BLUE3[idx]);
		  }
		  for(int x=2; x<5; x++){
			  for(int y=2; y<5; y++){
				  output.set(x, y, MAGENTA3[idx]);//left-top
				  output.set(width-x, y, CYAN3[idx]);//right-top
				  output.set(x, height-y, YELLOW3[idx]);//left-bottom
			  }
		  }
	  } 
	  if (layerNum==2){
		  for(int i=0; i<7; i++){
			  //left-top
			  output.set(0, i, GREEN2[idx]);
			  output.set(i, 0, GREEN2[idx]);
			  output.set(6, i, GREEN2[idx]);
			  output.set(i, 6, GREEN2[idx]);
			  //right-top
			  output.set(width-0, i, RED2[idx]);
			  output.set(width-i, 0, RED2[idx]);
			  output.set(width-6, i, RED2[idx]);
			  output.set(width-i, 6, RED2[idx]);
			  //left-bottom
			  output.set(0, height-i, BLUE2[idx]);
			  output.set(i, height-0, BLUE2[idx]);
			  output.set(6, height-i, BLUE2[idx]);
			  output.set(i, height-6, BLUE2[idx]);
		  }
		  for(int x=2; x<5; x++){
			  for(int y=2; y<5; y++){
				  //left-top
				  output.set(x, y, GREEN2[idx]);
				  output.set(width-x, y, RED2[idx]);//right-top
				  output.set(x, height-y, BLUE2[idx]);//left-bottom
			  }
		  }
	  }
	  //coloring alignment patterns
	  /**/
	  int[][] COLORSEQ3 =  {BLUE3, GREEN3, RED3, YELLOW3, MAGENTA3, CYAN3};
	  int[][] COLORSEQ2 =  {BLUE2, GREEN2, RED2};
	  if (layerNum>=2) {
		  int[][] COLORSEQ = null;
		  if (layerNum == 2)
			  COLORSEQ =  COLORSEQ2;
		  else
			  COLORSEQ =  COLORSEQ3;
		  
		  int length = alignCenters.length;
		  int k = 0;
		  for(int i=0; i<length; i++){
			  for(int j=0; j<length; j++){
				  if(i==0 && j==0 || i==0 && j==length-1 || i==length-1 && j==0)
					  continue;
				  if(i==length-1 && j==length-1)
					  if(!(layerNum == 4 && layerIdx == 1))
						  continue;
				  int colorIdx = k % COLORSEQ.length;
				  
				  if (layerNum == 4 && layerIdx == 1) {
					  output.set(alignCenters[i], alignCenters[j], 0);
					  for(int x=-2; x<3; x++){
						  output.set(alignCenters[i]+x, alignCenters[j]-2, 0);
						  output.set(alignCenters[i]+x, alignCenters[j]+2, 0);
						  output.set(alignCenters[i]-2, alignCenters[j]+x, 0);
						  output.set(alignCenters[i]+2, alignCenters[j]+x, 0);
					  }
				  }else {
					  
					  output.set(alignCenters[i], alignCenters[j], COLORSEQ[colorIdx][idx]);
					  for(int x=-2; x<3; x++){
						  output.set(alignCenters[i]+x, alignCenters[j]-2, COLORSEQ[colorIdx][idx]);
						  output.set(alignCenters[i]+x, alignCenters[j]+2, COLORSEQ[colorIdx][idx]);
						  output.set(alignCenters[i]-2, alignCenters[j]+x, COLORSEQ[colorIdx][idx]);
						  output.set(alignCenters[i]+2, alignCenters[j]+x, COLORSEQ[colorIdx][idx]);
					  }
				  }
				  k++;
			  }
		  }
	  }
	  return output;
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
	  BitMatrix result = getBitMatrix();	  
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
  
  public BitMatrix getBitMatrix() throws Exception{
	  if (this.bitMatrix != null)
		  return this.bitMatrix;
	  else
		  return encodeBitMatrix();
  }
  /**
   * encode 3 bitMatrixs into one color QR code bitmap
   * @param bm1
   * @param bm2
   * @param bm3
   * @return
   */
  public static BufferedImage encodeAsColorBitmap(BitMatrix bm1, BitMatrix bm2, BitMatrix bm3){
	  
	  if(bm1==null||bm2==null||bm3==null) return null;
	  if(bm1.getHeight()!=bm2.getHeight()||bm2.getHeight()!=bm3.getHeight()||bm3.getWidth()!=bm1.getWidth()) {
		  System.out.println("three monochrome QR codes are not in the same dimension");
		  return null;
	  }
	  
      int width = bm1.getWidth();
      int height = bm1.getHeight();
      BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
        	Color col = null;
        	if(bm1.get(x, y))
        		if(bm2.get(x, y))
        			if(bm3.get(x, y))
        				col = Color.BLACK;//111
        			else
        				col = Color.BLUE;//110
        		else
        			if(bm3.get(x, y))
        				col = Color.GREEN;//101
        			else
        				col = Color.CYAN;//100
    		else
    			if(bm2.get(x, y))
        			if(bm3.get(x, y))
        				col = Color.RED;//011
        			else
        				col = Color.MAGENTA;//010
        		else
        			if(bm3.get(x, y))
        				col = Color.YELLOW;//001
        			else
        				col = Color.WHITE;//000
        	int rgb = col.getRGB();
        	bitmap.setRGB(x, y, rgb);
        }
      }
      return bitmap;    
  }
public static BufferedImage encodeAsColorBitmap(BitMatrix[] bms){
	  
	  if(bms==null) return null;
	  
      int width = bms[0].getWidth();
      int height = bms[0].getHeight();
      BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
        	Color col = null;
        	switch (bms.length) {
        		case 2:
        			if(bms[0].get(x, y))
    	        		if(bms[1].get(x, y))
    	        			col = Color.RED;//11
    	        		else
    	        			col = Color.GREEN;//10
    	    		else
    	    			if(bms[1].get(x, y))
    	    				col = Color.BLUE;//01
    	        		else
    	        			col = Color.WHITE;//00
        			break;
        		case 3:
        			if(bms[0].get(x, y))
    	        		if(bms[1].get(x, y))
    	        			if(bms[2].get(x, y))
    	        				col = Color.BLACK;//111
    	        			else
    	        				col = Color.BLUE;//110
    	        		else
    	        			if(bms[2].get(x, y))
    	        				col = Color.GREEN;//101
    	        			else
    	        				col = Color.CYAN;//100
    	    		else
    	    			if(bms[1].get(x, y))
    	        			if(bms[2].get(x, y))
    	        				col = Color.RED;//011
    	        			else
    	        				col = Color.MAGENTA;//010
    	        		else
    	        			if(bms[2].get(x, y))
    	        				col = Color.YELLOW;//001
    	        			else
    	        				col = Color.WHITE;//000
        			break;
        		case 4:
        			if(bms[0].get(x, y))//1
    	        		if(bms[1].get(x, y))//11
    	        			if(bms[2].get(x, y))//111
    	        				if(bms[3].get(x, y))
    	        					col = new Color(67, 67, 67);//1111
    	        				else
    	        					col = new Color(67, 67, 187);//1110
    	        			else//110
    	        				if(bms[3].get(x, y))
    	        					col = new Color(67, 187, 67);//1101
    	        				else
    	        					col = new Color(67, 187, 187);//1100
    	        		else//10
    	        			if(bms[2].get(x, y))//101
    	        				if(bms[3].get(x, y))
    	        					col = new Color(187, 67, 67);//1011
    	        				else
    	        					col = new Color(187, 67, 187);//1010
    	        			else//100
    	        				if(bms[3].get(x, y))
    	        					col = new Color(187, 187, 67);//1001
    	        				else
    	        					col = new Color(187, 187, 187);//1000    			
    	    		else//0
    	    			if(bms[1].get(x, y))//01
    	        			if(bms[2].get(x, y))//011
    	        				if(bms[3].get(x, y))
    	        					col = Color.BLACK;//0111
    	        				else
    	        					col = Color.BLUE;//0110
    	        			else//010
    	        				if(bms[3].get(x, y))
    	        					col = Color.GREEN;//0101
    	        				else
    	        					col = Color.CYAN;//0100
    	        		else//00
    	        			if(bms[2].get(x, y))//001
    	        				if(bms[3].get(x, y))
    	        					col = Color.RED;//0011
    	        				else
    	        					col = Color.MAGENTA;//0010
    	        			else//000
    	        				if(bms[3].get(x, y))
    	        					col = Color.YELLOW;//0001
    	        				else
    	        					col = Color.WHITE;//0000 
        			break;
        		default:System.out.println("ERROR: undefined number of layer!"); 
        			return null;
        	}
        	int rgb = col.getRGB();
        	bitmap.setRGB(x, y, rgb);
        }
      }
      return bitmap;    
  }
}