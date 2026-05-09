# mdt Foundation World Control

这是一个用于 `Mindustry 157` 服务端的依赖插件，目标是把“控制游戏内世界”的能力统一沉淀成一个底层中台，供其他插件或外部服务直接调用。

它专注做这些事：

- 生成单位
- 生成建筑
- 修改单位或建筑队伍
- 给单位添加 Buff、移除 Buff、清空 Buff
- 修改单位和建筑血量
- 修改单位或建筑字段
- 调整核心物资
- 删除建筑
- 强制结束对局

详细使用说明见：

- [USAGE.md](./USAGE.md)

## 构建

```powershell
.\gradlew.bat jar
```

构建完成后会生成：

```text
build/libs/mdt-foundation-world-control.jar
dist/mdt-foundation-world-control.jar
```

## 配置文件

插件首次启动时会自动创建：

```text
config/mods/config/mdt-foundation-world-control/plugin-config.properties
```

## 插件入口

```text
com.mdt.foundation.FoundationWorldControlPlugin
```
