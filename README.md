# CatShare
类原生 & 海外设备，现已加入互传联盟。

Android 目前已不再支持非系统应用获取手机的 MAC 地址等无法重置的序列号，但由于各品牌的互传功能通常为系统应用，互传联盟协议将设备的 MAC 地址作为其认证信息的一部分，目前暂时无法绕过。

[<img src="https://f-droid.org/badge/get-it-on-zh-cn.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/moe.reimu.catshare)

## 功能
- [x] 蓝牙发现
- [x] 文件接收
- [x] 文件发送（需要 Shizuku 支持）

## 支持设备（已测试）
| 品牌        | 向该设备发送 | 从该设备接收            |
| ----------- | ------------ | ----------------------- |
| 小米        | Y            | Y                       |
| OPPO/一加等 | Y            | Y，但发送端提示接收失败 |
| vivo        | Y            | Y                       |

## 汇报问题

你可以在该项目的 issue 区汇报你在使用 CatShare 期间遇到的问题，尽量的，请附上 CatShare 的 adb logcat 日志。

通过该命令获取 CatShare 的日志。

```shell
adb logcat --pid $(adb shell pidof -s moe.reimu.catshare)
```

建议尽可能完整的截取日志，并注释从什么时候发送或接收内容，尽量使用折叠块语法来包裹日志内容。

````markdown
<details>
<summary>Details</summary>

```
在此处填入日志内容，注意其应被包裹在反括号代码块内
```

</details>
````
