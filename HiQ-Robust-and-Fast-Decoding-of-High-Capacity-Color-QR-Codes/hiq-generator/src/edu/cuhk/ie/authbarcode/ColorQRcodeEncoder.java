package edu.cuhk.ie.authbarcode;

import java.util.Map;

import com.google.zxing.EncodeHintType;
import com.google.zxing.common.CharacterSetECI;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Mode;

/**
 * Static class for creating three QR codes to store a data
 * The QR codes share the same version but have different error correction levels
 * author Solon Li
 */
public class ColorQRcodeEncoder{
	public static com.google.zxing.qrcode.encoder.QRCode[] divideByteIntoMultiQRs(byte[] inputs,
			ErrorCorrectionLevel[] ecLevels, Map<com.google.zxing.EncodeHintType,?> hints)
		throws com.google.zxing.WriterException{
		return divideByteIntoMultiQRs(inputs,com.google.zxing.qrcode.decoder.Mode.BinaryBYTE,null,
				com.google.zxing.common.fileTypeECI.auth2dbarcode,ecLevels,hints);
	}
	public static com.google.zxing.qrcode.encoder.QRCode[] divideByteIntoMultiQRs(String inputs,
			ErrorCorrectionLevel[] ecLevels, Map<com.google.zxing.EncodeHintType,?> hints)
		throws com.google.zxing.WriterException{
		String encoding = hints == null ? null : (String) hints.get(EncodeHintType.CHARACTER_SET);
		if(encoding ==null) encoding = DEFAULT_BYTE_MODE_ENCODING;
		Mode mode = com.google.zxing.qrcode.encoder.Encoder.chooseMode(inputs, encoding);		
		com.google.zxing.common.BitArray dataBits = new com.google.zxing.common.BitArray();
		com.google.zxing.qrcode.encoder.Encoder.appendBytes(inputs, mode, dataBits, encoding);
		byte[] inputInByte=new byte[dataBits.getSizeInBytes()];
		dataBits.toBytes(0, inputInByte, 0, dataBits.getSizeInBytes());
		return divideByteIntoMultiQRs(inputInByte,mode,encoding,null,ecLevels,hints);
	}
	private static final String DEFAULT_BYTE_MODE_ENCODING = "ISO-8859-1";
	/**
	 * Given a input array, divide the data into three QR codes with given error levels
	 * The created QR codes should be in the same version 
	 */
	private static com.google.zxing.qrcode.encoder.QRCode[] divideByteIntoMultiQRs(byte[] inputs, 
			com.google.zxing.qrcode.decoder.Mode mode, String encoding, com.google.zxing.common.fileTypeECI type,
			ErrorCorrectionLevel[] ecLevels, Map<com.google.zxing.EncodeHintType,?> hints)
		throws com.google.zxing.WriterException{		
		//Get the suitable version
		boolean isStructure = (hints.containsKey(com.google.zxing.EncodeHintType.QR_StructureAppend))? true:false;
		if(inputs ==null || inputs.length <=0 || ecLevels ==null || mode ==null) 
			return null;	
		//For each QR code, it stores not only the input data, but also the character encoding (mode)
		//and header bits (so that the QR codes can be merged)
		int headerBitPerQR = 4;
		//If it is structure append, add the length for structure append header bit
		if(isStructure) headerBitPerQR += 20;  
		if(mode ==Mode.BYTE && !DEFAULT_BYTE_MODE_ENCODING.equals(encoding))
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
		for(int i=0,j=0,l=inputs.length;i<ecLevels.length && j<l;i++){
			int capacityOfThisQR = getCapacityInByte(version,ecLevels[i]) - (headerBitPerQR+7) /8;
			//Divide the data array
			com.google.zxing.common.BitArray dataBits = new com.google.zxing.common.BitArray();
			System.out.println("Writing Data start from: "+j);
			for(int a=0;a<capacityOfThisQR && j<l;a++)
				dataBits.appendBits(inputs[j++], 8);
			System.out.println("Writing Data end at "+j+" with total length "+l);
			com.google.zxing.common.BitArray headerAndDataBits = new com.google.zxing.common.BitArray();		
			if(isStructure){
				headerAndDataBits.appendBits(com.google.zxing.qrcode.decoder.Mode.STRUCTURED_APPEND.getBits(), 4);
				headerAndDataBits.appendBits( (byte) (((0x03 & i)<<4) | 0x03), 8);
				headerAndDataBits.appendBits(parity, 8);
			}		
			if(mode ==Mode.BYTE && !DEFAULT_BYTE_MODE_ENCODING.equals(encoding)){
				CharacterSetECI eci = CharacterSetECI.getCharacterSetECIByName(encoding);
				if (eci != null){
					headerAndDataBits.appendBits(Mode.ECI.getBits(), 4);
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
			results[i] = com.google.zxing.qrcode.encoder.Encoder.prepareQRCode(headerAndDataBits,version,ecLevels[i],mode);	
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
		ErrorCorrectionLevel[] ecLevels, int headerBitPerQR, com.google.zxing.qrcode.decoder.Mode mode){
		
		for (int versionNum = 1; versionNum <= 40; versionNum++) {
			com.google.zxing.qrcode.decoder.Version version 
				= com.google.zxing.qrcode.decoder.Version.getVersionForNumber(versionNum);
			int colorQRByte = 0;
			for (int i = 0; i < ecLevels.length; i++)
				colorQRByte += getCapacityInByte(version,ecLevels[i]);
			
			int header = headerBitPerQR+mode.getCharacterCountBits(version);
			int totalInputBits = header*ecLevels.length + dataSizeInBits;

			//Round up to the nearest 8 
			int totalInputBytes = (totalInputBits + 7) / 8;
			if(colorQRByte >= totalInputBytes){
			  return version;
			}
		}
		return null;
	}
	private static int getCapacityInByte(com.google.zxing.qrcode.decoder.Version version, ErrorCorrectionLevel eclevel){
		return version.getTotalCodewords() - version.getECBlocksForLevel(eclevel).getTotalECCodewords();
	}
}