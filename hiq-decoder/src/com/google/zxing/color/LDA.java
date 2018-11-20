package com.google.zxing.color;

/**
 * Copyright (c) 2014, Version, 0.2, Dr. Wolfgang Lenhard, Psychometrica.de
 * All rights reserved.
 *
 * This code serves for calculating a linear discriminant analysis (LDA) and it is based on the
 * tutorial of Kardi Teknomo (http://people.revoledu.com/kardi/tutorial/LDA/index.html). You will
 * need JAMA (A Java Matrix Package; http://math.nist.gov/javanumerics/jama/) to run this routines.
 * Many thanks go to David Cabanillas for fixing a bug in the method matrixMultiplication(double[] A, double[][] B).
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Dr. Wolfgang Lenhard, 2014
 * @version 0.1, 07/16/2014
 *
 * Quotation:
 * Lenhard, W. (2014). Realisation of Linear Discriminant Analysis in Java. Bibergau: Psychometrica.
 * 
 * 
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;

import Jama.Matrix;

public class LDA extends Classifier{
	private double[][] groupMean;
	private double[][] pooledInverseCovariance;
	private double[] probability;
	private ArrayList<Integer> groupList = new ArrayList<Integer>();

	/**
	 * Calculates a linear discriminant analysis (LDA) with all necessary
	 * 
	 * @param data
	 *            The data as double array. The array must have the same size as
	 *            the group array
	 * @param group
	 *            The membership in the different groups
	 * @param p
	 *            Set to true, if the probability estimation should be based on
	 *            the real group sizes (true), or if the share of each group
	 *            should be equal
	 */
	@SuppressWarnings("unchecked")
	public LDA(double[][] d, int[] g, boolean p) {
		this.setIsLayerable(false);
		// check if data and group array have the same size
		if (d.length != g.length)
			return;

		double[][] data = new double[d.length][d[0].length];
		for (int i = 0; i < d.length; i++) {
			for (int j = 0; j < d[i].length; j++) {
				data[i][j] = d[i][j];
			}
		}
		int[] group = new int[g.length];
		for (int j = 0; j < g.length; j++) {
			group[j] = g[j];
		}

		double[] globalMean;
		double[][][] covariance;

		// determine number and label of groups
		for (int i = 0; i < group.length; i++) {
			if (!groupList.contains(group[i])) {
				groupList.add(group[i]);
			}
		}

		// divide data into subsets
		ArrayList<double[]>[] subset = new ArrayList[groupList.size()];
		for (int i = 0; i < subset.length; i++) {
			subset[i] = new ArrayList<double[]>();
			for (int j = 0; j < data.length; j++) {
				if (group[j] == groupList.get(i)) {
					subset[i].add(data[j]);
				}
			}
		}

		// calculate group mean
		groupMean = new double[subset.length][data[0].length];
		for (int i = 0; i < groupMean.length; i++) {
			for (int j = 0; j < groupMean[i].length; j++) {
				groupMean[i][j] = getGroupMean(j, subset[i]);
			}
		}

		// calculate global mean
		globalMean = new double[data[0].length];
		for (int i = 0; i < data[0].length; i++) {
			globalMean[i] = getGlobalMean(i, data);
		}

		// correct subset data
		for (int i = 0; i < subset.length; i++) {
			for (int j = 0; j < subset[i].size(); j++) {
				double[] v = subset[i].get(j);

				for (int k = 0; k < v.length; k++)
					//v[k] = v[k] - globalMean[k];
					v[k] = v[k] - groupMean[i][k];

				subset[i].set(j, v);
			}
		}

		// calculate covariance
		covariance = new double[subset.length][globalMean.length][globalMean.length];
		for (int i = 0; i < covariance.length; i++) {
			for (int j = 0; j < covariance[i].length; j++) {
				for (int k = 0; k < covariance[i][j].length; k++) {
					for (int l = 0; l < subset[i].size(); l++)
						covariance[i][j][k] += (subset[i].get(l)[j] * subset[i]
								.get(l)[k]);
				}
			}
		}

		// calculate pooled within group covariance matrix and invert it
		pooledInverseCovariance = new double[globalMean.length][globalMean.length];
		for (int j = 0; j < pooledInverseCovariance.length; j++) {
			for (int k = 0; k < pooledInverseCovariance[j].length; k++) {
				for (int l = 0; l < subset.length; l++) {
					pooledInverseCovariance[j][k] += covariance[l][j][k] / (double) (data.length-subset.length);
				}
			}
		}

		pooledInverseCovariance = new Matrix(pooledInverseCovariance).inverse()
				.getArray();

		// calculate probability for different groups
		this.probability = new double[subset.length];
		if (!p) {
			double prob = 1.0d / groupList.size();
			for (int i = 0; i < groupList.size(); i++) {
				this.probability[i] = prob;
			}
		} else {
			for (int i = 0; i < subset.length; i++) {
				this.probability[i] = (double) subset[i].size()
						/ (double) data.length;
			}
		}
	}

	private double getGroupMean(int column, ArrayList<double[]> data) {
		double[] d = new double[data.size()];
		for (int i = 0; i < data.size(); i++) {
			d[i] = data.get(i)[column];
		}

		return getMean(d);
	}

	private double getGlobalMean(int column, double data[][]) {
		double[] d = new double[data.length];
		for (int i = 0; i < data.length; i++) {
			d[i] = data[i][column];
		}

		return getMean(d);
	}

	public LDA(double[][] groupMean,
			double[][] pooledInverseCovariance,
			int[] groupList,
			double[] probList) {
		this.setIsLayerable(false);
		this.groupMean = groupMean;
		this.pooledInverseCovariance = pooledInverseCovariance;
		for(int i=0; i<groupList.length; i++)
			this.groupList.add(groupList[i]);
		this.probability = probList;
	}
	/**
	 * Calculates the discriminant function values for the different groups
	 * 
	 * @param values
	 * @return
	 */
	public double[] getDiscriminantFunctionValues(double[] values) {
		double[] function = new double[groupList.size()];
		for (int i = 0; i < groupList.size(); i++) {
			double[] tmp = matrixMultiplication(groupMean[i],
					pooledInverseCovariance);
			function[i] = (matrixMultiplication(tmp, values))
					- (.5d * matrixMultiplication(tmp, groupMean[i]))
					+ Math.log(probability[i]);
		}

		return function;
	}

	/**
	 * Calculates the discriminant function values for the different groups based on Mahalanobis distance
	 * 
	 * @param values
	 * @return
	 */
	// TODO has to be tested yet
	public double[] getMahalanobisDistance(double[] values) {
		double[] function = new double[groupList.size()];
		for (int i = 0; i < groupList.size(); i++) {
			double[] dist = new double[groupMean[i].length];
			for (int j = 0; j < dist.length; j++)
				dist[j] = values[j] - groupMean[i][j];
			function[i] = matrixMultiplication(matrixMultiplication(dist,
					this.pooledInverseCovariance), dist);
		}

		return function;
	}

	/**
	 * Predict the membership of an object to one of the different groups based on Mahalanobis distance
	 * 
	 * @param values
	 * @return the group
	 */
	// TODO has to be tested yet
	public int predictM(double[] values) {
		int group = -1;
		double max = Double.NEGATIVE_INFINITY;
		double[] discr = this.getMahalanobisDistance(values);
		for (int i = 0; i < discr.length; i++) {
			if (discr[i] > max) {
				max = discr[i];
				group = groupList.get(i);
			}
		}

		return group;
	}

	/**
	 * Calculates the probability for the membership in the different groups
	 * 
	 * @param values
	 * @return the probabilities
	 */
	public double[] getProbabilityEstimates(double[] values) {
		// TODO
		return new double[] {};
	}

	/**
	 * Returns the weight for the linear fisher's discrimination functions
	 * 
	 * @return the weights
	 */
	public double[] getFisherWeights() {
		// TODO
		return new double[] {};
	}

	/**
	 * Predict the membership of an object to one of the different groups.
	 * 
	 * @param values
	 * @return the group
	 */
	public boolean[] predict(double[] values) {
		int group = -1;
		double max = Double.NEGATIVE_INFINITY;
		double[] discr = this.getDiscriminantFunctionValues(values);
		for (int i = 0; i < discr.length; i++) {
			if (discr[i] > max) {
				max = discr[i];
				group = groupList.get(i);
			}
		}
		
		boolean[] rgb = new boolean[3];
		rgb[0] = (group >> 2) % 2 == 0;
		rgb[1] = (group >> 1) % 2 == 0;
		rgb[2] = group % 2 == 0;
		
		return rgb;
	}

	/**
	 * Multiplies two matrices and returns the result as a double[][]-array.
	 * Please not, that the number of rows in matrix a must be equal to the
	 * number of columns in matrix b
	 * 
	 * @param a
	 *            the first matrix
	 * @param b
	 *            the second matrix
	 * @return the resulting matrix
	 */
	@SuppressWarnings("unused")
	private double[][] matrixMultiplication(final double[][] matrixA,
			final double[][] matrixB) {
		int rowA = matrixA.length;
		int colA = matrixA[0].length;
		int colB = matrixB[0].length;

		double c[][] = new double[rowA][colB];
		for (int i = 0; i < rowA; i++) {
			for (int j = 0; j < colB; j++) {
				c[i][j] = 0;
				for (int k = 0; k < colA; k++) {
					c[i][j] = c[i][j] + matrixA[i][k] * matrixB[k][j];
				}
			}
		}

		return c;
	}

	/**
	 * Multiplies two matrices and returns the result as a double[]-array.
	 * Please not, that the number of rows in matrix a must be equal to the
	 * number of columns in matrix b
	 * 
	 * @param a
	 *            the first matrix
	 * @param b
	 *            the second matrix
	 * @return the resulting matrix
	 */
	public double[] matrixMultiplication(double[] A, double[][] B) {

		if (A.length != B.length) {
			throw new IllegalArgumentException("A:Rows: " + A.length
					+ " did not match B:Columns " + B.length + ".");
		}

		double[] C = new double[A.length];
		for (int i = 0; i < C.length; i++) {
			C[i] = 0.00000;
		}

		for (int i = 0; i < A.length; i++) { // aRow
			for (int j = 0; j < B[0].length; j++) { // bColumn
				C[i] += A[j] * B[i][j];
			}
		}

		return C;
	}

	/**
	 * save trained LDA parameters to file
	 */
	public void saveModel(String filename) {
		int classNum = groupList.size();
	    int dim = groupMean[0].length;
		Writer writer = null;
		try {
		    writer = new BufferedWriter(new OutputStreamWriter(
		          new FileOutputStream(filename), "utf-8"));
		    String parameters = ""+classNum+" "+dim+"\n";
		    //save group list
		    for(int i=0; i<classNum; i++)
		    	parameters += groupList.get(i) + " ";
		    parameters += "\n";
		    // save probability (prior) list
		    for(int i=0; i<classNum; i++)
		    	parameters += this.probability[i] + " ";
		    parameters += "\n";
		    // save group means
		    for(int i = 0; i<classNum; i++) {
		    	for(int j = 0; j<dim; j++) {
		    		parameters += groupMean[i][j] + " ";
		    	}
		    	parameters += "\n";
		    }
		    // save covariance matrix
		    for(int i = 0; i<dim; i++) {
		    	for(int j = 0; j<dim; j++) {
		    		parameters += pooledInverseCovariance[i][j] + " ";
		    	}
		    	parameters += "\n";
		    }
		    writer.write(parameters);
		} catch (IOException ex) {
		  // report
		} finally {
		   try {writer.close();} catch (Exception ex) {}
		}
	}
	
	/**
	 * Multiplies two matrices and returns the result as a double (the second
	 * matrix is transposed automatically). Please note, that the number of rows
	 * in matrix a must be equal to the number of columns in matrix b
	 * 
	 * @param a
	 *            the first matrix
	 * @param b
	 *            the second matrix
	 * @return the resulting matrix
	 */
	private double matrixMultiplication(double[] matrixA, double[] matrixB) {

		double c = 0d;
		for (int i = 0; i < matrixA.length; i++) {
			c += matrixA[i] * matrixB[i];
		}

		return c;
	}

	/**
	 * Transposes a matrix
	 * 
	 * @param matrix
	 *            the matrix to transpose
	 * @return the transposed matrix
	 */
	@SuppressWarnings("unused")
	private double[][] transpose(final double[][] matrix) {
		double[][] trans = new double[matrix[0].length][matrix.length];
		for (int i = 0; i < matrix.length; i++) {
			for (int j = 0; j < matrix[0].length; j++) {
				trans[j][i] = matrix[i][j];
			}
		}

		return trans;
	}

	/**
	 * Transposes a matrix
	 * 
	 * @param matrix
	 *            the matrix to transpose
	 * @return the transposed matrix
	 */
	@SuppressWarnings("unused")
	private double[][] transpose(final double[] matrix) {
		double[][] trans = new double[1][matrix.length];
		for (int i = 0; i < matrix.length; i++) {
			trans[0][i] = matrix[i];
		}

		return trans;
	}

	/**
	 * Returns the mean of the given values. On error or empty data returns 0.
	 * 
	 * @param values
	 *            The values.
	 * @return The mean.
	 * @since 1.5
	 */
	public static double getMean(final double[] values) {
		if (values == null || values.length == 0)
			return Double.NaN;

		double mean = 0.0d;

		for (int index = 0; index < values.length; index++)
			mean += values[index];

		return mean / (double) values.length;
	}

	public static LDA getInstance(String filename) {
		double[][] means = null;
		double[][] covMat = null;
		int[] groupList = null;
		double[] probList = null;
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			// read classnum and dimension
			String[] items = br.readLine().split(" ");
			int classNum = Integer.parseInt(items[0]);
			int dim = Integer.parseInt(items[1]);
			
			means = new double[classNum][dim];
			covMat = new double[dim][dim];
			groupList = new int[classNum];
			probList = new double[classNum];
			// read group list
			items = br.readLine().split(" ");
			for(int i=0; i<classNum; i++) 
				groupList[i] = Integer.parseInt(items[i]);
			// read probability list
			items = br.readLine().split(" ");
			for(int i=0; i<classNum; i++) 
				probList[i] = Double.parseDouble(items[i]);
			// read means
			for(int i=0; i<classNum; i++) {
				items = br.readLine().split(" ");
				for(int j=0; j<dim; j++) {
					means[i][j] = Double.parseDouble(items[j]);
				}
			}
			// read covMat
			for(int i=0; i<dim; i++) {
				items = br.readLine().split(" ");
				for(int j=0; j<dim; j++) {
					covMat[i][j] = Double.parseDouble(items[j]);
				}
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
		return new LDA(means, covMat, groupList, probList);
	}
	
	/**
	 * Test case with the original values from the tutorial of Kardi Teknomo
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
//		int[] group = { 1, 1, 1, 1, 2, 2, 2 };
//		double[][] data = { { 2.95, 6.63, 3 }, { 2.53, 7.79, 4 }, { 3.57, 5.65, 4 },
//				{ 3.16, 5.47, 3 }, { 2.58, 4.46,9 }, { 2.16, 6.22,9 }, { 3.27, 3.52,9 } };
				
		int[] group = null;
		double[][] data = null;
		BufferedReader br = null;
		int dataNum = 0;
		String filename = "/Users/ouyangzhibo/gitlab/colorqrcode/matlab_src/data/feature_mixed.txt";
		try {
			br = new BufferedReader(new FileReader(filename));
			String line = br.readLine();
			dataNum = Integer.parseInt(line);
			data = new double[dataNum][3];
			
			line = br.readLine();
			int i = 0;
			for (; i < dataNum; i++) {
	            String[] items = line.split(" ");
	            data[i][0] = Double.parseDouble(items[0]);
	            data[i][1] = Double.parseDouble(items[1]);
	            data[i][2] = Double.parseDouble(items[2]);
	            line = br.readLine();
	        }
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		filename = "/Users/ouyangzhibo/gitlab/colorqrcode/matlab_src/data/label_mixed.txt";
		try {
			br = new BufferedReader(new FileReader(filename));
			String line = br.readLine();
			dataNum = Integer.parseInt(line);
			group = new int[dataNum];
			
			line = br.readLine();
			int i = 0;
			while (line != null) {
	            group[i] = Integer.parseInt(line);
	            line = br.readLine();
	            i++;
	        }
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		LDA test = new LDA(data, group, true);
		LDA test = LDA.getInstance("model_mixed_preview.txt");
		double[] testData = { 1, 1, 1 };
		
//		test.saveModel("model_mixed.txt");
		
		//test
		double[] values = test.getDiscriminantFunctionValues(testData);
		for(int i = 0; i < values.length; i++){
			System.out.println("Discriminant function " + (i+1) + ": " + values[i]);	
		}
		
		System.out.println("Predicted group: " + test.predict(testData));
	}

	@Override
	public double[] evaluate(double[] values) {
		// TODO Auto-generated method stub
		return null;
	}
}