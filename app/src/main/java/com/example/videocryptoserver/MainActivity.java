package com.example.videocryptoserver;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
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
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "MainActivity_Debug";
    private static final int FRAME_DELAY = 0;
    int port = 9090;
    int i;
    private Button server_btn, receive_btn, playBtn;
    private SurfaceView videoView;
    private SurfaceHolder surfaceHolder;
    private TextView logText;
    private ScrollView logScrollView;
    //private ArrayList<Bitmap> frames = new ArrayList<>(); // Array of bitmaps for animation frames
    private final Bitmap[] frames = new Bitmap[8000];
    private boolean canPlayVideo = false;
    private DataInputStream dIs = null;
    private DataInputStream dIsFlag = null;
    private Handler handler;
    private int currentFrameIndex = 0;
    private ServerSocket serverSocket;
    private Socket clientSocket = null;
    private DataInputStream dataInputStream;
    private FileOutputStream fileOutputStream;
    private File resultFile;

    private void printLog(String s) {
        handler.post(() -> { // 메인 Handler를 통해 메인 스레드에 "Server is offline." 내용의 Toast Message를 띄움.
            logText.append(s + '\n');
            logScrollView.fullScroll(View.FOCUS_DOWN);
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler = new Handler(Looper.getMainLooper());

        logScrollView = findViewById(R.id.logScrollView);

        videoView = findViewById(R.id.videoView);
        surfaceHolder = videoView.getHolder();
        surfaceHolder.addCallback(this);

        logText = findViewById(R.id.logText);

        playBtn = findViewById(R.id.playBtn);
        playBtn.setEnabled(canPlayVideo);
        /*playBtn.setOnClickListener(view -> {
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
        });*/

        server_btn = findViewById(R.id.serverBtn);
        server_btn.setOnClickListener(view -> {
            startServer().start();
            // new ServerTask().execute();
        });

        receive_btn = findViewById(R.id.receiveBtn);
        receive_btn.setOnClickListener(view -> {
            new Thread(() -> {
                while (clientSocket.isConnected() == true) {
                    byte[] buffer = new byte[8192];
                    ByteArrayOutputStream bufferOS = new ByteArrayOutputStream();
                    int numRead;
                    long totalRead = 0;
                    boolean flag = false;
                    try {
                        while ((numRead = dIs.read()) != -1) {
                            bufferOS.write(buffer, 0, numRead);
                            totalRead += numRead;
                            bufferOS.flush();
                            //printLog("Bytes received: " + numRead + ", Total bytes: " + totalRead);
                            Log.i(TAG, "Bytes received: " + numRead + ", Total bytes: " + totalRead);
                            //handler.post(() -> {
                            //printLog("Current Received Bytes Length: " + numRead + "\n");
                            //});

                            flag = dIsFlag.readBoolean();
                            if(flag) {
                                byte[] resultArray = bufferOS.toByteArray();
                                Bitmap resultBitmp = BitmapFactory.decodeByteArray(resultArray, 0, resultArray.length);
                                //Log.i(TAG, "resultBitmap info: " + resultBitmp.getByteCount());
                                frames[currentFrameIndex++] = (resultBitmp);
                                Log.i(TAG, "currentFrameIndex info: " + currentFrameIndex);
                                drawFrame();
                                flag = false;
                            }
                        }

                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
            }
        }).start();
    });
}

    private Thread startServer() {
        return new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);

                //printLog("Server is on progress: " + serverSocket.getInetAddress() + ":" + serverSocket.getLocalPort());
                Log.i(TAG, "Server is on progress.");

                while (true) {
                    if (clientSocket != null)
                        break;
                    Log.i(TAG, String.valueOf((Boolean) (clientSocket == null)));
                    clientSocket = serverSocket.accept();
                }

                InetAddress clientHost = clientSocket.getLocalAddress();
                int clientPort = clientSocket.getPort();
                dIs = new DataInputStream(clientSocket.getInputStream());
                dIsFlag = new DataInputStream(clientSocket.getInputStream());

                if (clientSocket != null) {
                    //printLog("Connection Success.");
                    //printLog("The Client is connected: " + clientHost + ":" + clientPort + ", host: " + clientHost.getHostName());

                    Log.i(TAG, "The client is connected: " + clientHost + ":" + clientPort + ", host: " + clientHost.getHostName());

                    //Boolean flag = false;
                    //DataInputStream dIs = new DataInputStream(clientSocket.getInputStream());
//                        while (flag == false) {
//                            Log.i(TAG, "FLAG ON: " + flag);
//                            try {
//                                flag = dIs.readBoolean();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                                throw new RuntimeException(e);
//                            } finally {
//                                Log.i(TAG, "FLAG ON: " + flag);
//                            }
//                        }

                    Log.i(TAG, "Skip writeBoolean allocation");

                    // 출력물 파일을 담을 File 형 변수 resultFile 생성.
                    resultFile = null;
                        /*try {
                            resultFile = File.createTempFile("tmp", "mp4");
                        } catch (IOException e) {
                            //printLog("Failed to create new tmp file: " + e);
                            Log.e(TAG, "Failed to create new tmp file: " + e);
                            e.printStackTrace();
                        }*/

                        /*BufferedOutputStream bufferOS = null;
                        try {
                            bufferOS = new BufferedOutputStream(new FileOutputStream(resultFile));
                            Log.i(TAG, "bufferOS Info: " + bufferOS);
                            //printLog("OutputStream Info: " + bufferOS);
                        } catch (FileNotFoundException e) {
                            //printLog("FileNotFoundException: " + e);
                            Log.e(TAG, "FileNotFoundException: " + e);
                            e.printStackTrace();
                        }*/

                    ByteArrayOutputStream bufferOS = new ByteArrayOutputStream();
                    Boolean flag = false;


                    /*while (true) {
                        try {
                            //flag = dIs.readBoolean();
                            // byte[] buffer = new byte[16384]; // == 2^14
                            byte[] buffer = new byte[8192]; // == 2^13
                            int numRead;
                            long totalRead = 0;
                            //printLog("Starting to get receipts");
                            Log.i(TAG, "Starting to get receipts");

                            *//*try {
                                numRead = dIs.read(buffer);
                                Log.i(TAG, "Starting to get receipts: " + numRead);
                            } catch (Exception e) {
                                Log.i(TAG, "Starting to get receipts failed: " + e);
                                e.printStackTrace();
                            }*//*

                            byte[] tmp = new byte[536870912];
                            Boolean flag2 = false;
                            dIs.readFully(tmp);
                            Log.i(TAG, "tmp size: " + tmp);
                            Log.i(TAG, "readData info: " + readData);
                            while ((numRead = readData) != -1) {
                                bufferOS.write(buffer, 0, numRead);
                                totalRead += numRead;
                                bufferOS.flush();
                                //printLog("Bytes received: " + numRead + ", Total bytes: " + totalRead);
                                Log.i(TAG, "Bytes received: " + numRead + ", Total bytes: " + totalRead);
                                //handler.post(() -> {
                                //printLog("Current Received Bytes Length: " + numRead + "\n");
                                //});

                                    byte[] resultArray = bufferOS.toByteArray();
                                    Bitmap resultBitmp = BitmapFactory.decodeByteArray(resultArray, 0, resultArray.length);
                                    Log.i(TAG, "resultBitmap info: " + resultBitmp.getByteCount());
                                    frames[currentFrameIndex++] = (resultBitmp);
                                    Log.i(TAG, "currentFrameIndex info: " + currentFrameIndex);

                                //if (currentFrameIndex < frames.length) {
                                drawFrame();
                                //drawFrame();
                                //}
                            }


                            //printLog("End of the receipts");
                            Log.i(TAG, "End of the receipts");
                        } catch (Exception e) {
                            //printLog("Connection failed: " + e);
                            Log.e(TAG, "Connection failed: " + e);
                            e.printStackTrace();
                        }
                    }*/


                    //handler.post(() -> playBtn.setEnabled((canPlayVideo = true)));
                }
                // clientSocket.close();

            } catch (Exception e) {
                //printLog("Connection failed: " + e);
                Log.e(TAG, "Connection failed: " + e);
                e.printStackTrace();
            }
        });
    }

    protected void onResume() {
        super.onResume();
    }

    private void drawFrame() {
        Log.i(TAG, "drawFrame() running");
        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
            if (canvas != null) {
                // Clear the canvas
                canvas.drawColor(Color.BLACK);

                // Draw the current frame
                canvas.drawBitmap(frames[i], 0, 0, null);
                Log.i(TAG, "cur inx: " + i);

                // Increment frame index for the next frame
                i = (i + 1) % currentFrameIndex;
            }
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void startAnimation() {
        // Create a runnable to periodically draw frames
        Runnable animationRunnable = new Runnable() {
            @Override
            public void run() {
                i = 0;
                //if (currentFrameIndex < frames.length)
                drawFrame();
                // Control the animation frame rate here using a delay
                handler.postDelayed(this, FRAME_DELAY);
            }
        };

        // Start the animation loop
        handler.post(animationRunnable);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

    }
}
