/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.Tuple;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class MaxPropRestrictedDRNRouter extends MaxPropRouter {
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public MaxPropRestrictedDRNRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected MaxPropRestrictedDRNRouter(MaxPropRestrictedDRNRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
	}
	
	/**
	 * Specific to disaster response network
	 */
	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * hop counts and their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	protected Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts that are not transferring at the moment,
		 * collect all the messages that could be sent */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			MaxPropRouter othRouter = (MaxPropRouter)other.getRouter();
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			for (Message m : msgCollection) {
				boolean canMsgBeSent = shouldMessageBeSent(m,con);
				if(canMsgBeSent){
					/* skip messages that the other host has or that have
					 * passed the other host */
					if (othRouter.hasMessage(m.getId()) ||
							m.getHops().contains(other)) {
						continue; 
					}
					messages.add(new Tuple<Message, Connection>(m,con));
				}
				
			}			
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		/* sort the message-connection tuples according to the criteria
		 * defined in MaxPropTupleComparator */ 
		//Collections.sort(messages, new MaxPropTupleComparator(calcThreshold()));
		return tryMessagesForConnected(messages);	
	}
	protected boolean shouldMessageBeSent(Message m, Connection con) {
		boolean canMsgBeSent= false;
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
					(sHost.startsWith("n") && sOtherHost.startsWith("CD"))||
					(sHost.startsWith("n") && sOtherHost.startsWith("n"))){
					//System.out.println("The message " + m.toString() +" is sent from "+sHost+" to "+sOtherHost);
					canMsgBeSent =true;
				
			}
		}
		//from relief center to DTN nodes
		if(m.toString().startsWith("N")){
			if(	(sHost.startsWith("CS") && sOtherHost.startsWith("CD"))||
					(sHost.startsWith("CD")	&&  sOtherHost.startsWith("n"))||
					(sHost.startsWith("n") && sOtherHost.startsWith("n"))){
					//System.out.println("The message " + m.toString() +" is sent from "+sHost+" to "+sOtherHost);
					canMsgBeSent =true;
					}
		}
	return canMsgBeSent;	
}

	@Override
	public void update() {
		super.update();
		//reduceSendingAndScanningEnergy();
		if (!canStartTransfer() ||isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
		
		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		tryOtherMessages();	
	}
	
	
	@Override
	public MaxPropRestrictedDRNRouter replicate() {
		return new MaxPropRestrictedDRNRouter(this);
	}

}