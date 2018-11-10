/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import routing.clusterBasedRouting.ExemplarTableParams;
import routing.clusterBasedRouting.NodeInformation;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.ModuleCommunicationBus;
import core.ModuleCommunicationListener;
import core.NetworkInterface;
import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.SimScenario;
import core.Tuple;

/**
 * Energy level-aware variant of Epidemic router.
 */
public class ClusterBasedRouter extends ActiveRouter 
		implements ModuleCommunicationListener{
	/** Initial units of energy -setting id ({@value}). Can be either a 
	 * single value, or a range of two values. In the latter case, the used
	 * value is a uniformly distributed random value between the two values. */
	public static final String INIT_ENERGY_S = "initialEnergy";
	/** Energy usage per scanning -setting id ({@value}). */
	public static final String SCAN_ENERGY_S = "scanEnergy";
	/** Energy usage per second when sending -setting id ({@value}). */
	public static final String TRANSMIT_ENERGY_S = "transmitEnergy";
	/** Energy update warmup period -setting id ({@value}). Defines the 
	 * simulation time after which the energy level starts to decrease due to 
	 * scanning, transmissions, etc. Default value = 0. If value of "-1" is 
	 * defined, uses the value from the report warmup setting 
	 * {@link report.Report#WARMUP_S} from the namespace 
	 * {@value report.Report#REPORT_NS}. */
	public static final String WARMUP_S = "energyWarmup";

	/** {@link ModuleCommunicationBus} identifier for the "current amount of 
	 * energy left" variable. Value type: double */
	public static final String ENERGY_VALUE_ID = "Energy.value";
	public static final String IS_ENERGY_CONSTRAINED = "isEnergyConstrained";
	public static final String TIME_SLOT_INTERVAL = "timeSlotInterval";
	public static final String CF_ALPHA ="cFAlpha";
	public static final String WF_BETA = "wFBeta";
	public static final String RF_GAMMA = "rFGamma";
	public static final String CF_THRESHOLD = "cFThreshold";
	public static final String WF_THRESHOLD = "wFThreshold";
	public static final String RF_THRESHOLD = "rFThreshold";
	public static final String PREVIOUS_ENERGY_VALUE="PreviousEnergy.value";
	public static final String TOTAL_ENERGY_VALUE="TotalEnergy.value";
	public static final String DEBUG_MODE="debugMode";
	
	
	
	private final double[] initEnergy;
	private double warmupTime;
	private double currentEnergy;
	/** energy usage per scan */
	private double scanEnergy;
	private double transmitEnergy;
	private double lastScanUpdate;
	private double lastUpdate;
	private double scanInterval;	
	private ModuleCommunicationBus comBus;
	private static Random rng = null;
	private int isEnergyConstrained = 1;
	
	private double slotTimeInterval;
	private double lastTimeSlot;
	private int currentTimeSlotNumber;
	
	private NodeInformation nodeInf;
	private Map<DTNHost , NodeInformation> allNodeInfs;
	private double cf_alpha; // weightage to previous contact fitness value
	private double wf_beta; // weightage to residual energy in weighted fitness value
	private double rf_gamma; //weightage to previous contact fitness with R
	private double cf_thres;
	private double wf_thres;
	private double rf_thres;
	
	private double previousEnergy;
	private double totalEnergy;
	private int debugMode;
		
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public ClusterBasedRouter(Settings s) {
		super(s);
		this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
		
		if (this.initEnergy.length != 1 && this.initEnergy.length != 2) {
			throw new SettingsError(INIT_ENERGY_S + " setting must have " + 
					"either a single value or two comma separated values");
		}
		
		this.scanEnergy = s.getDouble(SCAN_ENERGY_S);
		this.transmitEnergy = s.getDouble(TRANSMIT_ENERGY_S);
		this.scanInterval  = s.getDouble(SimScenario.SCAN_INTERVAL_S);
			
		if (s.contains(WARMUP_S)) {
			this.warmupTime = s.getInt(WARMUP_S);
			if (this.warmupTime == -1) {
				this.warmupTime = new Settings(report.Report.REPORT_NS).
					getInt(report.Report.WARMUP_S);
			}
		}
		else {
			this.warmupTime = 0;
		}
		this.isEnergyConstrained = s.getInt(IS_ENERGY_CONSTRAINED);
		
		if(s.contains(TIME_SLOT_INTERVAL)){
			this.slotTimeInterval = s.getDouble(TIME_SLOT_INTERVAL);
		}
		else{
			this.slotTimeInterval = 30;
		}
			
		if(s.contains(CF_ALPHA)){
			this.cf_alpha = s.getDouble(CF_ALPHA);
		}
		else{
			this.cf_alpha = 0.9;
		}
		
		if(s.contains(WF_BETA)){
			this.wf_beta = s.getDouble(WF_BETA);
		}
		else{
			this.wf_beta = 0.9;
		}
		if(s.contains(RF_GAMMA)){
			this.rf_gamma = s.getDouble(RF_GAMMA);
		}
		else{
			this.rf_gamma = 0.9;
		}
		if(s.contains(CF_THRESHOLD)){
			this.cf_thres = s.getDouble(CF_THRESHOLD);
		}
		else{
			this.cf_thres = 0.1;
		}
		if(s.contains(WF_THRESHOLD)){
			this.wf_thres = s.getDouble(WF_THRESHOLD);
		}
		else{
			this.wf_thres = 0.1;
		}
		if(s.contains(RF_THRESHOLD)){
			this.rf_thres = s.getDouble(RF_THRESHOLD);
		}
		else{
			this.rf_thres = 0.1;
		}
		if(s.contains(DEBUG_MODE)){
			this.debugMode = s.getInt(DEBUG_MODE);
		}
		else{
			this.debugMode = 0;
		}
		//System.out.println("Debug Mode is: " + this.debugMode);
		this.currentTimeSlotNumber = 0;
	}
	
	/**
	 * Sets the current energy level into the given range using uniform 
	 * random distribution.
	 * 
	 * @param range The min and max values of the range, or if only one value
	 * is given, that is used as the energy level
	 */
	protected void setEnergy(double range[]) {
		if (range.length == 1) {
			this.currentEnergy = range[0];
		}
		else {
			if (rng == null) {
				rng = new Random((int)(range[0] + range[1]));
			}
			this.currentEnergy = range[0] + 
				rng.nextDouble() * (range[1] - range[0]);
		}
		this.previousEnergy = this.currentEnergy;
		this.totalEnergy = this.currentEnergy;
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected ClusterBasedRouter(ClusterBasedRouter r) {
		super(r);
		this.initEnergy = r.initEnergy;
		setEnergy(this.initEnergy);
		this.scanEnergy = r.scanEnergy;
		this.transmitEnergy = r.transmitEnergy;
		this.scanInterval = r.scanInterval;
		this.warmupTime  = r.warmupTime;
		this.comBus = null;
		this.lastScanUpdate = r.lastScanUpdate;
		this.lastUpdate = r.lastUpdate;
		this.isEnergyConstrained = r.isEnergyConstrained;
		this.slotTimeInterval = r.slotTimeInterval;
		this.nodeInf = new NodeInformation(this.getHost());
		this.allNodeInfs = new HashMap<DTNHost, NodeInformation>();
		this.currentTimeSlotNumber = r.currentTimeSlotNumber;
		this.cf_alpha = r.cf_alpha;
		this.wf_beta = r.wf_beta;
		this.rf_gamma = r.rf_gamma;
		this.cf_thres = r.cf_thres;
		this.wf_thres = r.wf_thres;
		this.rf_thres = r.rf_thres;
		this.debugMode = r.debugMode;
	
	}
	
	@Override
	protected int checkReceiving(Message m) {
		if (this.currentEnergy < 0) {
			return DENIED_UNSPECIFIED;
		}
		else {
			 return super.checkReceiving(m);
		}
	}
	
	/**
	 * Updates the current energy so that the given amount is reduced from it.
	 * If the energy level goes below zero, sets the level to zero.
	 * Does nothing if the warmup time has not passed.
	 * @param amount The amount of energy to reduce
	 */
	protected void reduceEnergy(double amount) {
		if (SimClock.getTime() < this.warmupTime) {
			return;
		}
		
		comBus.updateDouble(ENERGY_VALUE_ID, -amount);
		if (this.currentEnergy < 0) {
			comBus.updateProperty(ENERGY_VALUE_ID, 0.0);
		}
	}
	
	/**
	 * Reduces the energy reserve for the amount that is used by sending data
	 * and scanning for the other nodes. 
	 */
	protected void reduceSendingAndScanningEnergy() {
		double simTime = SimClock.getTime();
		
		if(this.isEnergyConstrained == 2){
			
			if (this.comBus == null) {
				this.comBus = getHost().getComBus();
			}
			
			if (this.comBus.getProperty(ENERGY_VALUE_ID)== null) {
				this.comBus.addProperty(ENERGY_VALUE_ID, this.currentEnergy);
				this.comBus.subscribe(ENERGY_VALUE_ID, this);
			}
			
			if(this.comBus.getProperty(PREVIOUS_ENERGY_VALUE)==null){
				this.comBus.addProperty(PREVIOUS_ENERGY_VALUE, this.previousEnergy);
				this.comBus.subscribe(PREVIOUS_ENERGY_VALUE, this);
			}
			
			if(this.comBus.getProperty(TOTAL_ENERGY_VALUE)==null){
				this.comBus.addProperty(TOTAL_ENERGY_VALUE, this.totalEnergy);
				this.comBus.subscribe(TOTAL_ENERGY_VALUE, this);
			}
			
			if (this.currentEnergy <= 0) {
				/* turn radio off */
				this.comBus.updateProperty(NetworkInterface.RANGE_ID, 0.0);
				return; /* no more energy to start new transfers */
			}
			
			//System.out.println("Outside In CBR: The size of sending connections: "+ sendingConnections.size());
			
			if (getHost().toString().startsWith("n") && simTime > this.lastUpdate && sendingConnections.size() > 0) {
				//System.out.println("Inside In CBR: The size of sending connections: "+ sendingConnections.size());
				/* sending data */
				reduceEnergy((simTime - this.lastUpdate) * this.transmitEnergy);
				//reduceEnergy(this.transmitEnergy);
			}
			this.lastUpdate = simTime;
			
			if (getHost().toString().startsWith("n") && simTime > this.lastScanUpdate + this.scanInterval) {
				/* scanning at this update round */
				reduceEnergy(this.scanEnergy);
				this.lastScanUpdate = simTime;
			}
		}
	}

	
	/**
	 * Specific to disaster response network
	 */
	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * hop counts and their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	protected Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts that are not transferring at the moment,
		 * collect all the messages that could be sent */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			ClusterBasedRouter othRouter = (ClusterBasedRouter)other.getRouter();
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			for (Message m : msgCollection) {
				
				/* skip messages that the other host has or that have
				 * passed the other host */
				if (othRouter.hasMessage(m.getId()) ||
						m.getHops().contains(other)) {
					continue; 
				}
				//System.out.println("Outside At Timeslot: " + SimClock.getTime()+" Message: " + m.getId() + " From: "+ m.getFrom()+" To: "+ m.getTo() +" fromNode: "+getHost()+" toHost: "+con.getOtherNode(getHost()));
				
				if(shouldMessageBeSent(m,con, othRouter)){
				//	System.out.println("At Timeslot: " + SimClock.getTime()+" Message: " + m.getId() + " From: "+ m.getFrom()+" To: "+ m.getTo() +" fromNode: "+getHost()+" toHost: "+con.getOtherNode(getHost()));
					
					messages.add(new Tuple<Message, Connection>(m,con));
				}
				
			}			
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		return tryMessagesForConnected(messages);	
	}
	
	protected boolean shouldMessageBeSent(Message m, Connection con, ClusterBasedRouter othRouter) {
		boolean canMsgBeSent= false;
		
		DTNHost host, otherHost;
		host = getHost();
		otherHost = con.getOtherNode(getHost());
		
		String sHost, sOtherHost;
		sHost = host.toString();
		sOtherHost = otherHost.toString(); 
		
		//MessageRouter mRouter = otherHost.getRouter();

		//assert mRouter instanceof ClusterBasedRouter : "ClusterBasedProp only works "+ 
		//" with other routers of same type";
		//ClusterBasedRouter otherRouter = (ClusterBasedRouter) mRouter;
		

		//current host is exemplar
		if(sHost.startsWith("n") && sOtherHost.startsWith("n") && 
				(this.nodeInf.getExemplarId() == otherHost && othRouter.nodeInf.getExemplarId() ==otherHost)){
			canMsgBeSent = true;
		}
		//both hosts are exemplars
//		else if(sHost.startsWith("n") && sOtherHost.startsWith("n") && 
//				(this.nodeInf.getExemplarId() == host && othRouter.nodeInf.getExemplarId() == otherHost)){
//			canMsgBeSent = true;
//		}
		else if(sHost.startsWith("n")  && this.nodeInf.getWeightedFitness() >= wf_thres && 
				this.nodeInf.getExemplarId() == host && sOtherHost.startsWith("CD")){
			canMsgBeSent = true;
		}

		else
			canMsgBeSent = false;
		
	return canMsgBeSent;	
}
	@Override
	public void update() {
		super.update();
		reduceSendingAndScanningEnergy();
		slotTimeOutEvent();
			
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		//this.tryAllMessagesToAllConnections();
		this.tryOtherMessages();
		
		
	}
		
	@Override
	public ClusterBasedRouter replicate() {
		return new ClusterBasedRouter(this);
	}
	
	/**
	 * Called by the combus is the energy value is changed
	 * @param key The energy ID
	 * @param newValue The new energy value
	 */
	public void moduleValueChanged(String key, Object newValue) {
		//this.currentEnergy = (Double) newValue;
		if(key.equals(ENERGY_VALUE_ID))
			this.currentEnergy = (Double)newValue;
		else if (key.equals(PREVIOUS_ENERGY_VALUE))
			this.previousEnergy = (Double) newValue;
		else if (key.equals(TOTAL_ENERGY_VALUE))
			this.totalEnergy = (Double) newValue;
	}
	
	@Override
	public void changedConnection(Connection con) {
		DTNHost currentHost = getHost();
		DTNHost otherHost = con.getOtherNode(getHost());
		MessageRouter mRouter = otherHost.getRouter();

		assert mRouter instanceof ClusterBasedRouter : "ClusterBasedProp only works "+ 
		" with other routers of same type";
		ClusterBasedRouter otherRouter = (ClusterBasedRouter) mRouter;
		
		double simTime = SimClock.getTime();
		
		if (!con.isUp()) {
			if (con.isInitiator(getHost())) {
			//Meeting Event
				/**Event 2: Meeting Event (say Nodes i and j meet):
				 * At Node i,
				 * If Nodes i and j are both survivor nodes
				 * 		If no entry for node j in EC(i)
				 * 				Make an entry with node j’s information
				 * 		If already existing entry for node j in EC Table
				 * 				Update current Timeslot  (Tij = Tcur )
				 * 		For k ∈ EC(j)  \\ all entries k in EC Table of node j, 
				 * 				If no entry for node k in EC(i)
				 * 						Make an entry for node k in EC(i)
				 * 						Update cFik = 0 \\ has not actually met
				 * 				If already existing entry for node k in EC(i)
				 * 						Update Tik = Tjk ,   if Tik  < Tjk   
				 * 
				 * If Node j is a responder
				 * 		Update rF(i)
				 */
				
				//System.out.println("-------------------------START: MEETING EVENT ------------------------\n");
				
				if(debugMode == 1 || debugMode == 3){
					System.out.println("\nMEETING EVENT: SimTime: " + SimClock.getTime() + " SlotTime: " + (this.lastTimeSlot + this.slotTimeInterval));	
					System.out.println("MEETING EVENT: Nodes " + getHost()+ "  -  "+otherHost);
				}
				
				//Both nodes are survivor nodes
				if(currentHost.toString().contains("n") && otherHost.toString().contains("n")){
					
					//At initial phase
					if(nodeInf.getCurrentHost() == null)
						nodeInf.setCurrentHost(getHost());
					
					if(otherRouter.nodeInf.getCurrentHost() == null)
						otherRouter.nodeInf.setCurrentHost(otherHost);
					
					if(nodeInf.getExemplarId() == null)
						this.nodeInf.setExemplarId(getHost());
					
					if (otherRouter.nodeInf.getExemplarId() == null){
						otherRouter.nodeInf.setExemplarId(otherHost);
					}
					
					//For contact duration updates
					double prevTime = nodeInf.getContactStartTimeWithNodeK(otherHost);
					double otherPrevTime = otherRouter.nodeInf.getContactStartTimeWithNodeK(currentHost);
					nodeInf.setContactDurationWithNodeK(otherHost, (simTime - prevTime) );
					otherRouter.nodeInf.setContactDurationWithNodeK(currentHost, (simTime - otherPrevTime));
				
					//Update exemplar table for current meeting node
					this.nodeInf.updateExemplarTableForMeetingNode(otherHost, otherRouter.nodeInf, true);							
					otherRouter.nodeInf.updateExemplarTableForMeetingNode(currentHost, nodeInf, false);
					
					handleCaseWhenMeetingNodesAreExemplars(currentHost, otherHost, nodeInf, otherRouter.nodeInf, cf_thres);
					
				}
				
				else{ //Meeting with responder Node
					
					if(otherHost.toString().startsWith("CD") && currentHost.toString().startsWith("n")){	
						double prevTime = nodeInf.getContactStartTimeWithR();
						nodeInf.setContactDurationWithR(nodeInf.getContactDurationWithR() + (simTime - prevTime) );
						this.nodeInf.setContactFreqWithR(nodeInf.getContactFreqWithR() + 1);
//						System.out.println("Meeting with Responder : Initiator " +  currentHost  +"    "+ nodeInf.getContactFreqWithR()+"   "+nodeInf.getContactDurationWithR());
					
					}
					
					else if(currentHost.toString().startsWith("CD") && otherHost.toString().startsWith("n")){
						double otherPrevTime = otherRouter.nodeInf.getContactStartTimeWithR();
						otherRouter.nodeInf.setContactDurationWithR(otherRouter.nodeInf.getContactDurationWithR() + simTime - otherPrevTime);
						otherRouter.nodeInf.setContactFreqWithR(otherRouter.nodeInf.getContactFreqWithR() + 1);
//						System.out.println("Meeting with Responder : Other Node " +  otherHost  +"    "+ otherRouter.nodeInf.getContactFreqWithR()+"  "+otherRouter.nodeInf.getContactDurationWithR());	
				
					}
				}
//				System.out.println("\n");
			//System.out.println("---------------------------END: MEETING EVENT ---------------------------\n");				
			}
		}
		
		else{ // CONNECTION UP
			/**
			 * Contact duration start time
			 */
			if(con.isInitiator(getHost())){
				//System.out.println("------------START CONNECTION UP: -----------------");
				if(debugMode == 1 || debugMode == 3){
					System.out.println("MEETING NODES: " + getHost()+ " "+con.getOtherNode(getHost())+" simTime: "+SimClock.getTime());
					
				}
				if(currentHost.toString().startsWith("n") && otherHost.toString().startsWith("n")){
					this.nodeInf.setContactStartTimeWithNodeK(otherHost, simTime);
					otherRouter.nodeInf.setContactStartTimeWithNodeK(currentHost, simTime);
				}
				else{
					if(otherHost.toString().startsWith("CD") && currentHost.toString().startsWith("n")){	
						this.nodeInf.setContactStartTimeWithR(simTime);
					}
					else if(currentHost.toString().startsWith("CD") && otherHost.toString().startsWith("n")){
						otherRouter.nodeInf.setContactStartTimeWithR(simTime);
					}
				}
				//System.out.println("------------END CONNECTION UP: -----------------");
			}
		}
	}
	


	/**
	 * Special Case:  (Both nodes i and j are exemplars)
     *   X(i) = i, X(j) = j, cFij ≥ cFthres

	 * Case 1: If Node j and its members can be assigned to Node i. However, Node i and 
	 * its members can not be assigned to Node j i.e. 
	 * cFik ≥ cFthres 	∀k ∈ CjX(j)  and  
	 * ヨk’ ∈ CiX(i) 		cFk’j < cFthres
	 * =>	X(j) = i,    Xjk = i   ∀ k  ∈ CjX(j)

	 * Case 2: If Node i and its members can be assigned to Node j. However, Node j and 
	 * its members can not be assigned to Node i, i.e. 
	 * cFjk ≥ cFthres		∀k ∈ CiX(i)  and
	 * ヨk’ ∈ CjX(j) 		cFk’i < cFthres
	 * =>	X(i) = j,     Xik = j   ∀ k  ∈ CiX(i)

	 * Case 3:  If both Node i and its members can be assigned to node j. Similarly, Node j and 
	 * its members can be assigned to Node i, i.e. 
	 * cFjk  ≥ cFthres 	∀k ∈ CiX(i)  and
	 * cFk’i ≥ cFthres	∀k’ ∈ CjX(j) 	

	 * Sub case 1: If the minimum of all contact fitness of node j with its member is greater than 
	 * the minimum of all contact fitness of node i with its members, i.e.
	 * min {cFjk } ≥ min {cFk’i }
	 * =>	X(j) = i,    Xjk = i   ∀ k  ∈ CjX(j)
	
	 * Sub Case 2: Else 
	 * min {cFjk } < min {cFk’i }
	 * =>	X(i) = j,    Xik = j   ∀ k  ∈ CiX(i)

	 * @param otherHost
	 * @param nodeInf
	 */
	public void handleCaseWhenMeetingNodesAreExemplars(DTNHost currentHost, DTNHost otherHost,
			NodeInformation nodeInf, NodeInformation otherNodeInf, double cfThres) {
		if(debugMode == 1 || debugMode == 3){
			System.out.println("\n          Meeting Nodes:  "+currentHost+"   "+otherHost);
			System.out.println("      Node & its exemplar:  "+ currentHost+ " => " + nodeInf.getExemplarId());
			System.out.println("Other Node & its Exemplar:  " + otherHost + " => " +otherNodeInf.getExemplarId());
			System.out.println("          Contact fitness:  "+nodeInf.getContactFitnessWithNodeK(otherHost)+"  -  cFThres: "+ cfThres );
			
		}
		
		if(currentHost == nodeInf.getExemplarId() && otherNodeInf.getExemplarId() == otherHost ){
			
			if(nodeInf.getContactFitnessWithNodeK(otherHost) >= cfThres ){
				
				if(debugMode == 1 || debugMode == 3){
					System.out.println("Handle Case when both nodes are exemplars");
				}
				boolean canCurrentHostBeAssignedToOtherHost = nodeInf.checkIfAllClusterMembersCanBeAssignedToOther(otherHost, otherNodeInf, cfThres, wf_thres);
				boolean canOtherHostBeAssignedToCurrentHost = otherNodeInf.checkIfAllClusterMembersCanBeAssignedToOther(currentHost, nodeInf, cfThres, wf_thres);
				
				if(canCurrentHostBeAssignedToOtherHost && !canOtherHostBeAssignedToCurrentHost && otherNodeInf.getWeightedFitness() >= wf_thres){
					nodeInf.assignAllClusterMembersToOtherNode(otherHost, otherNodeInf);
				}
				else if(!canCurrentHostBeAssignedToOtherHost && canOtherHostBeAssignedToCurrentHost && this.nodeInf.getWeightedFitness() >=wf_thres){
					otherNodeInf.assignAllClusterMembersToOtherNode(currentHost, this.nodeInf);
				}
				else if(canCurrentHostBeAssignedToOtherHost && canOtherHostBeAssignedToCurrentHost 
						&& this.nodeInf.getWeightedFitness() >=wf_thres && otherNodeInf.getWeightedFitness() >= wf_thres ){
//					System.out.println("Both exemplars can be assigned to each other \n");
					double hostLeastCFMember = nodeInf.getLeastContactFitnessClusterMemberValue();
					double otherHostLeastCFMember = otherNodeInf.getLeastContactFitnessClusterMemberValue();
					
//					System.out.println("Least CF for a cluster member at node: "+currentHost+" - "+hostLeastCFMember);
//					System.out.println("Least CF for a cluster member at other node: "+otherHost+" - "+otherHostLeastCFMember);
//					
					if(hostLeastCFMember < otherHostLeastCFMember){
						nodeInf.assignAllClusterMembersToOtherNode(otherHost, otherNodeInf);
					}
					else{
						otherNodeInf.assignAllClusterMembersToOtherNode(currentHost, this.nodeInf);
					}
//					System.out.println("After merging of clusters: ");
//					System.out.println("Exemplar ID at Node "+ currentHost+"  => " + nodeInf.getExemplarId());
//					System.out.println("Exemplar ID at other Node "+ otherHost+"  => " + otherNodeInf.getExemplarId());
					
					
				}
				
			}
		}	
		if(debugMode == 1 || debugMode == 3){
			nodeInf.showExemplarTableEntries();
			otherNodeInf.showExemplarTableEntries();
		}
	}
		
	
	public void slotTimeOutEvent(){
		double simTime = SimClock.getTime();
		
		//At Slot time out event
		/**
		 * Event 1: Slot timeout event (say T = T1 ) :
		 * Update cF_ik ,        ∀k ∈ EC(i)
		 * Update rF(i)
		 * Update wF(i)
		 * 
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
		 * CALCULATION OF wF(i)
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
		 *
		 *
		 **/
		
		//Slot time out
		if(simTime > this.lastTimeSlot + this.slotTimeInterval && getHost().toString().startsWith("n")){
			getHost().setNodeInformation(this.nodeInf);
			if(debugMode == 2 || debugMode == 3){
				System.out.println("-----------------------START: SLOT TIME OUT -------------------- \n");
				
				if(nodeInf!=null)
				{	
					String str ="Node: "+ getHost()+"  Exemplar Id: " + nodeInf.getExemplarId()+" wF: "+nodeInf.getWeightedFitness();
					String str2 = "Table Entries: (NodeId, ExemplarId, cF, wF) \n [ ";
					//write();
					for(Map.Entry<DTNHost, ExemplarTableParams> entry: nodeInf.getExemplarTable().entrySet()){
						if(entry.getValue().getExemplarId() == nodeInf.getExemplarId()){
							str +=entry.getKey() + " , ";
							
						}
						str2 +="("+entry.getKey()+", "+entry.getValue().getExemplarId()+", "+entry.getValue().getContactFitnessWithNodeK()+", "+entry.getValue().getWeightedFitness()+") , ";
					}
					str2 += " ]";
					System.out.println(" Node Information: "+ SimClock.getTime()+" \n" + str + "\n" + str2);
				}
			}
			this.currentTimeSlotNumber += 1;
			
			//Update the current time slot number
			nodeInf.setCurrentTimeSlotNumber(this.currentTimeSlotNumber);
			nodeInf.setLastUpdatedTime(SimClock.getTime());
			//Update the responder contact fitness for current node 
			nodeInf.updateContactFitnessWithR(rf_gamma, this.slotTimeInterval, debugMode);
			
//			System.out.println("SLOT TIMEOUT EVENT: Energy at Node "+ getHost());
//			System.out.println("SLOT TIMEOUT EVENT: Total Energy: "+ this.totalEnergy);
//			System.out.println("SLOT TIMEOUT EVENT: Energy at previous timeSlot: "+ this.previousEnergy);
//			System.out.println("SLOT TIMEOUT EVENT: Energy at current timeSlot: " + this.currentEnergy);
			//Update weighted fitness of current node 
			nodeInf.setWeightedFitness(wf_beta, rf_thres, this.totalEnergy, this.previousEnergy, this.currentEnergy, this.slotTimeInterval, debugMode);
			
			//update the contact fitness of all entries in exemplar table at current node
			nodeInf.updateContactFitnessForAllNodesInExemplarTable(cf_alpha, slotTimeInterval, debugMode);
			
			//Handle the special three cases
			nodeInf.handleAllPossibleCasesAfterContactAndWeightedFitnessUpdation(cf_thres, wf_thres, debugMode);

			this.lastTimeSlot = simTime;
			if(debugMode == 2 || debugMode == 3)
				System.out.println("----------------------END: SLOT TIME OUT -------------------------\n");
			
		}
	}
	
	@Override
	public String toString() {
		return super.toString() + " energy level = " + this.currentEnergy;
	}
	
}