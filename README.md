<p align="center">
  <a href="screenshots/1.jpg"><img src="screenshots/1.jpg" alt="Screenshot 1" width="18%" /></a>
  <a href="screenshots/2.jpg"><img src="screenshots/2.jpg" alt="Screenshot 2" width="18%" /></a>
  <a href="screenshots/3.jpg"><img src="screenshots/3.jpg" alt="Screenshot 3" width="18%" /></a>
  <a href="screenshots/4.jpg"><img src="screenshots/4.jpg" alt="Screenshot 4" width="18%" /></a>
  <a href="screenshots/5.jpg"><img src="screenshots/5.jpg" alt="Screenshot 5" width="18%" /></a>
</p>

## 🔨更多 Smartisan 相关开源项目

- [awesome-smartisanOS](https://github.com/CashewTeam/awesome-smartisanOS) — 收集与 SmartisanOS（锤子科技操作系统）相关的优质 GitHub 项目、工具、资源和文章汇总。包括本项目 Nova Text、TNT Anywhere、锤子音乐、锤子桌面移植、HandShaker 维护版、足迹壁纸收藏等数十个项目，面向 SmartisanOS 生态的开发者、爱好者和用户。

# Nova Text

Nova Text 是经典 Smartisan OS「大爆炸」功能的 Android 原生迁移与现代化项目。

### UI 与系统特性
基于 **Jetpack Compose** 重构大部分 UI，适配高版本 Android 特性：**深色模式**、**自适应图标**、**多窗口支持**。

### 文本获取与处理
支持**无障碍权限直接提取文本**，继承经典「**炸了又炸**」交互（上下拖拽追加相邻段落），支持百度、谷歌、有道等**搜索/词典/百科**引擎，额外接入了 DuckDuckGo 与萌娘百科。

### 离线 OCR 识别
基于**无障碍截图 / MediaProjection / Shizuku 截屏** + **Google ML Kit** 实现纯离线 OCR，支持中文、日语、韩语、英语。自动命中触点附近的文本块，支持**自定义 OCR 白名单**，可在识别后**重新选择识别范围或切换识别语言**。

### 悬浮球交互
支持**停住再触发**（默认约 300 ms，可调）：拖到目标处按住不动再炸开，炸完球回到拖动前的位置；一路拖、中途不停直接松手则只挪位置、不触发。可选**不吸附两侧**，自由拖到屏幕任意位置（此时只显示圆球）。仍支持自定义**大小/透明度/自动隐藏**，以及贴边模式下的**锁定高度 / 左右侧锁定 / 单手优化**。

### 实验性触控（面积 / 压感）
Android 13+ 可用触控事件监听替代悬浮球。开启后可按**面积 / Size / 压感 / 多指**触发；支持配置**上下左右放行区宽度**（同一滑块），边缘触摸交给系统手势（上滑回桌面、侧滑返回），拖动滑块时有半透明蓝预览。面积触发与悬浮球同时只能启用一种。

## 下载

- **GitHub Releases（本 fork）**：<https://github.com/chenxun233/BigBang_NovaText/releases/latest>
- **上游 Releases**：<https://github.com/CashewTeam/BigBang_NovaText/releases/latest>

## License / 许可证说明

Nova Text 作为一个整体，以 **GNU General Public License v3.0（GPLv3）** 协议分发。完整协议文本见 [`LICENSE`](./LICENSE)。

本项目是基于 SmartisanTech 开源的 BigBang / BigBoom 应用继续开发的社区分支与现代化改造版本。原始项目基于 **Apache License 2.0** 发布，因此本仓库中来自原始 SmartisanTech BigBang / BigBoom 项目的代码、资源、版权声明与归属信息，仍然保留其原有的 Apache License 2.0 授权与声明。Apache License 2.0 协议文本见 [`LICENSE-Apache 2.0`](./LICENSE-Apache%202.0)。

本仓库的许可结构可以理解为：

* 本分支作为整体，包括 Nova Text 新增代码、重构代码、集成逻辑、现代化适配、构建系统调整和项目特定修改，按 **GPLv3** 分发。
* 来自原始 SmartisanTech BigBang / BigBoom 项目的部分，仍保留原始 **Apache License 2.0** 的版权声明、归属声明和许可要求。
* 基于原始项目修改过的文件，可能同时包含原始作者版权声明和本项目修改声明。
* 第三方库、依赖、字体、模型、词典、图标或其他资源，如果各自带有独立许可证，则仍遵循其各自的许可证条款。
* Apache License 2.0 与 GPLv3 在该方向上兼容：Apache-2.0 代码可以被纳入 GPLv3 项目中；但本分支中受 GPLv3 约束的新增代码和修改代码，不能在没有额外授权的情况下重新以 Apache-2.0 协议并入原始项目。

本项目是独立的社区分支，不隶属于 Smartisan / SmartisanTech，也未获得 Smartisan / SmartisanTech 的官方背书或赞助。Smartisan、BigBang、BigBoom、锤子科技、Smartisan OS 等名称可能是其各自权利人的商标或产品名称，仅用于说明项目来源与兼容背景。
