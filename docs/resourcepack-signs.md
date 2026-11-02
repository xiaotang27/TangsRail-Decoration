# 标志牌贴图与预设

TRDM 的指示牌系统复用 **MTR 的自定义标志机制**：模组自己在
`assets/mtr/mtr_custom_resources.json` 里注册了一批 `custom_signs`，指向
`assets/trdm/textures/block/sign/` 下的贴图。

## 更换指示牌图案

最省事的做法：直接用资源包覆盖同名文件。

```
assets/trdm/textures/block/sign/<名字>.png
```

可覆盖的文件（全部在 `assets/trdm/textures/block/sign/`）：

| 文件 | 用途 |
|---|---|
| `arrow.png` | 门楣方向箭头的剪影蒙版 |
| `cross.png` | 禁止上 / 下行标志 |
| `lift_1.png`、`accessible_elevator.png` | 无障碍电梯 |
| `male.png`、`female.png`、`toilets.png` | 卫生间 / 男 / 女 |
| `bupiao.png` | 补票 |
| `wenxun.png` | 问询 |
| `anjian.png` | 安全检查 |
| `train.png`、`train_shiyu.png` | 乘车 / 乘车指引 |
| `customer_service_centre.png` | 客服中心 |
| `lunyishengjiangji.png` | 轮椅升降机 |
| `muyingshi.png` | 母婴室 |
| `station.png`、`thisstn.png`、`stncircle.png`、`headtail_stn.png` | 站名相关的底图与圆圈 |
| `colorline.png`、`barrier_line_1.png`、`barrier_line_2.png` | 色带 / 线路条 |
| `routeline_arrow.png` | 线路图箭头 |
| `transfer_circle_2.png`、`transfer_circle_3.png`、`transfer_label.png` | 换乘标志 |

> 只换图案时不用动 json，覆盖 png 就行。

## 新增 / 修改牌子文字：`custom_signs`

MTR 的 `custom_signs` 条目格式（TRDM 的预设文件用的是同一套字段）：

```json
{
  "custom_signs": {
    "wenxun_2": {
      "texture_id": "trdm:textures/block/sign/wenxun.png",
      "file_texture": false,
      "custom_text": "问询|Enquiry",
      "flip_custom_text": true,
      "small": true
    }
  }
}
```

| 字段 | 说明 |
|---|---|
| `texture_id` | 贴图资源路径（`命名空间:textures/...png`） |
| `file_texture` | `true` = 直接用贴图本身；`false` = 用贴图当图标再叠加文字 |
| `custom_text` | 叠在图标上的文字，`中文|English` |
| `flip_custom_text` | 文字是否左右镜像（牌子两面朝向不同时用） |
| `small` | 小号牌子排版 |

## 两处 json 的关系

| 文件 | 谁读 | 作用 |
|---|---|---|
| `assets/mtr/mtr_custom_resources.json` | **MTR** | 真正把「标志 id → 贴图/文字」注册进游戏 |
| `assets/trdm/sign_presets.json` | **TRDM** | 一份内置预设表，格式与上表完全一致，用于界面里列出可选的指示牌 |

想**新增**一种牌子：把条目写进资源包的 `assets/mtr/mtr_custom_resources.json`。
注意这个文件是**整份替换**的 —— 你的资源包提供了它，模组自带的那批条目就不再生效，
需要的话把模组里那份内容一起抄进去再改。
