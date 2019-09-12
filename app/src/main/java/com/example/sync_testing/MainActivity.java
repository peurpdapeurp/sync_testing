package com.example.sync_testing;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import net.named_data.jndn.Name;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Messages
    private static final int MSG_NETWORK_THREAD_INITIALIZED = 0;

    // Thread objects
    private NetworkThread networkThread_;
    private NetworkThread.Info networkThreadInfo_;
    private boolean networkThreadInitialized_ = false;

    // Back-end modules
    private SyncModule syncModule_;

    private Handler handler_;

    // Ui Elements
    EditText channelNameInput_;
    EditText userNameInput_;
    Button notifySyncOfNewStreamButton_;
    Button initializeSyncModuleButton_;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        handler_ = new Handler() {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_NETWORK_THREAD_INITIALIZED: {
                        networkThreadInfo_ = (NetworkThread.Info) msg.obj;
                        Log.d(TAG, "NetworkThread initialized.");
                        networkThreadInitialized_ = true;
                        break;
                    }
                    default:
                        throw new IllegalStateException("unexpected msg: " + msg.what);
                }
            }
        };

        channelNameInput_ = (EditText) findViewById(R.id.channel_name_input);
        userNameInput_ = (EditText) findViewById(R.id.user_name_input);

        networkThread_ = new NetworkThread(new NetworkThread.Callbacks() {
            @Override
            public void onInitialized(NetworkThread.Info info) {
                handler_
                        .obtainMessage(MSG_NETWORK_THREAD_INITIALIZED, info)
                        .sendToTarget();
            }
        });
        networkThread_.start();

        notifySyncOfNewStreamButton_ = (Button) findViewById(R.id.notify_new_stream_producing_button);
        notifySyncOfNewStreamButton_.setEnabled(false);
        notifySyncOfNewStreamButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                syncModule_.notifyNewStreamProducing(
                        new StreamInfo(1, 8000)
                );
            }
        });

        initializeSyncModuleButton_ = (Button) findViewById(R.id.initialize_sync_module_button);
        initializeSyncModuleButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (networkThreadInitialized_) {
                    Name applicationBroadcastPrefix = new Name(getString(R.string.broadcast_prefix)).append(
                            channelNameInput_.getText().toString()
                    );
                    Name applicationDataPrefix = new Name(getString(R.string.data_prefix)).append(
                            userNameInput_.getText().toString()
                    );
                    syncModule_ = new SyncModule(applicationBroadcastPrefix, applicationDataPrefix, networkThreadInfo_.looper);
                    channelNameInput_.setEnabled(false);
                    userNameInput_.setEnabled(false);
                    initializeSyncModuleButton_.setEnabled(false);
                    notifySyncOfNewStreamButton_.setEnabled(true);
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public Handler getHandler() {
        return handler_;
    }

}
