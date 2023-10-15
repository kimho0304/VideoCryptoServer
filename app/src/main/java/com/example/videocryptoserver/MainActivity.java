package com.example.videocryptoserver;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity_Debug";
    int port = 9090;
    private Button server_btn, receive_btn;
    private VideoView videoView;
    private TextView logText;
    private Handler mainHandler;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataInputStream dataInputStream;
    private FileOutputStream fileOutputStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainHandler = new Handler(Looper.getMainLooper());

        videoView = findViewById(R.id.videoView);

        logText = findViewById(R.id.logText);

        server_btn = findViewById(R.id.serverBtn);
        server_btn.setOnClickListener(view -> {
            startServer().start();
        });

        receive_btn = findViewById(R.id.receiveBtn);
        receive_btn.setOnClickListener(view -> {
            /*ry {
                receiveFileAsBytes();
            } catch (IOException e) {
                Log.e(TAG, "IOException: " + e);
            }*/
        });
    }

    private Thread receiveFileAsBytes() throws IOException {
        return new Thread(()->{
            // 출력물 파일을 담을 File 형 변수 bufferFile 생성.
            File bufferFile = null;
            try {
                bufferFile = File.createTempFile("tmp", "mp4");
            } catch (IOException e) {
                Log.e(TAG, "Failed to create new tmp file: " + e);
            }
            BufferedOutputStream bufferOS = null;
            try {
                bufferOS = new BufferedOutputStream(new FileOutputStream(bufferFile));
            } catch (FileNotFoundException e) {
                Log.e(TAG, "FileNotFoundException: " + e);
            }

            DataInputStream iS = null;
            try {
                iS = new DataInputStream(clientSocket.getInputStream());
            } catch (IOException e) {
                Log.e(TAG, "Failed to allocate the InputStream: " + e);
            }

            try {
                byte[] buffer = new byte[16384]; // == 2^14
                int numRead;
                long totalRead = 0;

                while ((numRead = iS.read(buffer)) != -1) {
                    bufferOS.write(buffer, 0, numRead);
                    totalRead += numRead;
                    bufferOS.flush();
                    Log.i(TAG, "Bytes received: " + numRead + ", Total bytes: " + totalRead);
                    //mainHandler.post(() -> {
                    logText.setText("Current Received Bytes Length: " + String.valueOf(numRead)+'\n');
                    //});
                }
            } catch (Exception e) {
                Log.e(TAG, "Connection failed: " + e);
            } finally {
                try {
                    iS.close();
                    bufferOS.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close streams: " + e);
                }
            }
        });
    }

    private Thread startServer() {
        return new Thread(()->{
            try {
                serverSocket = new ServerSocket(port);
                Log.i(TAG, "Server is on progress.");

                while (true) {
                    clientSocket = serverSocket.accept();
                    InetAddress clientHost = clientSocket.getLocalAddress();
                    int clientPort = clientSocket.getPort();

                    if(clientSocket != null) {
                        mainHandler.post(() -> {
                            Toast.makeText(this, "Connection Success.", Toast.LENGTH_SHORT);
                            logText.setText("The client info: "+clientHost+'\n');
                        });
                        Log.i(TAG, "Server is connected: " + clientHost + ":" + clientPort);

                        receiveFileAsBytes();

                        clientSocket.close();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Connection failed: " + e);
            }
        });
    }
}