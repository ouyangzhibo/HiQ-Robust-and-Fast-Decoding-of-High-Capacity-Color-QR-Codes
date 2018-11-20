/* Copyright 2012
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

/**
 * @author solon li
 */
public final class BinaryParsedResult extends ParsedResult {

  private final String dataType;
  private final List<byte[]> dataByte;

  public BinaryParsedResult(String type, List<byte[]> data) {
    super(ParsedResultType.BINARY);
    dataType=type;
    dataByte=data;
  }

  @Override
  public String getFileType() {
    return dataType;
  }
  
  @Override
  public byte[] getFileByte(){
	  return (dataByte.isEmpty())? null:dataByte.get(0);
  }

  @Override
  public String getDisplayResult() {
    StringBuilder result = new StringBuilder(30);
    maybeAppend("Binary data with type:", result);
    maybeAppend(dataType, result);
    return result.toString();
  }

}

