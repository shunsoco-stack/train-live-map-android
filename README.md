# Train Live Map for Android

関東を走る JR 列車の位置と運行状況を地図上で確認する、日本語のネイティブ Android アプリです。WebView は使用せず、画面は Jetpack Compose、地図は MapLibre Android で実装しています。

- Android リポジトリ: [shunsoco-stack/train-live-map-android](https://github.com/shunsoco-stack/train-live-map-android)
- 仕様確認元の Web 版: [shunsoco-stack/train-live-map](https://github.com/shunsoco-stack/train-live-map)
- Web 版の確認コミット: [`76d0837`](https://github.com/shunsoco-stack/train-live-map/commit/76d083725183adf513dfe94f2a20dec36fe6dcdc)

Android のソースはこのリポジトリだけで管理します。Web 版および `baobao-privacy-policy` には追加しません。

## アプリ情報

| 項目 | 値 |
| --- | --- |
| 表示名 | Train Live Map |
| Application ID | `com.shunsoco.trainlivemap` |
| Debug Application ID | `com.shunsoco.trainlivemap.debug` |
| minSdk | 26 |
| compileSdk / targetSdk | 37 / 37 |
| version | `1.0.0` (`versionCode = 1`) |
| 本番 API | `https://train-live-map.vercel.app` |

アプリが明示する権限は `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_COARSE_LOCATION` です。おおよその位置情報は、ユーザーが現在地ボタンを押したときだけ許可を求め、端末内で MapLibre のカメラを移動するためにだけ使用します。API、運行情報の投稿、ログ、永続化データへ座標を渡しません。正確な位置情報を要求する `ACCESS_FINE_LOCATION` は、依存ライブラリから merge される場合も含め Manifest で明示的に除去しています。Google Mobile Ads SDK 由来の Advertising ID / Privacy Sandbox 関連権限などは、最終的な merged Manifest と Play Console の申告で監査してください。

## 主な機能

- MapLibre 上に API 提供の JR 路線ポリラインを路線色で描画
- 路線名・カテゴリ・別名による検索、表示／非表示、全表示／全非表示
- 路線のお気に入り登録と「お気に入りのみ」の一発絞り込み
- 選択中の利用可能路線だけを `?lines=tokaido,yamanote` 形式で列車・運行情報 API へ送信
- `すべて`、`走行中`、`停車中`、`遅延`、`運転見合わせ`の列車状態フィルター（0 件の項目は無効）
- 列車番号の前方一致検索（最大 5 件）、候補選択、ズーム 12 以上へのカメラ移動、詳細表示
- 運行情報、取得元、最終更新、次回更新までの秒数、mock／fallback／offline／stale の表示
- 列車状態、進行方向、推定位置を説明する凡例と、優先順位付きの単一空状態
- 匿名の「みんなの運行情報」の閲覧・投稿
- 列車タップ時の詳細 Bottom Sheet
- 路線色、方向ラベル、遅延吹き出し、状態別の顔を持つ Compose Canvas 製の列車マーカー
- `routeSegment` と路線ポリラインに沿った有限時間のスムーズな位置補間
- ライト／ダークテーマ、edge-to-edge、縦画面優先のレスポンシブ配置
- TalkBack 用の路線名・方向・行き先・種別・状態・遅延説明と、約 48 dp 以上の操作領域
- 現在地ボタンによる端末内だけの地図移動と、プライバシーポリシー／利用規約リンク
- Preferences DataStore による設定、API スナップショット、端末内投票 ID の永続化
- UMP の同意状態を確認してから表示する AdMob アダプティブバナー
- Web 版のアイコンを元にした、可愛い電車の Adaptive / themed / legacy icon

列車番号は検索欄と検索候補だけで使用します。地図マーカー、通常の列車用 TalkBack 説明、列車詳細には表示しません。

## ランチャーアイコン

Web 版 `public/icons/train-live-map-1024.png` の電車を元に、背景 `#F68B1E` のランチャーアイコンを生成しています。Adaptive Icon の 432×432 px（108 dp @ xxxhdpi）前景は、電車の全ピクセルが中央の直径 66 dp 安全円内に入るよう配置しています。

- 前景マスター: `artwork/train-foreground-source.png`
- Adaptive / themed icon: `app/src/main/res/drawable-xxxhdpi/`
- legacy / round legacy icon: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/`
- Play Store 用 512×512 sRGB PNG: `artwork/play-store-icon-512.png`

Pillow が利用できる環境では次のコマンドで全画像を再生成できます。円、角丸四角、スクワークルごとの前景クリップ数と、66 dp 安全円からはみ出したピクセル数がすべて 0 でなければ失敗します。比較プレビューは Git 対象外の `.verification/launcher-mask-preview.png` に出力されます。

```powershell
python tools/generate_launcher_icons.py
```

## 技術構成

| 分類 | 採用技術 |
| --- | --- |
| 言語／UI | Kotlin 2.4.10、Jetpack Compose、Material 3 |
| 非同期／状態 | Coroutines 1.11.0、Flow、ViewModel、lifecycle-runtime-compose |
| 通信／JSON | Retrofit 3.0.0、kotlinx.serialization 1.11.0 |
| 地図 | MapLibre Android 13.3.0（OpenGL、互換性優先） |
| 永続化 | Preferences DataStore 1.2.1 |
| 広告／同意 | Google Mobile Ads SDK 25.4.0、UMP SDK 4.0.0 |
| ビルド | Gradle 9.5.0 Wrapper、Android Gradle Plugin 9.3.0、Kotlin DSL、Version Catalog |
| Java bytecode | Java / JVM 17 |

依存バージョンは [`gradle/libs.versions.toml`](gradle/libs.versions.toml)、Android 設定は [`app/build.gradle.kts`](app/build.gradle.kts) が正です。システムに別の Gradle を入れず、必ず Gradle Wrapper を使ってください。

## セットアップ

### 必要な環境

- JDK 17 以上（検証環境は Android Studio 同梱 JBR 21）
- Android Studio と Android SDK
- Android SDK Platform 37
- API 37 対応の Android SDK Build-Tools
- `platform-tools`
- 起動確認を行う場合は API 37 の Emulator system image と AVD、または Android 8.0 以上の実機

`java -version` と Gradle Wrapper が同じ JDK 17 以上を参照していることを確認します。

```powershell
java -version
.\gradlew.bat --version
```

macOS / Linux では `.\gradlew.bat` を `./gradlew` に読み替えてください。

### ローカル設定

サンプルをコピーして、各開発環境の Android SDK パスだけを設定します。

```powershell
Copy-Item local.properties.example local.properties
```

```properties
sdk.dir=C\:\\Android\\Sdk

# 省略時も本番 API が使われます。
API_BASE_URL=https://train-live-map.vercel.app

# Release 用。未発行なら空のままにします。
ADMOB_APP_ID=
ADMOB_BANNER_AD_UNIT_ID=
```

`local.properties` は `.gitignore` 対象です。実際の AdMob ID、署名情報、API トークンなどをコミットしないでください。API Base URL はビルド時に `BuildConfig.API_BASE_URL` へ入り、末尾の `/` は正規化されます。

### ビルドとテスト

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

まとめて検証する場合:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug APK は `app/build/outputs/apk/debug/app-debug.apk` に生成されます。

### Emulator で起動

Android Studio の Device Manager で API 37 の AVD を作成して起動するか、CLI で次のように実行します。

```powershell
$androidSdk = "C:\Android\Sdk"
& "$androidSdk\emulator\emulator.exe" -list-avds
& "$androidSdk\emulator\emulator.exe" -avd "<AVD_NAME>"
.\gradlew.bat :app:installDebug
& "$androidSdk\platform-tools\adb.exe" shell am start -n `
  com.shunsoco.trainlivemap.debug/com.shunsoco.trainlivemap.MainActivity
```

接続済み端末を確認するには `adb devices`、端末上の instrumentation test がある場合は次を使います。

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## API

Android アプリは Vercel の既存バックエンドだけへ接続します。ODPT へ直接アクセスしてはいけません。`ODPT_ACCESS_TOKEN` は Android のソース、APK、`BuildConfig`、`local.properties`、Git 履歴のいずれにも入れません。ODPT の認証、mock、fallback は Web 版バックエンドの責務です。

Android 側のモデル、クエリ、運行情報補完、コミュニティ投稿は Web 版コミット [`76d0837`](https://github.com/shunsoco-stack/train-live-map/commit/76d083725183adf513dfe94f2a20dec36fe6dcdc) の型定義、`src/lib/serviceStatus.ts`、Route Handler を基準にしています。

| Endpoint | 用途 | 主なレスポンス |
| --- | --- | --- |
| `GET /api/trains` | 列車位置 | `trains`, `generatedAt`, `dataUpdatedAt`, `isMock`, `source`, `fallback`, `notice` |
| `GET /api/service-status` | 運行情報 | `serviceStatus`, `serviceStatuses`, `isMock`, `source`, `fallback`, `notice` |
| `GET /api/railways` | 地図線形と路線選択肢 | `lines`, `options`, `generatedAt`, `source` |
| `GET /api/community-reports` | 匿名投稿の集計 | `summaries`, `windowMinutes`, `cooldownSeconds`, `persistent`, `votingEnabled` |
| `POST /api/community-reports` | 匿名の運行状況投稿 | 集計値と投稿対象の `summary` |

`trains[]` は `id`, `lineId`, `lineName`, `lineColor`, `trainNumber`, `direction`, `destination`, `trainType`, `latitude`, `longitude`, `delayMinutes`, `speedKmh`, `status`, `lastUpdatedAt`, `stoppedSince`, `dataAccuracy`, `routeSegment` をデコードします。`trainNumber` は前方一致検索と候補表示だけに使用します。

`routeSegment` は `fromFraction`, `toFraction`, 任意の `coordinates` を持ちます。GeoJSON と同じく座標配列は `[longitude, latitude]` 順です。

`serviceStatus` は `lineId`, `lineName`, `severity`, `message`, `updatedAt`, `dataAccuracy` を持ちます。`serviceStatuses` は現行 API の全路線向け配列で、旧 API と保存済みキャッシュでは省略可能です。`lines` は地図用ポリライン、`options` は検索・選択用の `available`, `coverage`, `coverageNote`, `aliases`, `kind` を含みます。路線の利用可否と coverage は固定値ではなく、常に API 応答を尊重します。

### 路線クエリとキャッシュ境界

`GET /api/trains` と `GET /api/service-status` には、現在表示している利用可能路線 ID を重複除去・昇順化し、カンマ区切りの `lines` として常に渡します。

```text
?lines=tokaido,yamanote
?lines=                    # 0 路線を明示的に選択
```

路線設定の初期化前だけはリクエストを開始しません。`lines` を省略する旧互換呼び出しと、空文字で 0 路線を明示する呼び出しは別契約です。列車・運行情報の端末キャッシュには正規化済み `lines` を記録し、現在のクエリと完全一致するスナップショットだけを fallback 候補にします。同じ路線集合の順序差は正規化して一致させますが、別の路線集合、空選択、旧 unscoped キャッシュを現在の応答として混ぜません。

### HTTP エラーと再試行

- HTTP 400: 同じ `lines` の自動再試行を停止し、路線選択の変更または明示的な手動再試行を待つ
- HTTP 429: delta-seconds／HTTP-date の `Retry-After` を解釈し、指定時刻まで該当 endpoint の polling を抑止する。ヘッダーがなければ 60 秒待つ
- HTTP 503: `Retry-After` があれば尊重し、なければ 10、20、40、80、最大 120 秒の bounded backoff を endpoint ごとに適用する
- その他の一時的な通信失敗: 通常の次周期で再試行するが、重複 request は `Mutex` で防ぐ

アプリ自身の API polling／再試行には WorkManager やバックグラウンド worker を使用しません。POST はユーザーの 1 操作につき 1 回だけ送信し、失敗時に自動再送しません。なお、Google Mobile Ads SDK の推移依存には WorkManager runtime が含まれますが、列車 API の更新処理には接続していません。

### みんなの運行情報

集計の閲覧は `GET /api/community-reports`、投稿は `POST /api/community-reports` です。POST body は `lineId`、`status`（`on-time` / `delayed` / `suspended`）、`delayMinutes` だけで、列車番号や端末位置を送りません。投票者は `X-Community-Reporter` header で識別します。

投票 ID は専用 Preferences DataStore `community_reporter_identity` へ端末内保存し、`^[A-Za-z0-9_-]{12,100}$` に一致する値だけを使用します。不正な保存値は再生成します。この DataStore は cloud backup／device transfer の対象外です。API の `votingEnabled`、`cooldownSeconds`、429 の `Retry-After` を尊重し、連投や自動再試行をしません。

### 運行情報の補完

API の `minor` / `major` は公式情報として列車位置より優先します。公式状態が `normal` の場合だけ、同一路線かつ有効な `lastUpdatedAt` が最新 2 分以内の列車を使って表示状態を補完します。時計差対策として未来 30 秒までは許容し、それより古い・遠い未来・日時不正の列車は使いません。

- `status == delayed` または `delayMinutes > 0` の列車があれば平常表示を取り消す
- 最大 30 分以上、または 15 分以上遅れている列車が対象列車の半数以上なら `major`
- それ以外の遅延は `minor`
- 最大遅延分数を表示し、`major` では公式情報も確認するよう本文で案内する
- 推定状態のフッターには常に「列車位置から推定・公式情報も確認」と表示する
- 遅延だけから「運転見合わせ」とは断定しない
- 「運転を再開しました」と「運転再開の見込み／予定」「現在も見合わせ中」を区別し、同じ文章では最後に現れる状態変化を現在状態として扱う

API から受け取った `serviceStatus` / `serviceStatuses` は上書きせず、`MainUiState.effectiveServiceStatuses` で全路線を補完してから、表示路線のうち最も重要な 1 件を `effectiveServiceStatus` としてコンパクトなパネルへ表示します。このため、列車位置が 2 分を超えて古くなれば補完表示は公式の状態へ戻ります。

開発時の疎通確認例:

```powershell
$baseUrl = "https://train-live-map.vercel.app"
Invoke-RestMethod "$baseUrl/api/trains?lines=tokaido,yamanote"
Invoke-RestMethod "$baseUrl/api/service-status?lines=tokaido,yamanote"
Invoke-RestMethod "$baseUrl/api/trains?lines=" # 0 路線を明示
Invoke-RestMethod "$baseUrl/api/railways"
Invoke-RestMethod "$baseUrl/api/community-reports"
```

## 更新、ライフサイクル、列車の移動

| データ | 更新タイミング |
| --- | --- |
| 列車位置 | フォアグラウンド開始時に即時、その後 10 秒周期 |
| 運行情報 | フォアグラウンド開始時に即時、その後 10 秒周期 |
| 路線情報 | ViewModel 初期化時。手動の「再試行」でも再取得 |
| みんなの運行情報 | Sheet を開いたとき、投稿後、またはユーザーの再試行時 |

`MainActivity` が `ON_START` / `ON_STOP` を `MainViewModel` へ伝えます。バックグラウンドでは列車・運行情報の polling を停止し、復帰時と路線クエリ変更時に即時更新します。手動の再試行は列車、運行情報、路線情報を並行更新します。全画面を毎秒更新する ViewModel clock は持たず、次回更新と投稿 cooldown の秒表示だけが小さな専用 Composable 内で局所的に更新されます。列車マーカーの有限アニメーション以外に、画面全体を毎秒再描画する ticker はありません。

列車位置は受信ごとに瞬間移動させず、次のルールで約 5.5 秒かけて補間します。

1. `routeSegment.coordinates` があれば、その線形と API の fraction を使用する。
2. fraction だけなら対象路線の最も近いポリラインを使い、outbound は増加方向、inbound は減少方向にする。
3. `routeSegment` がなければ API 座標を最寄りの路線ポリラインへ投影する。
4. 路線線形もなければ API の latitude / longitude をそのまま使う。
5. fraction と補間進捗は `0..1` に clamp し、最新 target 到達後は停止する。

アニメーション状態は Compose の再コンポーズから独立した `TrainMotionCoordinator` に保持されます。新しいサーバー根拠がないまま予測移動を続けることはありません。

## キャッシュとオフライン

Preferences DataStore `train_live_map` に次を保存します。

- お気に入り路線 ID
- 表示路線 ID と初期化済みフラグ
- 「お気に入りのみ」の設定
- `/api/trains`、`/api/service-status`、`/api/railways` の最後に成功した JSON

列車・運行情報 JSON は正規化した `lines` と一緒に保存します。`files/datastore/train_live_map.preferences_pb` は cloud backup と device transfer の対象に明示しており、対応する Android の復元経路でも設定と snapshot を引き継ぐ方針です。投票 ID の `community_reporter_identity.preferences_pb` は専用ファイルに分離し、include-only の backup rule から除外しています。端末や OS による実際の復元可否は保証されないため、リリース時に実機でも確認してください。

Repository は network-first です。endpoint ごとに成功応答を上書き保存し、取得失敗時は同じ endpoint・同じ `lines` の最後にデコードできたスナップショットを診断用に返します。UI は「オフライン」「データが古い」、最終更新、再試行を表示しますが、キャッシュ済み列車を現在位置として地図マーカー、列車番号検索、列車詳細へ出しません。選択中だった列車も解除します。キャッシュ済み路線線形と運行情報は明示付きで利用でき、古い／失敗した列車位置は運行情報の遅延補完にも使いません。列車データは `dataUpdatedAt` が現在時刻より 90 秒超古い場合にも stale と判定します。

新規インストール時の表示路線は、API が利用可能として返した東海道線があれば東海道線、なければ最初の利用可能路線です。その後のユーザー設定は再初期化しません。「お気に入りのみ」は、表示中路線とお気に入り路線の積集合を地図へ適用します。

## 検索、凡例、位置情報、法的リンク

列車番号検索は、現在の live 列車だけを大文字小文字を区別せず前方一致で検索し、最大 5 件を表示します。候補を選ぶと状態フィルターを「すべて」へ戻し、対象マーカーを選択してカメラを 500 ms で移動します。既存ズームが 12 未満なら 12 まで拡大し、その後に通常の列車詳細を開きます。列車番号は検索欄と候補だけに残し、詳細や通常のマーカー説明へ漏らしません。

状態フィルターは live 列車の件数を表示し、0 件の項目を TalkBack 上も disabled とします。選択路線なし、列車なし、フィルター結果なしは同時表示せず、優先順位に従った 1 種類の空状態だけを地図上へ出します。凡例では列車状態、`↑ 上り` / `↓ 下り`、位置推定を文字でも説明します。選択可能 UI には `contentDescription` と選択状態、無効な UI には disabled semantics を設定しています。

現在地ボタンはタップ時だけ `ACCESS_COARSE_LOCATION` を要求し、Network Provider のおおよその位置を端末内で取得してズーム 13 へ地図を動かします。取得座標は Repository、API model、DataStore、投稿 payload に入りません。詳細画面にはデータ精度にかかわらず「GPS 実測ではなく駅間情報をもとにした推定で、実際と異なる場合がある」旨を常時表示します。

- [プライバシーポリシー](https://train-live-map.vercel.app/privacy)
- [利用規約・免責](https://train-live-map.vercel.app/terms)

## MapLibre、OSM、CARTO の帰属

ベースマップは CARTO Voyager の raster tile、地物データは OpenStreetMap を利用しています。API 26世代を含む端末互換性を優先して、MapLibre 13.3.0の安定版OpenGL rendererを採用しています。Compose の `AndroidView` 内では `SurfaceView` の同期リサイズによる主スレッド停止を避けるため、MapLibre の `textureMode(true)` を有効にしています。MapLibre style に次の attribution を設定済みです。

`© OpenStreetMap contributors, © CARTO`

MapLibre の attribution 表示を削除、隠蔽、他 UI で覆う変更は禁止です。オフラインキャッシュの対象はアプリ API の JSON であり、地図タイルの完全なオフライン提供は保証しません。

## AdMob と UMP

実装は [Google Mobile Ads SDK の公式手順](https://developers.google.com/admob/android/quick-start) に沿って、画面最下部の `Scaffold.bottomBar` に anchored adaptive banner を置いています。地図や Bottom Sheet には重ねません。

Debug build は Google が公開する公式テスト ID だけを使用します。

| 種別 | Debug の公開テスト ID |
| --- | --- |
| App ID | `ca-app-pub-3940256099942544~3347511713` |
| Adaptive banner Ad Unit ID | `ca-app-pub-3940256099942544/9214589741` |

Release build の実 ID は、コミットしない `local.properties` の次のキーから注入します。

```properties
ADMOB_APP_ID=
ADMOB_BANNER_AD_UNIT_ID=
```

両方の Release ID が設定されていない限り、広告 SDK は初期化されず、広告領域も構成されません。自動初期化 provider は Manifest で削除し、`AdsManager` が UMP の consent information 更新と必要なフォーム表示を完了し、`canRequestAds()` が真になった後に限り `MobileAds.initialize()` を一度実行します。UMP が privacy options を必要と判定した場合は、ヘッダーに再表示操作を出します。

実広告での確認前に [テスト広告の公式ガイド](https://developers.google.com/admob/android/test-ads) と [UMP SDK の公式ガイド](https://developers.google.com/admob/android/privacy) も確認してください。

## テスト対象

`app/src/test` のローカルテストは、少なくとも次の種類を検証します。テスト件数は追加・統合で変わるため、この文書では固定しません。

- 列車、運行情報、路線、コミュニティ API の JSON デコードと `[longitude, latitude]`
- `lines` の正規化、空選択の明示、Retrofit query、完全一致する query scope のキャッシュだけを使うこと
- 通常／遅延／運転見合わせと列車の顔
- 上り／下り表示、位置推定、TalkBack 説明からの列車番号除外
- 路線検索、表示切替、お気に入り、お気に入りのみ、単一空状態
- 状態フィルターと 0 件項目の無効化
- 列車番号の前方一致、最大 5 件、選択後の filter reset と camera focus
- 路線への投影、補間、`0..1` clamp、方向別 fraction
- API 成功時の保存、失敗時の endpoint／query 別キャッシュ、offline 列車マーカー・検索・詳細の抑止
- HTTP 400、429 `Retry-After`、503 bounded backoff と POST の非自動再試行
- mock／fallback の保持
- DataStore での設定・スナップショット永続化、投票 ID の文字種・長さ・再生成
- コミュニティ集計、投稿 body/header、cooldown
- ViewModel の 10 秒同周期 polling、background 停止、foreground／query 変更時の即時再開
- 現在地の権限境界と端末内だけのカメラ移動
- AdMob ID 未設定時の広告無効化

`app/src/androidTest` には、状態 filter、路線検索・表示・お気に入り、列車番号検索、運行情報、空状態などの主要操作を確認する Compose UI test もあります。端末依存の見た目、MapLibre の gesture と camera animation、実ネットワーク、OS の位置権限 dialog、UMP フォーム、広告配信は、Emulator または実機でも確認してください。

## ディレクトリ構成

```text
app/src/main/java/com/shunsoco/trainlivemap/
├── ads/                 # AdMob 設定、UMP、adaptive banner
├── data/
│   ├── local/           # DataStore settings / snapshots / voter ID
│   ├── model/           # Web API と対応する serializable model
│   ├── remote/          # Retrofit API / data source
│   └── repository/      # query-scoped cache / community reports
├── domain/
│   ├── geo/             # polyline、距離、投影、補間
│   ├── motion/          # routeSegment と有限 transition
│   ├── railway/         # 検索、お気に入り、表示絞り込み
│   ├── service/         # 公式情報と最新列車による遅延補完
│   └── train/           # 状態 filter、番号検索、顔、TalkBack
├── ui/
│   ├── components/      # 列車 Canvas、検索、凡例操作、empty state
│   ├── location/        # 端末内だけの現在地取得
│   ├── map/             # MapLibre と motion coordinator
│   ├── sheets/          # 路線、列車詳細、凡例、コミュニティ
│   ├── theme/           # light / dark theme
│   ├── MainViewModel.kt
│   └── TrainLiveMapApp.kt
├── MainActivity.kt
└── TrainLiveMapApplication.kt
```

詳細な運用、リリース、引き継ぎ事項は [`HANDOFF.md`](HANDOFF.md) を参照してください。

## 既知の制約

- ODPT が返す位置は GPS の正確な現在位置ではなく、駅間などから計算した推定を含みます。アプリ内にも「位置推定」と明示しています。
- `routeSegment` の target は受信した範囲内で決め、次の API 更新まで推測で先へ進みません。
- オフライン時は保存済み列車 JSON があっても、過去位置を現在の列車マーカー、検索結果、詳細として表示しません。初回オフラインで路線 snapshot もなければ、表示できる路線データもありません。
- 現在地機能はユーザー操作時のおおよその Network Provider 位置に限り、屋内や設定状態によって取得できない場合があります。列車位置の算出には使いません。
- みんなの運行情報はバックエンドの `votingEnabled`、集計 window、永続化設定に依存し、公式運行情報の代替ではありません。
- CARTO / OpenStreetMap の tile が取得できない環境では、API キャッシュがあっても背景地図は完全には表示されません。
- MapLibre は Compose との安全な合成を優先して TextureView mode を使うため、SurfaceView mode より描画コストが高くなる場合があります。低速端末では実測してください。
- API の enum に未定義値が追加された場合、現在の厳密な enum model はその応答をデコードできません。バックエンド契約変更時は model と fixture test を同時更新してください。
- Release 署名設定と Play Console 登録はリポジトリ外の運用作業です。
- CI workflow は含めていないため、push 前にローカル検証コマンドを実行してください。

## 秘密情報の取り扱い

次の情報はソース、リソース、`BuildConfig` の固定値、README の例、issue、ログ、Git 履歴に含めないでください。

- `ODPT_ACCESS_TOKEN`
- Release 用 AdMob App ID / Ad Unit ID
- release keystore、alias、password
- Play Console や Vercel の認証情報

誤ってコミットした秘密情報は、ファイルを削除するだけでは不十分です。直ちに値を失効・再発行し、Git 履歴も適切に除去してください。
