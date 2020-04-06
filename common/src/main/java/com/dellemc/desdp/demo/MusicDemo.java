package com.dellemc.desdp.demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.flink.api.java.tuple.Tuple2;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MusicDemo implements Serializable {
    public Long timestamp;
    public Integer playerId;
    public String song;
    public String artist;


    @Override
    public String toString() {
        return "{" +
                "\"timestamp\": \"" + timestamp + "\"," +
                "\"playerId\": \"" + playerId + "\"," +
                "\"song\": \"" + song + "\"," +
                "\"artist\": \"" + artist + "\"" +
                "}";

        /*return "{" +
                "timestamp='" + Timestamp + '\'' +
                ", playerId=" + PlayerId +
                ", song=" + Song +
                ", artist=" + Artist +
                '}';*/
    }
}
