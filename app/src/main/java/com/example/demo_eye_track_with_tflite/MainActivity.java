package com.example.demo_eye_track_with_tflite;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ImageView;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MainActivity extends AppCompatActivity {
    Interpreter.Options tfliteOptions = new Interpreter.Options();
    MappedByteBuffer tfliteModel;
    Interpreter tflite;
    Interpreter.Options tfliteOptions2 = new Interpreter.Options();
    MappedByteBuffer tfliteModel2;
    Interpreter tflite2;
    GpuDelegate gpuDelegate = new GpuDelegate();
    GpuDelegate gpuDelegate2 = new GpuDelegate();
    int H = 56, W = 112;
    int H2 = 320, W2 = 320;
    int[] intValues = new int[W*H];
    ByteBuffer imgData = ByteBuffer.allocateDirect(W * H * 12);
    float[][] lm = new float[1][16];
    int[] intValues2 = new int[W2*H2];
    ByteBuffer imgData2 = ByteBuffer.allocateDirect(W2 * H2 * 12);
    float[][][] lm2 = new float[1][4200][16];
    Bitmap bm;
    ImageView img;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        img = findViewById(R.id.iv);

        try {
            tfliteModel2 = loadModelFile("rf.tflite", this);
            Log.e("sao", "ok");
        } catch (IOException e) {
            e.printStackTrace();
        }
        tfliteOptions2.addDelegate(gpuDelegate2);
//        tfliteOptions2.setNumThreads(4);
//        tfliteOptions2.setAllowFp16PrecisionForFp32(true);
        tflite2 = new Interpreter(tfliteModel2, tfliteOptions2);
        imgData2.order(ByteOrder.nativeOrder());
        long t1 = SystemClock.uptimeMillis();
        bm = BitmapFactory.decodeResource(this.getResources(),  R.drawable.tmp_2);
        bm = Bitmap.createScaledBitmap(bm, W2, H2, true);
//        System.out.println(bm.getHeight()+"-|-"+bm.getWidth());

        convertBitmapToByteBuffer2(bm);
        long bg = SystemClock.uptimeMillis();
        tflite2.run(imgData2, lm2);
        long ed = SystemClock.uptimeMillis();
        Log.e("time predict rf", ""+(ed-bg));
        ////////////////////////////////////////////////////////////////

        try {
            tfliteModel = loadModelFile("pfld.tflite", this);
            Log.e("sao 2", "ok");
        } catch (IOException e) {
            e.printStackTrace();
        }
        tfliteOptions.addDelegate(gpuDelegate);
        tfliteOptions.setNumThreads(4);
//        tfliteOptions.setAllowFp16PrecisionForFp32(true);
        tflite = new Interpreter(tfliteModel, tfliteOptions);
        imgData.order(ByteOrder.nativeOrder());
        t1 = SystemClock.uptimeMillis();
        bm = BitmapFactory.decodeResource(this.getResources(),  R.drawable.tmp_2);
        bm = Bitmap.createScaledBitmap(bm, W, H, true);
//        System.out.println(bm.getHeight()+"-|-"+bm.getWidth());

        convertBitmapToByteBuffer(bm);
        bg = SystemClock.uptimeMillis();
        tflite.run(imgData, lm);
        ed = SystemClock.uptimeMillis();
        Log.e("time predict ", ""+(ed-bg));
        pro();
        img.setImageBitmap(Bitmap.createScaledBitmap(bm, 112*4, 56*4, true));

        long t2 = SystemClock.uptimeMillis();
        Log.e("total", ""+(t2-t1));
    }

    void pro(){
        int pixel = Color.rgb(0, 255, 0), pos = 0;
//        int[] colors = new int[W * H];
//        bm.getPixels(colors, 0, 112, 0, 0, 112, 56);

//        for(int o = 0; o < 16; o+=2){
//            pos = (int)(lm[0][o]*112)+(int)(lm[0][o+1]*112)*112;
//            System.out.println(lm[0][o] + "-lm-"+lm[0][o+1]);
//            if(pos<W*H && 0<=pos)
//                intValues[pos] = pixel;
//        }
//        Bitmap tmp = Bitmap.createBitmap(bm, 0, 0,  W, H);
        bm.setPixels(intValues, 0, W, 0, 0, W, H);
//        return tmp;
    }

    void convertBitmapToByteBuffer(Bitmap bitmap) {
        imgData.rewind();
        bitmap.getPixels(intValues, 0, W, 0, 0, W, H);
        int pixel = 0;
        for (int i = 0; i < H; ++i) {
            for (int j = 0; j < W; ++j) {
                int val = intValues[pixel++];
                imgData.putFloat(((val >> 16) & 0xFF));
                imgData.putFloat(((val >> 8) & 0xFF));
                imgData.putFloat((val & 0xFF));
            }
        }
    }

    void convertBitmapToByteBuffer2(Bitmap bitmap) {
        imgData.rewind();
        bitmap.getPixels(intValues2, 0, W2, 0, 0, W2, H2);
        int pixel = 0;
        for (int i = 0; i < H2; ++i) {
            for (int j = 0; j < W2; ++j) {
                int val = intValues2[pixel++];
                imgData2.putFloat(((val >> 16) & 0xFF));
                imgData2.putFloat(((val >> 8) & 0xFF));
                imgData2.putFloat((val & 0xFF));
            }
        }
    }

    MappedByteBuffer loadModelFile(String file_name, Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(file_name);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}