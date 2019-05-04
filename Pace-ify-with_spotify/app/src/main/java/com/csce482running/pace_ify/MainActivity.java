package com.csce482running.pace_ify;

import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import java.lang.String;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;


public class MainActivity extends AppCompatActivity {

    public static Bundle userTargets = new Bundle();



    public void startRun(View view) {

        // set up user input fields
        EditText textTargetDistance = findViewById(R.id.start_targetDistance);
        EditText textTargetPace = findViewById(R.id.start_targetPace);
        Switch switchHFS = findViewById(R.id.haptic_switch);
        Switch switchAFS = findViewById(R.id.aural_switch);

        // input field
        if (textTargetDistance.getText().toString().isEmpty() || textTargetPace.getText().toString().isEmpty()) {
            Toast.makeText(getApplicationContext(), "Please input target pace and distance!",
                    Toast.LENGTH_SHORT).show();
        }
        else {
            // bundle user-input fields
            MainActivity.userTargets.putString("userTargetPace", textTargetPace.getText().toString());
            MainActivity.userTargets.putString("userTargetDistance", textTargetDistance.getText().toString());
            MainActivity.userTargets.putBoolean("userTargetHaptic", switchHFS.isChecked());
            MainActivity.userTargets.putBoolean("userTargetAural", switchAFS.isChecked());

            // log inputs
            Log.i("targetPace", textTargetPace.getText().toString());
            Log.i("targetDistance", textTargetDistance.getText().toString());
            Log.i("useHaptic", String.valueOf(switchHFS.isChecked()));
            Log.i("useAural", String.valueOf(switchAFS.isChecked()));

            // send to next activity
            Intent intent = new Intent(this, nextActivity.class);
            startActivity(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // animated background
        ConstraintLayout constraintLayout = findViewById(R.id.layout);
        AnimationDrawable animationDrawable = (AnimationDrawable) constraintLayout.getBackground();
        animationDrawable.setEnterFadeDuration(2000);
        animationDrawable.setExitFadeDuration(4000);
        animationDrawable.start();
    }




}
