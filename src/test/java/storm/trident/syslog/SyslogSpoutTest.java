package storm.trident.syslog;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.junit.Test;
import org.productivity.java.syslog4j.Syslog;
import storm.trident.operation.TridentCollector;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SyslogSpoutTest {

    public static final String SYSLOG_HOST = "localhost";
    public static final int SYSLOG_PORT = 50514;
    public static final String UDP_PROTOCOL = "udp";

    static {
        Syslog.getInstance(UDP_PROTOCOL).getConfig().setHost(SYSLOG_HOST);
        Syslog.getInstance(UDP_PROTOCOL).getConfig().setPort(SYSLOG_PORT);
    }

    @Test
    public void shouldOutputBatch() throws Exception {
        // given
        SyslogSpout syslogSpout = new SyslogSpout(UDP_PROTOCOL, 50514);
        syslogSpout.open(Maps.newHashMap(), null);

        generateNmessagesWithPattern("message%02d", SyslogSpout.DEFAULT_BATCH_SIZE);

        // when
        TestTridentCollector collector = new TestTridentCollector();
        syslogSpout.emitBatch(1, collector);

        // then
        List<List<Object>> emitted = collector.getEmitted();
        assertNmessagesWithPattern(emitted, ".* message%02d$", SyslogSpout.DEFAULT_BATCH_SIZE);
    }

    @Test
    public void shouldReemitBatch() throws Exception {
        // given
        SyslogSpout syslogSpout = new SyslogSpout(UDP_PROTOCOL, 50514);
        syslogSpout.open(Maps.newHashMap(), null);

        generateNmessagesWithPattern("message%02d", SyslogSpout.DEFAULT_BATCH_SIZE);

        // when & then
        TestTridentCollector collector = new TestTridentCollector();
        syslogSpout.emitBatch(1, collector);
        List<List<Object>> emitted = collector.getEmitted();
        assertNmessagesWithPattern(emitted, ".* message%02d$", SyslogSpout.DEFAULT_BATCH_SIZE);

        TestTridentCollector collector2 = new TestTridentCollector();
        syslogSpout.emitBatch(1, collector2);

        List<List<Object>> emitted2 = collector2.getEmitted();
        assertNmessagesWithPattern(emitted2, ".* message%02d$", SyslogSpout.DEFAULT_BATCH_SIZE);
    }

    private void generateNmessagesWithPattern(String pattern, int n) {
        for (int i = 0; i < n; i++) {
            Syslog.getInstance(UDP_PROTOCOL).info(String.format(pattern, i));
        }
    }

    private void assertNmessagesWithPattern(List<List<Object>> emitted, String pattern, int n) {
        assertEquals(10, emitted.size());
        for (int i = 0; i < n; i++) {
            String message = (String) emitted.get(i).get(0);
            assertTrue(message.matches(String.format(pattern, i)));
        }
    }

    static class TestTridentCollector implements TridentCollector {

        private List<List<Object>> emitted = Lists.newArrayList();

        @Override
        public void emit(List<Object> objects) {
            emitted.add(objects);
        }

        public List<List<Object>> getEmitted() {
            return emitted;
        }

        @Override
        public void reportError(Throwable throwable) {

        }
    }
}
