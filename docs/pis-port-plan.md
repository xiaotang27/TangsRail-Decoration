# TJPIS（MTR4 / Joban JCM）→ TRDM（MTR3）移植计划

对象：`D:\software\Minecraft\.minecraft\versions\MTR4\resourcepacks\TJPIS_1.0\assets\jsblock`
目标：在 TRDM（Fabric 1.20.1 + MTR 3.6.3，`mtr.*`）里复刻这套 PIS 的界面，并把 MTR4/JCM 的数据获取方式换成 MTR3 的实现。

---

## 1. 源包现状

| 文件 | 作用 |
|---|---|
| `joban_custom_resources.json` | 注册一个 PIS 图 `pids_tj_custom`，挂 `scripts/pids_tj_custom.js` |
| `scripts/pids_tj_custom.js` | 全部绘制逻辑：`create(state)` / `render(ctx, state, pids)` / `dispose` |
| `textures/tjpids.png` | 背景板（原图 1920×1080），左列三块蓝色面板 + 右侧黑底广告区 + 下方跑马灯条 |
| `textures/tc.png` `zj.png` `0tc.png` | 车厢底图：头车 / 中间车 / 尾车 |
| `textures/adsdef.png` | 默认广告图 |
| `textures/ads/adsN.png` | 可选广告图，按编号递增探测（本包里没有） |

### 画布与排版（从脚本坐标反推，单位为 PIS 虚拟像素）

- 画布 `pids.width × pids.height` ≈ **136 × 72**（广告 37+97=134、跑马灯 41..134、车厢 y=H-8 全部吻合）
- `Text.create().scale(s)` → 字高 = **9 × s** 画布单位（由脚本自身常量反推：`charWidth 5.4` 配 `scale 0.6` ⇒ 0.6 × 9 = 5.4）；`.pos(x, y)` = 文字中心
- 跑马灯**改成整条文字连续平移**（原脚本是逐字跳），速度 `38.6` 画布单位/秒、裁剪在跑马灯条内
- 布局常量（画布单位）：

| 元素 | 位置 | 字号 | 颜色 |
|---|---|---|---|
| 本次列车 ETA（已到站 / 即将进站） | (18, 15) 居中 | 0.65 | `0xFFFF00` |
| 本次列车 N 分钟 | (17, 15) / (21, 18.5) | 0.8 / 0.2 | 黄 / 白 |
| 本次列车 开往 X | (18, 24) | 0.3 | 白 |
| 下次列车（同上一整套） | y + 23 | 同上 | 同上 |
| 时钟 `HH:mm` | (18, 54) | 0.8 | 白 |
| 日期 `yyyy/MM/dd 星期X` | (18, 62) | 0.3 | 白 |
| 广告图 | (37, 3)，97×56 | — | 每 5000 ms 换下一张，缺图退化到 `adsdef.png` |
| 跑马灯 | y=60，x 41..134，可见 19 字 | 0.6 | 白 |
| 车厢图 | 车宽 14 / 间隔 1.2 / 高 6，整排居中，车顶 y=H-8 | 车号 0.35 | 白 |

- 跑马灯：`charWidth = 5.4`，每 **70 ms** 左移 `charWidth/2`，滚完重置
- 车厢：默认 6 节，能用列车实际节数就用实际节数；头车 `tc.png`、尾车 `0tc.png`、中间 `zj.png`
- 文案：`已到站` / `即将进站` / `N 分钟` / `开往:中文站名`

---

## 2. 数据获取：MTR4(JCM) → MTR3 对照

MTR3 里这些东西**都是现成的**（我直接读了 `MTR-fabric-1.20.1-3.6.3.jar` 的字节码），不用自己算 ETA。

| 脚本里的写法（MTR4/JCM） | MTR3 对应实现 | 已核实的成员 |
|---|---|---|
| `pids.arrivals().get(i)` | `ClientData.SCHEDULES_FOR_PLATFORM`（站台 id → 到站条目），配 `ClientData.STATIONS` / `ClientData.PLATFORMS` / `RailwayData.getStation(...)` 过滤 —— MTR3 自己的 PIDS 渲染器 `RenderPIDS.getSchedules(entity, pos, map)` 就是这么取的 | `mtr.client.ClientData.SCHEDULES_FOR_PLATFORM` |
| `arrival.arrivalTime()`（epoch ms） | `ScheduleEntry.arrivalMillis`（epoch ms，语义完全一致，脚本里本来就是 `(arrivalTime - now)/1000`） | `mtr.data.ScheduleEntry.arrivalMillis` |
| `arrival.destination()` | `Route route = ClientData.DATA_CACHE.routeIdMap.get(entry.routeId)` → `ClientData.DATA_CACHE.getFormattedRouteDestination(route, entry.currentStationIndex, ...)` | `ClientCache.routeIdMap`、`ClientCache.getFormattedRouteDestination(Route, int, String)` |
| `arrival.carCount()` | `ScheduleEntry.trainCars`（脚本里两个重复的 `carCount()` + try/catch 全部删掉） | `mtr.data.ScheduleEntry.trainCars` |
| 站台 id | `RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos)`（TRDM 现有站名牌/集控装置已经在用），或刷子手动选 | 已有 |
| `TextUtil.getCjkParts(str)` | 自己按 `IGui.isCjk` 判语言后 split，或直接复用我们已有的 `TrdmBigSignContent.splitRouteName`（「中文\|English」拆分） | `mtr.data.IGui.isCjk`、`IGui.textOrUntitled` |
| `Timing.currentTimeMillis()` | `System.currentTimeMillis()` | — |
| `new java.text.SimpleDateFormat` | 原样可用 | — |
| `Resources.readString(...)` 探测广告图 | `Minecraft.getInstance().getResourceManager().getResource(id)` 判空 + 监听资源重载重建列表 | — |
| `Texture.create().texture(...).size().pos().draw()` | AWT `drawImage` 到烘焙画布（TRDM 现有 `TrdmHeaderTextures` 那套） | — |

> 备注：`RenderPIDS` 里 `ScheduleEntry` 是按 `compareTo` 排序的（`arrivalMillis` 升序），所以 `arrivals()` 的第 0/1 条 = 列表前两个。

---

## 3. 渲染架构（TRDM 侧）

目标：同屏 60 fps 下不能卡。实测（`gradlew benchSignBake`）一张 4096×384 的牌面同步烘焙 ≈ 10 ms，所以**不能每帧整张重烘**。

推荐**分层渲染**：

1. **静态层**（背景 `tjpids.png` + 固定标签）—— 只在站台/线路变化时烘一次，沿用 `TrdmHeaderTextures.resolveNow` 的同步烘焙（约 10 ms 一次性开销）。
2. **动态文本层**（ETA 数字、开往站、时钟、日期）—— 整块 512×288 的小贴图，**250 ms 刷新一次**（只重画文本，不重画背景），成本 ≈ 1~2 ms/次。
3. **跑马灯层** —— 单独一条窄贴图（如 1024×48），**70 ms 一次**只平移像素（可以复用同一张位图做偏移绘制，不再重新排版文字）。
4. **广告 / 车厢图** —— 直接作为静态贴图四边形贴上去；广告每 5 s 换一张资源。

几何：PIS 方块一个（贴墙 + 吊挂两态可选），面板四角由方块 facing 推出，`entityCutout`，带宽高比 = 136:72 的贴图，按牌面尺寸等比放大（建议画布放大 8×：1088×576）。

---

## 4. 施工阶段（每阶段都有可验收产物）

| 阶段 | 内容 | 产物 / 验收 |
|---|---|---|
| **P0 资源** | 把 `tjpids.png`、`tc/zj/0tc.png`、`adsdef.png` 搬到 `trdm:assets/trdm/textures/block/pis/`，广告目录留空 | 资源能加载 |
| **P1 纯排版** | 新建 `TrdmPisPanel`：把上面那张布局常量表一对一搬成 AWT，用**假数据**（固定 ETA/站名/日期）烘整张；加 `gradlew previewPis` 离线预览 | 出图与 MTR4 版并排目视一致（像素级对坐标） |
| **P2 方块 + 渲染器** | `BlockTrdmPIS` + BE + `RenderTrdmPIS`：只画静态层 + 假数据文本层，贴墙放置，刷子右键选站台（复用 `StationScope` / `PsdControlScreen`） | 游戏里能看到完整 PIS 静态画面 |
| **P3 接真数据** | `PisData`（客户端）：`SCHEDULES_FOR_PLATFORM` → 前 2 条 → `record(EtaSeconds, Destination, CarCount, RouteColor)`；替换 MTR4 那套调用 | 倒计时和「开往」跟列车实际一致；车厢数跟列车一致 |
| **P4 动态刷新** | 文本层 250 ms、跑马灯 70 ms、时钟每秒、广告 5 s；跑马灯文字可配置（lang 或命令） | 长时间运行不掉帧（/debug 采样 1 min） |
| **P5 收尾** | 悬挂款（复用 `BlockTrdmHungBigSign` 的几何思路）、多站台/多屏同步、缺失数据兜底（无到站 → 「暂无列车信息」） | 与 MTR4 版并排截图对比通过 |

---

## 5. 已确认的决定（2026-xx 反馈）

| 项 | 决定 |
|---|---|
| 屏幕比例 | **16:9**，按原 JCM 画布（136×72 单位）等比放大烘焙 |
| 模型与贴图 | **先直接用 JCM 这套**（`tjpids.png` / `tc.png` / `zj.png` / `0tc.png` / `adsdef.png`），搬到 `trdm` 命名空间 |
| 广告图 | **由自定义资源包驱动**：资源包 `assets/trdm/` 下放广告图片 + 一份配置 json，格式如下 |
| 设置界面 | **刷子右键屏幕**打开单独设置界面，里面分三块：资源包设置、跑马文字、广告图设置 |

### 广告配置 json（资源包侧）

```json
{
  "pis_ads": {
    "xxx": {
      "name": "xxx.png",
      "time": 1000
    }
  }
}
```

- `time`：这张广告停留多久（**毫秒**），到点换下一张，按 json 里的顺序循环
- 资源包没有这份 json 或没有图片时，退回内置的默认广告（`adsdef.png`）

---

## 6. 待确认的点

1. json 与图片的确切路径（见下方提问）
2. 设置界面里「资源包设置 / 广告图设置」两个区块各自要做到什么程度
3. 跑马灯文字是每块屏幕各存一份，还是全服统一
4. 屏幕方块本身的尺寸与安装方式（贴墙 / 吊挂）

