import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.UniformReservoir;
import com.google.common.base.Suppliers;
import org.apache.samza.application.StreamApplication;
import org.apache.samza.config.Config;
import org.apache.samza.operators.KV;
import org.apache.samza.operators.MessageStream;
import org.apache.samza.operators.StreamGraph;
import org.apache.samza.serializers.KVSerde;
import org.apache.samza.serializers.StringSerde;
import org.coursera.metrics.datadog.DatadogReporter;
import org.coursera.metrics.datadog.transport.HttpTransport;
import org.ezstack.ezapp.client.EZappClientFactory;
import org.ezstack.ezapp.datastore.api.Rule;
import org.ezstack.ezapp.datastore.api.RulesManager;
import org.ezstack.ezapp.querybus.api.QueryMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class DenormalizationDeityApp implements StreamApplication {
    private static final Logger LOG = LoggerFactory.getLogger(DenormalizationDeityApp.class);

    private final MetricRegistry _metrics;
    private final MetricRegistry.MetricSupplier<Histogram> _histogramSupplier;
    private Map<String, QueryObject> _priorityObjects;
    private final Date _timestamp = new Date();
    private DeityConfig _config;
    private RulesManager _rulesManager;
    private Supplier<Set<Rule>> _ruleSupplier;
    private AtomicIntManager _intManager;

    public DenormalizationDeityApp() {
        _metrics = new MetricRegistry();
        _histogramSupplier = () -> new Histogram(new UniformReservoir());
        _priorityObjects = new HashMap<>();
        AtomicInteger runningQueryCount = new AtomicInteger(0);
        _intManager = new AtomicIntManager(runningQueryCount);
    }

    @Override
    public void init(StreamGraph streamGraph, Config config) {
        _config = new DeityConfig(config);
        _rulesManager = EZappClientFactory.newRulesManager(_config.getUriAddress());
        _ruleSupplier = Suppliers.memoizeWithExpiration(_rulesManager::getRules, _config.getCachePeriod(), TimeUnit.SECONDS);
        RuleCreationService timer = new RuleCreationService(_config, _metrics, _histogramSupplier, _priorityObjects, _rulesManager, _ruleSupplier, _intManager);
        timer.startAsync();

        MessageStream<QueryMetadata> queryStream = streamGraph.getInputStream("queries", new JsonSerdeV3<>(QueryMetadata.class));

        MessageStream<KV<String, QueryMetadata>> partionedQueryMetadata =
                queryStream.partitionBy(queryMetadata -> queryMetadata.hash(),
                        queryMetadata -> queryMetadata,
                        KVSerde.of(new StringSerde(), new JsonSerdeV3<>(QueryMetadata.class)),
                        "partition-query-metadata");

        queryStream.map(new QueryMetadataProcessor(_metrics, _histogramSupplier, _priorityObjects, _intManager));

        HttpTransport transport = new HttpTransport.Builder().withApiKey(_config.getDatadogKey()).build();
        DatadogReporter reporter = DatadogReporter.forRegistry(_metrics).withTransport(transport).withExpansions(DatadogReporter.Expansion.ALL).build();

        reporter.start(10, TimeUnit.SECONDS);
    }
}