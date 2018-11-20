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

public class Auth2DBarcodeParsedResult extends ParsedResult {

	 private final String dataString;
	 private final List<byte[]> dataByte;
	 
	 public Auth2DBarcodeParsedResult(String data) {
		    super(ParsedResultType.AUTH2DBARCODE);
		    dataString=data;
		    dataByte=null;
		  }
	 public Auth2DBarcodeParsedResult(List<byte[]> data) {
		    super(ParsedResultType.AUTH2DBARCODE);
		    dataString=null;
		    dataByte=data;
		  }
		  @Override
		  public String getFileType() {
		    if(dataString != null) return String.class.getSimpleName();
		    if(dataByte != null && !dataByte.isEmpty()) return Byte.class.getSimpleName();
		    return null;
		  }
		  
		  @Override
		  public byte[] getFileByte(){
			  return (dataByte==null || dataByte.isEmpty())? null:dataByte.get(0);
		  }
		  @Override
		  public String getFileString(){
			  return dataString;
		  }

		  @Override
		  public String getDisplayResult() {
		    if(dataString != null) return dataString;
		    else if(dataByte != null && !dataByte.isEmpty()) return Byte.class.getSimpleName();
		    else return null;
		  }
}
