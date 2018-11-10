/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;



//import routing.ProphetRouter.TupleComparator;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.Tuple;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class GrnProphetRouter extends ProphetRouter {
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public GrnProphetRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected GrnProphetRouter(GrnProphetRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
	}
	

	/**
	 * Specific to disaster response network
	 */
	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	protected Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			GrnProphetRouter othRouter = (GrnProphetRouter)other.getRouter();
			
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			boolean canMsgBeSent = shouldMessageBeSent(con);
			
			if(canMsgBeSent == false)
				continue;
			
			for (Message m : msgCollection) {
				
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				if (othRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
					
					// the other node has higher probability of delivery
					messages.add(new Tuple<Message, Connection>(m,con));
				}			
			}			
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		else{
			// sort the message-connection tuples
			Collections.sort(messages, new TupleComparator());
			return tryMessagesForConnected(messages);	// try to send messages
		}
		
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

		else{
//			if(host.toString().contains("n66") || sOtherHost.contains("n66")){
//				System.out.println("Connection: " + host +" " + sOtherHost);
//			}
			canMsgBeSent = false;
		}
			
		
	return canMsgBeSent;	
}

	@Override
	public void update() {
		super.update();
		updateNeighborList();
		reduceSendingAndScanningEnergy();
		if (!canStartTransfer() ||isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
		
		// try messages that could be delivered to final recipient
//		if (exchangeDeliverableMessages() != null) {
//			return;
//		}
		
		tryOtherMessages();		
	}

	
	@Override
	public GrnProphetRouter replicate() {
		return new GrnProphetRouter(this);
	}

}