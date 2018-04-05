package com.example.nam_o.walkietalkie;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
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
import com.montefiore.gaulthiergain.adhoclibrary.appframework.exceptions.MaxThreadReachedException;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.exceptions.DeviceException;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.exceptions.NoConnectionException;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.service.AdHocDevice;
import com.montefiore.gaulthiergain.adhoclibrary.network.exceptions.DeviceAlreadyConnectedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    // Requesting permission to RECORD_AUDIO
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    public static final String TAG = "[TalkieWalkie][Main]";
    private boolean permissionToRecordAccepted = false;

    // Define UI elements
    private Button btnAudio;
    private Button btnConnect;
    private Button btnEnable;
    private Button btnDisconnect;
    private ListView listView;

    private ArrayList<AdHocDevice> deviceList;
    private ArrayAdapter<AdHocDevice> adapter;

    private AudioClients audioClients;
    private TransferManager transferManager;

    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    // Initialization of layout
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        transferManager = new TransferManager(true, getApplicationContext(), new ListenerApp() {
            @Override
            public void onDiscoveryCompleted(HashMap<String, AdHocDevice> mapAddressDevice) {

            }

            @Override
            public void onReceivedData(String senderName, String senderAddress, Object pdu) {
                Log.d(TAG, "Receive from " + senderName + " " + senderAddress);
                audioClients.setData((byte[]) pdu);
            }

            @Override
            public void traceException(Exception e) {
                e.printStackTrace();
            }

            @Override
            public void onConnectionClosed(String remoteAddress, String remoteName) {
                Toast.makeText(MainActivity.this, "Disconnect with " + remoteAddress,
                        Toast.LENGTH_LONG).show();

                audioClients.clientDisconnect();

                audioClients.disconnect(remoteAddress);
                if (audioClients.getNbClients() == 0) {
                    // Enable buttons and disable listView
                    btnConnect.setEnabled(true);
                    listView.setVisibility(ListView.GONE);
                    audioClients.destroyProcesses();

                    // Handle UI element change
                    btnAudio.setVisibility(View.GONE);
                    btnDisconnect.setEnabled(false);
                    btnConnect.setEnabled(true);
                }
            }

            @Override
            public void onConnection(String remoteAddress, String remoteName, int hops) {

                Toast.makeText(MainActivity.this, "Connection was successful with " +
                        remoteAddress, Toast.LENGTH_LONG).show();

                audioClients.addRemoteAddr(remoteAddress);
                if (audioClients.getNbClients() == 0) {

                    // Start listening for btnAudio from other device
                    audioClients.audioCreate();
                    audioClients.startPlaying();
                    btnAudio.setVisibility(View.VISIBLE);
                    listView.setVisibility(ListView.GONE);
                    btnDisconnect.setEnabled(true);
                    btnConnect.setEnabled(false);
                }

                audioClients.clientConnect();
            }

            @Override
            public void onConnectionFailed(String deviceName) {
                // Change status of UI elements if connection was unsuccessful
                Toast.makeText(MainActivity.this, "Connection was unsuccessful", Toast.LENGTH_LONG).show();
                listView.setVisibility(ListView.GONE);
                btnConnect.setEnabled(true);
            }
        });

        try {
            transferManager.getConfig().setNbThreadBt(3);
            transferManager.getConfig().setJson(false);
            transferManager.getConfig().setReliableTransportWifi(false);
            transferManager.start();
            Log.d(TAG, transferManager.getConfig().toString());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MaxThreadReachedException e) {
            e.printStackTrace();
        }

        listView = findViewById(R.id.listViewItems);
        btnConnect = findViewById(R.id.connect);
        btnEnable = findViewById(R.id.enable);
        btnDisconnect = findViewById(R.id.disconnect);
        btnAudio = findViewById(R.id.audioBtn);

        audioClients = new AudioClients(transferManager);

        // Disable microphone button
        btnAudio.setVisibility(View.GONE);
        btnDisconnect.setEnabled(false);

        // Microphone button pressed/released
        btnAudio.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    audioClients.stopPlaying();
                    audioClients.startRecording();
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    audioClients.stopRecording();
                    audioClients.startPlaying();
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
                    deviceList.add(new AdHocDevice("No devices found", "", -1));
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

                Log.d(TAG, "Disconnect");

                // Enable buttons and disable listView
                btnConnect.setEnabled(true);
                listView.setVisibility(ListView.GONE);

                try {
                    transferManager.disconnectAll();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Handle UI element change
                btnAudio.setVisibility(View.GONE);
                btnConnect.setEnabled(true);
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
                                btnEnable.setText(R.string.disable);
                            } else {
                                Log.d(TAG, "Unable to enable Bluetooth");
                            }
                        }

                        @Override
                        public void onEnableWifi(boolean success) {
                            if (success) {
                                Log.d(TAG, "WiFi is enabled");
                            } else {
                                Log.d(TAG, "Unable to enable WiFi");
                            }
                        }
                    });

                } else {
                    try {
                        transferManager.disableBluetooth();
                        btnEnable.setText(R.string.disable);
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
                } catch (DeviceAlreadyConnectedException e) {
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
            transferManager.resetWifiName();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (DeviceException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }
}