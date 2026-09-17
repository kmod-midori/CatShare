# CatShare
类原生 & 海外设备，现已加入互传联盟。

Android 目前已不再支持非系统应用获取手机的 MAC 地址等无法重置的序列号，但由于各品牌的互传功能通常为系统应用，互传联盟协议将设备的 MAC 地址作为其认证信息的一部分。本应用提供以下方案获取 WiFi Direct P2P 接口 (p2p0) 的 MAC 地址：

### MAC 地址获取方案（按优先级）
1. **手动配置** - 在设置中手动输入设备的 MAC 地址，可使用命令获取：`adb shell ip addr show p2p0`
**（设备待机和发起传输时 MAC 地址可能不同，需注意抓取发起传输时的 MAC 地址）**
2. **系统权限** - 设备已授予 `android.permission.LOCAL_MAC_ADDRESS` 权限，例如 App 已由系统签名的情况。
3. **Shizuku / Root** - 通过提升权限的方式自动获取

### ⚠️ 已知事项
- 部分 Samsung 设备的 WiFi Direct MAC 地址可能会周期性变动，该情况下手动配置无效

本 App 的 GitHub Release 和 F-Droid 版本签名一致， F-Droid 版本可能相对滞后，可以任意选择。

[<img src="https://f-droid.org/badge/get-it-on-zh-cn.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/moe.reimu.catshare)
[<img src="https://www.openapk.net/images/openapk-badge.png"
    alt="Get it on OpenAPK"
    height="80">](https://www.openapk.net/catshare/moe.reimu.catshare/)
[<img src="https://www.androidfreeware.net/images/androidfreeware-badge.png"
    alt="Get it on Android Freeware"
    height="80">](https://www.androidfreeware.net/download-catshare-apk.html)

## 功能
- [x] 蓝牙发现
- [x] 文件接收
- [x] 文件发送（需要正确配置 MAC 地址，可通过手动输入、系统权限或 Shizuku 获取）
- [x] 文本传输（两侧均为 CatShare 时复制至剪贴板，接收方为其他设备时以文本文件形式发送） 

## 支持设备（已测试）
| 品牌        | 向该设备发送 | 从该设备接收            |
| ----------- | ------------ | ----------------------- |
| 小米        | Y            | Y                       |
| OPPO/一加等 | Y            | Y，但发送端提示接收失败 |
| vivo        | Y            | Y                       |

## 汇报问题

你可以在该项目的 issue 区汇报你在使用 CatShare 期间遇到的问题，尽量的，请附上 CatShare 的 adb logcat 日志。

通过该命令获取 CatShare 的日志。
<details>
<summary>release(正式版)</summary>

shell(linux)
```shell
adb logcat --pid $(adb shell pidof -s moe.reimu.catshare)
```
cmd(windows)
```shell
for /f "tokens=1" %i in ('adb shell pidof -s moe.reimu.catshare') do adb logcat --pid %i
```
</details>
<details>
<summary>debug(测试版)</summary>

shell(linux)
```shell
adb logcat --pid $(adb shell pidof -s moe.reimu.catshare.debug)
```
cmd(windows)
```shell
for /f "tokens=1" %i in ('adb shell pidof -s moe.reimu.catshare.debug') do adb logcat --pid %i
```
</details>
建议尽可能完整的截取日志，并注释从什么时候发送或接收内容，尽量使用折叠块语法来包裹日志内容。

````markdown
<details>
<summary>Details</summary>

```
在此处填入日志内容，注意其应被包裹在反括号代码块内
```

</details>
````
