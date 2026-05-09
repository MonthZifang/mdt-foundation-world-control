# 基垫物插件使用说明

## 定位

这个插件是一个“世界控制依赖插件”。

它自己不强调玩法，而是给其他插件、外部程序、脚本服务提供一个统一的控制入口，用来直接操作 Mindustry 对局里的单位、建筑、核心和胜负流程。

调用方式支持两类：

- Java 插件内直接调用
- HTTP 外部调用

## 核心能力

- 生成单位
- 生成建筑
- 修改单位队伍
- 修改建筑队伍
- 给单位添加 Buff
- 移除单位 Buff
- 清空单位所有 Buff
- 修改单位血量和最大血量
- 修改建筑血量和最大血量
- 修改单位字段
- 修改建筑字段
- 增减核心物资
- 直接设置核心物资
- 删除建筑
- 强制结束游戏并指定胜利方

## Java 调用

```java
import com.mdt.foundation.FoundationWorldControlPlugin;
import com.mdt.foundation.api.ActionRequest;
import com.mdt.foundation.api.ActionResult;
import com.mdt.foundation.api.FoundationWorldControlApi;

import java.util.LinkedHashMap;
import java.util.Map;

FoundationWorldControlApi api = FoundationWorldControlPlugin.getApi();

Map<String, String> params = new LinkedHashMap<String, String>();
params.put("unit", "toxopid");
params.put("x", "120");
params.put("y", "80");
params.put("amount", "3");
params.put("team", "sharded");

ActionResult result = api.execute(ActionRequest.of("spawn-unit", params));
```

## HTTP 调用

默认接口：

- `GET /api/v1/health`
- `GET /api/v1/world/execute`
- `POST /api/v1/world/execute`

默认地址示例：

```text
http://127.0.0.1:7788/api/v1/world/execute
```

如果开启令牌校验，可在请求头传：

```text
X-Api-Token: your-token
```

也可在查询参数传：

```text
token=your-token
```

## 通用请求格式

所有动作都以 `operation` 指定动作名，其他字段作为参数传入。

`GET` 示例：

```text
/api/v1/world/execute?operation=spawn-unit&unit=toxopid&x=120&y=80&amount=2&team=sharded
```

`POST` 示例：

```text
operation=core-item-adjust&team=sharded&item=copper&amount=-500
```

## 返回格式

统一返回 JSON：

```json
{
  "success": true,
  "operation": "spawn-unit",
  "message": "spawned 2 unit(s)",
  "data": {
    "unit": "toxopid",
    "team": "sharded",
    "count": 2,
    "unitIds": [25, 26]
  }
}
```

主要字段说明：

- `success`: 是否执行成功
- `operation`: 实际执行的动作名
- `message`: 简短执行结果
- `data`: 返回数据

## 动作一览

### 1. `spawn-unit`

用途：生成单位。

必传：

- `unit`: 单位名，例如 `dagger`、`toxopid`
- `x`: 世界坐标 X
- `y`: 世界坐标 Y

可选：

- `amount`: 生成数量，默认 `1`
- `team`: 队伍名或队伍 id，默认玩家默认队伍
- `spacing`: 多单位横向间距，默认 `6`
- `rotation`: 朝向，默认 `90`
- `health`: 生成后血量
- `maxHealth`: 生成后最大血量
- `shield`: 生成后护盾
- `status`: 逗号分隔 Buff 名，例如 `overclock,boss`
- `statusDuration`: Buff 持续时间，默认 `60`

返回：

- `unit`
- `team`
- `count`
- `unitIds`

### 2. `spawn-block`

用途：生成建筑。

必传：

- `block`: 建筑名，例如 `duo`、`core-nucleus`
- `x`: 中心区域 X，使用地块坐标
- `y`: 中心区域 Y，使用地块坐标

可选：

- `team`: 队伍名或队伍 id
- `width`: 生成区域宽度，默认 `1`
- `height`: 生成区域高度，默认 `1`
- `rotation`: 建筑朝向，默认 `0`
- `health`: 生成后血量
- `maxHealth`: 生成后最大血量

区域规则：

- `width=1 height=1` 时，只尝试生成一个
- `width=3 height=3` 时，会按区域铺开
- 如果建筑本身是大建筑，例如 `3x3`
- 且生成区域只有 `1x1` 或 `3x3`
- 最终通常只会生成一个
- 如果区域比建筑本身更大，插件会按建筑尺寸步进铺开

返回：

- `block`
- `team`
- `placed`
- `blockSize`

### 3. `set-unit-team`

用途：修改单位队伍。

定位单位方式二选一：

- `unitId`
- `x` `y` 再配合可选 `radius`、`unit`、`team`

必传：

- `team`: 目标队伍

可选：

- `unitId`
- `x`
- `y`
- `radius`
- `unit`

返回：

- `unitId`
- `team`

### 4. `set-building-team`

用途：修改建筑队伍。

必传：

- `x`: 建筑中心地块 X
- `y`: 建筑中心地块 Y
- `team`: 目标队伍

返回：

- `x`
- `y`
- `team`

### 5. `unit-apply-status`

用途：给单位加 Buff。

必传：

- 单位定位参数
- `status`

可选：

- `duration`，默认 `60`

返回：

- `unitId`
- `status`
- `duration`

### 6. `unit-remove-status`

用途：移除单位某个 Buff。

必传：

- 单位定位参数
- `status`

返回：

- `unitId`
- `status`

### 7. `unit-clear-status`

用途：清空单位全部 Buff。

必传：

- 单位定位参数

返回：

- `unitId`

### 8. `set-unit-health`

用途：修改单位血量。

必传：

- 单位定位参数
- `health`

可选：

- `maxHealth`

返回：

- `unitId`
- `health`
- `maxHealth`

### 9. `set-building-health`

用途：修改建筑血量。

必传：

- `x`
- `y`
- `health`

可选：

- `maxHealth`

返回：

- `x`
- `y`
- `health`
- `maxHealth`

### 10. `set-unit-property`

用途：通用修改单位字段。

必传：

- 单位定位参数
- `field`
- `value`

适合场景：

- 改单位某个数值字段
- 做非常规强化
- 给某个单位不合常理的属性

说明：

- 这是通用入口
- 它会尝试直接写入单位对象字段
- 推荐优先用于你明确知道字段名的场景

### 11. `set-building-property`

用途：通用修改建筑字段。

必传：

- `x`
- `y`
- `field`
- `value`

适合场景：

- 给建筑设置不合常理的属性
- 直接改某个建筑运行参数

### 12. `core-item-adjust`

用途：增减核心物资，允许削减核心里的东西。

必传：

- `team` 或核心坐标
- `item`
- `amount`

说明：

- `amount` 为正数时增加
- `amount` 为负数时减少
- 最低不会低于 `0`

示例：

```text
operation=core-item-adjust&team=sharded&item=copper&amount=-2000
```

### 13. `core-item-set`

用途：直接设置核心某种物资数量。

必传：

- `team` 或核心坐标
- `item`
- `amount`

### 14. `remove-building`

用途：删除某个建筑。

必传：

- `x`
- `y`

### 15. `end-game`

用途：强制结束游戏，并指定胜利队伍。

可选：

- `winnerTeam`

说明：

- 不传时默认按 `defaultTeam` 处理
- 可用于强制胜利
- 也可用于强制失败或强制结算

## 单位定位参数说明

以下动作都会用到“单位定位参数”：

- `set-unit-team`
- `unit-apply-status`
- `unit-remove-status`
- `unit-clear-status`
- `set-unit-health`
- `set-unit-property`

定位方式优先级：

- 先用 `unitId`
- 如果没有 `unitId`，再用 `x + y`

配合筛选：

- `radius`: 搜索半径，默认 `24`
- `unit`: 限定单位类型
- `team`: 限定搜索队伍

## 常见示例

### 生成 10 个单位

```text
operation=spawn-unit&unit=flare&x=100&y=60&amount=10&team=blue
```

### 生成一片 3x3 炮塔

```text
operation=spawn-block&block=duo&x=40&y=40&width=3&height=3&team=sharded
```

### 给某单位加 Boss Buff

```text
operation=unit-apply-status&unitId=25&status=boss&duration=600
```

### 把单位改到敌方队伍

```text
operation=set-unit-team&unitId=25&team=crux
```

### 给建筑超高血量

```text
operation=set-building-health&x=50&y=50&health=500000&maxHealth=500000
```

### 扣掉核心里的铜

```text
operation=core-item-adjust&team=sharded&item=copper&amount=-5000
```

### 强制让敌方获胜

```text
operation=end-game&winnerTeam=crux
```

## 服务端命令

- `fwc-status`
- `fwc-exec <operation> [key=value...]`

示例：

```text
fwc-exec spawn-unit unit=toxopid x=120 y=80 amount=2 team=crux
```

## 配置文件

路径：

```text
config/mods/config/mdt-foundation-world-control/plugin-config.properties
```

默认配置：

```properties
api.enabled=true
api.host=127.0.0.1
api.port=7788
api.requireToken=false
api.token=change-me
action.timeoutMillis=5000
```

## 设计建议

推荐把这个插件当作你的底层控制依赖。

上层插件只负责：

- 判断什么时候调用
- 组织业务参数
- 写你自己的玩法逻辑

这个插件负责：

- 真正对世界做修改
- 统一返回结果
- 统一暴露外部接口
- 保证调用尽量稳定
