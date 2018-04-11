package com.montefiore.gaulthiergain.walkietalkie;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
import com.montefiore.gaulthiergain.adhoclibrary.datalink.exceptions.BluetoothBadDuration;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.exceptions.DeviceException;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.service.AdHocDevice;
import com.montefiore.gaulthiergain.adhoclibrary.network.exceptions.DeviceAlreadyConnectedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // Requesting permission to RECORD_AUDIO
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    public static final String TAG = "[TalkieWalkie][Main]";
    private boolean permissionToRecordAccepted = false;

    // Define UI elements
    private Button btnAudio;
    private Button btnConnect;
    private ListView listView;

    private ArrayList<ListDevices> deviceList;
    private ArrayAdapter<ListDevices> adapter;

    private AudioClients audioClients;
    private TransferManager transferManager;

    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {


        MenuItem menuItem = menu.findItem(R.id.action_state);
        if (transferManager != null) {
            if (transferManager.isBluetoothEnable()) {
                menuItem.setTitle(R.string.disable);
            } else {
                menuItem.setTitle(R.string.enable);
            }
        } else {
            menuItem.setTitle(R.string.disable);
        }

        menuItem = menu.findItem(R.id.action_disconnect);
        if (audioClients != null) {
            if (audioClients.getNbClients() == 0) {
                menuItem.setEnabled(false);
            } else {
                menuItem.setEnabled(true);
            }
        } else {
            menuItem.setEnabled(false);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    // Initialization of layout
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        transferManager = new TransferManager(true, new ListenerApp() {

            @Override
            public void onReceivedData(AdHocDevice adHocDevice, Object pdu) {
                Log.d(TAG, "Receive from " + adHocDevice.toString());
                audioClients.setData((byte[]) pdu);
            }

            @Override
            public void onConnectionClosed(AdHocDevice adHocDevice) {
                Toast.makeText(MainActivity.this, "Disconnect with " + adHocDevice.getLabel(),
                        Toast.LENGTH_LONG).show();

                Log.d(TAG, "Disconnect with " + adHocDevice.toString());

                audioClients.clientDisconnect();

                audioClients.disconnect(adHocDevice);
                if (audioClients.getNbClients() == 0) {
                    // Enable buttons and disable listView
                    btnConnect.setEnabled(true);
                    listView.setVisibility(ListView.GONE);
                    audioClients.destroyProcesses();

                    // Handle UI element change
                    btnAudio.setVisibility(View.GONE);
                    btnConnect.setEnabled(true);
                }
            }

            @Override
            public void onConnectionClosedFailed(Exception e) {
                e.printStackTrace();
            }

            @Override
            public void processMsgException(Exception e) {
                e.printStackTrace();
            }

            @Override
            public void onConnection(AdHocDevice adHocDevice) {

                Toast.makeText(MainActivity.this, "Connection was successful with " +
                        adHocDevice.getLabel(), Toast.LENGTH_LONG).show();

                Log.d(TAG, "Connection with " + adHocDevice.toString());

                audioClients.addRemoteAddr(adHocDevice);
                if (audioClients.getNbClients() == 0) {

                    // Start listening for btnAudio from other device
                    audioClients.audioCreate();
                    audioClients.startPlaying();
                    btnAudio.setVisibility(View.VISIBLE);
                    listView.setVisibility(ListView.GONE);
                    btnConnect.setEnabled(false);
                }

                audioClients.clientConnect();
            }

            @Override
            public void onConnectionFailed(Exception e) {
                e.printStackTrace();
                // Change status of UI elements if connection was unsuccessful
                Toast.makeText(MainActivity.this, "Connection was unsuccessful", Toast.LENGTH_LONG).show();
                listView.setVisibility(ListView.GONE);
                btnConnect.setEnabled(true);
            }
        });

        try {
            transferManager.getConfig().setJson(false);
            transferManager.getConfig().setReliableTransportWifi(false);
            transferManager.start(getApplicationContext());
            Log.d(TAG, transferManager.getConfig().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        listView = findViewById(R.id.listViewItems);
        btnConnect = findViewById(R.id.connect);
        btnAudio = findViewById(R.id.audioBtn);

        audioClients = new AudioClients(transferManager);

        // Disable microphone button
        btnAudio.setVisibility(View.GONE);

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
                        deviceList.add(new ListDevices(entry.getValue()));
                    }
                } else {
                    Log.d(TAG, "No paired devices found");
                }

                // No devices found
                if (deviceList.size() == 0) {
                    deviceList.add(new ListDevices("No devices found", ""));
                }

                // Populate List view with device information
                adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, deviceList);
                listView.setAdapter(adapter);
            }
        });

        if (transferManager.isWifiEnable()) {
            try {
                transferManager.disableWifi();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_state:
                updateBluetoothState(item);
                return true;
            case R.id.action_disconnect:
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
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateBluetoothState(final MenuItem item) {
        if (!transferManager.isBluetoothEnable()) {
            try {
                transferManager.enableBluetooth(0, getApplicationContext(), new ListenerAdapter() {
                    @Override
                    public void onEnableBluetooth(boolean success) {
                        if (success) {
                            Log.d(TAG, "Bluetooth is enabled");
                            item.setTitle(R.string.disable);
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
            } catch (BluetoothBadDuration e) {
                e.printStackTrace();
            }

        } else {
            try {
                transferManager.disableBluetooth();
                item.setTitle(R.string.enable);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
    protected void onStop() {
        try {
            transferManager.stopListening();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onStop();
    }
}