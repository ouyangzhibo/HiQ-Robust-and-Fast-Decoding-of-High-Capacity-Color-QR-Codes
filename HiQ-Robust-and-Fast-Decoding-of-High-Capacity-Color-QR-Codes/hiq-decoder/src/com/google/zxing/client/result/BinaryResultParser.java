/*
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

package com.google.zxing.client.result;

import java.util.List;
import java.util.Map;

import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;


/**
 * parse result as Binary data
 * @author solon li
 */
public final class BinaryResultParser extends ResultParser {

  
  //getDisplayResult
  
  @Override
  public BinaryParsedResult parse(Result result) {
    String rawText = result.getText();
    //Map<ResultMetadataType,Object> metadata=result.getResultMetadata();
    List<byte[]> fileByte = result.getByteMetadata(ResultMetadataType.BYTE_SEGMENTS);
    // We specifically handle the odd "binary" scheme here for simplicity
    if (rawText.contains("binary:") && fileByte != null){
    	int textIndex=rawText.lastIndexOf("binary:");
    	if(textIndex <0 || textIndex+7 >=rawText.length()) return null;
    	rawText = rawText.substring(textIndex+7);
    	rawText = rawText.trim();
    	return new BinaryParsedResult(rawText, fileByte);
    }
    return null;
  }
}