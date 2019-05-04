package com.csce482running.pace_ify;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import android.location.Location;
import android.graphics.drawable.AnimationDrawable;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.view.View;
import android.content.Context;
import android.os.Vibrator;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Arrays;

public class nextActivity extends AppCompatActivity
        implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener, LocationListener {

    // Spotify credentials
    private static final String CLIENT_ID = "9b5f8f9feab543069a0bbcc83126c617";
    private static final String REDIRECT_URI = "https://Pace-ify-master/callback/";
    private SpotifyAppRemote mSpotifyAppRemote;

    // pacing feedback systems enabled
    private static final boolean HAPTIC_FEEDBACK_SYSTEM =
            MainActivity.userTargets.getBoolean("userTargetHaptic");
    private static final boolean AURAL_FEEDBACK_SYSTEM =
            MainActivity.userTargets.getBoolean("userTargetAural");

    // user target pace and distance
    private static final double TARGET_PACE = Double.parseDouble(
            MainActivity.userTargets.getString("userTargetPace"));
    private static final double TARGET_DISTANCE = Double.parseDouble(
            MainActivity.userTargets.getString("userTargetDistance"));

    // metric conversion constants
    private static final double METERS_IN_MILE = 1609.334;
    private static final double MS_TO_MPH_CONVERSION = 2.237;

    // gps accuracy threshold and update intervals
    // private static final float ACCURACY_THRESHOLD = 10;
    private static final long UPDATE_INTERVAL = 500, FASTEST_INTERVAL = 500;

    // haptic feedback update interval and accuracy threshold (seconds)
    private static final long HAPTIC_FEEDBACK_INTERVAL = 10;
    private static final int HAPTIC_FEEDBACK_THRESHOLD = 5;
    private static final int AURAL_FEEDBACK_INTERVAL = 60;

    // google play services
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    // vibrator and vibrate patterns for haptic feedback
    private static Vibrator HAPTICS;
    private static final long[] FAST_PATTERN = {0, 300, 350, 300, 350, 300};
    private static final long[] SLOW_PATTERN = {0, 300, 1000, 300, 1000, 300};

    // current distance (in miles) and pace (in {minutes, secs})
    private static float currentDistance = 0;
    private static int[] currentPace = {0, 0};

    // list of running coordinates
    private static ArrayList<Location> coordinateList = new ArrayList<>();

    // GUI elements
    private static ListView runningStatsListView;
    private static ArrayList<String> runningStatsList;
    private static ArrayAdapter<String> arrayAdapter;

    // final runningStatsList index values
    private static final int TIME_ELAPSED_INDEX = 0;
    private static final int CURRENT_PACE_INDEX = 1;
    private static final int TARGET_PACE_INDEX = 2;
    private static final int CURRENT_DISTANCE_INDEX = 3;
    private static final int TARGET_DISTANCE_INDEX = 4;
    private static final int LAST_DISTANCE_INDEX = 5;
    private static final int CURRENT_SPEED_INDEX = 6;
    private static final int CURRENT_ACCURACY_INDEX = 7;

    // locations
    private static Location location;
    private static Location lastLocation;

    // google fused location api client
    private static GoogleApiClient googleApiClient;

    // lists for permissions
    private static ArrayList<String> permissionsToRequest;
    private static ArrayList<String> permissionsRejected = new ArrayList<>();
    private static ArrayList<String> permissions = new ArrayList<>();

    // integer for permissions results request
    private static final int ALL_PERMISSIONS_RESULT = 1011;

    // clock timer
    private static int milliseconds, seconds, minutes, hours = 0;
    private static long currentElapsedTime = 0;
    private static long startTime = 0;

    // stop run button
    private Button buttonStopRun;

    // stop run button handler
    public void stopRun(View view) {
        view.setVisibility(View.INVISIBLE);
        onStop();
        onPause();
    }

    protected void onStop(){
        super.onStop();
        mSpotifyAppRemote.getPlayerApi().pause();
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_next);
 
        // log start
        Log.i("Status", "Starting Pace-ify");
        Log.i("HFS", String.valueOf(HAPTIC_FEEDBACK_SYSTEM));
        Log.i("AFS", String.valueOf(AURAL_FEEDBACK_SYSTEM));

        // start timer
        startTime = System.currentTimeMillis();

        // add permissions so device location can be obtained
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        // request permissions
        permissionsToRequest = permissionsToRequest(permissions);

        // add permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permissionsToRequest.size() > 0) {
                requestPermissions(permissionsToRequest.
                        toArray(new String[permissionsToRequest.size()]), ALL_PERMISSIONS_RESULT);
            }
        }

        // Build the Google API Client
        googleApiClient = new GoogleApiClient.Builder(this).
                addApi(LocationServices.API).
                addConnectionCallbacks(this).
                addOnConnectionFailedListener(this).build();

        // set up running stats list for GUI
        runningStatsListView = findViewById(R.id.runningStats);
        runningStatsList = new ArrayList<>(Arrays.asList(
                "Time Elapsed: ",
                "Current Mile Pace: ",
                "Target Mile Pace: ",
                "Current Distance Traveled: ",
                "Target Distance Traveled: ",
                "Last Point Distance: ",
                "Speed: ",
                "Accuracy: "
        ));
        arrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, runningStatsList);

        // set up haptics
        HAPTICS = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // set up stop run button
        buttonStopRun = findViewById(R.id.stopRunning);

        // set up ConstraintLayout for GUI background
        ConstraintLayout constraintLayout = findViewById(R.id.layout);
        AnimationDrawable animationDrawable = (AnimationDrawable) constraintLayout.getBackground();
        animationDrawable.setEnterFadeDuration(2000);
        animationDrawable.setExitFadeDuration(4000);
        animationDrawable.start();
    }

    // timer for displaying elapsed time
    public Runnable timer = new Runnable() {
        public void run() {
            long millisecondTime = System.currentTimeMillis() - startTime;
            milliseconds = (int) millisecondTime % 1000;
            seconds = (int) (millisecondTime / 1000);
            currentElapsedTime = seconds;
            minutes = seconds / 60;
            seconds = seconds % 60;
            hours = minutes / 60;
            runningStatsList.set(TIME_ELAPSED_INDEX, "Time Elapsed: " + hours + ":" +
                    String.format(Locale.US,"%02d", minutes) + ":" +
                    String.format(Locale.US,"%02d", seconds) + ":" +
                    String.format(Locale.US,"%03d", milliseconds)
            );
        }
    };

    @Override
    public void onConnectionSuspended(int i) {
        // ...
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // ...
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        int targetSeconds = (int)((TARGET_PACE * 60) % 60);
        int targetMinutes = (int)Math.floor(TARGET_PACE);

        // get last location
        getLastLocation();

        if (location != null) {
            runningStatsList.set(TIME_ELAPSED_INDEX, "Time Elapsed: " + hours + ":" +
                    String.format(Locale.US,"%02d", minutes) + ":" +
                    String.format(Locale.US,"%02d", seconds) + ":" +
                    String.format(Locale.US,"%03d", milliseconds)
            );
            runningStatsList.set(CURRENT_PACE_INDEX, "Current Mile Pace: Calibrating...");
            runningStatsList.set(TARGET_PACE_INDEX, "Target Mile Pace: " +
                    String.format(Locale.US, "%02d", targetMinutes) + ":" +
                    String.format(Locale.US, "%02d", targetSeconds)
            );
            runningStatsList.set(CURRENT_DISTANCE_INDEX, "Current Distance: Calibrating...");
            runningStatsList.set(TARGET_DISTANCE_INDEX, "Target Distance: " +
                    String.format(Locale.US, "%.3f", TARGET_DISTANCE) + " miles"
            );
            // extra debug metrics
            runningStatsList.set(LAST_DISTANCE_INDEX, "Last Point Distance: Calibrating...");
            runningStatsList.set(CURRENT_SPEED_INDEX, "Speed: Calibrating...");
            runningStatsList.set(CURRENT_ACCURACY_INDEX, "Accuracy: Calibrating...");

            // update
            runningStatsListView.setAdapter(arrayAdapter);
            coordinateList.add(location);
            lastLocation = location;
        }

        // start receiving location updates
        startLocationUpdates();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (googleApiClient != null) {
            googleApiClient.connect();
        }


        //Spotify Connection
        ConnectionParams connectionParams =
                new ConnectionParams.Builder(CLIENT_ID)
                        .setRedirectUri(REDIRECT_URI)
                        .showAuthView(true)
                        .build();

        SpotifyAppRemote.connect(this, connectionParams,
                new Connector.ConnectionListener() {

                    @Override
                    public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                        mSpotifyAppRemote = spotifyAppRemote;
                        Log.d("MainActivity", "Connected! Yay!");
                        //Toast.makeText(getApplicationContext(),"connected",Toast.LENGTH_LONG).show();
                        // Now you can start interacting with App Remote
                        connected();

                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        Log.e("MainActivity", throwable.getMessage(), throwable);


                        Toast.makeText(getApplicationContext(), throwable.getMessage(),
                                Toast.LENGTH_LONG).show();
                        // Something went wrong when attempting to connect! Handle errors here
                    }
                });


    }

    private void connected(){
        //spotify testing
        mSpotifyAppRemote.getPlayerApi().setShuffle(true);
        mSpotifyAppRemote.getPlayerApi().play("spotify:playlist:37i9dQZF1DXadOVCgGhS7j");

//                });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // check if google play services is installed
        if (!checkPlayServices()) {
            runningStatsList.set(TIME_ELAPSED_INDEX, "Install Google Play Services to use this app");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // stop location updates
        if (googleApiClient != null && googleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
            googleApiClient.disconnect();
        }
    }

    // gets current location from FusedLocationAPI
    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                &&  ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Permissions ok, we get last location
        location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
    }

    // start updating location coordinates
    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                &&  ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,
                    "You need to enable permissions to display location!",
                        Toast.LENGTH_SHORT).show();
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    // calculate running pace
    private int[] calculateRunningPace(double time, double distance) {

        // {minutes, seconds}
        int[] calculatedPace = {0, 0};

        // calculate pace in terms of minutes and seconds
        if (distance != 0) {
            double minutesPerMile = ((time / distance) / 60); // (360s / 1.25m) / 60s) = 4.8 m/mi
            int currentPaceMinutes = ((int)Math.floor(minutesPerMile)); // floor(4.8) = 4m
            int currentPaceSeconds = (int)((minutesPerMile % 1) * 60); // ((4.8 % 1) * 60) = 48s

            // update minutes and seconds
            calculatedPace[0] = currentPaceMinutes;
            calculatedPace[1] = currentPaceSeconds;
        }
        return calculatedPace;
    }

    // updates running metrics
    private void updateRunningMetrics(@NonNull Location location, @NonNull ArrayList<String> runningMetricsList) {
        runningMetricsList.set(TIME_ELAPSED_INDEX, "Time Elapsed: " + hours + ":" +
                String.format(Locale.US,"%02d", minutes) + ":" +
                String.format(Locale.US,"%02d", seconds) + ":" +
                String.format(Locale.US,"%03d", milliseconds)
        );
        runningMetricsList.set(CURRENT_PACE_INDEX, "Current Mile Pace: " +
                String.format(Locale.US,"%02d", currentPace[0]) + ":" +
                String.format(Locale.US,"%02d", currentPace[1])
        );
        runningMetricsList.set(CURRENT_DISTANCE_INDEX, "Current Distance: " +
                String.format(Locale.US,"%.3f", currentDistance) + " miles"
        );

        // extra debug metrics
        runningMetricsList.set(LAST_DISTANCE_INDEX, "Last Point Distance: " +
                String.format(Locale.US, "%.3f", location.distanceTo(coordinateList.get(coordinateList.size()-1))) + "m"
        );
        runningMetricsList.set(CURRENT_SPEED_INDEX, "Speed: " +
                String.format(Locale.US, "%.3f", location.getSpeed() * MS_TO_MPH_CONVERSION) + " mph"
        );
        runningMetricsList.set(CURRENT_ACCURACY_INDEX, "Accuracy: " +
                String.format(Locale.US, "%.3f", location.getAccuracy()) + "m Radius"
        );
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {

            // calculate current distance in miles
            currentDistance += (location.distanceTo(lastLocation) / METERS_IN_MILE);

            // get pace in terms of minutes and seconds
            currentPace = calculateRunningPace(currentElapsedTime, currentDistance);

            // update running metrics
            updateRunningMetrics(location, runningStatsList);
            runningStatsListView.setAdapter(arrayAdapter);

            // update location and timer
            coordinateList.add(location);
            lastLocation = location;
            timer.run();

            // log current time
            Log.i("currentElapsedTime", String.valueOf(currentElapsedTime));

            // if using HFS
            if (HAPTIC_FEEDBACK_SYSTEM) {

                // give user haptic feedback every feedback interval
                if (currentElapsedTime != 0 && (currentElapsedTime % HAPTIC_FEEDBACK_INTERVAL) == 0) {

                    // convert current and target user pace in seconds/mile
                    int currentPaceInSeconds = ((currentPace[0] * 60) + currentPace[1]);
                    int currentTargetPaceInSeconds = (int) (TARGET_PACE * 60);
                    Log.i("currentPace", String.valueOf(currentPaceInSeconds));
                    Log.i("currentTargetPace", String.valueOf(currentTargetPaceInSeconds));

                    // fast state (prompt user to slow down if below threshold)
                    if (currentPaceInSeconds < (currentTargetPaceInSeconds - HAPTIC_FEEDBACK_THRESHOLD)) {
                        HAPTICS.vibrate(SLOW_PATTERN, -1);
                        Log.i("state", "Slow Down!");
                        Toast.makeText(getApplicationContext(), "Slow Down!",
                                Toast.LENGTH_LONG).show();
                    }
                    // slow state (prompt user to speed up if above threshold
                    else if (currentPaceInSeconds > (currentTargetPaceInSeconds + HAPTIC_FEEDBACK_THRESHOLD)) {
                        HAPTICS.vibrate(FAST_PATTERN, -1);
                        Log.i("state", "Speed Up!");
                        Toast.makeText(getApplicationContext(), "Speed Up!",
                                Toast.LENGTH_LONG).show();
                    } else {
                        // Do not give feedback if they are within feedback threshold
                        Log.i("state", "Perfect Pace!");
                        Toast.makeText(getApplicationContext(), "Perfect Pace!",
                                Toast.LENGTH_LONG).show();
                    }
                }
                Log.i("----------------", "------------------------------------");
            }

            //if using just aural
            if (AURAL_FEEDBACK_SYSTEM){
                if (currentElapsedTime != 0 && (currentElapsedTime % AURAL_FEEDBACK_INTERVAL) == 0) {

                    // convert current and target user pace in seconds/mile
                    int currentPaceInSeconds = ((currentPace[0] * 60) + currentPace[1]);
                    int currentTargetPaceInSeconds = (int) (TARGET_PACE * 60);
                    Log.i("currentPace", String.valueOf(currentPaceInSeconds));
                    Log.i("currentTargetPace", String.valueOf(currentTargetPaceInSeconds));

                    // fast state (prompt user to slow down if below threshold)
                    if (currentPaceInSeconds < (currentTargetPaceInSeconds - HAPTIC_FEEDBACK_THRESHOLD)) {
                        mSpotifyAppRemote.getPlayerApi().setShuffle(true);
                        mSpotifyAppRemote.getPlayerApi().play("spotify:user:pmartika:playlist:148My4xA7WoEaNDmnl2A8Z");
                        Log.i("state", "Slow Down!");
                    }
                    // slow state (prompt user to speed up if above threshold
                    else if (currentPaceInSeconds > (currentTargetPaceInSeconds + HAPTIC_FEEDBACK_THRESHOLD)) {
                        mSpotifyAppRemote.getPlayerApi().setShuffle(true);
                        mSpotifyAppRemote.getPlayerApi().play("spotify:user:spotify:playlist:37i9dQZF1DX8jnAPF7Iiqp");
                        Log.i("state", "Speed Up!");
                    } else {
                        // Do not give feedback if they are within feedback threshold
                        mSpotifyAppRemote.getPlayerApi().setShuffle(true);
                        mSpotifyAppRemote.getPlayerApi().play("spotify:playlist:37i9dQZF1DXadOVCgGhS7j");
                        Log.i("state", "Perfect Pace!");
                    }
                }

                Log.i("----------------", "------------------------------------");
            }

            // stop running session if we reach target distance
            if (currentDistance >= TARGET_DISTANCE) {
                stopRun(buttonStopRun);
            }
        }
    }

    // checks for Google Play Services Connection
    private boolean checkPlayServices() {
        // check if google play services is available
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);

        // check if connection was a success
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST);
            }
            else {
                finish();
            }
            return false;
        }
        else {
            return true;
        }
    }

    // requests permissions
    private ArrayList<String> permissionsToRequest(@NonNull ArrayList<String> wantedPermissions) {
        ArrayList<String> result = new ArrayList<>();
        for (String perm : wantedPermissions) {
            if (!hasPermission(perm)) {
                result.add(perm);
            }
        }
        return result;
    }

    // checks for permissions
    private boolean hasPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode) {
            case ALL_PERMISSIONS_RESULT:
                for (String perm : permissionsToRequest) {
                    if (!hasPermission(perm)) {
                        permissionsRejected.add(perm);
                    }
                }

                if (permissionsRejected.size() > 0 ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (shouldShowRequestPermissionRationale(permissionsRejected.get(0))) {
                            new AlertDialog.Builder(nextActivity.this).
                                setMessage("Permissions Required.").
                                setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            requestPermissions(permissionsRejected.
                                                toArray(new String[permissionsRejected.size()]),
                                                    ALL_PERMISSIONS_RESULT);
                                        }
                                    }
                                }).setNegativeButton("Cancel", null).create().show();
                            return;
                        }
                    }
                }
                else {
                    if (googleApiClient != null) {
                        googleApiClient.connect();
                    }
                }
                break;
        }
    }
}
