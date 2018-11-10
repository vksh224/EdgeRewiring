/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.List;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class EnergyAwareRestrictedDRNRouter extends EnergyAwareRouter {
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public EnergyAwareRestrictedDRNRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EnergyAwareRestrictedDRNRouter(EnergyAwareRestrictedDRNRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
	}
	
	 /**
	  * Goes trough the messages until the other node accepts one
	  * for receiving (or doesn't accept any). If a transfer is started, the
	  * connection is included in the list of sending connections.
	  * @param con Connection trough which the messages are sent
	  * @param messages A list of messages to try
	  * @return The message whose transfer was started or null if no 
	  * transfer was started. 
	  */
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
					if(	(sHost.startsWith("CD") && sOtherHost.startsWith("CS"))||
							(sHost.startsWith("n") && sOtherHost.startsWith("ADB"))||
						(sHost.startsWith("ADB") && sOtherHost.startsWith("CD"))||
						(sHost.startsWith("n") && sOtherHost.startsWith("n"))){
						//System.out.println("The message " + m.toString() +" is sent from "+sHost+" to "+sOtherHost);
						canMsgBeSent =true;
					
				}
			}
			//from relief center to DTN nodes
			if(m.toString().startsWith("N")){
				if(	(sHost.startsWith("CS") && sOtherHost.startsWith("ADB"))||
						(sHost.startsWith("ADB") && sOtherHost.startsWith("CD"))||
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
		
		// then try any/all message to any/all connection
		this.tryAllMessagesToAllConnections();
	}
	
	
	@Override
	public EnergyAwareRestrictedDRNRouter replicate() {
		return new EnergyAwareRestrictedDRNRouter(this);
	}

}