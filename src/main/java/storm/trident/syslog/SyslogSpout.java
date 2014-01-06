package storm.trident.syslog;

import backtype.storm.Config;
import backtype.storm.task.TopologyContext;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.productivity.java.syslog4j.server.*;
import storm.trident.operation.TridentCollector;
import storm.trident.spout.IBatchSpout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

public class SyslogSpout implements IBatchSpout, SyslogServerEventHandlerIF {
	private static final long serialVersionUID = -1326400481240203319L;

    public static final int DEFAULT_QUEUE_SIZE = 1000;
    public static final int DEFAULT_BATCH_SIZE = 10;

	private String protocol;
    private int port;
    private int queueSize;
    private int batchSize;

	private transient ArrayBlockingQueue<String> syslog;
    private transient HashMap<Long, List<String>> emittedButNonAcked;
	private transient SyslogServerIF server;

	public SyslogSpout(String protocol, int port) {
		this(protocol, port, DEFAULT_QUEUE_SIZE, DEFAULT_BATCH_SIZE);
    }

    public SyslogSpout(String protocol, int port, int queueSize, int batchSize) {
        this.protocol = protocol;
        this.port = port;
        this.queueSize = queueSize;
        this.batchSize = batchSize;
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
        syslog = new ArrayBlockingQueue<String>(queueSize);
        emittedButNonAcked = Maps.newHashMapWithExpectedSize(queueSize);
        server = SyslogServer.getThreadedInstance(protocol.toLowerCase());
		SyslogServerConfigIF config = server.getConfig();
		config.setPort(port);
		config.addEventHandler(this);
	}

    @Override
	public void emitBatch(long batchId, TridentCollector collector) {
        if (emittedButNonAcked.containsKey(batchId)) {
            // re-emitting batch
            for (String message : emittedButNonAcked.get(batchId)) {
                collector.emit(new Values(message));
            }
        } else {
            List<String> batch = Lists.newArrayListWithCapacity(batchSize);
            emittedButNonAcked.put(batchId, batch);

            boolean interrupted = false;
            int leftToEmit = batchSize;

            do {
                try {
                    while (leftToEmit > 0) {
                        String message = syslog.take();
                        batch.add(message);
                        collector.emit(new Values(message));
                        leftToEmit--;
                    }
                    interrupted = false;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            } while (interrupted); // retry loop
        }
	}

	@Override
	public void ack(long batchId) {
        emittedButNonAcked.remove(batchId);
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
