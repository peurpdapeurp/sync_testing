package com.example.sync_testing;

public class MessageTypes {

    // General message types
    private static final int GENERAL_MSG_BASE = 0;
    public static final int MSG_DO_SOME_WORK = GENERAL_MSG_BASE;
    public static final int MSG_PROGRESS_EVENT = GENERAL_MSG_BASE + 1;
    public static final int MSG_SC_MODULE = GENERAL_MSG_BASE + 2;
    public static final int MSG_SP_MODULE = GENERAL_MSG_BASE + 3;
    public static final int MSG_SYNC_MODULE = GENERAL_MSG_BASE + 4;

    // Progress event msg subtypes
    private static final int PROGRESS_EVENT_MSG_BASE = GENERAL_MSG_BASE + 500;
    public static final int MSG_PROGRESS_EVENT_STREAM_CONSUMER_INITIALIZED = PROGRESS_EVENT_MSG_BASE;
    public static final int MSG_PROGRESS_EVENT_STREAM_FETCHER_FETCHING_COMPLETE = PROGRESS_EVENT_MSG_BASE + 1;
    public static final int MSG_PROGRESS_EVENT_STREAM_FETCHER_PRODUCTION_WINDOW_GROW = PROGRESS_EVENT_MSG_BASE + 2;
    public static final int MSG_PROGRESS_EVENT_STREAM_FETCHER_AUDIO_RETRIEVED = PROGRESS_EVENT_MSG_BASE + 3;
    public static final int MSG_PROGRESS_EVENT_STREAM_FETCHER_NACK_RETRIEVED = PROGRESS_EVENT_MSG_BASE + 4;
    public static final int MSG_PROGRESS_EVENT_STREAM_FETCHER_INTEREST_TRANSMIT = PROGRESS_EVENT_MSG_BASE + 5;
    public static final int MSG_PROGRESS_EVENT_STREAM_FETCHER_INTEREST_SKIP = PROGRESS_EVENT_MSG_BASE + 6;
    public static final int MSG_PROGRESS_EVENT_STREAM_FETCHER_INTEREST_TIMEOUT = PROGRESS_EVENT_MSG_BASE + 7;
    public static final int MSG_PROGRESS_EVENT_STREAM_FETCHER_PREMATURE_RTO = PROGRESS_EVENT_MSG_BASE + 8;
    public static final int MSG_PROGRESS_EVENT_STREAM_FETCHER_FINAL_BLOCK_ID_LEARNED = PROGRESS_EVENT_MSG_BASE + 9;
    public static final int MSG_PROGRESS_EVENT_STREAM_BUFFER_BUFFERING_COMPLETE = PROGRESS_EVENT_MSG_BASE + 10;
    public static final int MSG_PROGRESS_EVENT_STREAM_BUFFER_FRAME_SKIP = PROGRESS_EVENT_MSG_BASE + 11;
    public static final int MSG_PROGRESS_EVENT_STREAM_BUFFER_FRAME_PLAYED = PROGRESS_EVENT_MSG_BASE + 12;
    public static final int MSG_PROGRESS_EVENT_STREAM_BUFFER_FINAL_FRAME_NUM_LEARNED = PROGRESS_EVENT_MSG_BASE + 13;
    public static final int MSG_PROGRESS_EVENT_STREAM_PLAYER_PLAYING_COMPLETE = PROGRESS_EVENT_MSG_BASE + 14;
    public static final int MSG_PROGRESS_EVENT_STREAM_STATE_CREATED = PROGRESS_EVENT_MSG_BASE + 15;

    // SC Module msg subtypes
    private static final int SC_MODULE_MSG_BASE = PROGRESS_EVENT_MSG_BASE + 500;
    public static final int MSG_SC_MODULE_NEW_STREAM_AVAILABLE = SC_MODULE_MSG_BASE;

    // Sync Module msg subtypes
    private static final int SYNC_MODULE_MSG_BASE = SC_MODULE_MSG_BASE + 500;
    public static final int MSG_SYNC_MODULE_NEW_STREAM_PRODUCING = SYNC_MODULE_MSG_BASE;

}
