# DSP101 Mobile Security & Remote Monitoring System

An interchangeable, dual-node mobile surveillance system implemented using MATLAB Simulink and Android Studio. This project leverages the Simulink Support Package for Android Devices to generate native Android code, which is then augmented with custom Java APIs to bypass OS background limitations (WakeLocks, Foreground Services), retain Audio Focus, control the camera strobe, and log breach events.

## System Architecture & Role Reversal

This system is designed to be **100% interchangeable**. You can deploy the exact same Simulink model to both phones. The role each phone assumes is dictated entirely by a single parameter in the Simulink model and the network IP routing.

### 1. The Role Selector (`Constant` Block)
Inside the `dsp101.slx` Simulink model, there is a master `Constant` block that routes execution to either the Security subsystem or the Monitor subsystem.
* **Security Node (`Constant = 0`):** The device becomes a stationary sensor hub. It polls the ambient light and gyroscope sensors. Upon detecting an intrusion, it transmits UDP alarm packets and activates the device's siren and camera LED strobe.
* **Monitor Node (`Constant = 1`):** The device becomes a handheld remote monitor. It listens for incoming UDP telemetry packets, logs the exact timestamps of the last 5 breaches, and provides a UI to remotely disarm the security node or manually trigger the siren.

### 2. Flipping the IP Addresses
Because the devices communicate via UDP over the local Wi-Fi network (e.g., `IITGN-SSO`), they need to know each other's IP addresses. To swap the roles of Device A and Device B:
1. Change the `Constant` block value.
2. In the **UDP Send** blocks of the model, enter the static IP address of the *target* device. 
   *(If Device A is the Security Node, its UDP Send block must point to Device B's IP address, and vice versa).*

---

## Deployment Workflow: From Simulink to Android Studio

Because this project uses custom Java code to manipulate Android hardware features that Simulink does not natively support, you cannot use Simulink's one-click "Build and Deploy" button. You must build the base project in Simulink, open it in Android Studio, inject the custom code, and then flash it to the phone.

Follow these steps precisely for **each** device.

### Phase 1: Simulink Base Generation
1. Open `dsp101.slx` in MATLAB/Simulink.
2. Set the `Constant` block to your desired role (`0` for Security Node, `1` for Monitor Node).
3. Update the **UDP Send** block IP addresses to point to the opposing device.
4. Go to the **Hardware** tab in the Simulink toolstrip.
5. Click the **Build** button (Do *not* click "Build, Deploy & Start"). 
6. Wait for the build process to complete. Simulink will generate a folder named `dsp101_ert_rtw` in your current MATLAB working directory. Inside it is the Android Studio project folder, typically named `dsp101`.

### Phase 2: Android Studio Integration
1. Open **Android Studio**.
2. Click **File > Open** and navigate to the folder Simulink just created (e.g., `C:\Matlab_projects\dsp101_ert_rtw\dsp101`).
3. Allow Android Studio a few minutes to sync the Gradle build files and index the project.

### Phase 3: Injecting the Custom Code
Depending on what you set the `Constant` to in Phase 1, you must now overwrite the auto-generated MathWorks code with the custom code provided in this repository. 

Navigate to `app/src/main/java/com/example/dsp101/` in the Android Studio project pane.

**If building the Security Node (`Constant = 0`):**
1. Open the `Android_Security_Node` folder from this GitHub repository.
2. Replace the auto-generated `dsp101.java` with the repository's `dsp101.java` (adds WakeLocks and CameraManager strobe logic).
3. Copy `SecurityService.java` into the project directory (adds the Foreground Service to keep sensors alive).
4. Navigate to `app/src/main/AndroidManifest.xml` and replace it with the repository's version (adds `CAMERA` and `FOREGROUND_SERVICE` permissions).

**If building the Monitor Node (`Constant = 1`):**
1. Open the `Android_Monitor_Node` folder from this GitHub repository.
2. Replace the auto-generated `dsp101.java` with the repository's version (adds AudioFocus overrides and the rolling 5-breach edge detector).
3. Replace the auto-generated `InfoFragment.java` with the repository's version (ensures timestamps replace text in-place without HTML artifacts or stacking).
4. Copy `SecurityService.java` into the project directory (keeps UDP polling alive in the background).
5. Navigate to `app/src/main/AndroidManifest.xml` and replace it with the repository's version (adds `MEDIA_PLAYBACK` permissions).

### Phase 4: Compile and Flash
1. Connect your Android device to your computer via USB (ensure USB Debugging is enabled in Developer Options).
2. In Android Studio, select your device from the target dropdown menu at the top.
3. Click the green **Run** (Play) button or press `Shift + F10`.
4. The custom APK will compile and launch on the device.
5. **Crucial Android Setup:** Once installed, go to the Android app settings for "dsp101", navigate to **Battery**, and set it to **Unrestricted** to prevent the OS from killing the custom background services.

Repeat Phases 1 through 4 for the second device, ensuring you invert the `Constant` block value and the UDP IP addresses.

---

## Repository Structure

* `/1_Simulink_Models/`: Contains the base `.slx` model and required audio assets (`siren_alarm.wav`).
* `/2_Android_Security_Node/`: Custom Java source files and Manifest specifically tailored for the sensor-hub role.
* `/3_Android_Monitor_Node/`: Custom Java source files and Manifest specifically tailored for UI logging and background audio alarms.

