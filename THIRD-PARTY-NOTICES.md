# 第三方声明 / Third-Party Notices

TangsRail Decoration（下称「本模组」）的**代码**以 MIT 协议发布（见 `LICENSE`）。
本模组还使用、引用或再分发了下列第三方成果，它们各自的许可与版权属于各自的作者。
本文件会随每个构建产物一起打进 jar 的 `META-INF/`。

---

## 1. Minecraft Transit Railway (MTR)

本模组是 MTR 3.x 的附属模组（addon），在编译期与运行期依赖 MTR，
并且再分发了 MTR 的部分**字体与贴图**用于覆盖/复刻其视觉样式：

- `assets/mtr/font/noto-sans-semibold.ttf`、`assets/mtr/font/noto-serif-cjk-tc-semibold.ttf`
- `assets/mtr/textures/block/sign/exit_1.png`、`assets/mtr/textures/block/sign/escalator.png`

MTR 以 MIT 协议发布：

```
MIT License

Copyright (c) 2022 Jonathan Ho

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 2. Source Han Sans HW SC VF（思源黑体）

`assets/trdm/font/sourcehansanshwsc-vf.ttf` 随本模组一起分发，用于 PIS 与站名牌的中文渲染。

Copyright 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source'.

本字体以 **SIL Open Font License 1.1** 发布（允许随软件捆绑分发，不得单独出售），全文如下：

```
Copyright (c) 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source'.

This Font Software is licensed under the SIL Open Font License, Version 1.1.
This license is copied below, and is also available with a FAQ at:
https://openfontlicense.org


-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```

---

## 3. JCodec（MP4/H.264 解码）

本模组通过 Fabric 的 jar-in-jar 机制内置 `jcodec-0.2.5.jar` 与 `jcodec-javase-0.2.5.jar`
（`META-INF/jars/`），用于 PIS 广告的 mp4 播放。

- 作者：Stanislav Vitvitskyy、Alex Zhukov、Oleksandr Chugai 等
- 上游：https://github.com/jcodec/jcodec
- 许可：FreeBSD（2-clause BSD），见上游 `LICENSE` 与 http://jcodec.org/lic.html

> 待补：发布 Release 前，把上游 `LICENSE` 全文粘到本节下方（上游任一版本均适用）。
> 保守做法是直接从 https://github.com/jcodec/jcodec 复制该文件内容到这里，
> 因为 BSD 许可要求在再分发时保留版权声明与许可全文。

---

## 4. 美术素材与致谢

| 素材 | 位置 | 来源 / 版权 |
|---|---|---|
| 天津地铁风格指示牌图标（exit / escalator / 无障碍电梯 / 卫生间 / 检票机 等 13 个） | `assets/trdm/textures/block/sign/` | 由 **Baiyun_Hiyaku** 提供 |
| PIS 屏幕贴图（`tjpids` / `tc` / `zj` / `0tc` / `adsdef`） | `assets/trdm/textures/block/pis/` | 来自社区资源包 **JCM / TJPIS**（jsblock），版权归原作者 |
| 装饰带中央图文 | `assets/trdm/textures/block/band/band_center.png` | 从参考图提取，原图版权归原权利人 |
| 模组图标、logo、品牌图 | `assets/trdm/icon.png`、`assets/trdm/textures/gui/` | 本项目自制 / 用户提供 |

上述素材**仅用于还原天津轨道交通的视觉样式**，不以素材本身牟利。
如果你是其权利人并希望调整署名方式或要求下架，请通过仓库 Issue 联系。

---

## 5. 开发工具

- **Fabric Loom / Fabric Loader / Fabric API** — Apache-2.0
- **Minecraft** — 本模组不分发任何 Mojang 的原始资源文件，仅在构建期通过 Loom 使用官方 mappings

Minecraft 是 Mojang Studios 的商标，与本项目无关。
