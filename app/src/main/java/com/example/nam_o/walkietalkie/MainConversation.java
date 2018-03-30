package com.example.nam_o.walkietalkie;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Button;

import com.montefiore.gaulthiergain.adhoclibrary.appframework.TransferManager;
import com.montefiore.gaulthiergain.adhoclibrary.network.datalinkmanager.AdHocDevice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class MainConversation extends Activity {

    private TransferManager transferManager = null;
    private Thread recordingThread = null;
    private Thread playThread = null;
    private AudioRecord recorder = null;
    private AudioTrack track = null;
    private AudioManager am = null;

    private byte buffer[] = null;
    private byte playBuffer[] = null;
    private byte[] data;
    int minSize = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private int bufferSize = minSize;
    private boolean isRecording = false;
    private String remoteAddrDevice;

    public MainConversation(TransferManager transferManager) {
        this.transferManager = transferManager;
    }

    // Based on previous project and
    // http://stackoverflow.com/questions/8499042/android-audiorecord-example
    // Record Audio
    public void startRecording() {
        Log.d("AUDIO", "Assigning recorder");
        buffer = new byte[bufferSize];

        // Start Recording
        recorder.startRecording();
        isRecording = true;
        // Start a thread
        recordingThread = new Thread(new Runnable() {
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
                Log.d("AUDIO", "Error when sending recording");
            }

        }
    }

    // Set input & output streams
    public void setupStreams() {
        /*try {
            inStream = bSocket.getInputStream();
        } catch (IOException e) {
            Log.e("SOCKET", "Error when creating input stream", e);
        }
        try {
            outStream = bSocket.getOutputStream();
        } catch (IOException e) {
            Log.e("SOCKET", "Error when creating output stream", e);
        }*/
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
                16000, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);
        // Audio record object
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);
    }

    // Playback received audio
    public void startPlaying() {
        Log.d("AUDIO", "Assigning player");
        // Receive Buffer
        // Receive and play audio
        playThread = new Thread(new Runnable() {
            @Override
            public void run() {
                receiveRecording();
            }
        }, "AudioTrack Thread");
        playThread.start();

    }


    // Receive audio and write into audio track object for playback
    public void receiveRecording() {

        playBuffer = new byte[minSize];

        track.play();

        int i = 0;
        while (!isRecording) {
            /*if (inStream.available() == 0) {
                //Do nothing
            } else {
                //inStream.read(playBuffer);
                track.write(playBuffer, 0, playBuffer.length);
            }*/
            if (data == null || data.length == 0) {
                //Do nothing
            } else {
                //inStream.read(playBuffer);
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