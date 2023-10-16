package com.example.videocryptoserver;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
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
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity_Debug";
    int port = 9090;
    private Button server_btn, receive_btn, playBtn;
    private VideoView videoView;
    private TextView logText;
    private ScrollView logScrollView;
    private boolean canPlayVideo = false;
    private Handler mainHandler;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataInputStream dataInputStream;
    private FileOutputStream fileOutputStream;
    private File resultFile;

    private void printLog(String s) {
        mainHandler.post(() -> { // 메인 Handler를 통해 메인 스레드에 "Server is offline." 내용의 Toast Message를 띄움.
            logText.append(s + '\n');
            logScrollView.fullScroll(View.FOCUS_DOWN);
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainHandler = new Handler(Looper.getMainLooper());

        logScrollView = findViewById(R.id.logScrollView);

        videoView = findViewById(R.id.videoView);

        logText = findViewById(R.id.logText);

        playBtn = findViewById(R.id.playBtn);
        playBtn.setEnabled(canPlayVideo);
        playBtn.setOnClickListener(view->{
            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { // 비디오 리스너 등록.
                @Override
                public void onPrepared(MediaPlayer mp) {
                    // 준비 완료되면 비디오 재생.
                    mp.setLooping(true); // 비디오 무한루프 설정: true.
                    mp.start(); // 비디오 재생 시작.
                }
            });

            videoView.setVideoPath(resultFile.getAbsolutePath());
            videoView.start();
        });

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
        return new Thread(() -> {

        });
    }

    private Thread startServer() {
        return new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                printLog("Server is on progress: " + serverSocket.getInetAddress()+":"+serverSocket.getLocalPort());
                Log.i(TAG, "Server is on progress.");

                while (true) {
                    clientSocket = serverSocket.accept();
                    InetAddress clientHost = clientSocket.getLocalAddress();
                    int clientPort = clientSocket.getPort();

                    if (clientSocket != null) {
                        printLog("Connection Success.");
                        printLog("Server is connected: " + clientHost + ":" + clientPort);
                        Log.i(TAG, "Server is connected: " + clientHost + ":" + clientPort);

                        // 출력물 파일을 담을 File 형 변수 resultFile 생성.
                        resultFile = null;
                        try {
                            resultFile = File.createTempFile("tmp", "mp4");
                        } catch (IOException e) {
                            printLog("Failed to create new tmp file: " + e);
                            Log.e(TAG, "Failed to create new tmp file: " + e);
                        }
                        BufferedOutputStream bufferOS = null;
                        try {
                            bufferOS = new BufferedOutputStream(new FileOutputStream(resultFile));
                            Log.i(TAG, "OutputStream Info: " + bufferOS);
                            printLog("OutputStream Info: " + bufferOS);
                        } catch (FileNotFoundException e) {
                            printLog("FileNotFoundException: " + e);
                            Log.e(TAG, "FileNotFoundException: " + e);
                        }

                        DataInputStream iS = null;

                        try {
                            iS = new DataInputStream(clientSocket.getInputStream());
                            printLog("InputStream Info: " + iS);
                            Log.i(TAG, "InputStream Info: " + iS);
                        } catch (IOException e) {
                            printLog("Failed to allocate the InputStream: " + e);
                            Log.e(TAG, "Failed to allocate the InputStream: " + e);
                        }

                        try {
                            byte[] buffer = new byte[16384]; // == 2^14
                            int numRead;
                            long totalRead = 0;
                            Log.i(TAG, "Starting to get receipts");

                            while ((numRead = iS.read(buffer)) != -1) {
                                bufferOS.write(buffer, 0, numRead);
                                totalRead += numRead;
                                bufferOS.flush();
                                printLog("Bytes received: " + numRead + ", Total bytes: " + totalRead);
                                Log.i(TAG, "Bytes received: " + numRead + ", Total bytes: " + totalRead);
                                //mainHandler.post(() -> {
                                printLog("Current Received Bytes Length: " + String.valueOf(numRead) + "\n\n");
                                //});
                            }
                            Log.i(TAG, "Starting to get receipts");
                        } catch (Exception e) {
                            printLog("Connection failed: " + e);
                            Log.e(TAG, "Connection failed: " + e);
                        }/*finally {
                            try {
                                iS.close();
                                bufferOS.close();
                                Log.i(TAG, "Closed streams successfully");
                            } catch (IOException e) {
                                printLog("Failed to close streams: " + e);
                                Log.e(TAG, "Failed to close streams: " + e);
                            }
                        }*/

                        clientSocket.close();
                        playBtn.setEnabled((canPlayVideo = true));
                    }
                }
            } catch (Exception e) {
                printLog("Connection failed: " + e);
                Log.e(TAG, "Connection failed: " + e);
            }
        });
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private InputStream inputStream;

        ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
            try {
                this.inputStream = clientSocket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    // Process the received data
                    processReceivedData(buffer, bytesRead);
                }

                // Close resources when done
                inputStream.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void processReceivedData(byte[] buffer, int bytesRead) {
            // Implement your logic to process the received data
            System.out.println("Received: " + new String(buffer, 0, bytesRead));
        }
    }

}