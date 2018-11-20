package com.google.zxing.color;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class LayeredSVMCMI extends Classifier{
	
	private double[] module_weights;
	private Classifier innerClassifier;
	
	public LayeredSVMCMI(int layerNum, double[][] w, double[] b, double[] m) {
		this.innerClassifier = new LayeredSVM(layerNum, w, b);
		this.setCMIFlag(true);
		this.module_weights = m;
	}
	
	public static LayeredSVMCMI getInstance(String filename) {
		int layerNum = 0;
		double[][] w = null;
		double[] b = null;
		double[] m = null;
		
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
			
			// read m
			String[] items = br.readLine().split(" ");
			m = new double[items.length];
			for(int j=0; j<items.length; j++) 
				m[j] = Double.parseDouble(items[j]);
			
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
		
		return new LayeredSVMCMI(layerNum, w, b, m);
	}

	@Override
	public boolean[] predict(double[] values) {
		double[] newValues= null;
		if (values.length == 15)
			newValues = cancelCMI(values);
		else 
			newValues = values;
		return this.innerClassifier.predict(newValues);
	}

	@Override
	public double[] evaluate(double[] values) {
		double[] newValues= null;
		if (values.length == 15)
			newValues = cancelCMI(values);
		else 
			newValues = values;
		return this.innerClassifier.evaluate(newValues);
	}
	
	private double[] cancelCMI(double[] values) {
		int featDim = values.length / module_weights.length;
		double[] newValues = new double[featDim];
		for (int i = 0; i < featDim; i++)
			for (int j = 0; j < module_weights.length; j++)
				newValues[i] += module_weights[j] * values[j*featDim+i];
		return newValues;
	}
}
