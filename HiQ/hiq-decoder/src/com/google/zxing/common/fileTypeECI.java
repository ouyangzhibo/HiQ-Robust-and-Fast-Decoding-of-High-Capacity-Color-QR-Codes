/*
 * Copyright 2012 Solon
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

package com.google.zxing.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates a file type set 
 * This is modified from the CharacterSetECI.java. 
 * Notice that on the reader side, this information is treated as CharacterSetECI before the mode is known for the reader 
 *
 * @author Solon Li
 */
public enum fileTypeECI {

  // Enum name is a Java encoding valid for java.lang and java.io
  
	//0-3 may have problem when this is read as CharacterSetECI
	jpeg(4, "image/jpeg"),
	png(5, "image/png"),
	auth2dbarcode(6, "multipart/auth2dbarcode");
	/*
	Cp437(new int[]{0,2}),
  ISO8859_1(new int[]{1,3}, "ISO-8859-1"),
  ISO8859_2(4, "ISO-8859-2"),
  ISO8859_3(5, "ISO-8859-3"),
  */

  private static final Map<Integer,fileTypeECI> VALUE_TO_ECI = new HashMap<Integer,fileTypeECI>();
  private static final Map<String,fileTypeECI> NAME_TO_ECI = new HashMap<String,fileTypeECI>();
  static {
    for (fileTypeECI eci : values()) {
      for (int value : eci.values) {
        VALUE_TO_ECI.put(value, eci);
      }
      NAME_TO_ECI.put(eci.name(), eci);
      for (String name : eci.otherEncodingNames) {
        NAME_TO_ECI.put(name, eci);
      }
    }
  }

  private final int[] values;
  private final String[] otherEncodingNames;

  fileTypeECI(int value) {
    this(new int[] {value});
  }
  
  fileTypeECI(int value, String... otherEncodingNames) {
    this.values = new int[] {value};
    this.otherEncodingNames = otherEncodingNames;
  }

  fileTypeECI(int[] values, String... otherEncodingNames) {
    this.values = values;
    this.otherEncodingNames = otherEncodingNames;
  }

  public int getValue() {
    return values[0];
  }
  
  public String getOtherName(){
	  
	  return (otherEncodingNames.length>0)? otherEncodingNames[0]:this.name();
  }

  /**
   * @param value fileTypeECI value
   * @return fileTypeECI representing ECI of given value, or null if it is legal but
   *   unsupported
   * @throws IllegalArgumentException if ECI value is invalid
   */
  public static fileTypeECI getfileTypeECIByValue(int value) {
    if (value < 0 || value >= 900) {
      throw new IllegalArgumentException("Bad ECI value: " + value);
    }
    return VALUE_TO_ECI.get(value);
  }

  /**
   * @param name fileTypeECI encoding name
   * @return CharacterSetECI representing ECI for character encoding, or null if it is legal
   *   but unsupported
   */
  public static fileTypeECI getfileTypeECIByName(String name) {
    return NAME_TO_ECI.get(name); 
  }

}
