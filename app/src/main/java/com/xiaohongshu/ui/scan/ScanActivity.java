package com.xiaohongshu.ui.scan;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.Result;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 扫一扫Activity
 * 使用CameraX采集画面，zxing逐帧解码二维码
 */
public class ScanActivity extends BaseActivity {
    private PreviewView previewView;
    private static final int REQUEST_CODE_CAMERA = 1001;

    private final MultiFormatReader reader = new MultiFormatReader();
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    // 解码成功后置位，丢弃后续帧；对话框关闭后复位继续扫码
    private volatile boolean decoded;

    public static void start(Context context) {
        Intent intent = new Intent(context, ScanActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        initViews();

        // 检查相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能使用扫一扫功能", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void initViews() {
        previewView = findViewById(R.id.previewView);

        // 返回按钮
        android.view.View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }

    @Override
    protected void initData() {
        // 相机初始化在startCamera中完成
    }

    /**
     * 启动相机
     */
    private void startCamera() {
        if (previewView == null) {
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
            ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
                imageAnalysis.setAnalyzer(analysisExecutor, this::analyzeFrame);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector,
                    preview, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                Toast.makeText(this, "相机启动失败", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * 分析相机帧：Y平面转亮度矩阵后交给zxing解码
     */
    private void analyzeFrame(ImageProxy image) {
        if (decoded) {
            image.close();
            return;
        }
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            ImageProxy.PlaneProxy plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            buffer.rewind();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();

            byte[] luminance = new byte[width * height];
            byte[] row = new byte[rowStride];
            for (int y = 0; y < height; y++) {
                buffer.position(y * rowStride);
                buffer.get(row);
                int base = y * width;
                if (pixelStride == 1) {
                    System.arraycopy(row, 0, luminance, base, width);
                } else {
                    for (int x = 0; x < width; x++) {
                        luminance[base + x] = row[x * pixelStride];
                    }
                }
            }

            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    luminance, width, height, 0, 0, width, height, false);
            try {
                Result result = reader.decode(new BinaryBitmap(new HybridBinarizer(source)));
                onQrDecoded(result.getText());
            } catch (NotFoundException ignored) {
                // 当前帧没有二维码，继续扫描
            }
        } catch (Exception ignored) {
            // 单帧处理失败不影响后续帧
        } finally {
            image.close();
        }
    }

    private void onQrDecoded(String text) {
        decoded = true;
        reader.reset();
        runOnUiThread(() -> showResultDialog(text));
    }

    private void showResultDialog(String text) {
        String trimmed = text.trim();
        // 应用内路由：用户二维码，跳转对方主页
        if (trimmed.startsWith(com.xiaohongshu.util.QRCodeUtil.USER_SCHEME_PREFIX)) {
            String targetUserId = trimmed.substring(
                    com.xiaohongshu.util.QRCodeUtil.USER_SCHEME_PREFIX.length()).trim();
            new android.app.AlertDialog.Builder(this)
                    .setTitle("识别到用户二维码")
                    .setMessage("是否查看该用户的主页？")
                    .setPositiveButton("查看主页", (dialog, which) -> {
                        decoded = false;
                        if (!targetUserId.isEmpty()) {
                            com.xiaohongshu.ui.profile.UserProfileActivity.start(this, targetUserId);
                        }
                    })
                    .setNegativeButton("继续扫码", (dialog, which) -> decoded = false)
                    .setOnCancelListener(dialog -> decoded = false)
                    .show();
            return;
        }

        boolean isUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://");
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(isUrl ? "识别到链接" : "识别结果");
        builder.setMessage(trimmed);
        if (isUrl) {
            builder.setPositiveButton("打开链接", (dialog, which) -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(text)));
                } catch (Exception ignored) {
                    Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show();
                }
                decoded = false;
            });
        }
        builder.setNeutralButton("复制", (dialog, which) -> {
            copyToClipboard(text);
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            decoded = false;
        });
        builder.setNegativeButton("继续扫码", (dialog, which) -> decoded = false);
        builder.setOnCancelListener(dialog -> decoded = false);
        builder.show();
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager manager =
                (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(android.content.ClipData.newPlainText("qrcode", text));
        }
    }

    @Override
    protected void onDestroy() {
        analysisExecutor.shutdown();
        super.onDestroy();
    }
}
