package com.google.zxing.color;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.google.zxing.Binarizer;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.detector.FinderPattern;

/**
 * This wrapper class is used to handle color QR code image the functionality is
 * mainly processing rgb data so as to maximally reuse the existing codes for
 * monochrome QR code.
 * 
 * @author Zhibo Yang
 *
 */
public final class RGBColorWrapper {
	
	private byte[] red;
	private byte[] green;
	private byte[] blue;
	private int height;
	private int width;
	private BitMatrix bitMatrix;
	private Classifier colorClassifier = null;
	private Classifier[] candidateClassifiers = null;	
	private int layerNum = 3;//default
	private boolean[] channelHints = null;//indicator that whether the channel need to be decoded or not
	private int classifierIdx = -1;
	private QDA modelSelector = null;
	
	public RGBColorWrapper(int[] rgb, int height, int width, int layerNum) {
		this.height = height;
		this.width = width;
		this.layerNum = layerNum;
		this.channelHints = new boolean[layerNum];
		for (int i = 0; i < layerNum; i++) channelHints[i] = true;
		
		red = new byte[width * height];
		green = new byte[width * height];
		blue = new byte[width * height];
		
		for (int y = 0; y < height; y++) {
			int offset = y * width;
			for (int x = 0; x < width; x++) {
				int pixel = rgb[offset + x];
				red[offset + x] = (byte) ((pixel >> 16) & 0xff);
				green[offset + x] = (byte) ((pixel >> 8) & 0xff);
				blue[offset + x] = (byte) (pixel & 0xff);
			}
		}
//		String path = "./models/model_2layer.txt";
//		String path = "./models/model_alldevices.txt";
//		boolean isQDA = false;
//		try {
//			isQDA = isQDA(path);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
//		this.colorClassifier = isQDA ? QDA.getInstance(path) : LDA.getInstance(path);
//		this.colorClassifier = QDA.getInstance(path);
		
//		String[] MODELPATH_layerSVM = {"./models/layerSVM1/layer1.model",
//			"./models/layerSVM1/layer2.model","./models/layerSVM1/layer3.model"};
		
		
//		this.candidateClassifiers = new Classifier[3];
//		this.candidateClassifiers[0] = LayeredSVM.getInstance("./candidate_classifiers/layeredSVMs_nexus5_fluo.txt");
//		this.candidateClassifiers[3] = LayeredSVM.getInstance("./candidate_classifiers/layeredSVMs_screen.txt");
//		this.candidateClassifiers[1] = LayeredSVM.getInstance("./candidate_classifiers/layeredSVMs_nexus5_incand.txt");
//		this.candidateClassifiers[2] = LayeredSVM.getInstance("./candidate_classifiers/layeredSVMs_nexus5_outdoor.txt");
//		this.candidateClassifiers[4] = LayeredSVM.getInstance("./candidate_classifiers/layeredSVMs_ip6plus_mixed_new_aug.txt");
//		this.candidateClassifiers[5] = LayeredSVM.getInstance("./candidate_classifiers/layeredSVMs_ip6plus_mixed_new.txt");
//		this.colorClassifier = this.candidateClassifiers[0];//default one
		
//		this.modelSelector = QDA.getInstance("./candidate_classifiers/model_selector.txt");
		this.colorClassifier = CMIModel.getInstance("/Users/ouyangzhibo/gitlab/colorqrcode/core/candidate_classifiers/LSVM_CMI.txt", "LSVM");
	}
	
	public RGBColorWrapper(int[] rgb, int height, int width, int layerNum, boolean isSimple) {
		this.height = height;
		this.width = width;
		this.layerNum = layerNum;
		this.channelHints = new boolean[layerNum];
		for (int i = 0; i < layerNum; i++) channelHints[i] = true;
		
		red = new byte[width * height];
		green = new byte[width * height];
		blue = new byte[width * height];
		
		for (int y = 0; y < height; y++) {
			int offset = y * width;
			for (int x = 0; x < width; x++) {
				int pixel = rgb[offset + x];
				red[offset + x] = (byte) ((pixel >> 16) & 0xff);
				green[offset + x] = (byte) ((pixel >> 8) & 0xff);
				blue[offset + x] = (byte) (pixel & 0xff);
			}
		}
		if (isSimple)
			this.colorClassifier = new SimpleClassifier(layerNum);
	}

	public RGBColorWrapper(int[] rgb, int dataWidth, int dataHeight, int left,
			int top, int width, int height, boolean reverseHorizontal, 
			Classifier classifier, Map<DecodeHintType,Object> hints, int layerNum) {
		this.layerNum = layerNum;
		this.channelHints = new boolean[layerNum];
		init(rgb, dataWidth, dataHeight, left, top, width, height, reverseHorizontal, 
				classifier, hints);
	}
	
	public RGBColorWrapper(int[] rgb, int dataWidth, int dataHeight, int left,
			int top, int width, int height, boolean reverseHorizontal, 
			Classifier[] classifiers, Map<DecodeHintType,Object> hints, int layerNum) {
		this.layerNum = layerNum;
		this.channelHints = new boolean[layerNum];
		this.candidateClassifiers = classifiers;
		init(rgb, dataWidth, dataHeight, left, top, width, height, reverseHorizontal, 
				classifiers[0], hints);
	}
	
	private void init(int[] rgb, int dataWidth, int dataHeight, int left,
			int top, int width, int height, boolean reverseHorizontal, 
			Classifier classifier, Map<DecodeHintType,Object> hints) {
		this.height = dataHeight;
		this.width = dataWidth;
		if(hints!=null) {
			this.channelHints[0] = (hints.get(DecodeHintType.LAYER1_DECODED) !=null 
					&& ((Result)hints.get(DecodeHintType.LAYER1_DECODED)).getRawBytes() !=null)? false:true;
			this.channelHints[1] = (hints.get(DecodeHintType.LAYER2_DECODED) !=null 
					&& ((Result)hints.get(DecodeHintType.LAYER2_DECODED)).getRawBytes() !=null)? false:true;
			this.channelHints[2] = (hints.get(DecodeHintType.LAYER3_DECODED) !=null 
					&& ((Result)hints.get(DecodeHintType.LAYER3_DECODED)).getRawBytes() !=null)? false:true;			
		}else {
			this.channelHints[0] = true;
			this.channelHints[1] = true;
			this.channelHints[2] = true;
		}
		red = new byte[dataWidth * dataHeight];
		green = new byte[dataWidth * dataHeight];
		blue = new byte[dataWidth * dataHeight];

		for (int y = 0; y < dataHeight; y++) {
			int offset = y * dataWidth;
			for (int x = 0; x < dataWidth; x++) {
				int pixel = rgb[offset + x];
				red[offset + x] = (byte) ((pixel >> 16) & 0xff);
				green[offset + x] = (byte) ((pixel >> 8) & 0xff);
				blue[offset + x] = (byte) (pixel & 0xff);
			}
		}
		this.colorClassifier = classifier;
	}
	
	public RGBColorWrapper(int[] rgb, int dataWidth, int dataHeight, int left,
			int top, int width, int height, boolean reverseHorizontal, 
			Classifier classifier, Map<DecodeHintType,Object> hints) {
		this.channelHints = new boolean[3];
		init(rgb, dataWidth, dataHeight, left, top, width, height, reverseHorizontal, 
			classifier, hints);
	}
	
	/**
	 * bytes[] rgb is arranged as [red(byte), green(byte), blue(byte),
	 * red(byte), ...] repeatedly.
	 * 
	 * @param rgb
	 * @param height
	 * @param width
	 */
	public RGBColorWrapper(byte[] rgb, int dataWidth, int dataHeight, int left,
			int top, int width, int height, boolean reverseHorizontal) {
		this.height = dataHeight;
		this.width = dataWidth;
		this.channelHints[0] = true;
		this.channelHints[1] = true;
		this.channelHints[2] = true;
		red = new byte[dataWidth * dataHeight];
		green = new byte[dataWidth * dataHeight];
		blue = new byte[dataWidth * dataHeight];
		for (int y = 0; y < dataHeight; y++) {
			int offset = y * dataWidth;
			for (int x = 0; x < dataWidth; x++) {
				int pixelIdx = offset + x;
				red[offset + x] = rgb[pixelIdx];
				green[offset + x] = rgb[pixelIdx + 1];
				blue[offset + x] = rgb[pixelIdx + 2];
			}
		}
	}

	public RGBColorWrapper(byte[] r, byte[] g, byte[] b, int height, int width) {
		this.red = r;
		this.blue = b;
		this.green = g;
		this.height = height;
		this.width = width;
	}

	public void setBitMatrix(BitMatrix bitMatrix) {
		this.bitMatrix = bitMatrix;
	}
	
	public BitMatrix getBlackMatrix() throws NotFoundException {

		if (bitMatrix != null)
			return bitMatrix;

		this.bitMatrix = rgb2Binary();
		/*
		if (redBinarizer == null || greenBinarizer == null
				|| blueBinarizer == null)
			return null;

		BitMatrix redBitMtx = this.redBinarizer.getBlackMatrix();
		BitMatrix greenBitMtx = this.greenBinarizer.getBlackMatrix();
		BitMatrix blueBitMtx = this.blueBinarizer.getBlackMatrix();

		int h = redBitMtx.getHeight();
		int w = redBitMtx.getWidth();

		BitMatrix newBitMatrix = new BitMatrix(w, h);
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				if (redBitMtx.get(x, y) || greenBitMtx.get(x, y)
						|| blueBitMtx.get(x, y))
					newBitMatrix.set(x, y);
			}
		}
		bitMatrix = newBitMatrix;*/

		return bitMatrix;
	}

	/**
	 * Get binary image for specific channel
	 * 
	 * @param channel
	 *            1-red, 2-green, 3-blue
	 * @return
	 * @throws NotFoundException
	 */
	public BitMatrix getBlackMatrix(int channel) throws NotFoundException {
		Binarizer b;
		switch (channel) {
		case 0:
			b = channelHints[0]?new HybridBinarizer(new RGBLuminanceSource(red, width,
					height, 0, 0, width, height)):null;
			break;
		case 1:
			b = channelHints[1]?new HybridBinarizer(
					new RGBLuminanceSource(green, width, height, 0, 0, width,
					height)):null;
			break;
		case 2:
			b = channelHints[2]?new HybridBinarizer(new RGBLuminanceSource(
					blue, width, height, 0, 0, width, height)):null;
			break;
		default:
			return null;
		}
		return b!=null?b.getBlackMatrix():null;
	}
	
	public void classifierSelection(float[] whiteRGB){
		double[][] means = {{0.8954092329549372, 0.8930496043191198, 0.8936982635079254}, 
							{0.9174942989137096, 0.9067049647714024, 0.835187256770273}, 
							{0.8176229181144671, 0.8501265160419399, 0.8529760462539779}};
		double[][][] covMat = {{{28935.228980310556, -18690.120510093122, -8480.898991561162}, 
								{-18690.120510093104, 23823.649920063217, -5343.1696298484785}, 
								{-8480.898991561184, -5343.169629848457, 14185.232586727254}}, 
								{{27145.351117970255, -22933.1854318048, -1457.878455018236}, 
								{-22933.185431804442, 32156.210855918238, -9140.225035547013}, 
								{-1457.8784550185271, -9140.22503554675, 8602.896013226518}}, 
								{{1419.785931834801, -772.8726826620965, 12.553430350859108}, 
								{-772.8726826621044, 24992.82779432085, -23813.530438608494}, 
								{12.553430350859845, -23813.530438608497, 23258.946705619677}} };
		int[] groupList = {1,2,3};
		double[] probList = {-1.2239187186262297, -0.937211542668918, -1.1577068115349247};
		double[] dets = {-13.551520908001352, -12.173975249377342, -11.316989310173808};
		
		
		QDA selector = new QDA(means, covMat, groupList, probList, dets);
		
		double[] output = new double[whiteRGB.length];
	    for (int i = 0; i < whiteRGB.length; i++) output[i] = whiteRGB[i]/255;
		int idx = selector.predict_value(output) - 1;
		this.colorClassifier = this.candidateClassifiers[idx];
		this.classifierIdx = idx;
	}
	
	// select color classifier by testing and voting
	public void classifierSelection(int[] testPos, float[] whiteRGB){
		if(this.candidateClassifiers == null) return;
		if(this.candidateClassifiers.length == 1){
			this.colorClassifier = this.candidateClassifiers[0];
			this.classifierIdx = 0;
		}
		
		boolean[][] MCY= {{false,true,false},
				  {true,false,false},
			      {false,false,true}};

		int bestIdx = 0;		
		double[][] scoreArray = new double[whiteRGB.length][this.candidateClassifiers.length];
		for (int i = 0; i < testPos.length; i+=2) {
			for (int x = -3; x < 4; x++) {
				int posX = testPos[i] + x;
				int posY = testPos[i+1] + x;
				if (posX < 0 || posX >= width || posY < 0 || posY >= height) continue;
				double[] data = new double[whiteRGB.length];
				int offset = posY * width;
				data[0] = (red[offset+posX] & 0xff) / whiteRGB[0];
				data[1] = (green[offset+posX] & 0xff) / whiteRGB[1];
				data[2] = (blue[offset+posX] & 0xff) / whiteRGB[2];
				
				for (int j = 0; j < this.candidateClassifiers.length; j++) {
					double[] scores = candidateClassifiers[j].evaluate(data);
					for(int k = 0; k<data.length; k++) {
						double tempScore = 0;
						tempScore += (MCY[i/2][k] ^ scores[k] > 0) ? Math.abs(scores[k]) : 0;//
						scoreArray[k][j] += tempScore;	
					}
				}
			}
		}
		double[] weights = {2, 2, 0.5};
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int j = 0; j < this.candidateClassifiers.length; j++) {
			double tempScore = 0;
			for (int k = 0; k < whiteRGB.length; k++) {
				tempScore += weights[k] * scoreArray[k][j];
			}
			if (tempScore > bestScore){
				bestIdx = j;
				bestScore = tempScore;
			}
		}

//		int[] winCount = new int[this.candidateClassifiers.length];
//		int[] winRecord = new int[whiteRGB.length];
//		for (int i = 0; i < whiteRGB.length; i++) {
//			double bestScore = Double.NEGATIVE_INFINITY;
//			for (int j = 0; j < winCount.length; j++) {
//				if (bestScore < scoreArray[i][j]) {
//					bestIdx = j;
//					bestScore = scoreArray[i][j];
//				}
//			}
//			winCount[bestIdx]++;
//			winRecord[i] = bestIdx;
//		}
//		double[][] temp = new double[scoreArray.length][scoreArray[0].length];
//		for(int i=0; i<scoreArray.length; i++)
//			for(int j=0; j<scoreArray[i].length; j++)
//				temp[i][j]=scoreArray[i][j];
//		int[] secWinCount = new int[this.candidateClassifiers.length];
//		int[] secWinRecord = new int[whiteRGB.length];
//		for (int i = 0; i < whiteRGB.length; i++)	temp[i][winRecord[i]]=0;
//		for (int i = 0; i < whiteRGB.length; i++) {
//			double bestScore = Double.NEGATIVE_INFINITY;
//			for (int j = 0; j < winCount.length; j++) {
//				if (bestScore < temp[i][j]) {
//					bestIdx = j;
//					bestScore = temp[i][j];
//				}
//			}
//			secWinCount[bestIdx]++;
//			secWinRecord[i] = bestIdx;
//		}
//		// dominate one
//		boolean needRound2 = true;
//		for (int j = 0; j < winCount.length; j++) {
//			if (winCount[j] > 1) {
//				bestIdx = j;
//				needRound2 = false;
//			}
//		}
//		// 1 1 1 0
//		if (needRound2) {
//			for (int j = 0; j < winRecord.length; j++) {
//				
//			}
//		}
		this.colorClassifier = this.candidateClassifiers[bestIdx];
		System.out.println("selected classifier="+bestIdx);
		this.classifierIdx = bestIdx;
	}
	
	public float[] estimateWhiteRGB(int[] pos) {
		if (pos == null) return new float[]{255.0f, 255.0f, 255.0f};
		
		float[] whiteRGB = {.0f, .0f, .0f};
		int count = 0;
		for (int i = 0; i < pos.length; i+=2) {
			if (pos[i] < 0 || pos[i] >= width || pos[i+1] < 0 || pos[i+1] >= height) continue;
			
			if (!this.bitMatrix.get(pos[i], pos[i+1])) {
				int offset = pos[i+1] * width;
				whiteRGB[0] += red[offset+pos[i]] & 0xff;
				whiteRGB[1] += green[offset+pos[i]] & 0xff;
				whiteRGB[2] += blue[offset+pos[i]] & 0xff;
				count++;
			}
		}
		
		if (count == 0) {
			return new float[]{255.0f, 255.0f, 255.0f};
		} else {
			whiteRGB[0] /= count;
			whiteRGB[1] /= count;
			whiteRGB[2] /= count;
//			
//			// select color classifier
//			double[][] testData = new double[count][3];
//			int idx = 0;
//			for (int i = 0; i < pos.length; i+=2) {
//				if (pos[i] < 0 || pos[i] >= width || pos[i+1] < 0 || pos[i+1] >= height) continue;
//				
//				if (!this.bitMatrix.get(pos[i], pos[i+1])) {
//					int offset = pos[i+1] * width;
//					testData[idx][0] = (red[offset+pos[i]] & 0xff) / whiteRGB[0];
//					testData[idx][1] = (green[offset+pos[i]] & 0xff) / whiteRGB[1];
//					testData[idx][2] = (blue[offset+pos[i]] & 0xff) / whiteRGB[2];
//					idx++;
//				}
//			}
//			this.classifierSelection(testData);
			
			return whiteRGB;
		}
	}
	
	/**
	 * Sample some pixels from the white blocks in the finder patterns
	 * and compute the average
	 * @param fps finder patterns
	 * @param moduleSize
	 * @return
	 */
	public float[] estimateWhiteRGB(FinderPattern[] fps, float moduleSize) {
		float[] whiteRGB = {.0f, .0f, .0f};
		int count = 0;
		for(int i = 0; i < 3; i++) {
			FinderPattern currPattern = fps[i];
			float[] currWhite = estimateWhiteRGB(currPattern.getX(), currPattern.getY(), moduleSize, 3);
			for (int j=0; j<3; j++)
				whiteRGB[j] += currWhite[j];
			count++;
		}
		
		if (count == 0) {
			return new float[]{255.0f, 255.0f, 255.0f};
		} else {
			whiteRGB[0] /= count;
			whiteRGB[1] /= count;
			whiteRGB[2] /= count;
			return whiteRGB;
		} 
	}
	
	/**
	 * estimate the white RGB value in the white zones in each pattern
	 * @param centerX x coordinate of pattern center
	 * @param centerY y coordinate of pattern center
	 * @param moduleSize module size
	 * @param patModuleNum the number of modules between the center and the target position
	 * @return estimated white RGB values
	 */
	public float[] estimateWhiteRGB(float centerX, float centerY, float moduleSize, int patModuleNum) {
		float[] whiteRGB = {.0f, .0f, .0f};
		int count = 0;
		float ratio = (float)patModuleNum / 2.0f + 0.7f;
		int zoneWidth = 1;
		// top
		int posX = (int) (centerX);
		int posY = (int) (centerY-ratio*moduleSize);
		for (int y = posY; y < posY + zoneWidth; y++) {
			int offset = y * width;
			for (int x = posX; x < posX + zoneWidth; x++) {
				if (!this.bitMatrix.get(posX, posY)) {
					whiteRGB[0] += red[offset+x] & 0xff;
					whiteRGB[1] += green[offset+x] & 0xff;
					whiteRGB[2] += blue[offset+x] & 0xff;
					count++;
				}
			}
		}
		//bottom
		posY = (int) (centerY+ratio*moduleSize);
		for (int y = posY; y < posY + zoneWidth; y++) {
			int offset = y * width;
			for (int x = posX; x <= posX + 1; x++) {
				if (!this.bitMatrix.get(posX, posY)) {
					whiteRGB[0] += red[offset+x] & 0xff;
					whiteRGB[1] += green[offset+x] & 0xff;
					whiteRGB[2] += blue[offset+x] & 0xff;
					count++;
				}
			}
		}
		//left
		posX = (int) (centerX-ratio*moduleSize);
		posY = (int) (centerY);
		for (int y = posY; y < posY + zoneWidth; y++) {
			int offset = y * width;
			for (int x = posX; x < posX + zoneWidth; x++) {
				if (!this.bitMatrix.get(posX, posY)) {
					whiteRGB[0] += red[offset+x] & 0xff;
					whiteRGB[1] += green[offset+x] & 0xff;
					whiteRGB[2] += blue[offset+x] & 0xff;
					count++;
				}
			}
		}
		//right
		posX = (int) (centerX+ratio*moduleSize);
		for (int y = posY; y < posY + zoneWidth; y++) {
			int offset = y * width;
			for (int x = posX; x < posX + zoneWidth; x++) {
				if (!this.bitMatrix.get(posX, posY)) {
					whiteRGB[0] += red[offset+x] & 0xff;
					whiteRGB[1] += green[offset+x] & 0xff;
					whiteRGB[2] += blue[offset+x] & 0xff;
					count++;
				}
			}
		}
		
		whiteRGB[0] /= count;
		whiteRGB[1] /= count;
		whiteRGB[2] /= count;
		
		return whiteRGB;
	}
	
	/**
	 * get the color classification of pixel (x,y)
	 * @param x: x coordinate
	 * @param y: y coordinate
	 * @param white: estimated RGB values of white color 
	 * @return predictions, indicate the present of pixel (x,y) in each channel
	 */
	private boolean[] colorPredict(int x, int y, float[] white) {		
		
		if (x<0 || x >= width || y<0 || y >= height || colorClassifier == null) 
			return new boolean[]{false, false, false};
		
		double[] data = new double[]{0,0,0};
		
		//pick the center point
		data[0] = (red[y*width+x] & 0xff) / white[0];
		data[1] = (green[y*width+x] & 0xff) / white[1];
		data[2] = (blue[y*width+x] & 0xff) / white[2];

		return colorClassifier.predict(data);
	}
	
	/**
	 * get the color classification of pixel (x,y)
	 * @param x: x coordinate
	 * @param y: y coordinate
	 * @param white: estimated RGB values of white color 
	 * @return predictions, indicate the present of pixel (x,y) in each channel
	 */
	public boolean[] colorClassify(int x, int y, float[] white) {
		
//		int prediction = colorPredict(x, y, white);
		
//		if (prediction == -5)
//			return new int[]{0,0,0};
		
//		int[] rgb = new int[this.layerNum];
//		if (this.layerNum == 3) {
//			rgb[0] = (prediction >> 2) % 2;
//			rgb[1] = (prediction >> 1) % 2;
//			rgb[2] = prediction % 2;
//		}
//		else if (this.layerNum == 2) {
//			switch (prediction) {
//				case 0 : rgb = new int[]{0,0}; break;//black
//				case 1 : rgb = new int[]{1,0}; break;//cyan
//				case 2 : rgb = new int[]{0,1}; break;//red
//				case 3 : rgb = new int[]{1,1}; break;//white
//			}
//		}
//		return rgb;
		
		return colorPredict(x, y, white);
	}
	
	public boolean[] colorClassify(int x, int y, float[] white, boolean[] isLayerActive) {
		
		boolean[] rst = new boolean[this.layerNum];
		if (x<0 || x >= width || y<0 || y >= height || colorClassifier == null) 
			return rst;
		
		double[] data = new double[]{0,0,0};
		
		//pick the center point
		data[0] = (red[y*width+x] & 0xff) / white[0];
		data[1] = (green[y*width+x] & 0xff) / white[1];
		data[2] = (blue[y*width+x] & 0xff) / white[2];

		if (colorClassifier.getIsLayerable()) {
			for (int i = 0; i < isLayerActive.length; i++){
				if (isLayerActive[i])
					rst[i] = colorClassifier.predict_layer(data, i);
				if (this.layerNum==4)
					rst[i] = !rst[i];
			}
			return rst;
		}else{
			return colorClassifier.predict(data);
		}
	}
	
	public boolean[] colorClassify(int xList[], int yList[], float[] white, boolean[] isLayerActive) {
		
		boolean[] rst = new boolean[this.layerNum];
		if (colorClassifier == null) return rst;
		
		double[] data = new double[15];
		for (int i = 0; i<xList.length; i++) {
			int x = xList[i];
			int y = yList[i];
			if (x<=0 || x >= width || y<=0 || y >= height) continue;
			
			data[i*3] = (red[y*width+x] & 0xff) / white[0];
			data[i*3+1] = (green[y*width+x] & 0xff) / white[1];
			data[i*3+2] = (blue[y*width+x] & 0xff) / white[2];
		}
		
		if (colorClassifier.getIsLayerable()) {
			for (int i = 0; i < isLayerActive.length; i++){
				if (isLayerActive[i])
					rst[i] = colorClassifier.predict_layer(data, i);
				if (this.layerNum==4)
					rst[i] = !rst[i];
			}
			return rst;
		}else{
			return colorClassifier.predict(data);
		}
	}
	
	// see if the classifier use CMI scheme or not
	public boolean getClassifierCMIFlag () {
		return colorClassifier.getCMIFlag();
	}
	
	public int getNumLayer (int topLeftX, int topLeftY) {
		int R = red[topLeftY*width+topLeftX] & 0xff;
		int G = green[topLeftY*width+topLeftX] & 0xff;
		int B = blue[topLeftY*width+topLeftX] & 0xff;
		if(Math.abs(R-G) < 50 && Math.abs(R-B) < 50 && Math.abs(B-G) < 50)
			return 2;
		else
			return 3;
	}
	
	public boolean getChannelHint(int channel) {
		return this.channelHints[channel];
	}
	public boolean[] getChannelHint() {
		return this.channelHints;
	}
	
	// convert rgb image to binary bitmatrix
	private BitMatrix rgb2Binary() {
		BitMatrix binary = new BitMatrix(width, height);
		int length = 8; // number of blocks horizontally and vertically
		int stepWidth = this.width/length+1;
		int stepHeight = this.height/length+1;
		for (int y = 0; y < stepHeight*length; y+=stepHeight) {
			for (int x = 0; x < stepWidth*length; x+=stepWidth) {
				int x2 = x+stepWidth > width? width : x+stepWidth;
				int y2 = y+stepHeight > height? height : y+stepHeight;
				int t1 = getLocalThreshold(red, width, height, x, x2, y, y2);
				int t2 = getLocalThreshold(green, width, height, x, x2, y, y2);
				int t3 = getLocalThreshold(blue, width, height, x, x2, y, y2);
				for (int newY = y; newY < y2; newY++) {
					int offset = newY * width;
					for (int newX = x; newX < x2; newX++) {
						if((red[offset+newX] & 0xff)<=t1 || 
								(green[offset+newX] & 0xff)<=t2 || 
								(blue[offset+newX] & 0xff)<=0.95*t3)
							binary.set(newX, newY);
					}
				}
			}
		}
		return binary;
	}
	
	private int getLocalThreshold(byte[] img, int width, int heigh, 
			int x1, int x2, int y1, int y2) {
		int max = 0;
		int min = 255;
		for (int y = y1; y < y2; y++) {
			int offset = y * width;
			for (int x = x1; x < x2; x++) {
				int v = img[offset+x] & 0xff;
				if(max < v)
					max = v;
				if(min > v)
					min = v;			
			}
		}
		return (int) ((max+min)/2.0+0.5);
	} 
	// tell if the input file is QDA or LDA
	private boolean isQDA(String modelName) throws IOException
	{
		int count = 0;
		InputStream is = new BufferedInputStream(new FileInputStream(modelName));
	    try {
	        byte[] c = new byte[1024];
	        int readChars = 0;
	        boolean empty = true;
	        while ((readChars = is.read(c)) != -1) {
	            empty = false;
	            for (int i = 0; i < readChars; ++i) {
	                if (c[i] == '\n') {
	                    ++count;
	                }
	            }
	        }
	        count = (count == 0 && !empty) ? 1 : count;
	    } finally {
	        is.close();
	    }
	    return (count > 20) ? true : false;
	}
	
	public int getClassifierIdx() {
		return this.classifierIdx;
	}
	
	
//	public static byte[] YUV2RGB(byte[] yuvPixel) {
//		byte[] rgb = new byte[3];
//		rgb[0] = (byte) (yuvPixel[0] + 1.370705 * (yuvPixel[2]-128));
//		rgb[1] = (byte) (yuvPixel[0] + 0.698001 * (yuvPixel[2]-128) - 0.337633 * (yuvPixel[1]-128));
//		rgb[2] = (byte) (yuvPixel[0] + 1.732446 * (yuvPixel[1]-128));
//		return rgb;
//	}
}
