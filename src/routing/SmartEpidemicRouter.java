
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
public class SmartEpidemicRouter extends RestrictedEpidemicRouter 
		implements ModuleCommunicationListener{
	private ModuleCommunicationBus comBus;
	public static final String IS_RELAY_EXEMPLAR = "Host.isRelayExemplar";
	public boolean isRelayExemplar;
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public SmartEpidemicRouter(Settings s) {
		super(s);
	}
	
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected SmartEpidemicRouter(SmartEpidemicRouter r) {
		super(r);
		this.comBus = null;
		this.isRelayExemplar = true;
		
	}
	
	/**
	 * Check the existing node if it's the best exemplar node
	 * from all available connections
	 */
	protected void checkAndUpdateRelayExemplars() {
		
		if (this.comBus == null) {
			this.comBus = getHost().getComBus();
			this.comBus.addProperty(IS_RELAY_EXEMPLAR, this.isRelayExemplar);
			this.comBus.subscribe(IS_RELAY_EXEMPLAR, this);
		}
		DTNHost host , otherHost, currentExemplar = getHost();
		int maxContactFreq = 0;
		
		host = this.getHost();
		//check if current nodes is already an exemplar for current connections
		if(host.getComBus().getProperty(IS_RELAY_EXEMPLAR)!=null &&
				(Boolean) host.getComBus().getProperty(IS_RELAY_EXEMPLAR)){
				maxContactFreq = host.getContactFrequency();
				currentExemplar = host;
		}
		
		for(Connection con : getConnections()){			
			otherHost = con.getOtherNode(host);
			
			if(otherHost.getComBus().getProperty(IS_RELAY_EXEMPLAR)!=null &&
					(Boolean) otherHost.getComBus().getProperty(IS_RELAY_EXEMPLAR)){
					if(otherHost.getContactFrequency() < maxContactFreq){
						otherHost.getComBus().updateProperty(IS_RELAY_EXEMPLAR, false);
					}
					else{
						//if other node is better connected to CD, update other node as relay exemplar
						// Current node won't act as relay exemplar any more
						maxContactFreq = otherHost.getContactFrequency();
						this.comBus.updateProperty(IS_RELAY_EXEMPLAR, true);
						currentExemplar = otherHost;
					}
			}
		}
		//initially assign every nodes as relay exemplars
		if (maxContactFreq == 0 && host.toString().startsWith("n")) {
			this.comBus.updateProperty(IS_RELAY_EXEMPLAR, true);
			currentExemplar = host;
		}
		System.out.println("The current exemplar is : " + currentExemplar.toString() 
				+ " with contact frequency "+ currentExemplar.getContactFrequency() + " for following connections: ");
		System.out.println(host+"  "+host.getContactFrequency());
		for(Connection con: getConnections()){
			otherHost = con.getOtherNode(host);
			System.out.println(otherHost+"   "+otherHost.getContactFrequency());
		}
		System.out.println("\n -------------------------------");
			return;
	}
	/**
	 * Choose a certain number of DTNs among the available DTNs in all possible connections in every
	 * neighborhood which are fit to transfer messages to data mules
	 * @param connections
	 */
	public void computeRelayExemplars(List<Connection> connections){
		ArrayList<DTNHost> relayExemplars = new ArrayList<DTNHost>();
		int minNumberOfExemplars =3;
		
		Collections.sort(relayExemplars, new energyComparator());
		System.out.println("The relay Exemplars list is: " + relayExemplars.toString());
		System.out.println("The chosen relay exemplars are: " );
		for(int i = 0; i <= minNumberOfExemplars && i< relayExemplars.size();i++){
			System.out.print(relayExemplars.get(i).toString()+"   ");
			relayExemplars.get(i).setRelayExemplar(true);
		}
		System.out.println("-----------------------------------");
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
		
		if(		(sHost.contains("DB") && sOtherHost.startsWith("CD"))||
				(sHost.contains("CD") && sOtherHost.startsWith("DB"))||
				((Boolean)host.getComBus().getProperty(IS_RELAY_EXEMPLAR) == true && sOtherHost.startsWith("CD"))||
				( sHost.startsWith("CD")  && (Boolean)otherHost.getComBus().getProperty(IS_RELAY_EXEMPLAR) == true)||
				((Boolean)host.getComBus().getProperty(IS_RELAY_EXEMPLAR)== true && 
				(Boolean)otherHost.getComBus().getProperty(IS_RELAY_EXEMPLAR) == false)||
				((Boolean)host.getComBus().getProperty(IS_RELAY_EXEMPLAR)== false && 
				(Boolean)otherHost.getComBus().getProperty(IS_RELAY_EXEMPLAR) == true)){
					return true;
				}
		else
			return false;
		/*
		if(sHost.startsWith("n") && 
			(Boolean) host.getComBus().getProperty(IS_RELAY_EXEMPLAR) == false && 
			sOtherHost.startsWith("n") && 
			(Boolean)otherHost.getComBus().getProperty(IS_RELAY_EXEMPLAR)== false &&
			((host.getContactFrequency() == 0 && otherHost.getContactFrequency() > 0) ||
			(host.getContactFrequency() > 0 && otherHost.getContactFrequency() == 0))){
			//System.out.println("Message can <NOT BE SENT> between " + sHost +"  - " + sOtherHost);
			return false;
		}
		else{
			//System.out.println("Message can be <SENT> between " + sHost +"  - " + sOtherHost);
			return true;
		}*/
			

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
		
		//computeRelayExemplars(connections);
		
		boolean shouldSendMsg = true;
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
	
	public void updateContactFrequency(){
		DTNHost host, otherHost;
		
		
			for(Connection con: getConnections()){
				host = getHost();
				otherHost = con.getOtherNode(getHost());
				if(host.toString().startsWith("CD") && otherHost.toString().startsWith("n")){
					otherHost.setContactFrequency(otherHost.getContactFrequency()+1);
					System.out.println("Con is up between "+host.toString()+ " "+otherHost.toString() + " "+otherHost.getContactFrequency());
				}
				else if(otherHost.toString().startsWith("CD") && host.toString().startsWith("n")){
					host.setContactFrequency(host.getContactFrequency()+1);
					System.out.println("Con is up between "+host.toString()+ " "+otherHost.toString() + " "+host.getContactFrequency());
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
		
		return tryMessagesToConnections(messages, connections);
	}
	
	@Override
	public void update() {
		super.update();
		//updateContactFrequency();
		//checkAndUpdateRelayExemplars();	
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
	public SmartEpidemicRouter replicate() {
		return new SmartEpidemicRouter(this);
	}

	@Override
	public void moduleValueChanged(String key, Object newValue) {
		// TODO Auto-generated method stub
		this.isRelayExemplar = (Boolean)newValue;
	}
}