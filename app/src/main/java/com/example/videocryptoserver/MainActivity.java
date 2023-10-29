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
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "MainActivity_Debug";
    private static final int FRAME_DELAY = 0;
    int port = 9090;
    int i = 0;
    private Button server_btn, receive_btn, playBtn;
    private SurfaceView videoView;
    private SurfaceHolder surfaceHolder;
    private TextView logText;
    private ScrollView logScrollView;
    //private ArrayList<Bitmap> frames = new ArrayList<>(); // Array of bitmaps for animation frames
    private final Bitmap[] frames = new Bitmap[8000];
    private boolean canPlayVideo = false;
    private InputStream iS;
    private ObjectInputStream dIs = null;
    private DataInputStream dIsFlag = null;
    private Handler handler;
    private int currentFrameIndex = 0;
    private ServerSocket serverSocket;
    private Socket clientSocket = null;
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
            receiveData task = new receiveData();
            task.execute();
        });
    }

    private class receiveData extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            while (clientSocket.isConnected() == true) {
                try {
                    //  java.io.StreamCorruptedException: invalid type code: AC --> 스트림을 새로 생성하여 해결
                    //BitmapDataObject bitmapObject = new BitmapDataObject(null);
                    byte[] result = (byte[]) dIs.readObject();
                    //int resultLength = (int) dIs.readInt();
                    //byte[] buffer = new byte[resultLength];
                    //for(int pos = 0; pos != -1; pos = dIs.read(buffer));

                    Bitmap resultBitmap = BitmapFactory.decodeByteArray(result, 0, result.length);
                    //printLog("resultBitmap is null?: " + (Boolean)(resultBitmap == null));

                    frames[i] = resultBitmap;
                    currentFrameIndex++;
                    //if(frames[i] != null)
                        //printLog("frames[i]: info" + frames[i].getByteCount() + ", cnt: " + i);
                    Log.i(TAG, "getBitmap: " + i);
                    publishProgress();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(java.lang.Void... values) {
            super.onProgressUpdate(values);
            drawFrame();
        }
    }

    // https://github.com/rantibi/MyFirstApp/blob/master/src/com/sharedpaint/serializables/SerializableBitmap.java
    public class BitmapDataObject implements Serializable {

        private Bitmap currentImage;

        public BitmapDataObject(Bitmap bitmap) {
            currentImage = bitmap;
        }

        private byte[] compressBitmap() throws IOException {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            currentImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            return byteArray;
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
                    Log.i(TAG, String.valueOf((Boolean) (clientSocket == null)));
                    clientSocket = serverSocket.accept();
                }

                InetAddress clientHost = clientSocket.getLocalAddress();
                int clientPort = clientSocket.getPort();
                printLog("The client is connected: " + clientHost + ":" + clientPort);

                iS = clientSocket.getInputStream();
                dIs = new ObjectInputStream(iS);
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

    private void drawFrame() {
        Log.i(TAG, "drawFrame() running");
        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
            if (canvas != null) {
                // Clear the canvas
                canvas.drawColor(Color.BLACK);

                // Draw the current frame
                if(frames[i] != null)
                    canvas.drawBitmap(frames[i], 0, 0, null);
                else
                    Log.i(TAG, "Current frame is null.");
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
