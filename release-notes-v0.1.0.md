# wechat-export v0.1.0

**English**

First release of wechat-export — export **your own** WeChat Moments from a rooted Android phone to JSON / Markdown / HTML, no PC required.

- One-tap export (button ②, root): extracts `SnsMicroMsg.db` (+ WAL sidecars), parses `SnsInfo` / `SnsComment`, decodes WeChat 8.0.x protobuf blobs (`content`, `attrBuf` — incl. likes & comments)
- Combined output, newest first: `exported_sns.json` / `exported_sns.md` / `exported_sns.html`
- Per-month output, newest first: `YYYY-MM.md` / `YYYY-MM.html`
- Import existing moments JSON (button ①) and render it
- Zero Android permissions (no `INTERNET`); root is only invoked on your explicit tap
- Converter covered by unit tests (Kotlin)

**Install**: download `wechat-export-v0.1.0-debug.apk` below (Android 7.0+, WeChat 8.0.x verified on 8.0.42, Magisk root required; grant the superuser prompt on first export).

Verified: 560 moments / 16 months exported from a WeChat 8.0.42 device; converter covered by unit tests.

---

**中文**

wechat-export 首个版本 —— 在已 root 的安卓手机上一键把**你自己的**微信朋友圈导出为 JSON / Markdown / HTML，无需电脑。

- 一键导出（按钮 ②，需 root）：提取 `SnsMicroMsg.db`（含 WAL 附属文件），解析 `SnsInfo` / `SnsComment`，解码微信 8.0.x 的 protobuf 二进制列（`content`、`attrBuf`，含点赞与评论）
- 合并版输出（时间倒序）：`exported_sns.json` / `exported_sns.md` / `exported_sns.html`
- 每月一份（倒序）：`YYYY-MM.md` / `YYYY-MM.html`
- 按钮 ① 可导入已有朋友圈 JSON 并渲染
- 应用零权限（无 `INTERNET`）；root 仅在你主动点击时调用
- 转换层有单元测试覆盖（Kotlin）

**安装**：下载下方 `wechat-export-v0.1.0-debug.apk`（Android 7.0+，微信 8.0.x 已在 8.0.42 验证，需 Magisk root；首次导出时在 Magisk 弹窗中授权）。

实测：微信 8.0.42 设备导出 560 条朋友圈、16 个月；转换层有单元测试覆盖。
