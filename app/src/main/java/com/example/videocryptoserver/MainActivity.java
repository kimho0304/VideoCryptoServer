package com.example.videocryptoserver;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaPlayer;
import android.os.AsyncTask;
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

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
        playBtn.setOnClickListener(view -> {
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
            // new ServerTask().execute();
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

    private Thread startServer() {
        return new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);

                printLog("Server is on progress: " + serverSocket.getInetAddress()+":"+serverSocket.getLocalPort());
                Log.i(TAG, "Server is on progress.");

                while (true) {
                    Log.i(TAG, String.valueOf((Boolean) (clientSocket == null)));
                    clientSocket = serverSocket.accept();
                    InetAddress clientHost = clientSocket.getLocalAddress();
                    int clientPort = clientSocket.getPort();

                    if (clientSocket != null) {
                        printLog("Connection Success.");
                        printLog("The Client is connected: " + clientHost + ":" + clientPort + ", host: " + clientHost.getHostName());

                        Log.i(TAG, "The client is connected: " + clientHost + ":" + clientPort + ", host: " + clientHost.getHostName());

                        Boolean flag = false;
                        DataInputStream dIs = new DataInputStream(clientSocket.getInputStream());
                        while (flag == false) {
                            Log.i(TAG, "FLAG ON: " + flag);
                            try {
                                flag = dIs.readBoolean();
                            } catch (IOException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            } finally {
                                Log.i(TAG, "FLAG ON: " + flag);
                            }
                        }

                        Log.i(TAG, "Skip writeBoolean allocation");

                        // 출력물 파일을 담을 File 형 변수 resultFile 생성.
                        resultFile = null;
                        try {
                            resultFile = File.createTempFile("tmp", "mp4");
                        } catch (IOException e) {
                            printLog("Failed to create new tmp file: " + e);
                            Log.e(TAG, "Failed to create new tmp file: " + e);
                            e.printStackTrace();
                        }

                        BufferedOutputStream bufferOS = null;
                        try {
                            bufferOS = new BufferedOutputStream(new FileOutputStream(resultFile));
                            Log.i(TAG, "bufferOS Info: " + bufferOS);
                            printLog("OutputStream Info: " + bufferOS);
                        } catch (FileNotFoundException e) {
                            printLog("FileNotFoundException: " + e);
                            Log.e(TAG, "FileNotFoundException: " + e);
                            e.printStackTrace();
                        }

                        try {
                            // byte[] buffer = new byte[16384]; // == 2^14
                            byte[] buffer = new byte[8192]; // == 2^13
                            int numRead;
                            long totalRead = 0;
                            printLog("Starting to get receipts");
                            Log.i(TAG, "Starting to get receipts");

                            /*try {
                                numRead = dIs.read(buffer);
                                Log.i(TAG, "Starting to get receipts: " + numRead);
                            } catch (Exception e) {
                                Log.i(TAG, "Starting to get receipts failed: " + e);
                                e.printStackTrace();
                            }*/

                            while ((numRead = dIs.read(buffer)) != -1) {
                                bufferOS.write(buffer, 0, numRead);
                                totalRead += numRead;
                                bufferOS.flush();
                                printLog("Bytes received: " + numRead + ", Total bytes: " + totalRead);
                                Log.i(TAG, "Bytes received: " + numRead + ", Total bytes: " + totalRead);
                                //mainHandler.post(() -> {
                                printLog("Current Received Bytes Length: " + numRead + "\n");
                                //});
                            }
                            printLog("End of the receipts");
                            Log.i(TAG, "End of the receipts");
                        } catch (Exception e) {
                            printLog("Connection failed: " + e);
                            Log.e(TAG, "Connection failed: " + e);
                            e.printStackTrace();
                        } finally {
                            try {
                                dIs.close();
                                bufferOS.close();
                                Log.i(TAG, "Closed streams successfully");
                            } catch (IOException e) {
                                dIs.close();
                                bufferOS.close();
                                printLog("Failed to close streams: " + e);
                                Log.e(TAG, "Failed to close streams: " + e);
                            }
                        }

                        clientSocket.close();
                        mainHandler.post(() -> playBtn.setEnabled((canPlayVideo = true)));
                    }
                }
            } catch (Exception e) {
                printLog("Connection failed: " + e);
                Log.e(TAG, "Connection failed: " + e);
                e.printStackTrace();
            }
        });
    }
}
