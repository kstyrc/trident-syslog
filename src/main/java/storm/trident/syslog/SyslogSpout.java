package storm.trident.syslog;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.productivity.java.syslog4j.server.SyslogServer;
import org.productivity.java.syslog4j.server.SyslogServerConfigIF;
import org.productivity.java.syslog4j.server.SyslogServerEventHandlerIF;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.productivity.java.syslog4j.server.SyslogServerIF;

import storm.trident.operation.TridentCollector;
import storm.trident.spout.IBatchSpout;
import backtype.storm.Config;
import backtype.storm.task.TopologyContext;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;

public class SyslogSpout implements IBatchSpout, SyslogServerEventHandlerIF {
	private static final long serialVersionUID = -1326400481240203319L;

	public static final int BATCH_SIZE = 10;
	
	private String protocol;
	private int port;
	
	private transient BlockingQueue<String> syslog;
	private transient SyslogServerIF server;

	public SyslogSpout(String protocol, int port) {
		this.protocol = protocol;
		this.port = port;
	}
	
	@Override
	public void event(SyslogServerIF server, SyslogServerEventIF event) {
		boolean interrupted = false;
		do {
			if (!syslog.offer(event.getMessage())) {
				try {
					syslog.take();
					syslog.offer(event.getMessage());
					interrupted = false;
					
				} catch (InterruptedException e) {
					// shouldnt happen, we take() if the queue full, so no waiting
					interrupted = true;
				}
			}
		} while (interrupted); // retry loop
	}
	
	@Override
	public void open(@SuppressWarnings("rawtypes") Map conf, TopologyContext context) {
		syslog = new ArrayBlockingQueue<String>(100000);
		server = SyslogServer.getThreadedInstance(protocol.toLowerCase());
		SyslogServerConfigIF config = server.getConfig();
		config.setPort(port);
		config.addEventHandler(this);
	}

	@Override
	public void emitBatch(long batchId, TridentCollector collector) {
		boolean interrupted = false;
		do {
			try {
				for (int i = 0; i < BATCH_SIZE; i++) {
					String message = syslog.take();
					collector.emit(new Values(message));
				}
				interrupted = false;
			} catch (InterruptedException e) {
				interrupted = true;
			}
		} while (interrupted); // retry loop
	}

	@Override
	public void ack(long batchId) {
	}

	@Override
	public void close() {
		// This is deprecated and dirty, but boolean shutdown member in server.shutdown()
		// is not declared as volatile or AtomicBoolean, possible forever-thread.
		// Possible solution: reimplement SyslogServer run() method.
		server.getThread().stop();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getComponentConfiguration() {
		Config conf = new Config();
        conf.setMaxTaskParallelism(1);
        return conf;
	}

	@Override
	public Fields getOutputFields() {
		return new Fields("message");
	}
}
