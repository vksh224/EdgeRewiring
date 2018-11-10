/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;
import java.util.List;

import core.*;

/**
 * Energy level-aware variant of Epidemic router.
 */
public class GrnRouter extends ActiveRouter 
		implements ModuleCommunicationListener{
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public GrnRouter(Settings s) {
		super(s);
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected GrnRouter(GrnRouter r) {
		super(r);
	}
	
	@Override
	public void update() {
		super.update();
		updateNeighborList();
		reduceSendingAndScanningEnergy();
		
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		this.tryAllMessagesToAllConnections();
	}
		
	@Override
	public GrnRouter replicate() {
		return new GrnRouter(this);
	}

}