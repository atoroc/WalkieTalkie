package com.example.nam_o.walkietalkie;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.montefiore.gaulthiergain.adhoclibrary.appframework.ListenerAdapter;
import com.montefiore.gaulthiergain.adhoclibrary.appframework.ListenerApp;
import com.montefiore.gaulthiergain.adhoclibrary.appframework.TransferManager;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.exceptions.DeviceException;
import com.montefiore.gaulthiergain.adhoclibrary.network.datalinkmanager.AdHocDevice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    // Requesting permission to RECORD_AUDIO
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    public static final String TAG = "[TalkieWalkie]";
    private boolean permissionToRecordAccepted = false;

    // Define UI elements
    private Button btnAudio;
    private Button btnConnect;
    private Button btnEnable;
    private Button btnDisconnect;
    private ListView listView;

    private ArrayList<AdHocDevice> deviceList;
    private ArrayAdapter<AdHocDevice> adapter;

    private MainConversation audioClient;
    private TransferManager transferManager;

    private boolean listenAttempt = false;
    private boolean connectAttempt = false;
    private String remoteAddr;

    // Initialization of layout
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        transferManager = new TransferManager(true, getApplicationContext(), new ListenerApp() {
            @Override
            public void onDiscoveryCompleted(HashMap<String, AdHocDevice> mapAddressDevice) {

            }

            @Override
            public void receivedData(String senderName, String senderAddress, Object pdu) {
                Log.d(TAG, "Receive from" + senderName);
                audioClient.setData((byte[]) pdu);
            }

            @Override
            public void traceException(Exception e) {
                e.printStackTrace();
            }

            @Override
            public void onConnectionClosed(String remoteAddress, String remoteName) {
                Toast.makeText(MainActivity.this, "Disconnect with " + remoteAddress,
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onConnection(String remoteAddress, String remoteName) {

                // Start listening for btnAudio from other device
                audioClient.audioCreate();
                audioClient.startPlaying();
                btnAudio.setVisibility(View.VISIBLE);
                remoteAddr = remoteAddress;
                audioClient.setRemoteAddrDevice(remoteAddress);

                // Change status of UI elements
                Toast.makeText(MainActivity.this, "Connection was successful with " +
                        remoteAddress, Toast.LENGTH_LONG).show();
                listView.setVisibility(ListView.GONE);
                btnAudio.setVisibility(View.VISIBLE);
                btnConnect.setEnabled(false);
                connectAttempt = true;
            }

            @Override
            public void onConnectionFailed(String deviceName) {
                // Change status of UI elements if connection was unsuccessful
                Toast.makeText(MainActivity.this, "Connection was unsuccessful", Toast.LENGTH_LONG).show();
                listView.setVisibility(ListView.GONE);
            }
        });

        try {
            transferManager.getConfig().setJson(false);
            transferManager.start();
            Log.d(TAG, transferManager.getConfig().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        //ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);


        listView = findViewById(R.id.listViewItems);
        btnConnect = findViewById(R.id.connect);
        btnEnable = findViewById(R.id.enable);
        btnDisconnect = findViewById(R.id.disconnect);
        btnAudio = findViewById(R.id.audioBtn);

        audioClient = new MainConversation(transferManager);

        // Disable microphone button
        btnAudio.setVisibility(View.GONE);

        // Microphone button pressed/released
        btnAudio.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    audioClient.stopPlaying();
                    audioClient.startRecording();
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    audioClient.stopRecording();
                    audioClient.startPlaying();
                }
                return true;
            }
        });

        btnConnect.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Log.d(TAG, "Connect button pressed");

                // Handle UI changes
                listView.setVisibility(ListView.VISIBLE);
                btnConnect.setEnabled(false);

                // List to store all paired device information
                deviceList = new ArrayList<>();
                HashMap<String, AdHocDevice> pairedDevices = transferManager.getPairedDevices();

                // Populate list with the paired device information
                if (pairedDevices.size() > 0) {
                    Log.d(TAG, "Pair devices > 0");
                    for (Map.Entry<String, AdHocDevice> entry : pairedDevices.entrySet()) {
                        deviceList.add(entry.getValue());
                    }
                } else {
                    Log.d(TAG, "No paired devices found");
                }

                // No devices found
                if (deviceList.size() == 0) {
                    deviceList.add(new AdHocDevice("No devices found", ""));
                }

                // Populate List view with device information
                adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, deviceList);
                listView.setAdapter(adapter);
            }
        });

        // Disconnect
        btnDisconnect.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                /*boolean disconnectListen = false;
                boolean disconnectConnect = false;
                // Enable buttons and disable listView
                btnConnect.setEnabled(true);
                listView.setVisibility(ListView.GONE);

                audioClient.destroyProcesses();

                Log.d(TAG, "Disconnect");

                TODO
                if (disconnectListen || disconnectConnect) {
                    // Disconnect successful - Handle UI element change
                    btnAudio.setVisibility(View.GONE);
                    listen.setEnabled(true);
                    btnConnect.setEnabled(true);
                } else {
                    // Unsuccessful btnDisconnect - Do nothing
                }*/
            }
        });

        if (transferManager.isBluetoothEnable()) {
            btnEnable.setText(R.string.disable);
        } else {
            btnEnable.setText(R.string.enable);
        }

        btnEnable.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!transferManager.isBluetoothEnable()) {
                    transferManager.enableBluetooth(0, new ListenerAdapter() {
                        @Override
                        public void onEnableBluetooth(boolean success) {
                            if (success) {
                                Log.d(TAG, "Bluetooth is enabled");
                            } else {
                                Log.d(TAG, "Bluetooth is not enabled");
                            }
                        }

                        @Override
                        public void onEnableWifi(boolean success) {
                            if (success) {
                                Log.d(TAG, "WiFi is enabled");
                            } else {
                                Log.d(TAG, "WiFi is not enabled");
                            }
                        }
                    });

                } else {
                    try {
                        transferManager.disableBluetooth();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        // Attempt to btnConnect when paired device is clicked in ListView
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                try {
                    transferManager.connect(deviceList.get(position));
                } catch (DeviceException e) {
                    e.printStackTrace();
                }

                Log.d(TAG, "Attempting to Connect");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                // Permission granted
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted) finish();
    }

    @Override
    protected void onDestroy() {
        try {
            transferManager.stopListening();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }
}