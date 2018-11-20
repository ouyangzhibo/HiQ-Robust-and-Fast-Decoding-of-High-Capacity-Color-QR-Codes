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
 */

package com.google.zxing.qrcode.encoder;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Mode;
import com.google.zxing.qrcode.decoder.Version;

/**
 * @author satorux@google.com (Satoru Takabayashi) - creator
 * @author dswitkin@google.com (Daniel Switkin) - ported from C++
 */
public final class QRCode {

  public static final int NUM_MASK_PATTERNS = 8;

  private Mode mode=null;
  private ErrorCorrectionLevel ecLevel=null;
  private Version version=null;
  private int maskPattern;
  private ByteMatrix matrix=null;

  public QRCode() {
    maskPattern = -1;
  }

  // Mode of the QR Code.
  public Mode getMode() {
    return mode;
  }

  // Error correction level of the QR Code.
  public ErrorCorrectionLevel getECLevel() {
    return ecLevel;
  }

  // Version of the QR Code.  The bigger size, the bigger version.
  public Version getVersion() {
    return version;
  }

  // Mask pattern of the QR Code.
  public int getMaskPattern() {
    return maskPattern;
  }

  // ByteMatrix data of the QR Code.
  public ByteMatrix getMatrix() {
    return matrix;
  }
    
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder(200);
    result.append("<<\n");
    result.append(" mode: ");
    result.append(mode);
    result.append("\n ecLevel: ");
    result.append(ecLevel);
    result.append("\n version: ");
    result.append(version);
    result.append("\n maskPattern: ");
    result.append(maskPattern);
    if (matrix == null) {
      result.append("\n matrix: null\n");
    } else {
      result.append("\n matrix:\n");
      result.append(matrix.toString());
    }
    result.append(">>\n");
    return result.toString();
  }

  public void setMode(Mode value) {
    mode = value;
  }

  public void setECLevel(ErrorCorrectionLevel value) {
    ecLevel = value;
  }

  public void setVersion(Version value) {
    version = value;
  }

  public void setMaskPattern(int value) {
    maskPattern = value;
  }

  // This takes ownership of the 2D array.
  public void setMatrix(ByteMatrix value) {
    matrix = value;
  }

  // Check if "mask_pattern" is valid.
  public static boolean isValidMaskPattern(int maskPattern) {
    return maskPattern >= 0 && maskPattern < NUM_MASK_PATTERNS;
  }

}
