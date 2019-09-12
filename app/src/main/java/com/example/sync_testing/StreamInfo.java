package com.example.sync_testing;

import net.named_data.jndn.Name;

public class StreamInfo {

    public StreamInfo(long framesPerSegment, long producerSamplingRate) {
        this.framesPerSegment = framesPerSegment;
        this.producerSamplingRate = producerSamplingRate;
    }

    public long framesPerSegment;
    public long producerSamplingRate;

}