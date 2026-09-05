# wechat-export

Export **your own** WeChat Moments (朋友圈) to JSON / Markdown / HTML — one tap on a rooted Android phone, no PC required.

The app parses WeChat's local database on-device and writes all outputs directly on the phone. A moments JSON produced by any other tool can also be imported and rendered (button ①).

> This repository contains **no personal data**: no accounts, device serials, or MicroMsg hash directories. Do not commit your own export files.

---

## Features

- One-tap export (root): `SnsMicroMsg.db` → parsed moments → `exported_sns.json` + `exported_sns.md` + `exported_sns.html` — combined view, newest first
- Per-month files: `YYYY-MM.md` / `YYYY-MM.html` (months newest first, posts newest first within each month)
- Parses WeChat 8.0.x protobuf-blob columns (`content`, `attrBuf` → likes & comments) as well as legacy XML rows
- Button ① imports an existing moments JSON (from any tool) and renders Markdown/HTML
- Zero permissions: the app declares **no** Android permissions at all — not even `INTERNET`. Root is invoked only when you tap the export button (`su`).
- Tolerant JSON loading; converter covered by unit tests

## Output layout (on device)

```
/storage/emulated/0/Android/data/com.wechatexport/files/WechatExport/
├── exported_sns.json      # combined, newest first (merge-friendly schema)
├── exported_sns.md
├── exported_sns.html
├── 2026-08.md             # per-month, newest first
├── 2026-08.html
└── ...
```

## Requirements

- Android 7.0+ (minSdk 24), rooted (Magisk) device, WeChat 8.0.x (verified on 8.0.42)
- `SnsMicroMsg.db` must be plaintext (this DB is not SQLCipher-encrypted on verified builds)
- Coverage equals what WeChat has cached in the database; open and scroll older years inside WeChat first if you need them

## Build

- Android: JDK 17 + Android SDK 34 → `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

## How it works

`su -c` copies `SnsMicroMsg.db` together with its `-wal` / `-shm` / `.ini` sidecars (so WAL-buffered recent posts are not missed) into the app's external files dir → opened read-only with `SQLiteDatabase` → `SnsInfo` / `SnsComment` rows parsed; protobuf wire-format decoded in pure Kotlin (`SnsProto.kt`, a port of the Python reference parser) → grouped by month, sorted newest first → JSON + Markdown + HTML written through a single exporter.

## Privacy & legal

This tool exists for backing up **your own** moments on **your own** rooted device. Do not export, publish, or share other people's data. Root access is only used to read WeChat's private database at your explicit request.

## Project layout

```
wechat-export/
├── app/                                  # Android app (Kotlin + Compose)
│   └── src/main/java/com/wechatexport/
│       ├── MainActivity.kt               # UI: buttons ① import / ② root export
│       └── engine/
│           ├── MomentsModel.kt           # data model
│           ├── MomentsLoader.kt          # tolerant JSON import
│           ├── MomentsConverter.kt       # JSON → Markdown / HTML (pure functions)
│           ├── MomentsWriter.kt          # model → snskit-compatible JSON
│           ├── DbExtractor.kt            # su copy of SnsMicroMsg.db + sidecars
│           ├── DbMomentsParser.kt        # SQLite → moments (XML + protobuf paths)
│           ├── SnsProto.kt               # protobuf wire-format decoder
│           └── MonthlyExporter.kt        # combined + per-month md/html/json writer
├── app/src/test/                         # converter unit tests
└── README.md
```

## Changelog

### v0.1.0
- Initial release: root DB export with protobuf parsing, combined `exported_sns.json/md/html`, per-month files, newest-first ordering; bilingual documentation.

---

# 中文说明

把**你自己的**微信朋友圈导出为 JSON / Markdown / HTML —— 在已 root 的安卓手机上一键完成，无需电脑。

应用在手机本地解析微信数据库并直接写出全部文件；其他工具生成的朋友圈 JSON 也可以导入渲染（按钮 ①）。

## 功能

- 一键导出（需 root）：`SnsMicroMsg.db` → 解析朋友圈 → 合并版 `exported_sns.json` / `.md` / `.html`，时间倒序
- 每月一份：`YYYY-MM.md` / `YYYY-MM.html`（月份倒序，月内倒序）
- 支持微信 8.0.x 的 protobuf 二进制列（`content`、`attrBuf`，含点赞与评论），同时兼容旧版 XML 行
- 按钮 ① 可导入已有的朋友圈 JSON（来自任何工具）并渲染 Markdown/HTML
- 零权限：应用**不声明任何** Android 权限（连 `INTERNET` 都没有）；root 只在你点击导出按钮时通过 `su` 调用
- 容错 JSON 加载；转换层有单元测试

## 输出位置（手机上）

```
/storage/emulated/0/Android/data/com.wechatexport/files/WechatExport/
```

## 环境要求

- Android 7.0+（minSdk 24），Magisk root，微信 8.0.x（已在 8.0.42 验证）
- `SnsMicroMsg.db` 为明文（该库在已验证版本上未加密）
- 可导出的范围 = 微信数据库里已缓存的朋友圈；需要更早年份请先在微信里加载

## 构建

- Android：JDK 17 + Android SDK 34 → `./gradlew :app:assembleDebug`

## 工作原理

通过 `su -c` 把 `SnsMicroMsg.db` 连同 `-wal` / `-shm` / `.ini` 附属文件一起复制（避免丢失 WAL 中未合并的最近消息）到应用外部目录 → 以只读方式打开 SQLite → 解析 `SnsInfo` / `SnsComment`，用纯 Kotlin 实现的 protobuf wire 解码器（移植自 Python 参考实现）→ 按月分组、倒序排序 → 统一由一个导出器写出 JSON + Markdown + HTML。

## 隐私与法律

本工具仅用于在**自己的**已 root 设备上备份**自己的**朋友圈数据。请勿导出、发布或传播他人数据；root 权限仅在你主动点击时用于读取微信私有数据库。

## 更新日志

### v0.1.0
- 首个版本：root 导出 + protobuf 解析、合并版 `exported_sns.json/md/html`、每月分文件、全部倒序；双语文档。
