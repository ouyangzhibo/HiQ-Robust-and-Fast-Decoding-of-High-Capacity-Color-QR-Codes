package com.google.zxing.color;

public class SimpleClassifier extends Classifier{
	private static final double T = 0.5f;
	private int layerNum = 3;
	
	public SimpleClassifier(int layerNum) {
		super();
		this.layerNum = layerNum;
	}

	@Override
	public boolean[] predict(double[] values) {
		boolean[] prediction = new boolean[layerNum];
		for (int i = 0; i < layerNum; i++)
			prediction[i] = values[i] < T;
		return prediction;
	}

	@Override
	public double[] evaluate(double[] values) {
		// TODO Auto-generated method stub
		return null;
	}

}
