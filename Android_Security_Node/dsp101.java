package com.example.dsp101;

import android.Manifest;
import android.os.PowerManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;
import android.util.Log;
import java.util.ArrayList;
import android.content.res.Configuration;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.example.dsp101.databinding.ActivityMainBinding;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import android.view.MenuItem;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import android.widget.LinearLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraAccessException;
import android.view.View;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ToggleButton;
import android.widget.TextView;
import java.util.Hashtable;
import android.annotation.TargetApi;
import android.os.Build;
import android.media.AudioManager;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;

public class dsp101 extends AppCompatActivity implements OnFragmentInteractionListener, SensorEventListener {
    String externalFilesDir = null;
    private InfoFragment infoFragment;
    private AppFragment appFragment;
    FragmentManager fm;
    Fragment current;
    private static ArrayList<AudioFileRead> AudioFileReader = new ArrayList<AudioFileRead>();
    private Hashtable<Integer,Float> buttonStates = new Hashtable<Integer,Float>();
    private Hashtable<Integer,TextView> textViews = new Hashtable<Integer,TextView>();
    private float mLightData = 0.0f;
    private float[] mGyroscopeData = { 0.0f, 0.0f, 0.0f };
    private SensorManager mSensorManager;
    private GPSHandler mGPSHandler;
    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 300;
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 400;
    private boolean isFineLocationPermissionGranted = false;
    private boolean isFineLocationPermissionRequested = false;
    String nativeSampleRate;
    String nativeSampleBufSize;

    // Persistent WakeLock member
    private PowerManager.WakeLock mWakeLock;

    // Flashlight / Camera controls
    private CameraManager mCameraManager;
    private String mCameraId = null;
    private boolean isTorchOn = false;
    private int torchPulseCounter = 0;

    private void initCameraTorch() {
        try {
            mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (mCameraManager != null) {
                String[] cameraIds = mCameraManager.getCameraIdList();
                for (String id : cameraIds) {
                    mCameraId = id;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            Log.e("dsp101", "Camera access initialization error: " + e.getMessage());
        }
    }

    public void toggleTorch(boolean enable) {
        if (mCameraManager == null || mCameraId == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mCameraManager.setTorchMode(mCameraId, enable);
                isTorchOn = enable;
            }
        } catch (Exception e) {
            Log.e("dsp101", "Torch toggle error: " + e.getMessage());
        }
    }

    private void registerSensorManager() {
        if (mSensorManager != null) {
            mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_GAME);
            mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT),
                SensorManager.SENSOR_DELAY_GAME);
        }
    }

    String TARGET_BASE_PATH;
    private boolean checkIfAllPermissionsGranted() {
        return true && isFineLocationPermissionGranted;
    }

    private void requestPermission() {
        String permissionRationale = "";
        if (ContextCompat.checkSelfPermission(thisClass,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(thisClass,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                permissionRationale += "Access fine location, ";
            } else {
                if (!isFineLocationPermissionRequested) {
                    isFineLocationPermissionRequested = true;
                    ActivityCompat.requestPermissions(thisClass,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA},
                            MY_PERMISSIONS_REQUEST_FINE_LOCATION);
                    return;
                }
            }
        } else {
            isFineLocationPermissionGranted = true;
        }

        if (ContextCompat.checkSelfPermission(thisClass, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(thisClass,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }

        if (!permissionRationale.isEmpty()) {
            if (infoFragment != null) {
                infoFragment.updateModelInfo(permissionRationale + "permission not granted. Model cannot start.");
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep CPU awake and start Foreground Service
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dsp101:SecurityWakeLock");
                if (!mWakeLock.isHeld()) {
                    mWakeLock.acquire();
                }
            }
            Intent serviceIntent = new Intent(this, SecurityService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e("dsp101", "WakeLock/Service error: " + e.getMessage());
        }

        com.example.dsp101.databinding.ActivityMainBinding activityMainBinding = ActivityMainBinding.inflate(LayoutInflater.from(this));
        setContentView(activityMainBinding.getRoot());
        externalFilesDir = getExternalFilesDir(null).getPath();
        fm = getSupportFragmentManager();
        Fragment navHostFragment = fm.findFragmentById(R.id.nav_host_fragment);
        navHostFragment = navHostFragment.getChildFragmentManager().getFragments().get(0);
        current = navHostFragment;
        appFragment = (AppFragment) navHostFragment;
        infoFragment = new InfoFragment();
        fm.beginTransaction().add(R.id.nav_host_fragment, infoFragment, "2").hide(infoFragment).commit();
        fm.beginTransaction().add(R.id.nav_host_fragment, appFragment, "1").commit();
        BottomNavigationView navView = findViewById(R.id.nav_view);
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupWithNavController(navView, navController);
        navView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.navigation_app:
                        fm.beginTransaction().hide(current).show(appFragment).commit();
                        current = appFragment;
                        return true;
                    case R.id.navigation_info:
                        fm.beginTransaction().hide(current).show(infoFragment).commit();
                        current = infoFragment;
                        return true;
                }
                return false;
            }
        });

        // Initialize Sensors and Hardware Interfaces
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mGPSHandler = new GPSHandler(this);
        initCameraTorch();
        queryNativeAudioParameters();
        TARGET_BASE_PATH = getCacheDir().getAbsolutePath();

        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                copyAssetFiles();
            }
        });
       
        thisClass = this;
    }

    private dsp101 thisClass;
    private final Thread BgThread = new Thread() {
        @Override
        public void run() {
            String argv[] = new String[] {"MainActivity","dsp101"};
            naMain(argv, thisClass);
        }
    };

    public void flashMessage(final String inMessage) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getBaseContext(), inMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void terminateApp() {
        finish();
    }

    @Override
    protected void onDestroy() {
        toggleTorch(false);
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
        if (BgThread.isAlive()) {
            naOnAppStateChange(6);
        }
        super.onDestroy();
        System.exit(0);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        if (fragment instanceof InfoFragment) {
            this.infoFragment = (InfoFragment) fragment;
            infoFragment.setFragmentInteractionListener(this);
        }
        if (fragment instanceof AppFragment) {
            ((AppFragment)fragment).setFragmentInteractionListener(this);
        }
    }

    @Override
    public void onFragmentCreate(String name) {
    }

    @Override
    public void onFragmentStart(String name) {
    }

    @Override
    public void onFragmentResume(String name) {
        switch (name) {
            case "App":
                registerDataDisplays();
                for (int i = 1; i <= 2; i++) {
                    registerButtonFcn(i);
                }
                if (checkIfAllPermissionsGranted()){
                    if (!BgThread.isAlive()) {
                        BgThread.start();
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onFragmentPause(String name) {
    }

    @Override
    protected void onResume() {
        requestPermission();
        super.onResume();
        if (BgThread.isAlive()) {
            naOnAppStateChange(3);
        }
        registerSensorManager();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_FINE_LOCATION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    isFineLocationPermissionGranted = true;
                    mGPSHandler = new GPSHandler(thisClass);
                } else {
                    flashMessage("Access location Permission not granted");
                }
                isFineLocationPermissionRequested = false;
                break;
        }
        if (!checkIfAllPermissionsGranted() && !isFineLocationPermissionRequested) {
            requestPermission();
        }
    }

    public void registerDataDisplays() {
        for (int i = 1; i <= 7; i++) {
            TextView textView = (TextView) findViewById(
                getResources().getIdentifier("DataDisplay" + i, "id", getPackageName()));
            textViews.put(i, textView);
        }
    }

    public void registerButtonFcn(int id) {
        String buttonid = "button" + id;
        final ToggleButton button = (ToggleButton) findViewById(getResources().getIdentifier(buttonid, "id", getPackageName()));
        if (null == button)
            return;
        setButtonState(button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                setButtonState(button);
            }
        });
    }

    public void setButtonState(ToggleButton button) {
        if (button.isChecked()) {
            buttonStates.put(button.getId(), 1.0f);
        } else {
            buttonStates.put(button.getId(), 0.0f);
        }
    }

    public float getButtonState(int id) {
        String buttonid = "button" + id;
        Float buttonState = buttonStates.get(getResources().getIdentifier(buttonid, "id", getPackageName()));
        return buttonState == null ? -1 : buttonState.floatValue();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] values = event.values;
        switch (event.sensor.getType()) {
            case Sensor.TYPE_LIGHT:
                mLightData = values[0];
                break;
            case Sensor.TYPE_GYROSCOPE:
                mGyroscopeData[0] = values[0];
                mGyroscopeData[1] = values[1];
                mGyroscopeData[2] = values[2];
                break;
        }
    }

    public float getLightData() {
        return mLightData;
    }

    public float[] getGyroscopeData() {
        return mGyroscopeData;
    }

    public double[] getGPSData() {
        return mGPSHandler.getLocationData();
    }

    public void displayText(int id, byte[] data, byte[] format) {
        String formatString = new String(format);
        String toDisplay = String.format(formatString, data[0]);
        if (data.length > 1) {
            for (int i = 1; i < data.length; i++)
                toDisplay += "\n" + String.format(formatString, data[i]);
        }
        updateTextViewById(id, toDisplay);
    }

    public void displayText(int id, short[] data, byte[] format) {
        String formatString = new String(format);
        String toDisplay = String.format(formatString, data[0]);
        if (data.length > 1) {
            for (int i = 1; i < data.length; i++)
                toDisplay += "\n" + String.format(formatString, data[i]);
        }
        updateTextViewById(id, toDisplay);
    }

    public void displayText(int id, int[] data, byte[] format) {
        String formatString = new String(format);
        String toDisplay = String.format(formatString, data[0]);
        if (data.length > 1) {
            for (int i = 1; i < data.length; i++)
                toDisplay += "\n" + String.format(formatString, data[i]);
        }
        updateTextViewById(id, toDisplay);
    }

    public void displayText(int id, long[] data, byte[] format) {
        String formatString = new String(format);
        String toDisplay = String.format(formatString, data[0]);
        if (data.length > 1) {
            for (int i = 1; i < data.length; i++)
                toDisplay += "\n" + String.format(formatString, data[i]);
        }
        updateTextViewById(id, toDisplay);
    }

    public void displayText(int id, float[] data, byte[] format) {
        String formatString = new String(format);
        String toDisplay = String.format(formatString, data[0]);
        if (data.length > 1) {
            for (int i = 1; i < data.length; i++)
                toDisplay += "\n" + String.format(formatString, data[i]);
        }
        updateTextViewById(id, toDisplay);
    }

    public void displayText(int id, double[] data, byte[] format) {
        String formatString = new String(format);
        String toDisplay = String.format(formatString, data[0]);
        if (data.length > 1) {
            for (int i = 1; i < data.length; i++)
                toDisplay += "\n" + String.format(formatString, data[i]);
        }
        updateTextViewById(id, toDisplay);
    }

    private void updateTextViewById(final int id, final String finalStringToDisplay) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    TextView tv = textViews.get(id);
                    if (tv != null) {
                        tv.setText(finalStringToDisplay);
                    }
                } catch (Exception ex) {
                    Log.e("dsp101.updateTextViewById", ex.getLocalizedMessage());
                }
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void queryNativeAudioParameters() {
        Log.d("audioEQ", "queryNativeAudioParameters called");
        AudioManager myAudioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        nativeSampleRate = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        nativeSampleBufSize = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
    }

    public int getNativeSampleRate() {
        return Integer.parseInt(nativeSampleRate);
    }

    public int getNativeSampleBufSize() {
        return Integer.parseInt(nativeSampleBufSize);
    }

    public int audioFileReadInit(String fileName, int frameSize) {
        AudioFileReader.add(new AudioFileRead(this, fileName, frameSize));
        return AudioFileReader.size() - 1;
    }

    // Called repeatedly while the Siren audio subsystem is executing
    public short[] audioFileReadStep(int idx) {
        torchPulseCounter++;
        // Toggle the flash every 15 audio frames to create a strobe effect
        if (torchPulseCounter % 15 == 0) {
            toggleTorch(!isTorchOn);
        }
        return AudioFileReader.get(idx).AudioFileReadStep();
    }

    // Shuts off the flashlight when the siren terminates
    public void audioFileReadTerminate(int idx) {
        toggleTorch(false);
        AudioFileReader.get(idx).AudioFileReadTerminate();
    }

    private void copyAssetFiles() {
        AssetManager assetManager = this.getAssets();
        try {
            String[] files = assetManager.list("");
            for (String file : files) {
                copyFile(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void copyFile(String filename) {
        AssetManager assetManager = this.getAssets();
        String newFileName = null;
        try {
            InputStream input = assetManager.open(filename);
            newFileName = TARGET_BASE_PATH + "/" + filename;
            File file = new File(newFileName);
            file.createNewFile();
            OutputStream output = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            input.close();
            input = null;
            output.flush();
            output.close();
            output = null;
        } catch (Exception e) {
            Log.e("copyFile", "file: " + filename);
            e.printStackTrace();
        }
    }

    private native int naMain(String[] argv, dsp101 pThis);
    private native void naOnAppStateChange(int state);
    static {
        System.loadLibrary("dsp101");
    }
}
