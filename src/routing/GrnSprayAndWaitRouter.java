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


public class GrnSprayAndWaitRouter extends SprayAndWaitRouter {
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public GrnSprayAndWaitRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected GrnSprayAndWaitRouter(GrnSprayAndWaitRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
	}
	
	protected Connection tryMessagesToConnections(List<Message> messages,
			List<Connection> connections) {
		for (int i=0, n=connections.size(); i<n; i++) {
			Connection con = connections.get(i);
			
			boolean canMsgBeSent = shouldMessageBeSent(con);
			
			if(!canMsgBeSent)
				continue;
			
			Message started = tryAllMessages(con, messages); 
			if (started != null) { 
				return con;
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
		
		if(host.getNeighborList()!= null && host.getNeighborList().contains(sOtherHost)){
			canMsgBeSent = true;
		}

		else
			canMsgBeSent = false;
		
	return canMsgBeSent;	
}
	
	
	
	@Override
	public void update() {
		super.update();
		updateNeighborList();
		//to use energyAwareRouter, activate reduceSendingAndScanningEnergy in SprayAndWaitRouter
		reduceSendingAndScanningEnergy();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}

		/* try messages that could be delivered to final recipient */
//		if (exchangeDeliverableMessages() != null) {
//			return;
//		}
		
		/* create a list of SAWMessages that have copies left to distribute */
		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());
		
		if (copiesLeft.size() > 0) {
			/* try to send those messages */
			this.tryMessagesToConnections(copiesLeft, getConnections());
		}
	}
	
	@Override
	public GrnSprayAndWaitRouter replicate() {
		return new GrnSprayAndWaitRouter(this);
	}
}