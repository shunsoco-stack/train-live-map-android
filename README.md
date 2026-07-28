# Train Live Map for Android

関東を走る JR 列車の位置と運行状況を地図上で確認する、日本語のネイティブ Android アプリです。WebView は使用せず、画面は Jetpack Compose、地図は MapLibre Android で実装しています。

- Android リポジトリ: [shunsoco-stack/train-live-map-android](https://github.com/shunsoco-stack/train-live-map-android)
- 仕様確認元の Web 版: [shunsoco-stack/train-live-map](https://github.com/shunsoco-stack/train-live-map)
- Web 版の確認コミット: [`7315203`](https://github.com/shunsoco-stack/train-live-map/commit/7315203af8057cd7094f858847affcd29770bb3a)

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

アプリ自身が明示する通信権限は `INTERNET` と `ACCESS_NETWORK_STATE` です。端末の位置情報は利用せず、MapLibre 依存から merge される位置情報権限も Manifest で明示的に除去します。Google Mobile Ads SDK 由来の Advertising ID / Privacy Sandbox 関連権限などは、最終的な merged Manifest と Play Console の申告で監査してください。

## 主な機能

- MapLibre 上に API 提供の JR 路線ポリラインを路線色で描画
- 路線名・カテゴリ・別名による検索、表示／非表示、全表示／全非表示
- 路線のお気に入り登録と「お気に入りのみ」の一発絞り込み
- `すべて`、`走行中`、`停車中`、`遅延`、`運転見合わせ`の列車状態フィルター
- 運行情報、取得元、最終更新、次回更新までの秒数、mock／fallback／offline／stale の表示
- 列車タップ時の詳細 Bottom Sheet
- 路線色、方向ラベル、遅延吹き出し、状態別の顔を持つ Compose Canvas 製の列車マーカー
- `routeSegment` と路線ポリラインに沿った有限時間のスムーズな位置補間
- ライト／ダークテーマ、edge-to-edge、縦画面優先のレスポンシブ配置
- TalkBack 用の路線名・方向・行き先・種別・状態・遅延説明と、約 48 dp 以上の操作領域
- Preferences DataStore による設定と API スナップショットの永続化
- UMP の同意状態を確認してから表示する AdMob アダプティブバナー
- Web 版のアイコンを元にした、可愛い電車の Adaptive / themed / legacy icon

列車番号は画面にも TalkBack の読み上げにも表示しません。

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

Android 側のモデルは Web 版コミット [`7315203`](https://github.com/shunsoco-stack/train-live-map/commit/7315203af8057cd7094f858847affcd29770bb3a) の `src/types/train.ts`、`src/types/railway.ts` と Route Handler を基準にしています。

| Endpoint | 用途 | 主なレスポンス |
| --- | --- | --- |
| `GET /api/trains` | 列車位置 | `trains`, `generatedAt`, `dataUpdatedAt`, `isMock`, `source`, `fallback`, `notice` |
| `GET /api/service-status` | 運行情報 | `serviceStatus`, `isMock`, `source`, `fallback`, `notice` |
| `GET /api/railways` | 地図線形と路線選択肢 | `lines`, `options`, `generatedAt`, `source` |

`trains[]` は `id`, `lineId`, `lineName`, `lineColor`, `direction`, `destination`, `trainType`, `latitude`, `longitude`, `delayMinutes`, `speedKmh`, `status`, `lastUpdatedAt`, `stoppedSince`, `dataAccuracy`, `routeSegment` をデコードします。バックエンド契約に含まれる `trainNumber` もデコードしますが、プロダクト UI では使用しません。

`routeSegment` は `fromFraction`, `toFraction`, 任意の `coordinates` を持ちます。GeoJSON と同じく座標配列は `[longitude, latitude]` 順です。

`serviceStatus` は `lineName`, `severity`, `message`, `updatedAt`, `dataAccuracy` を持ちます。`lines` は地図用ポリライン、`options` は検索・選択用の `available`, `coverage`, `coverageNote`, `aliases`, `kind` を含みます。路線の利用可否と coverage は固定値ではなく、常に API 応答を尊重します。

開発時の疎通確認例:

```powershell
$baseUrl = "https://train-live-map.vercel.app"
Invoke-RestMethod "$baseUrl/api/trains"
Invoke-RestMethod "$baseUrl/api/service-status"
Invoke-RestMethod "$baseUrl/api/railways"
```

## 更新、ライフサイクル、列車の移動

| データ | 更新タイミング |
| --- | --- |
| 列車位置 | フォアグラウンド開始時に即時、その後 7 秒待機ごと |
| 運行情報 | フォアグラウンド開始時に即時、その後 30 秒待機ごと |
| 路線情報 | ViewModel 初期化時。手動の「再試行」でも再取得 |

`MainActivity` が `ON_START` / `ON_STOP` を `MainViewModel` へ伝えます。バックグラウンドでは列車・運行情報の polling と画面時計を停止し、復帰時に即時更新します。手動の再試行は 3 endpoint を並行更新します。

列車位置は受信ごとに瞬間移動させず、次のルールで約 5.5 秒かけて補間します。

1. `routeSegment.coordinates` があれば、その線形と API の fraction を使用する。
2. fraction だけなら対象路線の最も近いポリラインを使い、outbound は増加方向、inbound は減少方向にする。
3. `routeSegment` がなければ API 座標を最寄りの路線ポリラインへ投影する。
4. 路線線形もなければ API の latitude / longitude をそのまま使う。
5. fraction と補間進捗は `0..1` に clamp し、最新 target 到達後は停止する。

アニメーション状態は Compose の再コンポーズから独立した `TrainMotionCoordinator` に保持されます。新しいサーバー根拠がないまま予測移動を続けることはありません。

## キャッシュとオフライン

単一の Preferences DataStore `train_live_map` に次を保存します。

- お気に入り路線 ID
- 表示路線 ID と初期化済みフラグ
- 「お気に入りのみ」の設定
- `/api/trains`、`/api/service-status`、`/api/railways` の最後に成功した JSON

`files/datastore/train_live_map.preferences_pb` は cloud backup と device transfer の対象に明示しており、対応する Android の復元経路でも設定と snapshot を引き継ぐ方針です。端末や OS による実際の復元可否は保証されないため、リリース時に実機でも確認してください。

Repository は network-first です。endpoint ごとに成功応答を上書き保存し、取得失敗時は同じ endpoint の最後にデコードできたスナップショットを返します。UI はデータを消さず、「オフライン」「データが古い」、最終更新、再試行を表示します。列車データは `dataUpdatedAt` が現在時刻より 90 秒超古い場合にも stale と判定します。

新規インストール時の表示路線は、API が利用可能として返した東海道線があれば東海道線、なければ最初の利用可能路線です。その後のユーザー設定は再初期化しません。「お気に入りのみ」は、表示中路線とお気に入り路線の積集合を地図へ適用します。

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

`app/src/test` のローカルテストは、少なくとも次を検証します。

- 3 endpoint の JSON デコードと `[longitude, latitude]`
- 通常／遅延／運転見合わせと列車の顔
- 上り／下り表示と TalkBack 説明からの列車番号除外
- 路線検索、表示切替、お気に入り、お気に入りのみ
- 状態フィルター
- 路線への投影、補間、`0..1` clamp、方向別 fraction
- API 成功時の保存と失敗時の endpoint 別キャッシュ
- mock／fallback の保持
- DataStore での設定とスナップショット永続化
- ViewModel の 7 秒／30 秒 polling、background 停止、foreground 即時再開
- AdMob ID 未設定時の広告無効化

`app/src/androidTest` には、状態 filter chip、路線検索、表示切替、お気に入り、お気に入りのみの操作を確認する5件の Compose UI test もあります。端末依存の見た目、MapLibre の gesture、実ネットワーク、UMP フォーム、広告配信は、Emulator または実機でも確認してください。

## ディレクトリ構成

```text
app/src/main/java/com/shunsoco/trainlivemap/
├── ads/                 # AdMob 設定、UMP、adaptive banner
├── data/
│   ├── local/           # DataStore settings / snapshots
│   ├── model/           # Web API と対応する serializable model
│   ├── remote/          # Retrofit API / data source
│   └── repository/      # network-first + cache fallback
├── domain/
│   ├── geo/             # polyline、距離、投影、補間
│   ├── motion/          # routeSegment と有限 transition
│   ├── railway/         # 検索、お気に入り、表示絞り込み
│   └── train/           # 状態 filter、顔、表示文言、TalkBack
├── ui/
│   ├── components/      # 列車 Canvas、header、status panel
│   ├── map/             # MapLibre と motion coordinator
│   ├── sheets/          # 路線選択、列車詳細
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
- 初回オフライン起動で成功済みスナップショットがない場合は、表示できる列車・路線データがありません。
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
