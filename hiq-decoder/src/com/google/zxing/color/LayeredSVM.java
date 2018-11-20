package com.google.zxing.color;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


public class LayeredSVM extends Classifier{
	private final int layerNum;
	private double[][] w;
	private double[] b;
//	private final boolean isLogarithm = true;
	
	public LayeredSVM(int layerNum, double[][] w, double[] b) {
		this.setIsLayerable(true);
		this.setCMIFlag(false);
		this.layerNum = layerNum;
		this.w = w;
		this.b = b;
	}
	
	public void setWeight(double[] weight, int i){
		this.w[i] = weight;
	}

	public void setBias(double bias, int i){
		this.b[i] = bias;
	}
	
	public double[] getWeight(int i){
		return this.w[i];
	}

	public double getBias(int i){
		return this.b[i];
	}
	
	@Override
	public boolean[] predict(double[] values) {
		// TODO Auto-generated method stub
		boolean[] prediction = new boolean[this.layerNum];
		for(int i = 0; i < this.layerNum; i++)
			prediction[i] = svm_predict(values, i);
		
		return prediction;
	}
	
	
	public boolean predict_layer(double[] values, int layerIdx){
		return svm_predict(values, layerIdx);
	}
	
	private boolean svm_predict(double[] x, int layerIdx) {
		return (svm_predict_value(x, layerIdx) < 0);
	}
	
	private double svm_predict_value(double[] x, int layerIdx) {
		double[] weights = w[layerIdx];
		if (weights.length == 10)
			x = this.convt2poly(x);
		else if (weights.length == 20)
			x = this.convt2poly3(x);
		double y = 0;
		for (int i = 0; i < weights.length; i++)
			y += weights[i] * x[i];
		y -= this.b[layerIdx];
		return y;
	}
	
	public static LayeredSVM getInstance(String filename) {
		int layerNum = 0;
		double[][] w = null;
		double[] b = null;
		
		// read parameters from file
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			// read layerNum
			layerNum = Integer.parseInt(br.readLine());
			
			w = new double[layerNum][];
			b = new double[layerNum];
			
			// read w and b
			for (int i = 0; i < layerNum; i++) {
				// read wi
				int dim = Integer.parseInt(br.readLine());
				double[] wi = new double[dim];
				String[] items = br.readLine().split(" ");
				for(int j=0; j<dim; j++) 
					wi[j] = Double.parseDouble(items[j]);
				w[i] = wi;
				// read b
				b[i] = Double.parseDouble(br.readLine());
			}
			
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		return new LayeredSVM(layerNum, w, b);
	}
	
	public double[] convt2poly(double[] x){
		double[] x_new = new double[10];
		
		x_new[0] = 1;
		for (int i = 1; i<x.length+1; i++) {
			x_new[i] = x[i-1];
			x_new[i+x.length] = x[i-1]*x[i-1];
		}
		
		x_new[7] = x[0]*x[1];
		x_new[8] = x[1]*x[2];
		x_new[9] = x[0]*x[2];
		
		return x_new;
	}
	
	public double[] convt2poly3(double[] x){
		double[] x_new = new double[20];
		
		x_new[0] = 1;
		for (int i = 1; i<x.length+1; i++) {
			x_new[i] = x[i-1];
			x_new[i+x.length] = x[i-1]*x[i-1];
			x_new[i+2*x.length] = x[i-1]*x[i-1]*x[i-1];
		}
		
		x_new[10] = x[0]*x[1];
		x_new[11] = x[1]*x[2];
		x_new[12] = x[0]*x[2];
		
		x_new[13] = x[0]*x[0]*x[1];
		x_new[14] = x[1]*x[1]*x[2];
		x_new[15] = x[0]*x[0]*x[2];
	
		x_new[16] = x[0]*x[1]*x[1];
		x_new[17] = x[1]*x[2]*x[2];
		x_new[18] = x[0]*x[2]*x[2];
		
		x_new[19] = x[0]*x[1]*x[2];
		
		return x_new;
	}
	
	@Override
	public double[] evaluate(double[] values) {
		// TODO Auto-generated method stub
		double[] prediction = new double[this.layerNum];
		for(int i = 0; i < this.layerNum; i++)
			prediction[i] = svm_predict_value(values, i);

		return prediction;
	}
	
	public static void main(String[] args) {
		LayeredSVM test = getInstance("layeredSVMs_ip6plus.txt");
		double[] testData = { 1, 1, 1 };
		
		boolean[] prediction = test.predict(testData);
		System.out.println("prediction=["+prediction[0]+","+prediction[1]+","+prediction[2]+"]");
	}
}
