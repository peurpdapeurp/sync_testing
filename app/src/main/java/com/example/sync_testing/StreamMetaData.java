package com.example.sync_testing;

public class StreamMetaData {

    public StreamMetaData(long framesPerSegment, long producerSamplingRate, long recordingStartTimeStamp) {
        this.framesPerSegment = framesPerSegment;
        this.producerSamplingRate = producerSamplingRate;
        this.recordingStartTimestamp = recordingStartTimeStamp;
    }

    long framesPerSegment;
    long producerSamplingRate;
    long recordingStartTimestamp;

    @Override
    public String toString() {
        return "framesPerSegment " + framesPerSegment + ", " +
                "producerSamplingRate " + producerSamplingRate + ", " +
                "recordingStartTime " + recordingStartTimestamp;
    }
}
