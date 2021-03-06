/*
 * Copyright (c) 2019 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 */
package com.dellemc.desdp.demo;

import io.pravega.client.stream.ScalingPolicy;
import io.pravega.client.stream.Stream;
import io.pravega.connectors.flink.PravegaConfig;
import org.apache.flink.api.java.utils.ParameterTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

import static org.apache.flink.api.common.ExecutionConfig.PARALLELISM_DEFAULT;
import static org.apache.flink.api.common.ExecutionConfig.PARALLELISM_UNKNOWN;

/**
 * A generic configuration class for Flink Pravega applications.
 * This class can be extended for job-specific configuration parameters.
 */
public class AppConfiguration {
    private static Logger log = LoggerFactory.getLogger(AppConfiguration.class);

    private final ParameterTool params;
    private final PravegaConfig pravegaConfig;
    private final StreamConfig inputStreamConfig;
    private final StreamConfig outputStreamConfig;
    private final int parallelism;
    private final int readerParallelism;
    private final long checkpointIntervalMs;
    private final boolean enableCheckpoint;
    private final boolean enableOperatorChaining;
    private final boolean enableRebalance;
    private final boolean startAtTail;
    private final long maxOutOfOrdernessMs;
    private final ElasticSearch elasticSearch = new ElasticSearch();

    public AppConfiguration(String[] args) {
        params = ParameterTool.fromArgs(args);
        log.info("Parameter Tool: {}", getParams().toMap());
        String defaultScope = getParams().get("scope", "examples");
        pravegaConfig = PravegaConfig.fromParams(getParams()).withDefaultScope(defaultScope);
        inputStreamConfig = new StreamConfig(getPravegaConfig(),"input-",  getParams());
        outputStreamConfig = new StreamConfig(getPravegaConfig(),"output-",  getParams());
        parallelism = getParams().getInt("parallelism", PARALLELISM_UNKNOWN);
        readerParallelism = getParams().getInt("readerParallelism", PARALLELISM_DEFAULT);
        checkpointIntervalMs = getParams().getLong("checkpointIntervalMs", 180000);
        enableCheckpoint = getParams().getBoolean("enableCheckpoint", true);
        enableOperatorChaining = getParams().getBoolean("enableOperatorChaining", true);
        enableRebalance = getParams().getBoolean("rebalance", false);
        startAtTail = getParams().getBoolean("startAtTail", false);
        maxOutOfOrdernessMs = getParams().getLong("maxOutOfOrdernessMs", 1000);

        // elastic-sink: Whether to sink the results to Elastic Search or not.
        elasticSearch.setSinkResults(params.getBoolean("elastic-sink", true));

        elasticSearch.setDeleteIndex(params.getBoolean("elastic-delete-index", false));

        // elastic-host: Host of the Elastic instance to sink to.
        elasticSearch.setHost(params.get("elastic-host", "elasticsearch-client.default.svc.cluster.local"));

        // elastic-port: Port of the Elastic instance to sink to.
        elasticSearch.setPort(params.getInt("elasticsearch-client", 9200));

        // elastic-cluster: The name of the Elastic cluster to sink to.
        elasticSearch.setCluster(params.get("elastic-cluster", "elastic"));

        // elastic-index: The name of the Elastic index to sink to.
        elasticSearch.setIndex(params.get("elastic-index", "music-demo"));

        // elastic-type: The name of the type to sink.
        elasticSearch.setType(params.get("elastic-type", "event"));

    }

    @Override
    public String toString() {
        return "AppConfiguration{" +
                "pravegaConfig=" + pravegaConfig +
                ", inputStreamConfig=" + inputStreamConfig +
                ", outputStreamConfig=" + outputStreamConfig +
                ", parallelism=" + parallelism +
                ", readerParallelism=" + readerParallelism +
                ", checkpointIntervalMs=" + checkpointIntervalMs +
                ", enableCheckpoint=" + enableCheckpoint +
                ", enableOperatorChaining=" + enableOperatorChaining +
                ", enableRebalance=" + enableRebalance +
                ", startAtTail=" + startAtTail +
                ", maxOutOfOrdernessMs=" + maxOutOfOrdernessMs +
                '}';
    }

    public ParameterTool getParams() {
        return params;
    }

    public PravegaConfig getPravegaConfig() {
        return pravegaConfig;
    }

    public StreamConfig getInputStreamConfig() {
        return inputStreamConfig;
    }

    public StreamConfig getOutputStreamConfig() {
        return outputStreamConfig;
    }

    public int getParallelism() {
        return parallelism;
    }

    public int getReaderParallelism() {
        return readerParallelism;
    }

    public long getCheckpointIntervalMs() {
        return checkpointIntervalMs;
    }

    public boolean isEnableCheckpoint() {
        return enableCheckpoint;
    }

    public boolean isEnableOperatorChaining() {
        return enableOperatorChaining;
    }

    public boolean isEnableRebalance() {
        return enableRebalance;
    }

    public boolean isStartAtTail() {
        return startAtTail;
    }

    public long getMaxOutOfOrdernessMs() {
        return maxOutOfOrdernessMs;
    }

    public static class StreamConfig {
        private final Stream stream;
        private final int targetRate;
        private final int scaleFactor;
        private final int minNumSegments;

        public StreamConfig(PravegaConfig pravegaConfig, String argPrefix, ParameterTool params) {
            stream = pravegaConfig.resolve(params.get(argPrefix + "stream", "default"));
            targetRate = params.getInt(argPrefix + "targetRate", 10);  // data rate in KB/sec
            scaleFactor = params.getInt(argPrefix + "scaleFactor", 2);
            minNumSegments = params.getInt(argPrefix + "minNumSegments", 1);
        }

        @Override
        public String toString() {
            return "StreamConfig{" +
                    "stream=" + stream +
                    ", targetRate=" + targetRate +
                    ", scaleFactor=" + scaleFactor +
                    ", minNumSegments=" + minNumSegments +
                    '}';
        }

        public Stream getStream() {
            return stream;
        }

        public ScalingPolicy getScalingPolicy() {
            return ScalingPolicy.byEventRate(targetRate, scaleFactor, minNumSegments);
        }
    }

    public static class ElasticSearch implements Serializable {
        private boolean sinkResults;
        private boolean deleteIndex;
        private String host;
        private int port;
        private String cluster;
        private String index;
        private String type;

        public boolean isSinkResults() {
            return sinkResults;
        }

        public void setSinkResults(boolean sinkResults) {
            this.sinkResults = sinkResults;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getCluster() {
            return cluster;
        }

        public void setCluster(String cluster) {
            this.cluster = cluster;
        }

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isDeleteIndex() {
            return deleteIndex;
        }

        public void setDeleteIndex(boolean deleteIndex) {
            this.deleteIndex = deleteIndex;
        }
    }

    public ElasticSearch getElasticSearch() {
        return elasticSearch;
    }
}
