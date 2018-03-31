package com.example.nam_o.walkietalkie;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import com.montefiore.gaulthiergain.adhoclibrary.appframework.TransferManager;

import java.io.IOException;


public class MainConversation extends Activity {

    public static final int RATE_IN_HZ = 16000;
    public static final String TAG = "TAG";

    private final int minSize;
    private final int bufferSize;

    private TransferManager transferManager;
    private AudioRecord recorder = null;
    private AudioTrack track = null;

    private byte buffer[] = null;
    private byte[] data;
    private boolean isRecording = false;
    private String remoteAddrDevice;

    public MainConversation(TransferManager transferManager) {
        this.transferManager = transferManager;
        this.minSize = AudioTrack.getMinBufferSize(RATE_IN_HZ, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        this.bufferSize = minSize;
    }

    // Based on previous project and
    // http://stackoverflow.com/questions/8499042/android-audiorecord-example
    // Record Audio
    public void startRecording() {
        Log.d(TAG, "Assigning recorder");
        buffer = new byte[bufferSize];

        // Start Recording
        recorder.startRecording();
        isRecording = true;
        // Start a thread
        Thread recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                sendRecording();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    // Method for sending Audio
    public void sendRecording() {
        // Infinite loop until microphone button is released
        while (isRecording) {
            try {
                recorder.read(buffer, 0, bufferSize);
                //outStream.write(buffer);
                transferManager.sendMessageTo(buffer, remoteAddrDevice);
            } catch (IOException e) {
                Log.d(TAG, "Error when sending recording");
            }

        }
    }

    // Stop Recording and free up resources
    public void stopRecording() {
        if (recorder != null) {
            isRecording = false;
            recorder.stop();
        }
    }

    public void audioCreate() {
        // Audio track object
        track = new AudioTrack(AudioManager.STREAM_MUSIC,
                RATE_IN_HZ, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);
        // Audio record object
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE_IN_HZ,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);
    }

    // Playback received audio
    public void startPlaying() {
        Log.d(TAG, "Assigning player");
        // Receive and play audio
        Thread playThread = new Thread(new Runnable() {
            @Override
            public void run() {
                receiveRecording();
            }
        }, "AudioTrack Thread");
        playThread.start();

    }


    // Receive audio and write into audio track object for playback
    public void receiveRecording() {

        byte[] playBuffer = new byte[minSize];

        track.play();

        int i = 0;
        while (!isRecording) {
            if (data == null || data.length == 0) {
                //Do nothing
            } else {
                playBuffer = data;
                track.write(playBuffer, 0, playBuffer.length);
                data = null;
            }
        }
    }

    // Stop playing and free up resources
    public void stopPlaying() {
        if (track != null) {
            isRecording = true;
            track.stop();
        }
    }

    public void destroyProcesses() {
        //Release resources for audio objects
        track.release();
        recorder.release();
    }

    public void setRemoteAddrDevice(String remoteAddrDevice) {
        this.remoteAddrDevice = remoteAddrDevice;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}