package com.dellemc.desdp.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicReaderAppConfiguration extends AppConfiguration {
    private static Logger log = LoggerFactory.getLogger(MusicReaderAppConfiguration.class);

//    private final int numCameras;
//    private final int imageWidth;
//    private final int chunkSizeBytes;
//    private final boolean dropChunks;
//    private final double framesPerSec;
//    private final boolean writeToPravega;
//    private final boolean useCachedFrame;
    private final AppConfiguration.StreamConfig sensorStreamConfig;

    public MusicReaderAppConfiguration(String[] args) {
        super(args);
//        numCameras = getParams().getInt("numCameras", 4);
//        imageWidth = getParams().getInt("imageWidth", 100);
//        chunkSizeBytes = getParams().getInt("chunkSizeBytes", 512*1024);
//        dropChunks = getParams().getBoolean("dropChunks", false);
//        framesPerSec = getParams().getDouble("framesPerSec", 1.0);
//        writeToPravega = getParams().getBoolean("writeToPravega", false);
//        useCachedFrame = getParams().getBoolean("useCachedFrame", false);
        sensorStreamConfig = new AppConfiguration.StreamConfig(getPravegaConfig(),"sensor-",  getParams());
    }

    @Override
    public String toString() {
        return "MusicReaderAppConfiguration{" +
                super.toString() +
//                ", numCameras=" + numCameras +
//                ", imageWidth=" + imageWidth +
//                ", chunkSizeBytes=" + chunkSizeBytes +
//                ", dropChunks=" + dropChunks +
//                ", framesPerSec=" + framesPerSec +
//                ", writeToPravega=" + writeToPravega +
//                ", useCachedFrame=" + useCachedFrame +
                ", sensorStreamConfig=" + sensorStreamConfig +
                '}';
    }

//    public int getNumCameras() {
//        return numCameras;
//    }
//
//    public int getImageWidth() {
//        return imageWidth;
//    }
//
//    public int getChunkSizeBytes() {
//        return chunkSizeBytes;
//    }
//
//    public boolean isDropChunks() {
//        return dropChunks;
//    }
//
//    public double getFramesPerSec() {
//        return framesPerSec;
//    }
//
//    public boolean isWriteToPravega() {
//        return writeToPravega;
//    }
//
//    public boolean isUseCachedFrame() {
//        return useCachedFrame;
//    }

    public AppConfiguration.StreamConfig getSensorStreamConfig() {
        return sensorStreamConfig;
    }
}


