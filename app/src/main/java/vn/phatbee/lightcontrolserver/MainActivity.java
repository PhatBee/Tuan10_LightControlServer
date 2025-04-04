package vn.phatbee.lightcontrolserver;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {
    private TextView tvServerStatus, tvLightStatus;
    private Button btnStartServer;
    private View viewLight;
    private ServerSocket serverSocket;
    private boolean isLightOn = false;
    private static final int PORT = 8080;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;


    // Camera variables for flashlight
    private CameraManager cameraManager;
    private String cameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        tvServerStatus = findViewById(R.id.tvServerStatus);
        tvLightStatus = findViewById(R.id.tvLightStatus);
        btnStartServer = findViewById(R.id.btnStartServer);
        viewLight = findViewById(R.id.viewLight);

        // Initialize camera manager
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // Usually the first camera (index 0) has the flashlight
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        btnStartServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serverSocket == null || serverSocket.isClosed()) {
                    // Check camera permission before starting server
                    if (checkCameraPermission()) {
                        startServer();
                        btnStartServer.setText("Stop Server");
                    } else {
                        requestCameraPermission();
                    }
                } else {
                    stopServer();
                    btnStartServer.setText("Start Server");
                }
            }
        });
    }

    private boolean checkCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startServer();
                btnStartServer.setText("Stop Server");
            } else {
                Toast.makeText(this, "Camera permission required for flashlight control",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(PORT);
                    updateServerStatus("Server running on port " + PORT);

                    while (!serverSocket.isClosed()) {
                        // Wait for client connection
                        Socket socket = serverSocket.accept();

                        // Handle client connection in a new thread
                        handleClient(socket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    updateServerStatus("Server error: " + e.getMessage());
                }
            }
        }).start();
    }

    private void handleClient(final Socket socket) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

                    String command;
                    while ((command = reader.readLine()) != null) {
                        if (command.equals("ON")) {
                            toggleLight(true);
                            toggleFlashlight(true);

                        } else if (command.equals("OFF")) {
                            toggleLight(false);
                            toggleFlashlight(false);
                        } else if (command.equals("STATUS")) {
                            // Send light status back to client
                            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                            writer.println(isLightOn ? "ON" : "OFF");
                        }
                    }
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void toggleFlashlight(final boolean turnOn) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            toggleFlashlightNewAPI(turnOn);
        } else {
            toggleFlashlightOldAPI(turnOn);
        }
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.M)
    private void toggleFlashlightNewAPI(boolean turnOn) {
        try {
            if (cameraManager != null && cameraId != null) {
                cameraManager.setTorchMode(cameraId, turnOn);
                isLightOn = turnOn;
                updateFlashlightUI();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            showToast("Error accessing flashlight: " + e.getMessage());
        }
    }

    private android.hardware.Camera camera;
    private android.hardware.Camera.Parameters parameters;
    private void toggleFlashlightOldAPI(boolean turnOn) {
        try {
            if (turnOn) {
                // Turn on flashlight
                camera = android.hardware.Camera.open();
                parameters = camera.getParameters();
                parameters.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_TORCH);
                camera.setParameters(parameters);
                camera.startPreview();
                isLightOn = true;
            } else {
                // Turn off flashlight
                if (camera != null) {
                    camera.stopPreview();
                    camera.release();
                    camera = null;
                    isLightOn = false;
                }
            }
            updateFlashlightUI();
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Error controlling flashlight: " + e.getMessage());
        }
    }

    private void updateFlashlightUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvLightStatus.setText(isLightOn ? "ON" : "OFF");
                // If you added the flashlight icon in your layout
                ImageView ivFlashlightIcon = findViewById(R.id.ivFlashlightIcon);
                if (ivFlashlightIcon != null) {
                    ivFlashlightIcon.setImageResource(isLightOn ?
                            R.drawable.ic_flashlight_on : R.drawable.ic_flashlight_off);
                }
            }
        });
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleLight(final boolean turnOn) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                isLightOn = turnOn;
                viewLight.setBackgroundColor(isLightOn ?
                        Color.YELLOW : Color.GRAY);
                tvLightStatus.setText(isLightOn ? "ON" : "OFF");
            }
        });
    }

    private void stopServer() {
        try {
            // Turn off flashlight when server stops
            if (isLightOn) {
                toggleFlashlight(false);
            }

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            updateServerStatus("Server stopped");
        } catch (IOException e) {
            e.printStackTrace();
            updateServerStatus("Error stopping server: " + e.getMessage());
        }
    }

    private void updateServerStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvServerStatus.setText("Server Status: " + status);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Make sure flashlight is turned off and server stopped when activity is destroyed
        if (isLightOn) {
            toggleFlashlight(false);
        }
        stopServer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (camera != null) {
                camera.release();
                camera = null;
                isLightOn = false;
                updateFlashlightUI();
            }
        }
    }
}