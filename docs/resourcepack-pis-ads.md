# PIS 广告资源包

PIS 屏上的广告**全部由资源包提供**，模组本身不带任何广告内容。

## 目录结构

```
your-pack/
├── pack.mcmeta
└── assets/
    └── trdm/
        ├── pis_ads.json                          ← 广告清单
        └── textures/
            └── block/
                └── pis/
                    └── ads/
                        ├── ad_tianjin.png        ← 静态图
                        ├── ad_notice.gif         ← 动图
                        └── ad_video.mp4          ← 视频
```

## `pis_ads.json`

```json
{
  "pis_ads": {
    "tianjin": { "name": "ad_tianjin.png", "time": 8000 },
    "notice":  { "name": "ad_notice.gif" },
    "video":   { "name": "ad_video.mp4", "time": 4000 }
  }
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| 键名（`tianjin` / `notice` / …） | 是 | 广告的内部 id，**PIS 屏幕设置界面里显示的就是它**；顺序即播放顺序 |
| `name` | 是 | `assets/trdm/textures/block/pis/ads/` 下的文件名。**不带扩展名时按 `.png` 处理**；写 `.gif` / `.mp4` 就是动图 / 视频 |
| `time` | 否 | 这条广告显示多久（**毫秒**，最小 100）。不写时：视频按视频本身时长播，其余默认 **5000 ms** |

> **优先级**：多个资源包都带 `pis_ads.json` 时，**只有资源包列表里最靠上（优先级最高）的那一份生效**。
> 界面上显示的「已加载 N 个资源包」只是统计有几个包提供了这个文件，方便排查。

## 素材规格建议

| 类型 | 建议规格 | 说明 |
|---|---|---|
| 静态图 `png` | **720 × 432**（16:9） | 屏是 16:9 的，非 16:9 会被拉伸 |
| 动图 `gif` | 同上 | 逐帧解码，帧数多了会吃性能 |
| 视频 `mp4` | 建议 **320 × 192 / 15 fps**，几秒钟 | H.264，**不要音轨**；实时解码，性能开销最高 |

视频是纯 Java 解码（JCodec，随模组内置，玩家不用另外装），所以分辨率 / 帧率别开太高。
不想让服务器玩家播视频，就在通用设置里关掉「启用乘客资讯屏幕广告视频播放」（`config/trdm.json` 的
`pis_video_enabled`），关掉后 mp4 广告会被跳过，图片和动图不受影响。

## 快速生成一个示例包

仓库里带了一个生成器任务，直接产出一个能用的示例包（2 张 png + 1 个 mp4 + 清单 + `pack.mcmeta`）：

```bash
./gradlew makeSamplePack
# 输出：build/sample-pack/trdm-pis-sample/
```

把它整个文件夹（或打成同名 zip）丢进 `resourcepacks/` 就能在游戏里看到效果，
照着它的目录结构改成自己的图即可。

## 排查

1. PIS 屏幕设置里看「已读取 N 条广告」——0 条说明 `pis_ads.json` 没读到或格式不对；
2. 看日志里的 `[pis ads] 读到 N 条: …`，每条会打印 id、文件名和时长；
3. 广告显示但图是黑 / 紫黑格子：`name` 和实际文件名不一致，或忘了放 `textures/block/pis/ads/` 下面。
