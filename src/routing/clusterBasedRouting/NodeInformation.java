package routing.clusterBasedRouting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import routing.ClusterBasedRouter;
import core.DTNHost;
import core.SimClock;

/**
 * Class for cluster operations - Updation of node and its exemplar table information
 * for cluster based routing protocol
 * @author vijay
 *
 */
public class NodeInformation {
	double weightedFitness;
	double contactFitnessWithR;
	int contactFreqWithR;
	double contactFitnessWithNodeK;
	int currentTimeSlotNumber;
	double slotTimeInterval;
	DTNHost currentHost;
	DTNHost exemplarId;
	double cf_alpha;
	double wf_beta;
	double rf_gamma;
	double cf_thres;
	double wf_thres;
	double rf_thres;
	
	Map<DTNHost, Double> contactDurationWithNodeKMap;
	double contactDurationWithR;
	double lastUpdatedTime;
	double contactStartTimeWithR;
	Map<DTNHost, Double> contactStartTimeWithNodeKMap;
	
	private Map<DTNHost, ExemplarTableParams> exemplarTable;
	
	public NodeInformation(DTNHost host){
		this.exemplarTable = new HashMap<DTNHost,ExemplarTableParams>();
		this.contactDurationWithNodeKMap = new HashMap<DTNHost, Double>();
		this.contactFitnessWithR = 0;
		this.weightedFitness = 0;
		this.currentHost = host;
		this.exemplarId = host;
		contactDurationWithR = 0;
		this.contactStartTimeWithNodeKMap = new HashMap<DTNHost, Double>();
		//System.out.println("NODE INFORMATION CONSTRUCTOR: " + this.currentHost + " exemplarId: " + this.exemplarId);
	}
	
	/**
 	 * CALCULATION OF cF_{ij}
	 * 
	 * cF_ij^(T_cur)   = (1 - α) cF_ij^(T_old) +  α. cF_ij^(T_cur)
	 * 
	 * At any given time slot T_cur,
	 * 
	 * 				   ∑f=1^F  d_f
	 * cF_ij  =  	------------------
	 *					T_cur
	 *
	 * F be the number of times nodes S_i and S_j has met in time slot T_cur
	 * d_f be time duration for which S_i and S_j  remain in contact in f^th  meeting
	 *
	 *
	 * @param _cfAlpha
	 * @param _slotTimeInterval
	 */
	public void updateContactFitnessForAllNodesInExemplarTable(double _cfAlpha, double _slotTimeInterval, int debugMode){
		if(debugMode == 2 || debugMode == 3)
			System.out.println("SLOT TIMEOUT: Update Contact Fitness For All Nodes in Exemplar Table\n");
		
		this.cf_alpha = _cfAlpha;
		this.slotTimeInterval = _slotTimeInterval;
		
		for (Map.Entry<DTNHost, ExemplarTableParams> entry : getExemplarTable().entrySet()){
			DTNHost entryNodeId = entry.getKey();
			ExemplarTableParams row = entry.getValue();
			if(debugMode == 2 || debugMode == 3)
				System.out.println("TIMEOUT: contact Duration : "+currentHost+"  "+entryNodeId+"  "+row.getContactDurationWithNodeK());
			//calculate contact fitness for current time slot
			double currentSlotContactFitnessWithNodeK = row.getContactDurationWithNodeK()/slotTimeInterval;
			
			//reset the contact frequency with meeting node k for next timeslot
			row.setContactFreqWithNodeK(0);
			row.setContactDurationWithNodeK(0);
			
			double contactFitnessWithNodeKNotRounded = 0;
			
			if(currentSlotContactFitnessWithNodeK > 0)
			//calculate contact Fitness with meeting Node K using EWMA approach
				contactFitnessWithNodeKNotRounded = (1-cf_alpha) * row.getContactFitnessWithNodeK() + cf_alpha;
				//contactFitnessWithNodeKNotRounded = (1-cf_alpha) * row.getContactFitnessWithNodeK() + cf_alpha * currentSlotContactFitnessWithNodeK;
			else
				contactFitnessWithNodeKNotRounded = (1-cf_alpha) * row.getContactFitnessWithNodeK();
			
			contactFitnessWithNodeK = round(contactFitnessWithNodeKNotRounded,2);
			
			if(debugMode == 2 || debugMode == 3)
				System.out.println("TIMEOUT: cF(ik)_T => " + round(currentSlotContactFitnessWithNodeK,2));
			//Update the contact fitness of current node k in node i's exemplar table
			row.setContactFitnessWithNodeK(contactFitnessWithNodeK);
			
			exemplarTable.put(entryNodeId, row);
			
			if(debugMode == 2 || debugMode == 3)
				System.out.println("TIMEOUT: cF(ik) between nodes "+ getCurrentHost()+" - "+ entryNodeId+" =>" + contactFitnessWithNodeK);
		}
	}
	
	/**
	 * Case 1: If the contact fitness between node i and k decreases to less than the threshold, i.e.
	 * X(i) = k,     cFik < cFthres  
	 * =>	X(i) = i
	 * 
	 * Case 2: If the contact fitness of node i with node k in its EC table decreases to less than the threshold, i.e.
	 * Xik = i,       cFik < cFthres
	 * =>     Xik = k        ∀k   Xik = i 
	 *
	 * Case 3: If the weighted fitness of node i goes below the threshold, may be due to poor residual energy or poor connectivity with other nodes or rF(i) < rFthres 
	 * Xik = i,        wF(i) < wFthres
	 * =>     Xik = k        ∀k   Xik = i
	 * 
	 * @param _cfAlpha
	 */
	public void handleAllPossibleCasesAfterContactAndWeightedFitnessUpdation(double cfThres, double wfThres, int debugMode){
		
		if(debugMode == 1 || debugMode == 3)
			System.out.println("TIMEOUT: Handle All Possible Cases After Fitness Updation \n");
		//case 1
		if(currentHost != getExemplarId() &&
				getExemplarTable().get(getExemplarId()).getContactFitnessWithNodeK() < cfThres){
			if(debugMode == 1 || debugMode == 3)
				System.out.println("Case 1 Updation: Contact fitness goes below threshold");
			setExemplarId(currentHost);
		}
		
		else{
			for (Map.Entry<DTNHost, ExemplarTableParams> entry : getExemplarTable().entrySet()){
				DTNHost entryNodeId = entry.getKey();
				ExemplarTableParams row = entry.getValue();
				
				//case 2 or 3
				if(row.getExemplarId() == currentHost){
					if(row.getContactFitnessWithNodeK() < cfThres || getWeightedFitness() < wfThres ){
						if(debugMode == 1 || debugMode == 3)
							System.out.println("Case 2/3: CF or WF goes below threshold");
						row.setExemplarId(entryNodeId);
						getExemplarTable().put(entryNodeId, row);
					}
				}
				
			}
			
		}
	}
	
	
	/**Event 2: ME (say Nodes i and j meet):
	 * At Node i,
	 * 		If no entry for node j in EC(i)
	 * 				Make an entry with node j’s exemplar Id, cF = 0, wF, current timeslot and contact freq = 0
	 * 				
	 * 		If already existing entry for node j in EC Table
	 * 				Update current Timeslot  (Tij = Tcur ) and increase contact freq by 1
	 * 		For k ∈ EC(j)  \\ all entries k in EC Table of node j, 
	 * 				//If no entry for node k in EC(i)
	 * 						//Make an entry for node k in EC(i)
	 * 						//Update cFik = 0 \\ has not actually met
	 * 				If already existing entry for node k in EC(i)
	 * 						Update Tik = Tjk ,   if Tik  < Tjk   
	 * 
	 */
	public void updateExemplarTableForMeetingNode(DTNHost otherHost, NodeInformation otherNodeInf, boolean isInitiatorNode){
		
		if(otherNodeInf != null){
		
			ExemplarTableParams row;
			
			//No entry for node j in EC(i)
			if(!exemplarTable.containsKey(otherHost)){
				//create an empty row in exemplarTable of current Node
				 row = new ExemplarTableParams();
				
				//Update exemplar Id for meeting node 
				row.setExemplarId(otherNodeInf.getExemplarId());
				row.setContactFitnessWithNodeK(0);
				row.setWeightedFitness(otherNodeInf.getWeightedFitness());
				row.setCurrentTimeSlotNumber(currentTimeSlotNumber);
				row.setLastUpdatedTime(SimClock.getTime());
				row.setContactFreqWithNodeK(1);
				row.setContactDurationWithNodeK(getContactDurationWithNodeK(otherHost));
				
			}
			else{ //Entry for node j exists in EC(i)
				
				row = exemplarTable.get(otherHost);
				
//				double currentNodeLastUpdatedTime = row.getLastUpdatedTime();
//				double otherNodeLastUpdatedTime = otherNodeInf.getLastUpdatedTime();
				
//				if(currentNodeLastUpdatedTime < otherNodeLastUpdatedTime){
//					//Update exemplar Id for meeting node 
//					row.setExemplarId(otherNodeInf.getExemplarId());
//					row.setWeightedFitness(otherNodeInf.getWeightedFitness());
//					//Update timeslot
//					
//					
//				}
//				else{
//					otherNodeInf.setExemplarId(row.getExemplarId());
//					//otherNodeInf.setLastUpdatedTime(SimClock.getTime());
//				}
				
				row.setExemplarId(otherNodeInf.getExemplarId());
				row.setWeightedFitness(otherNodeInf.getWeightedFitness());
				//Update contact freq
				row.setContactFreqWithNodeK(row.getContactFreqWithNodeK() + 1);
				row.setContactDurationWithNodeK(row.getContactDurationWithNodeK() + getContactDurationWithNodeK(otherHost));
				row.setCurrentTimeSlotNumber(currentTimeSlotNumber);
				row.setLastUpdatedTime(SimClock.getTime());
		
				//otherNodeInf.setLastUpdatedTime(SimClock.getTime());
				//otherNodeInf.setCurrentTimeSlotNumber(currentTimeSlotNumber);
				
				}
					
			//First two conditions handled here
			exemplarTable.put(otherHost, row);
		
			Map<DTNHost, ExemplarTableParams> otherHostExemplarTable = otherNodeInf.getExemplarTable();
			
			//Now make necessary changes for the entries in the exemplar table
			for (Map.Entry<DTNHost, ExemplarTableParams> entry : otherHostExemplarTable.entrySet()){
				
				DTNHost entryNodeId = entry.getKey();
				ExemplarTableParams otherRow = entry.getValue();
				
				//Do not make an entry in the exemplar table for itself
				if(entryNodeId == this.currentHost || entryNodeId == otherHost){
					continue;
				}
				
				//If there exists no entry for node k 
				//If we do this, make sure that the existing entry in current node
				//doesn't get updated with 0 (because of otherHostExemplarTable)
//				if(!exemplarTable.containsKey(entryNodeId)){
//					//TODO: remove
//					//Set contact fitness to 0 - NOT MET
//					if(currentHost.toString().startsWith("n1"))
//						row.setContactFitnessWithNodeK(6);
//					else
//						row.setContactFitnessWithNodeK(-2);
//					row.setContactFreqWithNodeK(0);
//					
//				}
				
//				else //existing entry
//				{
					//TODO: remove
					//row.setContactFitnessWithNodeK(3);
				if(exemplarTable.containsKey(entryNodeId)){
					row = exemplarTable.get(entryNodeId);
					//int timeSlotAtNode =row.getCurrentTimeSlotNumber();
					//int timeSlotAtOtherNode = otherRow.getCurrentTimeSlotNumber();
					double simTimeAtNode = row.getLastUpdatedTime();
					double simTimeAtOtherNode = otherRow.getLastUpdatedTime();
					
					
					//otherRow.setLastUpdatedTime(SimClock.getTime());
					
					if(simTimeAtNode < simTimeAtOtherNode){
						row.setExemplarId(otherRow.getExemplarId());
						//row.setContactFitnessWithNodeK(otherRow.getContactFitnessWithNodeK());
						row.setWeightedFitness(otherRow.getWeightedFitness());
						row.setCurrentTimeSlotNumber(otherRow.getCurrentTimeSlotNumber());
						row.setLastUpdatedTime(otherRow.getLastUpdatedTime());
						// System.out.println("Update exemplar entry at node: "+ currentHost + " for entry Node: " + entryNodeId+"  " + row.getContactDurationWithNodeK());
						exemplarTable.put(entryNodeId, row);
					}
					
				}
						
			}
			//this.setExemplarTable(exemplarTable);
		}
		else{
			System.out.println("ME: otherNodeInf is null at node : "+ currentHost+ " - "+ otherHost);
		}
	}


//	public int getContactFreqWithNodeK(DTNHost otherHost) {
//		
//		if(!exemplarTable.containsKey(otherHost))
//			return 0;
//		else
//			return exemplarTable.get(otherHost).getContactFreqWithNodeK();
//	}

	public double getContactFitnessWithR(){
		return contactFitnessWithR;
	}
	
	public void setContactFitnessWithR(double _contactFitnessWithR){
		this.contactFitnessWithR = _contactFitnessWithR;
	}
	public int getContactFreqWithR() {
		return contactFreqWithR;
	}

	public void setContactFreqWithR(int _contactFreqWithR) {
		this.contactFreqWithR = _contactFreqWithR;
	}

	/**
	 * 
	 * @param _rf_gamma
	 * @param rfThres
	 * @param _slotTimeInterval
	 * 
	 * 
	 *  
	 * CALCULATION OF rF_(i)
	 * 
	 * rF_i^(T_cur)   = (1 - γ) rF_i^(T_old) +  γ. rF_i^(T_cur)
	 * 
	 * At any given time slot T_cur,
	 * 
	 * 				   		 ∑i=1^F d_i
	 * rF_i^(T_cur)  =  	------------------
	 *							T_cur
	 *
	 * F be the number of times nodes S_i has met with a responder node in time slot T_cur
	 * d_f be time duration for which S_i and a responder node remain in contact in f^th  meeting
	 *
	 */
	public void updateContactFitnessWithR(double _rf_gamma, double _slotTimeInterval, int  debugMode){
		this.rf_gamma = _rf_gamma;
		this.slotTimeInterval = _slotTimeInterval;
		
		if(debugMode == 2 || debugMode == 3)
			System.out.println("TIMEOUT:Contact Duration with R and "+ currentHost+ " : "+ getContactDurationWithR());

		//calculate responder contact fitness in current time slot
		double currentSlotContactFitnessWithR = (getContactDurationWithR())/slotTimeInterval;
		
		//reset the contact frequency with R
		setContactFreqWithR(0);
		setContactDurationWithR(0);
		
		//calculate contact Fitness with R using EWMA approach
		double contactFitnessWithRNotRounded = 0;
		
		if(currentSlotContactFitnessWithR > 0)
			contactFitnessWithRNotRounded = (1-rf_gamma) * getContactFitnessWithR() + rf_gamma;
		else
			contactFitnessWithRNotRounded = (1-rf_gamma) * getContactFitnessWithR();
		
		contactFitnessWithR = round(contactFitnessWithRNotRounded,2);
		
		this.setContactFitnessWithR(contactFitnessWithR);
		
		if(debugMode == 2 || debugMode == 3)
			System.out.println("TIMEOUT: rF_T  =>" + round(currentSlotContactFitnessWithR,2) +"   rF(i) => "+ + contactFitnessWithR);
	}
	
	public double getWeightedFitness(){
		return weightedFitness;
	}
	
	 /**CALCULATION OF wF(i)
	 * 
	 * wF_i^(T_cur)  = β(E_res^(T_cur)  - ECR(i) ) +  (1-β) K_i^(T_cur)   	if rF(i) ≥ rF_thres
	 *			  	 = 0 													otherwise
	 *
	 *
	 *					E_res^(T_cur) (i) - E_res^(T_old) (i)
	 * ECR(i) =     -----------------------------------------
	 *	     					T_cur - T_old
	 *
	 * K_i^(T_cur) = (1 - Ө) K_i^(T_old)  + Ө K_i^(T_cur)
	 *
	 *
	 * At given time slot T_cur,
	 *  
	 *					∑j=1^N |{ cF_ij(T_cur) | cF_ij(T_cur) ≥ cF_thres }|
	 * K_i^(T_cur)   = ----------------------------------------------------------
	 * 					∑j=1^N |{cF_ij^(T_cur) | cF_ij^(T_cur) > 0}|  + 2 
	 **/
	public void setWeightedFitness( double wfBeta, double rfThres, double totEnergy, double prevEnergy, double currEnergy, double slotTimeInterval, int debugMode){
		this.wf_beta = wfBeta;
		
		double prevEnergyInPerc = prevEnergy/totEnergy;
		double currEnergyInPerc = currEnergy/totEnergy;
		
		double energyConsumptionRate = (prevEnergyInPerc - currEnergyInPerc)/slotTimeInterval;
		
		if(getContactFitnessWithR()< rfThres ){
			weightedFitness = 0;
		}
		else
			weightedFitness = round((currEnergyInPerc*Math.exp(-1*energyConsumptionRate)),2);
		
		if(debugMode == 2 || debugMode == 3)
			System.out.println("TIMEOUT: For current Node =>"+ getCurrentHost()+" Update wF  => "+ weightedFitness );
		
	}
	
	public int getCurrentTimeSlotNumber(){
		return currentTimeSlotNumber;
	}
	
	public void  setCurrentTimeSlotNumber(int _currentTimeSlotNumber){
		
		currentTimeSlotNumber = _currentTimeSlotNumber;
		//System.out.println("TIMEOUT: Update time slot => " + currentTimeSlotNumber);
	}
	
	public DTNHost getCurrentHost() {
		return currentHost;
	}

	public void setCurrentHost(DTNHost currentHost) {
		this.currentHost = currentHost;
	}

	public DTNHost getExemplarId() {
		return exemplarId;
	}

	public void setExemplarId(DTNHost exemplarId) {
		this.exemplarId = exemplarId;
	}

	public Map<DTNHost, ExemplarTableParams> getExemplarTable(){
		return exemplarTable;
	}

	public double getContactFitnessWithNodeK(DTNHost otherHost) {
		if(this.exemplarTable.containsKey(otherHost)){
			ExemplarTableParams row = this.exemplarTable.get(otherHost);
			return row.getContactFitnessWithNodeK();
		}
		else 
			return 0;
	}

	public void showExemplarTableEntries() {
		//System.out.println(" For connection : " + this.getCurrentHost()+" - " + )
		System.out.println("ME: EC Table at Node: " + this.getCurrentHost());
		System.out.println("Weighted Fitness: " + this.getWeightedFitness());
		System.out.println("Exemplar Id: " + this.getExemplarId());
		System.out.println("Last Updated time: " + getLastUpdatedTime() +"\n" );
		System.out.println("Id :      " + "X_ID:   " + "cF:    " + " wf: "+ "  T " + " cFreq" + " cDur"+ "    lastUpdatedTime");
		for(Map.Entry<DTNHost,ExemplarTableParams > entry : this.exemplarTable.entrySet()){
			ExemplarTableParams row = entry.getValue();
			System.out.println(entry.getKey()+"    "+ row.getExemplarId()+"     "+row.getContactFitnessWithNodeK()+"     "+row.getWeightedFitness()+"    "+row.getCurrentTimeSlotNumber()+"   "+row.getContactFreqWithNodeK() +"     "+ row.getContactDurationWithNodeK()+ "    "+row.getLastUpdatedTime());
			
		}
		System.out.println("\n");
	}

	public void setExemplarTable(
			Map<DTNHost, ExemplarTableParams> exemplarTable2) {
		this.exemplarTable = exemplarTable2;
		
	}

	/**
	 * Store the contact duration for current meeting
	 * @param otherHost
	 * @return
	 */
	public double getContactDurationWithNodeK(DTNHost otherHost) {
		if(contactDurationWithNodeKMap.get(otherHost)!= null)
			return contactDurationWithNodeKMap.get(otherHost);
		else
			return 0;
	}

	public void setContactDurationWithNodeK(DTNHost otherHost, double contactDurationWithNodeK) {
		contactDurationWithNodeKMap.put(otherHost, contactDurationWithNodeK);
		//this.contactDurationWithNodeK = contactDurationWithNodeK;
	}

	public double getContactDurationWithR() {
		return contactDurationWithR;
	}

	public void setContactDurationWithR(double contactDurationWithR) {
		this.contactDurationWithR = contactDurationWithR;
	}


	public double getContactStartTimeWithR() {
		return contactStartTimeWithR;
	}

	public void setContactStartTimeWithR(double contactStartTimeWithR) {
		this.contactStartTimeWithR = contactStartTimeWithR;
	}

	public double getContactStartTimeWithNodeK(DTNHost otherHost) {
		if(contactStartTimeWithNodeKMap.get(otherHost) != null)
			return contactStartTimeWithNodeKMap.get(otherHost);
		
		else	
			return 0;
	}

	public void setContactStartTimeWithNodeK(DTNHost otherHost, double contactStartTimeWithNodeK) {
		this.contactStartTimeWithNodeKMap.put(otherHost, contactStartTimeWithNodeK);
	}

	public double getLastUpdatedTime() {
		return lastUpdatedTime;
	}

	public void setLastUpdatedTime(double lastUpdatedTime) {
		this.lastUpdatedTime = lastUpdatedTime;
	}

	/**
	 * Case 1: If Node j and its members can be assigned to Node i. However, Node i and 
	 * its members can not be assigned to Node j i.e. 
	 * 
	 * cFik ≥ cFthres 	∀k ∈ CjX(j)  and  
	 * ヨk’ ∈ CiX(i) 		cFk’j < cFthres
	 * 
	 * =>	X(j) = i,    X_j^k = i   ∀ k  ∈ C_j^X(j)
	 * @param otherHost
	 */
	public void assignAllClusterMembersToOtherNode(
			DTNHost otherHost, NodeInformation otherNodeInf) {
		
		this.setExemplarId(otherHost);
		this.setLastUpdatedTime(SimClock.getTime());
		
		//Update the nodes' exemplarId in current host's exemplar Table (whose exemplarId is currentHost) to otherHost
		for(Map.Entry<DTNHost, ExemplarTableParams> entry: this.exemplarTable.entrySet()){
			DTNHost entryNodeId = entry.getKey();
			ExemplarTableParams row = entry.getValue();
			
			//cluster member
			if(row.getExemplarId() == currentHost){
				row.setExemplarId(otherHost);
				row.setLastUpdatedTime(SimClock.getTime());
				this.exemplarTable.put(entryNodeId, row);
			}
		}
		//Update the nodes' exemplarId in other host's exemplar Table(whose exemplarId is currentHost) to otherHost
		for(Map.Entry<DTNHost, ExemplarTableParams> entry: otherNodeInf.exemplarTable.entrySet()){
			DTNHost entryNodeId = entry.getKey();
			ExemplarTableParams row = entry.getValue();
			
			//cluster member
			if(row.getExemplarId() == currentHost){
				row.setExemplarId(otherHost);
				row.setLastUpdatedTime(SimClock.getTime());
				otherNodeInf.exemplarTable.put(entryNodeId, row);
			}
		}
		
	}

	/**
	 * Check if all cluster members of current host can be assigned to other 
	 * @param otherHost
	 * @param otherNodeInf
	 * @param _cfAlpha
	 * @return
	 */
	public boolean checkIfAllClusterMembersCanBeAssignedToOther(DTNHost otherHost, NodeInformation otherNodeInf, double cfThres, double wfThres) {
		boolean canBeAssigned = true;
		
		if(otherNodeInf.getWeightedFitness() < wfThres){
			return false;
		}
		for(Map.Entry<DTNHost, ExemplarTableParams> entry: this.exemplarTable.entrySet()){
			DTNHost entryNodeId = entry.getKey();
			ExemplarTableParams row = entry.getValue();
			
			if(row.getExemplarId() == currentHost){
				//check if contact fitness of member node is greater than required threshold with new potential exemplar
				if(otherNodeInf.getExemplarTable().containsKey(entryNodeId) &&
						otherNodeInf.getExemplarTable().get(entryNodeId).getContactFitnessWithNodeK() > cfThres){
					continue;
				}
				else{
					canBeAssigned = false;
					break;
				}
			}
		}
		return canBeAssigned;
		
	}

	public double getLeastContactFitnessClusterMemberValue() {
		double leastCFMemberVal = 999;
		for(Map.Entry<DTNHost, ExemplarTableParams> entry: this.exemplarTable.entrySet()){
			DTNHost entryNodeId = entry.getKey();
			ExemplarTableParams row = entry.getValue();
			
			if(row.getExemplarId() == currentHost){
				if(row.getContactFitnessWithNodeK() < leastCFMemberVal){
					leastCFMemberVal = row.getContactFitnessWithNodeK();
				}
			}
		}
		return leastCFMemberVal;
	}


public double round(double d, int decimalPlace) {
    return BigDecimal.valueOf(d).setScale(decimalPlace,BigDecimal.ROUND_HALF_UP).doubleValue();
}
//	public void updateContactDurationAtMeetingNode(DTNHost otherHost,
//			NodeInformation nodeInf, double contactDuration) {
//		if(this.exemplarTable.containsKey(otherHost)){
//			this.exemplarTable.get(otherHost).setContactDurationWithNodeK(contactDuration);
//		}
//		System.out.println("After contact duration updation: "+ currentHost+ " - " + contactDuration + "\n");
//		showExemplarTableEntries();
//		
//	}

public void showContactDurationForOtherHosts() {
	System.out.println("Show contact duration for all other hosts at : " + currentHost);
	for(Map.Entry<DTNHost, Double> entry: this.contactDurationWithNodeKMap.entrySet()){
		System.out.println(currentHost +" - "+ entry.getKey()+ " => " + entry.getValue() );
	}
	
}

}

