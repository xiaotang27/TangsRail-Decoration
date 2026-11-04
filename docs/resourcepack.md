# 资源包总览与可覆盖清单

TRDM 的所有外观都是标准 Minecraft 资源，**都能用资源包覆盖**，不需要改模组。

## 资源包结构

```
your-pack/
├── pack.mcmeta
└── assets/
    ├── trdm/                    ← TRDM 自己的资源
    │   ├── lang/*.json          ← 翻译（物品名、界面文字）
    │   ├── textures/block/…     ← 见下表
    │   ├── models/…             ← 方块模型（进阶）
    │   ├── font/…               ← 内置字体
    │   ├── sign_presets.json    ← 指示牌预设
    │   └── pis_ads.json         ← PIS 广告清单（资源包里新建）
    └── mtr/                     ← MTR 的资源（TRDM 也读其中的 custom_signs）
        └── mtr_custom_resources.json
```

`pack.mcmeta` 示例：

```json
{
  "pack": {
    "pack_format": 15,
    "description": "我的 TRDM 资源包"
  }
}
```

> 1.20.1 的 `pack_format` 是 **15**。

## 可覆盖的贴图（实际文件名）

### 屏蔽门 / 端门 — `assets/trdm/textures/block/`

| 文件 | 用途 |
|---|---|
| `psd_door_top_left_1.png` / `psd_door_top_right_1.png` | 门扇上半段贴图 |
| `psd_glass_end_frame.png` | 端门玻璃加粗边框 |
| `psd_lamp_on.png` / `psd_lamp_off.png` | 门楣灯亮 / 灭（素材已就位，当前门楣灯改用 MTR 的 `light_on/off`） |
| `psd_metal.png` / `model_metal.png` / `model_black.png` | 门框 / 支柱等通用材质 |

### 玻璃装饰带 — `assets/trdm/textures/block/band/`

| 文件 | 用途 |
|---|---|
| `band_center.png` | 装饰带正中的「文字 + logo」图（透明底） |

### 指示牌 / 标志 — `assets/trdm/textures/block/sign/`

`arrow.png`、`barrier_line_1.png`、`barrier_line_2.png`、`colorline.png`、`cross.png`、
`station.png`、`thisstn.png`、`stncircle.png`、`headtail_stn.png`、`routeline_arrow.png`、
`transfer_circle_2.png`、`transfer_circle_3.png`、`transfer_label.png`、`lift_1.png`、
`accessible_elevator.png`、`male.png`、`female.png`、`toilets.png`、`anjian.png`、
`bupiao.png`、`wenxun.png`、`train.png`、`train_shiyu.png`、`customer_service_centre.png`、
`lunyishengjiangji.png`、`muyingshi.png`

### PIS — `assets/trdm/textures/block/pis/`

`tjpids.png`、`tc.png`、`zj.png`、`0tc.png`、`adsdef.png`（广告底图 / 版面）

### 字体 — `assets/trdm/font/`

`sourcehansanshwsc-vf.ttf`（思源黑体，OFL 授权）。只想换字体时覆盖它即可。

## TRDM 自己覆盖掉的 MTR 文件

模组 jar 里 `assets/mtr/` 下只有这几项，**都不涉及 PSD 玻璃**：

| 文件 | 作用 |
|---|---|
| `assets/trdm/font/noto-sans-semibold.ttf`、`noto-serif-cjk-tc-semibold.ttf` | 换掉「使用TRDM字体」打开时 **MTR 原版牌子**用的字体（TRDM 自己的牌子不受影响） |
| `assets/mtr/mtr_custom_resources.json` | 给 MTR 注册 TRDM 的指示牌（`custom_signs`） |
| `assets/mtr/textures/block/sign/escalator.png`、`exit_1.png` | 两张标志牌贴图 |

如果资源包想让 MTR 恢复原样，把对应文件用原版内容覆盖回去即可。
注意 MTR 的 `mtr_custom_resources.json` 是**整份替换**的：
你的资源包里如果有这个文件，TRDM 注册的指示牌就不见了（需要时把它们一并抄进去）。

## 应用与重载

1. 把包（文件夹或 zip）放进 `.minecraft/resourcepacks/`；
2. 游戏里「选项 → 资源包」启用；
3. 改了内容后按 `F3 + T` 重载资源；PIS 广告可以在屏幕设置里点「重新扫描」。

## 分页文档

- [PIS 广告资源包](resourcepack-pis-ads.md) —— 图片 / 动图 / 视频广告
- [标志牌贴图与预设](resourcepack-signs.md) —— 指示牌内容与预设文件
