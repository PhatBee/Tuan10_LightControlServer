package vn.phatbee.lightcontrolserver;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        tvServerStatus = findViewById(R.id.tvServerStatus);
        tvLightStatus = findViewById(R.id.tvLightStatus);
        btnStartServer = findViewById(R.id.btnStartServer);
        viewLight = findViewById(R.id.viewLight);

        btnStartServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serverSocket == null || serverSocket.isClosed()) {
                    startServer();
                    btnStartServer.setText("Stop Server");
                } else {
                    stopServer();
                    btnStartServer.setText("Start Server");
                }
            }
        });
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
                        } else if (command.equals("OFF")) {
                            toggleLight(false);
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
        stopServer();
    }
}