package com.example.sync_testing;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import net.named_data.jndn.Name;
import net.named_data.jndn.encoding.EncodingException;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Messages
    private static final int MSG_NETWORK_THREAD_INITIALIZED = 0;
    private static final int MSG_RECORDERMODULE_RECORD_STARTED = 3;
    private static final int MSG_SYNCMODULE_NEW_STREAMS_AVAILABLE = 5;

    // Thread objects
    private NetworkThread.Info networkThreadInfo_;
    private boolean networkThreadInitialized_ = false;

    // Back-end modules
    private SyncModule syncModule_;

    // Configuration parameters
    private Name applicationBroadcastPrefix_;
    private Name applicationDataPrefix_;
    private long syncSessionId_;

    // UI elements
    EditText channelNameInput_;
    EditText userNameInput_;
    Button notifySyncOfNewStreamButton_;
    Button initializeSyncModuleButton_;

    private Handler handler_;
    private Context ctx_;
    private long currentStreamId_ = 0;

    // shared preferences object to store login parameters between sessions
    SharedPreferences preferences_;
    SharedPreferences.Editor preferencesEditor_;
    private static String USER_NAME = "USER_NAME";
    private static String CHANNEL_NAME = "CHANNEL_NAME";

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        ctx_ = this;

        preferences_ = getSharedPreferences("preferences_", Context.MODE_PRIVATE);
        preferencesEditor_ = preferences_.edit();

        handler_ = new Handler() {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_NETWORK_THREAD_INITIALIZED: {
                        Log.d(TAG, "Network thread eventInitialized");
                        networkThreadInfo_ = (NetworkThread.Info) msg.obj;

                        syncModule_ = new SyncModule(
                                applicationBroadcastPrefix_,
                                applicationDataPrefix_,
                                syncSessionId_,
                                networkThreadInfo_.looper
                        );
                        syncModule_.eventNewStreamsAvailable.addListener(syncStreamInfos ->
                                handler_
                                        .obtainMessage(MSG_SYNCMODULE_NEW_STREAMS_AVAILABLE, syncStreamInfos)
                                        .sendToTarget()
                        );

                        notifySyncOfNewStreamButton_.setEnabled(true);

                        break;
                    }
                    case MSG_RECORDERMODULE_RECORD_STARTED: {
                        StreamInfo streamInfo = (StreamInfo) msg.obj;
                        try {
                            syncModule_.notifyNewStreamProducing(streamInfo.streamName.get(-1).toSequenceNumber());
                        } catch (EncodingException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    case MSG_SYNCMODULE_NEW_STREAMS_AVAILABLE: {
                        ArrayList<SyncModule.SyncStreamInfo> syncStreamInfos = (ArrayList<SyncModule.SyncStreamInfo>) msg.obj;
                        String syncStreamInfosString = "";
                        for (SyncModule.SyncStreamInfo syncStreamInfo : syncStreamInfos) {
                            syncStreamInfosString += syncStreamInfo.toString() + "\n";
                        }
                        Log.d(TAG, "Got notification of new streams from sync module: " + "\n" +
                                syncStreamInfosString);
                        break;
                    }
                    default:
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                }
            }
        };

        channelNameInput_ = (EditText) findViewById(R.id.channel_name_input);
        userNameInput_ = (EditText) findViewById(R.id.user_name_input);

        channelNameInput_.setText(preferences_.getString(CHANNEL_NAME, "DefaultChannelName"));
        userNameInput_.setText(preferences_.getString(USER_NAME, "DefaultUserName"));

        initializeSyncModuleButton_ = (Button) findViewById(R.id.initialize_sync_module_button);
        initializeSyncModuleButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String channelName = channelNameInput_.getText().toString();
                String userName = userNameInput_.getText().toString();

                preferencesEditor_.putString(CHANNEL_NAME, channelName).commit();
                preferencesEditor_.putString(USER_NAME, userName).commit();

                syncSessionId_ = System.currentTimeMillis();

                applicationBroadcastPrefix_ = new Name(getString(R.string.broadcast_prefix))
                        .append(channelName);
                applicationDataPrefix_ = new Name(getString(R.string.data_prefix))
                        .append(channelName)
                        .append(userName)
                .append(Long.toString(syncSessionId_));

                // Thread objects
                NetworkThread networkThread = new NetworkThread(
                        applicationDataPrefix_,
                        info -> handler_
                                .obtainMessage(MSG_NETWORK_THREAD_INITIALIZED, info)
                                .sendToTarget());

                networkThread.start();

                channelNameInput_.setEnabled(false);
                userNameInput_.setEnabled(false);
                initializeSyncModuleButton_.setEnabled(false);
            }
        });

        notifySyncOfNewStreamButton_ = (Button) findViewById(R.id.notify_new_stream_producing_button);
        notifySyncOfNewStreamButton_.setEnabled(false);
        notifySyncOfNewStreamButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handler_
                        .obtainMessage(MSG_RECORDERMODULE_RECORD_STARTED,
                        new StreamInfo(
                                new Name("test_stream_name").appendSequenceNumber(++currentStreamId_),
                                1,
                                8000,
                                System.currentTimeMillis()
                        ))
                        .sendToTarget();
            }
        });

    }

}
