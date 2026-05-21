# Phase 3 & 4 拡張: サブコア防衛システムと領土開拓の旗 実装完了ウォークスルー

本フェーズでは、「Territory Conquest Mod (1.18.2)」の根幹機能である**「サブコアによる聖域シールド（敵進入防止障壁）」**および**「国庫ゴールドを消費した領土開拓（開拓の旗ブロック）」**の全機能を無事に実装完了しました。

---

## 1. 実装された機能の概要

### 🟢 A. サブコアブロック (`SubCoreBlock`) と設置制限
- **クラフトレシピ**: 金ブロック×4、泣く黒曜石×4、ダイヤモンド×1 でクラフト可能です。
- **設置バリデーション (`TerritoryPlacementEventHandler`)**: 以下の条件を厳格に検証し、満たさない場合は設置を拒否してチャットで警告します。
  1. **地上世界（オーバーワールド）のみ**設置可能。
  2. 水中や水流の中は不可。
  3. **真上が屋外（空が見える・日光が届く）**であること（洞窟内や地中埋め込みの禁止）。
  4. **自分自身の領土内（聖域またはすでに旗で確保したチャンク）**であること。
- **データ保存**: 設置位置は `TerritorySavedData` にプレイヤーごとに永続保存されます。破壊されるとリストから自動で削除されます。

### 🛡️ B. 聖域シールド（進入障壁）の実装 (`SanctuaryShieldEventHandler`)
- プレイヤーが「他プレイヤーの聖域チャンク」に侵入しようとした際、その所有プレイヤーの**サブコアが1つ以上生存している場合**、強力なノックバックによってシールド外に弾き飛ばされます。
- アクションバーに「`§c警告: 進入障壁シールドにより侵入できません！`」と赤文字で警告され、シールド防衛時の効果音が響きます。

### 🪙 C. サブコアGUI画面と取引システム (`SubCoreScreen`)
- サブコアを右クリックした際に開く、プレミアムなダークグラスモーフィズムデザインの取引画面です。
  - **ゴールド一括納品**: 手持ちの金塊(1G相当)、インゴット(9G相当)、ブロック(81G相当)を自動スキャンして回収し、国庫ゴールド（`treasury`）に一括チャージするボタン。
  - **領有旗の購入 (50G)**: 国庫のゴールドを 50G 消費して、領有権主張アイテムである「開拓の旗」を1個購入するボタン。
  - **他人のサブコア判定**: 自分が所有者でない場合は取引ボタンが非活性になり、「`※自分のサブコアでのみ取引可能です`」と赤文字で美しく表示されます。

### 🚩 D. 開拓の旗ブロック (`TerritoryFlagBlock`) による領土主張
- 松明のように当たり判定がない（`noCollission`）装飾用ブロックです。
- **領土化**: 自領チャンクに隣接する未開拓（中立）のチャンクに設置することで、そのチャンクが即座にプレイヤーの所有領土として登録されます。
- **領土解除**: 旗ブロックが破壊される（誰でも一撃で破壊可能）と、そのチャンクは中立に戻ります。

---

## 2. 新規追加・変更されたファイル一覧

### 1️⃣ ブロック・アイテム実装
- **[MODIFY] [ModBlocks.java](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/java/com/example/territory/block/ModBlocks.java)**
  - サブコアブロック (`SUB_CORE_BLOCK`) と開拓の旗 (`TERRITORY_FLAG`) をDeferredRegisterに追加。
- **[MODIFY] [ModItems.java](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/java/com/example/territory/item/ModItems.java)**
  - サブコアおよび旗ブロック用の BlockItem を登録。
- **[NEW] [SubCoreBlock.java](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/java/com/example/territory/block/SubCoreBlock.java)**
  - 右クリック時に所有者情報を乗せて同期パケットを送信し、GUIを起動するサブコア本体。
- **[NEW] [TerritoryFlagBlock.java](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/java/com/example/territory/block/TerritoryFlagBlock.java)**
  - 当たり判定のない領土フラグブロック。

### 2️⃣ システム・イベント・ネットワーク
- **[NEW] [TerritoryPlacementEventHandler.java](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/java/com/example/territory/event/TerritoryPlacementEventHandler.java)**
  - サブコア・旗設置時のディメンション、水中、屋外、領有状況バリデーションイベントハンドラ。
- **[NEW] [SanctuaryShieldEventHandler.java](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/java/com/example/territory/event/SanctuaryShieldEventHandler.java)**
  - 敵の聖域に侵入したプレイヤーをベクトルベースで弾き返す障壁シールドイベントハンドラ。
- **[NEW] [SubCoreScreen.java](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/java/com/example/territory/client/gui/SubCoreScreen.java)**
  - ゴールド預け入れと旗購入を管理するダークグラスモーフィズムGUI画面。
- **[NEW] ネットワークパケット群**
  - [PacketSyncSubCoreInfo.java](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/java/com/example/territory/network/PacketSyncSubCoreInfo.java)
  - [PacketRequestDepositGold.java](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/java/com/example/territory/network/PacketRequestDepositGold.java)
  - [PacketRequestFlagPurchase.java](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/java/com/example/territory/network/PacketRequestFlagPurchase.java)
- **[MODIFY] [ModMessages.java](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/java/com/example/territory/network/ModMessages.java)**
  - 新規作成した上記3種類のパケットをネットワーク登録。

### 3️⃣ リソース定義（レシピ、モデル、テクスチャ、ローカライズ）
- **[NEW] [sub_core_block.json](file:///Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/resources/data/territory_conquest/recipes/sub_core_block.json)**
  - サブコアのクラフトレシピ定義。
- **[NEW] `ja_jp.json`** と **[MODIFY] `en_us.json`**
  - 日本語（「サブコア」「開拓の旗」）と英語の翻訳テキストを追加。
- **[NEW] ブロックステートおよび3DモデルJSON群**
  - サブコア、旗の3D描画モデルとブロックステート定義を追加。
- **[NEW] 超高精細AI生成テクスチャ**
  - **サブコア (`sub_core_block.png`)**: 金の額縁、泣く黒曜石、シアンのルーン装飾。
  - **開拓の旗 (`territory_flag.png`)**: ロイヤルブルーのバナー、金色の紋章、ウッドポール。

---

## 3. ビルドとデプロイの検証結果

### 🛠️ Gradleビルド
- **コンパイル検証**: `./gradlew compileJava` ➡️ **BUILD SUCCESSFUL** (エラーなし)
- **JARファイルの作成**: `./gradlew jar` ➡️ **BUILD SUCCESSFUL** (エラーなし)
- **デプロイ先**: 最新ModのJARファイルは、MinecraftのModフォルダへ自動配置されました。
  - 配置先: `/Users/kirinokazuya/Library/Application Support/minecraft/mods/territory-conquest-mod-1.0.0.jar`

---

## 4. 手動動作検証マニュアル（テスト項目）

ワールドに入り、以下の順序で動作を確認できます。

1. **サブコアクラフト＆設置制限の検証**:
   - サバイバルモードで金ブロック×4、泣く黒曜石×4、ダイヤモンド×1をクラフトテーブルに並べ、サブコアがクラフトできるか確認します。
   - 地下洞窟、水中、またはネザーで設置しようとした際、設置がキャンセルされて警告チャットが出ることを確認します。
   - 自分の領土（初期の聖域など）以外の場所で設置しようとした際に、拒否されることを確認します。

2. **ゴールドチャージ＆旗購入の検証**:
   - 自分の領土内に正しく設置したサブコアを右クリックします。
   - 取引画面（`SubCoreScreen`）が表示され、国庫残高（初期値: 1000G）が表示されることを確認します。
   - インベントリに「金塊」「金インゴット」等を持った状態で「手持ちの金を預ける」ボタンを押すと、アイテムが消費されて国庫ゴールドが増加することを確認します。
   - 「領有旗を購入 (50G)」ボタンを押し、国庫が50G減り、インベントリに「開拓の旗」アイテムが入ることを確認します。

3. **領土開拓＆旗の破壊の検証**:
   - 自分の領土に隣接する未開拓（中立）チャンクに入り、そこに「開拓の旗」を設置します。
   - 設置した瞬間、そのチャンクがプレイヤーの領土となり、Yキーのステータス画面に支配座標として追加されることを確認します。
   - 旗を壊すと領有が即座に解除され、中立に戻ることを確認します。

4. **聖域シールドの検証**:
   - サブコアが1つ以上設置されている敵プレイヤーの聖域（スポーン地点チャンク）へ侵入しようとします。
   - 見えない壁に弾かれるようにノックバックし、アクションバーに「警告」のメッセージが出ることを確認します。
