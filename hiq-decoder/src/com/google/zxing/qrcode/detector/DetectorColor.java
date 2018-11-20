/*
 Copyright (C) 2015 Zhibo Yang and Solon Li 
 */
package com.google.zxing.qrcode.detector;

import java.util.Map;

import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.ReaderException;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.color.RGBColorWrapper;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.GridSampler;
import com.google.zxing.common.PerspectiveTransformGeneral;
import com.google.zxing.qrcode.decoder.Version;


/**
 * Color QR code general detector class extended from zxing Detector class
 * @author zhibo Yang, solon li divides the functions to a separate class
 *
 */
public class DetectorColor extends Detector{

	private RGBColorWrapper colorWrapper = null;
	private float[] white = new float[]{255.0f, 255.0f, 255.0f}; //default white values
//	private static final int[] RED= {1,0,0};
//	private static final int[] GREEN= {0,1,0};
//	private static final int[] BLUE= {0,0,1};
//	private static final int[] CYAN= {0,1,1};
//	private static final int[] MAGENTA= {1,0,1};
//	private static final int[] YELLOW= {1,1,0};
//	private static final int[] BLACK = {0,0,0};
//	private static final int[][] COLORSEQ = {BLUE, GREEN, RED, YELLOW, MAGENTA, CYAN};
	private static final boolean[] RED= {false,true,true};
	private static final boolean[] GREEN= {true,false,true};
	private static final boolean[] BLUE= {true,true,false};
	private static final boolean[] CYAN= {true,false,false};
	private static final boolean[] MAGENTA= {false,true,false};
	private static final boolean[] YELLOW= {false,false,true};
	private static final boolean[] BLACK = {true,true,true};
	private static final boolean[][] COLORSEQ = {BLUE, GREEN, RED, YELLOW, MAGENTA, CYAN};
	private int layerNum = 3;//default
	private float estimatedModuleSize = -1;
	public DetectorColor(BitMatrix image) {
		super(image);
	}

	public DetectorColor(BitMatrix image, RGBColorWrapper colorWrapper, int layerNum) {
		super(image);
		this.layerNum = layerNum;
		this.colorWrapper = colorWrapper;
	}
	
	public DetectorResult detectRough(Map<DecodeHintType,?> hints) throws NotFoundException, FormatException {
		resultPointCallback = (hints ==null || !hints.containsKey(DecodeHintType.NEED_RESULT_POINT_CALLBACK))? 
				null:(ResultPointCallback) hints.get(DecodeHintType.NEED_RESULT_POINT_CALLBACK);
	    FinderPatternInfo info=getFinderPatternInfo(image,hints,resultPointCallback);
//	    FinderPattern topLeft = info.getTopLeft();
//	    this.layerNum = colorWrapper.getNumLayer((int)topLeft.getX(), (int)topLeft.getY());
	    return processFinderPatternInfo(info);
	  }
	
	public DetectorResult detect(PerspectiveTransformGeneral transform,
			FinderPattern[] centers, float moduleSize,
			AlignmentPattern alignmentPattern, int dimension)
			throws NotFoundException, FormatException {

		FinderPatternInfo info = new FinderPatternInfo(centers);
		return processFinderPatternInfoNew(transform, info, moduleSize,
				alignmentPattern, dimension);
	}

	protected final DetectorResult processFinderPatternInfoNew(
			PerspectiveTransformGeneral transform, FinderPatternInfo info,
			float moduleSize, AlignmentPattern alignmentPattern, int dimension)
			throws NotFoundException, FormatException {

		FinderPattern topLeft = info.getTopLeft();
		FinderPattern topRight = info.getTopRight();
		FinderPattern bottomLeft = info.getBottomLeft();
		BitMatrix bits = (transform != null) ? sampleGrid(image, transform,
				dimension) : null;

		if (resultPointCallback != null) {
			resultPointCallback.findCodeBoundLine(topLeft, topRight);
			resultPointCallback.findCodeBoundLine(topLeft, bottomLeft);
			if (alignmentPattern != null) {
				resultPointCallback.foundPossibleResultPoint(alignmentPattern);
				resultPointCallback
						.findCodeBoundLine(topLeft, alignmentPattern);
			}
		}

		ResultPoint[] points = (alignmentPattern == null) ? new ResultPoint[] {
				bottomLeft, topLeft, topRight } : new ResultPoint[] {
				bottomLeft, topLeft, topRight, alignmentPattern };

		// modifed by YZB
		FinderPattern[] patterns = new FinderPattern[] { bottomLeft, topLeft,
				topRight };
		return new DetectorResult(bits, points, patterns, null, moduleSize,
				null);

	}

	public float[] getWhite() {
		return white;
	}
	
	protected final DetectorResult processFinderPatternInfoNew(
			FinderPatternInfo info) throws NotFoundException, FormatException {

		FinderPattern topLeft = info.getTopLeft();
		FinderPattern topRight = info.getTopRight();
		FinderPattern bottomLeft = info.getBottomLeft();
		NotFoundException error = NotFoundException.getNotFoundInstance();
		AlignmentPattern alignmentPattern = null;
		BitMatrix bits = null;
		int dimension = 0, modulesBetweenFPCenters = 0;
		float moduleSize = calculateModuleSize(topLeft, topRight, bottomLeft);
		PerspectiveTransformGeneral transform = null;

		if (moduleSize < 1.0f) {
			error.setErrorMessage(error.getErrorMessage()
					+ "Cannot calculate module size. What we get is:"
					+ moduleSize);
			throw error;
		}
		try {
			dimension = computeDimension(topLeft, topRight, bottomLeft,
					moduleSize);
			Version provisionalVersion = Version
					.getProvisionalVersionForDimension(dimension);
			modulesBetweenFPCenters = provisionalVersion
					.getDimensionForVersion() - 7;
			// Anything above version 1 has an alignment pattern
			int alignementIndex = provisionalVersion
					.getAlignmentPatternCenters().length;
			if (alignementIndex > 0) {
				float blX = bottomLeft.getX(), blY = bottomLeft.getY(), tlX = topLeft
						.getX(), tlY = topLeft.getY(), trX = topRight.getX(), trY = topRight
						.getY();
				// Guess where a "bottom right" finder pattern would have been
				float bottomRightX = trX - tlX + blX;
				float bottomRightY = trY - tlY + blY;
				// Estimate that alignment pattern is closer by 3 modules
				// from "bottom right" to known top left location
				float correctionToTopLeft = 1.0f - 3.0f / (float) modulesBetweenFPCenters;
				int estAlignmentX = (int) (tlX + correctionToTopLeft
						* (bottomRightX - tlX));
				int estAlignmentY = (int) (tlY + correctionToTopLeft
						* (bottomRightY - tlY));

				if (alignementIndex > 5) {
					// For version 28 or above, we need a better way to get the
					// dimension value : using the timing pattern
					// Number of alignment patterns along anti-diagonal,
					// excluding finder patterns
					int diagonalAlignNum = alignementIndex - 2;
					// Number of intervals between alignment/finder patterns
					// along the anti-diagonal
					int alignIntevalNum = alignementIndex - 1;

					AlignmentPattern[] antiDiagonal = new AlignmentPattern[diagonalAlignNum];
					float antiDiaX = (trX - blX) / alignIntevalNum;
					float antiDiaY = (trY - blY) / alignIntevalNum;

					AlignmentPattern[] topLine = new AlignmentPattern[diagonalAlignNum];
					float topLineX = (trX - tlX) / alignIntevalNum;
					float topLineY = (trY - tlY) / alignIntevalNum;
					float topCorrectionX = (blX - tlX) * 3.0f
							/ modulesBetweenFPCenters;
					float topCorrectionY = (blY - tlY) * 3.0f
							/ modulesBetweenFPCenters;

					AlignmentPattern[] leftLine = new AlignmentPattern[diagonalAlignNum];
					float leftLineX = (blX - tlX) / alignIntevalNum;
					float leftLineY = (blY - tlY) / alignIntevalNum;
					float leftCorrectionX = (trX - tlX) * 3.0f
							/ modulesBetweenFPCenters;
					float leftCorrectionY = (trY - tlY) * 3.0f
							/ modulesBetweenFPCenters;

					for (int i = 0; i < diagonalAlignNum; i++) {
						int step = i + 1;
						int antiAlignmentX = (int) (blX + (step * antiDiaX));
						int antiAlignmentY = (int) (blY + (step * antiDiaY));
						int topAlignmentX = (int) (tlX + (step * topLineX) + topCorrectionX);
						int topAlignmentY = (int) (tlY + (step * topLineY) + topCorrectionY);
						int leftAlignmentX = (int) (tlX + (step * leftLineX) + leftCorrectionX);
						int leftAlignmentY = (int) (tlY + (step * leftLineY) + leftCorrectionY);

						try {
							antiDiagonal[i] = findAlignmentInRegion(moduleSize,
									antiAlignmentX, antiAlignmentY, alignRadius);
						} catch (NotFoundException re) {
						}
						try {
							topLine[i] = findAlignmentInRegion(moduleSize,
									topAlignmentX, topAlignmentY, alignRadius);
						} catch (NotFoundException re) {
						}
						try {
							leftLine[i] = findAlignmentInRegion(moduleSize,
									leftAlignmentX, leftAlignmentY, alignRadius);
						} catch (NotFoundException re) {
						}
					}
					try {
						float[] topLineEqu = bestFitLine(topLine);
						if (topLineEqu == null)
							throw new Exception();
						float[] leftLineEqu = bestFitLine(leftLine);
						if (leftLineEqu == null)
							throw new Exception();
						float[] antiDiaEqu = bestFitLine(antiDiagonal);
						if (antiDiaEqu == null)
							throw new Exception();
						;
						// Use line of the alignments to find start and end of
						// the timing patterns and use it to estimate correct
						// dimension
						ResultPoint topLeftP = intersectionPoint(topLineEqu,
								leftLineEqu);
						ResultPoint topRightP = intersectionPoint(topLineEqu,
								antiDiaEqu);
						ResultPoint bottomLeftP = intersectionPoint(antiDiaEqu,
								leftLineEqu);
						int[] topDimensionAndModule = countTimingPattern(
								topLeftP, topRightP);
						int[] leftDimensionAndModule = countTimingPattern(
								topLeftP, bottomLeftP);
						int topDimension = topDimensionAndModule[0] + 13;
						int topModule = topDimensionAndModule[1];
						int leftDimension = leftDimensionAndModule[0] + 13;
						int leftModule = leftDimensionAndModule[1];
						int checkBit = (topDimension & 0x03);
						topDimension = (checkBit == 1) ? topDimension
								: (checkBit == 0) ? topDimension + 1
										: (checkBit == 2) ? topDimension - 1
												: 1000;
						checkBit = (leftDimension & 0x03);
						leftDimension = (checkBit == 1) ? leftDimension
								: (checkBit == 0) ? leftDimension + 1
										: (checkBit == 2) ? leftDimension - 1
												: 1000;
						int newDimension = (topDimension < leftDimension) ? topDimension
								: (leftDimension < 1000) ? leftDimension
										: dimension;
						float newModuleSize = (topDimension < leftDimension) ? topModule
								: (leftDimension < 1000) ? leftModule
										: moduleSize;
						newDimension = (newDimension < 21) ? 21
								: (newDimension > 177) ? 177 : newDimension;

						// Now detect the alignment pattern again using the
						// correct dimension and module size
						try {
							alignmentPattern = findAlignmentInRegion(
									newModuleSize, estAlignmentX,
									estAlignmentY, alignRadius / 2.0f);
						} catch (NotFoundException re) {
							try {
								alignmentPattern = findAlignmentInRegion(
										newModuleSize, estAlignmentX,
										estAlignmentY, alignRadius);
							} catch (NotFoundException re2) {
							}
						}
						if (alignmentPattern != null) {
							// Use the new dimension and module size value only
							// if we can detect an alignment pattern correctly
							dimension = newDimension;
							moduleSize = newModuleSize;
						}
					} catch (Exception e) {
					}
					transform = createTransformGeneral(topLeft, topRight,
							bottomLeft, alignmentPattern, dimension);
					if (transform != null && alignementIndex >= 5)
						alignmentErrorCorrection(
								provisionalVersion.getAlignmentPatternCenters(),
								transform, image);

				} // End of getAlignmentPatternCenters().length > 5 if statement
				if (alignmentPattern == null) {
					try {
						alignmentPattern = findAlignmentInRegion(moduleSize,
								estAlignmentX, estAlignmentY,
								alignRadius / 2.0f);
					} catch (NotFoundException re) {
						// try next round
						try {
							alignmentPattern = findAlignmentInRegion(
									moduleSize, estAlignmentX, estAlignmentY,
									alignRadius);
						} catch (NotFoundException re2) {
							try {
								alignmentPattern = findAlignmentInRegion(
										moduleSize, estAlignmentX,
										estAlignmentY, alignRadius * 2.0f);
							} catch (NotFoundException re3) {
							}
						}
					}
				}
			} // End of alignementIndex > 0 if statement

			if (transform == null)
				transform = createTransformGeneral(topLeft, topRight,
						bottomLeft, alignmentPattern, dimension);
			bits = (transform != null) ? sampleGrid(image, transform, dimension)
					: null;
		} catch (ReaderException e) {
			// The error on finder patterns is too large to build a sample grid,
			// so here we modify the finder pattern and search again
			if (moduleSize < 1.0f)
				error.setErrorMessage(error.getErrorMessage()
						+ " and problem on module size. Detector");
			else if (dimension == 0 || (dimension & 0x03) != 1)
				error.setErrorMessage(error.getErrorMessage()
						+ " and problem on getting dimension. Detector");
			else if (transform == null)
				error.setErrorMessage(error.getErrorMessage()
						+ " or there is something wrong on transform. Detector");
			// Something wrong on transformation.
			else if (bits == null)
				error.setErrorMessage(error.getErrorMessage()
						+ " or there is something wrong on sampling. Detector");
			throw error;
		}

		if (bits == null || dimension == 0 || modulesBetweenFPCenters == 0
				|| moduleSize <= 1) {
			error.setErrorMessage(error.getErrorMessage()
					+ " all we can do is starting the next round. Detector");
			throw error;
		}

		if (resultPointCallback != null) {
			resultPointCallback.findCodeBoundLine(topLeft, topRight);
			resultPointCallback.findCodeBoundLine(topLeft, bottomLeft);
			if (alignmentPattern != null) {
				resultPointCallback.foundPossibleResultPoint(alignmentPattern);
				resultPointCallback
						.findCodeBoundLine(topLeft, alignmentPattern);
			}
		}
		ResultPoint[] points = (alignmentPattern == null) ? new ResultPoint[] {
				bottomLeft, topLeft, topRight } : new ResultPoint[] {
				bottomLeft, topLeft, topRight, alignmentPattern };
		// return new DetectorResult(bits, points);
		// modifed by Zhibo Yang
		FinderPattern[] patterns = new FinderPattern[] { bottomLeft, topLeft,
				topRight };
		return new DetectorResult(bits, points, patterns, null, moduleSize,
				transform);
	}

	/**
	 * calculate all alignment patterns in the high-density codes, return their
	 * center positions
	 * 
	 * @param topLeft
	 * @param topRight
	 * @param bottomLeft
	 * @param length
	 * @param moduleSize
	 * @param versionNum
	 * @param dimension
	 * @return all the alignment patterns in the QR code
	 */
	private AlignmentPattern[] findAllAlignPatterns(FinderPattern topLeft,
			FinderPattern topRight, FinderPattern bottomLeft,
			float moduleSize, int dimension) {
		// compute the scope and the step in x,y directions
		int[] alignCenters = null;
		try {
			alignCenters = Version.getProvisionalVersionForDimension(dimension).getAlignmentPatternCenters();
		} catch (FormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		int length = alignCenters.length;
		
		AlignmentPattern[] alignPtns = new AlignmentPattern[length * length - 4];
		int modulesBetweenFPCenters = dimension - 7;
		float bottomRightX = topRight.getX() - topLeft.getX()
				+ bottomLeft.getX();
		float bottomRightY = topRight.getY() - topLeft.getY()
				+ bottomLeft.getY();

		// Estimate that alignment pattern is closer by 3 modules
		// from "bottom right" to known top left location
		float correctionToTopLeft = 3.0f / (float) modulesBetweenFPCenters;
		int horizonalMarkerX = (int) (topLeft.getX() + correctionToTopLeft
				* (bottomRightX - topLeft.getX()));
		int horizonalMarkerY = (int) (topLeft.getY() + correctionToTopLeft
				* (bottomRightY - topLeft.getY()));
//		int horizonalMarkerX = estAlignmentX, horizonalMarkerY = estAlignmentY;
		int estAlignmentX=0, estAlignmentY=0;
		int count = 0;
		for (int i = 0; i < length; i++) {
			float stepX, stepY, tempX = horizonalMarkerX, tempY = horizonalMarkerY;

			stepX = (topRight.getX() - topLeft.getX()) * (alignCenters[i] - alignCenters[0])
					/ (float) modulesBetweenFPCenters;
			stepY = (topRight.getY() - topLeft.getY()) * (alignCenters[i] - alignCenters[0])
					/ (float) modulesBetweenFPCenters;
			tempX = (int) (horizonalMarkerX + stepX);
			tempY = (int) (horizonalMarkerY + stepY);
			
			for (int j = 0; j < length; j++) {
				// step over finderpatterns and last alignment pattern
				if ((i == 0 && j == 0) 
					|| (i == 0 && j == length - 1)
					|| (i == length - 1 && j == 0)
					|| (i == length - 1 && j == length - 1)) {
					continue; 
				}
				
				stepX = (bottomLeft.getX() - topLeft.getX()) * (alignCenters[j] - alignCenters[0])
						/ (float) modulesBetweenFPCenters;
				stepY = (bottomLeft.getY() - topLeft.getY()) * (alignCenters[j] - alignCenters[0])
						/ (float) modulesBetweenFPCenters;
				estAlignmentX = (int) (tempX + stepX);
				estAlignmentY = (int) (tempY + stepY);
				
				// Kind of arbitrary -- expand search radius before giving up
				for (int k = 4; k <= 8; k <<= 1) {
					try {
						AlignmentPattern temp = findAlignmentInRegion(
								moduleSize, estAlignmentX, estAlignmentY,
								(float) k);
						// check color constraint
						if (this.colorWrapper != null) {
							if (!checkColorConstraint(temp.getX(), temp.getY(),
									modulesBetweenFPCenters, moduleSize,
									topLeft, topRight, bottomLeft, count%6)) {
								continue;
							}
						}
						alignPtns[count] = temp;
						break;
					} catch (NotFoundException re) {
						// try next round
						alignPtns[count] = null;
					}
				}
				count++;
			}
		}
		
		return alignPtns;
	}

	/*
	private AlignmentPattern[] findAllAlignPatterns(FinderPattern topLeft,
			FinderPattern topRight, FinderPattern bottomLeft,
			float moduleSize, int dimension) {
		// compute the scope and the step in x,y directions
		Version version = null;
		try {
			version = Version.getProvisionalVersionForDimension(dimension);
		} catch (FormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		int length = version.getAlignmentPatternCenters().length;
		int versionNum = version.getVersionNumber();
		
		AlignmentPattern[] alignPtns = new AlignmentPattern[length * length - 4];
		// AlignmentPattern notfound = new AlignmentPattern(0, 0, 0);
		int modulesBetweenFPCenters = dimension - 7;
		float bottomRightX = topRight.getX() - topLeft.getX()
				+ bottomLeft.getX();
		float bottomRightY = topRight.getY() - topLeft.getY()
				+ bottomLeft.getY();

		// Estimate that alignment pattern is closer by 3 modules
		// from "bottom right" to known top left location
		float correctionToTopLeft = 3.0f / (float) modulesBetweenFPCenters;
		int estAlignmentX = (int) (topLeft.getX() + correctionToTopLeft
				* (bottomRightX - topLeft.getX()));
		int estAlignmentY = (int) (topLeft.getY() + correctionToTopLeft
				* (bottomRightY - topLeft.getY()));
		int horizonalMarkerX = estAlignmentX, horizonalMarkerY = estAlignmentY;

		float step1 = (dimension - 14) / (float) (length - 1);
		float step2 = step1;
		switch (versionNum) {
			case 15: step1 = 20; step2 = 22;
					 break;
			case 16: step1 = 20; step2 = 24;
					 break;
			case 18: step1 = 24; step2 = 26;
					 break;
			case 19: step1 = 24; step2 = 28;
					 break;
			case 22: step1 = 20; step2 = 24;
			 		 break;
			case 24: step1 = 22; step2 = 26;
					 break;
			case 26: step1 = 24; step2 = 28;
			 		 break;
			case 28: step1 = 20; step2 = 24;
			 		 break;
			case 30: step1 = 20; step2 = 26;
					 break;
			case 31: step1 = 24; step2 = 26;
			 		 break;
			case 32: step1 = 28; step2 = 26;
					 break;
			case 33: step1 = 24; step2 = 28;
			 		 break;
			case 36: step1 = 18; step2 = 26;
			 		 break;
			case 37: step1 = 22; step2 = 26;
			 		 break;
			case 39: step1 = 20; step2 = 28;
	 				 break;
			case 40: step1 = 24; step2 = 28;
					 break;
			default: break;
		}
		float horizontalX1 = (topRight.getX() - topLeft.getX()) * step1
				/ (float) modulesBetweenFPCenters;
		float horizontalX2 = (topRight.getX() - topLeft.getX()) * step2
				/ (float) modulesBetweenFPCenters;
		float horizontalY1 = (topRight.getY() - topLeft.getY()) * step1
				/ (float) modulesBetweenFPCenters;
		float horizontalY2 = (topRight.getY() - topLeft.getY()) * step2
				/ (float) modulesBetweenFPCenters;
		float verticalX1 = (bottomLeft.getX() - topLeft.getX()) * step1
				/ (float) modulesBetweenFPCenters;
		float verticalX2 = (bottomLeft.getX() - topLeft.getX()) * step2
				/ (float) modulesBetweenFPCenters;
		float verticalY1 = (bottomLeft.getY() - topLeft.getY()) * step1
				/ (float) modulesBetweenFPCenters;
		float verticalY2 = (bottomLeft.getY() - topLeft.getY()) * step2
				/ (float) modulesBetweenFPCenters;

		int count = 0;
		for (int i = 0; i < length; i++) {
			float stepX, stepY;
			if (i != 0) {
				if (i == 1) {
					stepX = horizontalX1;
					stepY = horizontalY1;
				} else {
					stepX = horizontalX2;
					stepY = horizontalY2;
				}
				estAlignmentX = (int) (horizonalMarkerX + stepX);
				estAlignmentY = (int) (horizonalMarkerY + stepY);
				horizonalMarkerX = estAlignmentX;
				horizonalMarkerY = estAlignmentY;
			}
			for (int j = 0; j < length; j++) {
				if ((i == 0 && j == length - 1) || (i == 0 && j == length - 2)
						|| (i == length - 1 && j == 0)
						|| (i == length - 1 && j == length - 1)) {
					continue; // finderpattern, step over
				}

				if (j != 0 || (j == 0 && i == 0)) {
					if (j == 0) {
						stepX = verticalX1;
						stepY = verticalY1;
					} else {
						stepX = verticalX2;
						stepY = verticalY2;
					}
					estAlignmentX = (int) (estAlignmentX + stepX);
					estAlignmentY = (int) (estAlignmentY + stepY);
				}
				// Kind of arbitrary -- expand search radius before giving up
				for (int k = 4; k <= 8; k <<= 1) {
					try {
						AlignmentPattern temp = findAlignmentInRegion(
								moduleSize, estAlignmentX, estAlignmentY,
								(float) k);
						// check color constraint
						if (this.colorWrapper != null) {
							if (!checkColorConstraint(temp.getX(), temp.getY(),
									modulesBetweenFPCenters, moduleSize,
									topLeft, topRight, bottomLeft, count%6)) {
								continue;
							}
						}
						alignPtns[count] = temp;
						break;
					} catch (NotFoundException re) {
						// try next round
						alignPtns[count] = null;
					}
				}
				count++;
			}
		}
		return alignPtns;
	}
	*/

	public static PerspectiveTransformGeneral createTransformGeneralNew(
			ResultPoint topLeft, ResultPoint topRight, ResultPoint bottomLeft,
			ResultPoint[] alignmentPatterns, ResultPoint alignmentPattern,
			int dimension) {
		// set x, coordinates in the real world image
		Version version = null;
		try {
			version = Version.getProvisionalVersionForDimension(dimension);
		} catch (FormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		int[] alignCenters = version.getAlignmentPatternCenters();
		int numAP = alignmentPatterns.length;
		int nonNullAPNum = 0;
		if (alignmentPattern != null)
			nonNullAPNum++;
		for (int i = 0; i < numAP; i++) {
			if (alignmentPatterns[i] != null) {
				nonNullAPNum++;
			}
		}
		float[] u = new float[2 * (3 + nonNullAPNum)];
		float dimMinusThree = (float) dimension - 3.5f;
		float sourceBottomRightX, sourceBottomRightY;
		sourceBottomRightX = sourceBottomRightY = dimMinusThree - 3.0f;

		float[] x = new float[2 * (3 + nonNullAPNum)];

		x[0] = topLeft.getX();
		x[1] = topLeft.getY();
		x[2] = topRight.getX();
		x[3] = topRight.getY();
		x[4] = bottomLeft.getX();
		x[5] = bottomLeft.getY();

		int alignBase;
		if (alignmentPattern != null) {
			x[6] = alignmentPattern.getX();
			x[7] = alignmentPattern.getY();

			u[6] = sourceBottomRightX;
			u[7] = sourceBottomRightY;// alignment

			alignBase = 8;
		} else {
			alignBase = 6;
		}

		for (int i = 0, j = 0; j < numAP; j++) {
			if (alignmentPatterns[j] != null) {
				x[alignBase + i * 2] = alignmentPatterns[j].getX();
				x[alignBase + 1 + i * 2] = alignmentPatterns[j].getY();
				i++;
			}
		}

		// set u, coordinates in the bitmaps (virtual image)
		u[0] = 3.5f;
		u[1] = 3.5f;// topleft
		u[2] = dimMinusThree;
		u[3] = 3.5f;// topRight
		u[4] = 3.5f;
		u[5] = dimMinusThree;// bottomLeft

		int length = alignCenters.length;//number of APs along one side
		int k = 0, m = 0;
		for (int i = 0; i < length; i++) {
			for (int j = 0; j < length; j++) {
				if ((i == 0 && j == length - 1) || (i == 0 && j == 0)
						|| (i == length - 1 && j == 0)
						|| (i == length - 1 && j == length - 1)) {
					continue; // finderpattern, step over
				}
				if (alignmentPatterns[m] != null) {
					u[alignBase + k * 2] = alignCenters[i]+0.5f;
					u[alignBase + 1 + k * 2] = alignCenters[j]+0.5f;
					k++;
				}
				m++;
			}
		}
		return PerspectiveTransformGeneral.getTransform(u, x);
	}
	
	/*
	public static PerspectiveTransformGeneral createTransformGeneralNew(

			ResultPoint topLeft, ResultPoint topRight, ResultPoint bottomLeft,
			ResultPoint[] alignmentPatterns, ResultPoint alignmentPattern,
			int dimension) {
		// set x, coordinates in the real world image
		int numAP = alignmentPatterns.length;
		int nonNullAPNum = 0;
		if (alignmentPattern != null)
			nonNullAPNum++;
		for (int i = 0; i < numAP; i++) {
			if (alignmentPatterns[i] != null) {
				nonNullAPNum++;
			}
		}
		float[] u = new float[2 * (3 + nonNullAPNum)];
		float dimMinusThree = (float) dimension - 3.5f;
		float sourceBottomRightX, sourceBottomRightY;
		sourceBottomRightX = sourceBottomRightY = dimMinusThree - 3.0f;

		float[] x = new float[2 * (3 + nonNullAPNum)];

		x[0] = topLeft.getX();
		x[1] = topLeft.getY();
		x[2] = topRight.getX();
		x[3] = topRight.getY();
		x[4] = bottomLeft.getX();
		x[5] = bottomLeft.getY();

		int alignBase;
		if (alignmentPattern != null) {
			x[6] = alignmentPattern.getX();
			x[7] = alignmentPattern.getY();

			u[6] = sourceBottomRightX;
			u[7] = sourceBottomRightY;// alignment

			alignBase = 8;
		} else {
			alignBase = 6;
		}

		for (int i = 0, j = 0; j < numAP; j++) {
			if (alignmentPatterns[j] != null) {
				x[alignBase + i * 2] = alignmentPatterns[j].getX();
				x[alignBase + 1 + i * 2] = alignmentPatterns[j].getY();
				i++;
			}
		}

		// set u, coordinates in the bitmaps (virtual image)
		u[0] = 3.5f;
		u[1] = 3.5f;// topleft
		u[2] = dimMinusThree;
		u[3] = 3.5f;// topRight
		u[4] = 3.5f;
		u[5] = dimMinusThree;// bottomLeft

		int length = (int) Math.sqrt(numAP + 4);
		float step1 = (dimension - 13) / (float) (length - 1);
		float step2 = step1;
		int versionNum = 0;
		try {
			versionNum = Version.getProvisionalVersionForDimension(dimension)
					.getVersionNumber();
		} catch (FormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		switch (versionNum) {
		case 15:
			step1 = 20;
			step2 = 22;
			break;
		case 16:
			step1 = 20;
			step2 = 24;
			break;
		case 18:
			step1 = 24;
			step2 = 26;
			break;
		case 19:
			step1 = 24;
			step2 = 28;
			break;
		case 22:
			step1 = 20;
			step2 = 24;
			break;
		case 24:
			step1 = 22;
			step2 = 26;
			break;
		case 26:
			step1 = 24;
			step2 = 28;
			break;
		case 28:
			step1 = 20;
			step2 = 24;
			break;
		case 30:
			step1 = 20;
			step2 = 26;
			break;
		case 31:
			step1 = 24;
			step2 = 26;
			break;
		case 32:
			step1 = 28;
			step2 = 26;
			break;
		case 33:
			step1 = 24;
			step2 = 28;
			break;
		case 36:
			step1 = 18;
			step2 = 26;
			break;
		case 37:
			step1 = 22;
			step2 = 26;
			break;
		case 39:
			step1 = 20;
			step2 = 28;
			break;
		case 40:
			step1 = 24;
			step2 = 28;
			break;
		default:
			break;
		}
		int k = 0, m = 0;
		for (int i = 0; i < length; i++) {
			for (int j = 0; j < length; j++) {
				if ((i == 0 && j == length - 1) || (i == 0 && j == 0)
						|| (i == length - 1 && j == 0)
						|| (i == length - 1 && j == length - 1)) {
					continue; // finderpattern, step over
				}
				if (alignmentPatterns[m] != null) {
					if (i >= 1) {
						u[alignBase + k * 2] = 6.5f + step1 + (i - 1) * step2;
					} else {
						u[alignBase + k * 2] = 6.5f;
					}
					if (j >= 1) {
						u[alignBase + 1 + k * 2] = 6.5f + step1 + (j - 1)
								* step2;
					} else {
						u[alignBase + 1 + k * 2] = 6.5f;
					}
					k++;
				}
				m++;
			}
		}

		return PerspectiveTransformGeneral.getTransform(u, x);

	}
	*/

	private BitMatrix[] colorSampleGrid(float[] white, PerspectiveTransformGeneral transform, int dimension) throws NotFoundException {
		
		GridSampler sampler = GridSampler.getInstance();
		try {
			// return sampler.sampleGridAffine(image, dimension, dimension,
			// transform);
			return sampler.colorSampleGrid(white, this.colorWrapper, dimension, dimension, transform, layerNum);
			
		} catch (NotFoundException e) {
			NotFoundException error = NotFoundException.getNotFoundInstance();
			error.setErrorMessage((e.getErrorMessage() != "") ? e
					.getErrorMessage()
					: "Something wrong in sample Grid. Detector");
			throw error;
		}
	}

	private float[] estimateWhiteProjection(PerspectiveTransformGeneral transform, int dimension, float moduleSize) {
		GridSampler sampler = GridSampler.getInstance();
		int[] sampledPoints = sampler.getWhiteSamplePoints(dimension, colorWrapper, transform, moduleSize);
		return this.colorWrapper.estimateWhiteRGB(sampledPoints);
	}
	
	/**
	 * This checks whether a detected alignment pattern satisfies the color
	 * constraint: color of the pixels in the alignment pattern should be the
	 * same
	 * 
	 * @param detectedPositionX
	 *            x coordinate of the position of the detected alignment pattern
	 * @param detectedPositionY
	 *            y coordinate of the position of the detected alignment pattern
	 * @param moduleSize
	 * @param bottomLeft
	 * @param topRight
	 * @param topLeft
	 * @param dimension
	 * @return indicator, true means a qualified alignment pattern, false
	 *         otherwise.
	 * @throws NotFoundException 
	 */
	private Boolean checkColorConstraint(float detectedPositionX,
			float detectedPositionY, int modulesBetweenFPCenters,
			float moduleSize, ResultPoint topLeft, ResultPoint topRight,
			ResultPoint bottomLeft, int colorIdx) throws NotFoundException {
		float[] positions = new float[10];
		positions[0] = detectedPositionX;
		positions[1] = detectedPositionY;
		positions[2] = detectedPositionX-1;
		positions[3] = detectedPositionY-1;
		positions[4] = detectedPositionX+1;
		positions[5] = detectedPositionY+1;
		positions[6] = detectedPositionX-1;
		positions[7] = detectedPositionY+1;
		positions[8] = detectedPositionX+1;
		positions[9] = detectedPositionY-1;
//		float correctionToTopLeft = 2.0f / (float) modulesBetweenFPCenters;
//		positions[2] = detectedPositionX + correctionToTopLeft
//				* (topLeft.getX() - bottomLeft.getX());
//		positions[3] = detectedPositionY + correctionToTopLeft
//				* (topLeft.getY() - bottomLeft.getY());
//		positions[4] = detectedPositionX - correctionToTopLeft
//				* (topLeft.getX() - bottomLeft.getX());
//		positions[5] = detectedPositionY - correctionToTopLeft
//				* (topLeft.getY() - bottomLeft.getY());
//		positions[6] = detectedPositionX + correctionToTopLeft
//				* (topLeft.getX() - topRight.getX());
//		positions[7] = detectedPositionY + correctionToTopLeft
//				* (topLeft.getY() - topRight.getY());
//		positions[8] = detectedPositionX - correctionToTopLeft
//				* (topLeft.getX() - topRight.getX());
//		positions[9] = detectedPositionY - correctionToTopLeft
//				* (topLeft.getY() - topRight.getY());

		// collect data in a square 1/2 module size width
		int count = 0;
		for (int i = 0; i < 5; i++) {
			boolean[] rgb = this.colorWrapper.colorClassify((int) positions[i * 2],
					(int) positions[i * 2 + 1], white);
			
//			if(rgb[0]==COLORSEQ[colorIdx][0] && 
//					rgb[1]==COLORSEQ[colorIdx][1] && 
//					rgb[2]==COLORSEQ[colorIdx][2])
			switch (this.layerNum) {
				case 2:
					if(rgb[0]==BLACK[0] && rgb[1]==BLACK[1])       
						count++;
					break;
				case 3:
					if(rgb[0]==COLORSEQ[colorIdx][0] 
							&& rgb[1]==COLORSEQ[colorIdx][1] 
							&& rgb[2]==COLORSEQ[colorIdx][2])    
						count++;
					break;
				case 4:
					if(rgb[0]!=COLORSEQ[colorIdx][0] 
							&& rgb[1]!=COLORSEQ[colorIdx][1] 
							&& rgb[2]!=COLORSEQ[colorIdx][2]
							&& rgb[3]==true)
						count++;
					break;
				default: break;
			}
		}
		return (count >= 3)? true:false;		
	}

	/**
	 * Based on information about the QR code, build the result again. The code
	 * in this function depends on those in detect() and
	 * processFinderPatternInfoNew/processFinderPatternInfo
	 * 
	 * @param previousResult
	 * @param newDimension
	 * @return
	 */
	public DetectorResult detect(DetectorResult previousResult, int newDimension) {
		int[] alignments = null;
		try {
			alignments = Version
					.getProvisionalVersionForDimension(newDimension)
					.getAlignmentPatternCenters();
		} catch (ReaderException e) {
		}
		if (alignments == null) {
			if (this.colorWrapper != null)
				return null;
			else
				return previousResult;
		}
		int alignementIndex = alignments.length;

		// for low version and correct dimension, transform matrix need not to be recomputed
		if (previousResult.getBits().getWidth() == newDimension && alignementIndex < 4) {
			PerspectiveTransformGeneral transform = previousResult.getTransform();
			try {
				BitMatrix[] channelBits = (transform != null && colorWrapper != null) ? colorSampleGrid(white, transform, newDimension):null;
				previousResult.setChannelBits(channelBits);
			} catch (NotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
			return previousResult;
		}
			
		
		ResultPoint[] points = previousResult.getPoints();
		ResultPoint topLeft, bottomLeft, topRight, alignmentPattern;
		
		if (points.length < 4) 
			alignmentPattern = null;
		else 
			alignmentPattern = points[3];
		
		topLeft = points[1];
		topRight = points[2];
		bottomLeft = points[0];
		
		try {
			AlignmentPattern[] alignPtns = null;
			PerspectiveTransformGeneral transform = null;
			if(this.colorWrapper == null) {
				transform = createTransformGeneral(
						topLeft, topRight, bottomLeft, alignmentPattern,
						newDimension);
			}else {
				if (alignementIndex < 4) {
					transform = createTransformGeneral(
							topLeft, topRight, bottomLeft, alignmentPattern,
							newDimension);
				} else {
//					Version provisionalVersion = Version.getProvisionalVersionForDimension(newDimension);
					alignPtns = findAllAlignPatterns((FinderPattern)topLeft, (FinderPattern)topRight, (FinderPattern)bottomLeft, previousResult.getModuleSize(), newDimension);
					transform = createTransformGeneralNew(topLeft, topRight, bottomLeft, alignPtns, alignmentPattern, newDimension);
				}
//				this.white = this.colorWrapper.estimateWhiteRGB(new FinderPattern[]{(FinderPattern)bottomLeft, (FinderPattern)topLeft, (FinderPattern)topRight}, previousResult.getModuleSize());
				this.white = estimateWhiteProjection(transform, newDimension, previousResult.getModuleSize());
				int[] testPos = new int[] {(int)(topLeft.getX()+0.5f),(int)(topLeft.getY()+0.5f), 
											(int)(topRight.getX()+0.5f),(int)(topRight.getY()+0.5f), 
											(int)(bottomLeft.getX()+0.5f),(int)(bottomLeft.getY()+0.5f)};
				colorWrapper.classifierSelection(testPos, white);
//				colorWrapper.classifierSelection(white);
//				System.out.println("white=["+white[0]+","+white[1]+","+white[2]+"], dimen="+newDimension);
//				for (int i = 0; i<white.length; i++)
//					white[i] += Math.random()*10 - 5;
//				this.white = new float[]{222.2f, 238.7f, 230.7f};
//				this.white[2] = 235.7f;
//				System.out.println("adding noise ["+white[0]+","+white[1]+","+white[2]+"]");
			}

			
			BitMatrix[] channelBits = (transform != null && colorWrapper != null) ? colorSampleGrid(white, transform, newDimension):null;
			
			DetectorResult dr= new DetectorResult(null, points,
						previousResult.getPatterns(), alignPtns,
						previousResult.getModuleSize(), transform);
			if (dr!=null) {
				dr.setChannelBits(channelBits);
			}
			return dr;
		} catch (ReaderException e) {
		}
		
		return previousResult;
	}

	public DetectorResult detectWithoutClassify(DetectorResult previousResult, int newDimension) {
		int[] alignments = null;
		try {
			alignments = Version
					.getProvisionalVersionForDimension(newDimension)
					.getAlignmentPatternCenters();
		} catch (ReaderException e) {
		}
		if (alignments == null) {
			if (this.colorWrapper != null)
				return null;
			else
				return previousResult;
		}
		int alignementIndex = alignments.length;

		// for low version and correct dimension, transform matrix need not to be recomputed
		if (previousResult.getBits().getWidth() == newDimension && alignementIndex < 4) {
			PerspectiveTransformGeneral transform = previousResult.getTransform();
			try {
				BitMatrix[] channelBits = (transform != null && colorWrapper != null) ? colorSampleGrid(white, transform, newDimension):null;
				previousResult.setChannelBits(channelBits);
			} catch (NotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
			return previousResult;
		}
			
		
		ResultPoint[] points = previousResult.getPoints();
		ResultPoint topLeft, bottomLeft, topRight, alignmentPattern;
		
		if (points.length < 4) 
			alignmentPattern = null;
		else 
			alignmentPattern = points[3];
		
		topLeft = points[1];
		topRight = points[2];
		bottomLeft = points[0];
		
		try {
			AlignmentPattern[] alignPtns = null;
			PerspectiveTransformGeneral transform = null;
			if(this.colorWrapper == null) {
				transform = createTransformGeneral(
						topLeft, topRight, bottomLeft, alignmentPattern,
						newDimension);
			}else {
				if (alignementIndex < 4) {
					transform = createTransformGeneral(
							topLeft, topRight, bottomLeft, alignmentPattern,
							newDimension);
				} else {
					alignPtns = findAllAlignPatterns((FinderPattern)topLeft, (FinderPattern)topRight, (FinderPattern)bottomLeft, previousResult.getModuleSize(), newDimension);
					transform = createTransformGeneralNew(topLeft, topRight, bottomLeft, alignPtns, alignmentPattern, newDimension);
				}
			}
			BitMatrix bits = sampleGrid(image, transform, newDimension);
			
			DetectorResult dr= new DetectorResult(bits, points,
						previousResult.getPatterns(), alignPtns,
						previousResult.getModuleSize(), transform);
			if (dr!=null) {
				// set sampled points
				// comment this if it not for matlab
				GridSampler sampler = GridSampler.getInstance();
				try {
					dr.setSamplePoints(sampler.getSampledPoints(image, newDimension, newDimension, transform));
				} catch (NotFoundException e) {
					NotFoundException error = NotFoundException.getNotFoundInstance();
					error.setErrorMessage((e.getErrorMessage() != "") ? e
							.getErrorMessage()
							: "Something wrong in sample Grid. Detector");
					throw error;
				}
			}
			return dr;
		} catch (ReaderException e) {
		}
		
		return previousResult;
	}
}

