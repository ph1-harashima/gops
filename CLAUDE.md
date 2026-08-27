# CLAUDE.md

このファイルは、このリポジトリで Claude Code が作業する際のプロジェクト指示書です。

## プロジェクト概要

**G-SYS** は、在庫・発注(PO)・出荷管理を行う社内基幹システムに、新たに **オンライン注文(Online Ordering)機能** を追加するプロジェクトです。
既存システムは Tempostar / Logizero との外部連携を持つ Java (Spring Boot) 製の基幹バッチ・Web システムで、今回の開発はその上に新機能を実装するものです。

## 体制

- **Techlead: ChatGPT** — 要件整理・設計方針・技術的意思決定を担当
- **SEPG: Claude Code** — 本リポジトリでの実装・詳細設計・コード作成を担当

Claude Code は SEPG として、Techlead (ChatGPT) 側で整理された方針・指示を踏まえて実装を進めます。方針や仕様に疑義がある場合は、`docs/` 配下の資料を優先的な一次情報として確認し、不明点はユーザーに確認してください。

## ディレクトリ構成

```
g-sys/                                            ← プロジェクトルート（本リポジトリ）
├── CLAUDE.md                                     ← このファイル
├── docs/                                         ← 仕様・打ち合わせ資料
│   ├── G-Sys_mtg_20260826_02.pptx                    お客様打ち合わせメモ (2026/08/26)
│   ├── G-SYS_Online-Ordering_Prototype_Requirements.md  お客様提供の元仕様書
│   └── GSYS_Specification.md                         元仕様書から起こしたMD版仕様書（G-SYS全体のシステム仕様）
└── phasep-gulliver/                              ← G-SYS本体のプログラム（独自Gitリポジトリ、既存の基幹システム）
    ├── gulliver/                                     Mavenプロジェクト本体（Spring Boot, ソースコード一式）
    └── README.md                                     phasep-gulliver固有の開発環境セットアップ手順
```

- `phasep-gulliver/` はお客様から受領した既存プログラム（7z展開物）で、独自の `.git` 履歴を持つ別リポジトリです。ルートの Git 管理には含めません（`.gitignore` 参照）。
- 仕様に関する一次情報は `docs/GSYS_Specification.md`（全体仕様）と `docs/G-SYS_Online-Ordering_Prototype_Requirements.md`（オンライン注文機能の要件）です。実装前に必ず参照してください。

## 開発の進め方

1. 実装に着手する前に `docs/GSYS_Specification.md` と `docs/G-SYS_Online-Ordering_Prototype_Requirements.md` を確認し、既存システムの構成・制約を把握する。
2. コード変更は `phasep-gulliver/` 配下で行い、そのリポジトリの Git 履歴・ブランチ運用に従う（ルートリポジトリとは別管理）。
3. 仕様・議事録に変更や追記があった場合は `docs/` に反映し、ルートリポジトリでコミットする。
4. 技術的な意思決定で Techlead (ChatGPT) 側の方針が必要な場合は、ユーザーを介して確認する。
