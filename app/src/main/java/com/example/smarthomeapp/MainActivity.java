package com.example.smarthomeapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // Used for debugging
    private static final String DEBUG_TAG = "DEBUGGING";
    // Used for identifying shared types between calling functions
    private static final int REQUEST_ENABLE_BT = 1;
    // Used by the main thread Handler to distinguish Messages containing a command from the Arduino
    private static final int COMMAND_RECEIVED = 1;
    // Used by the main thread Handler to distinguish Messages containing connection status
    private static final int CONNECTION_STATUS = 2;
    // Used by the main thread Handler to distinguish Messages containing user feedback
    private static final int USER_FEEDBACK = 3;
    // Used when requesting audio recording permissions
    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
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

    private SpeechRecognizer speechRecognizer;
    private Vibrator vibrator;

    private TextView mConnectionStatusTextView;
    private TextView mTemperatureTextView;
    private TextView mWindowsTextView;
    private TextView mAlarmTextView;

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
        Button voiceControlButton = (Button) findViewById(R.id.voiceControlButton);
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

        // Check if the Android API level is equal to or greater than 23 (Marshmallow). If so, ask
        // the user for necessary audio recording permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestRecAudioPerm();
        }

        // Set the GUI listeners
        connectButton.setOnClickListener(connectButtonOnClickListener);
        kitchenLightButton.setOnClickListener(kitchenLightButtonOnClickListener);
        bedroomLightButton.setOnClickListener(bedroomLightButtonOnClickListener);
        livingroomLightButton.setOnClickListener(livingroomLightButtonOnClickListener);
        voiceControlButton.setOnClickListener(voiceControlButtonOnClickListener);

        // Obtain the paired Arduino device
        mBtDevice = mBtAdapter.getRemoteDevice(ARDUINO_MAC);

        // Prepare for speech recognition
        boolean supported = SpeechRecognizer.isRecognitionAvailable(this);
        Log.d(DEBUG_TAG, "speech regignition supported: " + Boolean.toString(supported));
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(commandRecognitionListener);

        // Obtain a Vibrator for interacting with the device's vibration hardware
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
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

    private View.OnClickListener voiceControlButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            recordSpeech();
        }
    };

    private RecognitionListener commandRecognitionListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            String feedback = "In which room do you want to toggle the light?";
            Toast.makeText(getApplicationContext(), feedback, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onBeginningOfSpeech() {

        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {

        }

        @Override
        public void onEndOfSpeech() {

        }

        @Override
        public void onError(int error) {
            Log.d(DEBUG_TAG, "error: " + error);
        }

        @Override
        public void onResults(Bundle results) {
            Log.d(DEBUG_TAG, "Result obtained!");
            ArrayList<String> result = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);

            for (String suggestion : result) {
                Log.d(DEBUG_TAG, "result: " + suggestion);
                for (String word : suggestion.split(" ")) {
                    Log.d(DEBUG_TAG, word);
                    switch (word.toLowerCase()) {
                        case "kitchen":
                            sendCommand("1");
                            return;
                        case "bedroom":
                            sendCommand("2");
                            return;
                        case "living room":
                            sendCommand("3");
                            return;
                    }
                }
            }
            String feedback = "Unfortunately it is not possible to control the light in the " +
                    "mentioned room. Please press the button and try again!";
            Toast.makeText(getApplicationContext(), feedback, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {

        }

        @Override
        public void onEvent(int eventType, Bundle params) {

        }
    };

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case COMMAND_RECEIVED:
                    byte[] data = (byte[]) msg.obj;
                    int numBytes = msg.arg1;
                    int byteIndex = 0;
                    while (byteIndex < numBytes) {
                        int key = (int) data[byteIndex];
                        switch (key) {
                            case 1:
                                // Temperature received
                                int temperature = (int) data[byteIndex + 1];
                                mTemperatureTextView.setText(
                                        Integer.toString(temperature) + " \u00b0C");
                                break;
                            case 2:
                                // Windows status received
                                vibrate(300);
                                int windowStatus = (int) data[byteIndex + 1];
                                if (windowStatus == 0) {
                                    mWindowsTextView.setText("Closed");
                                } else if (windowStatus == 1) {
                                    mWindowsTextView.setText("Open");
                                }
                                break;
                            case 3:
                                // Alarm status received
                                vibrate(300);
                                int alarmStatus = (int) data[byteIndex + 1];
                                if (alarmStatus == 0) {
                                    mAlarmTextView.setText("Off");
                                } else if (alarmStatus == 1) {
                                    mAlarmTextView.setText("On");
                                }
                        }
                        byteIndex = byteIndex + 2;
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
            String connectionStatus = "Connected";
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

            // Request sensor data from the Arduino
            sendCommand("0");

            // Start transferring data
            while (true) {
                try {
                    numBytes = mInputStream.available();
                    if (numBytes > 0) {
                        // Wait for all data to arrive
                        SystemClock.sleep(50);
                        numBytes = mInputStream.read(buffer);
                        String data = Integer.toString(numBytes) + " bytes received: ";
                        for (int i = 0; i < numBytes; i++) {
                            data += Byte.toString(buffer[i]) + ":";
                        }
                        Log.d(DEBUG_TAG, data);
                        msg = mHandler.obtainMessage(COMMAND_RECEIVED, numBytes, 0, buffer);
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

    private void requestRecAudioPerm() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

    private void recordSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizer.startListening(intent);
    }

    private void vibrate(int vibrationTime) {
        // Check if the Android API level is equal to or greater than 26 (Oreo). If so, use this
        // method instead of the one below, since that one is deprecated.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(vibrationTime,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(vibrationTime);
        }
    }
}
