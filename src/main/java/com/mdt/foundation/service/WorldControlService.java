package com.mdt.foundation.service;

import arc.Core;
import arc.struct.Seq;
import arc.util.Log;
import com.mdt.foundation.api.ActionRequest;
import com.mdt.foundation.api.ActionResult;
import com.mdt.foundation.api.FoundationWorldControlApi;
import com.mdt.foundation.config.PluginConfiguration;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

public final class WorldControlService implements FoundationWorldControlApi {
    private final PluginConfiguration configuration;

    public WorldControlService(PluginConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public ActionResult execute(ActionRequest request) {
        final String operation = normalizeOperation(request.getOperation());
        if (operation.isEmpty()) {
            return ActionResult.failure(operation, "operation is required");
        }

        if (!Vars.state.isGame()) {
            return ActionResult.failure(operation, "game world is not running");
        }

        if (Core.app == null) {
            return executeInternal(operation, request.getParameters());
        }

        final AtomicReference<ActionResult> reference = new AtomicReference<ActionResult>();
        final CountDownLatch latch = new CountDownLatch(1);
        Core.app.post(new Runnable() {
            @Override
            public void run() {
                try {
                    reference.set(executeInternal(operation, request.getParameters()));
                } catch (Exception exception) {
                    reference.set(ActionResult.failure(operation, exception.getMessage()));
                    Log.err(exception);
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (!latch.await(configuration.getActionTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                return ActionResult.failure(operation, "action execution timed out");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return ActionResult.failure(operation, "action execution interrupted");
        }

        ActionResult result = reference.get();
        return result == null ? ActionResult.failure(operation, "action execution returned no result") : result;
    }

    private ActionResult executeInternal(String operation, Map<String, String> parameters) {
        if ("spawn-unit".equals(operation)) {
            return spawnUnit(parameters);
        }
        if ("spawn-block".equals(operation) || "spawn-building".equals(operation)) {
            return spawnBlock(parameters);
        }
        if ("set-unit-team".equals(operation)) {
            return setUnitTeam(parameters);
        }
        if ("set-building-team".equals(operation) || "set-block-team".equals(operation)) {
            return setBuildingTeam(parameters);
        }
        if ("unit-apply-status".equals(operation) || "unit-add-buff".equals(operation)) {
            return unitApplyStatus(parameters);
        }
        if ("unit-remove-status".equals(operation) || "unit-remove-buff".equals(operation)) {
            return unitRemoveStatus(parameters);
        }
        if ("unit-clear-status".equals(operation) || "unit-clear-buffs".equals(operation)) {
            return unitClearStatus(parameters);
        }
        if ("set-unit-health".equals(operation)) {
            return setUnitHealth(parameters);
        }
        if ("set-building-health".equals(operation) || "set-block-health".equals(operation)) {
            return setBuildingHealth(parameters);
        }
        if ("set-unit-property".equals(operation) || "patch-unit".equals(operation)) {
            return setUnitProperty(parameters);
        }
        if ("set-building-property".equals(operation) || "patch-building".equals(operation)) {
            return setBuildingProperty(parameters);
        }
        if ("core-item-adjust".equals(operation)) {
            return adjustCoreItems(parameters);
        }
        if ("core-item-set".equals(operation)) {
            return setCoreItems(parameters);
        }
        if ("remove-building".equals(operation) || "remove-block".equals(operation)) {
            return removeBuilding(parameters);
        }
        if ("end-game".equals(operation) || "finish-game".equals(operation)) {
            return endGame(parameters);
        }
        return ActionResult.failure(operation, "unsupported operation: " + operation);
    }

    private ActionResult spawnUnit(Map<String, String> parameters) {
        UnitType unitType = requireUnitType(parameters, "unit");
        if (unitType == null) {
            return ActionResult.failure("spawn-unit", "unit is required");
        }

        Team team = findTeam(parameters.get("team"), Vars.state.rules.defaultTeam);
        float x = requireFloat(parameters, "x");
        float y = requireFloat(parameters, "y");
        int amount = Math.max(1, getInt(parameters, "amount", 1));
        float spacing = getFloat(parameters, "spacing", 6f);
        float rotation = getFloat(parameters, "rotation", 90f);
        Float health = getOptionalFloat(parameters, "health");
        Float maxHealth = getOptionalFloat(parameters, "maxHealth");
        Float shield = getOptionalFloat(parameters, "shield");
        String statuses = parameters.get("status");
        float statusDuration = getFloat(parameters, "statusDuration", 60f);

        List<Integer> ids = new ArrayList<Integer>();
        for (int index = 0; index < amount; index++) {
            Unit unit = unitType.create(team);
            unit.set(x + index * spacing, y);
            unit.rotation(rotation);
            unit.add();

            if (maxHealth != null && maxHealth.floatValue() > 0f) {
                unit.maxHealth(maxHealth.floatValue());
            }
            if (health != null) {
                unit.health(Math.max(0f, health.floatValue()));
            }
            if (shield != null) {
                unit.shield(shield.floatValue());
            }
            applyStatuses(unit, statuses, statusDuration);
            ids.add(Integer.valueOf(unit.id));
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("unit", unitType.name);
        data.put("team", team.name);
        data.put("count", Integer.valueOf(ids.size()));
        data.put("unitIds", ids);
        return ActionResult.success("spawn-unit", "spawned " + ids.size() + " unit(s)", data);
    }

    private ActionResult spawnBlock(Map<String, String> parameters) {
        Block block = requireBlock(parameters, "block");
        if (block == null) {
            return ActionResult.failure("spawn-block", "block is required");
        }

        Team team = findTeam(parameters.get("team"), Vars.state.rules.defaultTeam);
        int x = requireInt(parameters, "x");
        int y = requireInt(parameters, "y");
        int width = Math.max(1, getInt(parameters, "width", 1));
        int height = Math.max(1, getInt(parameters, "height", 1));
        int rotation = getInt(parameters, "rotation", 0);
        Float health = getOptionalFloat(parameters, "health");
        Float maxHealth = getOptionalFloat(parameters, "maxHealth");

        Seq<Tile> placed = new Seq<Tile>();
        for (int[] center : expandCenters(x, y, width, height, block.size)) {
            Tile tile = Vars.world.tile(center[0], center[1]);
            if (tile == null) {
                continue;
            }
            if (!mindustry.world.Build.validPlace(block, team, center[0], center[1], rotation, false, false)) {
                continue;
            }

            Call.setTile(tile, block, team, rotation);
            Tile placedTile = Vars.world.tile(center[0], center[1]);
            if (placedTile != null && placedTile.build != null) {
                if (maxHealth != null && maxHealth.floatValue() > 0f) {
                    placedTile.build.maxHealth(maxHealth.floatValue());
                }
                if (health != null) {
                    placedTile.build.health(Math.max(0f, health.floatValue()));
                }
                placed.add(placedTile);
            }
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("block", block.name);
        data.put("team", team.name);
        data.put("placed", Integer.valueOf(placed.size));
        data.put("blockSize", Integer.valueOf(block.size));
        return ActionResult.success("spawn-block", "placed " + placed.size + " block(s)", data);
    }

    private ActionResult setUnitTeam(Map<String, String> parameters) {
        Unit unit = findUnit(parameters);
        if (unit == null) {
            return ActionResult.failure("set-unit-team", "target unit not found");
        }
        Team team = findTeam(parameters.get("team"), unit.team);
        unit.team(team);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("unitId", Integer.valueOf(unit.id));
        data.put("team", team.name);
        return ActionResult.success("set-unit-team", "unit team updated", data);
    }

    private ActionResult setBuildingTeam(Map<String, String> parameters) {
        Tile tile = requireTile(parameters);
        if (tile == null || tile.build == null) {
            return ActionResult.failure("set-building-team", "target building not found");
        }

        Team team = findTeam(parameters.get("team"), tile.team());
        Call.setTile(tile, tile.block(), team, tile.build.rotation);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("x", Integer.valueOf(tile.x));
        data.put("y", Integer.valueOf(tile.y));
        data.put("team", team.name);
        return ActionResult.success("set-building-team", "building team updated", data);
    }

    private ActionResult unitApplyStatus(Map<String, String> parameters) {
        Unit unit = findUnit(parameters);
        if (unit == null) {
            return ActionResult.failure("unit-apply-status", "target unit not found");
        }

        String effectName = requireString(parameters, "status");
        StatusEffect effect = Vars.content.statusEffect(effectName);
        if (effect == null) {
            return ActionResult.failure("unit-apply-status", "status not found: " + effectName);
        }

        float duration = getFloat(parameters, "duration", 60f);
        unit.apply(effect, duration);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("unitId", Integer.valueOf(unit.id));
        data.put("status", effect.name);
        data.put("duration", Float.valueOf(duration));
        return ActionResult.success("unit-apply-status", "status applied", data);
    }

    private ActionResult unitRemoveStatus(Map<String, String> parameters) {
        Unit unit = findUnit(parameters);
        if (unit == null) {
            return ActionResult.failure("unit-remove-status", "target unit not found");
        }

        String effectName = requireString(parameters, "status");
        StatusEffect effect = Vars.content.statusEffect(effectName);
        if (effect == null) {
            return ActionResult.failure("unit-remove-status", "status not found: " + effectName);
        }

        unit.unapply(effect);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("unitId", Integer.valueOf(unit.id));
        data.put("status", effect.name);
        return ActionResult.success("unit-remove-status", "status removed", data);
    }

    private ActionResult unitClearStatus(Map<String, String> parameters) {
        Unit unit = findUnit(parameters);
        if (unit == null) {
            return ActionResult.failure("unit-clear-status", "target unit not found");
        }

        unit.clearStatuses();

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("unitId", Integer.valueOf(unit.id));
        return ActionResult.success("unit-clear-status", "all statuses cleared", data);
    }

    private ActionResult setUnitHealth(Map<String, String> parameters) {
        Unit unit = findUnit(parameters);
        if (unit == null) {
            return ActionResult.failure("set-unit-health", "target unit not found");
        }

        Float maxHealth = getOptionalFloat(parameters, "maxHealth");
        if (maxHealth != null && maxHealth.floatValue() > 0f) {
            unit.maxHealth(maxHealth.floatValue());
        }

        float health = requireFloat(parameters, "health");
        unit.health(Math.max(0f, health));

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("unitId", Integer.valueOf(unit.id));
        data.put("health", Float.valueOf(unit.health));
        data.put("maxHealth", Float.valueOf(unit.maxHealth));
        return ActionResult.success("set-unit-health", "unit health updated", data);
    }

    private ActionResult setBuildingHealth(Map<String, String> parameters) {
        Tile tile = requireTile(parameters);
        if (tile == null || tile.build == null) {
            return ActionResult.failure("set-building-health", "target building not found");
        }

        Float maxHealth = getOptionalFloat(parameters, "maxHealth");
        if (maxHealth != null && maxHealth.floatValue() > 0f) {
            tile.build.maxHealth(maxHealth.floatValue());
        }

        float health = requireFloat(parameters, "health");
        tile.build.health(Math.max(0f, health));

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("x", Integer.valueOf(tile.x));
        data.put("y", Integer.valueOf(tile.y));
        data.put("health", Float.valueOf(tile.build.health));
        data.put("maxHealth", Float.valueOf(tile.build.maxHealth));
        return ActionResult.success("set-building-health", "building health updated", data);
    }

    private ActionResult setUnitProperty(Map<String, String> parameters) {
        Unit unit = findUnit(parameters);
        if (unit == null) {
            return ActionResult.failure("set-unit-property", "target unit not found");
        }

        String fieldName = requireString(parameters, "field");
        String value = requireString(parameters, "value");
        if (!writeField(unit, fieldName, value)) {
            return ActionResult.failure("set-unit-property", "failed to write field: " + fieldName);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("unitId", Integer.valueOf(unit.id));
        data.put("field", fieldName);
        data.put("value", value);
        return ActionResult.success("set-unit-property", "unit property updated", data);
    }

    private ActionResult setBuildingProperty(Map<String, String> parameters) {
        Tile tile = requireTile(parameters);
        if (tile == null || tile.build == null) {
            return ActionResult.failure("set-building-property", "target building not found");
        }

        String fieldName = requireString(parameters, "field");
        String value = requireString(parameters, "value");
        if (!writeField(tile.build, fieldName, value)) {
            return ActionResult.failure("set-building-property", "failed to write field: " + fieldName);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("x", Integer.valueOf(tile.x));
        data.put("y", Integer.valueOf(tile.y));
        data.put("field", fieldName);
        data.put("value", value);
        return ActionResult.success("set-building-property", "building property updated", data);
    }

    private ActionResult adjustCoreItems(Map<String, String> parameters) {
        CoreBuild core = findCore(parameters);
        if (core == null) {
            return ActionResult.failure("core-item-adjust", "core not found");
        }

        Item item = requireItem(parameters, "item");
        if (item == null) {
            return ActionResult.failure("core-item-adjust", "item is required");
        }

        int amount = requireInt(parameters, "amount");
        int current = core.items.get(item);
        int next = Math.max(0, current + amount);
        core.items.set(item, next);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("team", core.team.name);
        data.put("item", item.name);
        data.put("before", Integer.valueOf(current));
        data.put("after", Integer.valueOf(next));
        return ActionResult.success("core-item-adjust", "core item amount adjusted", data);
    }

    private ActionResult setCoreItems(Map<String, String> parameters) {
        CoreBuild core = findCore(parameters);
        if (core == null) {
            return ActionResult.failure("core-item-set", "core not found");
        }

        Item item = requireItem(parameters, "item");
        if (item == null) {
            return ActionResult.failure("core-item-set", "item is required");
        }

        int amount = Math.max(0, requireInt(parameters, "amount"));
        core.items.set(item, amount);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("team", core.team.name);
        data.put("item", item.name);
        data.put("amount", Integer.valueOf(amount));
        return ActionResult.success("core-item-set", "core item amount set", data);
    }

    private ActionResult removeBuilding(Map<String, String> parameters) {
        Tile tile = requireTile(parameters);
        if (tile == null || tile.block() == Blocks.air) {
            return ActionResult.failure("remove-building", "target building not found");
        }
        tile.removeNet();

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("x", Integer.valueOf(tile.x));
        data.put("y", Integer.valueOf(tile.y));
        return ActionResult.success("remove-building", "building removed", data);
    }

    private ActionResult endGame(Map<String, String> parameters) {
        Team winner = findTeam(parameters.get("winnerTeam"), Vars.state.rules.defaultTeam);
        Vars.state.gameOver = true;
        Vars.state.won = winner == Vars.state.rules.defaultTeam;
        arc.Events.fire(new GameOverEvent(winner));

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("winnerTeam", winner.name);
        return ActionResult.success("end-game", "game finished", data);
    }

    private Unit findUnit(Map<String, String> parameters) {
        Integer unitId = getOptionalInt(parameters, "unitId");
        if (unitId != null) {
            for (Unit unit : Groups.unit) {
                if (unit.id == unitId.intValue()) {
                    return unit;
                }
            }
        }

        Integer x = getOptionalInt(parameters, "x");
        Integer y = getOptionalInt(parameters, "y");
        if (x == null || y == null) {
            return null;
        }

        String typeName = parameters.get("unit");
        UnitType type = typeName == null || typeName.trim().isEmpty() ? null : Vars.content.unit(typeName.trim());
        Integer teamId = getOptionalInt(parameters, "teamId");
        Team team = parameters.containsKey("team") ? findTeam(parameters.get("team"), null) : (teamId == null ? null : Team.get(teamId.intValue()));
        float radius = getFloat(parameters, "radius", 24f);

        Unit closest = null;
        float closestDistance = Float.MAX_VALUE;
        for (Unit unit : Groups.unit) {
            if (type != null && unit.type != type) {
                continue;
            }
            if (team != null && unit.team != team) {
                continue;
            }
            float distance = unit.dst(x.intValue(), y.intValue());
            if (distance <= radius && distance < closestDistance) {
                closestDistance = distance;
                closest = unit;
            }
        }
        return closest;
    }

    private Tile requireTile(Map<String, String> parameters) {
        Integer x = getOptionalInt(parameters, "x");
        Integer y = getOptionalInt(parameters, "y");
        if (x == null || y == null) {
            return null;
        }
        return Vars.world.tile(x.intValue(), y.intValue());
    }

    private CoreBuild findCore(Map<String, String> parameters) {
        Team team = findTeam(parameters.get("team"), Vars.state.rules.defaultTeam);
        if (team != null && team.core() != null) {
            return team.core();
        }

        Tile tile = requireTile(parameters);
        if (tile != null && tile.build instanceof CoreBuild) {
            return (CoreBuild) tile.build;
        }
        return null;
    }

    private void applyStatuses(Unit unit, String statuses, float duration) {
        if (statuses == null || statuses.trim().isEmpty()) {
            return;
        }
        for (String raw : statuses.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            StatusEffect effect = Vars.content.statusEffect(name);
            if (effect != null) {
                unit.apply(effect, duration);
            }
        }
    }

    private List<int[]> expandCenters(int centerX, int centerY, int width, int height, int blockSize) {
        List<int[]> positions = new ArrayList<int[]>();
        int step = Math.max(1, blockSize);
        int startX = centerX - (width - 1) / 2;
        int startY = centerY - (height - 1) / 2;
        int endX = startX + width - 1;
        int endY = startY + height - 1;

        for (int tileX = startX; tileX <= endX; tileX += step) {
            for (int tileY = startY; tileY <= endY; tileY += step) {
                positions.add(new int[]{tileX, tileY});
            }
        }
        return positions;
    }

    private Block requireBlock(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        return value == null || value.trim().isEmpty() ? null : Vars.content.block(value.trim());
    }

    private UnitType requireUnitType(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        return value == null || value.trim().isEmpty() ? null : Vars.content.unit(value.trim());
    }

    private Item requireItem(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        return value == null || value.trim().isEmpty() ? null : Vars.content.item(value.trim());
    }

    private Team findTeam(String rawValue, Team fallback) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return fallback;
        }
        String value = rawValue.trim();
        if (isInteger(value)) {
            return Team.get(Integer.parseInt(value));
        }
        for (Team team : Team.all) {
            if (team != null && (team.name.equalsIgnoreCase(value) || team.localized().equalsIgnoreCase(value))) {
                return team;
            }
        }
        return fallback;
    }

    private boolean writeField(Object target, String fieldName, String rawValue) {
        try {
            Field field = findField(target.getClass(), fieldName);
            if (field == null) {
                return false;
            }
            field.setAccessible(true);
            field.set(target, castValue(field.getType(), rawValue));
            return true;
        } catch (Exception exception) {
            Log.err(exception);
            return false;
        }
    }

    private Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private Object castValue(Class<?> type, String rawValue) {
        if (type == String.class) {
            return rawValue;
        }
        if (type == int.class || type == Integer.class) {
            return Integer.valueOf(Integer.parseInt(rawValue));
        }
        if (type == float.class || type == Float.class) {
            return Float.valueOf(Float.parseFloat(rawValue));
        }
        if (type == double.class || type == Double.class) {
            return Double.valueOf(Double.parseDouble(rawValue));
        }
        if (type == long.class || type == Long.class) {
            return Long.valueOf(Long.parseLong(rawValue));
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.valueOf(Boolean.parseBoolean(rawValue));
        }
        if (type == Team.class) {
            return findTeam(rawValue, Team.derelict);
        }
        if (UnlockableContent.class.isAssignableFrom(type)) {
            if (type == Block.class) {
                return Vars.content.block(rawValue);
            }
            if (type == UnitType.class) {
                return Vars.content.unit(rawValue);
            }
            if (type == Item.class) {
                return Vars.content.item(rawValue);
            }
            if (type == StatusEffect.class) {
                return Vars.content.statusEffect(rawValue);
            }
        }
        return rawValue;
    }

    private String normalizeOperation(String operation) {
        return operation == null ? "" : operation.trim().toLowerCase(Locale.ROOT);
    }

    private String requireString(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }

    private int requireInt(Map<String, String> parameters, String key) {
        String value = requireString(parameters, key);
        return Integer.parseInt(value);
    }

    private float requireFloat(Map<String, String> parameters, String key) {
        String value = requireString(parameters, key);
        return Float.parseFloat(value);
    }

    private int getInt(Map<String, String> parameters, String key, int fallback) {
        String value = parameters.get(key);
        return value == null || !isInteger(value.trim()) ? fallback : Integer.parseInt(value.trim());
    }

    private Integer getOptionalInt(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        return value == null || !isInteger(value.trim()) ? null : Integer.valueOf(Integer.parseInt(value.trim()));
    }

    private float getFloat(Map<String, String> parameters, String key, float fallback) {
        String value = parameters.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Float getOptionalFloat(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Float.valueOf(Float.parseFloat(value.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isInteger(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) {
            return false;
        }
        for (int index = start; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
