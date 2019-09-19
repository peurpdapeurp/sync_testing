package com.example.sync_testing;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    // Events
    public Event<ArrayList<SyncStreamInfo>> eventNewStreamsAvailable;

    private Network network_;
    private Name applicationBroadcastPrefix_;
    private Name applicationDataPrefix_;
    private long sessionId_;
    private Handler handler_;

    public static class SyncStreamInfo {
        public SyncStreamInfo(String channelName, String userName, long sessionId, long seqNum) {
            this.channelName = channelName;
            this.userName = userName;
            this.sessionId = sessionId;
            this.seqNum = seqNum;
        }
        String channelName;
        String userName;
        long sessionId;
        long seqNum;

        @Override
        public String toString() {
            return "channelName " + channelName + ", " +
                    "userName " + userName + ", " +
                    "sessionId " + sessionId + ", " +
                    "seqNum " + seqNum;
        }
    }

    public SyncModule(Name applicationBroadcastPrefix, Name applicationDataPrefix,
                      long sessionId, Looper networkThreadLooper) {

        applicationBroadcastPrefix_ = applicationBroadcastPrefix;
        applicationDataPrefix_ = applicationDataPrefix;
        Log.d(TAG, "SyncModule initialized (" +
                "applicationBroadcastPrefix " + applicationBroadcastPrefix + ", " +
                "applicationDataPrefix " + applicationDataPrefix +
                ")");

        sessionId_ = sessionId;

        eventNewStreamsAvailable = new SimpleEvent<>();

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
                        Long seqNum = (Long) msg.obj;
                        Log.d(TAG, "new stream being produced, seq num " + seqNum);
                        network_.newStreamProductionNotifications_.add(seqNum);
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

    public void notifyNewStreamProducing(long seqNum) {
        handler_
                .obtainMessage(MSG_NEW_STREAM_PRODUCING, Long.valueOf(seqNum))
                .sendToTarget();
    }

    private class Network {

        private final static String TAG = "SyncModule_Network";

        private LinkedTransferQueue<Long> newStreamProductionNotifications_;
        private Face face_;
        private KeyChain keyChain_;
        private boolean closed_ = false;
        private ChronoSync2013 sync_;
        private Gson jsonSerializer_;
        private HashMap<UserIdAndSession, HashSet<Long>> recvdSeqNums_;
        private HashMap<UserIdAndSession, Long> lastSeqNum_;

        private class UserIdAndSession {
            public UserIdAndSession(String userId, long session) {
                this.userId = userId;
                this.session = session;
            }
            final String userId;
            final long session;

            @Override
            public String toString() {
                return "userId " + userId + ", " +
                        "session " + session;
            }

            @Override
            public int hashCode() {
                int prime = 31;
                int result = 1;
                result = prime * result + ((userId == null) ? 0 : userId.hashCode());
                result = prime * result + Long.valueOf(session).hashCode();
                return result;
            }

            @Override
            public boolean equals(Object obj) {
                UserIdAndSession other;
                try {
                    other = (UserIdAndSession) obj;
                }
                catch (Exception e) {
                    return false;
                }
                return (userId.equals(other.userId) && session == other.session);
            }
        }

        private Network() {

            newStreamProductionNotifications_ = new LinkedTransferQueue<>();

            recvdSeqNums_ = new HashMap<>();
            lastSeqNum_ = new HashMap<>();

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
                        sessionId_, face_, keyChain_, keyChain_.getDefaultCertificateName(), DEFAULT_SYNC_INTEREST_LIFETIME_MS,
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
                Long seqNum = newStreamProductionNotifications_.poll();
                if (seqNum == null) continue;
                try {
                    if (seqNum != sync_.getSequenceNo() + 1) {
                        throw new IllegalStateException("got unexpected stream seq num (expected " +
                                (sync_.getSequenceNo() + 1) + ", got " + seqNum + ")");
                    }
                    sync_.publishNextSequenceNo();
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
                    Name dataPrefixName = new Name(syncState.getDataPrefix());
                    long session = syncState.getSessionNo();
                    long seqNum = syncState.getSequenceNo();
                    String userName = dataPrefixName.get(-2).toEscapedString();
                    String channelName = dataPrefixName.get(-3).toEscapedString();
                    UserIdAndSession userIdAndSession = new UserIdAndSession(userName, session);

                    Log.d(TAG, "sync state data prefix: " + dataPrefixName.toString() + ", " +
                                "our appDataPrefix; " + applicationDataPrefix_.toString());

                    if (dataPrefixName.equals(applicationDataPrefix_)) {
                        Log.d(TAG, "got sync state for own user");
                        continue;
                    }

                    if (isDuplicateSeqNum(userIdAndSession, seqNum))
                        continue;

                    if (!lastSeqNum_.containsKey(userIdAndSession)) {
                        lastSeqNum_.put(userIdAndSession, seqNum);
                    }

                    Log.d(TAG, "\n" + "got sync state (" +
                            "session " + session + ", " +
                            "seqNum " + seqNum + ", " +
                            "dataPrefix " + dataPrefixName.toString() + ", " +
                            "userId " + userName + ", " +
                            "isRecovery " + isRecovery +
                            ")");

                    ArrayList<SyncStreamInfo> availableStreams = new ArrayList<>();
                    long lastSeqNum = lastSeqNum_.get(userIdAndSession);

                    if (lastSeqNum > seqNum) {
                        Log.d(TAG, "got seq num " + seqNum + ", but last seq num was " + lastSeqNum + "; ignoring it");
                        continue;
                    }

                    if (lastSeqNum < seqNum) {
                        for (int i = 0; i < seqNum - lastSeqNum - 1; i++) {
                            availableStreams.add(
                                    new SyncStreamInfo(
                                            channelName,
                                            userName,
                                            session,
                                            lastSeqNum + i + 1
                                    )
                            );
                        }
                    }
                    availableStreams.add(
                            new SyncStreamInfo(
                                    channelName,
                                    userName,
                                    session,
                                    seqNum
                            )
                    );

                    eventNewStreamsAvailable.trigger(availableStreams);

                    lastSeqNum_.put(userIdAndSession, seqNum);

                }

            }
        };

        // returns true if seq num was duplicate, false if seq num was not duplicate
        private boolean isDuplicateSeqNum(UserIdAndSession userIdAndSession, long seqNum) {
            if (!recvdSeqNums_.containsKey(userIdAndSession)) {
                recvdSeqNums_.put(userIdAndSession, new HashSet<Long>());
                recvdSeqNums_.get(userIdAndSession).add(seqNum);
                return false;
            }

            HashSet<Long> seqNums = recvdSeqNums_.get(userIdAndSession);
            if (seqNums.contains(seqNum)) {
                Log.d(TAG, "duplicate seq num " + seqNum + " from " + userIdAndSession);
                return true;
            }
            seqNums.add(seqNum);
            return false;
        }

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
