package com.example.shoprecog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class IntroActivity extends AppCompatActivity {

    private boolean isLoading;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location userLocation;
    private String apiKey, nextPageToken;
    private List<String> shopNameList, shopIDList;

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.RECORD_AUDIO  // Add RECORD_AUDIO permission
    };


    @SuppressLint("MissingPermission")
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                boolean locationPermissionGranted = isGranted.get(android.Manifest.permission.ACCESS_FINE_LOCATION) != null
                        && Boolean.TRUE.equals(isGranted.get(android.Manifest.permission.ACCESS_FINE_LOCATION));

                boolean wifiStatePermissionGranted = isGranted.get(android.Manifest.permission.ACCESS_WIFI_STATE) != null
                        && Boolean.TRUE.equals(isGranted.get(android.Manifest.permission.ACCESS_WIFI_STATE));

                boolean cameraPermissionGranted = isGranted.get(android.Manifest.permission.CAMERA) != null
                        && Boolean.TRUE.equals(isGranted.get(Manifest.permission.CAMERA));

                if (locationPermissionGranted && wifiStatePermissionGranted && cameraPermissionGranted) {
                    //startCamera();
                    findCurrentPlaceWithPermissions();

                } else {
                    // Fallback behavior if any of the permissions is denied
                    Log.d("Error", "One or more permissions denied");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motion_layout);

        apiKey = getString(R.string.API_KEY);
        shopIDList = new ArrayList<>();
        shopNameList = new ArrayList<>();
        isLoading = false;
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        findCurrentPlaceWithPermissions();
    }

    private void findCurrentPlaceWithPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS);
            return;
        }

        fusedLocationProviderClient.getLastLocation().addOnCompleteListener(this, task -> {
            if (task.isSuccessful() && task.getResult() != null) {

                userLocation = task.getResult();
                //To connect to Google Web Services
                String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                        "?location=" + userLocation.getLatitude() + "%2C" + userLocation.getLongitude() +
                        "&rankby=distance" +
                        "&type=store" +
                        "&key=" + apiKey;
                MyThread connectingThread = new MyThread(url);
                connectingThread.start();

            }
        });

    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                findCurrentPlaceWithPermissions();
            } else {
                // Not all permissions are granted, request permissions again
                requestPermissionLauncher.launch(REQUIRED_PERMISSIONS);
            }
        }
    }


    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private class MyThread extends Thread {
        private final String searchURL;

        public MyThread(String searchUrl) {
            this.searchURL = searchUrl;
        }

        public void run() {
            isLoading = true;

            try {
                URL url = new URL(searchURL);
                HttpURLConnection hc = (HttpURLConnection) url.openConnection();

                InputStream input = hc.getInputStream();
                String response = readStream(input);

                //OK response code
                if (hc.getResponseCode() == 200) {
                    Log.d("Map API", "Response: " + response);
                    updateShopList(response);
                }else{
                    Log.e("API ERROR", "Response code: " + hc.getResponseCode());
                }

                input.close();

            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private String readStream(InputStream is) {
        try {
            ByteArrayOutputStream bo = new
                    ByteArrayOutputStream();
            int i = is.read();
            while (i != -1) {
                bo.write(i);
                i = is.read();
            }
            return bo.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private String getNextPageToken(String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            return jsonObject.getString("next_page_token");
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void loadMoreData(){
        if (nextPageToken != null) {
            // Set isLoading to true to prevent duplicate requests
            isLoading = true;
            // Create and start a new thread to fetch more data
            String url ="https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?pagetoken=" + nextPageToken+
                    "&key="+ apiKey;
            MyThread connectingThread = new MyThread(url);
            connectingThread.start();
        } else {
            Log.i("nextPageToken","Next Page Token is null!");
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class) private void updateShopList(String response) throws JSONException {

        JSONObject jsonObject = new JSONObject(response);
        JSONArray resultsArray = jsonObject.getJSONArray("results");

        for (int i = 0; i < resultsArray.length(); i++) {
            //Get every restaurant's name
            JSONObject result = resultsArray.getJSONObject(i);
            shopNameList.add(result.getString("name"));
            shopIDList.add(result.getString("place_id"));
        }
        Log.i("shopNameList",shopNameList.toString());
        Log.i("shopIDList",shopIDList.toString());


        //Check for next page token
        nextPageToken = getNextPageToken(response);
        if (nextPageToken != null) {
            Log.i("Page Token Get",nextPageToken);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            loadMoreData();
        } else {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putStringArrayListExtra("shopNameList", new ArrayList<>(shopNameList));
            intent.putStringArrayListExtra("shopIDList", new ArrayList<>(shopIDList));
            startActivity(intent);
        }

    }
}