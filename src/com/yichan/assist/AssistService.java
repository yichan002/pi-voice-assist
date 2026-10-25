package com.yichan.assist;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The bridge from a spoken sentence to pi-phone's message box: opens the app, types the words into its composer
 * (a WebView textarea, which Android shows as an editable node) and, unless the person turned that off, taps Send.
 * Android's accessibility service is the only way into another app's screen without that app's help.
 */
public final class AssistService extends AccessibilityService {

    static final String PREFS = "assist";
    static final String AUTO_SEND = "auto-send";

    private static AssistService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Called by {@link AssistActivity} right after the sentence was heard. */
    static void deliver(Context context, String text) {
        AssistService service = instance;

        if (service == null) {
            Toast.makeText(context, "先在「Pi 语音助手」里打开无障碍服务", Toast.LENGTH_LONG).show();
            return;
        }

        service.begin(text);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }

        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    private void begin(String text) {
        Intent launch = getPackageManager().getLaunchIntentForPackage("io.piphone");

        if (launch == null) {
            toast("这台手机上没有装 pi-phone");
            return;
        }

        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(launch);
        type(0, text);
    }

    /** pi-phone opens cold in a few seconds: look for its box until it is there. */
    private void type(int tries, String text) {
        handler.postDelayed(() -> {
            AccessibilityNodeInfo field = findField();

            if (field == null) {
                if (tries < 40) {
                    type(tries + 1, text);
                } else {
                    toast("没找到 pi-phone 的消息框");
                }

                return;
            }

            if (!setText(field, text)) {
                if (!paste(field, text)) {
                    clipboard(text);
                    toast("已复制到剪贴板，长按消息框粘贴");
                    return;
                }
            }

            if (autoSend()) {
                handler.postDelayed(() -> send(), 700);
            }
        }, tries == 0 ? 1200 : 400);
    }

    private boolean autoSend() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        return prefs.getBoolean(AUTO_SEND, true);
    }

    private AccessibilityNodeInfo findField() {
        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) return null;

        List<AccessibilityNodeInfo> editables = new ArrayList<>();
        collect(root, editables, 0);

        AccessibilityNodeInfo best = null;
        for (AccessibilityNodeInfo node : editables) {
            if (best == null || area(node) > area(best)) best = node;
        }

        return best;
    }

    private void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out, int depth) {
        if (node == null || depth > 40) return;

        CharSequence className = node.getClassName();

        if (node.isEditable() || (className != null && className.toString().contains("EditText"))) {
            out.add(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), out, depth + 1);
        }
    }

    private static long area(AccessibilityNodeInfo node) {
        android.graphics.Rect rect = new android.graphics.Rect();
        node.getBoundsInScreen(rect);
        return (long) rect.width() * rect.height();
    }

    private boolean setText(AccessibilityNodeInfo node, String text) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);

        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private boolean paste(AccessibilityNodeInfo node, String text) {
        clipboard(text);

        if (!node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return false;

        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return true;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        return root != null && root.performAction(AccessibilityNodeInfo.ACTION_PASTE);
    }

    private void clipboard(String text) {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("Pi", text));
    }

    /** The composer's Send: its text, or failing that the keyboard's action on the box. */
    private void send() {
        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) return;

        AccessibilityNodeInfo send = findSend(root, 0);

        if (send != null && send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;

        AccessibilityNodeInfo field = findField();

        if (field != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (field.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)) return;
        }

        toast("词已填好，点一下发送");
    }

    private AccessibilityNodeInfo findSend(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 40) return null;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String label = (text == null ? "" : text.toString()) + " " + (desc == null ? "" : desc.toString());
        String lower = label.toLowerCase();

        if (node.isClickable()
                && (lower.contains("发送") || lower.contains("send") || lower.contains("send message"))) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findSend(node.getChild(i), depth + 1);

            if (found != null) return found;
        }

        return null;
    }

    private void toast(String message) {
        handler.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }
}
