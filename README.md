本程序源自:https://github.com/labexp/osmtracker-android
感谢原作者提供源码，本人做如下修改。
*需通过该程序采集演示用机的经纬度、亮屏时长信息，并反馈至相关管理人员邮箱，与基础数据比对后考核每台演示用机的使用情况。
*imei号采集
*定期邮件采集数据
*程序需开机自启动
*将原版程序和应用ku框架升级到最新版本
*去除openstreet联网部分
***自己编译注意:需要增加【local.properties】内的属性，不支持TLS/SSL加密仅支持明文(一般为端口25):
smtpServer = 发送服务器
smtpUser = 用户名
smtpPass = 密码

以下为原始说明：
[<img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png' height="80"/>](https://play.google.com/store/apps/details?id=net.osmtracker)
[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/app/net.osmtracker)

OSMTracker for Android™ official source code repository is [https://github.com/labexp/osmtracker-android](https://github.com/labexp/osmtracker-android).

[![Build Status](https://travis-ci.org/labexp/osmtracker-android.svg?branch=master)](https://travis-ci.org/labexp/osmtracker-android)

For more information about the project, documentation and bug reports please visit https://github.com/labexp/osmtracker-android/wiki

If you are interested in contribute to this project, please visit https://github.com/labexp/osmtracker-android/blob/master/CONTRIBUTING.md to know the way you could do it. 

To help translate OSMTracker, please visit https://www.transifex.com/projects/p/osmtracker-android/
