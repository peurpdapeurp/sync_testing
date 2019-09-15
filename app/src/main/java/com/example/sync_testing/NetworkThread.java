package com.example.sync_testing;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;

import java.io.IOException;

public class NetworkThread extends HandlerThread {

    private static final String TAG = "NetworkThread";

    // Private constants
    private static final int PROCESSING_INTERVAL_MS = 50;

    // Messages
    private static final int MSG_DO_SOME_WORK = 0;

    private Face face_;
    private KeyChain keyChain_;
    private Callbacks callbacks_;
    private Handler handler_;
    private Name applicationDataPrefix_;

    public static class Info {
        public Info(Looper looper, Face face) {
            this.looper = looper;
            this.face = face;
        }
        public Looper looper;
        public Face face;
    }

    public interface Callbacks {
        void onInitialized(Info info);
    }

    public NetworkThread(Name applicationDataPrefix, Callbacks callbacks) {
        super(TAG);
        applicationDataPrefix_ = applicationDataPrefix;
        callbacks_ = callbacks;
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

        try {
            face_.registerPrefix(applicationDataPrefix_,
                    new OnInterestCallback() {
                        @Override
                        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {

                        }
                    }, new OnRegisterFailed() {
                        @Override
                        public void onRegisterFailed(Name prefix) {

                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
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
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        handler_.obtainMessage(MSG_DO_SOME_WORK).sendToTarget();

        callbacks_.onInitialized(new Info(getLooper(), face_));
    }

    private void doSomeWork() {
        try {
            face_.processEvents();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (EncodingException e) {
            e.printStackTrace();
        }
        scheduleNextWork(SystemClock.uptimeMillis());
    }

    private void scheduleNextWork(long thisOperationStartTimeMs) {
        handler_.removeMessages(MSG_DO_SOME_WORK);
        handler_.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + PROCESSING_INTERVAL_MS);
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

