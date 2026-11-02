# 屏蔽门端门 —— 实现规格（已确认）

本文件记录端门方块的需求与实现约定，避免重复讨论。以下每一条都已与需求方确认。

## 1. 结构与形态

- 总高 **3 格**：
  - 下 2 格：玻璃门，**可动**；
  - 上 1 格：站台门玻璃的门楣（`psd_top`），**不动**。
- 形态定义：*"用站台门玻璃贴图的 MC 原版门 + 上方 PSD 玻璃门楣"*。
- 外观完全沿用普通 PSD 玻璃那一套模型与贴图；合并后在世界里与合并后的普通玻璃**没有视觉差别**。
- 后续可能为端门单独做一套模型（预留：模型名统一以 `psd_end_gate_` 开头）。

## 2. 方块状态

- 复用 PSD 玻璃的全部状态：`half=lower/upper`、`side=left/right/middle/single`、`facing`；
- 额外增加 `open=true/false`；
- blockstate 采用与 `psd_glass_1.json` 同构的全量变体（288 组），仅 `open=true` 的门格替换为绕铰链旋转 90° 的模型副本。

## 3. 合并

- 用**刷子右键**合并，与普通玻璃同一套合并逻辑；
- 合并后整段共享同一个 `open`（开门时整段玻璃一起动）。

## 4. 开门

- 方式：**旋转**，绕端部竖轴，行为参考 MC 原版门；
- 关门：挡住玩家；开门：让出通道。

## 5. 装饰条与灯

- 端门上**绘制带 slogan 的装饰条，但不绘制人字形（箭头）装饰**；
- 门楣上有灯：开门亮、关门灭；
- 合并后，灯的位置按**整段 x 轴长度居中**。

## 6. 已就位的素材

| 文件 | 说明 |
|---|---|
| `assets/trdm/textures/block/psd_glass_end_frame.png` | 端门玻璃加粗边框 |
| `assets/trdm/textures/block/psd_lamp_on.png` | 门楣灯亮 |
| `assets/trdm/textures/block/psd_lamp_off.png` | 门楣灯灭 |
| `assets/trdm/textures/block/psd_door_top_left_1.png` / `_right_1.png` | 天津导视资源包的上半段滑动门贴图 |
