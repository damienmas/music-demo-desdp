package com.dellemc.desdp.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravega.client.stream.StreamCut;
import io.pravega.connectors.flink.FlinkPravegaReader;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.connectors.elasticsearch.ElasticsearchSinkFunction;
import org.apache.flink.streaming.connectors.elasticsearch.RequestIndexer;
import org.apache.flink.streaming.connectors.elasticsearch6.ElasticsearchSink;
import org.apache.flink.util.Collector;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class SongPlayReaderJobNew extends AbstractJob {
    private static Logger log = LoggerFactory.getLogger(SongPlayReaderJobNew.class);

    /**
     * The entry point for Flink applications.
     *
     * @param args Command line arguments
     */
    public static void main(String... args) {
        MusicReaderAppConfiguration config = new MusicReaderAppConfiguration(args);
        log.info("config: {}", config);
        SongPlayReaderJobNew job = new SongPlayReaderJobNew(config);
        job.run();
    }

    public SongPlayReaderJobNew(MusicReaderAppConfiguration config) {
        super(config);
    }

    @Override
    public MusicReaderAppConfiguration getConfig() {
        return (MusicReaderAppConfiguration) super.getConfig();
    }

    public void run() {
        try {
            final String jobName = SongPlayReaderJobNew.class.getName();
            StreamExecutionEnvironment env = initializeFlinkStreaming();
            env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
            setupElasticSearch();
            createStream(getConfig().getInputStreamConfig());
            //createStream(getConfig().getOutputStreamConfig());

            StreamCut startStreamCut = StreamCut.UNBOUNDED;
            if (getConfig().isStartAtTail()) {
                startStreamCut = getStreamInfo(getConfig().getInputStreamConfig().getStream()).getTailStreamCut();
            }

            // Read Stream of text from Pravega.
            FlinkPravegaReader<MusicDemo> flinkPravegaReader = FlinkPravegaReader.<String>builder()
                    .withPravegaConfig(getConfig().getPravegaConfig())
                    .forStream(getConfig().getInputStreamConfig().getStream(), startStreamCut, StreamCut.UNBOUNDED)
                    .withDeserializationSchema(new JsonDeserializationSchema(MusicDemo.class))
                    .build();
            // No Transformation, simply read the Stream
            DataStream<Tuple3<Long, String, Integer>> events = env
                    .addSource(flinkPravegaReader)
                    .name("events").assignTimestampsAndWatermarks(new AscendingTimestampExtractor<MusicDemo>() {
                        private long currentMaxTimestamp;
                        @Override
                        public long extractAscendingTimestamp(MusicDemo element) {
                            currentMaxTimestamp = element.timestamp;
                            return element.timestamp;
                        }

                        @Nullable
                        @Override
                        public Watermark getCurrentWatermark() {
                            // return the watermark as current highest timestamp minus the out-of-orderness bound
                            return new Watermark(currentMaxTimestamp);
                        }
                    })
                    .map(new RowSplitter())
                    .flatMap(new ArtistCount())
                    .keyBy(1)
                    .sum(2);



 /*           DataStream<Tuple3<Long, String, Integer>> events = env
            //DataStream<Tuple2<String, Integer>> events = env
                    .addSource(flinkPravegaReader)
                    .name(getConfig().getInputStreamConfig().getStream().toString())
                    .map(new RowSplitter())
                    .flatMap(new ArtistCount())
                    .keyBy(1)
                    .sum(2);
*/
            //events.printToErr();
            events.print();

            //ElasticsearchSink<Tuple3<Long, String, Integer>> elasticSink = newElasticSearchSink();
            //events.addSink(elasticSink).name("Write to ElasticSearch");

            log.info("Executing {} job", jobName);
            env.execute(jobName);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class RowSplitter implements
            MapFunction<MusicDemo, Tuple4<Long, String, String, String>> {

        //private transient JSONDataCustom obj;

        public Tuple4<Long, String, String, String> map(MusicDemo row)
                throws Exception {

            //if (obj == null) {
            //   JSONDataCustom obj = new ObjectMapper().readValue(row, JSONDataCustom.class);
            //}
            return new Tuple4<Long, String, String, String>(
                    row.timestamp,
                    row.playerId.toString(),
                    row.song,
                    row.artist
            );
        }
    }

    public static class ArtistCount implements FlatMapFunction<Tuple4<Long, String, String, String>, Tuple3<Long, String, Integer>> {
         public void flatMap(Tuple4<Long, String, String, String> list, Collector<Tuple3<Long, String, Integer>> out)
                throws Exception {
           out.collect(new Tuple3<>(list.f0, list.f3, 1));
        }
    }

/*    public static class Result implements FlatMapFunction<Tuple2<String, Integer>, String> {
        public void flatMap(Tuple2<String, Integer> list, Collector<String> str)
                throws Exception {
            str.collect(new String("{\"artist\": \"" + list.f0.toString() + "\", \"count\": \"" + list.f1 + "\"}"));
        }
    }*/


    @Override
    protected ElasticsearchSinkFunction getResultSinkFunction() {
        String index = getConfig().getElasticSearch().getIndex();
        String type = getConfig().getElasticSearch().getType();

        return new ResultSinkFunction(index, type);
    }

    public static class ResultSinkFunction implements ElasticsearchSinkFunction<Tuple3<Long, String, Integer>> {
        private final String index;
        private final String type;

        public ResultSinkFunction(String index, String type){
            this.index = index;
            this.type = type;
        }

        @Override
        public void process(Tuple3<Long, String, Integer> event, RuntimeContext ctx, RequestIndexer indexer) {
            indexer.add(createIndexRequest(event));
        }

        private IndexRequest createIndexRequest(Tuple3<Long, String, Integer> event) {
            try {
                Map json = new HashMap();
                json.put("Timestamp", event.f0.toString());
                json.put("Artist", event.f1);
                json.put("Count", event.f2.toString());

                return Requests.indexRequest()
                        .index(index)
                        .type(type)
                        .source(json, XContentType.JSON);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
