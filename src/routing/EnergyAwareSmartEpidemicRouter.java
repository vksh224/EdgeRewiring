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
public class EnergyAwareSmartEpidemicRouter extends RestrictedEpidemicRouter 
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
	public static final String ORIGINAL_ENERGY_VALUE="OriginalEnergy.value";
	public static final String PREVIOUS_ENERGY_VALUE="PreviousEnergy.value";
	public static final String CONTACT_FREQUENCY="Host.contactFreq";
	public static final String IS_RELAY_EXEMPLAR = "Host.isRelayExemplar";
	
	public static final String NO_OF_EXEMPLARS = "noOfExemplars";
	public int noOfExemplars;
	public String currentConnections;
	
	private final double[] initEnergy;
	private double warmupTime;
	private double currentEnergy;
	private double originalEnergy;
	private double previousEnergy;
	/** energy usage per scan */
	private double scanEnergy;
	private double transmitEnergy;
	private double lastScanUpdate;
	private double lastUpdate;
	private double scanInterval;	
	private ModuleCommunicationBus comBus;
	private static Random rng = null;
	private double exemplarLastUpdate;
	public boolean isRelayExemplar;
	
	public List<DTNHost> relayExemplarsList;
	ArrayList<DTNHost> currentExemplarsList = new ArrayList<DTNHost>();
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public EnergyAwareSmartEpidemicRouter(Settings s) {
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
		this.relayExemplarsList = new ArrayList<DTNHost> ();
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
		this.originalEnergy = this.currentEnergy;
		this.previousEnergy = this.currentEnergy;
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EnergyAwareSmartEpidemicRouter(EnergyAwareSmartEpidemicRouter r) {
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
		this.exemplarLastUpdate = 0;
		this.isRelayExemplar = true;
		this.noOfExemplars = r.noOfExemplars;
	}
	@Override
	public void changedConnection(Connection con) { 
		updateContactFrequency(con);
		
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
		
		this.comBus.updateDouble(ENERGY_VALUE_ID, -amount);
		if (this.currentEnergy < 0) {
			this.comBus.updateProperty(ENERGY_VALUE_ID, 0.0);
		}
	}
	
	/**
	 * Reduces the energy reserve for the amount that is used by sending data
	 * and scanning for the other nodes. 
	 */
	protected void reduceSendingAndScanningEnergy() {
		double simTime = SimClock.getTime();
		
		if(this.comBus == null){
			this.comBus = getHost().getComBus();
		}
		if (this.comBus.getProperty(ENERGY_VALUE_ID)== null) {
			this.comBus.addProperty(ENERGY_VALUE_ID, this.currentEnergy);
			this.comBus.subscribe(ENERGY_VALUE_ID, this);
		}
		
		if(this.comBus.getProperty(ORIGINAL_ENERGY_VALUE)==null){
			this.comBus.addProperty(ORIGINAL_ENERGY_VALUE, this.originalEnergy);
			this.comBus.subscribe(ORIGINAL_ENERGY_VALUE, this);
		}
		if(this.comBus.getProperty(PREVIOUS_ENERGY_VALUE)==null){
			this.comBus.addProperty(PREVIOUS_ENERGY_VALUE, this.previousEnergy);
			this.comBus.subscribe(PREVIOUS_ENERGY_VALUE, this);
		}
		//System.out.println("VIJAY");
		
		if (this.currentEnergy <= 0) {
			/* turn radio off */
			this.comBus.updateProperty(NetworkInterface.RANGE_ID, 0.0);
			return; /* no more energy to start new transfers */
		}
		//reduce energy for DTN nodes only - NOT rescue workers
		if (this.getHost().toString().startsWith("n") &&
				simTime > this.lastUpdate && sendingConnections.size() > 0) {
			/* sending data */
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
	
	@SuppressWarnings("unchecked")
	protected void printRelayExemplarList(){
		currentExemplarsList = (ArrayList<DTNHost>)getHost().getComBus().getProperty(getHost().toString());
		if(currentExemplarsList!=null){
		System.out.println("At timestamp:  " + SimClock.getTime() + "  For host: " + getHost() +"\nSTART");
		//if(getHost().getRelayExemplarMap()!=null){
			for(DTNHost host : currentExemplarsList){
				System.out.println(host.toString() +" : "+
						host.getContactFrequency()+" : "  +
						host.getComBus().getProperty(ENERGY_VALUE_ID) +" : "+
						host.getComBus().getProperty(ORIGINAL_ENERGY_VALUE));
			}
			System.out.println( currentExemplarsList);
			System.out.println("END");
		}
	}
	
	private ArrayList<DTNHost> prunePotentialExemplars(
			ArrayList<DTNHost> collectivePotentialExemplars) {
		ArrayList<DTNHost> finalPotentialExemplars = new ArrayList<DTNHost>();
		
		for(DTNHost host : collectivePotentialExemplars){
			if((Double) host.getComBus().getProperty(ENERGY_VALUE_ID)>0)
				finalPotentialExemplars.add(host);
		}
		return finalPotentialExemplars;
	}
	@SuppressWarnings("unchecked")
	protected synchronized ArrayList<DTNHost> updateRelayExemplarList(){
		ArrayList<DTNHost> previousExemplarList = (ArrayList<DTNHost>) this.comBus.getProperty(getHost().toString());
		//ArrayList<DTNHost> previousExemplarList = getHost().getRelayExemplarMap();
		ArrayList<DTNHost> finalExemplarList = new ArrayList<DTNHost>();
		
		ArrayList<DTNHost> collectivePotentialExemplars = previousExemplarList;
		
		for(Connection con : getHost().getConnections()){
			DTNHost otherHost = con.getOtherNode(getHost());
			
			if(otherHost.toString().startsWith("n") && 
				!collectivePotentialExemplars.contains(otherHost) &&
				getHost().getClusterNumber() == otherHost.getClusterNumber()){
				//System.out.println("Add other host");
				collectivePotentialExemplars.add(otherHost);
			}
		}
		ArrayList<DTNHost>fitPotentialExemplars = prunePotentialExemplars(collectivePotentialExemplars);
		Collections.sort(fitPotentialExemplars, new energyComparator());
//		System.out.println("New relay exemplar list is : ");
//		for(DTNHost host : collectivePotentialExemplars){
//			System.out.println(host.toString() +" : "+
//					host.getContactFrequency()+" : "  +
//					host.getComBus().getProperty(ENERGY_VALUE_ID) +" : "+
//					host.getComBus().getProperty(PREVIOUS_ENERGY_VALUE)+" : "+
//					host.getComBus().getProperty(ORIGINAL_ENERGY_VALUE));
//		}
		if(fitPotentialExemplars.size()>this.noOfExemplars){
			finalExemplarList = new ArrayList<DTNHost>(fitPotentialExemplars.subList(0, this.noOfExemplars));
			
		}
		else
			finalExemplarList = fitPotentialExemplars;
		//System.out.println("The host and exemplar list is: "  +getHost()+" > " + finalExemplarList);
		//getHost().setRelayExemplarMap(finalExemplarList);
		getHost().getComBus().updateProperty(getHost().toString(), finalExemplarList);
		
		for(Connection con : getConnections()){
			DTNHost otherHost = con.getOtherNode(getHost());
			if(otherHost.toString().startsWith("n") && 
			   getHost().getClusterNumber() == otherHost.getClusterNumber())
				otherHost.getComBus().updateProperty(getHost().toString(), finalExemplarList);
				//otherHost.setRelayExemplarMap(finalExemplarList);
		}
		
		return finalExemplarList;
		
	}
	
	
	@SuppressWarnings("unchecked")
	protected synchronized void checkAndUpdateRelayExemplarList(){
		
		//currentExemplarsList = getHost().getRelayExemplarMap();
		//System.out.println("The host is: "  +getHost() + " "+ getConnections());
		if(getHost().getComBus().getProperty(getHost().toString())==null){
			getHost().getComBus().addProperty(getHost().toString(), this.currentExemplarsList);
			getHost().getComBus().subscribe(getHost().toString(), this);
		}
		
		currentExemplarsList = (ArrayList<DTNHost>) getHost().getComBus().getProperty(getHost().toString());
	
		if( currentExemplarsList.contains(getHost()) &&
			(Double) getHost().getComBus().getProperty(ENERGY_VALUE_ID)<=
			0.7 * (Double) getHost().getComBus().getProperty(PREVIOUS_ENERGY_VALUE) ){
			this.comBus.updateProperty(PREVIOUS_ENERGY_VALUE, (Double) this.comBus.getProperty(ENERGY_VALUE_ID));
			
//			System.out.println(" Update relay exemplars: "  + getHost()+" - no Of Exemplars" + this.noOfExemplars+" at time: "+ SimClock.getTime());
//			System.out.println("Before sorting: " + currentExemplarsList.toString());
			
			currentExemplarsList = updateRelayExemplarList();
		
//			System.out.println("After sorting: host is: " +getHost() + "\n "  +currentExemplarsList.toString()+"\n "+
//			getHost().getComBus().getProperty(getHost().toString()));
		}
	
		//TODO: fix this
		if(currentExemplarsList.size()> this.noOfExemplars){
			//currentExemplarsList = new ArrayList<DTNHost>(currentExemplarsList.subList(0,this.noOfExemplars));
			System.out.println("Current Exemplar list: at time "+SimClock.getTime()+
					"\n"  +getHost() +"\n "+ currentExemplarsList+
					"\n" + "Connections "+ getConnections());
		}
			
		//check if still some more exemplars can be added
		// after initialization phase - frequency check
		if(getHost().toString().startsWith("n") && 
			getHost().getContactFrequency() > 0 && 
			(Double)getHost().getComBus().getProperty(ENERGY_VALUE_ID) >0 &&
			!currentExemplarsList.contains(getHost()) &&
			currentExemplarsList.size()< this.noOfExemplars){
			//System.out.println("Add host");
			currentExemplarsList.add(getHost());
		}
	
		for(Connection con : this.getConnections()){
			DTNHost otherHost = con.getOtherNode(getHost());
			
			if(otherHost.toString().startsWith("n") && 
				otherHost.getContactFrequency() > 0 &&
				(Double)otherHost.getComBus().getProperty(ENERGY_VALUE_ID) >0 &&
				!currentExemplarsList.contains(otherHost) &&
				getHost().getClusterNumber() == otherHost.getClusterNumber() &&
				currentExemplarsList.size()< this.noOfExemplars){
				//System.out.println("Add other host");
				currentExemplarsList.add(otherHost);
			}
		}

		//set all the hosts in current connection with updated exemplars list
		//getHost().setRelayExemplarMap(currentExemplarsList);
		getHost().getComBus().updateProperty(getHost().toString(), currentExemplarsList);
		//System.out.println("Current exemplar list is: "+getHost().toString() +"  " +currentExemplarsList+"\n"+getHost().getComBus().getProperty(getHost().toString()));
		for(Connection con : this.getConnections()){
			DTNHost otherHost = con.getOtherNode(getHost());
			if(otherHost.toString().startsWith("n") && 
				!currentExemplarsList.contains(otherHost) &&
				getHost().getClusterNumber() == otherHost.getClusterNumber())
				otherHost.getComBus().updateProperty(getHost().toString(), currentExemplarsList);
				//otherHost.setRelayExemplarMap(currentExemplarsList);
		}

	}
	
	/**
	 * Message should not be sent from one non-exemplar node to another non-exemplar node
	 * @param con
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected boolean shouldSendMessage(Connection con)
	{
		DTNHost host, otherHost;
		host = getHost();
		otherHost = con.getOtherNode(getHost());
		
		String sHost = host.toString() , sOtherHost = otherHost.toString();
		
		ArrayList<DTNHost> hostExemplarList = (ArrayList<DTNHost>)host.getComBus().getProperty(sHost);
		ArrayList<DTNHost> otherHostExemplarList = (ArrayList<DTNHost>)otherHost.getComBus().getProperty(sOtherHost);
		
//		ArrayList<DTNHost> compareList = new ArrayList<DTNHost>(hostExemplarList);
//		compareList.removeAll(otherHostExemplarList);
//		if(compareList.size()>0){
//			System.out.println("Relay exemplar list are different: "  + host +" > "+hostExemplarList
//					+"\n" + otherHost +" > "+ otherHostExemplarList);
//		}
		
		//don't send message only when
		// both hosts are not exemplar nodes provided their exemplar list is not empty (else it's the 
		//initialization phase).
		if(hostExemplarList!=null && hostExemplarList.size()>0 && !hostExemplarList.contains(host) 
				&& !hostExemplarList.contains(otherHost)
				&&otherHostExemplarList!=null && otherHostExemplarList.size()>0 &&
				!otherHostExemplarList.contains(host) &&
				!otherHostExemplarList.contains(otherHost)){
		//	System.out.println("FALSE The host: " + host + "- " + hostExemplarList +
			//		"\n other host: " + otherHost + " - "  + otherHostExemplarList);
			return false;
		}

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
		//printRelayExemplarList();
		checkAndUpdateRelayExemplarList();
		
		reduceSendingAndScanningEnergy();
		
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		tryAllMessagesToAllConnections();
	}
		
	@Override
	public EnergyAwareSmartEpidemicRouter replicate() {
		return new EnergyAwareSmartEpidemicRouter(this);
	}
	
	/**
	 * Called by the combus is the energy value is changed
	 * @param key The energy ID
	 * @param newValue The new energy value
	 */
	@SuppressWarnings("unchecked")
	public void moduleValueChanged(String key, Object newValue) {
		if(key.equals(IS_RELAY_EXEMPLAR))
			this.isRelayExemplar = (Boolean) newValue;
		else if(key.equals(ENERGY_VALUE_ID))
			this.currentEnergy = (Double)newValue;
		else if(key.equals(ORIGINAL_ENERGY_VALUE))
			this.originalEnergy = (Double) newValue;
		else if (key.equals(PREVIOUS_ENERGY_VALUE))
			this.previousEnergy = (Double) newValue;
		else if(key.equals(getHost().toString()))
			this.currentExemplarsList = (ArrayList<DTNHost>) newValue;
	}

	
	@Override
	public String toString() {
		return super.toString() + " energy level = " + this.currentEnergy;
	}	
}
class energyComparator implements Comparator<DTNHost> {
    @Override
    public int compare(DTNHost a, DTNHost b) {
       if(a.getContactFrequency() > b.getContactFrequency())
    	   return -1;
       else if(a.getContactFrequency() == b.getContactFrequency()){
    	   if((Double)a.getComBus().getProperty("Energy.value")>= (Double) b.getComBus().getProperty("Energy.value")){
    		   return -1;
    	   }
    	   else
    		   return 1;
       }
       else
    	   return 1;
    
    }
}
