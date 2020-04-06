package com.dellemc.desdp.demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.flink.api.java.tuple.Tuple2;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TopArtist implements Serializable {
    Tuple2<String, Integer> artist;
    //public String Artist;
    //public Integer Count;

    @Override
    public String toString() {
        return "FlatMetricReport{" +
                "Artist='" + artist.f0 + '\'' +
                ", Count=" + artist.f1 +
                '}';
    }
}
