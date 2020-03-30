package com.dellemc.desdp.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravega.client.stream.StreamCut;
import io.pravega.connectors.flink.FlinkPravegaReader;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

            DataStream<Tuple2<String, Integer>> events = env
                    .addSource(flinkPravegaReader)
                    .name(getConfig().getInputStreamConfig().getStream().toString())
                    .map(new RowSplitter())
                    .flatMap(new ArtistCount())
                    .keyBy(0)
                    .sum(1);

            events.printToErr();

            log.info("Executing {} job", jobName);
            env.execute(jobName);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class RowSplitter implements
            MapFunction<String, Tuple3<String, String, String>> {

        public Tuple3<String, String, String> map(String row)
                throws Exception {

            JSONDataCustom obj = new ObjectMapper().readValue(row, JSONDataCustom.class);

            return new Tuple3<String, String, String>(
                    obj.playerId,
                    obj.song,
                    obj.artist
            );
        }
    }

    public static class ArtistCount implements
            FlatMapFunction<Tuple3<String, String, String>, Tuple2<String, Integer>> {
        //FlatMapFunction<Tuple3<String, String, String>, String> {

        public void flatMap(Tuple3<String, String, String> list, Collector<Tuple2<String, Integer>> out)
                throws Exception {

            out.collect(new Tuple2<String, Integer> (list.f2, 1));
            // out.collect(new String (list.f2.toString() + ", 1"));

        }
    }

    public static class Result implements FlatMapFunction<Tuple2<String, Integer>, String> {
        public void flatMap(Tuple2<String, Integer> list, Collector<String> str)
                throws Exception {
            str.collect(new String("{\"artist\": \""+ list.f0.toString() + "\", \"count\": \"" + list.f1 + "\"}"  ));
        }
    }


}
