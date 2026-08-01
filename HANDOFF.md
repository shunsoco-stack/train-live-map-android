# Train Live Map Android 運用引き継ぎ

この文書は、実装を引き継いで API 追従、検証、広告設定、リリースを行う担当者向けです。導入手順と機能概要は [`README.md`](README.md) を先に参照してください。

## 1. 管理境界

- Android の保存先は `shunsoco-stack/train-live-map-android` です。
- Web 版 `shunsoco-stack/train-live-map` は API 契約の確認元であり、Android のソースを追加しません。
- `baobao-privacy-policy` にも Android のソースを追加しません。
- Android リポジトリの visibility は Web 版と同じに保ちます。
- 作業中に使う `.reference-web/` は読み取り専用の参照 checkout で、`.gitignore` 対象です。

visibility の監査例:

```powershell
gh repo view shunsoco-stack/train-live-map --json nameWithOwner,visibility
gh repo view shunsoco-stack/train-live-map-android --json nameWithOwner,visibility
```

Web 版の型と API Route Handler は次のコミットで確認済みです。

- short SHA: `76d0837`
- full SHA: `76d083725183adf513dfe94f2a20dec36fe6dcdc`
- commit: [shunsoco-stack/train-live-map@76d0837](https://github.com/shunsoco-stack/train-live-map/commit/76d083725183adf513dfe94f2a20dec36fe6dcdc)

Web 版を追跡更新する場合も、Android の変更とは別 checkout / repository で行い、意図せず Web 版へ push しないでください。

## 2. 現在のビルド基準

| 項目 | 現在値 |
| --- | --- |
| Application ID | `com.shunsoco.trainlivemap` |
| Debug suffix | `.debug` |
| minSdk | 26 |
| compileSdk / targetSdk | 37 / 37 |
| JDK / JVM target | 17 / 17 |
| Gradle Wrapper | 9.5.0 |
| Android Gradle Plugin | 9.3.0 |
| Kotlin | 2.4.10 |
| Compose BOM | 2026.06.00 |
| MapLibre Android | 13.3.0（OpenGL variant） |
| Google Mobile Ads / UMP | 25.4.0 / 4.0.0 |

バージョンの一次情報:

- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

JDK 17 以上と Android SDK Platform 37 が入った環境を保守基準とします。今回の Windows 検証では Android Studio 同梱 JBR 21 を使用しました。Gradle は Wrapper のみを使用します。

## 3. アーキテクチャ

依存の流れは次の通りです。

```text
Compose UI / MapLibre / Ads
          ↓ events / StateFlow
      MainViewModel
       ↙          ↘
TrainRepository  CommunityReportRepository
       ↓          ↙                 ↘
Retrofit API   VoterIdStore    Retrofit community API
       ↓            ↓
query-scoped   backup 対象外の専用 DataStore
snapshots
```

`TrainLiveMapApplication` の `AppContainer` が Retrofit、2 種類の DataStore、Repository を組み立てる簡潔な manual DI です。`MainViewModelFactory` が TrainRepository、SettingsStore、CommunityReportRepository を ViewModel へ渡し、端末内位置取得は Compose 側で application context から生成します。

### 主な責務

| 場所 | 責務 |
| --- | --- |
| `data/model` | Web API と対応する kotlinx.serialization model |
| `data/remote` | Retrofit interface、base URL／`lines` 正規化、JSON 設定、Retry-After と HTTP failure 分類 |
| `data/local` | お気に入り、表示設定、query 付き response snapshot、backup 対象外の投票 ID |
| `data/repository` | network-first、完全一致 query の cache fallback、1 操作 1 POST |
| `domain/geo` | haversine、polyline index、投影、fraction、clamp |
| `domain/motion` | routeSegment 解決、方向付け、有限 transition |
| `domain/railway` | 路線検索、表示、お気に入り filter |
| `domain/service` | 公式運行情報の文脈分類、最新列車による遅延補完 |
| `domain/train` | 状態 filter、列車番号 prefix 検索、顔、方向／状態文言、TalkBack |
| `ui/map` | MapLibre style/layer、marker 投影、animation coordinator |
| `ui/components` | Canvas 列車、番号検索、header、data health、単一 empty state |
| `ui/location` | `ACCESS_COARSE_LOCATION` による端末内だけの camera coordinate 取得 |
| `ui/sheets` | 路線選択、列車詳細、凡例、コミュニティ Modal Bottom Sheet |
| `ads` | BuildConfig ID、UMP flow、Mobile Ads 初期化、banner lifecycle |

## 4. API 契約

本番 base URL は `https://train-live-map.vercel.app` です。`local.properties` の `API_BASE_URL` で build-time override でき、値は `BuildConfig.API_BASE_URL` に入ります。

Android アプリから ODPT へ直接接続しません。認証と fallback は Vercel 側だけで管理します。`ODPT_ACCESS_TOKEN` を Android 側へ渡すユースケースはありません。

### `GET /api/trains`

レスポンス:

```text
TrainsApiResponse
├── trains: TrainLocation[]
├── generatedAt: ISO-8601
├── dataUpdatedAt: ISO-8601
├── isMock: Boolean
├── source: "odpt" | "mock"
├── fallback: Boolean
└── notice: String?
```

`TrainLocation` の契約:

```text
id, lineId, lineName, lineColor, trainNumber,
direction("inbound" | "outbound"),
destination,
trainType("local" | "rapid" | "special_rapid"),
latitude, longitude, delayMinutes, speedKmh,
status("running" | "stopped" | "delayed" | "suspended" | "unknown"),
lastUpdatedAt, stoppedSince?,
dataAccuracy("actual" | "estimated" | "mock"),
routeSegment?
```

`trainNumber` は列車番号の前方一致検索と検索候補でだけ使用します。地図 marker、通常の accessibility description、列車詳細では使用しません。`routeSegment` は `fromFraction`, `toFraction`, 任意の `coordinates` を持ちます。

### `GET /api/service-status`

```text
ServiceStatusApiResponse
├── serviceStatus
│   ├── lineId, lineName
│   ├── severity: "normal" | "minor" | "major"
│   ├── message
│   ├── updatedAt
│   └── dataAccuracy
├── serviceStatuses[]?（現行の全路線配列。旧キャッシュでは省略）
├── isMock
├── source
├── fallback
└── notice
```

### `lines` query と cache scope

`GET /api/trains` と `GET /api/service-status` は、表示対象かつ `options.available == true` の路線 ID を `normalizeLinesQuery` で trim、空値除外、重複除去、昇順化し、常に `lines` query として渡します。

```text
GET /api/trains?lines=tokaido,yamanote
GET /api/service-status?lines=tokaido,yamanote
GET /api/trains?lines=                 # 初期化済みだが 0 路線
GET /api/service-status?lines=
```

初期化前の `selectedLinesQuery == null` は取得開始を待つ状態です。初期化済み 0 路線の空文字とは区別してください。引数なしで query を省略する API は旧互換 test 用で、通常の ViewModel path では使いません。

`snapshot_trains_json` と `snapshot_service_status_json` は `ScopedSnapshot` として正規化済み `linesQuery` を持ちます。fallback decode は request query と完全一致した場合だけ成功します。同じ集合の順序違いは正規化で一致しますが、別の路線集合、空選択、旧 unscoped snapshot を取り違えないことが重要です。

### HTTP failure policy

`ApiFailure` と `PollingRetryGate` は列車・運行情報 endpoint ごと、かつ `linesQuery` ごとに保持します。

- 400: 同一 query の自動 polling を停止する。query 変更で gate を切り替え、明示的な手動再試行だけは許可する
- 429: delta-seconds と HTTP-date の `Retry-After` を解釈し、その時刻まで該当 endpoint を抑止する。値がなければ 60 秒
- 503: `Retry-After` があれば尊重し、なければ 10 → 20 → 40 → 80 → 120 秒を上限とする指数 backoff
- network／その他: 保存済み response を診断用に返し、通常周期で再取得する

同一 endpoint は `Mutex` で直列化します。アプリ自身の API polling／再試行に WorkManager、AlarmManager、バックグラウンド worker は使用しません。Google Mobile Ads SDK の推移依存には WorkManager runtime が含まれますが、列車 API の更新処理には接続していません。ユーザー投稿の POST は 1 操作につき exactly once で、通信失敗・400・429・503のどれでも自動再送しません。

### 運行情報補完の保守条件

基準実装は Web 版 `src/lib/serviceStatus.ts` の `classifyServiceStatusSeverity` と `serviceStatusWithTrainDelayFallback` です。Android の対応実装は `domain/service/ServiceStatusPolicy.kt` にあり、15 分条件は受け入れ要件どおり「15 分以上の列車数」で判定します。

1. API の `minor` / `major` は公式情報としてそのまま優先する。
2. `normal` のときだけ、同じ `lineId` で最新 2 分以内の列車を対象にする。未来時刻は 30 秒まで許容する。
3. `status == delayed` または `delayMinutes > 0` を遅延列車とする。
4. 最大 30 分以上、または 15 分以上の遅延列車が対象列車の半数以上なら `major`、それ以外は `minor` とする。
5. 補完後は `dataAccuracy = estimated`、`updatedAt` は遅延列車のうち最新の時刻とする。
6. 遅延から運転見合わせを生成しない。`major` は大幅な遅延を表す場合もある。
7. 再開済み表現と、再開予定・再開見込みなし・現在見合わせ中の表現を区別する。同じ文章では最後に現れる状態変化を採用し、再開後の残存支障は再開表現より後ろだけで判定する。ただし API から受け取った非平常 severity は端末側の文章分類で変更しない。

`MainUiState.serviceStatus` と `serviceStatuses` は API / cache の原本、`effectiveServiceStatuses` と `effectiveServiceStatus` は画面用の導出値です。全路線を個別に補完した後、表示中の路線から最も重要な 1 件をパネルへ出します。原本を補完結果で置換すると、列車が古くなっても推定の `minor` / `major` が残り続けるため、この分離を崩さないでください。

### `GET /api/railways`

```text
RailwaysApiResponse
├── lines[]
│   ├── id, odptId, name, color
│   └── coordinates: LngLat[][]
├── options[]
│   ├── id, name, category, color, aliases
│   ├── coverage, coverageNote, kind
│   └── available
├── generatedAt
└── source: "odpt" | "fallback"
```

座標は `[longitude, latitude]` 順です。`LngLatSerializer` が要素数 2 を検証します。Retrofit と on-device snapshot は同じ `Json` instance を使い、未知の JSON key は無視します。一方、未知の enum value は現状では decode error になるため、サーバー契約を拡張する際は Android model と decode fixture を同時更新してください。

### `GET` / `POST /api/community-reports`

GET は投票 ID を作らず、`summaries`, `windowMinutes`, `cooldownSeconds`, `persistent`, `votingEnabled` を取得します。POST は次の body と、`X-Community-Reporter` header だけを送ります。

```json
{
  "lineId": "tokaido",
  "status": "delayed",
  "delayMinutes": 15
}
```

`status` は `on-time` / `delayed` / `suspended` です。`delayed` の `delayMinutes` は 1..120 の整数、それ以外は JSON `null` を明示します。列車番号、端末位置、広告 ID などを body/header に追加しないでください。`votingEnabled == false` のとき投稿操作を無効にし、成功時の server `cooldownSeconds` と 429 の `Retry-After` を尊重します。

### API 変更時の手順

1. Web 版の対象 commit と型定義、service status policy、対象 Route Handler を確認する。
2. Android の `data/model` を更新する。
3. `ApiJsonDecodingTest` の JSON fixture を実際の新契約に合わせる。
4. cache に旧 JSON が残る upgrade path を確認する。互換性がない場合は key versioning または migration を設計する。
5. mock、fallback、notice、coverage、available、community の feature flag／cooldown が失われていないことを確認する。
6. read endpoint の本番 smoke test、投稿は test backend での契約 test、オフライン再起動を実施する。
7. HANDOFF に新しい Web 版 commit SHA を記録する。

## 5. polling と lifecycle

`MainActivity` は Lifecycle event を `MainViewModel.setForeground` へ渡します。

| Event | 挙動 |
| --- | --- |
| ViewModel 初期化 | `/api/railways` を 1 回取得 |
| `ON_START` | foreground 化し、列車と運行情報を即時取得して polling 開始 |
| foreground 継続 | `/api/trains` と `/api/service-status` を同じ loop から並行取得し、完了後 10 秒待機 |
| 選択路線変更 | polling job を作り直し、新しい正規化 query で両 endpoint を即時取得 |
| `ON_STOP` | foreground polling job を cancel し、次回更新表示を消す |
| 再度 `ON_START` | 古い timer を再利用せず即時取得 |
| ヘッダーの更新／data health の再試行 | 列車、運行情報、路線情報を並行取得。400 gate にも明示的な 1 回を許可 |
| コミュニティ Sheet | 開いたとき／再試行時に GET。投稿操作時だけ POST |

`TRAIN_POLLING_MILLIS` と `SERVICE_POLLING_MILLIS` はともに 10,000 ms です。`Mutex` により同一 endpoint の更新を直列化し、`PollingRetryGate` が 400／429／503 の skip 判定を endpoint ごとに行います。片方が gate 中でも loop 自体は 10 秒ごとに起きますが、該当 endpoint の request は待機期限まで送信しません。

10 秒は fixed-rate ではなく「両取得完了後の delay」です。通信時間分だけ次回開始は後ろへずれます。`MainUiState.nowMillis` を 1 秒ごとに変える全画面 clock job はありません。次回更新とコミュニティ cooldown の秒表示は、それぞれの小さな Composable が局所 state だけを更新します。画面全体の 1 秒 ticker、API polling／再試行用 WorkManager、background polling を追加しないでください。

## 6. 列車位置とアニメーション

`TrainMotionCoordinator` は `remember` され、再コンポーズでは作り直されません。列車ごとの snapshot signature が変わった時だけ新しい target を作り、現在描画位置から約 5.5 秒の smoothstep transition を開始します。

優先順位:

1. `routeSegment.coordinates` の明示的な from → to 線形
2. `routeSegment` の fraction と最寄りの路線ポリライン
3. API latitude / longitude を最寄り路線へ投影
4. 線形がなければ API 座標

fraction だけの区間は outbound を増加方向、inbound を減少方向にします。fraction、segment progress、transition progress は `0..1` に clamp します。target 到達後は停止し、新しい API snapshot なしに速度外挿しません。

データ精度は UI の重要な契約です。ODPT の列車位置は GPS 位置ではなく、駅・駅間情報からの推定を含みます。ヘッダーと凡例に説明し、詳細画面では `dataAccuracy` の値に関係なく「GPS 実測ではなく、実際の位置と異なる場合がある」旨を常時表示します。取得失敗や cache fallback に入った時点で `MainUiState.currentTrains` は空となり、過去の target を現在位置として描画しません。

## 7. 表示設定と snapshot

Preferences DataStore 名は `train_live_map` です。現在の key は次の通りです。

```text
favorite_line_ids
visible_line_ids
favorites_only
visible_line_ids_initialized
snapshot_trains_json
snapshot_service_status_json
snapshot_railways_json
```

列車・運行情報 snapshot の JSON value は response そのものではなく、`linesQuery` と payload を持つ scope envelope です。raw JSON を直接読む保守コードを追加せず、Repository の exact-query decode を通してください。

初回の表示路線初期化は `visible_line_ids_initialized` で一度だけ行います。API の available option のうち `tokaido` があれば選び、なければ先頭を選びます。ユーザーが「すべて隠す」を選んだ状態を未初期化と混同しないことが、このフラグの目的です。

Repository の原則:

- 成功した endpoint だけ、その endpoint の JSON snapshot を置き換える。
- snapshot 書き込み失敗でも live response は利用する。
- network error 時は endpoint ごとに cache を best-effort decode し、列車・運行情報は現在の `linesQuery` と完全一致したものだけ受け入れる。
- cache を返す場合も元の error を残し、UI が stale/offline を明示できるようにする。
- cache／失敗状態の列車は `currentTrains` から除外し、marker、番号検索、詳細、運行情報の列車由来補完へ渡さない。
- cancel は握りつぶさず再 throw する。
- cache も壊れていれば `data = null` と error を返す。

列車データの `dataUpdatedAt` が 90 秒より古い場合も stale です。live データであっても、運行情報補完は各列車の `lastUpdatedAt` が最新 2 分以内かつ未来 30 秒以内のものに限定します。初回オフラインかつ cache なしではデータを作り出しません。

設定と snapshot は同一インストール内の再起動で永続化されます。`backup_rules.xml` と `data_extraction_rules.xml` は include-only で `file` domain の `datastore/train_live_map.preferences_pb` だけを cloud backup と device transfer の対象へ明示しています。OS の復元経路を介した端末間移行は実機でも検証してください。将来ここへ秘密情報を保存しないでください。

投票 ID は別の Preferences DataStore `community_reporter_identity`、key `community_reporter_id` に保存します。使用できる値は `^[A-Za-z0-9_-]{12,100}$` の完全一致だけです。現在の generator は UUID 文字列で、保存値が不正なら原子的に再生成します。`datastore/community_reporter_identity.preferences_pb` は上記 include-only rule に含めず、cloud backup／device transfer の対象外に維持してください。GET では ID を生成せず、POST 時にだけ `getOrCreateVoterId()` を呼びます。

## 8. MapLibre と attribution

`TrainMap.kt` の base style は CARTO Voyager raster tile を使い、source attribution に次を設定しています。

```text
© OpenStreetMap contributors, © CARTO
```

運用上の必須条件:

- MapLibre の attribution UI を削除しない。
- attribution を header、banner、Bottom Sheet などで恒常的に覆わない。
- tile provider や style を変更したら、新しい provider の利用条件と attribution を確認する。
- API JSON cache と tile cache を同一視しない。現在、完全オフライン地図は提供しない。

路線 layer は API の `lines` から作り、casing はダークブラウン、内側線は API `color` です。消えた API line の既存 layer は非表示にします。路線一覧の利用可否は `options.available`、説明は `coverage` / `coverageNote` を使い、Android の固定一覧へ退行させないでください。

列車番号検索で列車を選ぶと、`TrainMap` は対象座標へ 500 ms で camera animation し、現在 zoom と 12 の大きい方を使います。現在地ボタンでは zoom 13 を要求します。この 2 種類の camera request を列車の motion target と混同しないでください。

## 9. 列車マーカーと accessibility

`TrainMarker` は 72 × 86 dp の再利用可能な Canvas component です。

- body は `lineColor`
- inbound は `↑ 上り`、outbound は `↓ 下り`
- delay は上部に `+N分`
- suspended は上部に `見合わせ`
- normal は笑顔
- delayed または `delayMinutes > 0` は困り眉・困り顔
- suspended は悲しい顔と青い涙

方向と状態は色だけで伝えません。TalkBack description には路線名、方向、行き先、種別、状態、遅延を含め、列車番号を除外します。列車番号は prefix 検索欄と最大 5 件の候補にだけ表示し、候補を選ぶと状態 filter を `ALL` に戻して marker 選択、camera 移動、詳細表示を行います。offline／cache-only 時は検索対象を空にします。

状態 filter chip は live 列車の件数が 0 なら `enabled = false` と disabled semantics を設定し、選択中 filter が 0 件になった場合は `ALL` へ戻します。空状態は `NO_LINES` → `NO_TRAINS` → `NO_FILTER_RESULTS` の優先順位で必ず 1 種類だけ表示します。凡例は通常／遅延／見合わせの顔、進行方向、位置推定を文字でも説明します。

路線、列車、検索候補、filter、コミュニティ投稿には `contentDescription`、選択可能項目には選択 state、操作不能時には disabled semantics を設定します。48 dp 程度の tap target、font scale、高コントラストを維持してください。

### 現在地と法的リンク

Manifest は `ACCESS_COARSE_LOCATION` だけを宣言し、依存から入る `ACCESS_FINE_LOCATION` を `tools:node="remove"` で除去します。許可 dialog は現在地ボタンを押したときだけ出します。`AndroidCurrentLocationProvider` は Network Provider の current／last-known のおおよその座標を端末内で返し、10 秒で timeout します。座標は camera request だけに使い、Repository、Retrofit、community body/header、DataStore、analytics へ送信・保存しません。

凡例 Sheet から次を開けます。URL を変更する場合は Web 側の公開状態と法務文言も確認してください。

- プライバシーポリシー: `https://train-live-map.vercel.app/privacy`
- 利用規約・免責: `https://train-live-map.vercel.app/terms`

## 10. AdMob / UMP の運用

Google Mobile Ads の auto-init provider は Manifest merge で削除しています。理由は、Release ID 未設定の build で SDK を自動起動しないことと、必要な地域で UMP の状態確認を初期化より先に行うことです。

起動シーケンス:

```text
Activity 起動
  → requestConsentInfoUpdate
  → loadAndShowConsentFormIfRequired
  → ConsentInformation.canRequestAds()
  → MobileAds.initialize（process 内で一度）
  → anchored adaptive banner を load
```

Debug build の ID は Google 公開のテスト用です。

```text
App ID: ca-app-pub-3940256099942544~3347511713
Banner: ca-app-pub-3940256099942544/9214589741
```

Release の実 ID は次だけに置きます。

```properties
# local.properties — Git 管理禁止
ADMOB_APP_ID=
ADMOB_BANNER_AD_UNIT_ID=
```

両方が non-blank でない限り `AdsConfiguration.isConfigured` は false となり、consent flow、Mobile Ads 初期化、banner surface は無効です。片方だけの設定も無効として扱います。

実広告を有効にする担当者の作業:

1. AdMob 側で Application ID `com.shunsoco.trainlivemap` の Android app を登録する。
2. banner ad unit を発行する。
3. 対象地域の privacy message を AdMob / Funding Choices 側で設定する。
4. 実 ID を各リリース環境の非公開 `local.properties` または安全な CI secret injection に設定する。
5. UMP の同意フォームと privacy options 再表示を対象地域／test geography で検証する。
6. Google Play の「データ セーフティ」、広告、Advertising ID、プライバシーポリシー申告を実際の配信設定に合わせる。
7. Debug では引き続き公開テスト ID だけを使う。実 ID で開発時クリック試験をしない。

公式資料:

- [Google Mobile Ads SDK quick start](https://developers.google.com/admob/android/quick-start)
- [Enable test ads](https://developers.google.com/admob/android/test-ads)
- [UMP SDK](https://developers.google.com/admob/android/privacy)

## 11. 検証手順

### ローカルの必須 gate

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

一括実行:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

失敗時は最初の原因を明確にするため、必要に応じて対象 task に `--stacktrace` を付けます。生成物、HTML report、test result XML は `app/build/` 配下であり Git 管理しません。

### Emulator / 実機

API 37 AVD を使う推奨確認:

1. portrait で初回起動し、路線、選択路線の列車・運行情報、コミュニティ集計が表示される。
2. pan / zoom 中も header、route chip、banner が地図操作を妨げない。
3. 列車と運行情報が同じ 10 秒周期で選択路線の `?lines=` を送信し、列車が瞬間移動せず線路上で target に停止する。
4. 列車 tap で詳細項目が出て、GPS 実測でない位置推定の注意がデータ精度にかかわらず常時見える。
5. 列車番号の前方一致候補を選び、対象へ zoom 12 以上で移動して詳細が開く。番号は marker／詳細へ出ない。
6. 路線検索、表示切替、お気に入り、お気に入りのみ、状態 filter を操作し、0 件 filter が無効で空状態が 1 種類だけになる。
7. 凡例で状態、方向、推定位置を確認し、プライバシーポリシーと利用規約を開く。
8. 現在地ボタンのタップ時だけ「おおよその位置」の権限を求め、地図だけが動く。正確な位置の権限を求めない。
9. process を終了して再起動し、路線設定とお気に入りが残る。投票 ID は app backup／device transfer の対象外である。
10. 通信を切って再起動し、offline/stale、最終更新、再試行は出るが、cached 列車 marker・番号検索結果・詳細は現在位置として出ない。
11. 通信を戻して再試行し、live 表示へ復帰する。
12. Home へ移動中に polling が止まり、復帰直後に列車・運行情報が request される。アプリ実装の background worker から列車 API 通信が発生しない。
13. community の閲覧、投稿 disabled/cooldown、1 tap 1 POST を test backend で確認する。本番へ検証票を送らない。
14. light / dark、最大 font scale、TalkBack、横画面／tablet 幅、選択／無効状態の読み上げを確認する。
15. attribution が見え、広告が地図や Bottom Sheet に重ならない。

CLI:

```powershell
$androidSdk = "C:\Android\Sdk"
& "$androidSdk\platform-tools\adb.exe" devices
.\gradlew.bat :app:installDebug
& "$androidSdk\platform-tools\adb.exe" shell am force-stop `
  com.shunsoco.trainlivemap.debug
& "$androidSdk\platform-tools\adb.exe" shell am start -n `
  com.shunsoco.trainlivemap.debug/com.shunsoco.trainlivemap.MainActivity
& "$androidSdk\platform-tools\adb.exe" logcat
```

instrumentation test がある場合:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

### 本番 API smoke test

```powershell
$baseUrl = "https://train-live-map.vercel.app"
$trains = Invoke-RestMethod "$baseUrl/api/trains?lines=tokaido,yamanote"
$status = Invoke-RestMethod "$baseUrl/api/service-status?lines=tokaido,yamanote"
$emptyTrains = Invoke-RestMethod "$baseUrl/api/trains?lines="
$railways = Invoke-RestMethod "$baseUrl/api/railways"
$community = Invoke-RestMethod "$baseUrl/api/community-reports"

$trains | Select-Object generatedAt,dataUpdatedAt,isMock,source,fallback,notice
$status | Select-Object isMock,source,fallback,notice
$emptyTrains | Select-Object generatedAt,dataUpdatedAt,isMock,source,fallback,notice
$railways | Select-Object generatedAt,source
$community | Select-Object windowMinutes,cooldownSeconds,persistent,votingEnabled
```

`isMock`, `fallback`, `notice`, `railways.source` は異常ではなく、バックエンドが明示する現在状態です。値を隠さず、UI 表示を確認してください。本番 smoke test では `POST /api/community-reports` を実行せず、POST 契約は MockWebServer または専用 test backend で確認します。

## 12. 現在のテスト範囲

ローカル unit test は以下の種類をカバーします。追加・統合で変動するため、件数は固定して記載しません。

- 列車、運行情報、路線、コミュニティの API JSON、enum、nullable field、未知 key、座標配列の decode
- `lines` の正規化、空選択、Retrofit query、成功 snapshot と exact-query cache fallback
- cache なし／破損／query 不一致と、offline 列車 marker・検索・詳細抑止
- HTTP 400 の自動停止、429 `Retry-After`、503 bounded backoff、foreground だけの 10 秒同周期 polling
- DataStore 設定と snapshot の独立永続化、投票 ID の 12..100 文字 regex／再生成／並行取得
- community GET／POST body／header、1 操作 1 POST、cooldown と feature flag
- 路線の日本語／alias 検索、表示／お気に入り toggle、お気に入りのみ
- 状態 filter の重なり、0 件 disabled、単一 empty state
- 列車番号 prefix 検索、最大 5 件、選択時の filter reset
- 顔、方向ラベル、位置推定、TalkBack 文言と選択／無効 semantics
- 地理計算、nearest projection、fraction と progress clamp
- routeSegment の方向付け、fallback、有限 transition
- 運行情報の公式優先、最新 2 分、重大遅延、再開文脈
- current-location provider と repository/API へ位置を渡さない境界
- AdMob ID の両方設定／片方欠落／trim

`app/src/androidTest` の Compose UI test は、状態 filter、路線検索・表示・お気に入り、列車番号検索、運行情報、空状態などの主要操作を確認します。端末依存の MapLibre rendering、Canvas の視覚差分、camera／gesture、OS の permission dialog、実 API、UMP、Google Mobile Ads 配信は自動 test だけでは保証できません。前節の Emulator / 実機確認をリリース gate に残します。

## 13. Release と GitHub への保存

Release 前チェックリスト:

- [ ] `versionCode` と `versionName` を更新
- [ ] Web API 契約の参照 commit を更新・記録
- [ ] 本番 read endpoint を選択 `lines` と空 `lines` の両方で smoke test（community POST は test backend のみ）
- [ ] `testDebugUnitTest`, `lintDebug`, `assembleDebug` が成功
- [ ] API 37 Emulator で起動、foreground/background、offline 復帰を確認
- [ ] mock / fallback / stale / attribution / 凡例 / 詳細の常時位置推定注意が見える
- [ ] offline cache の列車が marker／検索／詳細に出ず、現在地座標が backend request に含まれない
- [ ] 400／429／503 の抑止、10 秒 foreground polling、復帰即時取得、1 操作 1 POST を確認
- [ ] `community_reporter_identity.preferences_pb` が backup／device transfer 対象外
- [ ] Debug が Google test ad、Release は非公開 ID または広告無効
- [ ] Release signing を安全な外部設定から注入
- [ ] `local.properties`, keystore, logs, `.reference-web`, build output が未追跡
- [ ] token や実広告 ID が diff と履歴にない
- [ ] repository visibility が Web 版と一致

Git の監査例:

```powershell
git status --short
git diff --check
git grep -n -I -E "ODPT_ACCESS_TOKEN|ca-app-pub-[0-9]+[~/][0-9]+" -- `
  ":(exclude)README.md" ":(exclude)HANDOFF.md"
```

最後の検索では、意図している Google 公開 test ID 以外の ID がないことを確認します。秘密情報の検査を自動化する場合も、秘密値そのものを command line やログへ出さないでください。

Release signing は現在リポジトリに定義しません。keystore、alias、password、Play Console credential は安全な外部保管を使います。GitHub へ保存する際は、この Android repository の branch だけを commit / push し、Web 版の checkout を作業対象に含めないでください。

## 14. 未設定の外部項目

ソース完成と Debug 検証を妨げない外部項目:

- Release 用 AdMob App ID
- Release 用 anchored adaptive banner Ad Unit ID
- Release signing / Play Console 登録情報

AdMob の 2 ID がなくても Debug は Google 公式 test ID で動作し、Release は広告領域なしで動作する設計です。これらを待つために API、地図、キャッシュ、テストの作業を止める必要はありません。

## 15. 既知の制約と保守上の注意

- 列車位置は GPS ではなく位置推定を含みます。
- 補間は最新 snapshot までの有限 transition であり、将来位置予測ではありません。
- offline では保存済み列車 snapshot があっても過去位置を現在の marker／検索／詳細へ表示しません。初回 offline で路線 snapshot もなければ路線データもありません。
- 現在地はユーザー操作時の Network Provider によるおおよその位置だけで、屋内、端末設定、provider 状態によって取得できない場合があります。backend へは送信しません。
- community report はユーザー投稿の集計であり公式情報ではありません。`votingEnabled`、window、cooldown、backend の永続化設定に依存します。
- API snapshot は保存しますが、CARTO tile の完全オフライン利用は保証しません。
- `TrainMap` は Compose `AndroidView` での `SurfaceView.surfaceChanged` 同期待ちを避けるため、MapLibre の `textureMode(true)` を使います。TextureView mode は SurfaceView mode より描画コストが高い場合があるため、低速端末でフレーム時間とメモリを実測してください。
- API enum の未知値には model 更新が必要です。
- 路線取得は起動時 1 回と手動再試行です。定期更新要件へ変える場合は lifecycle と cache の test を追加してください。
- background polling はありません。バックグラウンド更新を追加する場合は、通信量、電池、Android background 制限、ユーザーへの説明を改めて設計してください。
- Release shrinker は有効です。serialization model の keep rule を削除する場合は Release build でも decode を確認してください。
- CI は未構成です。GitHub Actions を追加する場合も Android SDK license、JDK 17、API 37、Gradle cache、秘密情報の境界を明示してください。

## 16. トラブルシュート

### SDK location not found

`local.properties` の `sdk.dir` を実環境に合わせます。サンプルのパスをそのまま使わないでください。

### API 取得失敗

1. `API_BASE_URL` が `http` ではなく HTTPS であることを確認する。
2. 選択 `?lines=tokaido,yamanote` と空 `?lines=` を含む read endpoint を端末外から個別に smoke test する。
3. HTTP status と `Retry-After`、`isMock`, `fallback`, `notice` を確認する。400 を高速に自動再試行しないことも確認する。
4. 端末を offline にして offline/stale 表示へ切り替わり、cached 列車位置が marker／検索／詳細に出ないことを確認する。
5. 同じ路線集合でも query の並びが正規化され、別 query の snapshot を fallback していないことを確認する。
6. ODPT token をアプリへ追加して回避しない。Vercel 側を直す。

### 位置情報権限が出ない／現在地へ移動しない

権限は起動時ではなく現在地ボタンを押したときだけ要求します。端末の「おおよその位置」、システム位置情報、Network Provider を確認してください。正確な位置を許可させる変更や、取得座標を backend へ送る回避は行いません。

### コミュニティ投稿ができない

`votingEnabled`、cooldown、429 の `Retry-After`、400 の入力エラーを確認します。投票 ID は `community_reporter_identity` 内で regex に合う値へ再生成されます。POST を WorkManager へ渡したり、自動再送したりしないでください。

### 背景地図が出ない

列車 API と CARTO raster tile は別ホストです。API が成功しても tile の DNS、TLS、provider 障害、ネットワーク制限で背景だけ出ないことがあります。attribution を削除して別 tile を試すのではなく、provider 利用条件を確認した上で style を変更します。

### Release で広告が出ない

`ADMOB_APP_ID` と `ADMOB_BANNER_AD_UNIT_ID` の両方、UMP の `canRequestAds()`、Mobile Ads 初期化完了を確認します。どちらかの ID が空なら非表示が正しい挙動です。Debug の公開 test ID を Release に転用しません。

### stale が消えない

`dataUpdatedAt` はサーバー生成時刻ではなく、表示中列車データの最新 timestamp です。`generatedAt` だけが新しくても `dataUpdatedAt` が 90 秒超古ければ stale 表示が正しい挙動です。

## 17. ランチャーアイコン

Web 版 `public/icons/train-live-map-1024.png` を元にした電車前景は `artwork/train-foreground-source.png` にあります。背景色は `app/src/main/res/values/colors.xml` の `launcher_background` (`#F68B1E`) です。

`python tools/generate_launcher_icons.py` は次を一括生成します。

- 108 dp Adaptive / monochrome 前景（xxxhdpi 432×432 px）
- mdpi〜xxxhdpi の legacy / round legacy PNG
- `artwork/play-store-icon-512.png`（512×512、RGBA、sRGB、1 MB 未満）
- `.verification/launcher-mask-preview.png`

Adaptive 前景は中央 66 dp 安全円内へ収め、circle / rounded square / squircle の各マスクで前景のクリップが 0 ピクセルになることをスクリプトで検証します。アイコン更新時は生成コマンド、`lintDebug`、`assembleDebug` を再実行し、マスクプレビューも目視確認してください。

## 18. 秘密情報インシデント

次を Git に入れないでください。

- `ODPT_ACCESS_TOKEN`
- 実 AdMob ID
- `local.properties`
- keystore と署名 password
- Vercel、Google、GitHub の token

誤 commit が判明した場合:

1. 影響する token / credential / ID の利用を止め、管理画面で失効または再発行する。
2. repository visibility に関係なく漏えいとして扱う。
3. secret scanning と Git 履歴の除去を行う。
4. force push が必要なら共同作業者へ影響を周知する。
5. Android APK、artifact、CI log、release note にも残っていないか確認する。

ファイルを最新 commit から消すだけでは、既存の Git 履歴や配布済み artifact からは消えません。
