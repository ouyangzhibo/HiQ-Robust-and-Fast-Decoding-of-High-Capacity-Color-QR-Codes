/*
 * Copyright 2015 Solon and Elky
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

import java.util.Map;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitArray;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Mode;
import com.google.zxing.qrcode.decoder.Version;

/**
 * Created to build QR code with bigger alignment patterns, for easier detection and higher decoding rate
 * @author Elky creator
 * @author solon li putting it into a separate class
 *
 */

public final class EncoderLargerAlign{
	
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
		QRCode qrCode=Encoder.encode(content, ecLevel, hints);
		return modifyAlignPatterns(qrCode);
	}
	public static QRCode encode(byte[] array,
			  String fileType,
	          ErrorCorrectionLevel ecLevel,
	          Map<EncodeHintType,?> hints) throws WriterException {
		QRCode qrCode=Encoder.encode(array, fileType, ecLevel, hints);
		return modifyAlignPatterns(qrCode);
	}
	public static QRCode encode(String content, byte[] array,
			String fileType,
			ErrorCorrectionLevel ecLevel,
			Map<EncodeHintType,?> hints) throws WriterException{
		QRCode qrCode=Encoder.encode(content, array, fileType, ecLevel, hints);
		return modifyAlignPatterns(qrCode);
	}
	
	public static QRCode modifyAlignPatterns(QRCode qrCode) throws WriterException{
		if(qrCode ==null) return null;
		ByteMatrix matrix=qrCode.getMatrix();
		Version version=qrCode.getVersion();
		int dimension=matrix.getWidth();
		// rewrite the bigger inside alignment patterns after all the data bits have been appended. This is just for quickly test the improvement of bigger 
		// alignment patterns 
		//MatrixUtil.rewriteFinderPattern(matrix);
		//only use the bigger bottom right alignment pattern when the version is bigger than 20
		if(version.getVersionNumber() > 20){
			MatrixUtilLargerAlign.rewriteBRAlignmentPattern(matrix,dimension);
		}
		MatrixUtilLargerAlign.rewriteAlignmentPattern(matrix,version);
		qrCode.setMatrix(matrix);
		return qrCode;		
	}
	
}