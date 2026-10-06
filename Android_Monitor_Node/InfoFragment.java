package com.example.dsp101;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;

import java.util.ArrayList;

public class InfoFragment extends Fragment {

    private OnFragmentInteractionListener mListener;

    // Network info
    private boolean networkState;
    private String networkName;
    private String networkIP;

    // Device info
    private String deviceSerial;
    private String deviceUnsupportedSensors;
    private ArrayList<String> listOfUnsupportedSensorsInModel;

    // Model info
    private String modelName;
    private String awaitedModelInfo = "";
    private TextView modelDataTextView;

    public InfoFragment() {}

    public static InfoFragment newInstance() {
        InfoFragment fragment = new InfoFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_info, container, false);

        // Initialize list of sensors
        listOfUnsupportedSensorsInModel = new ArrayList<>();
        setListOfUnsupportedSensorsInModel();

        // Initialize properties when view is created
        setNetworkInfo();
        setDeviceInfo();
        setModelInfo();

        // Display the appropriate text sections
        displayNetworkInfo((TextView) rootView.findViewById(R.id.InfoTab_Network_Data));
        displayDeviceInfo((TextView) rootView.findViewById(R.id.InfoTab_Device_Data));

        modelDataTextView = (TextView) rootView.findViewById(R.id.InfoTab_Model_Data);
        displayModelInfo(modelDataTextView);

        return rootView;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mListener == null) {
            try {
                mListener = (OnFragmentInteractionListener) getActivity();
            } catch (ClassCastException e) {
                throw new ClassCastException(getActivity().toString()
                        + " must implement OnFragmentInteractionListener");
            }
        }
        mListener.onFragmentStart("Info");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (modelDataTextView != null) {
            displayModelInfo(modelDataTextView);
        }
        if (mListener != null) {
            mListener.onFragmentResume("Info");
        }
    }

    private void setListOfUnsupportedSensorsInModel() {
        SensorManager mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            if (mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) == null)
                listOfUnsupportedSensorsInModel.add("Gyroscope");
            if (mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) == null)
                listOfUnsupportedSensorsInModel.add("Light");
        }
    }

    private void setNetworkInfo() {
        WifiManager wifiManager = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            networkState = wifiManager.isWifiEnabled();
            if (networkState) {
                networkName = wifiManager.getConnectionInfo().getSSID();
                if (networkName != null) {
                    networkName = networkName.replace("\"", "");
                }
                int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
                networkIP = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
            }
        }
    }

    private void setDeviceInfo() {
        deviceSerial = Build.SERIAL;
        deviceUnsupportedSensors = !listOfUnsupportedSensorsInModel.isEmpty() ? listOfUnsupportedSensorsInModel.toString() : "None";
    }

    private void setModelInfo() {
        modelName = "dsp101";
    }

    private void displayNetworkInfo(TextView tv) {
        if (tv == null) return;
        tv.setText("");

        if (!networkState) {
            tv.append("Wifi is not enabled");
            return;
        }
        if (networkName != null && !networkName.isEmpty() && !networkName.equals("<unknown ssid>")) {
            tv.append("Name:      " + networkName + "\n\n");
        }
        tv.append("IP Address:      " + networkIP + "\n\n");
    }

    private void displayDeviceInfo(TextView tv) {
        if (tv == null) return;
        tv.setText("");
        tv.append("Serial:     " + deviceSerial + "\n\n");
        tv.append("Unsupported sensors in model:" + deviceUnsupportedSensors + "\n\n");
    }

    private void displayModelInfo(TextView tv) {
        if (tv == null) return;
        tv.setText("");
        if (awaitedModelInfo != null && !awaitedModelInfo.isEmpty()) {
            tv.setText(awaitedModelInfo + "\n\nName:      " + modelName);
        } else {
            tv.setText("Name:      " + modelName);
        }
    }

    // Overwrite the view content directly instead of appending
    public void updateModelInfo(final String msg) {
        awaitedModelInfo = msg;
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (modelDataTextView == null && getView() != null) {
                        modelDataTextView = (TextView) getView().findViewById(R.id.InfoTab_Model_Data);
                    }
                    if (modelDataTextView != null) {
                        modelDataTextView.setText(awaitedModelInfo + "\n\nName:      " + modelName);
                    }
                }
            });
        }
    }

    public void setFragmentInteractionListener(Activity activity) {
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }
}
