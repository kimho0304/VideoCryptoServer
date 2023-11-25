package com.example.videocryptoserver;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "MainActivity_Debug";
    private static final boolean VERBOSE = false;
    private static int bufferSize = 1;
    private static int FRAME_RATE = -1;
    int port = 9090;
    private Button server_btn, receive_btn, playBtn;
    private SurfaceView videoView;
    private SurfaceHolder surfaceHolder;
    private TextView logText;
    private ScrollView logScrollView;
    private boolean canPlayVideo = false;
    private ObjectInputStream dIs = null;
    private Handler handler;
    private ServerSocket serverSocket;
    private Socket clientSocket = null;
    private int once = 0;

    // Queue definition
    ArrayBlockingQueue<Bitmap> frameQueue;

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
        frameQueue = new ArrayBlockingQueue<>(1024, true);

        logScrollView = findViewById(R.id.logScrollView);

        videoView = findViewById(R.id.videoView);
        surfaceHolder = videoView.getHolder();
        surfaceHolder.addCallback(this);

        logText = findViewById(R.id.logText);

        playBtn = findViewById(R.id.playBtn);
        playBtn.setEnabled(canPlayVideo);

        server_btn = findViewById(R.id.serverBtn);
        server_btn.setOnClickListener(view -> {
            startServer().start();
        });

        receive_btn = findViewById(R.id.receiveBtn);
        receive_btn.setEnabled(false);
    }

    private class receiveData extends AsyncTask<Void, Void, Void> {
        Bitmap bmp = null;
        int i = 0;

        @Override
        protected Void doInBackground(Void... voids) {
            while (clientSocket.isConnected()) {
                try {
                    // Log.d(TAG, "looping: " + i++);
                    byte[] buffer = (byte[]) dIs.readObject();
                    // Log.d(TAG, "to String: " + buffer[0] + " " + buffer[1] + " " + buffer[2]);

                    if (buffer[0] == 1 && buffer[1] == 2 && buffer[2] == 3) {
                        bufferSize = i;
                        runOnUiThread(() -> new DrawFrame().run());
                        Log.d(TAG, "Got the end signal.");
                        break;
                    } else {
                        if (i >= bufferSize) {
                            runOnUiThread(() -> new DrawFrame().run());
                            i %= bufferSize;
                        }
                        i++;
                        bmp = BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
                        frameQueue.put(bmp);
                        //frameQueue.offer(bmp, 10, TimeUnit.MICROSECONDS);
                        //Log.d("frameQueue", "Current frameQueue size: " + frameQueue.size());
                        if (once == 0) {
                            setVideoSize(bmp.getWidth(), bmp.getHeight());
                            once = -1;
                        }

                        //bmp.recycle();
                        if (VERBOSE)
                            Log.d(TAG + "SizeSet", bmp.getWidth() + ", " + bmp.getHeight());

                        if (VERBOSE)
                            saveBitmapToPNG(buffer);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        private void saveBitmapToPNG(byte[] buffer) throws IOException {
            File tmp = File.createTempFile("frame" + i++ + "/", ".png");
            BufferedOutputStream bos = null;
            Bitmap bmp = BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
            try {
                bos = new BufferedOutputStream(new FileOutputStream(tmp.getAbsolutePath()));
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            Log.d(TAG, "Looping ended.");
        }
    }

    private class DrawFrame extends Thread {
        @Override
        public void run() {
            Log.i(TAG, "drawFrame() running");
            Canvas canvas = null;
            int inx = 0;
            for (; inx < bufferSize; inx++) {
                try {
                    // Draw the current frame
                    canvas = surfaceHolder.lockCanvas();
                    if (canvas != null) {
                        // Clear the canvas
                        canvas.drawColor(Color.BLACK);

                        //canvas.drawBitmap(frameQueue.poll(1, TimeUnit.MILLISECONDS), 0, 0, null);
                        Bitmap tmp = frameQueue.take();
                        if(tmp != null) {
                            canvas.drawBitmap(tmp, 0, 0, null);
                            synchronized (this) {
                               this.wait(FRAME_RATE-3);
                            }
                            tmp.recycle();
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "InterruptedException: " + e);
                    e.printStackTrace();
                } finally {
                    if (canvas != null) {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
            }
        }
    }

    private Thread startServer() {
        return new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);

                //printLog("Server is on progress: " + serverSocket.getInetAddress() + ":" + serverSocket.getLocalPort());
                Log.i(TAG, "Server is on progress.");
                printLog("Server is on progress.");

                while (true) {
                    if (clientSocket != null)
                        break;
                    Log.i(TAG, "Is socket null?: " + String.valueOf(clientSocket == null));
                    clientSocket = serverSocket.accept();
                }

                InetAddress clientHost = clientSocket.getLocalAddress();
                int clientPort = clientSocket.getPort();
                printLog("The client is connected: " + clientHost + ":" + clientPort);

                dIs = new ObjectInputStream(clientSocket.getInputStream());

                try {
                    Log.d(TAG, "process on readObject().");
                    // Frame rate == frames / sec.
                    // Ex; 24 frame rate = 24 frames for 1s(1000ms) = drawing 1 frame for (1000/24)ms
                    // hence server delay drawing 1 frame for (1000/24)ms.
                    FRAME_RATE = 1000 / dIs.readInt();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read Integer value: " + e);
                    e.printStackTrace();
                }
                Log.d(TAG + "FRAME_RATE", "FRAME_RATE: " + FRAME_RATE);

                printLog("New InputStream created.");

                receiveData task = new receiveData();
                task.execute();
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

    // https://stackoverflow.com/a/20491603/22779130
    private void setVideoSize2(int width, int height) {
        android.view.ViewGroup.LayoutParams lp = videoView.getLayoutParams();

        Log.d("setVideoSize", "size info: " + width + ", " + height);
        lp.width = width;
        lp.height = height;

        runOnUiThread(() -> {
            // Commit the layout parameters
            videoView.setLayoutParams(lp);
            Log.d("setVideoSize", "SurfaceView size changed: " + videoView.getWidth() + ", " + videoView.getHeight());
        });
    }


    private void setVideoSize(int width, int height) {
        float videoProportion = (float) width / (float) height;

        // Get the width of the screen
        int screenWidth = getWindowManager().getDefaultDisplay().getWidth();
        int screenHeight = getWindowManager().getDefaultDisplay().getHeight();
        float screenProportion = (float) screenWidth / (float) screenHeight;

        // Get the SurfaceView layout parameters
        android.view.ViewGroup.LayoutParams lp = videoView.getLayoutParams();
        if (videoProportion > screenProportion) {
            lp.width = screenWidth;
            lp.height = (int) ((float) screenWidth / videoProportion);
        } else {
            lp.width = (int) (videoProportion * (float) screenHeight);
            lp.height = screenHeight;
        }

        Log.d("setVideoSize", "size info: " + width + ", " + height);
        //lp.width = width;
        //lp.height = height;

        // Commit the layout parameters
        runOnUiThread(() -> {
            // Commit the layout parameters
            videoView.setLayoutParams(lp);
            //surfaceHolder.setFixedSize(lp.width, lp.height);
            Log.d("setVideoSize", "setFixedSize called: " + lp.width + ", " + lp.height);
        });
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
