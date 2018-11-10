
/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import core.*;

/**
 * Energy level-aware and smartly computed variant of Epidemic router.
 */
public class EnergyAwareSmartEpidemicRouterBackup extends EnergyAwareRestrictedEpidemicRouter 
		implements ModuleCommunicationListener{
	private ModuleCommunicationBus comBus;
	public static final String IS_RELAY_EXEMPLAR = "isRelayExemplar";
	public boolean isRelayExemplar = false;
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public EnergyAwareSmartEpidemicRouterBackup(Settings s) {
		super(s);
	}
	
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EnergyAwareSmartEpidemicRouterBackup(EnergyAwareSmartEpidemicRouterBackup r) {
		super(r);
		
	}
	
	public void checkAndUpdateRelayExemplars(){
		int existingExemplars = 0;
		
		System.out.println("Existing relay exemplars are: ");
		if(getHost().getRelayExemplar() && (Double)getHost().getComBus().getProperty(ENERGY_VALUE_ID) > 10){
			existingExemplars++;
			System.out.print(getHost() + "  ");
		}
		for(Connection con: getConnections()){
			DTNHost host = con.getOtherNode(getHost());
			if(existingExemplars >= 3)
				return;
			
			if(host.getRelayExemplar() && (Double)host.getComBus().getProperty(ENERGY_VALUE_ID) > 10){
				existingExemplars++;
				System.out.print(host + "  ");
			}
		}
		System.out.println(" - ----------------");
		
		if(existingExemplars < 3){
			System.out.println("\nWe would compute the relay exemplars for current connections now: ");
			computeRelayExemplars(getConnections());
		}
	}
	/**
	 * Choose a certain number of DTNs among the available DTNs in all possible connections in every
	 * neighborhood which are fit to transfer messages to data mules
	 * @param connections
	 */
	public void computeRelayExemplars(List<Connection> connections){
		ArrayList<DTNHost> relayExemplars = new ArrayList<DTNHost>();
		double minEnergy = 0;
		int minNumberOfExemplars =3;
		
//		if (comBus == null) {
//			this.comBus = getHost().getComBus();
//			this.comBus.addProperty(IS_RELAY_EXEMPLAR, this.isRelayExemplar);
//			this.comBus.subscribe(IS_RELAY_EXEMPLAR, this);
//		}
		//reset the relay exemplar list
		if(relayExemplars.size() > 0){
			relayExemplars.clear();
		}
		DTNHost potentialExemplar=getHost();
		//System.out.println("Node energy is: "  +potentialExemplar.getEnergy() + " contact frequency :"+potentialExemplar.getContactFrequency());
		
		if(potentialExemplar.toString().startsWith("n") && !potentialExemplar.toString().startsWith("neig")){
			//System.out.println("The energy of current node is " + potentialExemplar.getComBus().getProperty(ENERGY_VALUE_ID));
			potentialExemplar.setRelayExemplar(false);
			relayExemplars.add(potentialExemplar);
			//getHost().getComBus().updateProperty(IS_RELAY_EXEMPLAR, false);
		}
		
		for (Connection con : connections) {
			potentialExemplar = con.getOtherNode(getHost());
			//System.out.println("Node energy is: "  +potentialExemplar.getEnergy() + " contact frequency :"+potentialExemplar.getContactFrequency());
			if(potentialExemplar.toString().startsWith("n") && !potentialExemplar.toString().startsWith("neig")){
				potentialExemplar.setRelayExemplar(false);
				relayExemplars.add(potentialExemplar);
				//potentialExemplar.getComBus().updateProperty(IS_RELAY_EXEMPLAR, false);
			}
		}
		Collections.sort(relayExemplars, new energyComparator());
//		System.out.println("The relay exemplars list is: " );
//		for(int i = 0; i< relayExemplars.size();i++){
//			System.out.println(relayExemplars.get(i).toString()+"   "+ relayExemplars.get(i).getContactFrequency()+"  "+relayExemplars.get(i).getComBus().getProperty(ENERGY_VALUE_ID));
//			
//		}
		System.out.println("The chosen relay exemplars above critical Energy: " );
		int numOfRelayExemplars = 0;
		double criticalEnergy = 10;
		for(int i = 0; i< relayExemplars.size();i++){
			
			if(numOfRelayExemplars < minNumberOfExemplars && (Double)relayExemplars.get(i).getComBus().getProperty(ENERGY_VALUE_ID) >= criticalEnergy){
				System.out.print(relayExemplars.get(i).toString()+"   ");
				relayExemplars.get(i).setRelayExemplar(true);
				//relayExemplars.get(i).getComBus().updateProperty(IS_RELAY_EXEMPLAR, true);
				numOfRelayExemplars++;
			}				
		}
		
		if(numOfRelayExemplars < minNumberOfExemplars && relayExemplars.size() >= minNumberOfExemplars){
			System.out.println("The chosen relay exemplars below critical Energy: ");
			for(int i = 0; i< relayExemplars.size();i++){
				
				if(numOfRelayExemplars < minNumberOfExemplars &&
						(Double)relayExemplars.get(i).getComBus().getProperty(ENERGY_VALUE_ID) > minEnergy &&
						(Double)relayExemplars.get(i).getComBus().getProperty(ENERGY_VALUE_ID) < criticalEnergy){
					System.out.print(relayExemplars.get(i).toString()+"   ");
					relayExemplars.get(i).setRelayExemplar(true);
					//relayExemplars.get(i).getComBus().updateProperty(IS_RELAY_EXEMPLAR, true);
					numOfRelayExemplars++;
				}				
			}
		}
		
	}
	/**
	 * Message should not be sent from one non-exemplar node to another non-exemplar node
	 * @param con
	 * @return
	 */
	protected boolean shouldSendMessage(Connection con)
	{
		DTNHost to, from;
		to = getHost();
		from = con.getOtherNode(getHost());
		
		/*		(to.toString().contains("DB") && from.toString().contains("CD"))||
				(to.toString().contains("CD") && from.toString().contains("DB"))||
				(to.getRelayExemplar() && from.toString().contains("CD"))||
				(from.getRelayExemplar() && to.toString().contains("CD"))||
				(!to.getRelayExemplar() && from.getRelayExemplar())||
				(to.getRelayExemplar() && !from.getRelayExemplar()))
		*/
		System.out.println("The host is "+ to.toString()+" "+ to.getRelayExemplar());
		System.out.println("The other node is "  +from.toString()+ " "+ from.getRelayExemplar());
		if(to.toString().startsWith("n") && !to.getRelayExemplar() &&
				from.toString().startsWith("n") && !from.getRelayExemplar()){
			return false;
		}
		else
			return true;

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
		
		checkAndUpdateRelayExemplars();
		
		boolean shouldSendMsg = false;
		for (int i=0, n=connections.size(); i<n; i++) {
			Connection con = connections.get(i);
			//added code
			shouldSendMsg = shouldSendMessage(con);
			Message started=null;
			
			if(shouldSendMsg){
				started = tryAllMessages(con, messages);
			}
			if (started != null) { 
				return con;
			}
		}
		
		return null;
	}	
	
	public void updateContactFrequency(List<Connection> connections){
		DTNHost to, from;
		to = getHost();
		
			for(Connection con: connections){
				from = con.getOtherNode(getHost());
				if(to.toString().startsWith("CD")){
					from.setContactFrequency(from.getContactFrequency()+1);
					//System.out.println("Inside update contact Frequency "+x.toString()+ " "+y.toString() + x.getContactFrequency());
				}
				else if(from.toString().startsWith("CD")){
					to.setContactFrequency(to.getContactFrequency()+1);
					//System.out.println("Inside update contact Frequency "+x.toString()+ " "+y.toString());
				}			
			}
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
		
		updateContactFrequency(connections);
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
	}
		
	@Override
	public EnergyAwareSmartEpidemicRouterBackup replicate() {
		return new EnergyAwareSmartEpidemicRouterBackup(this);
	}
	
}