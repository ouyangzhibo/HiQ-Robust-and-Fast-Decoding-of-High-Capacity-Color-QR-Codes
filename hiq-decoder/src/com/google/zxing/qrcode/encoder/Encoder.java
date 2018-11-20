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
 * Copyright 2012 Solon Li
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

package com.google.zxing.qrcode.encoder;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitArray;
import com.google.zxing.common.CharacterSetECI;
import com.google.zxing.common.fileTypeECI;
import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Mode;
import com.google.zxing.qrcode.decoder.Version;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author satorux@google.com (Satoru Takabayashi) - creator
 * @author dswitkin@google.com (Daniel Switkin) - ported from C++
 * @author solon li - modify to support binary data input and mix mode, also modified the chooseVersion method
 */
public class Encoder {

  // The original table is defined in the table 5 of JISX0510:2004 (p.19).
  private static final int[] ALPHANUMERIC_TABLE = {
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 0x00-0x0f
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 0x10-0x1f
      36, -1, -1, -1, 37, 38, -1, -1, -1, -1, 39, 40, -1, 41, 42, 43,  // 0x20-0x2f
      0,   1,  2,  3,  4,  5,  6,  7,  8,  9, 44, -1, -1, -1, -1, -1,  // 0x30-0x3f
      -1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,  // 0x40-0x4f
      25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, -1, -1, -1, -1, -1,  // 0x50-0x5f
  };

  static final String DEFAULT_BYTE_MODE_ENCODING = "ISO-8859-1";

  protected Encoder() {
  }

  // The mask penalty calculation is complicated.  See Table 21 of JISX0510:2004 (p.45) for details.
  // Basically it applies four rules and summate all penalties.
  private static int calculateMaskPenalty(ByteMatrix matrix) {
	  return MaskUtil.applyMaskPenaltyRule1(matrix)
		        + MaskUtil.applyMaskPenaltyRule2(matrix)
		        + MaskUtil.applyMaskPenaltyRule3(matrix)
		        + MaskUtil.applyMaskPenaltyRule4(matrix);
  }

  /**
   *  Encode "bytes" with the error correction level "ecLevel". The encoding mode will be chosen
   * internally by chooseMode(). On success, store the result in "qrCode".
   *
   * We recommend you to use QRCode.EC_LEVEL_L (the lowest level) for
   * "getECLevel" since our primary use is to show QR code on desktop screens. We don't need very
   * strong error correction for this purpose.
   *
   * Note that there is no way to encode bytes in MODE_KANJI. We might want to add EncodeWithMode()
   * with which clients can specify the encoding mode. For now, we don't need the functionality.
   */
  public static QRCode encode(String content, ErrorCorrectionLevel ecLevel)
      throws WriterException {
    return encode(content, ecLevel, null);
  }
  public static QRCode encode(byte[] array, String fileType, ErrorCorrectionLevel ecLevel)
	      throws WriterException {
	  return encode(array, fileType, ecLevel, null);
  }
  
  public static QRCode encode(String content,
                              ErrorCorrectionLevel ecLevel,
                              Map<EncodeHintType,?> hints) throws WriterException {
	  String encoding = hints == null ? null : (String) hints.get(EncodeHintType.CHARACTER_SET);
	  if(encoding ==null) encoding = DEFAULT_BYTE_MODE_ENCODING;
	  
	  //Step 1: Choose the encoding mode.
	  Mode mode = chooseMode(content, encoding);
	  
	  //Step 2: Build a bit vector that contains both header and data.
	  BitArray headerAndDataBits = new BitArray();
	  //If it is structure append, add the structure append data first,
	  if(hints.containsKey(EncodeHintType.QR_StructureAppend)){
		  byte[] appendHeader=(byte[]) hints.get(EncodeHintType.QR_StructureAppend);
		  if(appendHeader !=null && appendHeader.length ==2){
			  headerAndDataBits.appendBits(Mode.STRUCTURED_APPEND.getBits(), 4);
			  headerAndDataBits.appendBits(appendHeader[0], 8);
			  headerAndDataBits.appendBits(appendHeader[1], 8);
		  }		  
	  }
	  //Append ECI message if applicable
	  if(mode ==Mode.BYTE && !DEFAULT_BYTE_MODE_ENCODING.equals(encoding)){
		  CharacterSetECI eci = CharacterSetECI.getCharacterSetECIByName(encoding);
		  if (eci != null) appendECI(eci, headerAndDataBits);
	  }
	  
	  // Step 3: Append "bytes" into "dataBits" in appropriate encoding.
	  BitArray dataBits = new BitArray();
	  appendBytes(content, mode, dataBits, encoding);
	  //Find out a proper version of QR code
	  Version version = chooseVersion(dataBits.getSize(), ecLevel, mode, headerAndDataBits.getSize());
	  int numLetters = mode == Mode.BYTE ? dataBits.getSizeInBytes() : content.length();
	  //mode + data length indicator + data content
	  appendModeInfo(mode, headerAndDataBits);
	  appendLengthInfo(numLetters, version, mode, headerAndDataBits);
	  // Put data together into the overall payload
	  headerAndDataBits.appendBitArray(dataBits);
	  
	  return (hints.containsKey(EncodeHintType.QR_ShuffleCodeword))?
			  prepareQRCode(headerAndDataBits,version,ecLevel,mode,true):
			  prepareQRCode(headerAndDataBits,version,ecLevel,mode,false);
  }
  
  public static QRCode encode(byte[] array,
		  String fileType,
          ErrorCorrectionLevel ecLevel,
          Map<EncodeHintType,?> hints) throws WriterException {
	  Mode mode=Mode.BinaryBYTE;
	  // Step 2: Build another bit vector that contains header and data.
	  BitArray headerAndDataBits = new BitArray();
	  //If it is structure append, add the structure append data first,
	  if(hints.containsKey(EncodeHintType.QR_StructureAppend)){
		  byte[] appendHeader=(byte[]) hints.get(EncodeHintType.QR_StructureAppend);
		  if(appendHeader !=null && appendHeader.length ==2){
			  headerAndDataBits.appendBits(Mode.STRUCTURED_APPEND.getBits(), 4);
			  headerAndDataBits.appendBits(appendHeader[0], 8);
			  headerAndDataBits.appendBits(appendHeader[1], 8);
		  }		  
	  }
	  //append the file type of the data, ECI must be added before mode
	  fileTypeECI type = (fileType == null)? null:fileTypeECI.getfileTypeECIByName(fileType); 
	  if(type != null) {
		  headerAndDataBits.appendBits(Mode.ECI.getBits(), 4);
		  //This is correct for values up to 127, which is all we need now.
		  headerAndDataBits.appendBits(type.getValue(), 8);
	  }
	
	  BitArray dataBits = new BitArray();
	  for (byte b : array)
		  dataBits.appendBits(b, 8);
	  //Find out a proper version of QR code
	  Version version = chooseVersion(dataBits.getSize(), ecLevel, mode, headerAndDataBits.getSize());
	  int numLetters = dataBits.getSizeInBytes();
	  //mode + data length indicator + data content
	  appendModeInfo(mode, headerAndDataBits);
	  appendLengthInfo(numLetters, version, mode, headerAndDataBits);
	  headerAndDataBits.appendBitArray(dataBits);
	  
	  return (hints.containsKey(EncodeHintType.QR_ShuffleCodeword))?
			  prepareQRCode(headerAndDataBits,version,ecLevel,mode,true):
			  prepareQRCode(headerAndDataBits,version,ecLevel,mode,false);	  
  }
  
  public static QRCode prepareQRCode(BitArray headerAndDataBits, Version version, ErrorCorrectionLevel ecLevel, Mode mode, 
		  boolean isShuffle) throws WriterException{
	  Version.ECBlocks ecBlocks = version.getECBlocksForLevel(ecLevel);
	  int numDataBytes = version.getTotalCodewords() - ecBlocks.getTotalECCodewords();
	  //Terminate the bits properly.
	  terminateBits(numDataBytes, headerAndDataBits);
	  
	  // Interleave data bits with error correction code.
	  BitArray finalBits = interleaveWithECBytes(headerAndDataBits,version.getTotalCodewords()
			  ,numDataBytes,ecBlocks.getNumBlocks());
	  
	  if(isShuffle)
		  finalBits=com.google.zxing.qrcode.decoder.BlockShifting.breakingToBit(finalBits,version,ecLevel);
	  
	  QRCode qrCode = new QRCode();
	  qrCode.setECLevel(ecLevel);
	  if(mode !=null) qrCode.setMode(mode);
	  qrCode.setVersion(version);
	  //  Choose the mask pattern and set to "qrCode".
	  int dimension = version.getDimensionForVersion();
	  ByteMatrix matrix = new ByteMatrix(dimension, dimension);
	  int maskPattern = chooseMaskPattern(finalBits, ecLevel, version, matrix);
	  qrCode.setMaskPattern(maskPattern);

	  // Build the matrix and set it to "qrCode".
	  MatrixUtil.buildMatrix(finalBits, ecLevel, version, maskPattern, matrix);
	  qrCode.setMatrix(matrix);

	  return qrCode;
  }

  /**
   * Inserting both string and binary data using mix mode
   * Data inserted by this method may not be supported on the reader size
   * I have not even tested whether it really work......
   * @author Solon Li
   */
  public static QRCode encode(String content, byte[] array,
			String fileType,
			ErrorCorrectionLevel ecLevel,
			Map<EncodeHintType,?> hints) throws WriterException {
	  //Do step 1-3 for string first
	  String encoding = hints == null ? null : (String) hints.get(EncodeHintType.CHARACTER_SET);
	  if(encoding ==null) encoding = DEFAULT_BYTE_MODE_ENCODING;
	  Mode modeString = chooseMode(content, encoding);
	  BitArray headerAndDataBitsString = new BitArray();
	  if(modeString ==Mode.BYTE && !DEFAULT_BYTE_MODE_ENCODING.equals(encoding)){
		  CharacterSetECI eci = CharacterSetECI.getCharacterSetECIByName(encoding);
		  if (eci != null) appendECI(eci, headerAndDataBitsString);
	  }
	  BitArray dataBitsString = new BitArray();
	  appendBytes(content, modeString, dataBitsString, encoding);
	  
	  //Then do step 1-3 for the binary data
	  Mode modeByte=Mode.BinaryBYTE;
	  BitArray headerAndDataBitsByte = new BitArray();
	  fileTypeECI type = (fileType == null)? null:fileTypeECI.getfileTypeECIByName(fileType); 
	  if(type != null) {
		  headerAndDataBitsByte.appendBits(Mode.ECI.getBits(), 4);
		  headerAndDataBitsByte.appendBits(type.getValue(), 8);
	  }
	  BitArray dataBitsByte = new BitArray();
	  for (byte b : array)
		  dataBitsByte.appendBits(b, 8);
	  
	  //Get a proper version of QR code that can store both of them
	  Version version = chooseVersion(new int[]{dataBitsString.getSize(), dataBitsByte.getSize()}
			  , ecLevel, new Mode[]{modeString, modeByte}, headerAndDataBitsString.getSize()+headerAndDataBitsByte.getSize());
	  
	  //String first
	  int numLetters = modeString == Mode.BYTE ? dataBitsString.getSizeInBytes() : content.length();
	  //mode + data length indicator + data content
	  appendModeInfo(modeString, headerAndDataBitsString);
	  appendLengthInfo(numLetters, version, modeString, headerAndDataBitsString);
	  headerAndDataBitsString.appendBitArray(dataBitsString);
	  //Then byte
	  numLetters = dataBitsByte.getSizeInBytes();
	  appendModeInfo(modeByte, headerAndDataBitsByte);
	  appendLengthInfo(numLetters, version, modeByte, headerAndDataBitsByte);
	  headerAndDataBitsByte.appendBitArray(dataBitsByte);
	  //Then group them together
	  headerAndDataBitsString.appendBitArray(headerAndDataBitsByte);
	  
	  return (hints.containsKey(EncodeHintType.QR_ShuffleCodeword))?
			  prepareQRCode(headerAndDataBitsString,version,ecLevel,null,true):
			  prepareQRCode(headerAndDataBitsString,version,ecLevel,null,false);	  
  }

  /**
   * @return the code point of the table used in alphanumeric mode or
   *  -1 if there is no corresponding code in the table.
   */
  static int getAlphanumericCode(int code) {
    if (code < ALPHANUMERIC_TABLE.length) {
      return ALPHANUMERIC_TABLE[code];
    }
    return -1;
  }

  public static Mode chooseMode(String content) {
    return chooseMode(content, null);
  }

  /**
   * Choose the best mode by examining the content. Note that 'encoding' is used as a hint;
   * if it is Shift_JIS, and the input is only double-byte Kanji, then we return {@link Mode#KANJI}.
   */
  public static Mode chooseMode(String content, String encoding) {
    if ("Shift_JIS".equals(encoding)) {
      // Choose Kanji mode if all input are double-byte characters
      return isOnlyDoubleByteKanji(content) ? Mode.KANJI : Mode.BYTE;
    }
    boolean hasNumeric = false;
    boolean hasAlphanumeric = false;
    for (int i = 0; i < content.length(); ++i) {
      char c = content.charAt(i);
      if (c >= '0' && c <= '9') {
        hasNumeric = true;
      } else if (getAlphanumericCode(c) != -1) {
        hasAlphanumeric = true;
      } else {
        return Mode.BYTE;
      }
    }
    if (hasAlphanumeric) {
      return Mode.ALPHANUMERIC;
    }
    if (hasNumeric) {
      return Mode.NUMERIC;
    }
    return Mode.BYTE;
  }

  private static boolean isOnlyDoubleByteKanji(String content) {
    byte[] bytes;
    try {
      bytes = content.getBytes("Shift_JIS");
    } catch (UnsupportedEncodingException uee) {
      return false;
    }
    int length = bytes.length;
    if (length % 2 != 0) {
      return false;
    }
    for (int i = 0; i < length; i += 2) {
      int byte1 = bytes[i] & 0xFF;
      if ((byte1 < 0x81 || byte1 > 0x9F) && (byte1 < 0xE0 || byte1 > 0xEB)) {
        return false;
      }
    }
    return true;
  }

  private static int chooseMaskPattern(BitArray bits,
                                       ErrorCorrectionLevel ecLevel,
                                       Version version,
                                       ByteMatrix matrix) throws WriterException {

    int minPenalty = Integer.MAX_VALUE;  // Lower penalty is better.
    int bestMaskPattern = -1;
    // We try all mask patterns to choose the best one.
    for (int maskPattern = 0; maskPattern < QRCode.NUM_MASK_PATTERNS; maskPattern++) {
      MatrixUtil.buildMatrix(bits, ecLevel, version, maskPattern, matrix);
      int penalty = calculateMaskPenalty(matrix);
      if (penalty < minPenalty) {
        minPenalty = penalty;
        bestMaskPattern = maskPattern;
      }
    }
    return bestMaskPattern;
  }
  /**
   * Select a proper version of QR code such that it can store the input data
   * @param numInputBits number of bits on input (excluding the mode / length metadata) 
   * @param numberOfHeaderBits number of header bits appended before the input data (mode+length indicator+data) 
   * We lazily ignore the effect of ECI block here
   */
  private static Version chooseVersion(int numInputBits, ErrorCorrectionLevel ecLevel, Mode mode, int numberOfHeaderBits) 
		  throws WriterException {
	  // In the following comments, we use numbers of Version 7-H.
	  for (int versionNum = 1; versionNum <= 40; versionNum++) {
		  Version version = Version.getVersionForNumber(versionNum);
		  // numBytes = 196
		  int numBytes = version.getTotalCodewords();
		  // getNumECBytes = 130
		  Version.ECBlocks ecBlocks = version.getECBlocksForLevel(ecLevel);
		  int numEcBytes = ecBlocks.getTotalECCodewords();
		  // getNumDataBytes = 196 - 130 = 66
		  int numDataBytes = numBytes - numEcBytes;
		  //A stream of data consists of the header bits, mode indicator, data length indicator, and then the data
		  int totalInputBytes = numberOfHeaderBits + 4 + mode.getCharacterCountBits(version) + numInputBits;
		  //Round up to the nearest 8 
		  totalInputBytes = (totalInputBytes + 7) / 8;
		  if (numDataBytes >= totalInputBytes) {
			  return version;
		  }
	  }
	  throw new WriterException("Data too big");
  }
  /** 
   * Select a proper version of QR code such that it can store the input data
   * This method is prepared for mixed mode (i.e. the QR code stores more than one type of data
   * The number of bits in numInputBits and the mode in mode should match 
   */
  private static Version chooseVersion(int[] numInputBits, ErrorCorrectionLevel ecLevel, Mode[] mode, int numberOfHeaderBits)
		  throws WriterException {
	  if(numInputBits.length<1 || numInputBits.length !=mode.length) return null;
	  // In the following comments, we use numbers of Version 7-H.
	  for (int versionNum = 1; versionNum <= 40; versionNum++) {
		  Version version = Version.getVersionForNumber(versionNum);
		  // numBytes = 196
		  int numBytes = version.getTotalCodewords();
		  // getNumECBytes = 130
		  Version.ECBlocks ecBlocks = version.getECBlocksForLevel(ecLevel);
		  int numEcBytes = ecBlocks.getTotalECCodewords();
		  // getNumDataBytes = 196 - 130 = 66
		  int numDataBytes = numBytes - numEcBytes;
		  int totalInputBytes = 0;
		  for(int i=0;i<numInputBits.length;i++)
			  totalInputBytes += (4 + mode[i].getCharacterCountBits(version) + numInputBits[i]);
		  totalInputBytes = (numberOfHeaderBits + totalInputBytes + 7) / 8;
		  
		  if (numDataBytes >= totalInputBytes) return version;
	  }
	  throw new WriterException("Data too big");
  }

  /**
   * Terminate bits as described in 8.4.8 and 8.4.9 of JISX0510:2004 (p.24).
   */
  static void terminateBits(int numDataBytes, BitArray bits) throws WriterException {
    int capacity = numDataBytes << 3;
    if (bits.getSize() > capacity) {
      throw new WriterException("data bits cannot fit in the QR Code" + bits.getSize() + " > " +
          capacity);
    }
    for (int i = 0; i < 4 && bits.getSize() < capacity; ++i) {
      bits.appendBit(false);
    }
    // Append termination bits. See 8.4.8 of JISX0510:2004 (p.24) for details.
    // If the last byte isn't 8-bit aligned, we'll add padding bits.
    int numBitsInLastByte = bits.getSize() & 0x07;    
    if (numBitsInLastByte > 0) {
      for (int i = numBitsInLastByte; i < 8; i++) {
        bits.appendBit(false);
      }
    }
    // If we have more space, we'll fill the space with padding patterns defined in 8.4.9 (p.24).
    int numPaddingBytes = numDataBytes - bits.getSizeInBytes();
    for (int i = 0; i < numPaddingBytes; ++i) {
      bits.appendBits((i & 0x01) == 0 ? 0xEC : 0x11, 8);
    }
    if (bits.getSize() != capacity) {
      throw new WriterException("Bits size does not equal capacity");
    }
  }

  /**
   * Get number of data bytes and number of error correction bytes for block id "blockID". Store
   * the result in "numDataBytesInBlock", and "numECBytesInBlock". See table 12 in 8.5.1 of
   * JISX0510:2004 (p.30)
   */
  static void getNumDataBytesAndNumECBytesForBlockID(int numTotalBytes,
                                                     int numDataBytes,
                                                     int numRSBlocks,
                                                     int blockID,
                                                     int[] numDataBytesInBlock,
                                                     int[] numECBytesInBlock) throws WriterException {
    if (blockID >= numRSBlocks) {
      throw new WriterException("Block ID too large");
    }
    // numRsBlocksInGroup2 = 196 % 5 = 1
    int numRsBlocksInGroup2 = numTotalBytes % numRSBlocks;
    // numRsBlocksInGroup1 = 5 - 1 = 4
    int numRsBlocksInGroup1 = numRSBlocks - numRsBlocksInGroup2;
    // numTotalBytesInGroup1 = 196 / 5 = 39
    int numTotalBytesInGroup1 = numTotalBytes / numRSBlocks;
    // numTotalBytesInGroup2 = 39 + 1 = 40
    int numTotalBytesInGroup2 = numTotalBytesInGroup1 + 1;
    // numDataBytesInGroup1 = 66 / 5 = 13
    int numDataBytesInGroup1 = numDataBytes / numRSBlocks;
    // numDataBytesInGroup2 = 13 + 1 = 14
    int numDataBytesInGroup2 = numDataBytesInGroup1 + 1;
    // numEcBytesInGroup1 = 39 - 13 = 26
    int numEcBytesInGroup1 = numTotalBytesInGroup1 - numDataBytesInGroup1;
    // numEcBytesInGroup2 = 40 - 14 = 26
    int numEcBytesInGroup2 = numTotalBytesInGroup2 - numDataBytesInGroup2;
    // Sanity checks.
    // 26 = 26
    if (numEcBytesInGroup1 != numEcBytesInGroup2) {
      throw new WriterException("EC bytes mismatch");
    }
    // 5 = 4 + 1.
    if (numRSBlocks != numRsBlocksInGroup1 + numRsBlocksInGroup2) {
      throw new WriterException("RS blocks mismatch");
    }
    // 196 = (13 + 26) * 4 + (14 + 26) * 1
    if (numTotalBytes !=
        ((numDataBytesInGroup1 + numEcBytesInGroup1) *
            numRsBlocksInGroup1) +
            ((numDataBytesInGroup2 + numEcBytesInGroup2) *
                numRsBlocksInGroup2)) {
      throw new WriterException("Total bytes mismatch");
    }

    if (blockID < numRsBlocksInGroup1) {
      numDataBytesInBlock[0] = numDataBytesInGroup1;
      numECBytesInBlock[0] = numEcBytesInGroup1;
    } else {
      numDataBytesInBlock[0] = numDataBytesInGroup2;
      numECBytesInBlock[0] = numEcBytesInGroup2;
    }
  }

  /**
   * Interleave "bits" with corresponding error correction bytes. On success, store the result in
   * "result". The interleave rule is complicated. See 8.6 of JISX0510:2004 (p.37) for details.
   */
  static BitArray interleaveWithECBytes(BitArray bits,
                                    int numTotalBytes,
                                    int numDataBytes,
                                    int numRSBlocks) throws WriterException {

    // "bits" must have "getNumDataBytes" bytes of data.
    if (bits.getSizeInBytes() != numDataBytes) {
      throw new WriterException("Number of bits and data bytes does not match");
    }

    // Step 1.  Divide data bytes into blocks and generate error correction bytes for them. We'll
    // store the divided data bytes blocks and error correction bytes blocks into "blocks".
    int dataBytesOffset = 0;
    int maxNumDataBytes = 0;
    int maxNumEcBytes = 0;

    // Since, we know the number of reedsolmon blocks, we can initialize the vector with the number.
    Collection<BlockPair> blocks = new ArrayList<BlockPair>(numRSBlocks);

    for (int i = 0; i < numRSBlocks; ++i) {
      int[] numDataBytesInBlock = new int[1];
      int[] numEcBytesInBlock = new int[1];
      getNumDataBytesAndNumECBytesForBlockID(
          numTotalBytes, numDataBytes, numRSBlocks, i,
          numDataBytesInBlock, numEcBytesInBlock);

      int size = numDataBytesInBlock[0];
      byte[] dataBytes = new byte[size];
      bits.toBytes(8*dataBytesOffset, dataBytes, 0, size);
      byte[] ecBytes = generateECBytes(dataBytes, numEcBytesInBlock[0]);
      blocks.add(new BlockPair(dataBytes, ecBytes));

      maxNumDataBytes = Math.max(maxNumDataBytes, size);
      maxNumEcBytes = Math.max(maxNumEcBytes, ecBytes.length);
      dataBytesOffset += numDataBytesInBlock[0];
    }
    if (numDataBytes != dataBytesOffset) {
      throw new WriterException("Data bytes does not match offset");
    }

    BitArray result = new BitArray();
    
    // First, place data blocks.
    for (int i = 0; i < maxNumDataBytes; ++i) {
      for (BlockPair block : blocks) {
        byte[] dataBytes = block.getDataBytes();
        if (i < dataBytes.length) {
          result.appendBits(dataBytes[i], 8);
        }
      }
    }
    // Then, place error correction blocks.
    for (int i = 0; i < maxNumEcBytes; ++i) {
      for (BlockPair block : blocks) {
        byte[] ecBytes = block.getErrorCorrectionBytes();
        if (i < ecBytes.length) {
          result.appendBits(ecBytes[i], 8);
        }
      }
    }
    if (numTotalBytes != result.getSizeInBytes()) {  // Should be same.
      throw new WriterException("Interleaving error: " + numTotalBytes + " and " +
          result.getSizeInBytes() + " differ.");
    }
    
    return result;
  }

  static byte[] generateECBytes(byte[] dataBytes, int numEcBytesInBlock) {
    int numDataBytes = dataBytes.length;
    int[] toEncode = new int[numDataBytes + numEcBytesInBlock];
    for (int i = 0; i < numDataBytes; i++) {
      toEncode[i] = dataBytes[i] & 0xFF;
    }
    new ReedSolomonEncoder(GenericGF.QR_CODE_FIELD_256).encode(toEncode, numEcBytesInBlock);

    byte[] ecBytes = new byte[numEcBytesInBlock];
    for (int i = 0; i < numEcBytesInBlock; i++) {
      ecBytes[i] = (byte) toEncode[numDataBytes + i];
    }
    return ecBytes;
  }

  /**
   * Append mode info. On success, store the result in "bits".
   */
  static void appendModeInfo(Mode mode, BitArray bits) {
    bits.appendBits(mode.getBits(), 4);
  }


  /**
   * Append length info. On success, store the result in "bits".
   */
  static void appendLengthInfo(int numLetters, Version version, Mode mode, BitArray bits)
      throws WriterException {
    int numBits = mode.getCharacterCountBits(version);
    if (numLetters >= (1 << numBits)) {
      throw new WriterException(numLetters + "is bigger than" + ((1 << numBits) - 1));
    }
    bits.appendBits(numLetters, numBits);
  }

  /**
   * Append "bytes" in "mode" mode (encoding) into "bits". On success, store the result in "bits".
   */
  public static void appendBytes(String content,
                          Mode mode,
                          BitArray bits,
                          String encoding) throws WriterException {
    switch (mode) {
      case NUMERIC:
        appendNumericBytes(content, bits);
        break;
      case ALPHANUMERIC:
        appendAlphanumericBytes(content, bits);
        break;
      case BYTE:
        append8BitBytes(content, bits, encoding);
        break;
      case KANJI:
        appendKanjiBytes(content, bits);
        break;
      default:
        throw new WriterException("Invalid mode: " + mode);
    }
  }

  static void appendNumericBytes(CharSequence content, BitArray bits) {
    int length = content.length();
    int i = 0;
    while (i < length) {
      int num1 = content.charAt(i) - '0';
      if (i + 2 < length) {
        // Encode three numeric letters in ten bits.
        int num2 = content.charAt(i + 1) - '0';
        int num3 = content.charAt(i + 2) - '0';
        bits.appendBits(num1 * 100 + num2 * 10 + num3, 10);
        i += 3;
      } else if (i + 1 < length) {
        // Encode two numeric letters in seven bits.
        int num2 = content.charAt(i + 1) - '0';
        bits.appendBits(num1 * 10 + num2, 7);
        i += 2;
      } else {
        // Encode one numeric letter in four bits.
        bits.appendBits(num1, 4);
        i++;
      }
    }
  }

  static void appendAlphanumericBytes(CharSequence content, BitArray bits) throws WriterException {
    int length = content.length();
    int i = 0;
    while (i < length) {
      int code1 = getAlphanumericCode(content.charAt(i));
      if (code1 == -1) {
        throw new WriterException();
      }
      if (i + 1 < length) {
        int code2 = getAlphanumericCode(content.charAt(i + 1));
        if (code2 == -1) {
          throw new WriterException();
        }
        // Encode two alphanumeric letters in 11 bits.
        bits.appendBits(code1 * 45 + code2, 11);
        i += 2;
      } else {
        // Encode one alphanumeric letter in six bits.
        bits.appendBits(code1, 6);
        i++;
      }
    }
  }

  static void append8BitBytes(String content, BitArray bits, String encoding)
      throws WriterException {
    byte[] bytes;
    try {
      bytes = content.getBytes(encoding);
    } catch (UnsupportedEncodingException uee) {
      throw new WriterException(uee.toString());
    }
    for (byte b : bytes) {
      bits.appendBits(b, 8);
    }
  }

  static void appendKanjiBytes(String content, BitArray bits) throws WriterException {
    byte[] bytes;
    try {
      bytes = content.getBytes("Shift_JIS");
    } catch (UnsupportedEncodingException uee) {
      throw new WriterException(uee.toString());
    }
    int length = bytes.length;
    for (int i = 0; i < length; i += 2) {
      int byte1 = bytes[i] & 0xFF;
      int byte2 = bytes[i + 1] & 0xFF;
      int code = (byte1 << 8) | byte2;
      int subtracted = -1;
      if (code >= 0x8140 && code <= 0x9ffc) {
        subtracted = code - 0x8140;
      } else if (code >= 0xe040 && code <= 0xebbf) {
        subtracted = code - 0xc140;
      }
      if (subtracted == -1) {
        throw new WriterException("Invalid byte sequence");
      }
      int encoded = ((subtracted >> 8) * 0xc0) + (subtracted & 0xff);
      bits.appendBits(encoded, 13);
    }
  }

  private static void appendECI(CharacterSetECI eci, BitArray bits) {
    bits.appendBits(Mode.ECI.getBits(), 4);
    // This is correct for values up to 127, which is all we need now.
    bits.appendBits(eci.getValue(), 8);
  }

}
