/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import core.*;

/**
 * Energy level-aware variant of Epidemic router.
 */
public class EnergyAwareHeirarchialRouter extends ActiveRouter 
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
	
	private final double[] initEnergy;
	private double warmupTime;
	private double currentEnergy;
	/** energy usage per scan */
	private double scanEnergy;
	private double transmitEnergy;
	private double lastScanUpdate;
	private double lastUpdate;
	private double scanInterval;	
	private double samplingInterval;
	private double lastSampleUpdate;
	private double startSamplingTime;
	
	private ModuleCommunicationBus comBus;
	private static Random rng = null;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public EnergyAwareHeirarchialRouter(Settings s) {
		super(s);
		this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
		this.samplingInterval = 300;
		this.lastSampleUpdate = 0;
		this.startSamplingTime = 1800;
		
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
	protected EnergyAwareHeirarchialRouter(EnergyAwareHeirarchialRouter r) {
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
		this.samplingInterval = 300;
		this.lastSampleUpdate = 0;
		this.startSamplingTime = 1800;
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
		
		if (this.comBus == null) {
			this.comBus = getHost().getComBus();
			this.comBus.addProperty(ENERGY_VALUE_ID, this.currentEnergy);
			this.comBus.subscribe(ENERGY_VALUE_ID, this);
		}
		
		if (this.currentEnergy <= 0) {
			/* turn radio off */
			this.comBus.updateProperty(NetworkInterface.RANGE_ID, 0.0);
			return; /* no more energy to start new transfers */
		}
		
		if (simTime > this.lastUpdate && sendingConnections.size() > 0) {
			/* sending data */
			reduceEnergy((simTime - this.lastUpdate) * this.transmitEnergy);
		}
		this.lastUpdate = simTime;
		
		if (simTime > this.lastScanUpdate + this.scanInterval) {
			/* scanning at this update round */
			reduceEnergy(this.scanEnergy);
			this.lastScanUpdate = simTime;
		}
	}
	
	@Override
	public void update() {
		super.update();
		reduceSendingAndScanningEnergy();
		//deleteRawMessagesAndCreateFilteredMessages();
		
//		System.out.println("Last sample Update: "+ this.lastSampleUpdate);
//		System.out.println(" Sampling Interval " + this.samplingInterval);
//		System.out.println(" Start sampling Time "+ this.startSamplingTime);
//		System.out.println(" Current simulation time: "+ SimClock.getTime());
		if(SimClock.getTime() - this.lastSampleUpdate > this.startSamplingTime &&
				SimClock.getTime() - this.lastSampleUpdate < (this.samplingInterval + this.startSamplingTime)){
			System.out.println(" Computer Exemplar at: " + SimClock.getTime() );
			computeExemplar();
			this.lastSampleUpdate = SimClock.getTime();
			this.startSamplingTime = SimClock.getTime() + 300;
		}
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		this.tryAllMessagesToAllConnections();
	}

	private void computeExemplar() {
		DTNHost host = getHost();
		
		if(host.toString().startsWith("CD") || host.toString().startsWith("control_station"))
			return;
		
		if(host.getConnections().size() <1)
			return;
		
		//Initialize all the hosts as a non-exemplar
		host.setExemplar(false);
		
		DTNHost chosenExemplar = host;
		
		//calculate alpha, beta and gamma for the current host
		double alpha = (double)host.getComBus().getProperty(ENERGY_VALUE_ID);
		double beta = (double) host.getProcessingPower();
		double gamma = (double) host.getConnections().size();
		
		double tAlpha = alpha , tBeta = beta, tGamma = gamma;
		
		//calculate alpha, beta and gamma for all neighboring nodes
		//Also calculate the total alpha ,total beta and total gamma for all the nodes in vicinity
		for(Connection conn: this.getConnections()){
			DTNHost oHost = conn.getOtherNode(host);
			if(oHost.toString().startsWith("CD")){
				continue;
			}
			//System.out.println(" The Other host "+oHost.toString()+" Is exemplar ? " + oHost.isExemplar());
			alpha = (double) oHost.getComBus().getProperty(ENERGY_VALUE_ID);
			beta = (double) oHost.getProcessingPower();
			gamma = (double) oHost.getConnections().size();
			
			oHost.setExemplar(false);
			tAlpha += alpha;
			tBeta += beta;
			tGamma += gamma;
			
		}
		double fitValue = (alpha/tAlpha) * (beta/tBeta) * (gamma/tGamma);
		
		for(Connection conn: this.getConnections()){
			DTNHost oHost = conn.getOtherNode(host);
			//System.out.println(" The Other host "+oHost.toString()+" Is exemplar ? " + oHost.isExemplar());
			if(oHost.toString().startsWith("CD")){
				continue;
			}
			
			alpha = (double) oHost.getComBus().getProperty(ENERGY_VALUE_ID);
			beta = (double) oHost.getProcessingPower();
			gamma = (double) oHost.getConnections().size();
			
			if((alpha/tAlpha) * (beta/tBeta) * (gamma/tGamma) > fitValue ){
				chosenExemplar = oHost;
			}
		}
		chosenExemplar.setExemplar(true);
		
		//System.out.println(" The host is: " + host + " is Exemplar ? " + host.isExemplar()
		//		+" "+host.getComBus().getProperty(ENERGY_VALUE_ID)+" "+ host.getProcessingPower()+" "+host.getConnections().size());
		
		host.setlNeighborhoodId(chosenExemplar);
		//System.out.println(" The connection : ");
		for(Connection conn: this.getConnections()){
			DTNHost oHost = conn.getOtherNode(host);
			if(oHost.toString().startsWith("CD")){
				continue;
			}
//			System.out.println(" The oHost "+ oHost+" isExemplar ? "+ oHost.isExemplar()
//					+" "+oHost.getComBus().getProperty(ENERGY_VALUE_ID)+" "+ oHost.getProcessingPower()
//					+" "+oHost.getConnections().size()+"\n\n");
			oHost.setlNeighborhoodId(chosenExemplar);
		}	
	}
	
	protected Connection tryMessagesToConnections(List<Message> messages,
			List<Connection> connections) {
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

	private boolean shouldSendMessage(Connection con) {
		DTNHost host = getHost(), oHost = con.getOtherNode(host);
		
		if((host.isExemplar() && !oHost.isExemplar()) ||
				(!host.isExemplar() && oHost.isExemplar()) ||
				(host.toString().startsWith("CD") && oHost.toString().startsWith("n")) ||
				(host.toString().startsWith("n") && oHost.toString().startsWith("CD")) ||
				(host.toString().startsWith("CD") && oHost.toString().startsWith("control_station")) ||
				(host.toString().startsWith("control_station") && oHost.toString().startsWith("CD"))){
						return true;
		}
		else
			return false;
			//return true;
	}
	
	protected Message tryAllMessages(Connection con, List<Message> messages) {
		//System.out.println("restricted epidemic -  try all messages");
		
		boolean canMsgBeSent= false;
		for (Message m : messages) {
			
			canMsgBeSent = false;
			DTNHost host, otherHost;
			host = getHost();
			otherHost = con.getOtherNode(getHost());
			String sHost, sOtherHost;
			sHost = host.toString();
			sOtherHost = otherHost.toString(); 
			
			//System.out.println("The message " + m.toString() +" can be transferred between "+sHost+"  -  "+sOtherHost);
			
			//from DTN nodes to relief center
			if(m.toString().startsWith("M")){
					if(	(sHost.startsWith("CD") && sOtherHost.startsWith("control_station"))||
						(sHost.startsWith("n") && sOtherHost.startsWith("CD"))||
						(sHost.startsWith("n") && sOtherHost.startsWith("n"))){
						//System.out.println("The message " + m.toString() +" is sent from "+sHost+" to "+sOtherHost);
						canMsgBeSent =true;
					
				}
			}
			//from relief center to DTN nodes
			if(m.toString().startsWith("N")){
				if(	(sHost.startsWith("control_station") && sOtherHost.startsWith("CD"))||
						(sHost.startsWith("CD")	&&  sOtherHost.startsWith("n"))||
						(sHost.startsWith("n") && sOtherHost.startsWith("n"))){
						//System.out.println("The message " + m.toString() +" is sent from "+sHost+" to "+sOtherHost);
						canMsgBeSent =true;
						}
			}
			
			int retVal = -1;
			
			if(canMsgBeSent){
				retVal = startTransfer(m, con); 
			}
				
			
			if (retVal == RCV_OK) {
				//System.out.println("Message "+m.toString() + " is sent succesfully");
				return m;	// accepted a message, don't try others
			}
			else if (retVal > 0) { 
				return null; // should try later -> don't bother trying others
			}
		}
		
		return null; // no message was accepted		
	}

	@Override
	public EnergyAwareHeirarchialRouter replicate() {
		return new EnergyAwareHeirarchialRouter(this);
	}
	
	/**
	 * Called by the combus is the energy value is changed
	 * @param key The energy ID
	 * @param newValue The new energy value
	 */
	public void moduleValueChanged(String key, Object newValue) {
		this.currentEnergy = (Double)newValue;
	}

	
	@Override
	public String toString() {
		return super.toString() + " energy level = " + this.currentEnergy;
	}
	
	protected void transferDone(Connection con) {
		
		String id = con.getMessage().getId();
		DTNHost from = con.getMessage().getFrom();
		
		if(id.startsWith("M") && from.getlNeighborhoodId() == this.getHost()
				&& this.getHost().isExemplar() == true
				&& this.getHost().toString().startsWith("CD") 
				&& !this.getHost().toString().startsWith("control_station")
				){
			Message res = new Message(this.getHost().getControlStation(),from, 
					"P_"+id, 500);
			this.createNewMessage(res);
		}
	
		
		if(id.startsWith("M") && from.getlNeighborhoodId() == this.getHost() && this.getHost().isExemplar() == true){
			this.deleteMessage(id, false);
		}
	}
}