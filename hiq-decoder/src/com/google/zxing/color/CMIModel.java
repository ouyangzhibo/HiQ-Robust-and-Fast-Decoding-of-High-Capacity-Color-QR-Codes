package com.google.zxing.color;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class CMIModel extends Classifier{

	private double[][] module_weights;
	private Classifier innerClassifier;
	private static final String QDA_CMI_COEF_PATH = "./candidate_classifiers/Qmodel_CMI_coef.txt";
	private static final String LSVM_CMI_COEF_PATH = "./candidate_classifiers/LSVM_CMI_coef.txt";
	
	public CMIModel(Classifier innerClassifier, double[][] module_weights) {
		super();
		this.setCMIFlag(true);
		this.module_weights = module_weights;
		this.innerClassifier = innerClassifier;
	}
	
	public static CMIModel getInstance(String filename, String modelName) {
		// read cross-module interference coefficients
		double[][] m = null;
		String CMICoefPath = null;
		if (modelName.equals("QDA"))
			CMICoefPath = QDA_CMI_COEF_PATH;
		else if (modelName.equals("LSVM"))
			CMICoefPath = LSVM_CMI_COEF_PATH;
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(CMICoefPath));
			int lineNum = Integer.parseInt(br.readLine());
			m = new double[lineNum][];
			for (int i = 0; i < lineNum; i++) {
				String[] items = br.readLine().split(" ");
				m[i] = new double[items.length];
				for(int j=0; j<items.length; j++) 
					m[i][j] = Double.parseDouble(items[j]);
			}
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// read model file
		if (modelName.equals("QDA"))
			return new CMIModel(QDA.getInstance(filename), m);
		else if (modelName.equals("LSVM"))
			return new CMIModel(LayeredSVM.getInstance(filename), m);
		else 
			return null;
		
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
		int featDim = values.length / module_weights[0].length;
		double[] newValues = new double[featDim];
		
		if (module_weights.length == 1) // non-layered model
			for (int i = 0; i < featDim; i++)
				for (int j = 0; j < module_weights.length; j++)
					newValues[i] += module_weights[0][j] * values[j*featDim+i];
		else if (module_weights.length == 3) // layered model
			for (int i = 0; i < featDim; i++)
				for (int j = 0; j < module_weights.length; j++)
					newValues[i] += module_weights[i][j] * values[j*featDim+i];
		
		return newValues;
	}
}
