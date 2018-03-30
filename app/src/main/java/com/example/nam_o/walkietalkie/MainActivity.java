package com.example.nam_o.walkietalkie;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
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

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    // Requesting permission to RECORD_AUDIO
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;

    // Define UI elements
    private Button audio;
    private Button listen;
    private Button connect;
    private Button disconnect;
    private ListView listView;

    //Bluetooth parameters
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private BluetoothAdapter mBluetoothAdapter;
    private Set<BluetoothDevice> pairedDevices;
    private BluetoothDevice device;
    private ArrayList<DeviceInfo> deviceList;
    private ArrayAdapter<DeviceInfo> adapter;

    private MainConversation audioClient;
    private ListenThread listenThread;
    private ConnectThread connectThread;
    private BluetoothSocket bSocket;

    private boolean listenAttempt = false;
    private boolean connectAttempt = false;

    // Initialization of layout
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        //Bluetooth
        Log.d("BLUETOOTH", "On create");
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Identify UI elements
        listView = (ListView) findViewById(R.id.listViewItems);
        connect = (Button) findViewById(R.id.connect);
        listen = (Button) findViewById(R.id.listen);
        disconnect = (Button) findViewById(R.id.disconnect);
        audio = (Button) findViewById(R.id.audioBtn);

        listenThread = new ListenThread();
        connectThread = new ConnectThread();
        audioClient = new MainConversation();

        // Disable microphone button
        audio.setVisibility(View.GONE);

        // Microphone button pressed/released
        audio.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN ) {
                    Log.d("TAG", "MotionEvent.ACTION_DOWN");
                    audioClient.stopPlaying();
                    audioClient.startRecording();

                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ) {
                    Log.d("TAG", "MotionEvent.ACTION_UP");
                    audioClient.stopRecording();
                    audioClient.startPlaying();
                }
                return true;
            }
        });

        // Send CONNECT request
        connect.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View arg0) {

                Log.d("BLUETOOTH", "Connect button pressed");

                // Handle UI changes
                listView.setVisibility(ListView.VISIBLE);
                connect.setEnabled(false);

                // List to store all paired device information
                deviceList = new ArrayList<DeviceInfo>();
                pairedDevices = mBluetoothAdapter.getBondedDevices();

                // Populate list with the paired device information
                if (pairedDevices.size() > 0) {
                    Log.d("BLUETOOTH", "Pair devices > 0");
                    for (BluetoothDevice device : pairedDevices) {
                        DeviceInfo newDevice= new DeviceInfo(device.getName(),device.getAddress());
                        deviceList.add(newDevice);
                    }
                } else {
                    Log.d("BLUETOOTH", "No paired devices found");
                }

                // No devices found
                if (deviceList.size() == 0) {
                    deviceList.add(new DeviceInfo("No devices found", ""));
                }

                // Populate List view with device information
                adapter = new ArrayAdapter<DeviceInfo>(MainActivity.this, android.R.layout.simple_list_item_1, deviceList);
                listView.setAdapter(adapter);
            }
        });

        // Listen for connection requests
        listen.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View arg0) {
                // Handle UI elements - change status
                listen.setEnabled(false);
                connect.setEnabled(false);
                listView.setVisibility(ListView.GONE);
                // Accept connection
                boolean connectSuccess = listenThread.acceptConnect(mBluetoothAdapter, MY_UUID);
                Log.d("BLUETOOTH", "Listen");

                if (connectSuccess) {
                    // Connection successful - get socket object, start listening, change visibility of UI elements
                    bSocket = listenThread.getSocket();
                    audioClient.audioCreate();
                    audioClient.setSocket(bSocket);
                    audioClient.setupStreams();
                    audioClient.startPlaying();
                    Toast.makeText(MainActivity.this, "Connection was successful", Toast.LENGTH_LONG).show();
                    audio.setVisibility(View.VISIBLE);
                    listenAttempt = true;
                } else {
                    // Connection Unsuccessful - change visibility of UI elements
                    Toast.makeText(MainActivity.this, "Connection was unsuccessful", Toast.LENGTH_LONG).show();
                    listen.setEnabled(true);
                    connect.setEnabled(true);
                }
            }
        });

        // Disconnect
        disconnect.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View arg0) {

                boolean disconnectListen = false;
                boolean disconnectConnect = false;
                // Enable buttons and disable listView
                listen.setEnabled(true);
                connect.setEnabled(true);
                listView.setVisibility(ListView.GONE);
                // Close the bluetooth socket
                if (listenAttempt) {
                    disconnectListen = listenThread.closeConnect();
                    listenAttempt = false;
                }
                if (connectAttempt) {
                    disconnectConnect = connectThread.closeConnect();
                    connectAttempt = false;
                }

                audioClient.destroyProcesses();

                Log.d("BLUETOOTH", "Disconnect");

                if (disconnectListen || disconnectConnect) {
                    // Disconnect successful - Handle UI element change
                    audio.setVisibility(View.GONE);
                    listen.setEnabled(true);
                    connect.setEnabled(true);
                } else {
                    // Unsuccessful disconnect - Do nothing
                }
            }
        });

        // Attempt to connect when paired device is clicked in ListView
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Log.d("BLUETOOTH", "pos: " + position + " id: " + id + " device: " + deviceList.get(position).getName());

                // Get the MAC address of the device you want to connect to
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceList.get(position).getAddress());
                // Connect to the device
                boolean connectSuccess = connectThread.connect(device, MY_UUID);
                Log.d("BLUETOOTH", "Attempting to connect");

                // Toast notification on status.
                if (connectSuccess) {
                    // Handle socket objects
                    bSocket = connectThread.getSocket();
                    // Start listening for audio from other device
                    audioClient.audioCreate();
                    audioClient.setSocket(bSocket);
                    audioClient.setupStreams();
                    audioClient.startPlaying();
                    // Change status of UI elements
                    Toast.makeText(MainActivity.this, "Connection was successful", Toast.LENGTH_LONG).show();
                    listView.setVisibility(ListView.GONE);
                    audio.setVisibility(View.VISIBLE);
                    listen.setEnabled(false);
                    connect.setEnabled(false);
                    connectAttempt = true;

                } else {
                    // Change status of UI elements if connection was unsuccessful
                    Toast.makeText(MainActivity.this, "Connection was unsuccessful", Toast.LENGTH_LONG).show();
                    connect.setEnabled(true);
                    listView.setVisibility(ListView.GONE);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                // Permission granted
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();
    }
}
