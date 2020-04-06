package com.dellemc.desdp.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravega.client.stream.StreamCut;
import io.pravega.connectors.flink.FlinkPravegaReader;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.elasticsearch.ElasticsearchSinkFunction;
import org.apache.flink.streaming.connectors.elasticsearch.RequestIndexer;
import org.apache.flink.streaming.connectors.elasticsearch6.ElasticsearchSink;
import org.apache.flink.util.Collector;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class SongPlayReaderJobSimple extends AbstractJob {
    private static Logger log = LoggerFactory.getLogger(SongPlayReaderJobSimple.class);

    /**
     * The entry point for Flink applications.
     *
     * @param args Command line arguments
     */
    public static void main(String... args) {
        MusicReaderAppConfiguration config = new MusicReaderAppConfiguration(args);
        log.info("config: {}", config);
        SongPlayReaderJobSimple job = new SongPlayReaderJobSimple(config);
        job.run();
    }

    public SongPlayReaderJobSimple(MusicReaderAppConfiguration config) {
        super(config);
    }

    @Override
    public MusicReaderAppConfiguration getConfig() {
        return (MusicReaderAppConfiguration) super.getConfig();
    }

    public void run() {
        try {
            final String jobName = SongPlayReaderJobSimple.class.getName();
            StreamExecutionEnvironment env = initializeFlinkStreaming();
            //setupElasticSearch();
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

            // No Transformation, simply read the Stream
            DataStream<String> events = env
                    .addSource(flinkPravegaReader)
                    .name("events");

            //events.printToErr();
            events.print();

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
            return new Tuple4<Long, String, String, String>(
                    obj.timestamp,
                    obj.playerId,
                    obj.song,
                    obj.artist
            );
        }
    }

    public static class ArtistCount implements FlatMapFunction<Tuple4<Long, String, String, String>, Tuple3<Long, String, Integer>> {
    //public static class ArtistCount implements FlatMapFunction<Tuple4<Long, String, String, String>, Tuple2<String, Integer>> {
        //FlatMapFunction<Tuple3<String, String, String>, String> {

        public void flatMap(Tuple4<Long, String, String, String> list, Collector<Tuple3<Long, String, Integer>> out)
        //public void flatMap(Tuple4<Long, String, String, String> list, Collector<Tuple2<String, Integer>> out)
        //public void flatMap(Tuple3<String, String, String> list, Collector<String> out)
                throws Exception {

            out.collect(new Tuple3<Long, String, Integer>(list.f0, list.f3, 1));
            //out.collect(new Tuple2<String, Integer>(list.f3, 1));
          //  out.collect(new String (list.f2.toString() + ", 1"));

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
    //public static class ResultSinkFunction implements ElasticsearchSinkFunction<Tuple2<String, Integer>> {
        private final String index;
        private final String type;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public ResultSinkFunction(String index, String type){
            this.index = index;
            this.type = type;
        }

        @Override
        public void process(Tuple3<Long, String, Integer> event, RuntimeContext ctx, RequestIndexer indexer) {
        //public void process(Tuple2<String, Integer> event, RuntimeContext ctx, RequestIndexer indexer) {
            indexer.add(createIndexRequest(event));
        }

        private IndexRequest createIndexRequest(Tuple3<Long, String, Integer> event) {
        //private IndexRequest createIndexRequest(Tuple2<String, Integer> event) {
            try {

                Map json = new HashMap();
                json.put("Timestamp", event.f0.toString());
                json.put("Artist", event.f1);
                json.put("Count", event.f2.toString());

                /*return Requests.indexRequest()
                        .index(index)
                        .type(type)
                        .source(json, XContentType.JSON);*/
                //byte[] json = objectMapper.writeValueAsBytes(event);
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
