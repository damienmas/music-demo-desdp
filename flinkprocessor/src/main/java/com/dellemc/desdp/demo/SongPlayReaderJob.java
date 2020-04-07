package com.dellemc.desdp.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravega.client.stream.StreamCut;
import io.pravega.connectors.flink.FlinkPravegaReader;
import jdk.nashorn.internal.parser.JSONParser;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.elasticsearch.ElasticsearchSinkFunction;
import org.apache.flink.streaming.connectors.elasticsearch.RequestIndexer;
import org.apache.flink.streaming.connectors.elasticsearch6.ElasticsearchSink;
import org.apache.flink.util.Collector;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.util.parsing.json.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class SongPlayReaderJob extends AbstractJob {
    private static Logger log = LoggerFactory.getLogger(SongPlayReaderJob.class);

    /**
     * The entry point for Flink applications.
     *
     * @param args Command line arguments
     */
    public static void main(String... args) {
        MusicReaderAppConfiguration config = new MusicReaderAppConfiguration(args);
        log.info("config: {}", config);
        SongPlayReaderJob job = new SongPlayReaderJob(config);
        job.run();
    }

    public SongPlayReaderJob(MusicReaderAppConfiguration config) {
        super(config);
    }

    @Override
    public MusicReaderAppConfiguration getConfig() {
        return (MusicReaderAppConfiguration) super.getConfig();
    }

    public void run() {
        try {
            final String jobName = SongPlayReaderJob.class.getName();
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
            FlinkPravegaReader<String> flinkPravegaReader = FlinkPravegaReader.<String>builder()
                    .withPravegaConfig(getConfig().getPravegaConfig())
                    .forStream(getConfig().getInputStreamConfig().getStream(), startStreamCut, StreamCut.UNBOUNDED)
                    .withDeserializationSchema(new UTF8StringDeserializationSchema())
                    .build();
//            // No Transformation, simply read the Stream
//            DataStream<String> events = env
//                    .addSource(flinkPravegaReader)
//                    .name("events");

            DataStream<Tuple3<Long, String, Integer>> events = env
            //DataStream<Tuple2<String, Integer>> events = env
                    .addSource(flinkPravegaReader)
                    .name(getConfig().getInputStreamConfig().getStream().toString())
                    .map(new RowSplitter())
                    .flatMap(new ArtistCount())
                    .keyBy(1)
                    .reduce(new ReduceFunction<Tuple3<Long, String,Integer>>() {
                        @Override
                        public Tuple3<Long, String, Integer> reduce(Tuple3<Long, String, Integer> value1, Tuple3<Long, String, Integer> value2) throws Exception {
                            return new Tuple3<Long, String, Integer>(value2.f0, value2.f1, value1.f2 + value2.f2);
                        }
                    });
                    //.sum(2);

            //events.printToErr();
            events.print();

            ElasticsearchSink<Tuple3<Long, String, Integer>> elasticSink = newElasticSearchSink();
            events.addSink(elasticSink).name("Write to ElasticSearch");

            log.info("Executing {} job", jobName);
            env.execute(jobName);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class RowSplitter implements
            MapFunction<String, Tuple4<Long, String, String, String>> {

        //private transient JSONDataCustom obj;

        public Tuple4<Long, String, String, String> map(String row)
                throws Exception {

            //if (obj == null) {
               JSONDataCustom obj = new ObjectMapper().readValue(row, JSONDataCustom.class);
            //}
            return new Tuple4<>(
                    obj.timestamp,
                    obj.playerId,
                    obj.song,
                    obj.artist
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
