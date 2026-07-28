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

- short SHA: `7315203`
- full SHA: `7315203af8057cd7094f858847affcd29770bb3a`
- commit: [shunsoco-stack/train-live-map@7315203](https://github.com/shunsoco-stack/train-live-map/commit/7315203af8057cd7094f858847affcd29770bb3a)

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
          ↓
    TrainRepository
      ↙          ↘
Retrofit API   Preferences DataStore
                 ├─ user settings
                 └─ three response JSON snapshots
```

`TrainLiveMapApplication` の `AppContainer` が Retrofit、DataStore、Repository を組み立てる簡潔な manual DI です。`MainViewModelFactory` が Repository と SettingsStore を ViewModel へ渡します。

### 主な責務

| 場所 | 責務 |
| --- | --- |
| `data/model` | Web API と対応する kotlinx.serialization model |
| `data/remote` | Retrofit interface、base URL 正規化、JSON 設定 |
| `data/local` | お気に入り、表示設定、3 endpoint の response JSON snapshot |
| `data/repository` | network-first、成功時保存、失敗時 cache fallback |
| `domain/geo` | haversine、polyline index、投影、fraction、clamp |
| `domain/motion` | routeSegment 解決、方向付け、有限 transition |
| `domain/railway` | 路線検索、表示、お気に入り filter |
| `domain/train` | 状態 filter、顔、方向／状態文言、TalkBack |
| `ui/map` | MapLibre style/layer、marker 投影、animation coordinator |
| `ui/components` | Canvas 列車、header、data health、運行情報 |
| `ui/sheets` | 路線選択と列車詳細 Modal Bottom Sheet |
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

`trainNumber` は互換デコードのため model にありますが、画面と accessibility description では使用しません。`routeSegment` は `fromFraction`, `toFraction`, 任意の `coordinates` を持ちます。

### `GET /api/service-status`

```text
ServiceStatusApiResponse
├── serviceStatus
│   ├── lineName
│   ├── severity: "normal" | "minor" | "major"
│   ├── message
│   ├── updatedAt
│   └── dataAccuracy
├── isMock
├── source
├── fallback
└── notice
```

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

### API 変更時の手順

1. Web 版の対象 commit と `src/types/train.ts`, `src/types/railway.ts`、3 Route Handler を確認する。
2. Android の `data/model` を更新する。
3. `ApiJsonDecodingTest` の JSON fixture を実際の新契約に合わせる。
4. cache に旧 JSON が残る upgrade path を確認する。互換性がない場合は key versioning または migration を設計する。
5. mock、fallback、notice、coverage、available の表示が失われていないことを確認する。
6. 3 endpoint の本番 smoke test とオフライン再起動を実施する。
7. HANDOFF に新しい Web 版 commit SHA を記録する。

## 5. polling と lifecycle

`MainActivity` は Lifecycle event を `MainViewModel.setForeground` へ渡します。

| Event | 挙動 |
| --- | --- |
| ViewModel 初期化 | `/api/railways` を 1 回取得 |
| `ON_START` | foreground 化し、列車と運行情報を即時取得して polling 開始 |
| foreground 継続 | 列車取得完了後 7 秒待機、運行情報取得完了後 30 秒待機 |
| `ON_STOP` | 2 polling job と 1 秒 clock job を cancel |
| 再度 `ON_START` | 古い timer を再利用せず即時取得 |
| ヘッダーの更新／data health の再試行 | 3 endpoint を並行取得 |

`Mutex` により同一 endpoint の更新を直列化します。次回列車更新までの表示は 1 秒 clock job で更新され、バックグラウンドでは消えます。

注意点として、7 秒／30 秒は fixed-rate ではなく「取得完了後の delay」です。通信時間分だけ次回開始は後ろへずれます。fixed-rate へ変更する場合も、重複 request と foreground 復帰時の即時更新を壊さないでください。

## 6. 列車位置とアニメーション

`TrainMotionCoordinator` は `remember` され、再コンポーズでは作り直されません。列車ごとの snapshot signature が変わった時だけ新しい target を作り、現在描画位置から約 5.5 秒の smoothstep transition を開始します。

優先順位:

1. `routeSegment.coordinates` の明示的な from → to 線形
2. `routeSegment` の fraction と最寄りの路線ポリライン
3. API latitude / longitude を最寄り路線へ投影
4. 線形がなければ API 座標

fraction だけの区間は outbound を増加方向、inbound を減少方向にします。fraction、segment progress、transition progress は `0..1` に clamp します。target 到達後は停止し、新しい API snapshot なしに速度外挿しません。

データ精度は UI の重要な契約です。ODPT の列車位置は GPS 位置ではなく、駅・駅間情報からの推定を含みます。ヘッダーと詳細画面の「位置推定」説明を削除しないでください。

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

初回の表示路線初期化は `visible_line_ids_initialized` で一度だけ行います。API の available option のうち `tokaido` があれば選び、なければ先頭を選びます。ユーザーが「すべて隠す」を選んだ状態を未初期化と混同しないことが、このフラグの目的です。

Repository の原則:

- 成功した endpoint だけ、その endpoint の JSON snapshot を置き換える。
- snapshot 書き込み失敗でも live response は利用する。
- network error 時は endpoint ごとに cache を best-effort decode する。
- cache を返す場合も元の error を残し、UI が stale/offline を明示できるようにする。
- cancel は握りつぶさず再 throw する。
- cache も壊れていれば `data = null` と error を返す。

列車データの `dataUpdatedAt` が 90 秒より古い場合も stale です。初回オフラインかつ cache なしではデータを作り出しません。

設定と snapshot は同一インストール内の再起動で永続化されます。`backup_rules.xml` と `data_extraction_rules.xml` は Preferences DataStore の実体に合わせ、`file` domain の `datastore/train_live_map.preferences_pb` を cloud backup と device transfer の対象へ明示しています。OS の復元経路を介した端末間移行は実機でも検証してください。将来ここへ秘密情報を保存しないでください。

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

## 9. 列車マーカーと accessibility

`TrainMarker` は 72 × 86 dp の再利用可能な Canvas component です。

- body は `lineColor`
- inbound は `↑ 上り`、outbound は `↓ 下り`
- delay は上部に `+N分`
- suspended は上部に `見合わせ`
- normal は笑顔
- delayed または `delayMinutes > 0` は困り眉・困り顔
- suspended は悲しい顔と青い涙

方向と状態は色だけで伝えません。TalkBack description には路線名、方向、行き先、種別、状態、遅延を含め、列車番号を除外します。48 dp 程度の tap target、font scale、高コントラストを維持してください。

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

1. portrait で初回起動し、3 endpoint からデータが表示される。
2. pan / zoom 中も header、route chip、banner が地図操作を妨げない。
3. 7 秒更新時に列車が瞬間移動せず、線路上で停止する。
4. 列車 tap で詳細項目と位置推定表示が出る。
5. 路線検索、表示切替、お気に入り、お気に入りのみ、状態 filter を操作する。
6. process を終了して再起動し、設定が残る。
7. 通信を切って再起動し、保存済み snapshot、offline/stale、最終更新、再試行が出る。
8. 通信を戻して再試行し、live 表示へ復帰する。
9. Home へ移動中に polling が止まり、復帰直後に request される。
10. light / dark、最大 font scale、TalkBack、横画面／tablet 幅を確認する。
11. attribution が見え、広告が地図や Bottom Sheet に重ならない。

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
$trains = Invoke-RestMethod "$baseUrl/api/trains"
$status = Invoke-RestMethod "$baseUrl/api/service-status"
$railways = Invoke-RestMethod "$baseUrl/api/railways"

$trains | Select-Object generatedAt,dataUpdatedAt,isMock,source,fallback,notice
$status | Select-Object isMock,source,fallback,notice
$railways | Select-Object generatedAt,source
```

`isMock`, `fallback`, `notice`, `railways.source` は異常ではなく、バックエンドが明示する現在状態です。値を隠さず、UI 表示を確認してください。

## 12. 現在のテスト範囲

ローカル unit test は以下をカバーします。

- API JSON、enum、nullable field、未知 key、座標配列の decode
- endpoint ごとの成功 snapshot と network failure fallback
- cache なし／破損 cache 時の挙動
- DataStore 設定と snapshot の独立永続化
- 路線の日本語／alias 検索、表示／お気に入り toggle、お気に入りのみ
- 状態 filter の重なりと suspended 優先
- 顔、方向ラベル、TalkBack 文言
- 地理計算、nearest projection、fraction と progress clamp
- routeSegment の方向付け、fallback、有限 transition
- ViewModel の 7 秒／30 秒 polling、background 停止、foreground 復帰時の即時再開
- AdMob ID の両方設定／片方欠落／trim

`app/src/androidTest` の Compose UI test 5件は、状態 filter chip、路線検索、表示切替、お気に入り、お気に入りのみの操作を確認します。端末依存の MapLibre rendering、Canvas の視覚差分、gesture、実 API、UMP、Google Mobile Ads 配信は自動 test だけでは保証できません。前節の Emulator / 実機確認をリリース gate に残します。

## 13. Release と GitHub への保存

Release 前チェックリスト:

- [ ] `versionCode` と `versionName` を更新
- [ ] Web API 契約の参照 commit を更新・記録
- [ ] 本番 3 endpoint を smoke test
- [ ] `testDebugUnitTest`, `lintDebug`, `assembleDebug` が成功
- [ ] API 37 Emulator で起動、foreground/background、offline 復帰を確認
- [ ] mock / fallback / stale / attribution / 位置推定が見える
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
- 初回オフラインで snapshot がなければ表示データはありません。
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
2. 3 endpoint を端末外から個別に smoke test する。
3. `isMock`, `fallback`, `notice` を確認する。
4. 端末を offline にして cache 表示へ切り替わるか確認する。
5. ODPT token をアプリへ追加して回避しない。Vercel 側を直す。

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
