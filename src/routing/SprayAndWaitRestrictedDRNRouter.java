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
public class SprayAndWaitRestrictedDRNRouter extends SprayAndWaitRouter {
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public SprayAndWaitRestrictedDRNRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected SprayAndWaitRestrictedDRNRouter(SprayAndWaitRestrictedDRNRouter r) {
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
					if(	(sHost.startsWith("CD") && sOtherHost.startsWith("DB"))||
						(sHost.startsWith("n") && sOtherHost.startsWith("CD"))||
						(sHost.startsWith("n") && sOtherHost.startsWith("n"))){
						//System.out.println("The message " + m.toString() +" is sent from "+sHost+" to "+sOtherHost);
						canMsgBeSent =true;
					
				}
			}
			//from relief center to DTN nodes
			if(m.toString().startsWith("N")){
				if(	(sHost.startsWith("DB") && sOtherHost.startsWith("CD"))||
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
		//to use energyAwareRouter, activate reduceSendingAndScanningEnergy in SprayAndWaitRouter
		//reduceSendingAndScanningEnergy();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		/* create a list of SAWMessages that have copies left to distribute */
		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());
		
		if (copiesLeft.size() > 0) {
			/* try to send those messages */
			this.tryMessagesToConnections(copiesLeft, getConnections());
		}
	}
	
	
	@Override
	public SprayAndWaitRestrictedDRNRouter replicate() {
		return new SprayAndWaitRestrictedDRNRouter(this);
	}
}