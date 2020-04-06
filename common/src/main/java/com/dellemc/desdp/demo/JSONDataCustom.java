package com.dellemc.desdp.demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JSONDataCustom implements Serializable {
    public long timestamp;
    public String playerId;
    public String song;
    public String artist;

    @Override
    public String toString() {
        return "{" +
                "timestamp='" + timestamp + '\'' +
                ", playerId='" + playerId + '\'' +
                ", song='" + song + '\'' +
                ", artist='" + artist + '\'' +
                '}';
    }
}
