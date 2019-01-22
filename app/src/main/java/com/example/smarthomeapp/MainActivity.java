package com.example.smarthomeapp;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    // Used for debugging
    private static final String DEBUG_TAG = "DEBUGGING";

    private Button kitchenLightButton;
    private Button bedroomLightButton;
    private Button livingroomLightButton;
    private TextView temperatureTextView;
    private TextView windowsTextView;
    private TextView alarmTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(DEBUG_TAG, "onCreate started");

        // Obtain the GUI components
        kitchenLightButton = (Button) findViewById(R.id.kitchenLightButton);
        bedroomLightButton = (Button) findViewById(R.id.bedroomLightButton);
        livingroomLightButton = (Button) findViewById(R.id.livingroomLightButton);
        temperatureTextView = (TextView) findViewById(R.id.temperatureTextView);
        windowsTextView = (TextView) findViewById(R.id.windowsTextView);
        alarmTextView = (TextView) findViewById(R.id.alarmTextView);

        // Set the GUI listeners
        kitchenLightButton.setOnClickListener(kitchenLightButtonOnClickListener);
        bedroomLightButton.setOnClickListener(bedroomLightButtonOnClickListener);
        livingroomLightButton.setOnClickListener(livingroomLightButtonOnClickListener);

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
