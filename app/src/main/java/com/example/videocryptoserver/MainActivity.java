package com.example.videocryptoserver;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "MainActivity_Debug";
    private static final boolean VERBOSE = false;
    private static int bufferSize = 20;
    private static final int bufferSpeed = 35;
    private static int bufferCurInx = 0;
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
    private ArrayList<Bitmap> bufferBitmap = new ArrayList<>();

    private void printLog(String s) {
        handler.post(() -> { // 메인 Handler를 통해 메인 스레드에 "Server is offline." 내용의 Toast Message를 띄움.
            logText.append(s + '\n');
            logScrollView.fullScroll(View.FOCUS_DOWN);
        });
    }

    private static class BufferBitmap {
        private Bitmap frame;
        private boolean flag;

        BufferBitmap(Bitmap frame, boolean flag) {
            this.frame = frame;
            this.flag = flag;
        }

        public void recycle() {
            // int inx = bufferCurInx;
            for (int i = 0; i < bufferCurInx; i++) {
                //if(bufferBitmap.get(i).flag)
                // bufferBitmap.remove(i);
            }
            bufferCurInx = 0;
        }
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
            receiveData task = new receiveData();
            task.execute();
        });
    }

    private int firstFlag = 0;

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
                        Log.d(TAG, "Got the bitmap: " + i++);
                        if (i > bufferSize) {
                            runOnUiThread(() -> new DrawFrame().run());
                            i %= bufferSize;
                        }

                        bmp = BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
                        // bufferBitmap.add(new BufferBitmap(bmp, false));
                        bufferBitmap.add(i, bmp);
                        if((firstFlag) == 0) {
                            setVideoSize(bmp.getWidth(), bmp.getHeight());
                            firstFlag = -1;
                        }

                        if(VERBOSE) Log.d(TAG + "SizeSet", bmp.getWidth() + ", " + bmp.getHeight());

                        // surfaceHolder.setFixedSize((int)(bmp.getHeight()), (int) (bmp.getWidth()));
                        // Log.d(TAG, "bufferBitmap len: " + bufferBitmap.size());

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
            File tmp = File.createTempFile("pigeon" + i++, ".png");
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

    private void refreshArray() {

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
                    Log.i(TAG, String.valueOf((Boolean) (clientSocket == null)));
                    clientSocket = serverSocket.accept();
                }

                InetAddress clientHost = clientSocket.getLocalAddress();
                int clientPort = clientSocket.getPort();
                printLog("The client is connected: " + clientHost + ":" + clientPort);

                dIs = new ObjectInputStream(clientSocket.getInputStream());
                printLog("New InputStream created.");
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

    private class DrawFrame extends Thread {
        private ReentrantLock locker = new ReentrantLock();

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
                        Log.i("helloworld", inx + ": " + (inx + bufferCurInx));
                        // Clear the canvas
                        canvas.drawColor(Color.BLACK);
                        canvas.drawBitmap(bufferBitmap.get(inx), 0, 0, null);
                        synchronized (this) {
                            this.wait(bufferSpeed);
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
            if (inx >= bufferSize - 1) {
                bufferCurInx %= bufferSize;
                // bufferBitmap.clear();
            }
            else
                Log.w(TAG, "iterating err.");
        }
    }

    // https://stackoverflow.com/a/20491603/22779130
    private void setVideoSize(int width, int height) {
        runOnUiThread(()->{
            android.view.ViewGroup.LayoutParams lp = videoView.getLayoutParams();

            lp.width = width;
            lp.height = height;

            // Commit the layout parameters
            videoView.setLayoutParams(lp);
            Log.d(TAG + "_SetSize", "SurfaceView size changed: " + videoView.getWidth() + ", " + videoView.getHeight());
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
