# 电视系统精简指南（实测版）

> 基于小米电视 4X 55 英寸（MiTV4-ANSM0，Android 6.0.1）实测整理。
> 目标：减少开机广告、后台推送与统计上报，让电视回归「干净桌面 + 轻量应用」。
> 适用：小米 / 红米电视及同类 MIUI TV 固件，其他品牌可参考分类思路。

## ⚠️ 重要提醒（先读三遍）

1. **保留还原入口**：不要卸载任何「核心」组件（见下文），尤其不要卸载原桌面（如 PatchWall `com.mitv.tvhome`）——它是系统 HOME 兜底，也是你日后恢复默认桌面的唯一入口。
2. **禁用 ≠ 卸载**：优先使用 `pm disable-user --user 0 <包名>`（可随时 `pm enable` 恢复）。
   - 实测发现：部分 MIUI 固件未给 adb shell 授予 `CHANGE_COMPONENT_ENABLED_STATE` 权限，`pm disable-user` 会被拒绝（`SecurityException: Permission Denial`）。
   - 此时改用社区标准方法 **`pm uninstall --user 0 <包名>`**：把应用从你的用户空间移除（不显示、不运行），**APK 仍保留在系统分区**，效果等同禁用，且可用 `pm install -r --user 0 <系统APK路径>` 随时恢复（实测成功）。
3. **记录再动手**：精简前先 `pm list packages -s > 系统包清单.txt` 留档；每删一个都记下 APK 路径（见恢复表格式）。
4. 精简后按主页键若桌面异常，重启电视或恢复对应包即可，不会变砖。

## 精简原则

| 等级 | 分类 | 处理建议 |
|---|---|---|
| 🔴 核心 | 系统框架 / 系统UI / 设置 / 遥控 / 视频解码 / 账号 / 原桌面 | **绝不精简** |
| 🟡 功能 | 投屏、语音、商店、支付、输入法等 | 按需决定（下列每组写明后果） |
| 🟢 广告统计 | 广告、推送、分析、统计 | **建议精简** |
| ⚪ 无用内置 | 天气、画报、推荐流、清理工具 | **建议精简** |

## 建议精简清单

### 🟢 广告 / 统计（收益最大，放心删）

| 包名 | 作用 | 删除影响 |
|---|---|---|
| com.miui.systemAdSolution | 小米系统广告 | 开机/桌面/弹窗广告减少 |
| com.xiaomi.mitv.tvpush.tvpushservice | 电视推送服务 | 推送广告减少 |
| com.miui.tv.analytics | 电视分析统计 | 停止使用数据上报 |
| com.xiaomi.statistic | 小米统计 | 停止统计上报 |

### ⚪ 无用内置（零影响）

| 包名 | 作用 | 删除影响 |
|---|---|---|
| com.xiaomi.tweather | 小米天气 | 无 |
| com.xiaomi.tv.gallery | 电视画报（壁纸） | 桌面壁纸轮播不再更新（第三方桌面不受影响） |
| com.duokan.videodaily | 视频每日（推荐流） | 推荐短视频入口消失 |
| cleantools.mitv.com.tvcleantools | 电视清理工具 | 无（电视管家可替代） |

### 🟡 功能组件（按需，每组写明后果）

**确认用不到再删——投屏组**（删后手机投屏 / 无线显示 / DLNA 全失效）：
`com.xiaomi.miplay`、`com.mi.miplay.mitvupnpsink`、`com.xiaomi.upnp`、`com.xiaomi.mitv.smartshare`、`com.duokan.airkan.tvbox`、`com.xiaomi.mi_connect_service`

**语音组**：`com.xiaomi.voicecontrol`（小爱语音；注意它在系统无障碍服务列表，删除后需同步清理该条目）

**娱乐组**：`com.xiaomi.mitv.karaoke.service`（全民K歌）、`com.xiaomi.gamecenter.sdk.service.mibox`（游戏中心）、`com.xiaomi.smarthome.tv.service`（米家电视版）

**系统功能组**（按需）：
- `com.xiaomi.mitv.appstore`（应用商店 → 之后只能 adb / U 盘装应用）
- `com.xiaomi.mitv.upgrade`（系统升级 → 不再收 OTA）
- `com.xiaomi.mitv.payment` / `com.xiaomi.mitv.pay`（电视支付）
- `com.xiaomi.mitv.tvmanager`（电视管家）
- `com.xiaomi.tvqs`（全局搜索）
- `com.mitv.alarmcenter`（闹钟）
- `com.mitv.cloudcontrol`（云控制，手机 App 远程管理失效）
- `com.xiaomi.mibox.lockscreen`（锁屏 / 儿童模式）
- `com.xiaomi.screenrecorder`（屏幕录制）
- `com.sohu.inputmethod.sogou.tv`（搜狗输入法 → **删后所有文字输入失效**，慎删）
- `com.xiaomi.mitv.mediaexplorer`（本地媒体浏览）
- `com.mitv.videoplayer` / `com.mitv.mivideoplayer`（本地视频播放）
- `com.xiaomi.mitv.legal.webview`（法律条款页，几乎无影响）
- `com.mitv.screensaver`（屏保）

### 🔴 绝对不要动（核心 31 项节选）

`android`、`com.android.systemui`、`com.android.packageinstaller`、`com.android.providers.*`、`com.xiaomi.mitv.settings`、`com.xiaomi.mitv.systemui`、`com.xiaomi.mitv.remotecontroller.service`（遥控器）、`com.xiaomi.mitv.tvplayer`（视频解码）、`mitv.service`、`com.droidlogic`（硬件框架）、`com.xiaomi.account*`（账号）、`com.xiaomi.mitv.debug`（adb）、`com.mitv.tvhome`（原桌面）

## 执行命令

```bash
# 查看系统应用与当前已禁用/已移除状态
adb shell "pm list packages -s"
adb shell "pm list packages -d"

# 方法一：禁用（推荐，固件允许时）
adb shell "pm disable-user --user 0 <包名>"

# 方法二：用户空间移除（disable 被拒时的替代，完全可逆）
adb shell "pm uninstall --user 0 <包名>"

# 查看无障碍服务列表（删掉语音/K歌后需同步清理失效条目）
adb shell settings get secure enabled_accessibility_services
```

## 一键还原

```bash
# 方法一还原
adb shell "pm enable <包名>"

# 方法二还原（需要系统 APK 路径，精简时务必记录）
adb shell "pm install -r --user 0 <系统APK路径>"
```

恢复路径示例（小米电视 4X 实测）：

| 包名 | 系统 APK 路径 |
|---|---|
| com.miui.systemAdSolution | /data/app/com.miui.systemAdSolution-1/base.apk |
| com.xiaomi.mitv.tvpush.tvpushservice | /system/vendor/app/TVPushService/TVPushService.apk |
| com.miui.tv.analytics | /data/app/com.miui.tv.analytics-2/base.apk |
| com.xiaomi.statistic | /system/vendor/app/MiTVStat/MiTVStat.apk |
| com.xiaomi.tweather | /data/app/com.xiaomi.tweather-1/base.apk |
| com.xiaomi.tv.gallery | /data/app/com.xiaomi.tv.gallery-2/base.apk |
| com.duokan.videodaily | /data/app/com.duokan.videodaily-2/base.apk |
| cleantools.mitv.com.tvcleantools | /system/vendor/app/MobileService/MobileService.apk |
| com.xiaomi.mitv.karaoke.service | /system/app/KaraokeService/KaraokeService.apk |
| com.xiaomi.gamecenter.sdk.service.mibox | /data/app/com.xiaomi.gamecenter.sdk.service.mibox-1/base.apk |
| com.xiaomi.smarthome.tv.service | /system/vendor/app/TVSmartHomeAssistant/TVSmartHomeAssistant.apk |
| com.xiaomi.screenrecorder | /data/app/com.xiaomi.screenrecorder-1/base.apk |
| com.xiaomi.mibox.lockscreen | /data/app/com.xiaomi.mibox.lockscreen-1/base.apk |
| com.mitv.alarmcenter | /data/app/com.mitv.alarmcenter-2/base.apk |
| com.mitv.cloudcontrol | /data/app/com.mitv.cloudcontrol-1/base.apk |
| com.xiaomi.tvqs | /data/app/com.xiaomi.tvqs-2/base.apk |

> 若系统 OTA 更新把部分应用装回来，对相应包重跑 `pm uninstall --user 0 <包名>` 即可再次精简。

## 精简后的「黄金组合」

```
HugoTV 桌面（开机直达，无广告） + 精简后的系统（无推送无统计）
+ ADB 常开（方便随时管理） + 需要时用 pm install 恢复任何组件
```

配合同一项目的 `README.md` 安装 HugoTV 并设为默认桌面，即可得到一个干净、快速、无广告的电视。
