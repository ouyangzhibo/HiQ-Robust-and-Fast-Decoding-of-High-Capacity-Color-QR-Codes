package com.google.zxing.color;

public abstract class Classifier {
	
	private boolean isLayerable;
	private boolean useCMI;
	
	abstract public boolean[] predict(double[] values);
	
	// evaluate the capability of the the classifier
	abstract public double[] evaluate(double[] values);
	
	public boolean predict_layer(double[] values, int layerIdx) {
		/*
		 * to be implemented in layered SVM
		 */
		return false;
	}
	
	public boolean getIsLayerable() {
		return isLayerable;
	}
	
	public boolean getCMIFlag() {
		return useCMI;
	}
	
	public void setIsLayerable(boolean isLayerable) {
		this.isLayerable = isLayerable;
	}
	
	public void setCMIFlag(boolean useCMI) {
		this.useCMI = useCMI;
	}
}
