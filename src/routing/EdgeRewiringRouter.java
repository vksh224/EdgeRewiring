/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import input.NeighborListReader;

import java.util.ArrayList;
import java.util.List;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.NetworkInterface;
import core.Settings;
import core.SimClock;
import core.SimScenario;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class EdgeRewiringRouter extends ActiveRouter {
	
	private static NeighborListReader reader;
	private double samplingInterval = 900;
	private double lastSamplingUpdate = 0;
	private ArrayList<String >currentNodeNeighborList;
	private ArrayList<String> failedNodeList;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public EdgeRewiringRouter(Settings s) {
		super(s);
		String filePath = s.getSetting("neighborListFile");
		reader = new NeighborListReader(filePath);
		
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EdgeRewiringRouter(EdgeRewiringRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
	}
	
	
			
	@Override
	public void update() {
		super.update();
		updateNeighborList();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
//		if (exchangeDeliverableMessages() != null) {
//			return; // started a transfer, don't try others (yet)
//		}
		
		// then try any/all message to any/all connection
		this.tryAllMessagesToAllConnections();
	}
	
	protected void killNodesInFailedNodeList(){
		
		 if (SimClock.getIntTime() >= this.lastSamplingUpdate) {
			failedNodeList = getHost().getFailedNodeList(SimClock.getIntTime());
			List<DTNHost> hostList = SimScenario.getInstance().getHosts();
			 
			for(DTNHost thisHost: hostList){
				if(failedNodeList!= null && failedNodeList.contains(thisHost.toString())){
					thisHost.getComBus().updateProperty(NetworkInterface.RANGE_ID, 0.0);
					System.out.println("Failed node: " + SimClock.getIntTime() + "  " +  thisHost.toString());
				}
			}
			this.lastSamplingUpdate += this.samplingInterval;
		 }
		
	}
	protected void updateNeighborList() {
		
		if (SimClock.getIntTime() == this.lastSamplingUpdate) {
				
			currentNodeNeighborList = reader.getNeighborList(getHost().toString(), SimClock.getIntTime());
			this.lastSamplingUpdate += this.samplingInterval;
			getHost().setNeighborList(currentNodeNeighborList);
			
//			 if (SimClock.getIntTime() % this.samplingInterval == 0 && getHost().toString().matches("n0")) {
//				System.out.println("At time: " + SimClock.getIntTime() +" Neighorlist: ");
//				 if(currentNodeNeighborList != null){
//					System.out.println("Node " + getHost().toString() +" : " + currentNodeNeighborList.toString());
//				}
//			 }
		 }	
	}

	
	protected Connection tryAllMessagesToAllConnections(){
		List<Connection> connections = getConnections();
		if (connections.size() == 0 || this.getNrofMessages() == 0) {
			return null;
		}

		List<Message> messages = 
			new ArrayList<Message>(this.getMessageCollection());
		this.sortByQueueMode(messages);

		return tryMessagesToConnections(messages, connections);
	}
		
	
	protected Connection tryMessagesToConnections(List<Message> messages,
			List<Connection> connections) {
		
		for (int i=0, n=connections.size(); i<n; i++) {
			Connection con = connections.get(i);
			
			boolean canMsgBeSent = shouldMessageBeSent(con);
			
			if(canMsgBeSent == false)
				continue;
			
			else{
//				System.out.println("At Timeslot: " + SimClock.getTime()+" from: "+getHost()+" To: "+con.getOtherNode(getHost()));
				Message started = tryAllMessages(con, messages); 
				if (started != null) { 
					return con;
				}
			}
		}
		
		return null;
	}
	
	protected boolean shouldMessageBeSent(Connection con) {
		boolean canMsgBeSent= false;
		
		DTNHost host, otherHost;
		host = getHost();
		otherHost = con.getOtherNode(getHost());
		
		String sOtherHost;
		sOtherHost = otherHost.toString(); 
		
//		if(SimClock.getIntTime() == 3600){
//			System.out.print("The neighborlist: " + host.getNeighborList().toString());
//		}
		
		if(host.getNeighborList().contains(sOtherHost)) {
				//|| otherHost.getNeighborList().contains(host.toString())){
			canMsgBeSent = true;
//			System.out.println(host + " = " + sOtherHost.toString());
		}

		else
			canMsgBeSent = false;
			
		return canMsgBeSent;	
	}
	
	@Override
	public EdgeRewiringRouter replicate() {
		return new EdgeRewiringRouter(this);
	}

}