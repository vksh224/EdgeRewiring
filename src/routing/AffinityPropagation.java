package routing;
import java.util.ArrayList;
import java.util.Collections;

import core.DTNHost;


public class AffinityPropagation {
	private int N= -1;
	private double[][] dataPoint;
	private double [][]S = new double[N][N];
	private double [][]R = new double[N][N];
	private double [][]A = new double[N][N];
	private int iter = 250;
	private double lambda = 0.9;
	
	private double max(double d, double e) {
		if( d > e){
			return d;
		}
		else
			return e;
	}
	private double min(double d, double e) {
		if( d < e){
			return d;
		}
		else
			return e;
	}
	private void setDataPoint(ArrayList<DTNHost> localHostList){
		N = localHostList.size();
		dataPoint = new double[N][N];
		int i = 0;
		for(DTNHost host: localHostList){
			dataPoint[i][0]= host.getContactFrequency();
			dataPoint[i][1]= host.getCurEnergy(); 
			i++;
		}
	}
	private void printSimilarityMatrix(){
		for(int i= 0; i< N; i++){
			for(int j = 0; j< N; j++){
				System.out.println(S[i][j]+" ");
			}
			System.out.print("\n\n\n");
		}
	}
	public void readS(){
		int size = N * (N -1)/2;
		ArrayList<Double > tmpS = new ArrayList<Double>();
		//compute similarity between data point i and j
		//(i is not equal to j)
		//s[i][j] - node i is more similar to node j
		for(int i=0; i < N ; i++){
			for(int j = 0; j < N; j++){
				
				if(dataPoint[j][0] == 0 || dataPoint[j][1]==0){
					S[i][j] = -(dataPoint[i][0]*10
							+(dataPoint[i][1]));
				}
				if(dataPoint[j][0] != 0 && dataPoint[j][1]!=0){
					S[i][j] = -((dataPoint[i][0]*10
							+dataPoint[i][1])/( dataPoint[j][0]*10 + dataPoint[j][1]));
				}
			
				//S[j][i] = S[i][j];
				tmpS.add(S[i][j]);
				
			}
		}
		Collections.sort(tmpS);
		double median = 0;
		if(size%2 == 0)
			median = (tmpS.get(size/2) + tmpS.get(size/2 - 1))/2;
		else
			median = (tmpS.get(size/2));
		for (int i = 0; i < N ; i++){
			if(dataPoint[i][0]>0 && dataPoint[i][1]>0)
			S[i][i] = median;
			else
				S[i][i] = -9999;
		}
	}
	private void updateResponsibility(){
			//update responsibility
			for(int i = 0;i< N; i++){
				for(int k=0; k< N; k++){
					double max = -9999999;
					for(int kk=0; kk < k ; kk++){
						if(S[i][kk] + A[i][kk] > max)
							max = S[i][kk] + A[i][kk];
					}
					for(int kk = k+1 ; kk < N; kk++){
						if(S[i][kk] + A[i][kk] > max)
							max = S[i][kk] +A[i][kk];
					}
					R[i][k] = (1 - lambda) *(S[i][k] - max) + lambda * R[i][k];
				}
			}
		}
	private void updateAvailability(){
		for (int i= 0; i < N; i++ ){
			for (int k= 0; k< N ; k++){
				if( i == k){
					double sum = 0.0;
					for (int ii = 0; ii < i; ii++){
						sum += max(0.0 , R[ii][k]);
					}
					for (int ii =i+1; ii < N; ii++){
						sum += max(0.0 , R[ii][k]);
					}
					A[i][k] = (1 - lambda) *sum + lambda *A[i][k];
				}else{
					double sum = 0.0;
					int maxik = (int) max(i,k);
					int minik = (int) min(i,k);
					
					for(int ii = 0; ii < minik; ii++){
						sum += max(0.0, R[ii][k]);
					}
					for(int ii= minik +1; ii <maxik ;ii++){
						sum += max(0.0, R[ii][k]);
					}
					for (int ii= maxik +1; ii <N; ii++){
						sum += max(0.0 , R[ii][k]);
					}
					A[i][k] = (1 - lambda) *min(0.0, R[k][k]+sum) + lambda *A[i][k];
					
					
				}
			}
		}
	}
	private void findExemplar(){
		double [][]E = new double [N][N];
		ArrayList< Integer> center = new ArrayList<Integer>();
		for (int i =0; i < N; i++){
			E[i][i] = R[i][i]  +  A[i][i];
			if(E[i][i] > 0){
				center.add(i);
			}
		}
		
		//data point assignment, idx[i] is the exemplar for data point i
		int []idx = new int [25];
		for( int i = 0; i < N; i++){
			int idxForI = 0;
			double maxSim = -99999999;
			for (int j = 0 ; j < center.size() ; j++){
				int c = center.get(j);
				if(S[i][c] > maxSim){
					maxSim = S[i][c];
					idxForI = c;
				}
			}
			idx[i] = idxForI;
		}
		//output the assignment
		for (int i = 0; i < N; i++){
			System.out.println(idx[i] + 1);
		}
	}
	
	public void updateRelayExemplarsAP(ArrayList<DTNHost> localHostList){
		setDataPoint(localHostList);
		readS();
		System.out.println("\n Similarity matrix is: \n");
		printSimilarityMatrix();
		for (int m = 0; m < iter ; m++){
			updateResponsibility();
			updateAvailability();
		}
		findExemplar();
		System.out.println("Hello");
	}
}
