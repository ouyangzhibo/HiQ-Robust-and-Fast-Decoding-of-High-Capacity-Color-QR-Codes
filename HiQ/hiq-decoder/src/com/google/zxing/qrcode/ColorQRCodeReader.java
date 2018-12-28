package com.google.zxing.qrcode;

import java.util.List;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LogCallback;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.color.RGBColorWrapper;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.decoder.QRCodeDecoderMetaData;
import com.google.zxing.qrcode.detector.AlignmentPattern;
import com.google.zxing.qrcode.detector.DetectorColor;

/**
 * A stand-along reader for color QR code Color QR code are seperated into 3
 * channel images and decode independently.
 * 
 * @author Zhibo Yang
 *
 */

public class ColorQRCodeReader {

	private static final ResultPoint[] NO_POINTS = new ResultPoint[0];
	private final Decoder decoder = new Decoder();
	private RGBColorWrapper colorWrapper;
	private ResultPointCallback resultPointCallback;
	private LogCallback logCallback;
	private float[] white = new float[]{0, 0, 0};
	private int layerNum = 3;//default
	public int dimension = 0;
	
	public ColorQRCodeReader(RGBColorWrapper colorWrapper, int layerNum) {
		// TODO: init colorWrapper
		this.colorWrapper = colorWrapper;
		this.layerNum = layerNum;
	}

	public Result[] decode(Map<DecodeHintType, ?> hints, DetectorResult roughDetectRst)
			throws FormatException, ChecksumException, NotFoundException {
		resultPointCallback = (hints == null) ? null
				: (ResultPointCallback) hints
						.get(DecodeHintType.NEED_RESULT_POINT_CALLBACK);
		if (resultPointCallback != null)
			resultPointCallback.findCodePresent(null, null, null, null);
		logCallback = (hints ==null || !hints.containsKey(DecodeHintType.NEED_LOG_CALLBACK))? 
				null:(LogCallback) hints.get(DecodeHintType.NEED_LOG_CALLBACK);
		if(logCallback !=null) logCallback.LogMsg("Start color decoding",false);
		Result[] lastResults = new Result[layerNum];
		if(hints !=null && hints.containsKey(DecodeHintType.Need_Successful_DataBlocks)){
			lastResults[0] = (Result)hints.get(DecodeHintType.LAYER1_DECODED);
			if(lastResults[0] ==null) lastResults[0]=new Result(null,null,null,null,null);
			lastResults[1] = (Result)hints.get(DecodeHintType.LAYER2_DECODED);
			if(lastResults[1] ==null) lastResults[1]=new Result(null,null,null,null,null);
			if (layerNum >= 3) {
				lastResults[2] = (Result)hints.get(DecodeHintType.LAYER3_DECODED);
				if(lastResults[2] ==null) lastResults[2]=new Result(null,null,null,null,null);
			}
			if (layerNum >= 4) {
				lastResults[3] = (Result)hints.get(DecodeHintType.LAYER4_DECODED);
				if(lastResults[3] ==null) lastResults[3]=new Result(null,null,null,null,null);
			}
		}
		
		DecoderResult[] decoderResultList = new DecoderResult[layerNum];
		Result[] resultList = new Result[layerNum];
		ResultPoint[] points = null;
		BitMatrix rawBits = null;
		DetectorResult detectorResult = null;
		// String errorString="";
		if (hints != null && hints.containsKey(DecodeHintType.PURE_BARCODE)) {
			for(int i=1;i<=layerNum;i++) {
				if(lastResults[i-1] !=null && lastResults[i-1].getRawBytes() !=null){
					decoderResultList[i-1] = null;
					resultList[i-1] = lastResults[i-1];
					continue;
				}
				if(logCallback !=null) logCallback.LogMsg("Start extracting color pure bits channel "+i,true);
				BitMatrix bits = QRCodeReader.extractPureBits(this.colorWrapper.getBlackMatrix());
				if(logCallback !=null) logCallback.LogMsg("Finish extracting, start decoding color pure bits channel "+i,true);
				decoderResultList[i-1] = (lastResults[i-1] !=null)?
					decoder.decode(bits, hints, lastResults[i-1].getDataBlocks())
					: decoder.decode(bits, hints, false);
				if(logCallback !=null) logCallback.LogMsg("Finish decoding pure bits channel "+i,true);
			}
			points = NO_POINTS;
		} else {
			// black-white detector
			detectorResult = (roughDetectRst==null) ? detect(hints) : deepDetect(roughDetectRst);
			if(logCallback !=null) logCallback.LogMsg("Finish detecting color QR code ",true);
			if (detectorResult == null)
				throw NotFoundException.getNotFoundInstance();			
			points = detectorResult.getPoints();
			if (resultPointCallback != null) {
				resultPointCallback.findCodePresent(points[0], points[1],
						points[2],
						(points.length >= 4 && points[3] != null) ? points[3]
								: null);
			}
			// channel image decoding
			if(detectorResult.getChannelBits()!=null) {//new method
				for (int i = 0; i < layerNum; i++) {
					if(lastResults[i] !=null && lastResults[i].getRawBytes() !=null){
						decoderResultList[i] = null;
						resultList[i] = lastResults[i];
						continue;
					}
					if(logCallback !=null) logCallback.LogMsg("Start getting and decoding channel "+i+" from detected code",true);
					if (this.colorWrapper.getChannelHint(i)) {
						try{
							decoderResultList[i] = (lastResults[i] !=null)?
									decoder.decode(detectorResult.getChannelBits(i), 
										hints, lastResults[i].getDataBlocks())
									: decoder.decode(detectorResult.getChannelBits(i), 
										hints, false);
						}catch(FormatException | ChecksumException e){
							decoderResultList[i] = null;
						}
					}else decoderResultList[i] = null;
					if(logCallback !=null) logCallback.LogMsg("Finish getting and decoding channel "+i+" from detected code",true);
				}
			}
		}
		for(int i = 0; i < layerNum; i++) {			
			if(decoderResultList[i] == null) 
				continue;
			rawBits = detectorResult.getChannelBits(i);
			// If the code was mirrored: swap the bottom-left and the top-right
			// points.
			if (decoderResultList[i].getOther() instanceof QRCodeDecoderMetaData) {
				((QRCodeDecoderMetaData) decoderResultList[i].getOther())
						.applyMirroredCorrection(points);
			}
			DecoderResult decoderResult = decoderResultList[i];
			Result result = new Result(decoderResult.getText(),
					decoderResult.getRawBytes(), points, BarcodeFormat.QR_CODE,
					System.currentTimeMillis(), rawBits,decoderResult.getDataBlocks());
			List<byte[]> byteSegments = decoderResult.getByteSegments();
			if (byteSegments != null) {
				result.putMetadata(ResultMetadataType.BYTE_SEGMENTS, byteSegments);
			}
			String ecLevel = decoderResult.getECLevel();
			if (ecLevel != null) {
				result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL,
						ecLevel);
			}
			if (decoderResult.hasStructuredAppend()) {
				result.putMetadata(ResultMetadataType.STRUCTURED_APPEND_SEQUENCE,
						decoderResult.getStructuredAppendSequenceNumber());
				result.putMetadata(ResultMetadataType.STRUCTURED_APPEND_PARITY,
						decoderResult.getStructuredAppendParity());
			}			
			resultList[i] = result;
			if(logCallback !=null) logCallback.LogMsg("Finish packaging result from channel "+i+" for return",true);
		}
		return resultList;
	}
	
	// detect color to gray scale and binary image
	private DetectorResult detect(Map<DecodeHintType, ?> hints)
			throws FormatException, NotFoundException {
		if(logCallback !=null) logCallback.LogMsg("Detect function. Start getting black matrix",false,1);
		BitMatrix bits = colorWrapper.getBlackMatrix();
		if(logCallback !=null) logCallback.LogMsg("Detect function. Finish getting black matrix, Start detecting ",true,1);
		DetectorResult detectorResult  = null;		
		DetectorColor detector = this.colorWrapper != null ? new DetectorColor(bits, this.colorWrapper, layerNum): new DetectorColor(bits);
		try{
			detectorResult = detector.detect(hints);
			if(logCallback !=null) logCallback.LogMsg("Detect function. Finish detecting.",true,1);
		}catch (NotFoundException e){ // nofound exception
			if(logCallback !=null) logCallback.LogMsg("Detect function. Finish detecting but failed.",true,1);
			return null;
		}
		// Check the dimension, if it is wrong. use the correct one to build
		int dimension = detectorResult.getBits().getWidth();		
		int correctDimension = Decoder.checkDimension(detectorResult.getBits());
		if (correctDimension > 0) {
			dimension = correctDimension;
		}
		if(logCallback !=null) logCallback.LogMsg("Detect function. Done checking dimensions, Start redetecting",true,1);
		// detect inner alignment patterns
		DetectorResult detectorResultNew = detector.detect(detectorResult, dimension);
		detectorResult = (detectorResultNew != null) ? detectorResultNew : detectorResult;
		if(logCallback !=null) logCallback.LogMsg("Detect function. Done redetecting, Returning",true,1);
		this.white = detector.getWhite();
		this.dimension = dimension;
		
		return detectorResult;
	}
	
	/**
	 * use the Y channel to do rough detection for monochrome marker
	 * @param bits
	 * @param hints
	 * @return
	 */
	public static DetectorResult roughDetect (BitMatrix bits, Map<DecodeHintType, ?> hints) {
		try {
			return new DetectorColor(bits).detectRough(hints);
		} catch (NotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public DetectorResult deepDetect (DetectorResult roughDetectResult) 
			throws NotFoundException {
		// Check the dimension, if it is wrong. use the correct one to build
		if(logCallback !=null) logCallback.LogMsg("Deep Detect function. Start getting black matrix",false,1);
		BitMatrix bits = colorWrapper.getBlackMatrix();
		if(logCallback !=null) logCallback.LogMsg("Deep Detect function. Finish getting black matrix",true,1);
		DetectorColor detector = this.colorWrapper != null ? new DetectorColor(bits, this.colorWrapper, layerNum): new DetectorColor(bits);

		int dimension = roughDetectResult.getBits().getWidth();
		int correctDimension = Decoder.checkDimension(roughDetectResult.getBits());
		if (correctDimension > 0) {
			dimension = correctDimension;
		}		
		// detect inner alignment patterns
		DetectorResult detectorResult = detector.detect(roughDetectResult, dimension);
		this.dimension = dimension;
		this.white = detector.getWhite();
		
		if (detectorResult == null) 
			detectorResult = roughDetectResult;
		if(logCallback !=null) logCallback.LogMsg("Deep Detect function. Finish redetecting by correct dimension, Returning",true,1);
		
				
		return detectorResult;
	}
	
	public float[] getWhite() {
		return white;
	}
	public int getDimension() {
		return this.dimension;
	}
}
