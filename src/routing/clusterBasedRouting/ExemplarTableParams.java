package routing.clusterBasedRouting;

import core.DTNHost;

public class ExemplarTableParams {
	DTNHost exemplarId;
	double contactFitnessWithNodeK;
	double weightedFitness;
	int currentTimeSlotNumber;
	int contactFreqWithNodeK;
	double contactDurationWithNodeK;
	double lastUpdatedTime;
	
	public ExemplarTableParams(){
		exemplarId= null;
		contactFitnessWithNodeK = 0;
		weightedFitness = 0;
		currentTimeSlotNumber = 0;
		contactFreqWithNodeK = 0;
		contactDurationWithNodeK = 0;
	}
	

	public ExemplarTableParams(DTNHost _exemplarId, double _cF, double _wF, int _timeSlotNr){
		exemplarId= _exemplarId;
		contactFitnessWithNodeK = _cF;
		weightedFitness = _wF;
		currentTimeSlotNumber = _timeSlotNr;
		contactFreqWithNodeK = 0;
		contactDurationWithNodeK = 0;
	}
	
	public DTNHost getExemplarId() {
		return exemplarId;
	}
	public void setExemplarId(DTNHost exemplarId) {
		this.exemplarId = exemplarId;
	}
	public double getContactFitnessWithNodeK() {
		return contactFitnessWithNodeK;
	}
	public void setContactFitnessWithNodeK(double _contactFitnessWithNodeK) {
		this.contactFitnessWithNodeK = _contactFitnessWithNodeK;
	}
	public double getWeightedFitness() {
		return weightedFitness;
	}
	public void setWeightedFitness(double weightedFitness) {
		this.weightedFitness = weightedFitness;
	}
	public int getCurrentTimeSlotNumber() {
		return currentTimeSlotNumber;
	}
	public void setCurrentTimeSlotNumber(int currentTimeSlotNumber) {
		this.currentTimeSlotNumber = currentTimeSlotNumber;
	}


	public int getContactFreqWithNodeK() {
		// TODO Auto-generated method stub
		return this.contactFreqWithNodeK;
	}
	
	public void setContactFreqWithNodeK(int _contactFreqWithNodeK){
		this.contactFreqWithNodeK = _contactFreqWithNodeK;
	}

	public double getContactDurationWithNodeK() {
		return contactDurationWithNodeK;
	}


	public void setContactDurationWithNodeK(double contactDurationWithNodeK) {
		this.contactDurationWithNodeK = contactDurationWithNodeK;
	}


	public double getLastUpdatedTime() {
		return lastUpdatedTime;
	}


	public void setLastUpdatedTime(double lastUpdatedTime) {
		this.lastUpdatedTime = lastUpdatedTime;
	}


}
