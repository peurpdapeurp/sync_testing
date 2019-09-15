package com.example.sync_testing;

import net.named_data.jndn.Name;

public class StreamInfo {

    public StreamInfo(Name streamName, long framesPerSegment, long producerSamplingRate,
                      long recordingStartTime) {
        this.streamName = streamName;
        this.framesPerSegment = framesPerSegment;
        this.producerSamplingRate = producerSamplingRate;
        this.recordingStartTime = recordingStartTime;
    }

    public Name streamName;
    public long framesPerSegment;
    public long producerSamplingRate;
    public long recordingStartTime;

    public StreamMetaData getMetaData() {
        return new StreamMetaData(framesPerSegment, producerSamplingRate, recordingStartTime);
    }

    @Override
    public String toString() {
        return "streamName " + streamName.toString() + ", " +
                "framesPerSegment " + framesPerSegment + ", " +
                "producerSamplingRate " + producerSamplingRate + ", " +
                "recordingStartTime " + recordingStartTime;
    }
}