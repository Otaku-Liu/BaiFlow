package com.baiflow.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.baiflow.android.R;
import com.baiflow.android.ui.NoteDrawView;

import java.io.ByteArrayOutputStream;

/**
 * 画画页 — 手写画布，保存时导出 PNG 字节经 Result 回传给编辑器插入为图片。
 */
public class NoteDrawActivity extends AppCompatActivity {

    /** 结果中携带的 PNG 字节数组 */
    public static final String EXTRA_PNG = "png";

    private NoteDrawView drawView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_draw);

        drawView = findViewById(R.id.drawView);
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        // 颜色（走资源色值，见 EditorColors 设计意图）
        findViewById(R.id.btnColorBlack).setOnClickListener(v -> drawView.setStrokeColor(getColor(R.color.text_primary)));
        findViewById(R.id.btnColorRed).setOnClickListener(v -> drawView.setStrokeColor(getColor(R.color.danger)));
        findViewById(R.id.btnColorBlue).setOnClickListener(v -> drawView.setStrokeColor(getColor(R.color.primary)));
        // 粗细
        findViewById(R.id.btnThin).setOnClickListener(v -> drawView.setStrokeWidth(4f));
        findViewById(R.id.btnThick).setOnClickListener(v -> drawView.setStrokeWidth(10f));
        // 撤销 / 清空
        findViewById(R.id.btnUndo).setOnClickListener(v -> drawView.undo());
        findViewById(R.id.btnClear).setOnClickListener(v -> drawView.clear());

        findViewById(R.id.btnDrawSave).setOnClickListener(v -> save());
    }

    private void save() {
        if (!drawView.hasContent()) {
            Toast.makeText(this, getString(R.string.note_draw_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        drawView.toBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        Intent result = new Intent();
        result.putExtra(EXTRA_PNG, out.toByteArray());
        setResult(RESULT_OK, result);
        finish();
    }
}
