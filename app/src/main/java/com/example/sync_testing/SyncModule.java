package com.example.sync_testing;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;
import net.named_data.jndn.sync.ChronoSync2013;

import java.io.IOException;
import java.util.HashSet;

public class SyncModule {

    private static final String TAG = "SyncModule";

    private Handler mainThreadHandler_;
    private NetworkThread networkThread_;
    private ChronoSync2013 sync_;

    public SyncModule(Handler mainThreadHandler) {
        mainThreadHandler_ = mainThreadHandler;
        networkThread_ = new NetworkThread();
        networkThread_.start();
    }

    public void handleMessage(Message msg) {
        switch(msg.what) {
            default:
                throw new IllegalStateException("unexpected msg.what " + msg.what);
        }
    }

    private class NetworkThread extends HandlerThread {

        private final static String TAG = "SyncModule_NetworkThread";

        // Private constants
        private static final int PROCESSING_INTERVAL_MS = 50;

        // Messages
        private static final int MSG_DO_SOME_WORK = 0;

        private Face face_;
        private KeyChain keyChain_;
        private boolean closed_ = false;
        private Handler handler_;

        private NetworkThread() {
            super(TAG);
        }

        private void close() {
            if (closed_) return;
            closed_ = true;
        }

        private void doSomeWork() {
            if (closed_) return;
            try {
                face_.processEvents();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (EncodingException e) {
                e.printStackTrace();
            }
            scheduleNextWork(PROCESSING_INTERVAL_MS);
        }

        private void scheduleNextWork(long thisOperationStartTimeMs) {
            handler_.removeMessages(MSG_DO_SOME_WORK);
            handler_.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + PROCESSING_INTERVAL_MS);
        }

        @SuppressLint("HandlerLeak")
        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
            // set up keychain
            keyChain_ = configureKeyChain();
            // set up face
            face_ = new Face();
            try {
                face_.setCommandSigningInfo(keyChain_, keyChain_.getDefaultCertificateName());
            } catch (SecurityException e) {
                e.printStackTrace();
            }
            handler_ = new Handler() {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    switch (msg.what) {
                        case MSG_DO_SOME_WORK: {
                            doSomeWork();
                            break;
                        }
                        default:
                            throw new IllegalStateException("unexpected msg.what: " + msg.what);
                    }
                }
            };
            doSomeWork();
        }

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
