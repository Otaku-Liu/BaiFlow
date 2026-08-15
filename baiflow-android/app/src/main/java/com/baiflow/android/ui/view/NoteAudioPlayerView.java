package com.baiflow.android.ui.view;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.baiflow.android.R;
import com.baiflow.android.data.MediaFiles;
import com.baiflow.android.network.ApiClient;

import java.io.File;
import java.io.FileOutputStream;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * 笔记音频块播放组件（真实 View，非 span）：播放/暂停 + 当前/总时长 + 进度条。
 * 音频来源：本地文件（离线创建/同步缓存）优先，否则从服务端下载到缓存再播。
 */
public class NoteAudioPlayerView extends LinearLayout {

    private ApiClient client;
    private MediaPlayer player;
    private boolean prepared;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (player != null && prepared && player.isPlaying()) {
                seekBar.setProgress(player.getCurrentPosition());
                updateTime();
            }
            handler.postDelayed(this, 500);
        }
    };

    private TextView btnPlay;   // 用 TextView 而非 Button，避免系统按钮默认样式
    private TextView tvTime;
    private SeekBar seekBar;
    private long durationMs;
    private long knownDurationMs = -1;   // 已知时长（URL &duration=，优先用于显示，避免误读）

    public NoteAudioPlayerView(Context context) {
        super(context);
        init(context);
    }

    public NoteAudioPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        setPadding(0, 2, 0, 2);

        btnPlay = new TextView(context);
        btnPlay.setTextSize(15f);
        btnPlay.setGravity(android.view.Gravity.CENTER);
        btnPlay.setContentDescription(context.getString(R.string.note_edit_playing));
        btnPlay.setOnClickListener(v -> toggle());
        // 播放键/进度条按比例缩小，避免音频块太高/溢出
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(26), dp(26));
        addView(btnPlay, btnLp);

        tvTime = new TextView(context);
        tvTime.setTextSize(12f);
        tvTime.setMinWidth(dp(72));   // 固定时间区宽度，避免时长加载后整块宽度跳动
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        timeLp.setMarginStart(dp(6));
        addView(tvTime, timeLp);

        seekBar = new SeekBar(context);
        seekBar.setPadding(0, 0, 0, 0);
        seekBar.setMinimumHeight(dp(24));
        seekBar.setMinimumWidth(dp(100));   // 进度条最小宽度，保证可操作、不太短
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
                0, dp(28), 1f);
        seekLp.setMarginStart(dp(8));
        seekLp.setMarginEnd(dp(8));
        addView(seekBar, seekLp);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && player != null && prepared) {
                    player.seekTo(progress);
                    updateTime();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        updateUi(false);
    }

    /** 绑定音频（url 本地或服务端；mediaId 用于下载；knownDurationMs 为 URL 里记录的已知时长） */
    public void bind(String url, String mediaId, long knownDurationMs) {
        this.knownDurationMs = knownDurationMs;
        release();
        File local = MediaFiles.resolveLocal(getContext(), url);
        if (local != null && local.exists()) {
            load(local);
            return;
        }
        if (mediaId == null || mediaId.isEmpty()) {
            updateUi(false);
            return;
        }
        new Thread(() -> {
            try {
                Response<ResponseBody> resp = client.getNoteMedia(mediaId).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    byte[] bytes = resp.body().bytes();
                    File f = new File(MediaFiles.cacheDir(getContext()), mediaId);
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(bytes);
                    }
                    handler.post(() -> load(f));
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void load(File f) {
        try {
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                prepared = true;
                // 优先用已知时长（URL 记录的真实录音时长），MediaPlayer 可能误读（如 1s 读成 2s）
                durationMs = knownDurationMs > 0 ? knownDurationMs : mp.getDuration();
                if (durationMs <= 0) {
                    durationMs = 0;
                }
                seekBar.setMax((int) durationMs);
                updateUi(false);   // 仅准备完成、未开始播放，应显示 ▶
                updateTime();
                handler.post(progressTick);
            });
            player.setOnCompletionListener(mp -> {
                updateUi(false);
                seekBar.setProgress(seekBar.getMax());
                updateTime();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                release();
                Toast.makeText(getContext(), R.string.note_edit_play_failed, Toast.LENGTH_SHORT).show();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.note_edit_play_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggle() {
        if (player == null || !prepared) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            updateUi(false);
        } else {
            if (player.getCurrentPosition() >= seekBar.getMax() && seekBar.getMax() > 0) {
                player.seekTo(0);
            }
            player.start();
            updateUi(true);
            handler.post(progressTick);
        }
    }

    private void updateUi(boolean playing) {
        btnPlay.setText(playing ? "⏸" : "▶");
    }

    private void updateTime() {
        long pos = player != null ? player.getCurrentPosition() : 0;
        tvTime.setText(fmt(pos) + " / " + fmt(durationMs));
    }

    private static String fmt(long ms) {
        long totalSec = ms / 1000;
        long m = totalSec / 60;
        long s = totalSec % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    public void setClient(ApiClient client) {
        this.client = client;
    }

    /** 释放播放器（RecyclerView 复用/销毁时调用） */
    public void release() {
        handler.removeCallbacks(progressTick);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        prepared = false;
        durationMs = 0;
        seekBar.setProgress(0);
        updateUi(false);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
