/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcodescanner.android.result;

import java.io.UnsupportedEncodingException;
import java.util.EnumMap;
import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.WriterException;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ParsedResultType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.fileTypeECI;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;

import edu.cuhk.ie.authbarcodescanner.android.ScannerFragment;

/**
 * A light version of QR code encoder.
 * @author solon li
 *
 */
public class LightBarcodeEncoder {
	public static Bitmap reconstructBarcode(ParsedResult result, Result rawResult){
		//Here we use the input data to recontruct the QR code back
		Map<EncodeHintType,Object> hints = new EnumMap<EncodeHintType,Object>(EncodeHintType.class);
		String ecLevel=rawResult.getStringMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL);
		//Check if the scanned QR code is colorby reading its error correction level		
		boolean isColor=(ecLevel !=null && ecLevel.length() >1)? true:false;
		//Use enlarged alignment QR codes
		String isEnlargedAlign=rawResult.getStringMetadata(ResultMetadataType.Enlarged_Alignment);
		if(isEnlargedAlign !=null && isEnlargedAlign.compareTo("true")==0) hints.put(EncodeHintType.QR_LargerAlign, true);
		String isShuffleCodeword=rawResult.getStringMetadata(ResultMetadataType.Shuffled_Codeword);
		if(isShuffleCodeword !=null && isShuffleCodeword.compareTo("true")==0) hints.put(EncodeHintType.QR_ShuffleCodeword, true);
		
		BarcodeFormat format=rawResult.getBarcodeFormat();
		int width=350, height=350;
		//Change the height for 1D barcodes
		if(ScannerFragment.oneDFormats.contains(format)) height=150;
		if(result.getType() !=ParsedResultType.AUTH2DBARCODE && result.getType() !=ParsedResultType.BINARY){
			//Add UTF-8 encoding if the text cannot be encoded into ISO-8859-1
			boolean isUTF8Encoding=false;
			try{
				String encodedText=new String(rawResult.getText().getBytes("ISO-8859-1"),"ISO-8859-1");
				isUTF8Encoding = (encodedText !=null && !encodedText.isEmpty() 
						&& encodedText.equals(rawResult.getText()))? false : true;						
			}catch(UnsupportedEncodingException e){
				isUTF8Encoding=true;
			}
			if(isUTF8Encoding) hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");
		}
		if(!isColor){
			hints.put(EncodeHintType.ERROR_CORRECTION, (ecLevel ==null || ErrorCorrectionLevel.getEnum(ecLevel) ==null)?
					ErrorCorrectionLevel.M : ErrorCorrectionLevel.getEnum(ecLevel));
			return ( result.getType() ==ParsedResultType.AUTH2DBARCODE 
				&& result.getFileType() !=null 
				&& result.getFileType().indexOf(Byte.class.getSimpleName()) != -1 )?
				createBWQRcode(null, result.getFileByte(), fileTypeECI.auth2dbarcode.name(), 
					format, width, height, hints)										
				:(result.getType() ==ParsedResultType.BINARY && result.getFileType() !=null)?
					createBWQRcode(null, result.getFileByte(), result.getFileType(),
						format, width, height, hints)
					:createBWQRcode(rawResult.getText(), null, null, format, width, height, hints);
		}else{
			ErrorCorrectionLevel[] eclevels =new ErrorCorrectionLevel[ecLevel.length()];
			for(int i=0,L=eclevels.length;i<L;i++){	
				String ecc=String.valueOf(ecLevel.charAt(i));				
				eclevels[i]=(ecc==null || ErrorCorrectionLevel.getEnum(ecc) ==null)?
						ErrorCorrectionLevel.M : ErrorCorrectionLevel.getEnum(ecc);				
			}			
			return ( result.getType() ==ParsedResultType.AUTH2DBARCODE 
				&& result.getFileType() !=null 
				&& result.getFileType().indexOf(Byte.class.getSimpleName()) != -1 )?
				createColorQRcode(null, result.getFileByte(), fileTypeECI.auth2dbarcode.name(), 
					format, width, height, hints, eclevels)
				:(result.getType() ==ParsedResultType.BINARY && result.getFileType() !=null)?
					createColorQRcode(null, result.getFileByte(), result.getFileType(),
						format, width, height, hints, eclevels)
				:createColorQRcode(rawResult.getText(), null, null, format, width, height, hints, eclevels);
		}
	}
	private static Bitmap createBWQRcode(String inputStr, byte[] content, String type, 
			BarcodeFormat format, int width, int height, Map<EncodeHintType,Object> hints){
		MultiFormatWriter writer = new MultiFormatWriter();
		try{
			BitMatrix tempRow = (inputStr !=null)? writer.encode(inputStr, format, width, height, hints)
					:writer.encode(content, type, format, width, height, hints);
			return bitMatrixToBitmap(tempRow);
		}catch(WriterException e2){ }		
		//Try color QR code instead
		return ((inputStr !=null && inputStr.length() > 600) || (content !=null && content.length >600))?
			createColorQRcode(inputStr, content, type, format,width,height,hints,null) : null;
	}
	private static Bitmap bitMatrixToBitmap(BitMatrix tempRow){
		if(tempRow==null) return null;
		int imgheight=tempRow.getHeight(), imgwidth=tempRow.getWidth();
		int[] tempRowColor = new int[imgheight*imgwidth];
		for(int i=0,j=0;j<imgheight;j++){
			int offset=j*imgwidth;
			for(i=0;i<imgwidth;i++){
				//Array.setInt(tempRowColor, j*width+i, (tempRow.get(i, j))? Color.BLACK:Color.WHITE);
				tempRowColor[offset+i]=(tempRow.get(i, j))? Color.BLACK:Color.WHITE;
			}
		}
		Bitmap img = Bitmap.createBitmap(imgwidth,imgheight, Bitmap.Config.RGB_565);
		img.setPixels(tempRowColor, 0, imgwidth, 0, 0, imgwidth, imgheight);
		return img;
	}
	
	private static final int QUIET_ZONE_SIZE = 4;
	private static final String DEFAULT_BYTE_MODE_ENCODING = "ISO-8859-1";
	private static Bitmap createColorQRcode(String inputStr, byte[] content, String type, 
			BarcodeFormat format, int width, int height, Map<EncodeHintType,Object> hints, 
			com.google.zxing.qrcode.decoder.ErrorCorrectionLevel[] eclevels){
		com.google.zxing.qrcode.encoder.QRCode[] qrCodes=null;
		if(eclevels ==null){
			eclevels = new com.google.zxing.qrcode.decoder.ErrorCorrectionLevel[3];
			eclevels[2]=com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M;
			eclevels[1]=com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L;
			eclevels[0]=com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L;
		}		
		hints.put(com.google.zxing.EncodeHintType.MARGIN, 0);
		hints.put(com.google.zxing.EncodeHintType.QR_StructureAppend, new byte[]{0x0, 0x0});
		com.google.zxing.common.fileTypeECI fileType = (type !=null)? 
				com.google.zxing.common.fileTypeECI.getfileTypeECIByName(type):null;
		
		try{
			if(inputStr !=null){
				String encoding = hints == null ? null : (String) hints.get(com.google.zxing.EncodeHintType.CHARACTER_SET);
				if(encoding ==null) encoding = DEFAULT_BYTE_MODE_ENCODING;
				com.google.zxing.qrcode.decoder.Mode mode = com.google.zxing.qrcode.encoder.Encoder.chooseMode(inputStr, encoding);		
				com.google.zxing.common.BitArray dataBits = new com.google.zxing.common.BitArray();
				com.google.zxing.qrcode.encoder.Encoder.appendBytes(inputStr, mode, dataBits, encoding);
				byte[] inputInByte=new byte[dataBits.getSizeInBytes()];
				dataBits.toBytes(0, inputInByte, 0, dataBits.getSizeInBytes());				
				qrCodes=divideByteIntoThreeQR(inputInByte,mode,encoding,null,eclevels,hints);
			}else qrCodes=divideByteIntoThreeQR(content,com.google.zxing.qrcode.decoder.Mode.BinaryBYTE,null,
					fileType,eclevels,hints);
		}catch(WriterException e2){ return null; }
		if(qrCodes ==null || qrCodes[0] ==null || qrCodes.length !=3){
			return ((inputStr !=null && inputStr.length() < 600) || (content !=null && content.length <600))?
					createBWQRcode(inputStr, content, type, format,width,height,hints) : null;
		}
		com.google.zxing.common.BitMatrix[] bms=new com.google.zxing.common.BitMatrix[3];
		for(int i=0;i<3;i++){
			bms[i] = renderResult(repaintPatternsStatic(
				qrCodes[i].getMatrix(),qrCodes[i].getVersion(),i), width, height);
		}			
		return encodeAsColorBitmap(bms[0],bms[1],bms[2]);	
	}
	
	/**
	 * Given a input array, divide the data into three QR codes with given error levels
	 * The created QR codes should be in the same version 
	 */
	private static com.google.zxing.qrcode.encoder.QRCode[] divideByteIntoThreeQR(byte[] inputs, 
			com.google.zxing.qrcode.decoder.Mode mode, String encoding, com.google.zxing.common.fileTypeECI type,
			com.google.zxing.qrcode.decoder.ErrorCorrectionLevel[] ecLevels, 
			java.util.Map<com.google.zxing.EncodeHintType,?> hints)
		throws com.google.zxing.WriterException{		
		//Get the suitable version
		boolean isStructure = (hints.containsKey(com.google.zxing.EncodeHintType.QR_StructureAppend))? true:false;
		if(inputs ==null || inputs.length <=0 || ecLevels ==null || ecLevels.length <1 || ecLevels.length >15 || mode ==null) 
			return null;	
		//For each QR code, it stores not only the input data, but also the character encoding (mode)
		//and header bits (so that the QR codes can be merged)
		int headerBitPerQR = 4;
		//If it is structure append, add the length for structure append header bit
		if(isStructure) headerBitPerQR += 20;  
		if(mode ==com.google.zxing.qrcode.decoder.Mode.BYTE && !DEFAULT_BYTE_MODE_ENCODING.equals(encoding))
			headerBitPerQR += 12; //extra bits for indicating the encoding method
		if(type != null)
			headerBitPerQR += 12; //extra bits for indicating the encoding method
		com.google.zxing.qrcode.decoder.Version version = chooseColorQRcodeVersion(inputs.length *8, ecLevels, headerBitPerQR, mode);
		if(version ==null){
			if(isStructure){//If the data is too big, try it again without structure append mode
				hints.remove(com.google.zxing.EncodeHintType.QR_StructureAppend);
				return divideByteIntoThreeQR(inputs,mode,encoding,type,ecLevels,hints);
			}else throw new com.google.zxing.WriterException("Data too big");
		}
		
		//With the suitable version, we can perform the QR code creations
		com.google.zxing.qrcode.encoder.QRCode[] results = new com.google.zxing.qrcode.encoder.QRCode[ecLevels.length];
		headerBitPerQR += mode.getCharacterCountBits(version);
		byte parity=0x0;			
		
		if(isStructure){
			for(int i=0, count=inputs.length;i<count;i++)
				parity ^= inputs[i];
		}
		//System.out.println("Writing Data to QR code version  : "+version);
		for(int i=0,j=0,l=inputs.length,m=ecLevels.length;i<m && j<l;i++){
			int capacityOfThisQR = getCapacityInByte(version,ecLevels[i]) - (headerBitPerQR+7) /8;
			//System.out.println("Writing Data to QR code with capacity : "+capacityOfThisQR);
			//Divide the data array
			com.google.zxing.common.BitArray dataBits = new com.google.zxing.common.BitArray();
			//System.out.println("Writing Data start from: "+j);
			for(int a=0;a<capacityOfThisQR && j<l;a++)
				dataBits.appendBits(inputs[j++], 8);
			//System.out.println("Writing Data end at "+j+" with total length "+l);
			com.google.zxing.common.BitArray headerAndDataBits = new com.google.zxing.common.BitArray();		
			if(isStructure){
				headerAndDataBits.appendBits(com.google.zxing.qrcode.decoder.Mode.STRUCTURED_APPEND.getBits(), 4);
				headerAndDataBits.appendBits( (byte) (((0x0f & i)<<4) | (0x0f & m)), 8);
				headerAndDataBits.appendBits(parity, 8);
			}		
			if(mode ==com.google.zxing.qrcode.decoder.Mode.BYTE && !DEFAULT_BYTE_MODE_ENCODING.equals(encoding)){
				com.google.zxing.common.CharacterSetECI eci 
					= com.google.zxing.common.CharacterSetECI.getCharacterSetECIByName(encoding);
				if (eci != null){
					headerAndDataBits.appendBits(com.google.zxing.qrcode.decoder.Mode.ECI.getBits(), 4);
				    // This is correct for values up to 127, which is all we need now.
					headerAndDataBits.appendBits(eci.getValue(), 8);
				}
			}
			if(type != null){
				headerAndDataBits.appendBits(com.google.zxing.qrcode.decoder.Mode.ECI.getBits(), 4);
				headerAndDataBits.appendBits(type.getValue(), 8);
			}
			int numLetters = dataBits.getSizeInBytes();
			//mode
			headerAndDataBits.appendBits(mode.getBits(), 4);
			//length indicator
			int numBits = mode.getCharacterCountBits(version);
			if (numLetters >= (1 << numBits)) {
			  throw new com.google.zxing.WriterException(numLetters + "is bigger than" + ((1 << numBits) - 1));
			}
			headerAndDataBits.appendBits(numLetters, numBits);
			//data content
			headerAndDataBits.appendBitArray(dataBits);
			results[i] = com.google.zxing.qrcode.encoder.Encoder.prepareQRCode(headerAndDataBits,version,ecLevels[i],
					mode,hints.containsKey(com.google.zxing.EncodeHintType.QR_ShuffleCodeword));
			if(hints.containsKey(com.google.zxing.EncodeHintType.QR_LargerAlign))
				com.google.zxing.qrcode.encoder.EncoderLargerAlign.modifyAlignPatterns(results[i]);
		}
		return results;
	}
	/**
	 * Given a data size dataSizeInBits and mode (Indicating the encodeing of the input data, 
	 * e.g. Mode.Mode.ALPHANUMERIC, Mode.BYTE, etc),
	 * this function calculates the suitable version of color QR code which consists of two eclevel L and one eclevel L
	 * and also to store this data
	 */
	private static com.google.zxing.qrcode.decoder.Version chooseColorQRcodeVersion(int dataSizeInBits, 
			com.google.zxing.qrcode.decoder.ErrorCorrectionLevel[] ecLevels, 
			int headerBitPerQR, com.google.zxing.qrcode.decoder.Mode mode){
		//Check if the data is too small
		com.google.zxing.qrcode.decoder.Version minVer 
			= com.google.zxing.qrcode.decoder.Version.getVersionForNumber(5);
		int minDataSize=0;
		for(int a=0,b=ecLevels.length;a<b;a++){
			minDataSize+=getCapacityInByte(minVer,ecLevels[a]);
		}
		if(minDataSize > (dataSizeInBits/8)) return null;
		
		for (int versionNum = 5; versionNum <= 40; versionNum++){
			com.google.zxing.qrcode.decoder.Version version 
				= com.google.zxing.qrcode.decoder.Version.getVersionForNumber(versionNum);
			int colorQRByte =0;
			for(int a=0,b=ecLevels.length;a<b;a++){
				colorQRByte+=getCapacityInByte(version,ecLevels[a]);
			}				
	
			int header = headerBitPerQR+mode.getCharacterCountBits(version);
			int totalInputBits = header*3 + dataSizeInBits;
	
			//Round up to the nearest 8 
			int totalInputBytes = (totalInputBits + 7) / 8;
			if(colorQRByte >= totalInputBytes){
			  return version;
			}
		}
		return null;
	}
	private static int getCapacityInByte(com.google.zxing.qrcode.decoder.Version version, 
		com.google.zxing.qrcode.decoder.ErrorCorrectionLevel eclevel){
		return version.getTotalCodewords() - version.getECBlocksForLevel(eclevel).getTotalECCodewords();
	}
	/**
	 * Change the given QR code into a BitMatrix, this function is copied from the zxing library
	 * Note that the input matrix uses 0 == white, 1 == black, while the output matrix uses
	 * 0 == black, 255 == white (i.e. an 8 bit greyscale bitmap).
	 * @param code
	 * @param width
	 * @param height
	 * @return
	 */
	private static BitMatrix renderResult(ByteMatrix input, int width, int height) {
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
			//Write the contents of this row of the barcode
			for (int inputX = 0, outputX = leftPadding; inputX < inputWidth; inputX++, outputX += multiple) {
				if (input.get(inputX, inputY) == 1) {
					output.setRegion(outputX, outputY, multiple, multiple);
				}
			}
		}
		return output;
	}
	private static final int[] RED= {0,1,1};
	private static final int[] GREEN= {1,0,1};
	private static final int[] BLUE= {1,1,0};
	private static final int[] CYAN= {1,0,0};
	private static final int[] MAGENTA= {0,1,0};
	private static final int[] YELLOW= {0,0,1};
	  
	private static com.google.zxing.qrcode.encoder.ByteMatrix repaintPatternsStatic(
			com.google.zxing.qrcode.encoder.ByteMatrix input, 
			com.google.zxing.qrcode.decoder.Version versionInfo, int idx) {		
		com.google.zxing.qrcode.encoder.ByteMatrix output = input;
		int width = output.getWidth()-1;
		int height = output.getHeight()-1;
		int[] alignCenters = versionInfo.getAlignmentPatternCenters();		
		//coloring finder patterns
		for(int i=0; i<7; i++){
			//left-top
			output.set(0, i, GREEN[idx]);
			output.set(i, 0, GREEN[idx]);
			output.set(6, i, GREEN[idx]);
			output.set(i, 6, GREEN[idx]);
			//right-top
			output.set(width-0, i, RED[idx]);
			output.set(width-i, 0, RED[idx]);
			output.set(width-6, i, RED[idx]);
			output.set(width-i, 6, RED[idx]);
			//left-bottom
			output.set(0, height-i, BLUE[idx]);
			output.set(i, height-0, BLUE[idx]);
			output.set(6, height-i, BLUE[idx]);
			output.set(i, height-6, BLUE[idx]);
		}
		for(int x=2; x<5; x++){
			for(int y=2; y<5; y++){
				output.set(x, y, MAGENTA[idx]);//left-top
				output.set(width-x, y, CYAN[idx]);//right-top
				output.set(x, height-y, YELLOW[idx]);//left-bottom
			}
		}
		//coloring alignment patterns
		int[][] COLORSEQ = {BLUE, GREEN, RED, YELLOW, MAGENTA, CYAN};
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
	//If a module is 1,0,1 on BitMatrix 1,2,3 respectively, we should choose the color with index 101b = 5 from this array
	private static final int[] colorThreeList={
		Color.WHITE,Color.YELLOW,Color.MAGENTA,Color.RED, //000 001 010 011
		Color.CYAN,Color.GREEN,Color.BLUE,Color.BLACK //100 101 110 111
	};
	/**
	 * encode 3 bitMatrixs into one color QR code bitmap
	 * @param bm1
	 * @param bm2
	 * @param bm3
	 * @return
	 */
	private static Bitmap encodeAsColorBitmap(com.google.zxing.common.BitMatrix bm1, 
			com.google.zxing.common.BitMatrix bm2, com.google.zxing.common.BitMatrix bm3){	  
		if(bm1==null||bm2==null||bm3==null) return null;
		if(bm1.getHeight()!=bm2.getHeight()||bm2.getHeight()!=bm3.getHeight()||bm3.getWidth()!=bm1.getWidth())			
			return null;
  
		int width = bm1.getWidth();
		int height = bm1.getHeight();
		Bitmap img = Bitmap.createBitmap(width,height, Bitmap.Config.RGB_565);
		for (int y = 0; y < height; y++){
			for (int x = 0; x < width; x++){
				int colorIndex= (((bm1.get(x, y))? 1:0) <<2) | (((bm2.get(x, y))? 1:0) <<1) | ((bm3.get(x, y))? 1:0);
				colorIndex = colorIndex & 0x07; //Only get residue of 8  
				img.setPixel(x, y, colorThreeList[colorIndex]);					
			}
      }
      return img;    
	}
	 
}