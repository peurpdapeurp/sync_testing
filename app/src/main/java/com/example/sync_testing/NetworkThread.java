package com.example.sync_testing;

import android.os.HandlerThread;
import android.os.Looper;

import net.named_data.jndn.Face;
import net.named_data.jndn.Name;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;
import net.named_data.jndn.util.MemoryContentCache;

public class NetworkThread extends HandlerThread {

    private static final String TAG = "NetworkThread";

    private Face face_;
    private KeyChain keyChain_;
    private MemoryContentCache mcc_;
    private Callbacks callbacks_;

    public static class Info {
        public Info(Looper looper, Face face, MemoryContentCache mcc) {
            this.looper = looper;
            this.face = face;
            this.mcc = mcc;
        }
        public Looper looper;
        public Face face;
        public MemoryContentCache mcc;
    }

    public interface Callbacks {
        void onInitialized(Info info);
    }

    public NetworkThread(Callbacks callbacks) {
        super(TAG);
        callbacks_ = callbacks;
    }

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

        callbacks_.onInitialized(new Info(getLooper(), face_, mcc_));
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

