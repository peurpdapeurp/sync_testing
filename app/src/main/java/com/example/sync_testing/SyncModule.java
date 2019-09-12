package com.example.sync_testing;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import net.named_data.jndn.Face;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;
import net.named_data.jndn.sync.ChronoSync2013;
import net.named_data.jndn.util.Blob;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;

public class SyncModule {

    private static final String TAG = "SyncModule";

    // Private constants
    private static final int DEFAULT_SYNC_INTEREST_LIFETIME_MS = 2000;
    private static final int PROCESSING_INTERVAL_MS = 50;

    // Messages
    private static final int MSG_DO_SOME_WORK = 0;
    private static final int MSG_INITIALIZE_SYNC = 1;
    public static final int MSG_NEW_STREAM_PRODUCING = 2;

    private Network network_;
    private Name applicationBroadcastPrefix_;
    private Name applicationDataPrefix_;
    private Handler handler_;

    public SyncModule(Name applicationBroadcastPrefix, Name applicationDataPrefix,
                      Looper networkThreadLooper) {

        applicationBroadcastPrefix_ = applicationBroadcastPrefix;
        applicationDataPrefix_ = applicationDataPrefix;
        Log.d(TAG, "SyncModule initialized (" +
                "applicationBroadcastPrefix " + applicationBroadcastPrefix + ", " +
                "applicationDataPrefix " + applicationDataPrefix +
                ")");

        handler_ = new Handler(networkThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case MSG_DO_SOME_WORK: {
                        doSomeWork();
                        break;
                    }
                    case MSG_INITIALIZE_SYNC: {
                        network_.initializeSync();
                        handler_.obtainMessage(MSG_DO_SOME_WORK).sendToTarget();
                        break;
                    }
                    case MSG_NEW_STREAM_PRODUCING: {
                        StreamInfo streamInfo = (StreamInfo) msg.obj;
                        Log.d(TAG, "new stream being produced: " + Helpers.getStreamInfoString(streamInfo));
                        network_.newStreamProductionNotifications_.add(streamInfo);
                        break;
                    }
                    default:
                        throw new IllegalStateException("unexpected msg.what: " + msg.what);
                }
            }
        };

        network_ = new Network();

        handler_.obtainMessage(MSG_INITIALIZE_SYNC).sendToTarget();
    }

    public void close() {
        network_.close();
        handler_.removeCallbacksAndMessages(null);
    }

    private void doSomeWork() {
        network_.doSomeWork();
        scheduleNextWork(SystemClock.uptimeMillis());
    }

    private void scheduleNextWork(long thisOperationStartTimeMs) {
        handler_.removeMessages(MSG_DO_SOME_WORK);
        handler_.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + PROCESSING_INTERVAL_MS);
    }

    public void notifyNewStreamProducing(StreamInfo streamInfo) {
        handler_
                .obtainMessage(MSG_NEW_STREAM_PRODUCING, streamInfo)
                .sendToTarget();
    }

    private class Network {

        private final static String TAG = "SyncModule_Network";
        
        private LinkedTransferQueue<StreamInfo> newStreamProductionNotifications_;
        private Face face_;
        private KeyChain keyChain_;
        private boolean closed_ = false;
        private ChronoSync2013 sync_;
        private Gson jsonSerializer_;

        private Network() {

            newStreamProductionNotifications_ = new LinkedTransferQueue<>();

            jsonSerializer_ = new Gson();

            // set up keychain
            keyChain_ = configureKeyChain();

            // set up face / sync
            face_ = new Face();
            try {
                face_.setCommandSigningInfo(keyChain_, keyChain_.getDefaultCertificateName());
            } catch (SecurityException e) {
                e.printStackTrace();
            }

        }

        private void initializeSync() {
            try {
                sync_ = new ChronoSync2013(onReceivedSyncState, onInitialized, applicationDataPrefix_, applicationBroadcastPrefix_,
                        System.currentTimeMillis(), face_, keyChain_, keyChain_.getDefaultCertificateName(), DEFAULT_SYNC_INTEREST_LIFETIME_MS,
                        onRegisterFailed);
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private void close() {
            if (closed_) return;
            closed_ = true;
        }

        private void doSomeWork() {

            if (closed_) return;

            while (newStreamProductionNotifications_.size() != 0) {
                StreamInfo streamInfo = newStreamProductionNotifications_.poll();
                if (streamInfo == null) continue;
                Log.d(TAG, "new stream being produced: " + Helpers.getStreamInfoString(streamInfo));
                try {
                    String streamInfoString = jsonSerializer_.toJson(streamInfo);
                    Log.d(TAG, "serialized stream info into json string: " + streamInfoString);
                    sync_.getSequenceNo();
                    sync_.publishNextSequenceNo(new Blob(streamInfoString));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }

            try {
                face_.processEvents();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (EncodingException e) {
                e.printStackTrace();
            }

        }

        ChronoSync2013.OnReceivedSyncState onReceivedSyncState = new ChronoSync2013.OnReceivedSyncState() {
            @Override
            public void onReceivedSyncState(List syncStates, boolean isRecovery) {
                for (Object o : syncStates) {

                    ChronoSync2013.SyncState syncState = (ChronoSync2013.SyncState) o;
                    long session = syncState.getSessionNo();
                    long seqNum = syncState.getSequenceNo();

                    if (seqNum == 0) {
                        // ignore the first sync state from all users, it will contain no application data
                        continue;
                    }

                    String dataPrefix = syncState.getDataPrefix();
                    String userId = dataPrefix.substring(dataPrefix.lastIndexOf("/") + 1);

                    if (dataPrefix.equals(applicationDataPrefix_.toString())) {
                        Log.d(TAG, "got sync state for own user");
                        continue;
                    }
                    else {
                        StreamInfo streamInfo = jsonSerializer_.fromJson(syncState.getApplicationInfo().toString(), StreamInfo.class);
                        Log.d(TAG, "\n" + "got sync state (" +
                                "session " + session + ", " +
                                "seqNum " + seqNum + ", " +
                                "dataPrefix " + dataPrefix.toString() + ", " +
                                "userId " + userId +
                                ")" + "\n" +
                                "application info (" +
                                Helpers.getStreamInfoString(streamInfo) +
                                ")");
                    }
                }

            }
        };

        ChronoSync2013.OnInitialized onInitialized = new ChronoSync2013.OnInitialized() {
            @Override
            public void onInitialized() {
                Log.d(TAG, "sync initialized, initial seq num " + sync_.getSequenceNo());
            }
        };

        OnRegisterFailed onRegisterFailed = new OnRegisterFailed() {
            @Override
            public void onRegisterFailed(Name prefix) {
                Log.e(TAG, "registration failed for " + prefix.toString());
            }
        };

        // taken from https://github.com/named-data-mobile/NFD-android/blob/4a20a88fb288403c6776f81c1d117cfc7fced122/app/src/main/java/net/named_data/nfd/utils/NfdcHelper.java
        private KeyChain configureKeyChain() {

            final MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
            final MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
            final KeyChain keyChain = new KeyChain(new IdentityManager(identityStorage, privateKeyStorage),
                    new SelfVerifyPolicyManager(identityStorage));

            Name name = new Name("/tmp-identity");

            try {
                // create keys, certs if necessary
                if (!identityStorage.doesIdentityExist(name)) {
                    keyChain.createIdentityAndCertificate(name);

                    // set default identity
                    keyChain.getIdentityManager().setDefaultIdentity(name);
                }
            }
            catch (SecurityException e){
                e.printStackTrace();
            }

            return keyChain;

        }
    }

}
