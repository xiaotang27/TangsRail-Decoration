# PIS（乘客资讯显示屏）自定义广告播放

您可以通过制作Minecraft资源包的方式为TRDM的PIS屏幕添加自定义的广告内容。本篇教程将介绍如何实现。

> 本篇教程不涉及Minecraft资源包的原理和制作基础，关于制作Minecraft资源包的基础内容请参考网上资料。本教程默认阅读者了解如何制作Minecraft资源包。

## 概览
您要制作的资源包结构如下所示：
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
                        ├── ad_picture.png        ← 静态图
                        ├── ad_gif.gif            ← 动图
                        └── ad_video.mp4          ← 视频
```
对于`pis_ads.json`：

```json
{
  "pis_ads": {
        "tianjin": { 
            "name": "ad_tianjin.png", 
            "time": 8000 
        },
        "notice":  { 
            "name": "ad_notice.gif" 
        },
        "video":   { 
            "name": "ad_video.mp4", 
            "time": 4000 
        }
  }
}
```
| 字段 | 必填 | 说明 |
|---|---|---|
| 键名（`tianjin` / `notice` / …） | 是 | 广告的内部 id，**即PIS 屏幕设置界面里显示的**；顺序即播放顺序（可在游戏内调整） |
| `name` | 是 | `assets/trdm/textures/block/pis/ads/` 下的文件名。**不带扩展名时按 `.png` 处理**；写 `.gif` / `.mp4` 就是动图 / 视频 |
| `time` | 否 | 广告播放时间（**毫秒**，最小 100）。不写时：视频按视频本身时长播，其余默认 **5000 ms** |

> **优先级**：多个资源包都带 `pis_ads.json` 时，**只有资源包列表里最靠上（优先级最高）的那一份生效**。
> 界面上显示的「已加载 N 个资源包」只是统计有几个包提供了这个文件。

### 素材的建议规格
| 类型 | 建议规格 | 说明 |
|---|---|---|
| 静态图 `png` | **720 × 432**（16:9） | 屏是 16:9 的，非 16:9 会被拉伸 |
| 动图 `gif` | 同上 | 逐帧解码，帧数较高对性能影响较大 |
| 视频 `mp4` | 建议 **320 × 192 / 15 fps** <br>（最大支持640 x 480 30 fps）| H.264，**不要音轨**；不要超过规格最大限制，否则将不会被加载 |

> 如播放视频对性能影响较大，可考虑在模组通用设置内关闭视频播放功能。这样将不会在屏幕上播放视频。

## 示例资源包
可以运行以下Gradle任务生成一个示例资源包：
```bash
./gradlew makeSamplePack
# 输出：build/sample-pack/trdm-pis-sample/
```

## 注意事项

请务必认真确认json内写的文件名是否和真实文件名一致。
