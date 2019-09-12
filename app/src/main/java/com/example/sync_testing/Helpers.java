package com.example.sync_testing;

public class Helpers {

    public static String getStreamInfoString(StreamInfo info) {
        return "frames per segment " + info.framesPerSegment + ", " +
                "producer sampling rate " + info.producerSamplingRate;
    }

}
