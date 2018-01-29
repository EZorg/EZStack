import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.UniformReservoir;
import org.apache.samza.application.StreamApplication;
import org.apache.samza.config.Config;
import org.apache.samza.operators.MessageStream;
import org.apache.samza.operators.StreamGraph;
import org.codehaus.jackson.map.ObjectMapper;
import org.coursera.metrics.datadog.DatadogReporter;
import org.coursera.metrics.datadog.DatadogReporter.Expansion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.coursera.metrics.datadog.transport.HttpTransport;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DenormalizationDeityApp implements StreamApplication {

    private static final Logger log = LoggerFactory.getLogger(DenormalizationDeityApp.class);

    private final MetricRegistry metrics;
    private final ObjectMapper objectMapper;
    private final MetricRegistry.MetricSupplier<Histogram> histogramSupplier;
    private Map<String, QueryObject> priorityObjects;

    public DenormalizationDeityApp() {
        metrics = new MetricRegistry();
        objectMapper = new ObjectMapper();
        histogramSupplier = () -> new Histogram(new UniformReservoir());
        priorityObjects = new HashMap<>();
    }

    @Override
    public void init(StreamGraph streamGraph, Config config) {
        DeityConfig deityConfig = new DeityConfig(config);
        MessageStream<Query> queryStream = streamGraph.<String, Map<String, Object>, Query>getInputStream("queries", this::convertToQuery);
        queryStream.map(this::processQuery);

        HttpTransport transport = new HttpTransport.Builder().withApiKey(deityConfig.getDatadogKey()).build();
        DatadogReporter reporter = DatadogReporter.forRegistry(metrics).withTransport(transport).withExpansions(Expansion.ALL).build();

        reporter.start(10, TimeUnit.SECONDS);
    }

    private Query convertToQuery(String key, Map<String, Object> msg) {
        return objectMapper.convertValue(msg, Query.class);
    }

    private Query processQuery(Query query) {
        String strippedQuery = query.getStrippedQuery();

        Histogram histogram = metrics.histogram(strippedQuery, histogramSupplier);
        histogram.update(query.getResponseTime());

        updateQueryObject(strippedQuery);

        return query;
    }

    private void updateQueryObject(String strippedQuery) { //Priority is represented as the Median, skewed by the mean absolute deviation from the median
        Histogram histogram = metrics.histogram(strippedQuery, histogramSupplier);
        Snapshot snap = histogram.getSnapshot();

        long mean = (long) snap.getMean();
        long median = (long) snap.getMedian();

        long[] values = snap.getValues();
        long meanAbsoluteDeviation = 0;

        long priority;

        for (int i = 0; i < values.length; i++) {
            meanAbsoluteDeviation += Math.abs(values[i]-median);
        }
        meanAbsoluteDeviation = meanAbsoluteDeviation/mean;

        if(median <= mean) {
            priority = median - meanAbsoluteDeviation;
        }
        else {
            priority = median + meanAbsoluteDeviation;
        }

        Date timestamp = new Date();
        long stamp = timestamp.getTime();

        QueryObject queryObject;

        if (priorityObjects.containsKey(strippedQuery)) {
            queryObject = priorityObjects.get(strippedQuery);
            queryObject.setRecentTimestamp(stamp);
            queryObject.setPriority(priority);
        }
        else {
            queryObject = new QueryObject(strippedQuery, priority, stamp);
        }

        priorityObjects.put(strippedQuery, queryObject);
    }
}