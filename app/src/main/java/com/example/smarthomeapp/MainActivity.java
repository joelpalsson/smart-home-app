package com.example.smarthomeapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
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
    // Used by the main thread Handler to distinguish Messages containing a command from the Arduino
    private static final int COMMAND_RECEIVED = 2;
    // Used by the main thread Handler to distinguish Messages containing connection status
    private static final int CONNECTION_STATUS = 3;
    // Used by the main thread Handler to distinguish Messages containing user feedback
    private static final int USER_FEEDBACK = 4;
    // Bluetooth uuid, used to determine which channel to connect to
    private static final UUID BT_MODULE_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // The Arduino MAC address
    private static final String ARDUINO_MAC = "98:D3:32:70:D2:ED";

    private BluetoothAdapter mBtAdapter;
    private BluetoothDevice mBtDevice;
    private BluetoothSocket mBtSocket;
    private InputStream mInputStream;
    private OutputStream mOutputStream;

    TextView mConnectionStatusTextView;
    TextView mTemperatureTextView;
    TextView mWindowsTextView;
    TextView mAlarmTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(DEBUG_TAG, "OnCreate method started");

        // Obtain the GUI components
        Button connectButton = (Button) findViewById(R.id.connectButton);
        Button kitchenLightButton = (Button) findViewById(R.id.kitchenLightButton);
        Button bedroomLightButton = (Button) findViewById(R.id.bedroomLightButton);
        Button livingroomLightButton = (Button) findViewById(R.id.livingroomLightButton);
        mConnectionStatusTextView = (TextView) findViewById(R.id.connectionStatusTextView);
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
        connectButton.setOnClickListener(connectButtonOnClickListener);
        kitchenLightButton.setOnClickListener(kitchenLightButtonOnClickListener);
        bedroomLightButton.setOnClickListener(bedroomLightButtonOnClickListener);
        livingroomLightButton.setOnClickListener(livingroomLightButtonOnClickListener);

        // Obtain the paired Arduino device
        mBtDevice = mBtAdapter.getRemoteDevice(ARDUINO_MAC);
    }

    // To avoid blocking the GUI thread, start a new thread to handle the connection
    private View.OnClickListener connectButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mBtSocket == null || !mBtSocket.isConnected()) {
                networkThread.start();
            } else {
                String feedback = "Already connected!";
                Message msg = mHandler.obtainMessage(USER_FEEDBACK, feedback);
                msg.sendToTarget();
            }
        }
    };

    private View.OnClickListener kitchenLightButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            sendCommand("1");
        }
    };

    private View.OnClickListener bedroomLightButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            sendCommand("2");
        }
    };

    private View.OnClickListener livingroomLightButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            sendCommand("3");
        }
    };

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case COMMAND_RECEIVED:
                    byte[] data = (byte[]) msg.obj;

                    // Update the temperature
                    int temperature = (int) data[0];
                    mTemperatureTextView.setText(Integer.toString(temperature) + " \u00b0C");

                    // Update the windows status
                    int windowStatus = (int) data[1];
                    if (windowStatus == 0) {
                        mWindowsTextView.setText("Closed");
                    } else if (windowStatus == 1) {
                        mWindowsTextView.setText("Open");
                    }

                    // Update the alarm status
                    int alarmStatus = (int) data[2];
                    if (alarmStatus == 0) {
                        mAlarmTextView.setText("Off");
                    } else if (alarmStatus == 1) {
                        mAlarmTextView.setText("On");
                    }
                    break;

                case CONNECTION_STATUS:
                    String connectionStatus = (String) msg.obj;
                    mConnectionStatusTextView.setText(connectionStatus);
                    break;

                case USER_FEEDBACK:
                    String feedback = (String) msg.obj;
                    Toast.makeText(getApplicationContext(), feedback, Toast.LENGTH_SHORT).show();
            }
        }
    };

    private Thread networkThread = new Thread() {
        Message msg;
        String feedback;

        @Override
        public void run() {
            feedback = "Connecting...";
            msg = mHandler.obtainMessage(USER_FEEDBACK, feedback);
            msg.sendToTarget();

            // Obtain a Bluetooth socket
            try {
                mBtSocket = mBtDevice.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
                Log.d(DEBUG_TAG, "Socket created");
            } catch (IOException e) {
                Log.d(DEBUG_TAG, "Socket creation failed");
                return;
            }

            // Before connecting, make sure that there is no active device discovery process
            mBtAdapter.cancelDiscovery();

            // Obtain a connection to the Arduino
            try {
                mBtSocket.connect();
                Log.d(DEBUG_TAG, "Socket connected");
            } catch (IOException e1) {
                Log.d(DEBUG_TAG, "Connecting to the remote device failed");
                // Connecting to Arduino failed - provide user feedback
                feedback = "Connecting to the network failed - Check if the Arduino device " +
                        "is turned on!";
                msg = mHandler.obtainMessage(USER_FEEDBACK, feedback);
                msg.sendToTarget();
                try {
                    mBtSocket.close();
                    Log.d(DEBUG_TAG, "Socket closed");
                } catch (IOException e2) {
                    Log.d(DEBUG_TAG, "Closing the client socket failed");
                }
                return;
            }

            // Connected to the Arduino - update connection status and provide user feedback
            feedback = "Connected to network!";
            msg = mHandler.obtainMessage(USER_FEEDBACK, feedback);
            msg.sendToTarget();
            String connectionStatus = "connected";
            msg = mHandler.obtainMessage(CONNECTION_STATUS, connectionStatus);
            msg.sendToTarget();

            // Obtain the input and output streams
            try {
                mInputStream = mBtSocket.getInputStream();
                mOutputStream = mBtSocket.getOutputStream();
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
                    numBytes = mInputStream.available();
                    if (numBytes > 0) {
                        // Wait for all data to arrive
                        SystemClock.sleep(100);
                        numBytes = mInputStream.available();
                        mInputStream.read(buffer);
                        Log.d(DEBUG_TAG, Integer.toString(numBytes) +
                                " bytes received: " + Byte.toString(buffer[0]) + ":" +
                                Byte.toString(buffer[1]) + ":" + Byte.toString(buffer[2]));
                        msg = mHandler.obtainMessage(COMMAND_RECEIVED, buffer);
                        msg.sendToTarget();
                    }
                } catch (IOException e) {
                    Log.d(DEBUG_TAG, "Reading the input stream failed");
                    break;
                }
            }
        }
    };

    private void sendCommand(String command) {
        if (mOutputStream != null) {
            try {
                Log.d(DEBUG_TAG, "Command sent: " + command);
                byte[] bytesToSend = command.getBytes("UTF-8");
                mOutputStream.write(bytesToSend);
            } catch (IOException e) {
                Log.d(DEBUG_TAG, "Writing to the output stream failed");
            }
        }
    }

}
