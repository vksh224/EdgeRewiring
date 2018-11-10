/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

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

/**
 * Energy level-aware and smartly computed variant of Epidemic router.
 */
public class EnergyAwareSmart2EpidemicRouter extends RestrictedEpidemicRouter 
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
	
	public static final String NO_OF_EXEMPLARS = "noOfExemplars";
	public int noOfExemplars;
	public String currentConnections;
	
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
	public boolean isRelayExemplar;
	
	ArrayList<DTNHost> currentExemplarsList = new ArrayList<DTNHost>();
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public EnergyAwareSmart2EpidemicRouter(Settings s) {
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
		this.noOfExemplars = s.getInt(NO_OF_EXEMPLARS);
	}
	
	/**
	 * Sets the current energy level into the given range using uniform 
	 * random distribution.
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
		
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EnergyAwareSmart2EpidemicRouter(EnergyAwareSmart2EpidemicRouter r) {
		super(r);
		this.initEnergy = r.initEnergy;
		setEnergy(this.initEnergy);
		this.scanEnergy = r.scanEnergy;
		this.transmitEnergy = r.transmitEnergy;
		this.scanInterval = r.scanInterval;
		this.warmupTime  = r.warmupTime;
		this.comBus = null;
		this.lastScanUpdate = 0;
		this.lastUpdate = 0;
		this.isRelayExemplar = true;
		this.noOfExemplars = r.noOfExemplars;
	}
	@Override
	public void changedConnection(Connection con) { 
		updateContactFrequency(con);
		
	}
	@Override
	protected int checkReceiving(Message m) {
		if (getHost().getCurEnergy() < 0) {
			return DENIED_UNSPECIFIED;
		}
		else {
			 return super.checkReceiving(m);
		}
	}
	
	public void updateContactFrequency(Connection con){
		DTNHost host, otherHost;
		
		host = getHost();
		otherHost = con.getOtherNode(getHost());
		if(host.toString().startsWith("CD") && otherHost.toString().startsWith("n")){
			otherHost.setContactFrequency(otherHost.getContactFrequency()+1);
			//System.out.println(SimClock.getTime()+ " Con is up between "+host.toString()+ " "+otherHost.toString() + " "+otherHost.getContactFrequency());
		}
		else if(otherHost.toString().startsWith("CD") && host.toString().startsWith("n")){
			host.setContactFrequency(host.getContactFrequency()+1);
			//System.out.println(SimClock.getTime()+ " Con is up between "+host.toString()+ " "+otherHost.toString() + " "+host.getContactFrequency());
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
		
		//this.comBus.updateDouble(ENERGY_VALUE_ID, -amount);
		getHost().setCurEnergy(getHost().getCurEnergy() - amount);
		if (getHost().getCurEnergy() < 0) {
			//this.comBus.updateProperty(ENERGY_VALUE_ID, 0.0);
			getHost().setCurEnergy(0.0);
		}
	}
	
	/**
	 * Reduces the energy reserve for the amount that is used by sending data
	 * and scanning for the other nodes. 
	 */
	protected void reduceSendingAndScanningEnergy() {
		double simTime = SimClock.getTime();
		
		if(getHost().getInitialEnergy()== -1){
			getHost().setInitialEnergy(this.currentEnergy);
		}
		if(getHost().getCurEnergy() == -1){
			getHost().setCurEnergy(this.currentEnergy);
		}
		if(getHost().getPrevEnergy() == -1){
			getHost().setPrevEnergy(this.currentEnergy);
		}
		
		if (getHost().getCurEnergy() <= 0) {
			/* turn radio off */
			//this.comBus.updateProperty(NetworkInterface.RANGE_ID, 0.0);
			getHost().getComBus().updateProperty(NetworkInterface.RANGE_ID, 0.0);
			return; /* no more energy to start new transfers */
		}
		
		if (this.getHost().toString().startsWith("n") &&
				simTime > this.lastUpdate && sendingConnections.size() > 0) {
			reduceEnergy((simTime - this.lastUpdate) * this.transmitEnergy);
		}
		this.lastUpdate = simTime;
		
		if ( this.getHost().toString().startsWith("n") &&
				simTime > this.lastScanUpdate + this.scanInterval) {
			//System.out.println(" The scan energy being lost at " + simTime +" for host: " + getHost().toString() );
			// scanning at this update round 
			reduceEnergy(this.scanEnergy);
			this.lastScanUpdate = simTime;
		}
	}

	protected void printRelayExemplarList(){
		currentExemplarsList = this.getHost().getRelayExemplarMap();
		if(currentExemplarsList!=null){
		System.out.println("At timestamp:  " + SimClock.getTime() + "  For host: " + getHost() +"\nSTART");
		//if(getHost().getRelayExemplarMap()!=null){
			for(DTNHost host : currentExemplarsList){
				System.out.println(host.toString() +" : "+
						host.getContactFrequency()+" : "  +
						host.getCurEnergy()+" : ");
			}
			System.out.println( currentExemplarsList);
			System.out.println("END");
		}
	}
	
	private ArrayList<DTNHost> prunePotentialExemplars(
			ArrayList<DTNHost> collectivePotentialExemplars) {
		ArrayList<DTNHost> finalPotentialExemplars = new ArrayList<DTNHost>();
		
		for(DTNHost host : collectivePotentialExemplars){
			if(host.getCurEnergy()>0)
				finalPotentialExemplars.add(host);
		}
		return finalPotentialExemplars;
	}

	protected  ArrayList<DTNHost> updateRelayExemplarList(DTNHost host){
		ArrayList<DTNHost> previousExemplarList = host.getRelayExemplarMap();
		ArrayList<DTNHost> finalExemplarList = new ArrayList<DTNHost>();
		
		ArrayList<DTNHost> collectivePotentialExemplars = previousExemplarList;
		
		for(Connection con : host.getConnections()){
			DTNHost otherHost = con.getOtherNode(host);
			
			if(otherHost.toString().startsWith("n") && 
				!collectivePotentialExemplars.contains(otherHost) &&
				host.getClusterNumber() == otherHost.getClusterNumber()){
				//System.out.println("Add other host");
				collectivePotentialExemplars.add(otherHost);
			}
		}
		ArrayList<DTNHost>fitPotentialExemplars = prunePotentialExemplars(collectivePotentialExemplars);
		Collections.sort(fitPotentialExemplars, new energy2Comparator());
//		System.out.println("New relay exemplar list is : ");
//		for(DTNHost ahost : collectivePotentialExemplars){
//			System.out.println(ahost.toString() +" : "+
//					ahost.getContactFrequency()+" : "  +
//					ahost.getCurEnergy() +" : " +
//					ahost.getPrevEnergy()+" : " +
//					ahost.getInitialEnergy()
//					);
//		}
		if(fitPotentialExemplars.size()>this.noOfExemplars){
			finalExemplarList = new ArrayList<DTNHost>(fitPotentialExemplars.subList(0, this.noOfExemplars));
			
		}
		else
			finalExemplarList = fitPotentialExemplars;
		
		host.setRelayExemplarMap(finalExemplarList);
		
		for(Connection con : getHost().getConnections()){
			DTNHost otherHost = con.getOtherNode(getHost());
			if(otherHost.toString().startsWith("n") && 
			   host.getClusterNumber() == otherHost.getClusterNumber()){
				otherHost.setRelayExemplarMap(finalExemplarList);					
			}				
		}
		
		return finalExemplarList;
		
	}
	
	protected void checkAndUpdateRelayExemplarList(){
		
		DTNHost host = getHost();
		currentExemplarsList = host.getRelayExemplarMap();
//		Boolean isSameList = checkIfExemplarListInConnections();
//		if(!isSameList){
//			currentExemplarsList = updateRelayExemplarList();
//		}
		
		if(currentExemplarsList.contains(getHost()) &&
			getHost().getCurEnergy()<=
			0.7 * host.getPrevEnergy()){
			//System.out.println("Host: Current and Previous Energy: "+getHost()+" "+getHost().getCurEnergy()+" "+getHost().getPrevEnergy());
			
//			System.out.println("Before updated: " + host + " : " + host.getRelayExemplarMap() +" at "+host.getLastExemplarListUpdated());
//			for(Connection con : host.getConnections()){
//				DTNHost otherHost = con.getOtherNode(host);
//				System.out.println(otherHost  +" : "+otherHost.getRelayExemplarMap()+" at "+otherHost.getLastExemplarListUpdated());
//			}
			currentExemplarsList = updateRelayExemplarList(host);
//			System.out.println("After updated: " + host + " : " + host.getRelayExemplarMap()+" at "+host.getLastExemplarListUpdated());
//			for(Connection con : host.getConnections()){
//				DTNHost otherHost = con.getOtherNode(host);
//				System.out.println(otherHost  +" : "+otherHost.getRelayExemplarMap()+" at "+otherHost.getLastExemplarListUpdated());
//			}
			double tempEnergy = host.getCurEnergy();
			host.setPrevEnergy(tempEnergy);
		}
		
		if(currentExemplarsList.size() > this.noOfExemplars){
			ArrayList<DTNHost> fixExemplarList = new ArrayList<DTNHost>();
			System.out.println("This is unexpected. Let's fix it: for " + host +" and it's connections" +
			host.getConnections());
			System.out.println("Exemplar List last updated: "  +host.getLastExemplarListUpdated());
			System.out.println("Before fixing: " + host + " : " + host.getRelayExemplarMap());
			System.out.println("Exemplar string is: " + host.getExemplarString());
//			
//			for(Connection con : host.getConnections()){
//				DTNHost otherHost = con.getOtherNode(host);
//				System.out.println(otherHost  +" : "+otherHost.getRelayExemplarMap());
//			}
			for(DTNHost ahost : host.getRelayExemplarMap()){
				if(host.getExemplarString().contains(ahost.toString())){
					fixExemplarList.add(ahost);
				}
			}
			host.setRelayExemplarMap(fixExemplarList);
			System.out.println("After fixing: " + host + " : " + host.getRelayExemplarMap());
//			for(Connection con : host.getConnections()){
//				DTNHost otherHost = con.getOtherNode(host);
//				System.out.println(otherHost  +" : "+otherHost.getRelayExemplarMap());
//			}
		}
		
		ArrayList<DTNHost> prevExemplarsList = currentExemplarsList;
		// after initialization phase - frequency check
		if(host.toString().startsWith("n") && 
			host.getContactFrequency() > 0 && 
			host.getCurEnergy() >0 &&
			(currentExemplarsList.size() ==0 || !currentExemplarsList.contains(host)) &&
			currentExemplarsList.size()< this.noOfExemplars){
			System.out.println("Add host");
			currentExemplarsList.add(host);
		}
		for(Connection con : host.getConnections()){
			DTNHost otherHost = con.getOtherNode(getHost());
			
			if(otherHost.toString().startsWith("n") && 
				otherHost.getContactFrequency() > 0 &&
				otherHost.getCurEnergy() >0 &&
				(currentExemplarsList.size()==0 || !currentExemplarsList.contains(otherHost)) &&
				host.getClusterNumber() == otherHost.getClusterNumber() &&
				currentExemplarsList.size()< this.noOfExemplars){
				System.out.println("Add other host");
				currentExemplarsList.add(otherHost);
			}
		}
		
		if(currentExemplarsList.size()>0)
			System.out.println("Current Exemplar list for "+host+" "+currentExemplarsList+" : "+prevExemplarsList);
		
		host.setRelayExemplarMap(currentExemplarsList);

		//update the neighboring nodes with updated exemplar list as well
		for(Connection con : host.getConnections()){
			DTNHost otherHost = con.getOtherNode(host);
			if(otherHost.toString().startsWith("n"))
					otherHost.setRelayExemplarMap(currentExemplarsList);			
		}					
	}
	
	
	private Boolean checkIfExemplarListInConnections() {
		ArrayList<DTNHost> hostExemplarList = getHost().getRelayExemplarMap();

		for(Connection con : getHost().getConnections()){
			DTNHost otherHost = con.getOtherNode(getHost());
			ArrayList<DTNHost> otherHostExemplarList = otherHost.getRelayExemplarMap();
			ArrayList<DTNHost> compareList = hostExemplarList;
			compareList.removeAll(otherHostExemplarList);
			if(compareList.size()>0){
				//System.out.println("Different exemplar lists: " + hostExemplarList+"\n "+otherHostExemplarList);
				return false;
			}			
		}
		return true;
	}

	/**
	 * Message should not be sent from one non-exemplar node to another non-exemplar node
	 * @param con
	 * @return
	 */
	protected boolean shouldSendMessage(Connection con)
	{
		DTNHost host, otherHost;
		host = getHost();
		otherHost = con.getOtherNode(getHost());
		
		String sHost = host.toString() , sOtherHost = otherHost.toString();

		ArrayList<DTNHost> hostExemplarList = host.getRelayExemplarMap();
		ArrayList<DTNHost> otherHostExemplarList = otherHost.getRelayExemplarMap();

		if(hostExemplarList!=null && hostExemplarList.size()>0 && !hostExemplarList.contains(host) 
				&& !hostExemplarList.contains(otherHost)
				&&otherHostExemplarList!=null && otherHostExemplarList.size()>0 &&
				!otherHostExemplarList.contains(host) &&
				!otherHostExemplarList.contains(otherHost)){
		//	System.out.println("FALSE The host: " + host + "- " + hostExemplarList +
			//		"\n other host: " + otherHost + " - "  + otherHostExemplarList);
			return false;
		}
//		if(		(sHost.startsWith("DB") && sOtherHost.startsWith("CD"))||
//				(sHost.startsWith("CD") && sOtherHost.startsWith("DB"))||
//				
//				(hostExemplarList!=null && hostExemplarList.contains(host) && sOtherHost.startsWith("CD"))||
//				( sHost.startsWith("CD")  && otherHostExemplarList!=null && otherHostExemplarList.contains(otherHost))||
//				
//				(hostExemplarList!=null && hostExemplarList.contains(host) && 
//				(otherHostExemplarList==null || !hostExemplarList.contains(otherHost)))||
//				
//				((hostExemplarList==null || !otherHostExemplarList.contains(host)) && 
//				otherHostExemplarList!=null && otherHostExemplarList.contains(otherHost))||
//				
//				(hostExemplarList!=null && hostExemplarList.contains(host) && 
//						hostExemplarList.contains(otherHost)) ||
//				(otherHostExemplarList!=null && otherHostExemplarList.contains(host) && 
//				otherHostExemplarList.contains(otherHost)) ||
//				
//				(hostExemplarList == null && otherHostExemplarList == null) ||
//						
//				(hostExemplarList!=null && hostExemplarList.size() == 0 && 
//				otherHostExemplarList!=null && otherHostExemplarList.size() == 0)){
//					
//					return true;
//				}
		else{
			//System.out.println("FALSE The host: " + host + "- " + hostExemplarList +
			//	"\n other host: " + otherHost + " - "  + otherHostExemplarList);
			return true;
		}
			
	}
	

	/**
	 * Tries to send all given messages to all given connections. Connections
	 * are first iterated in the order they are in the list and for every
	 * connection, the messages are tried in the order they are in the list.
	 * Once an accepting connection is found, no other connections or messages
	 * are tried.
	 * @param messages The list of Messages to try
	 * @param connections The list of Connections to try
	 * @return The connections that started a transfer or null if no connection
	 * accepted a message.
	 */
	protected Connection tryMessagesToConnections(List<Message> messages,
			List<Connection> connections) {
		//System.out.println("smart epidemic  - try messages to connections");
		
		boolean shouldConnectionBeConsidered = false;
		for (int i=0, n=connections.size(); i<n; i++) {
			Connection con = connections.get(i);
			//added code
			shouldConnectionBeConsidered = shouldSendMessage(con);
			Message started=null;
			
			if(shouldConnectionBeConsidered == true){
				started = tryAllMessages(con, messages);
			}
			if (started != null) { 
				return con;
			}
		}
		
		return null;
	}	
	/**
	 * Tries to send all messages that this router is carrying to all
	 * connections this node has. Messages are ordered using the 
	 * {@link MessageRouter#sortByQueueMode(List)}. See 
	 * {@link #tryMessagesToConnections(List, List)} for sending details.
	 * @return The connections that started a transfer or null if no connection
	 * accepted a message.
	 */
	protected Connection tryAllMessagesToAllConnections(){
		//System.out.println("Smart epidemic - try all messages to all connections");
		List<Connection> connections = getConnections();
		if (connections.size() == 0 || this.getNrofMessages() == 0) {
			return null;
		}

		List<Message> messages = 
			new ArrayList<Message>(this.getMessageCollection());
		this.sortByQueueMode(messages);
		
		return tryMessagesToConnections(messages, connections);
	}
	@Override
	public void update() {
		super.update();
		
		reduceSendingAndScanningEnergy();
		
		
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		tryAllMessagesToAllConnections();
		checkAndUpdateRelayExemplarList();
	}
		
	@Override
	public EnergyAwareSmart2EpidemicRouter replicate() {
		return new EnergyAwareSmart2EpidemicRouter(this);
	}
	
	/**
	 * Called by the combus is the energy value is changed
	 * @param key The energy ID
	 * @param newValue The new energy value
	 */

	public void moduleValueChanged(String key, Object newValue) {

	}

	
	@Override
	public String toString() {
		return super.toString() + " energy level = " + this.currentEnergy;
	}	
}
class energy2Comparator implements Comparator<DTNHost> {
    @Override
    public int compare(DTNHost a, DTNHost b) {
       if(a.getContactFrequency() > b.getContactFrequency())
    	   return -1;
       else if(a.getContactFrequency() == b.getContactFrequency()){
    	   if(a.getCurEnergy()>= b.getCurEnergy()){
    		   return -1;
    	   }
    	   else
    		   return 1;
       }
       else
    	   return 1;
    
    }
}
