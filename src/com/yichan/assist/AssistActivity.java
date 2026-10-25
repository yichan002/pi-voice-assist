package com.yichan.assist;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Android's assistant entry (ACTION_ASSIST): listens to one sentence and hands it to pi-phone through
 * {@link AssistService}, which types it into the message box. Debug: `am start -a android.intent.action.ASSIST
 * -n com.yichan.assist/.AssistActivity --es text "..."` skips the microphone.
 */
public final class AssistActivity extends Activity implements RecognitionListener {

    private SpeechRecognizer recognizer;
    private TextView status;
    private boolean delivered;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());

        Intent intent = getIntent();
        String given = intent == null ? null : intent.getStringExtra("text");

        if (given != null && !given.isEmpty()) {
            hand(given);
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("这台手机没有语音识别服务");
            return;
        }

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("需要麦克风权限");
            requestPermissions(new String[] { android.Manifest.permission.RECORD_AUDIO }, 1);
            return;
        }

        listen();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code != 1) return;

        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            listen();
        } else {
            status.setText("没有麦克风权限，听不到");
        }
    }

    private void listen() {
        if (recognizer != null) {
            recognizer.destroy();
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        status.setText("在听…");
        recognizer.startListening(intent);
    }

    /** One sentence is the whole job: pi-phone takes it from here. */
    private void hand(String text) {
        if (delivered) return;

        delivered = true;

        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }

        AssistService.deliver(this, text.trim());
        finish();
    }

    private LinearLayout buildUi() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setBackgroundColor(Color.parseColor("#1A1B26"));

        int pad = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics()));
        column.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setText("在听…");
        status.setTextColor(Color.WHITE);
        status.setTextSize(22);
        column.addView(status);

        Button cancel = new Button(this);
        cancel.setText("取消");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = pad;
        cancel.setLayoutParams(params);
        cancel.setOnClickListener((View v) -> {
            if (recognizer != null) {
                recognizer.cancel();
                recognizer.destroy();
                recognizer = null;
            }

            finish();
        });
        column.addView(cancel);

        return column;
    }

    @Override
    public void onPartialResults(Bundle partial) {
        ArrayList<String> heard = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        if (heard != null && !heard.isEmpty()) {
            status.setText(heard.get(0));
        }
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        if (heard == null || heard.isEmpty()) {
            status.setText("没听清，再说一次");
            listen();
            return;
        }

        hand(heard.get(0));
    }

    @Override
    public void onError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                status.setText("没听清，再说一次");
                listen();
                break;
            default:
                status.setText("语音识别出错（" + error + "）");
                break;
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {}

    @Override
    public void onBeginningOfSpeech() {}

    @Override
    public void onRmsChanged(float rms) {}

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {
        status.setText("…");
    }

    @Override
    public void onEvent(int type, Bundle params) {}
}
