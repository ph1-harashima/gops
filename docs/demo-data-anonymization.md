# Demo Data Anonymization Mapping

このドキュメントは、9/17顧客デモに向けて実施したLocal Demo Legacy Seed（`backend/demo-data/02-seed.sql`、ローカルDocker `gsys-legacy-demo-mysql`にのみ投入されるSeed）の固有名称匿名化について、変更前後の対応関係を記録するものである。

**本Documentは開発・デモ準備用であり、顧客へ見せる前提ではない。**

**機密性についての確認**: 変更前（Before）の名称（`アウトドアギア社`／`アルファ商事株式会社`等）は、Step 0/1でこのPrototype用に作成された架空のPlaceholder名称であり、実在の顧客・実在のBrand・実在のメーカー名を含んでいないことをコード（`backend/demo-data/02-seed.sql`のコメント、Requirements MD/Technical Design MDの記述）で確認した。したがって本Documentおよび変更前名称をGit管理下に置くことは問題ないと判断した。

## 対象範囲

匿名化対象は、ローカルDocker上のDemo Legacy MySQL Seed（`legacy_demo`データベース）の表示名称のみ。以下は一切変更していない。

- `phasep-gulliver`（Legacy本体）
- ITEM_CD / SKU、BRAND_CD、Supplier Code（`SUP_ALPHA`/`SUP_BETA`/`SUP_GAMMA`等の内部Code）
- MS_FORMULA、STK_STANDARD、SOLD_QTY、PO_QTY、ARR_QTY、Stock Qty、Open PO、Lead Time、Recommended Qty計算結果、PO History数量
- Workflow Status、Audit Logic

## Brand（MS_COMM, CATE_ID = 'MS_BRAND'）

| Code | Before | After |
|---|---|---|
| `BR_OUTDOOR` | アウトドアギア社 | アウトドアブランドA |
| `BR_HOME` | ホームグッズ社 | ホームブランドA |
| `BR_KITCHEN` | キッチンウェア社 | キッチンブランドA |

## Supplier（MS_COMM, CATE_ID = 'MS_SUPPL'）

各SupplierはOutdoor/Home/Kitchenの複数Brandへ商品を供給しているため（実データ確認済み、下記参照）、単一カテゴリに限定した名称にすると実際の供給範囲と矛盾するため、カテゴリ非依存の一般的な商流会社名とした。

| Code | Before | After | 実際に供給しているBrand（TR_PO実績ベース） |
|---|---|---|---|
| `SUP_ALPHA` | アルファ商事株式会社 | 誠和商事株式会社 | Outdoor（OD-TENT-001/002, OD-CHAIR-001, OD-LAMP-001）、Kitchen（KT-KNIFE-001/002, KT-BOWL-001/002） |
| `SUP_BETA` | ベータトレーディング株式会社 | 中央トレーディング株式会社 | Outdoor（OD-CHAIR-002, OD-BAG-001/002）、Home（HM-RUG-001/002） |
| `SUP_GAMMA` | ガンマ物産株式会社 | さくら物産株式会社 | Home（HM-MUG-001/002, HM-TOWEL-001/002）、Kitchen（KT-PAN-001/002） |

## Item（MS_ITEM.DESCRIPTION）

SKU（ITEM_CD）自体は変更していない。商品名（DESCRIPTION）のみ、より自然なデモ用商品名に更新した。商品カテゴリ・種別はBefore/Afterで矛盾しないようにしている。

| SKU | Brand | Before | After |
|---|---|---|---|
| `OD-TENT-001` | Outdoor | テント L型 グリーン | ファミリーテント L グリーン |
| `OD-TENT-002` | Outdoor | テント S型 ブルー | ソロテント S ブルー |
| `OD-CHAIR-001` | Outdoor | 折りたたみチェア ブラック | アウトドアチェア ブラック |
| `OD-CHAIR-002` | Outdoor | 折りたたみチェア ベージュ | アウトドアチェア ベージュ |
| `OD-BAG-001` | Outdoor | デイパック 20L | トレッキングデイパック 20L |
| `OD-BAG-002` | Outdoor | デイパック 30L | トレッキングデイパック 30L |
| `OD-LAMP-001` | Outdoor | LEDランタン(廃番) | コンパクトLEDランタン(廃番) |
| `HM-MUG-001` | Home | マグカップ 白 | マグカップ ホワイト |
| `HM-MUG-002` | Home | マグカップ 黒 | マグカップ ブラック |
| `HM-TOWEL-001` | Home | バスタオル グレー | モダンバスタオル グレー |
| `HM-TOWEL-002` | Home | バスタオル ネイビー | モダンバスタオル ネイビー |
| `HM-RUG-001` | Home | ラグマット 130x190 | リビングラグ 130x190 |
| `HM-RUG-002` | Home | ラグマット 200x250 | リビングラグ 200x250 |
| `KT-PAN-001` | Kitchen | フライパン 26cm | ステンレスフライパン 26cm |
| `KT-PAN-002` | Kitchen | フライパン 20cm | ステンレスフライパン 20cm |
| `KT-KNIFE-001` | Kitchen | 三徳包丁 | ステンレス三徳包丁 |
| `KT-KNIFE-002` | Kitchen | ペティナイフ | ステンレスペティナイフ |
| `KT-BOWL-001` | Kitchen | ボウル 3点セット | ステンレスボウル 3点セット |
| `KT-BOWL-002` | Kitchen | ボウル 5点セット(発注停止) | ステンレスボウル 5点セット(発注停止) |

## PO No.（TR_PO.PO_NO / TR_PO_DTL.PO_NO）

Legacy PO Historyの`PO No.`は、SKU Detail画面の「履歴（Legacy PO実績）」テーブルに実際に表示される値であり、旧SupplierコードNameの一部（`ALPHA`/`BETA`/`GAMMA`）がPO No.文字列自体に埋め込まれていたため、Supplier名匿名化後もPO No.欄から旧名称が漏れないよう、あわせて汎用的な採番形式へ変更した。数量・単価・日付・Status等は一切変更していない。

| Before | After | SKU |
|---|---|---|
| `ALPHA-OUTDOOR-01` | `PO-OUTDOOR-01` | OD-TENT-001 |
| `ALPHA-OUTDOOR-02` | `PO-OUTDOOR-02` | OD-TENT-002 |
| `ALPHA-OUTDOOR-03` | `PO-OUTDOOR-03` | OD-CHAIR-001 |
| `BETA-OUTDOOR-04` | `PO-OUTDOOR-04` | OD-CHAIR-002 |
| `BETA-OUTDOOR-05` | `PO-OUTDOOR-05` | OD-BAG-001 |
| `BETA-OUTDOOR-06` | `PO-OUTDOOR-06` | OD-BAG-002 |
| `ALPHA-OUTDOOR-07` | `PO-OUTDOOR-07` | OD-LAMP-001 |
| `GAMMA-HOME-08` | `PO-HOME-08` | HM-MUG-001 |
| `GAMMA-HOME-09` | `PO-HOME-09` | HM-MUG-002 |
| `GAMMA-HOME-10` | `PO-HOME-10` | HM-TOWEL-001 |
| `GAMMA-HOME-11` | `PO-HOME-11` | HM-TOWEL-002 |
| `BETA-HOME-12` | `PO-HOME-12` | HM-RUG-001 |
| `BETA-HOME-13` | `PO-HOME-13` | HM-RUG-002 |
| `GAMMA-KITCHEN-14` | `PO-KITCHEN-14` | KT-PAN-001 |
| `GAMMA-KITCHEN-15` | `PO-KITCHEN-15` | KT-PAN-002 |
| `ALPHA-KITCHEN-16` | `PO-KITCHEN-16` | KT-KNIFE-001 |
| `ALPHA-KITCHEN-17` | `PO-KITCHEN-17` | KT-KNIFE-002 |
| `ALPHA-KITCHEN-18` | `PO-KITCHEN-18` | KT-BOWL-001 |
| `ALPHA-KITCHEN-19` | `PO-KITCHEN-19` | KT-BOWL-002 |

## 変更していない数値・コード

以下はBefore/Afterで完全に同一の値であり、匿名化の対象にも影響範囲にも含めていない（`backend/demo-data/02-seed.sql`のUPDATE文はDESCRIPTION/CODE_NAME/PO_NO列のみを対象とし、他列には触れていない）。

- ITEM_CD（全19 SKU）
- BRAND_CD（`BR_OUTDOOR`/`BR_HOME`/`BR_KITCHEN`）
- Supplier Code（`SUP_ALPHA`/`SUP_BETA`/`SUP_GAMMA`）
- `MS_FORMULA`の内容（`OD-TENT-001`のItem別Formula式を含む）
- `STK_STANDARD`、`SOLD_QTY`、`PO_QTY_*`、`ARR_QTY_*`、`STK_QTY`（現在庫）
- `LEAD_TIME`
- `PRC_UNIT`（単価）、`QTY_PO`（PO数量）、`AMT_LINE`、`ORDR_DATE`、`STATUS`、`CCY`
- `ITEM_STATUS`、`DISCON`フラグ

## Recommended Qty匿名化前後比較

名称変更後にLocal Demo Legacyを再構築し、全19 SKUについてOrder Candidate API（`GET /api/order-candidates`）のRecommended Qtyを匿名化前の記録値と突合した。結果は「Regression」章（本書ではなくFinal Reportに記載）を参照。名称以外の差分はゼロであることを確認済み。
