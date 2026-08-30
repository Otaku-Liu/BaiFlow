package com.baiflow.android.ui.activity;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.data.ProgressReporter;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.transfer.DownloadService;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * 文件预览页 — 按 MIME 类型预览图片 / 文本 / Markdown / 音频 / 视频 / PDF。
 * <p>
 * 其余类型显示「暂不支持在线预览」并提供下载。与 Web 端预览能力对齐。
 */
public class PreviewActivity extends BaseActivity {

    private static final String EXTRA_FILE_ID = "file_id";
    private static final String EXTRA_FILE_NAME = "file_name";
    private static final String EXTRA_MIME = "mime_type";
    private static final String EXTRA_PRIVACY_TOKEN = "privacy_token";
    private static final String EXTRA_SIZE_BYTES = "size_bytes";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ApiClient client;
    private SessionManager session;

    private String fileId, fileName, mime, privacyToken;
    private long sizeBytes;

    private FrameLayout previewContainer;
    private LinearLayout loadingContainer, errorContainer, unsupportedContainer;
    private ExoPlayer audioPlayer;

    // 进度相关：视频/音频续播 + 文本/Markdown 续读，与 Web 共用同一份数据
    private ExoPlayer videoPlayer;
    private PlayerView videoPlayerView;
    private ScrollView textScrollView;
    private Runnable videoTimerTask;   // 视频 10s 定时上报
    private Runnable audioTimerTask;   // 音频 10s 定时上报
    private Runnable scrollTimer;      // 文本/Markdown 滚动防抖上报
    private long videoPendingSeekMs;   // 视频续播目标（ms），就绪后消费
    private long audioPendingSeekMs;   // 音频续播目标（ms），就绪后消费
    private boolean videoPlayed;       // 视频已开始播放（允许存 0 清除历史）
    private boolean audioPlayed;       // 音频已开始播放（允许存 0 清除历史）
    private boolean forcedLandscape;   // 是否强制过横屏（离开时恢复传感器方向，避免旋转回传文件列表导致其重建丢目录）

    public static Intent newIntent(android.content.Context ctx, String fileId, String fileName,
                                   String mime, String privacyToken, long sizeBytes) {
        Intent i = new Intent(ctx, PreviewActivity.class);
        i.putExtra(EXTRA_FILE_ID, fileId);
        i.putExtra(EXTRA_FILE_NAME, fileName);
        i.putExtra(EXTRA_MIME, mime);
        i.putExtra(EXTRA_PRIVACY_TOKEN, privacyToken);
        i.putExtra(EXTRA_SIZE_BYTES, sizeBytes);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        fileId = getIntent().getStringExtra(EXTRA_FILE_ID);
        fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);
        mime = getIntent().getStringExtra(EXTRA_MIME);
        privacyToken = getIntent().getStringExtra(EXTRA_PRIVACY_TOKEN);
        sizeBytes = getIntent().getLongExtra(EXTRA_SIZE_BYTES, 0);

        session = SessionManager.getInstance(this);
        client = ApiClient.getInstance(session);

        previewContainer = findViewById(R.id.previewContainer);
        loadingContainer = findViewById(R.id.loadingContainer);
        errorContainer = findViewById(R.id.errorContainer);
        unsupportedContainer = findViewById(R.id.unsupportedContainer);

        ((TextView) findViewById(R.id.tvFileName)).setText(fileName != null ? fileName : getString(R.string.preview_title_default));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 下载按钮（不支持时显示）
        findViewById(R.id.btnDownload).setOnClickListener(v -> startDownload());
        findViewById(R.id.btnDownloadBottom).setOnClickListener(v -> startDownload());

        String category = previewCategory(mime);
        if (!"unsupported".equals(category)) {
            loadAndRender(category);
        } else {
            showUnsupported();
        }
    }

    /** 判断预览类型，与 Web 的 mimeCategory 对齐 */
    public static String previewCategory(String mime) {
        if (mime == null || mime.isEmpty()) { return "unsupported"; }
        if (mime.startsWith("image/")) return "image";
        if (mime.startsWith("video/")) return "video";
        if (mime.startsWith("audio/")) return "audio";
        if ("application/pdf".equals(mime)) return "pdf";
        if ("text/markdown".equals(mime)) return "markdown";
        if (mime.startsWith("text/") || "application/json".equals(mime) || "application/xml".equals(mime)) return "text";
        return "unsupported";
    }

    public static boolean canPreview(String mime) {
        return !"unsupported".equals(previewCategory(mime));
    }

    private void loadAndRender(String category) {
        loadingContainer.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                Response<ResponseBody> resp = client.previewFile(fileId, privacyToken).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    byte[] bytes = resp.body().bytes();
                    mainHandler.post(() -> {
                        loadingContainer.setVisibility(View.GONE);
                        render(category, bytes);
                    });
                } else {
                    mainHandler.post(this::showError);
                }
            } catch (Exception e) {
                mainHandler.post(this::showError);
            }
        }).start();
    }

    private void render(String category, byte[] bytes) {
        switch (category) {
            case "image": renderImage(bytes); break;
            case "text": renderText(bytes, false); break;
            case "markdown": renderText(bytes, true); break;
            case "audio": renderAudio(bytes); break;
            case "video": renderVideo(bytes); break;
            case "pdf": renderPdf(bytes); break;
            default: showUnsupported();
        }
    }

    private void renderImage(byte[] bytes) {
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        iv.setOnClickListener(v -> { /* 点击隐藏/显示头部，简单实现忽略 */ });
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        sv.addView(iv, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        previewContainer.addView(sv, lp);
    }

    private void renderText(byte[] bytes, boolean markdown) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        TextView tv = new TextView(this);
        if (markdown) {
            // Markwon 渲染 Markdown（标题/加粗/列表/代码块等原生样式）
            io.noties.markwon.Markwon.create(this).setMarkdown(tv, text);
        } else {
            tv.setText(text);
        }
        tv.setTextSize(15f);
        tv.setTextColor(0xFF1D1D1F);
        tv.setLineSpacing(4f, 1f);
        tv.setPadding(24, 24, 24, 24);
        ScrollView sv = new ScrollView(this);
        textScrollView = sv;
        sv.addView(tv);
        previewContainer.addView(sv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // 续读：拉到 SCROLL_PERCENT 后自动滚动到记录位置
        new Thread(() -> {
            double pct = ProgressReporter.fetchFileProgress(client, fileId);
            mainHandler.post(() -> applyTextProgress(sv, pct));
        }).start();
        // 上报：滚动防抖 2s（与 Web 预览一致）
        sv.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollTimer != null) mainHandler.removeCallbacks(scrollTimer);
            scrollTimer = () -> saveTextProgress(sv);
            mainHandler.postDelayed(scrollTimer, 2000);
        });
    }

    private void renderAudio(byte[] bytes) {
        try {
            File f = writeTemp(bytes, fileName);
            ExoPlayer player = new ExoPlayer.Builder(this).build();
            audioPlayer = player;
            // 音频无画面，用纯控制器（播放/暂停 + 进度条 + 时长），常驻显示
            PlayerControlView controlView = new PlayerControlView(this);
            controlView.setPlayer(player);
            controlView.setShowTimeoutMs(0);   // 0 = 不自动隐藏
            controlView.show();                // 音频没有可点击画面，控制器立即显示并保持
            // 续播：拉进度 → 就绪后 seek（未就绪先挂起 audioPendingSeekMs）
            new Thread(() -> {
                double s = ProgressReporter.fetchFileProgress(client, fileId);
                mainHandler.post(() -> {
                    if (audioPlayer != player || s <= 0) return;
                    audioPendingSeekMs = (long) (s * 1000);
                    if (player.getPlaybackState() == Player.STATE_READY) {
                        applyAudioPendingSeek(player);
                    }
                });
            }).start();
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY && audioPendingSeekMs > 0) {
                        applyAudioPendingSeek(player);
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying && !audioPlayed) {
                        audioPlayed = true;
                        startAudioTimer();
                    }
                }
            });
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(f)));
            player.prepare();
            player.setPlayWhenReady(true);

            TextView tv = new TextView(this);
            tv.setText(getString(R.string.preview_playing,
                    fileName != null ? fileName : getString(R.string.preview_audio_default)));
            tv.setTextSize(16f);
            tv.setTextColor(0xFF1D1D1F);
            tv.setGravity(android.view.Gravity.CENTER);
            // 垂直布局：文件名文字 + 常驻控制器
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(android.view.Gravity.CENTER);
            column.addView(tv);
            column.addView(controlView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            previewContainer.addView(column, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Exception e) {
            showError();
        }
    }

    private void renderVideo(byte[] bytes) {
        try {
            File f = writeTemp(bytes, fileName);
            // 横屏视频（旋转 90/270 或有效宽高为横）自动横屏播放
            if (isLandscapeVideo(f)) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                forcedLandscape = true;
            }
            // 应用内播放器：ExoPlayer + PlayerView（正确处理旋转元数据、宽高比、控制器时长显示）
            PlayerView playerView = new PlayerView(this);
            videoPlayerView = playerView;
            playerView.setUseController(true);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setControllerShowTimeoutMs(3000);
            ExoPlayer player = new ExoPlayer.Builder(this).build();
            videoPlayer = player;
            playerView.setPlayer(player);
            // 续播：拉进度 → 就绪后 seek（未就绪先挂起 videoPendingSeekMs）
            new Thread(() -> {
                double s = ProgressReporter.fetchFileProgress(client, fileId);
                mainHandler.post(() -> {
                    if (videoPlayer != player || s <= 0) return;
                    videoPendingSeekMs = (long) (s * 1000);
                    if (player.getPlaybackState() == Player.STATE_READY) {
                        applyVideoPendingSeek(player);
                    }
                });
            }).start();
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY && videoPendingSeekMs > 0) {
                        applyVideoPendingSeek(player);
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying && !videoPlayed) {
                        videoPlayed = true;
                        startVideoTimer();
                    }
                }
            });
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(f)));
            player.prepare();
            player.setPlayWhenReady(true);
            // 视频在容器内居中：非满屏（如 16:9 视频在更宽屏上左右留边）不贴左上角
            previewContainer.addView(playerView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                    android.view.Gravity.CENTER));
        } catch (Exception e) {
            showError();
        }
    }

    /**
     * 判断视频是否为横屏：旋转元数据 + 有效宽高综合判断。
     * 手机拍摄的横屏视频旋转元数据为 90/270；下载/转码的视频通常旋转为 0 但画面本身是横的
     * （宽 > 高）。只查旋转会漏判后者，导致不触发横屏、竖屏容器里贴顶显示留大片空白。
     */
    private boolean isLandscapeVideo(File f) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(f.getAbsolutePath());
            String rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            String wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            int rotation = 0;
            int w = 0;
            int h = 0;
            try {
                rotation = Integer.parseInt(rot);
            } catch (Exception ignored) { }
            try {
                w = Integer.parseInt(wStr);
                h = Integer.parseInt(hStr);
            } catch (Exception ignored) { }
            if (w > 0 && h > 0) {
                // 应用旋转后的有效宽高：旋转 90/270 时宽高互换
                boolean swapped = rotation % 180 == 90;
                int effW = swapped ? h : w;
                int effH = swapped ? w : h;
                return effW > effH;
            }
            // 读取不到尺寸时退化为仅旋转判断
            return rotation % 180 == 90;
        } catch (Exception e) {
            // 无法读取元数据时按竖屏处理，不强制旋转
            return false;
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
    }

    private void renderPdf(byte[] bytes) {
        try {
            File f = writeTemp(bytes, fileName);
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(pfd);

            // 渲染宽度上限 = 屏宽，避免高密度设备按 72dpi 放大出超大位图导致 OOM
            float densityScale = getResources().getDisplayMetrics().densityDpi / 72f;
            int maxWidth = getResources().getDisplayMetrics().widthPixels;

            LinearLayout pageList = new LinearLayout(this);
            pageList.setOrientation(LinearLayout.VERTICAL);

            for (int i = 0; i < renderer.getPageCount(); i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                int w = Math.max(1, (int) (page.getWidth() * densityScale));
                int h = Math.max(1, (int) (page.getHeight() * densityScale));
                if (w > maxWidth) {
                    float ratio = (float) maxWidth / w;
                    w = (int) (w * ratio);
                    h = (int) (h * ratio);
                }
                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(bmp);
                pageList.addView(iv, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                page.close();
            }
            renderer.close();
            pfd.close();

            ScrollView sv = new ScrollView(this);
            sv.addView(pageList);
            previewContainer.addView(sv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Exception e) {
            showError();
        }
    }

    private File writeTemp(byte[] bytes, String name) throws Exception {
        String safe = (name != null ? name.replaceAll("[^\\w.\\-]", "_") : "file");
        File f = new File(getCacheDir(), "preview_" + System.currentTimeMillis() + "_" + safe);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        }
        return f;
    }

    private void startDownload() {
        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra(DownloadService.EXTRA_FILE_ID, fileId);
        intent.putExtra(DownloadService.EXTRA_FILE_NAME, fileName);
        intent.putExtra(DownloadService.EXTRA_SIZE_BYTES, sizeBytes);
        startForegroundService(intent);
        android.widget.Toast.makeText(this, getString(R.string.files_download_started, fileName), android.widget.Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showUnsupported() {
        findViewById(R.id.btnDownload).setVisibility(View.VISIBLE);
        loadingContainer.setVisibility(View.GONE);
        unsupportedContainer.setVisibility(View.VISIBLE);
    }

    private void showError() {
        loadingContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.VISIBLE);
    }

    // ==================== 进度上报辅助 ====================

    /** 视频每 10s 上报当前秒数（与 Web 预览节奏一致） */
    private void startVideoTimer() {
        if (videoTimerTask != null) mainHandler.removeCallbacks(videoTimerTask);
        videoTimerTask = new Runnable() {
            @Override
            public void run() {
                if (videoPlayer != null) {
                    saveVideoProgress();
                    mainHandler.postDelayed(this, 10000);
                }
            }
        };
        mainHandler.postDelayed(videoTimerTask, 10000);
    }

    /** 音频每 10s 上报当前秒数 */
    private void startAudioTimer() {
        if (audioTimerTask != null) mainHandler.removeCallbacks(audioTimerTask);
        audioTimerTask = new Runnable() {
            @Override
            public void run() {
                if (audioPlayer != null) {
                    saveAudioProgress();
                    mainHandler.postDelayed(this, 10000);
                }
            }
        };
        mainHandler.postDelayed(audioTimerTask, 10000);
    }

    /** 音频上报：已播放过才允许存 0（seek 回开头时清除历史），未播放则避免误存 0 覆盖历史 */
    private void saveAudioProgress() {
        if (audioPlayer == null) return;
        long pos = audioPlayer.getCurrentPosition();
        if (pos > 0 || audioPlayed) {
            ProgressReporter.saveFileProgress(client, fileId,
                    ProgressReporter.TYPE_SECONDS, pos / 1000.0);
        }
    }

    /** 视频上报：已播放过才允许存 0（seek 回开头时清除历史），未播放则避免误存 0 覆盖历史 */
    private void saveVideoProgress() {
        if (videoPlayer == null) return;
        long pos = videoPlayer.getCurrentPosition();
        if (pos > 0 || videoPlayed) {
            ProgressReporter.saveFileProgress(client, fileId, ProgressReporter.TYPE_SECONDS, pos / 1000.0);
        }
    }

    /** 视频续播：应用挂起的 seek 并清 0 */
    private void applyVideoPendingSeek(ExoPlayer player) {
        if (videoPendingSeekMs <= 0) return;
        player.seekTo(videoPendingSeekMs);
        videoPendingSeekMs = 0;
        Toast.makeText(PreviewActivity.this, R.string.progress_resumed, Toast.LENGTH_SHORT).show();
    }

    /** 音频续播：应用挂起的 seek 并清 0 */
    private void applyAudioPendingSeek(ExoPlayer player) {
        if (audioPendingSeekMs <= 0) return;
        player.seekTo(audioPendingSeekMs);
        audioPendingSeekMs = 0;
        Toast.makeText(PreviewActivity.this, R.string.progress_resumed, Toast.LENGTH_SHORT).show();
    }

    /** 文本/Markdown 续读：自动滚动到记录百分比（布局完成后计算最大滚动距离） */
    private void applyTextProgress(ScrollView sv, double pct) {
        if (pct <= 0.01) return;
        sv.post(() -> {
            View child = sv.getChildAt(0);
            if (child == null) return;
            int max = child.getHeight() - sv.getHeight();
            if (max > 0) {
                sv.scrollTo(0, (int) (pct * max));
                Toast.makeText(PreviewActivity.this, R.string.progress_resumed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 文本/Markdown 上报滚动百分比（0~1；回顶 pct=0 也保存，用于清除历史进度） */
    private void saveTextProgress(ScrollView sv) {
        View child = sv.getChildAt(0);
        if (child == null) return;
        int max = child.getHeight() - sv.getHeight();
        if (max <= 0) return;
        double pct = Math.min(1, Math.max(0, (double) sv.getScrollY() / max));
        ProgressReporter.saveFileProgress(client, fileId, ProgressReporter.TYPE_SCROLL_PERCENT, pct);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 离开页面暂停播放（旋转由 configChanges 处理，不触发 onPause）
        if (videoPlayer != null) videoPlayer.pause();
        if (audioPlayer != null) audioPlayer.pause();
        // 强制过横屏且正在退出：先恢复传感器方向，让旋转发生在预览页自身，
        // 避免把旋转带回文件列表（MainActivity 被重建会丢失当前目录，Xiaomi 尤甚）
        if (forcedLandscape && isFinishing()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoTimerTask != null) mainHandler.removeCallbacks(videoTimerTask);
        if (audioTimerTask != null) mainHandler.removeCallbacks(audioTimerTask);
        if (scrollTimer != null) mainHandler.removeCallbacks(scrollTimer);
        // 退出前保存最终进度
        if (videoPlayer != null) {
            saveVideoProgress();
        }
        if (audioPlayer != null) {
            saveAudioProgress();
            audioPlayer.release();
            audioPlayer = null;
        }
        if (videoPlayerView != null) {
            videoPlayerView.setPlayer(null);
            videoPlayerView = null;
        }
        if (videoPlayer != null) {
            videoPlayer.release();
            videoPlayer = null;
        }
        if (textScrollView != null) {
            saveTextProgress(textScrollView);
        }
    }
}
