package com.yichan.assist;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The three switches a person must set once: the microphone, the accessibility service, and this app as the phone's
 * assistant. Everything here opens the system's own screen for it; nothing is granted silently.
 */
public final class SetupActivity extends Activity {

    private LinearLayout column;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        column.setPadding(pad, pad, pad, pad);
        scroll.addView(column);
        setContentView(scroll);

        setTitle("Pi 语音助手");
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        column.removeAllViews();

        title("Pi 语音助手");
        note("长按电源键（或手机的助手手势）唤起我 → 说一句 → 文字进 pi-phone 的消息框"
                + "（默认直接发送），Pi 收到就开始干活。");

        step("1. 麦克风", hasMic() ? "已允许" : "未允许",
                hasMic() ? null : "允许麦克风", () -> requestPermissions(
                        new String[] { android.Manifest.permission.RECORD_AUDIO }, 1));

        step("2. 无障碍服务", serviceOn() ? "已开启" : "未开启",
                serviceOn() ? "打开设置看看" : "去开启",
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        step("3. 设为默认数字助理", isAssistant() ? "已是默认助手" : "还不是",
                isAssistant() ? null : "设为默认助手", this::askRole);

        SharedPreferences prefs = getSharedPreferences(AssistService.PREFS, MODE_PRIVATE);
        Switch auto = new Switch(this);
        auto.setText("听到后直接发送（关掉则只填进输入框，你自己点发送）");
        auto.setChecked(prefs.getBoolean(AssistService.AUTO_SEND, true));
        auto.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(AssistService.AUTO_SEND, checked).apply());
        auto.setPadding(0, dp(18), 0, 0);
        column.addView(auto);

        Button tryIt = new Button(this);
        tryIt.setText("试一试");
        tryIt.setOnClickListener((View v) ->
                startActivity(new Intent(this, AssistActivity.class).setAction(Intent.ACTION_ASSIST)));
        tryIt.setPadding(0, dp(18), 0, 0);
        column.addView(tryIt);

        note("说明：无障碍服务只用来把听写结果写进 pi-phone 的输入框并点发送，不读别的 App 的内容。");
    }

    private void askRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            note("这台系统太旧，请在 设置 › 应用 › 默认应用 › 数字助理 里选择");
            return;
        }

        RoleManager roles = getSystemService(RoleManager.class);

        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT));
        } else {
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        }
    }

    private boolean isAssistant() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;

        RoleManager roles = getSystemService(RoleManager.class);
        return roles != null && roles.isRoleHeld(RoleManager.ROLE_ASSISTANT);
    }

    private boolean hasMic() {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean serviceOn() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName() + "/" + AssistService.class.getName());
    }

    private void step(String label, String state, String action, Runnable run) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(18), 0, 0);

        TextView head = new TextView(this);
        head.setText(label + "：" + state);
        head.setTextSize(17);
        head.setTextColor(state.startsWith("已") || state.startsWith("不") ? Color.parseColor("#7AA2F7") : Color.parseColor("#F7768E"));
        row.addView(head);

        if (action != null && run != null) {
            Button button = new Button(this);
            button.setText(action);
            button.setOnClickListener((View v) -> run.run());
            row.addView(button);
        }

        column.addView(row);
    }

    private void title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(24);
        view.setTextColor(Color.WHITE);
        column.addView(view);
    }

    private void note(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.parseColor("#A9B1D6"));
        view.setPadding(0, dp(10), 0, 0);
        column.addView(view);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
