# 虚空科技 · Void Technology

面向 Minecraft **1.21.1 / NeoForge 21.1.x** 的 **Applied Energistics 2** 附属。提供以「输入销毁、产物回网」为模型的自动化合成设备。

An Applied Energistics 2 add-on for Minecraft 1.21.1 / NeoForge 21.1.x, built on a *void sink* model: inputs are destroyed, products are returned to the ME network.

---

## 中文说明

设备由两个协同方块组成：**虚空样板供应器**向 ME 网络提供合成样板并回吐产物，**虚空控制器**负责销毁输入材料。只有当供应器网格在线、且相邻存在控制器时，该链路才可用；否则供应器不会向网络上报任何样板，合成终端中也无法选中它，输入不会被消耗。

### 工作机制

1. 合成 CPU 选定样板后，将输入材料交给供应器；
2. 供应器把材料转交给相邻的虚空控制器；
3. 控制器销毁材料并记录数量，随即通知供应器回吐产物；
4. 供应器在下一个服务端 tick 将产物写入 ME 网络；
5. 合成 CPU 收到产物后完成本次步骤的结算。

产物采用延迟一 tick 写入，是因为 AE2 在推送动作完成后才登记「期望产物」；同 tick 写入会被判定为未被等待的物品，导致任务无法结束。产物写入同时不受物品堆叠上限约束，单次订单超过 64 时不会截断。

### 虚空样板供应器

基于 AE2 原生样板供应器，推送方向、记忆卡、产物回收与合成 CPU 联动均保持原有行为。

| 项目 | 规格 |
|---|---|
| 样板槽 | 54（原生 9 个） |
| 样板类型 | 仅接受编码样板，每格 1 个 |
| 升级槽 | 无 |
| 网格连接 | 六面均可接入线缆 |
| 界面尺寸 | 208 × 304 |
| 槽位排布 | 三环分布，半径 40 / 60 / 80，对应 12 / 18 / 24 个槽 |
| 网络状态 | 初始化中 / 未连网络 / 无频道 / 在线 |

### 虚空控制器

销毁端，不持有 ME 网络连接，也不需要线缆。

| 项目 | 规格 |
|---|---|
| 可用条件 | 相邻存在处于「在线」状态的虚空样板供应器 |
| 输入处理 | 按订单销毁，累计销毁计数 |
| 产物处理 | 不读取也不保存产物，仅请求供应器回吐 |
| 界面尺寸 | 176 × 176 |

### 虚空石碑

将结构文件复现为世界建筑。

| 项目 | 规格 |
|---|---|
| 结构格式 | 原版结构文件（1.21.1），默认结构 149 × 48 × 145，130,229 个方块 |
| 成型落点 | 石碑正前方 3 格、左右居中、与结构最底层同高 |
| 预览 | 世界内绘制范围线框，10 秒后自动消失，与成型落点使用同一套计算 |
| 界面尺寸 | 176 × 100 |

---

## English

The system consists of two cooperating blocks: the **Void Pattern Provider**, which offers patterns to the ME network and returns products, and the **Void Controller**, which destroys the incoming materials. The link is usable only when the provider's grid connection is online **and** a controller is adjacent; otherwise no patterns are reported, and no inputs are consumed.

Products are written one tick late on purpose — AE2 registers its expected outputs only after the push call returns. Insertion is not subject to item stack limits, so single orders larger than 64 are transferred in full.

- **Void Pattern Provider** — 54 pattern slots (vanilla: 9), encoded patterns only, no upgrade slots, all six faces accept cables, 208 × 304 GUI with live network state.
- **Void Controller** — destroy-only; requires an adjacent online pattern provider; 176 × 176 GUI with link / task / network state.
- **Void Stele** — previews and reconstructs a structure file in front of itself; previews expire automatically after 10 seconds.

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x (`[21.1,22)`) |
| Applied Energistics 2 | 19.2+ |
| LDLib2 | 2.2.44+ |

Client and server both need the dependencies installed.

---

## 构建 / Building

```bash
./gradlew build          # 产物：build/libs/void_technology-<version>.jar
./gradlew runClient      # 开发客户端
./gradlew runServer      # 专用服务器（首次需在 run/eula.txt 写 eula=true）
```

`libs/` 中的 LDLib2 与 jar-in-jar 依赖（taffy / yoga / kotlin-stdlib）以 `compileOnly` 方式参与编译，运行时由玩家侧的 LDLib2 模组提供，**不会打进产物 jar**。构建前请确认这些文件存在：

```
libs/ldlib2-neoforge-1.21.1-2.2.44-all.jar
libs/ldlib2-deps/taffy-1.1.5.jar
libs/ldlib2-deps/yoga-1.0.0.jar
libs/ldlib2-deps/kotlin-stdlib-2.1.20.jar
```

---

## 项目结构

```
src/main/java/com/voidtechnology/     模组代码
src/main/resources/                   材质、模型、语言、结构文件、模组图标
docs/*.html                           方块 / 界面美术的 HTML 源文件（离线渲染成贴图）
tools/*.py                            渲染与结构提取脚本
libs/                                 编译期依赖（LDLib2 及其 jar-in-jar 依赖）
```

美术与结构相关的工具：

| 脚本 | 作用 |
|---|---|
| `tools/render_gui_html.py` | 渲染控制器界面动画图集 |
| `tools/render_provider_gui.py` | 渲染供应器面板与槽位坐标 JSON |
| `tools/render_stele_art.py` | 渲染石碑方块贴图 |
| `tools/render_stele_gui.py` | 渲染石碑面板 |
| `tools/render_mod_icon.py` | 渲染模组图标（256 / 400 两种尺寸） |
| `tools/extract_building.py` | 从存档按范围提取建筑为结构文件（含方块实体） |
| `tools/litematic_to_structure.py` | Litematica 投影转原版结构文件 |

---

## 已知限制

- 成型结构会覆盖落点范围内的既有方块，建议先用预览确认范围；
- 结构文件仅保存方块与方块实体，不保存实体；
- 默认结构可在 `src/main/resources/data/void_technology/structure/` 中替换，落点偏移会按新结构尺寸自动重算。

---

## 许可 / License

代码与资源版权归作者所有（`All Rights Reserved`）。如需以其他许可证发布，请修改 `gradle.properties` 中的 `mod_license` 并补充 LICENSE 文件。
