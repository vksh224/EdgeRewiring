/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.SimClock;

/**
 * Report for generating different kind of total statistics about message
 * relaying performance. Messages that were created during the warm up period
 * are ignored.
 * <P><strong>Note:</strong> if some statistics could not be created (e.g.
 * overhead ratio if no messages were delivered) "NaN" is reported for
 * double values and zero for integer median(s).
 */
public class DeliveryProbReport extends Report implements MessageListener {
	private Map<String, Double> creationTimes;
	private List<Double> latencies;
	private List<Integer> hopCounts;
	private List<Double> msgBufferTime;
	private List<Double> rtt; // round trip times
	
	private int nrofDropped;
	private int nrofRemoved;
	private int nrofStarted;
	private int nrofAborted;
	private int nrofRelayed;
	private int nrofCreated;
	private int nrofResponseReqCreated;
	private int nrofResponseDelivered;
	private int nrofDelivered;
	public int printtime = 0;
	public int prev_created = 0;
	public int prev_delivered = 0;
	
	/**
	 * Constructor.
	 */
	public DeliveryProbReport() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		this.creationTimes = new HashMap<String, Double>();
		this.latencies = new ArrayList<Double>();
		this.msgBufferTime = new ArrayList<Double>();
		this.hopCounts = new ArrayList<Integer>();
		this.rtt = new ArrayList<Double>();
		
		this.nrofDropped = 0;
		this.nrofRemoved = 0;
		this.nrofStarted = 0;
		this.nrofAborted = 0;
		this.nrofRelayed = 0;
		this.nrofCreated = 0;
		this.nrofResponseReqCreated = 0;
		this.nrofResponseDelivered = 0;
		this.nrofDelivered = 0;
	}

	
	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
		
	}

	
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
		
	}

	
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
			boolean finalTarget) {
		if (isWarmupID(m.getId())) {
			return;
		}

		this.nrofRelayed++;
		if (finalTarget) {
			this.latencies.add(getSimTime() - 
				this.creationTimes.get(m.getId()) );
			this.nrofDelivered++;
			this.hopCounts.add(m.getHops().size() - 1);
			
			if (m.isResponse()) {
				this.rtt.add(getSimTime() -	m.getRequest().getCreationTime());
				this.nrofResponseDelivered++;
			}
		}
	}


	public void newMessage(Message m) {
		//SimClock time = new SimClock();
		int simtime = (int)getSimTime();
		double cum_del_prob = (1.0 * this.nrofDelivered)/this.nrofCreated;
		if(simtime - this.printtime > 1000){
			double inst_del_prob = (1.0 *(this.nrofDelivered -this.prev_delivered))/(this.nrofCreated - this.prev_created);
			write(simtime + "\t" + (1*(this.nrofCreated - this.prev_created)) + "\t" + (1*(this.nrofDelivered -this.prev_delivered)) + "\t" + cum_del_prob + "\t" + inst_del_prob);
			printtime = simtime;
			this.prev_created = this.nrofCreated;
			this.prev_delivered = this.nrofDelivered;
		}
		if (isWarmup()) {
			addWarmupID(m.getId());
			return;
		}
		
		this.creationTimes.put(m.getId(), getSimTime());
		this.nrofCreated++;
		if (m.getResponseSize() > 0) {
			this.nrofResponseReqCreated++;
		}
	}
	
	
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getId())) {
			return;
		}

		this.nrofStarted++;
	}
	

	@Override
	public void done() {
		write("Message stats for scenario " + getScenarioName() + 
				"\nsim_time: " + format(getSimTime()));
		double deliveryProb = 0; // delivery probability
		double responseProb = 0; // request-response success probability
		double overHead = Double.NaN;	// overhead ratio
		
		if (this.nrofCreated > 0) {
			deliveryProb = (1.0 * this.nrofDelivered) / this.nrofCreated;
		}
		if (this.nrofDelivered > 0) {
			overHead = (1.0 * (this.nrofRelayed - this.nrofDelivered)) /
				this.nrofDelivered;
		}
		if (this.nrofResponseReqCreated > 0) {
			responseProb = (1.0* this.nrofResponseDelivered) / 
				this.nrofResponseReqCreated;
		}
		
		String statsText = "created: " + this.nrofCreated + 
//			"\nstarted: " + this.nrofStarted + 
//			"\nrelayed: " + this.nrofRelayed +
//			"\naborted: " + this.nrofAborted +
//			"\ndropped: " + this.nrofDropped +
//			"\nremoved: " + this.nrofRemoved +
			"\ndelivered: " + this.nrofDelivered +
			"\ndelivery_prob: " + format(deliveryProb);
//			"\nresponse_prob: " + format(responseProb) + 
//			"\noverhead_ratio: " + format(overHead) + 
//			"\nlatency_avg: " + getAverage(this.latencies) +
//			"\nlatency_med: " + getMedian(this.latencies) + 
//			"\nhopcount_avg: " + getIntAverage(this.hopCounts) +
//			"\nhopcount_med: " + getIntMedian(this.hopCounts) + 
//			"\nbuffertime_avg: " + getAverage(this.msgBufferTime) +
//			"\nbuffertime_med: " + getMedian(this.msgBufferTime) +
//			"\nrtt_avg: " + getAverage(this.rtt) +
//			"\nrtt_med: " + getMedian(this.rtt)
//			;
		
		write(statsText);
		super.done();
	}
	
}
