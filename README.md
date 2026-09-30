# Xiaomi Watch 5 Neon Core Blue

可安裝到 Xiaomi Watch 5 的 Wear OS Watch Face Format (WFF) 錶盤專案。

## 目前功能

- 黑色 AMOLED 背景
- 藍青霓虹外圈
- 上左：天氣 complication
- 上右：電量
- 中央：大型數位時間
- 日期只顯示一次
- 下左：步數
- 下右：心率
- AOD：保留外圈、時間、日期
- Windows 一鍵編譯＋安裝到手錶

## 第一次使用

1. Xiaomi Watch 5 開啟「開發人員選項 → 無線偵錯」。
2. 第一次先用 `adb pair` 完成配對。
3. 確認 `adb devices` 至少有一行顯示 `device`。
4. 雙擊 `INSTALL_WATCH.cmd`。
5. 安裝完成後，在手錶長按目前錶盤 → 新增錶盤 → 選擇 **Neon Core Blue**。

## 之後更新

在此專案資料夾執行：

```powershell
git pull
```

接著雙擊：

```text
INSTALL_WATCH.cmd
```

即可重新編譯並安裝最新版。

## 必要環境

- Windows 10 / 11
- Android SDK
- Android SDK Platform-Tools
- Xiaomi Watch 5 已開啟無線偵錯

> `local.properties`、Gradle build 產物及安裝 log 不會提交到 GitHub。

## Desktop shortcut

Run `CREATE_DESKTOP_SHORTCUT.cmd` once. It creates a single desktop shortcut named **Xiaomi Watch 5 NeonFace**. After that, open the manager from the desktop shortcut; the project itself can remain on drive D:.


## 手機更新器

Repository 內已加入 Android 手機端 NeonFace 更新器。它可以：

- 從 GitHub Releases 下載最新版 Neon Core Blue 錶盤 APK。
- 透過 Wear OS 無線偵錯進行配對。
- 從手機直接連線 Xiaomi Watch 5。
- 將最新版 APK 傳到手錶並安裝，不需要電腦。

第一次啟用固定簽章前，在 Windows 執行：

```powershell
git pull
powershell -ExecutionPolicy Bypass -File .\SETUP_RELEASE_SIGNING.ps1
```

完成 GitHub Actions secrets 後，GitHub Actions 會產生：

- `NeonCoreBlue-watch.apk`
- `NeonFace-Updater.apk`

並放到固定的 `latest` Release。手機更新器會直接下載這個最新版錶盤。
