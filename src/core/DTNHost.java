/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import movement.MovementModel;
import movement.Path;
import routing.MessageRouter;
import routing.RoutingInfo;
import routing.clusterBasedRouting.NodeInformation;

/**
 * A DTN capable host.
 */
public class DTNHost implements Comparable<DTNHost> {
	private static int nextAddress = 0;
	private int address;

	private Coord location; 	// where is the host
	private Coord destination;	// where is it going

	private MessageRouter router;
	private MovementModel movement;
	private Path path;
	private double speed;
	private double nextTimeToMove;
	private String name;
	private List<MessageListener> msgListeners;
	private List<MovementListener> movListeners;
	private List<NetworkInterface> net;
	private ModuleCommunicationBus comBus;
	private boolean relayExemplar = false;
	private int contactFrequency = 0;
	private DTNHost destinationRW = null;
	private Map<String,ArrayList<DTNHost>> relayExemplarMap = new HashMap<String, ArrayList<DTNHost>>();
	private ArrayList<DTNHost> relayExemplarList = new ArrayList<DTNHost>();
	private String exemplarString;
	//My code
	public double energy=0;
	public double transferLoss=0;
	public double recieverLoss=0;
	public double scannerLoss=0;
	
	public double initialEnergy = -1;
	public double prevEnergy = -1;
	public double curEnergy = -1;
	
	public double lastExemplarListUpdated =0.0;
	public boolean isExemplar = false;
	public double processingPower = 2;
	public DTNHost lNeighborhoodId = null;
	private NodeInformation nodeInf;
	private double weightedFitness = -1;
	private NodeInformation nodeInformation;
	//My code end
	private ArrayList<String> currentNodeNeighborList;
	private ArrayList<DTNHost> failedNodeList;

	private int isGrnRouter = 1;
	
	static {
		DTNSim.registerForReset(DTNHost.class.getCanonicalName());
		reset();
	}
	/**
	 * Creates a new DTNHost.
	 * @param msgLs Message listeners
	 * @param movLs Movement listeners
	 * @param groupId GroupID of this host
	 * @param interf List of NetworkInterfaces for the class
	 * @param comBus Module communication bus object
	 * @param mmProto Prototype of the movement model of this host
	 * @param mRouterProto Prototype of the message router of this host
	 */
	public DTNHost(List<MessageListener> msgLs,
			List<MovementListener> movLs,
			String groupId, List<NetworkInterface> interf,
			ModuleCommunicationBus comBus, 
			MovementModel mmProto, MessageRouter mRouterProto) {
		this.comBus = comBus;
		this.location = new Coord(0,0);
		this.address = getNextAddress();
		this.name = groupId+address;
		this.net = new ArrayList<NetworkInterface>();

		for (NetworkInterface i : interf) {
			NetworkInterface ni = i.replicate();
			ni.setHost(this);
			net.add(ni);
		}	

		// TODO - think about the names of the interfaces and the nodes
		//this.name = groupId + ((NetworkInterface)net.get(1)).getAddress();

		this.msgListeners = msgLs;
		this.movListeners = movLs;

		// create instances by replicating the prototypes
		this.movement = mmProto.replicate();
		this.movement.setComBus(comBus);
		setRouter(mRouterProto.replicate());

		this.location = movement.getInitialLocation();

		this.nextTimeToMove = movement.nextPathAvailable();
		this.path = null;

		if (movLs != null) { // inform movement listeners about the location
			for (MovementListener l : movLs) {
				l.initialLocation(this, this.location);
			}
		}

	}
	
	/**
	 * Returns a new network interface address and increments the address for
	 * subsequent calls.
	 * @return The next address.
	 */
	private synchronized static int getNextAddress() {
		return nextAddress++;	
	}

	/**
	 * Reset the host and its interfaces
	 */
	public static void reset() {
		nextAddress = 0;
	}

	/**
	 * Returns true if this node is active (false if not)
	 * @return true if this node is active (false if not)
	 */
	public boolean isActive() {
		return this.movement.isActive();
	}

	/**
	 * Set a router for this host
	 * @param router The router to set
	 */
	private void setRouter(MessageRouter router) {
		router.init(this, msgListeners);
		this.router = router;
	}

	/**
	 * Returns the router of this host
	 * @return the router of this host
	 */
	public MessageRouter getRouter() {
		return this.router;
	}

	/**
	 * Returns the network-layer address of this host.
	 */
	public int getAddress() {
		return this.address;
	}
	
	/**
	 * Returns this hosts's ModuleCommunicationBus
	 * @return this hosts's ModuleCommunicationBus
	 */
	public ModuleCommunicationBus getComBus() {
		return this.comBus;
	}
	
    /**
	 * Informs the router of this host about state change in a connection
	 * object.
	 * @param con  The connection object whose state changed
	 */
	public void connectionUp(Connection con) {
		this.router.changedConnection(con);
	}

	public void connectionDown(Connection con) {
		this.router.changedConnection(con);
	}

	/**
	 * Returns a copy of the list of connections this host has with other hosts
	 * @return a copy of the list of connections this host has with other hosts
	 */
	public List<Connection> getConnections() {
		List<Connection> lc = new ArrayList<Connection>();

		for (NetworkInterface i : net) {
			lc.addAll(i.getConnections());
		}

		return lc;
	}

	/**
	 * Returns the current location of this host. 
	 * @return The location
	 */
	public Coord getLocation() {
		return this.location;
	}

	/**
	 * Returns the Path this node is currently traveling or null if no
	 * path is in use at the moment.
	 * @return The path this node is traveling
	 */
	public Path getPath() {
		return this.path;
	}


	/**
	 * Sets the Node's location overriding any location set by movement model
	 * @param location The location to set
	 */
	public void setLocation(Coord location) {
		this.location = location.clone();
	}

	/**
	 * Sets the Node's name overriding the default name (groupId + netAddress)
	 * @param name The name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the messages in a collection.
	 * @return Messages in a collection
	 */
	public Collection<Message> getMessageCollection() {
		//System.out.println("The message collection is at host " +this+"  " + this.router.getMessageCollection());
		return this.router.getMessageCollection();
	}

	/**
	 * Returns the number of messages this node is carrying.
	 * @return How many messages the node is carrying currently.
	 */
	public int getNrofMessages() {
		return this.router.getNrofMessages();
	}

	/**
	 * Returns the buffer occupancy percentage. Occupancy is 0 for empty
	 * buffer but can be over 100 if a created message is bigger than buffer 
	 * space that could be freed.
	 * @return Buffer occupancy percentage
	 */
	public double getBufferOccupancy() {
		double bSize = router.getBufferSize();
		double freeBuffer = router.getFreeBufferSize();
		return 100*((bSize-freeBuffer)/bSize);
	}

	/**
	 * Returns routing info of this host's router.
	 * @return The routing info.
	 */
	public RoutingInfo getRoutingInfo() {
		return this.router.getRoutingInfo();
	}

	/**
	 * Returns the interface objects of the node
	 */
	public List<NetworkInterface> getInterfaces() {
		return net;
	}

	/**
	 * Find the network interface based on the index
	 */
	protected NetworkInterface getInterface(int interfaceNo) {
		NetworkInterface ni = null;
		try {
			ni = net.get(interfaceNo-1);
		} catch (IndexOutOfBoundsException ex) {
			System.out.println("No such interface: "+interfaceNo);
			System.exit(0);
		}
		return ni;
	}

	/**
	 * Find the network interface based on the interfacetype
	 */
	public NetworkInterface getInterface(String interfacetype) {
		for (NetworkInterface ni : net) {
			if (ni.getInterfaceType().equals(interfacetype)) {
				return ni;
			}
		}
		return null;	
	}

	/**
	 * Force a connection event
	 */
	public void forceConnection(DTNHost anotherHost, String interfaceId, 
			boolean up) {
		NetworkInterface ni;
		NetworkInterface no;

		if (interfaceId != null) {
			ni = getInterface(interfaceId);
			no = anotherHost.getInterface(interfaceId);

			assert (ni != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
			assert (no != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
		} else {
			ni = getInterface(1);
			no = anotherHost.getInterface(1);
			
			assert (ni.getInterfaceType().equals(no.getInterfaceType())) : 
				"Interface types do not match.  Please specify interface type explicitly";
		}
		
		if (up) {
			ni.createConnection(no);
		} else {
			ni.destroyConnection(no);
		}
	}

	/**
	 * for tests only --- do not use!!!
	 */
	public void connect(DTNHost h) {
		System.err.println(
				"WARNING: using deprecated DTNHost.connect(DTNHost)" +
		"\n Use DTNHost.forceConnection(DTNHost,null,true) instead");
		forceConnection(h,null,true);
	}

	/**
	 * Updates node's network layer and router.
	 * @param simulateConnections Should network layer be updated too
	 */
	public void update(boolean simulateConnections) {
		if (!isActive()) {
			return;
		}
		
		if (simulateConnections) {
			for (NetworkInterface i : net) {
				i.update();
			}
		}
		this.router.update();
	}

	/**
	 * Moves the node towards the next waypoint or waits if it is
	 * not time to move yet
	 * @param timeIncrement How long time the node moves
	 */
	public void move(double timeIncrement) {		
		double possibleMovement;
		double distance;
		double dx, dy;

		if (!isActive() || SimClock.getTime() < this.nextTimeToMove) {
			return; 
		}
		if (this.destination == null) {
			if (!setNextWaypoint()) {
				return;
			}
		}

		possibleMovement = timeIncrement * speed;
		distance = this.location.distance(this.destination);

		while (possibleMovement >= distance) {
			// node can move past its next destination
			this.location.setLocation(this.destination); // snap to destination
			possibleMovement -= distance;
			if (!setNextWaypoint()) { // get a new waypoint
				return; // no more waypoints left
			}
			distance = this.location.distance(this.destination);
		}

		// move towards the point for possibleMovement amount
		dx = (possibleMovement/distance) * (this.destination.getX() -
				this.location.getX());
		dy = (possibleMovement/distance) * (this.destination.getY() -
				this.location.getY());
		this.location.translate(dx, dy);
	}	

	/**
	 * Sets the next destination and speed to correspond the next waypoint
	 * on the path.
	 * @return True if there was a next waypoint to set, false if node still
	 * should wait
	 */
	private boolean setNextWaypoint() {
		if (path == null) {
			path = movement.getPath();
		}

		if (path == null || !path.hasNext()) {
			this.nextTimeToMove = movement.nextPathAvailable();
			this.path = null;
			return false;
		}

		this.destination = path.getNextWaypoint();
		this.speed = path.getSpeed();

		if (this.movListeners != null) {
			for (MovementListener l : this.movListeners) {
				l.newDestination(this, this.destination, this.speed);
			}
		}

		return true;
	}

	/**
	 * Sends a message from this host to another host
	 * @param id Identifier of the message
	 * @param to Host the message should be sent to
	 */
	public void sendMessage(String id, DTNHost to) {
		this.router.sendMessage(id, to);
	}

	/**
	 * Start receiving a message from another host
	 * @param m The message
	 * @param from Who the message is from
	 * @return The value returned by 
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}
	 */
	public int receiveMessage(Message m, DTNHost from) {
		int retVal = this.router.receiveMessage(m, from); 

		if (retVal == MessageRouter.RCV_OK) {
			m.addNodeOnPath(this);	// add this node on the messages path
		
		}

		return retVal;	
	}

	/**
	 * Requests for deliverable message from this host to be sent trough a
	 * connection.
	 * @param con The connection to send the messages trough
	 * @return True if this host started a transfer, false if not
	 */
	public boolean requestDeliverableMessages(Connection con) {
		//if(this.getNeighborList().contains(con.getOtherNode(this)) && isGrnRouter == 1){
			return this.router.requestDeliverableMessages(con);
		//}
		//return false;

	}

	/**
	 * Informs the host that a message was successfully transferred.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 */
	public void messageTransferred(String id, DTNHost from) {

		//change the destination of message to control station
	
		this.router.messageTransferred(id, from);
	}

	/**
	 * Informs the host that a message transfer was aborted.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 * @param bytesRemaining Nrof bytes that were left before the transfer
	 * would have been ready; or -1 if the number of bytes is not known
	 */
	public void messageAborted(String id, DTNHost from, int bytesRemaining) {
		this.router.messageAborted(id, from, bytesRemaining);
	}

	/**
	 * Creates a new message to this host's router
	 * @param m The message to create
	 */
	public void createNewMessage(Message m) {

		//create a new message at any node only when it's neighborhood is determined
//		if(m.getId().startsWith("M") && m.getFrom().getlNeighborhoodId() != null){
//			m.setTo(m.getFrom().getlNeighborhoodId());
//			this.router.createNewMessage(m);
//		}	
		this.router.createNewMessage(m);
	}

	/**
	 * Deletes a message from this host
	 * @param id Identifier of the message
	 * @param drop True if the message is deleted because of "dropping"
	 * (e.g. buffer is full) or false if it was deleted for some other reason
	 * (e.g. the message got delivered to final destination). This effects the
	 * way the removing is reported to the message listeners.
	 */
	public void deleteMessage(String id, boolean drop) {
		System.out.println(" The message is deleted: "+ id +"  ");
		this.router.deleteMessage(id, drop);
	}

	/**
	 * Returns a string presentation of the host.
	 * @return Host's name
	 */
	public String toString() {
		return name;
	}

	/**
	 * Checks if a host is the same as this host by comparing the object
	 * reference
	 * @param otherHost The other host
	 * @return True if the hosts objects are the same object
	 */
	public boolean equals(DTNHost otherHost) {
		return this == otherHost;
	}

	/**
	 * Compares two DTNHosts by their addresses.
	 * @see Comparable#compareTo(Object)
	 */
	public int compareTo(DTNHost h) {
		return this.getAddress() - h.getAddress();
	}

	public float getFreeBufferSize(){
		return this.router.getFreeBufferSize();
	}

	public boolean getRelayExemplar(){
		return this.relayExemplar;
	}
	public void setRelayExemplar(boolean isTrue){
		this.relayExemplar = isTrue;
	}
	public int getContactFrequency(){
		return this.contactFrequency;
	}

	public void setContactFrequency(int contactFrequency){
		this.contactFrequency = contactFrequency;
	}

	public void setDestinationRW(DTNHost dest){
		this.destinationRW = dest;
	}

	public DTNHost getDestinationRW(){
		return this.destinationRW;
	}
	//my code
	public double getEnergy(){
		return this.energy;
		}
	//my code end

	public ArrayList<DTNHost> getRelayExemplarMap() {
		return this.relayExemplarList;
	}

	public void setRelayExemplarMap(ArrayList<DTNHost> relayExemplarList) {
		if(this.name.toString().startsWith("n") && 
				relayExemplarList.size()> this.relayExemplarList.size()){
			System.out.println("The prev and current exemplar list: "+ this.name+" "+ 
				this.relayExemplarList+"\n"+relayExemplarList);
		}
		
			this.relayExemplarList = relayExemplarList;
			this.lastExemplarListUpdated = SimClock.getTime();
			this.exemplarString = relayExemplarList.toString();
	}
	
	public int getClusterNumber(){
		String clusterNum = "-1";
		if(this.name.startsWith("n")){
			String[] parts = this.name.split("_");
			clusterNum = parts[0].substring(1);
			//System.out.println("The cluster Num: "+clusterNum);
		}
		return Integer.parseInt(clusterNum);
	}
	
	public double getInitialEnergy() {
		return initialEnergy;
	}

	public void setInitialEnergy(double initialEnergy) {
		this.initialEnergy = initialEnergy;
	}

	public double getPrevEnergy() {
		return prevEnergy;
	}

	public void setPrevEnergy(double prevEnergy) {
		this.prevEnergy = prevEnergy;
	}

	public double getCurEnergy() {
		return curEnergy;
	}

	public void setCurEnergy(double curEnergy) {
		this.curEnergy = curEnergy;
	}
	public boolean isSameCluster(DTNHost otherHost){
		int otherClusterNumber = otherHost.getClusterNumber();
		int currentClusterNumber = getClusterNumber();
		
		if(currentClusterNumber == otherClusterNumber)
			return true;
		else
			return false;
		
	}
	public void printRelayMap(){
		//then you just access the reversedMap however you like...
		for (Map.Entry entry : this.relayExemplarMap.entrySet()) {
		    System.out.println(entry.getKey() + ", " + entry.getValue());
		}
	}

	public double getLastExemplarListUpdated() {
		return lastExemplarListUpdated;
	}

	public void setLastExemplarListUpdated(double lastExemplarListUpdated) {
		this.lastExemplarListUpdated = lastExemplarListUpdated;
	}

	public String getExemplarString() {
		return exemplarString;
	}

	public void setExemplarString(String exemplarString) {
		this.exemplarString = exemplarString;
	}
	
	public boolean isExemplar() {
		return isExemplar;
	}

	public void setExemplar(boolean isExemplar) {
		this.isExemplar = isExemplar;
	}

	public double getProcessingPower() {
		return processingPower;
	}

	public void setProcessingPower(double processingPower) {
		this.processingPower = processingPower;
	}

	public DTNHost getlNeighborhoodId() {
		return lNeighborhoodId;
	}

	public void setlNeighborhoodId(DTNHost lNeighborhoodId) {
		this.lNeighborhoodId = lNeighborhoodId;
	}

	public DTNHost getControlStation(){
		List<DTNHost> hosts = SimScenario.getInstance().getHosts();
		DTNHost controlStation = this;
		for(DTNHost host: hosts){
			if(host.toString().contains("control_station")){
				controlStation = host;
				break;
			}
		}
		return controlStation;
	}
	private Boolean checkIfSameExemplarList(ArrayList<DTNHost> prevExemplarList, ArrayList<DTNHost> currentExemplarList){
		System.out.println("The prev and current lists are: "+prevExemplarList+"\n "+currentExemplarList);
		if(prevExemplarList.size()>0 && currentExemplarList.size() > 0){
			prevExemplarList.removeAll(currentExemplarList);
			if(prevExemplarList.size()>0)
				return false;
		}
		return true;
}
	
	public double getWeightedFitness(){
		if(this.nodeInf != null)
			return this.nodeInf.getWeightedFitness();
		else if(this.weightedFitness > -1)
			return this.weightedFitness;
		else
			return -1;
			
	}
	
	public void setWeightedFitness(double weightedFitness){
		this.weightedFitness = weightedFitness;
	}

	public void setNodeInformation(NodeInformation nodeInf2) {
		 nodeInformation = nodeInf2;
		
	}
	public NodeInformation getNodeInformation(){
		return nodeInformation;
	}

	//Bio-DRN Functions
	public void setNeighborList(ArrayList<String> currentNodeNeighborList) {
		this.currentNodeNeighborList = currentNodeNeighborList;
		
	}
	
	public List<String> getNeighborList(){
		return this.currentNodeNeighborList;
	}
	
	public ArrayList<String> getFailedNodeList(int simTime){
		int noOfHosts = SimScenario.getInstance().getHosts().size();
		File inFile = new File("/Users/vijay/BioDRNICDCSWorkSpace/ONEICDCS/src/FailedNodeList/F_C"+String.valueOf(noOfHosts)+".txt");
//      File inFile = new File("/mounts/u-amo-d0/guest/vksh224/BioDRNICDCSWorkSpace/EdgeRewiringONE/src/FailedNodeList/F_C" + String.valueOf(noOfHosts)+".txt");

		ArrayList<String> allLines = new ArrayList<String>();
		
		Scanner scanner = null;
		try {
				scanner = new Scanner(inFile);
			} catch (FileNotFoundException e) {
				System.out.println("Couldn't find external movement input " +
						"file " + inFile);
			}
		
		//read all lines
		while(scanner.hasNextLine()){
			String currentLine = scanner.nextLine();
			//System.out.println("Here " + currentLine );
			allLines.add(currentLine);
		}	
			
		ArrayList<String> failedNodeList = new ArrayList<String>();
		
		for(int i =0; i < allLines.size(); i++){
			Scanner lineScan = new Scanner(allLines.get(i));
			Double time = lineScan.nextDouble();
			
			if(simTime == time){
				while(lineScan.hasNext()){
					int value = lineScan.nextInt();
					//System.out.print(value +" ");
					failedNodeList.add("n" + value);
				}
			}	
		}
		return failedNodeList;
	}
	
}
