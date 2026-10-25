# Pi 语音助手 (pi-voice-assist)

把手机的「数字助理」变成 Pi 的语音入口：长按电源键（或助手手势）→ 说一句 → 文字自动进 pi-phone 的消息框并发出去。

- 只做三件事：听写（Android 自带语音识别）、把文字写进 pi-phone 的输入框（无障碍服务）、点发送
- 不读别的 App 的内容，不联网，不需要 root
- pi-phone 本身不需要改动

## 安装

从 Actions 的产物里取 `pi-assist-apk`，装好后打开 App，按里面三步走：麦克风 → 无障碍服务 → 设为默认数字助理。

## 说明

pi-phone（`io.piphone`）是另一个开源项目；本应用通过 Android 的无障碍服务把文字填进它的消息框。
输入框的位置或名称变了，可能需要在 `AssistService` 里跟着调整。
