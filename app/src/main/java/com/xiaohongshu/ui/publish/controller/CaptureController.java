package com.xiaohongshu.ui.publish.controller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Size;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class CaptureController {
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private Preview preview;
    private CameraSelector cameraSelector;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;

    @SuppressLint("RestrictedApi")
    public void init(Context context, LifecycleOwner lifecycleOwner, PreviewView previewView) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        preview = new Preview.Builder().build();
        imageCapture = new ImageCapture.Builder()
                .setTargetResolution(new Size(1080, 1920))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(100)
                .build();
        cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    @SuppressLint("RestrictedApi")
    public void release() {
        if (imageCapture != null) {
            imageCapture.onUnbind();
        }
    }
}

