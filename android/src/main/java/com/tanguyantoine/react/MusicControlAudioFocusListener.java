package com.tanguyantoine.react;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;

public class MusicControlAudioFocusListener implements AudioManager.OnAudioFocusChangeListener {
    private final MusicControlEventEmitter emitter;
    private final MusicControlVolumeListener volume;

    private AudioManager mAudioManager;
    private AudioFocusRequest mFocusRequest;

    private final Object focusLock = new Object();

    private boolean playbackDelayed = false;
    private boolean resumeOnFocusGain = false;
    private boolean playbackAuthorized = false;

    MusicControlAudioFocusListener(ReactApplicationContext context, MusicControlEventEmitter emitter,
                                   MusicControlVolumeListener volume) {
        this.emitter = emitter;
        this.volume = volume;

        this.mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                Log.d("MusicControlAudioFocus", "FOCUS GAINED");
                if (playbackDelayed || resumeOnFocusGain) {
                    synchronized(focusLock) {
                        playbackDelayed = false;
                        resumeOnFocusGain = false;
                    }
                    if (volume.getCurrentVolume() != 100) {
                        volume.setCurrentVolume(100);
                    }
                    if (playbackAuthorized) {
                        emitter.onPlay();
                    }
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                Log.d("MusicControlAudioFocus", "FOCUS LOST");
                synchronized(focusLock) {
                    resumeOnFocusGain = false;
                    playbackDelayed = false;
                }
                if (playbackAuthorized) {
                    emitter.onPause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                Log.d("MusicControlAudioFocus", "FOCUS LOST TRANSIENT");
                synchronized(focusLock) {
                    resumeOnFocusGain = true;
                    playbackDelayed = false;
                }
                if (playbackAuthorized) {
                    emitter.onPause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Log.d("MusicControlAudioFocus", "FOCUS LOST TRANSIENT DUCK");
                synchronized(focusLock) {
                    resumeOnFocusGain = true;
                    playbackDelayed = false;
                }
                if (playbackAuthorized) {
                    volume.onSetVolumeTo(40);
                }
                break;
        }
    }

    public void requestAudioFocus() {
        int focusResponse;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(this)
                    .build();
            focusResponse = mAudioManager.requestAudioFocus(mFocusRequest);
        } else {
            focusResponse = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        synchronized(focusLock) {
            if (focusResponse == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                playbackAuthorized = false;
            } else if (focusResponse == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                playbackAuthorized = true;
            } else if (focusResponse == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                playbackDelayed = true;
                playbackAuthorized = false;
            }
        }
    }

    public void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mAudioManager != null) {
            mAudioManager.abandonAudioFocusRequest(mFocusRequest);
        } else if ( mAudioManager != null ) {
            mAudioManager.abandonAudioFocus(this);
        }
    }
}
