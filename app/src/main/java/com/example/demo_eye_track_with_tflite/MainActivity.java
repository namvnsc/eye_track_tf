package com.example.demo_eye_track_with_tflite;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import jp.co.cyberagent.android.gpuimage.GPUImageView;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity {

    private String cameraId;
    private AutoFitTextureView textureView;
    private GPUImageView im_face, im_eye;
    private CameraDevice cameraDevice;
    private Size previewSize;
    private CaptureRequest previewCaptureRequest;
    private CaptureRequest.Builder previewCaptureRequestBuilder;
    private CameraCaptureSession cameraCaptureSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = (AutoFitTextureView) findViewById(R.id.texture);
        im_face = (GPUImageView)findViewById(R.id.im_fgd);
        im_eye = (GPUImageView)findViewById(R.id.im_eye);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            setupCamera(textureView.getWidth(), textureView.getHeight());
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        closeCamera();
        super.onPause();
    }

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(id);

                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map =
                        cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // Set Size để hiển thị lên màn hình
                previewSize = map.getOutputSizes(SurfaceTexture.class)[0];
//                    getPreferredPreviewsSize(
//                            map.getOutputSizes(SurfaceTexture.class),
//                            width,
//                            height);
                cameraId = id;
                break;
            }
            textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            hhh = textureView.getHeight();
            www = textureView.getWidth();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

//    private Size getPreferredPreviewsSize(Size[] mapSize, int width, int height) {
//        List<Size> collectorSize = new ArrayList<>();
//        for (Size option : mapSize) {
//            if (width > height) {
//                if (option.getWidth() > width && option.getHeight() > height) {
//                    collectorSize.add(option);
//                }
//            } else {
//                if (option.getWidth() > height && option.getHeight() > width) {
//                    collectorSize.add(option);
//                }
//            }
//        }
//        if (collectorSize.size() > 0) {
//            return Collection.min(collectorSize, new Comparator<Size>() {
//                @Override
//                public int compare(Size lhs, Size rhs) {
//                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth());
//                }
//            });
//        }
//        return mapSize[0];
//    }

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 100);
                return;
            }
            cameraManager.openCamera(cameraId, cameraDeviceStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera(){
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private CameraCaptureSession.CaptureCallback cameraSessionCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                                             long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session,
                                            CaptureRequest request, CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                }
            };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);

            previewCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            previewCaptureRequestBuilder.addTarget(previewSurface);

            getModel();
            startBackgroundThread();
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface),
                    // Hàm Callback trả về kết quả khi khởi tạo.
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            try {
                                previewCaptureRequest = previewCaptureRequestBuilder.build();
                                cameraCaptureSession = session;
                                cameraCaptureSession.setRepeatingRequest(
                                        previewCaptureRequest,
                                        cameraSessionCaptureCallback,
                                        backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(),
                                    "Create camera session fail", Toast.LENGTH_SHORT).show();
                        }
                    },
                    null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Object lock = new Object();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private boolean runsegmentor = false;

    private void startBackgroundThread(){
        backgroundThread = new HandlerThread("haizzz");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        synchronized (lock) {
            runsegmentor = true;
        }
        backgroundHandler.post(periodicSegment);
    }

    private void stopBackgroundThread() {
        try {
            backgroundThread.quitSafely();
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
            synchronized (lock) {
                runsegmentor = false;
            }
        } catch (InterruptedException e) {
            Log.e("TAG", "Interrupted when stopping background thread", e);
        }catch (Exception e){

        }
    }

    private Runnable periodicSegment = new Runnable() {
        @Override
        public void run() {
            synchronized (lock) {
                if (runsegmentor) {
                    if(land_dmd!=null)
                        segmentFrame();
                }
            }
            try {
                backgroundHandler.post(periodicSegment);
            }catch (Exception e){

            }
        }
    };
    int hhh, www;
    private void segmentFrame() {
        if (cameraDevice == null) {
            return;
        }
        long t1 = SystemClock.uptimeMillis();
        hhh = textureView.getHeight();
        www = textureView.getWidth();
        SZ = Math.min(1640, Math.min(textureView.getHeight(), textureView.getWidth()));
        fgd = Bitmap.createBitmap(textureView.getBitmap(),www/2-SZ/2, hhh/2-SZ/2, SZ, SZ);
        bm = Bitmap.createScaledBitmap(fgd, W1, H1, true);
        predict();
        long t2 = SystemClock.uptimeMillis();
        System.out.println("total "+(t2-t1) +" FPS: "+(1000.0/(t2-t1)));

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if(eye_area!=null){
                    im_eye.setImage(eye_area);
                    im_face.setImage(fgd);
                }
            }
        });
    }

    float xc, yc, w, h;
    int xbg, ybg, _w, _h, SZ = 640;
    public void predict(){
        convertBitmapToByteBufferFace(bm);
//        long bg = SystemClock.uptimeMillis();
        face_dmd.run(imgDataFace, outFace);
//        long ed = SystemClock.uptimeMillis();
//        Log.e("time predict rf", ""+(ed-bg));
        int idx = 0;
        for(int i = 0; i < 4200; i++){
            if(outFace[0][i][1] > outFace[0][idx][1]) idx = i;
        }
//        System.out.println(idx+" "+outFace[0][idx][1]);

        xc = (float) (bb.get(idx).get(0) + outFace[0][idx][2+0]*0.1*bb.get(idx).get(2));
        yc = (float) (bb.get(idx).get(1) + outFace[0][idx][2+1]*0.1*bb.get(idx).get(3));
        w = (float) (bb.get(idx).get(2)*Math.exp(outFace[0][idx][2+2]*0.2));
        h = (float) (bb.get(idx).get(3)*Math.exp(outFace[0][idx][2+3]*0.2));

        xbg = (int)((xc-w/2)*SZ);
        ybg = (int)((yc-h/2)*SZ);
        _w = (int)(w*SZ);
        _h = (int)(h*SZ);
        System.out.println(xc+"_"+yc+" "+w+" "+h);

        xbg = Math.max(0, (int)(xbg));
        ybg = Math.max(0, (int)(ybg));
        _w = Math.min(SZ-xbg, _w);
        _h = Math.min(SZ-ybg, _h);
        System.out.println(xbg+" "+ybg+" "+_w+" "+_h);
        bm = Bitmap.createBitmap(fgd, xbg, ybg, _w, _h/2);
        bm = Bitmap.createScaledBitmap(bm, 112, 56, true);
        convertBitmapToByteBufferLand(bm);
//        bg = SystemClock.uptimeMillis();
        land_dmd.run(imgDataLand, outLand);
        pro();
        eye_area = Bitmap.createScaledBitmap(bm, 112*4, 56*4, true);
    }

    Interpreter.Options tfliteOptionsFace = new Interpreter.Options();
    Interpreter.Options tfliteOptionsLand = new Interpreter.Options();
    MappedByteBuffer faceModel, landModel;
    Interpreter face_dmd, land_dmd;
    GpuDelegate gpuDelegateFace = new GpuDelegate();
    GpuDelegate gpuDelegateLand = new GpuDelegate();
    int H1 = 320, W1 = 320;
    int H2 = 56, W2 = 112;
    int[] intValuesFace = new int[W1*H1];
    int[] intValuesLand = new int[W2*H2];
    ByteBuffer imgDataFace = ByteBuffer.allocateDirect(W1 * H1 * 12);
    ByteBuffer imgDataLand = ByteBuffer.allocateDirect(W2 * H2 * 12);
    float[][][] outFace = new float[1][4200][16];//2, 4, 10
    float[][] outLand = new float[1][16];
    Bitmap fgd, bm, face, eye_area;


    int pixel = Color.rgb(0, 255, 0);
    void pro(){
        int pos = 0;
        for(int o = 0; o < 16; o+=2){
            pos = (int)(outLand[0][o]*112)+(int)(outLand[0][o+1]*112)*112;
            if(pos<W2*H2 && 0<=pos)
                intValuesLand[pos] = pixel;
        }
        bm.setPixels(intValuesLand, 0, W2, 0, 0, W2, H2);
    }

    void convertBitmapToByteBufferFace(Bitmap bitmap) {
        imgDataFace.rewind();
        bitmap.getPixels(intValuesFace, 0, W1, 0, 0, W1, H1);
        int pixel = 0;
        for (int i = 0; i < H1; ++i) {
            for (int j = 0; j < W1; ++j) {
                int val = intValuesFace[pixel++];
                imgDataFace.putFloat(((val >> 16) & 0xFF));
                imgDataFace.putFloat(((val >> 8) & 0xFF));
                imgDataFace.putFloat((val & 0xFF));
            }
        }
    }

    void convertBitmapToByteBufferLand(Bitmap bitmap) {
        imgDataLand.rewind();
        bitmap.getPixels(intValuesLand, 0, W2, 0, 0, W2, H2);
        int pixel = 0;
        for (int i = 0; i < H2; ++i) {
            for (int j = 0; j < W2; ++j) {
                int val = intValuesLand[pixel++];
                imgDataLand.putFloat(((val >> 16) & 0xFF));
                imgDataLand.putFloat(((val >> 8) & 0xFF));
                imgDataLand.putFloat((val & 0xFF));
            }
        }
    }

    void getModel(){
        try {
            faceModel = loadModelFile("rf.tflite", this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        tfliteOptionsFace.addDelegate(gpuDelegateFace);
//        tfliteOptionsFace.setNumThreads(4);
//        tfliteOptionsFace.setAllowFp16PrecisionForFp32(true);
        face_dmd = new Interpreter(faceModel, tfliteOptionsFace);

        try {
            landModel = loadModelFile("pfld.tflite", this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        tfliteOptionsLand.addDelegate(gpuDelegateLand);
//        tfliteOptionsLand.setNumThreads(4);
//        tfliteOptionsLand.setAllowFp16PrecisionForFp32(true);
        land_dmd = new Interpreter(landModel, tfliteOptionsLand);
        imgDataFace.order(ByteOrder.nativeOrder());
        imgDataLand.order(ByteOrder.nativeOrder());
        prior(H1);
    }

    MappedByteBuffer loadModelFile(String file_name, Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(file_name);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    private static ArrayList<ArrayList<Float>> bb = new ArrayList<>();
    public static void prior(int sz){
        bb = new ArrayList<>();
        int[][] min_sz = {{16, 32}, {64, 128}, {256, 512}};
        int[] step = {8, 16, 32};
        int[][] fm = {{sz/8, sz/8}, {sz/16, sz/16}, {sz/32, sz/32}};
        for(int i = 0; i < 3; i++){
            int h = fm[i][0], w = fm[i][1];
            for(int r = 0; r < h; r++){
                for(int c = 0; c < w; c++){
                    for(int l = 0; l < 2; l++){
                        float mz = min_sz[i][l];
                        float s_kx = mz/sz;
                        float s_ky = s_kx;
                        float d_cx = (float) ((r+0.5)*step[i]/sz);
                        float d_cy = (float) ((c+0.5)*step[i]/sz);
                        ArrayList<Float> tmp = new ArrayList<>();
                        tmp.add(d_cx);tmp.add(d_cy);tmp.add(s_kx);tmp.add(s_ky);
                        bb.add(tmp);
                    }
                }
            }
        }

//        for(ArrayList<Float> x: bb){
//            for(float val: x){
//                System.out.print(x+" ");
//            }
//            System.out.println();
//        }
    }
}