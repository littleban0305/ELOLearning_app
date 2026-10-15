# ELOLearning Android App

原生 Android 學習助手，安裝後顯示名稱為 **ELOLearning**。支援 AI 學習問答、筆記管理、依筆記出題與交卷批改。筆記保存在手機本機；Gemini API key 使用 Android Keystore 加密保存。

## 功能

- 依年級、科目與主題整理 AI 學習重點
- 自動保存、手動新增、編輯與刪除筆記
- 根據已保存筆記產生四選一練習題，交卷後顯示分數與解析
- 可選 1 至 50 題、簡單/普通/困難，以及四選一/是非/填空/混合題型
- 題目逐題作答，支援上一題/下一題，交卷前會再次確認
- 設定個人顯示名稱與 Gemini API key

首次使用需在「設定」輸入自己的 Gemini API key。AI 請求會傳送至 Google Gemini。

## 建置 APK

使用 Android Studio 開啟專案，安裝 JDK 17 和 Android SDK Platform 35，再執行 `:app:assembleDebug`。

GitHub Actions 會在推送 `main` 或手動執行 workflow 時建置 APK。成功後到該次 workflow 執行摘要的 Artifacts 下載 `ELOLearning-android-apk`。
