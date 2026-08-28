# ローカル開発環境メモ

このファイルは、`docs/GSYS_Specification.md` 等の仕様書とは異なり、開発環境固有の既知事象を記録するための短いメモです。Application Sourceの挙動とは無関係です。

## `backend/demo-reset.sh` が `mvn: command not found` / `JAVA_HOME` エラーで失敗する場合（2026-08-28 調査）

### 事象
一部のシェル環境（本Sessionで使用したBashツール等）で `backend/demo-reset.sh`（内部で `mvn spring-boot:run` を呼ぶ）を実行すると、以下のいずれかで失敗する。

- `demo-reset.sh: line 35: mvn: command not found`（PATHに `mvn` が無い）
- `Error: JAVA_HOME is set to an invalid directory. JAVA_HOME = "?C:\Program Files\Java\jdk-11"`（`./mvnw.cmd` を直接使った場合）

### 原因
その環境の `JAVA_HOME` 環境変数の**先頭に不可視のUnicode制御文字（U+202A, LEFT-TO-RIGHT EMBEDDING）が混入**しており、値全体が `C:\Program Files\Java\jdk-11` として正しく見えても、シェルからは無効なパスとして扱われる。

```
$ printf '%s' "$JAVA_HOME" | xxd | head -1
00000000: e280 aa43 3a5c 5072 6f67 7261 6d20 4669  ...C:\Program Fi
```
（先頭3バイト `e2 80 aa` が U+202A。おそらく別のツールが値をコピー＆ペーストで設定した際に混入したもので、本リポジトリのコードとは無関係。）

さらに、たとえ `JAVA_HOME` が正しくても `jdk-11` 自体は本プロジェクトが要求する Java 21（`backend/pom.xml` の `<java.version>21</java.version>`）を満たさない。このマシンには `C:\Program Files\Java\jdk-23` が既にインストール済みで、Backendの通常起動（2026-08-28付 Technical Design 記載の `./mvnw clean package` 実行）はこちらを指す別環境で成功している。

### 対処（Application Source変更は不要）
影響を受けるシェルで一時的に `JAVA_HOME` を上書きしてから実行すれば動く（確認済み）。

```bash
cd backend
JAVA_HOME="C:\Program Files\Java\jdk-23" ./mvnw.cmd -q spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments=--app.demo-reset.enabled=true
```

恒久的に直すには、Windowsのユーザー環境変数 `JAVA_HOME` を設定し直す（コントロールパネル、またはPowerShellで `[Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Java\jdk-23', 'User')` のように、コピー&ペーストではなくタイプ入力で設定し、不可視文字の混入を避ける）。

### 補足：ポート競合
上記コマンドは `spring-boot:run` で**専用の一時プロセス**を起動して即Resetし終了する仕組み（`backend/demo-reset.sh` 冒頭コメント参照）。既にポート8080でDev Serverが起動中の場合は `Port 8080 was already in use` で失敗する（これは正常な排他動作）。Demo Resetを実行する前に、起動中のBackend Dev Serverを一旦停止すること。
