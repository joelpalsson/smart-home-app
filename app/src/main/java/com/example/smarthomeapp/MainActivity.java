package com.example.smarthomeapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // Used for debugging
    private static final String DEBUG_TAG = "DEBUGGING";
    // Used for identifying shared types between calling functions
    private static final int REQUEST_ENABLE_BT = 1;
    // Bluetooth uuid, used to determine which channel to connect to
    private static final UUID BT_MODULE_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter mBtAdapter;
    private BluetoothDevice mBtDevice;
    private InputStream mInputstream;
    private OutputStream mOutputStream;

    TextView mTemperatureTextView;
    TextView mWindowsTextView;
    TextView mAlarmTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(DEBUG_TAG, "OnCreate method started");

        // Obtain the GUI components
        Button kitchenLightButton = (Button) findViewById(R.id.kitchenLightButton);
        Button bedroomLightButton = (Button) findViewById(R.id.bedroomLightButton);
        Button livingroomLightButton = (Button) findViewById(R.id.livingroomLightButton);
        mTemperatureTextView = (TextView) findViewById(R.id.temperatureTextView);
        mWindowsTextView = (TextView) findViewById(R.id.windowsTextView);
        mAlarmTextView = (TextView) findViewById(R.id.alarmTextView);

        // Obtain the device's Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // Check if the device supports Bluetooth
        if (mBtAdapter == null) {
            String msg = "This device does not support Bluetooth";
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check Bluetooth is enabled. If disabled, enable it.
        if (!mBtAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Set the GUI listeners
        kitchenLightButton.setOnClickListener(kitchenLightButtonOnClickListener);
        bedroomLightButton.setOnClickListener(bedroomLightButtonOnClickListener);
        livingroomLightButton.setOnClickListener(livingroomLightButtonOnClickListener);

        // Connect to the
        String address = "98:D3:32:70:D2:ED"; // The Arduino MAC address
        mBtDevice = mBtAdapter.getRemoteDevice(address);

        // To avoid blocking the GUI thread, start a new thread to handle the connection
        new Thread() {
            @Override
            public void run() {
                // Obtain a socket
                BluetoothSocket socket;
                try {
                    socket = mBtDevice.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
                    Log.d(DEBUG_TAG, "Socket created");
                } catch (IOException e) {
                    Log.d(DEBUG_TAG, "Socket creation failed");
                    return;
                }

                // Before connecting, make sure that there is no active device discovery process
                mBtAdapter.cancelDiscovery();

                // Obtain a connection
                try {
                    socket.connect();
                    Log.d(DEBUG_TAG, "Socket connected");
                } catch (IOException e1) {
                    Log.d(DEBUG_TAG, "Connecting to the remote device failed" +
                            " - Check if the device is on!");
                    try {
                        socket.close();
                        Log.d(DEBUG_TAG, "Socket closed");
                    } catch (IOException e2) {
                        Log.d(DEBUG_TAG, "Closing the client socket failed");
                    }
                    return;
                }

                // Obtain the input and output streams
                try {
                    mInputstream = socket.getInputStream();
                    mOutputStream = socket.getOutputStream();
                    Log.d(DEBUG_TAG, "Streams obtained");
                } catch (IOException e) {
                    Log.d(DEBUG_TAG, "Obtaining the streams failed");
                    return;
                }

                // Create a buffer to fill with incoming data
                byte[] buffer = new byte[1024];
                int numBytes;

                // Start transferring data
                while (true) {
                    try {
                        numBytes = mInputstream.available();
                        if (numBytes > 0) {
                            // Wait for all data to arrive
                            SystemClock.sleep(100);
                            numBytes = mInputstream.available();
                            mInputstream.read(buffer);
                            Log.d(DEBUG_TAG, Integer.toString(numBytes) +
                                    " bytes received: " + Byte.toString(buffer[0]) + ":" +
                                    Byte.toString(buffer[1]) + ":" + Byte.toString(buffer[2]));
                        }
                    } catch (IOException e) {
                        Log.d(DEBUG_TAG, "Reading the input stream failed");
                        break;
                    }
                }
            }
        }.start();
    }

    private View.OnClickListener kitchenLightButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

        }
    };

    private View.OnClickListener bedroomLightButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

        }
    };

    private View.OnClickListener livingroomLightButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

        }
    };
}
