/*
 Copyright (C) 2014 Solon Li 
 */
package edu.cuhk.ie.authbarcode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.pdf417.PDF417ResultMetadata;
import com.google.zxing.qrcode.decoder.DataBlock;

/**
 * This class serializes a Result object into something can be stored in local storage (serializableResult)
 * The Result object can also be transformed to JSON String so that it can be sent though Internet
 * @author solon li
 *
 */
public class serializableResult implements Serializable{
		/**
	 * 
	 */
	private static final long serialVersionUID = 2748353080255024920L;
		public final String text;
		  public final byte[] rawBytes;
		  public final BarcodeFormat format;
		  //public Map<ResultMetadataType,Object> resultMetadata;
		  private Map<ResultMetadataType, Integer> resultIntMetadata
		  	=new EnumMap<ResultMetadataType,Integer>(ResultMetadataType.class);
		  private Map<ResultMetadataType, String> resultStringMetadata
		  	=new EnumMap<ResultMetadataType,String>(ResultMetadataType.class);
		  private Map<ResultMetadataType, List<byte[]>> resultByteMetadata
		  	=new EnumMap<ResultMetadataType,List<byte[]>>(ResultMetadataType.class);
		  private PDF417ResultMetadata pdf417ResultMetadata=null;
		  public final DataBlock[] dataBlocks;
		  
		  public serializableResult(Result result){
			  this.text=result.getText();
			  this.rawBytes=result.getRawBytes();
			  this.format=result.getBarcodeFormat();
			  result.copyMetadata(resultIntMetadata, resultStringMetadata, 
					  resultByteMetadata, pdf417ResultMetadata);
			  if(resultIntMetadata.isEmpty()) resultIntMetadata=null;
			  if(resultStringMetadata.isEmpty()) resultStringMetadata=null;
			  if(resultByteMetadata.isEmpty()) resultByteMetadata=null;
			  dataBlocks=(result.getDataBlocks() !=null)? result.getDataBlocks() : null;
		  }
		  public static Result getResultFromSerializableResult(serializableResult sResult){
			  Result result = new Result(sResult.text, sResult.rawBytes, null, 
					  sResult.format,(sResult.dataBlocks !=null)? sResult.dataBlocks:null);			  
			  //result.putAllMetadata(sResult.resultMetadata);
			  result.putAllIntMetadata(sResult.resultIntMetadata);
			  result.putAllStringMetadata(sResult.resultStringMetadata);
			  result.putAllByteMetadata(sResult.resultByteMetadata);
			  result.putMetadata(sResult.pdf417ResultMetadata);
			  return result;
		  }
		  public static String toJSONString(serializableResult sResult){			  
			  JSONObject inputs=new JSONObject();
			  //Input the basic data first
			  try{
				  inputs.putOpt("text", sResult.text);
				  inputs.putOpt("format", sResult.format.ordinal());
				  if(sResult.rawBytes !=null) 
					  inputs.putOpt("rawBytes", AuthBarcodePlainText.base64Encode(sResult.rawBytes));
				  DataBlock[] blocks=sResult.dataBlocks;
				  if(blocks !=null && blocks.length>0){
					  JSONObject blockJSON=new JSONObject();
					  blockJSON.put("total", blocks.length);
					  for(int i=0,l=blocks.length;i<l;i++){
						  DataBlock block=blocks[i];
						  blockJSON.putOpt("num"+i, block.getNumDataCodewords());
						  blockJSON.putOpt("byte"+i, AuthBarcodePlainText.base64Encode(block.getCodewords()));
					  }
					  inputs.putOpt("blocks", blockJSON);
				  }
				  
			  }catch(JSONException e){
				  //Log.d("serializableResult","Cannot add basicInfo");
				  return null;
			  }
			  //Input the int metadata
			  try{
				  Map<ResultMetadataType, Integer> intMet=sResult.resultIntMetadata;
				  if(intMet !=null && !intMet.isEmpty()){
					  Iterator<ResultMetadataType> iter=intMet.keySet().iterator();			  
					  JSONArray metadata=new JSONArray();
					  while(iter.hasNext()){
						  ResultMetadataType type=iter.next();
						  metadata.put(type.ordinal(), intMet.get(type).intValue());
					  }
					  inputs.putOpt("intMet", metadata);
				  }
			  }catch(JSONException e){
				  //Log.d("serializableResult","Cannot add intMet");
				  return null;
			  }
			  //Input the string metadata
			  try{
				  Map<ResultMetadataType, String> strMet=sResult.resultStringMetadata;
				  if(strMet !=null && !strMet.isEmpty()){
					  Iterator<ResultMetadataType> iter=strMet.keySet().iterator();			  
					  JSONArray metadata=new JSONArray();
					  while(iter.hasNext()){
						  ResultMetadataType type=iter.next();
						  metadata.put(type.ordinal(), strMet.get(type));
					  }
					  inputs.putOpt("strMet", metadata);
				  }
			  }catch(JSONException e){
				  //Log.d("serializableResult","Cannot add strMet");
				  return null;
			  }
			  //Input the byte metadata
			  try{
				  Map<ResultMetadataType, List<byte[]>> byteMet=sResult.resultByteMetadata;
				  if(byteMet !=null && !byteMet.isEmpty()){
					  Iterator<ResultMetadataType> iter=byteMet.keySet().iterator();			  
					  JSONArray metadata=new JSONArray();
					  while(iter.hasNext()){
						  ResultMetadataType type=iter.next();
						  List<byte[]> bytes=byteMet.get(type);
						  if(bytes !=null){
							  JSONArray byteList=new JSONArray();
							  for(int i=0,l=bytes.size();i<l;i++){
								  byteList.put(i, AuthBarcodePlainText.base64Encode(bytes.get(i)));
							  }
							  metadata.put(type.ordinal(), byteList);
						  }
					  }
					  inputs.putOpt("byteMet", metadata);
				  }
			  }catch(JSONException e){
				  //Log.d("serializableResult","Cannot add byteMet");
				  return null;
			  }
			  //Input the pdf417Metadata
			  try{
				  PDF417ResultMetadata pdf=sResult.pdf417ResultMetadata;
				  if(pdf !=null){
					  JSONObject pdfMeta=new JSONObject();
					  pdfMeta.putOpt("segmentIndex",pdf.getSegmentIndex());
					  pdfMeta.putOpt("fileID",pdf.getFileId());
					  pdfMeta.putOpt("lastSeg",pdf.isLastSegment());
					  int[] optionalData=pdf.getOptionalData();
					  JSONArray metadata=new JSONArray();
					  for(int i=0,l=optionalData.length;i<l;i++){
						  metadata.put(i, optionalData[i]);
					  }
					  pdfMeta.putOpt("optionData", metadata);
					  inputs.putOpt("pdfMeta", pdfMeta);
				  }
			  }catch(JSONException e2){
				  //Log.d("serializableResult","Cannot add pdf417Met");
				  return null;
			  }
			  return inputs.toString();
		  }
		  public static serializableResult fromJSONString(String str){
			  try{
				  JSONObject inputs=new JSONObject(str);
				  Result result=null;
				  //Basic Info first
				  try{
					  String text=inputs.getString("text");
					  int formatInt=inputs.getInt("format");
					  if(formatInt >=BarcodeFormat.values().length){
						  //Log.d("serializableResult","Wrong format");
						  return null;
					  }
					  byte[] rawByte=(!inputs.isNull("rawBytes"))? 
							  AuthBarcodePlainText.base64Decode(inputs.getString("rawBytes")) : null;
					  BarcodeFormat format = BarcodeFormat.values()[formatInt];
					  try{
						  JSONObject blockJSON=inputs.getJSONObject("blocks");
						  int totalNumberOfBlocks=blockJSON.getInt("total");
						  DataBlock[] blocks=new DataBlock[totalNumberOfBlocks];
						  for(int i=0,l=blocks.length;i<l;i++){
							  DataBlock block=new DataBlock(blockJSON.getInt("num"+i), 
									  AuthBarcodePlainText.base64Decode(blockJSON.getString("byte"+i)));
							  blocks[i]=block;
						  }
						  result = new Result(text,rawByte,null,format,blocks);
					  }catch(JSONException e){ }//It is OK not having datablocks
					  if(result ==null) result = new Result(text,rawByte,null,format,null);
				  }catch(JSONException e){
					  //Log.d("serializableResult","Cannot get Basic info");
					  return null;
				  }
				  //Then intResultMetadata
				  try{
					  JSONArray metadata=inputs.getJSONArray("intMet");					  
					  ResultMetadataType[] ordinals=ResultMetadataType.values();	
					  for(int i=0,l=ordinals.length;i<l;i++){
						  if(!metadata.isNull(i)) result.putMetadata(ordinals[i], metadata.getInt(i));
					  }					  
				  }catch(JSONException e){
					  //It is OK not having int metadata
					 // Log.d("serializableResult","Cannot read int metadata");
				  }
				  //Then strResultMetadata
				  try{
					  JSONArray metadata=inputs.getJSONArray("strMet");
					  ResultMetadataType[] ordinals=ResultMetadataType.values();	
					  for(int i=0,l=ordinals.length;i<l;i++){
						  if(!metadata.isNull(i)) result.putMetadata(ordinals[i], metadata.getString(i));
					  }					  
				  }catch(JSONException e){
					  //It is OK not having str metadata
					  //Log.d("serializableResult","Cannot read str metadata");
				  }
				  //Then byteMetadata
				  try{
					  JSONArray metadata=inputs.getJSONArray("byteMet");
					  ResultMetadataType[] ordinals=ResultMetadataType.values();	
					  for(int i=0,l=ordinals.length;i<l;i++){
						  if(!metadata.isNull(i)){
							  JSONArray byteList=metadata.getJSONArray(i);
							  List<byte[]> bytes=new ArrayList<byte[]>();
							  for(int a=0,b=byteList.length();a<b;a++){
								  bytes.add(a, AuthBarcodePlainText.base64Decode(byteList.getString(a)));
							  }
							  if(!bytes.isEmpty()) result.putMetadata(ordinals[i], bytes); 								  
						  }
					  }					  
				  }catch(JSONException e){
					  //It is OK not having byte metadata
					  //Log.d("serializableResult","Cannot read byte metadata");
				  }
				  //Then pdfMetadata
				  try{
					  JSONObject pdfMeta=inputs.getJSONObject("pdfMeta");
					  PDF417ResultMetadata pdf417ResultMetadata = new PDF417ResultMetadata();
					  pdf417ResultMetadata.setSegmentIndex(pdfMeta.getInt("segmentIndex"));
					  pdf417ResultMetadata.setFileId(pdfMeta.getString("fileID"));
					  pdf417ResultMetadata.setLastSegment(pdfMeta.getBoolean("fileID"));
					  JSONArray metadata=pdfMeta.getJSONArray("optionData");
					  int[] optionalData=new int[metadata.length()];
					  for(int i=0,l=metadata.length();i<l;i++){
						  optionalData[i]=metadata.getInt(i);
					  }
					  pdf417ResultMetadata.setOptionalData(optionalData);
					  if(pdf417ResultMetadata !=null) result.putMetadata(pdf417ResultMetadata);					  
				  }catch(JSONException e){
					  //It is OK not having pdf metadata
					  //Log.d("serializableResult","Cannot read pdf metadata");
				  }
				  //Log.d("serializableResult","Ready to return");
				  return new serializableResult(result);
			  }catch(JSONException e){
				  //Log.d("serializableResult","Cannot create JSON Object from str");
				  return null;
			  }
		  }
	}	