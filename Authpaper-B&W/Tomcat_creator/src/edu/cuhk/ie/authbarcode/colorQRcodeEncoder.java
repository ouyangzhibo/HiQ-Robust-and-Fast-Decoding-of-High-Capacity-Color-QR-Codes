package edu.cuhk.ie.authbarcode;

import com.google.zxing.EncodeHintType;

/**
 * Static class for creating three QR codes to store a data
 * The QR codes share the same version but have different error correction levels
 * This also support creating a color QR code by merging three QR codes with the same version but error correction levels M,M,L respectively.
 * author Solon Li
 */
public class colorQRcodeEncoder{
	private final String resultStr;
	private final byte[] resultByte;
	private final boolean isStructureAppend;
	private final int dimension;
	private int codeSize=0,codeDimension=0;
	public boolean isLargeAlign=false,isShuffle=false;
	public com.google.zxing.qrcode.decoder.ErrorCorrectionLevel[] eclevels=null;
	
	public colorQRcodeEncoder(String resultStr, int imageDimension){
		this.resultStr=resultStr;
		this.resultByte=null;
		this.isStructureAppend=false;
		this.dimension=(imageDimension>=177)? imageDimension:177;
	}
	public colorQRcodeEncoder(String resultStr, boolean isStructureAppend, int imageDimension){
		this.resultStr=resultStr;
		this.resultByte=null;
		this.isStructureAppend=isStructureAppend;
		this.dimension=(imageDimension>=177)? imageDimension:177;
	}
	public colorQRcodeEncoder(byte[] resultByte, boolean isStructureAppend, int imageDimension){
		this.resultStr=null;
		this.resultByte=resultByte;
		this.isStructureAppend=isStructureAppend;
		this.dimension=(imageDimension>=177)? imageDimension:177;
	}
	public java.awt.image.BufferedImage createColorBitmap(){
		com.google.zxing.qrcode.encoder.QRCode[] qrCodes=null;
		if(eclevels ==null){ 
			eclevels = new com.google.zxing.qrcode.decoder.ErrorCorrectionLevel[3];
			eclevels[2]=com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L;
			eclevels[1]=com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L;
			eclevels[0]=com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L;
		}
		java.util.Map<com.google.zxing.EncodeHintType,Object> hints 
			= new java.util.EnumMap<com.google.zxing.EncodeHintType,Object>(com.google.zxing.EncodeHintType.class);
		hints.put(com.google.zxing.EncodeHintType.MARGIN, 0);
		if(isStructureAppend) hints.put(com.google.zxing.EncodeHintType.QR_StructureAppend, new byte[]{0x0, 0x0});
		if(isLargeAlign) hints.put(EncodeHintType.QR_LargerAlign, true);
	    if(isShuffle) hints.put(EncodeHintType.QR_ShuffleCodeword, "true");
		try {
			if(resultStr !=null) qrCodes=divideByteIntoThreeQR(resultStr,eclevels, hints);
			else qrCodes=divideByteIntoThreeQR(resultByte,eclevels, hints);
			if(qrCodes !=null && qrCodes[0] !=null){
				codeDimension=qrCodes[0].getVersion().getDimensionForVersion();
				codeSize=0;
				for(int a=0,b=qrCodes.length;a<b;a++){
					codeSize+=getCapacityInByte(qrCodes[a].getVersion(),eclevels[a]);
				}
				return encodeAsColorBitmap(qrCodes,dimension);
			}
		}catch(com.google.zxing.WriterException e){ 
			//try normal QR codes
			QRCodeCreator qrCodeEncoder=(resultStr !=null)? new QRCodeCreator(resultStr, 708):new QRCodeCreator(resultByte, 708);
			qrCodeEncoder.isLargeAlign=isLargeAlign;	
			qrCodeEncoder.isShuffle=isShuffle;
			try {
				java.awt.image.BufferedImage qrCodeImage=qrCodeEncoder.encodeAsBitmap();
				codeSize=qrCodeEncoder.getNumberOfBit() >>3;
				codeDimension = qrCodeEncoder.getCodeDimension();
				return qrCodeImage;
			}catch(Exception e1){ }	
		}
		return null;
	}
	public int getCodeSize(){return codeSize;}
	public int getCodeDimension(){return codeDimension;}
	
	//If a module is 1,0,1 on BitMatrix 1,2,3 respectively, we should choose the color with index 101b = 5 from this array
	private static final java.awt.Color[] colorThreeList={
		java.awt.Color.WHITE,java.awt.Color.YELLOW,java.awt.Color.MAGENTA,java.awt.Color.RED, //000 001 010 011
		java.awt.Color.CYAN,java.awt.Color.GREEN,java.awt.Color.BLUE,java.awt.Color.BLACK //100 101 110 111
	};
	private static final java.awt.Color[] colorTwoList={
		java.awt.Color.WHITE,java.awt.Color.RED, //00 01
		java.awt.Color.CYAN,java.awt.Color.BLACK //10 11
	};
	/**
	 * encode 3 bitMatrixs into one color QR code bitmap
	 * @param bm1
	 * @param bm2
	 * @param bm3
	 * @return
	 */
	private static java.awt.image.BufferedImage encodeAsColorBitmap(com.google.zxing.common.BitMatrix bm1, 
			com.google.zxing.common.BitMatrix bm2, com.google.zxing.common.BitMatrix bm3){	  
		if(bm1==null||bm2==null||bm3==null) return null;
		if(bm1.getHeight()!=bm2.getHeight()||bm2.getHeight()!=bm3.getHeight()||bm3.getWidth()!=bm1.getWidth())			
			return null;
  
		int width = bm1.getWidth();
		int height = bm1.getHeight();
		java.awt.image.BufferedImage bitmap = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_BGR);
		for (int y = 0; y < height; y++){
			for (int x = 0; x < width; x++){
				int colorIndex= (((bm1.get(x, y))? 1:0) <<2) | (((bm2.get(x, y))? 1:0) <<1) | ((bm3.get(x, y))? 1:0);
				colorIndex = colorIndex & 0x07; //Only get residue of 8  
				bitmap.setRGB(x, y, colorThreeList[colorIndex].getRGB());
			}
      }
      return bitmap;    
	}
	//TODO: Extend this function to support multiple qrCodes
	private static java.awt.image.BufferedImage encodeAsColorBitmap(com.google.zxing.qrcode.encoder.QRCode[] qrCodes, int dimension){
		if(qrCodes ==null || qrCodes.length <2 || qrCodes.length >3) return null;
		if(qrCodes.length ==3){
			com.google.zxing.common.BitMatrix[] bms=new com.google.zxing.common.BitMatrix[3];
			for(int i=0;i<3;i++){
				bms[i] = QRCodeCreator.renderResult(repaintPatternsStatic(
					qrCodes[i].getMatrix(),qrCodes[i].getVersion(),i), dimension, dimension);
			}			
			return encodeAsColorBitmap(bms[0],bms[1],bms[2]);
		}
		//There are only two QR codes
		com.google.zxing.common.BitMatrix bm1=QRCodeCreator.renderResult(repaintPatternsStatic(
				qrCodes[0].getMatrix(),qrCodes[0].getVersion(),0), dimension, dimension);
		com.google.zxing.common.BitMatrix bm2=QRCodeCreator.renderResult(repaintPatternsStatic(
				qrCodes[1].getMatrix(),qrCodes[1].getVersion(),1), dimension, dimension);
		if(bm1.getHeight()!=bm2.getHeight()) return null;
  
		int width = bm1.getWidth();
		int height = bm1.getHeight();
		java.awt.image.BufferedImage bitmap = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_BGR);
		for (int y = 0; y < height; y++){
			for (int x = 0; x < width; x++){
				int colorIndex= (((bm1.get(x, y))? 1:0) <<1) | ((bm2.get(x, y))? 1:0);
				colorIndex = colorIndex & 0x03; //Only get residue of 4
				bitmap.setRGB(x, y, colorTwoList[colorIndex].getRGB());
			}
      }
      return bitmap;  
		
	}
	
	public static com.google.zxing.qrcode.encoder.QRCode[] divideByteIntoThreeQR(byte[] inputs,
			com.google.zxing.qrcode.decoder.ErrorCorrectionLevel[] ecLevels, 
			java.util.Map<com.google.zxing.EncodeHintType,?> hints)
		throws com.google.zxing.WriterException{
		return divideByteIntoThreeQR(inputs,com.google.zxing.qrcode.decoder.Mode.BinaryBYTE,null,
				com.google.zxing.common.fileTypeECI.auth2dbarcode,ecLevels,hints);
	}
	public static com.google.zxing.qrcode.encoder.QRCode[] divideByteIntoThreeQR(String inputs,
			com.google.zxing.qrcode.decoder.ErrorCorrectionLevel[] ecLevels, 
			java.util.Map<com.google.zxing.EncodeHintType,?> hints)
		throws com.google.zxing.WriterException{
		String encoding = hints == null ? null : (String) hints.get(com.google.zxing.EncodeHintType.CHARACTER_SET);
		if(encoding ==null) encoding = DEFAULT_BYTE_MODE_ENCODING;
		com.google.zxing.qrcode.decoder.Mode mode = com.google.zxing.qrcode.encoder.Encoder.chooseMode(inputs, encoding);		
		com.google.zxing.common.BitArray dataBits = new com.google.zxing.common.BitArray();
		com.google.zxing.qrcode.encoder.Encoder.appendBytes(inputs, mode, dataBits, encoding);
		byte[] inputInByte=new byte[dataBits.getSizeInBytes()];
		dataBits.toBytes(0, inputInByte, 0, dataBits.getSizeInBytes());
		return divideByteIntoThreeQR(inputInByte,mode,encoding,null,ecLevels,hints);
	}
	private static final String DEFAULT_BYTE_MODE_ENCODING = "ISO-8859-1";
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
		if(version ==null) throw new com.google.zxing.WriterException("Data too big");
		
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
}