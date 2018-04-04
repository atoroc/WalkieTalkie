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
import java.util.ArrayList;


public class AudioClients extends Activity {

    public static final int RATE_IN_HZ = 44100;
    private int[] mSampleRates = new int[]{8000, 11025, 22050, 44100};
    public static final String TAG = "TAG";
    public int nbClients;

    private final int minSize;
    private final int bufferSize;

    private TransferManager transferManager;
    private AudioRecord recorder = null;
    private AudioTrack track = null;

    private byte buffer[] = null;
    private byte[] data;
    private boolean isRecording = false;
    private ArrayList<String> arrayRemoteDevices;

    public AudioClients(TransferManager transferManager) {
        this.transferManager = transferManager;
        this.minSize = AudioTrack.getMinBufferSize(RATE_IN_HZ, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        this.bufferSize = minSize;
        this.nbClients = 0;
        this.arrayRemoteDevices = new ArrayList<>();
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
                for (String remoteAddrDevice : arrayRemoteDevices) {
                    transferManager.sendMessageTo(buffer, remoteAddrDevice);
                }
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

    public void addRemoteAddr(String remoteAddrDevice) {
        this.arrayRemoteDevices.add(remoteAddrDevice);
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    private AudioRecord findAudioRecord() {
        for (int rate : mSampleRates) {
            for (short audioFormat : new short[]{AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT}) {
                for (short channelConfig : new short[]{AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO}) {
                    try {
                        Log.d(TAG, "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: "
                                + channelConfig);
                        int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

                        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                            // check if we can instantiate and have a success
                            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, channelConfig, audioFormat, bufferSize);

                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                                Log.d(TAG, "Success");
                                return recorder;
                            } else {
                                Log.d(TAG, String.valueOf(recorder.getState()));
                            }

                        }
                    } catch (Exception e) {
                        Log.v(TAG, rate + "Exception, keep trying.", e);
                    }
                }
            }
        }
        return null;
    }

    public int getNbClients() {
        return nbClients;
    }

    public void clientDisconnect() {
        nbClients--;
    }

    public void clientConnect() {
        nbClients++;
    }

    public void disconnect(String remoteAddrDevice) {
        if (arrayRemoteDevices.contains(remoteAddrDevice)) {
            arrayRemoteDevices.remove(remoteAddrDevice);
        }
    }
}