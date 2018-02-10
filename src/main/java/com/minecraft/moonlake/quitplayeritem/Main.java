/*
 * Copyright (C) 2017 The MoonLake Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.minecraft.moonlake.quitplayeritem;

import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class Main extends JavaPlugin implements Listener {

    private Set<String> kickSafe;

    public Main() {
    }

    @Override
    public void onEnable() {
        this.initFolder();
        this.kickSafe = new HashSet<>();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getLogger().info("退出玩家物品 QuitPlayerItem 插件 v" + getDescription().getVersion() + " 成功加载.");
    }

    @Override
    public void onDisable() {
        this.kickSafe.clear();
        this.kickSafe = null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        // 处理玩家退出事件进行检测
        handlerPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        // 处理玩家被 KICK 事件则添加到 safe 集合
        this.kickSafe.add(event.getPlayer().getName());
    }

    private void handlerPlayer(Player player) {
        // 处理玩家的检测
        if(player.hasPermission("moonlake.quitplayeritem.ignore"))
            return;
        // 没有权限则进行检测
        if(kickSafe.contains(player.getName()) && getConfig().getBoolean("kickCancelled", true)) {
            // safe 集合包含玩家则返回不处理
            kickSafe.remove(player.getName());
            return;
        }
        // 没有在安全集合则进行检测
        List<EntityType> checkMobList = getCheckType();
        double radius = getConfig().getDouble("radius", 10d);
        int mobCount = 0, playerCount = 0;
        // 统计玩家附近半径内的怪物数量和玩家数量
        for(Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if(entity instanceof Player && !entity.equals(player))
                playerCount++; // 为玩家并且不是自己则玩家数自加
            else if(entity instanceof LivingEntity && (checkMobList == null || checkMobList.contains(entity.getType())))
                mobCount++; // 如果实体为活着的实体并且待检测怪物列表为null或包含则自加
        }
        if(mobCount == 0 && playerCount == 0)
            // 两个值都为 0 说明玩家附近无任何则不处理
            return;
        // 判断结果选择处理方式: true 为掉落否则为清除
        boolean type = mobCount > 0 && playerCount > 0 || playerCount > 0;
        int value = mobCount > 0 && playerCount > 0 ? getConfig().getInt("mobPlayerDrop", 50)
                : playerCount > 0 ? getConfig().getInt("playerDrop", 50) : getConfig().getInt("mobRemove", 50);

        // 进行处理玩家的背包物品包含装备
        int size = player.getInventory().getSize();
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        int armorSize = armorContents.length;
        ListIterator<ItemStack> contents = player.getInventory().iterator();
        Random random = new Random(System.nanoTime());
        List<Integer> indexList = new ArrayList<>();
        for(int i = 0; i < (size + armorSize) * (value * 0.01); i++) {
            int index = random.nextInt(size + armorSize);
            if(!indexList.contains(index)) indexList.add(index);
        }
        // 处理玩家的背包物品
        while (contents.hasNext()) {
            ItemStack itemStack = contents.next();
            if(itemStack != null && itemStack.getType() != Material.AIR && indexList.contains(contents.nextIndex())) {
                contents.set(null); // 将这个选中的物品设置为空气然后检测如果为 true 那么掉落物品
                if(type) player.getWorld().dropItemNaturally(player.getLocation(), itemStack).setPickupDelay(3 * 20);
            }
        }
        for(int i = armorSize - 1; i >= 0; i--) {
            ItemStack armor = armorContents[i];
            if(armor != null && armor.getType() != Material.AIR && indexList.contains(size + i)) {
                armorContents[i] = null;
                if(type) player.getWorld().dropItemNaturally(player.getLocation(), armor).setPickupDelay(3 * 20);
            }
        }
        player.getInventory().setArmorContents(armorContents);
        player.updateInventory();
    }

    private List<EntityType> getCheckType() {
        // 获取待检测的实体列表
        List<String> typeList = getConfig().getStringList("mobList");
        if(typeList == null || typeList.isEmpty())
            // 返回 null 则检测任何怪物
            return null;
        // 否则获取对应的实体类型
        List<EntityType> resultList = new ArrayList<>();
        for(String type : typeList) {
            EntityType typeObj = EntityType.fromName(type);
            if(typeObj == null) {
                // 未知类型
                getLogger().log(Level.WARNING, "错误: 配置文件检测怪物类型列表未知的类型: " + type);
                continue;
            }
            resultList.add(typeObj);
        }
        return resultList;
    }

    private void initFolder() {
        if(!getDataFolder().exists())
            getDataFolder().mkdirs();
        File config = new File(getDataFolder(), "config.yml");
        if(!config.exists())
            saveDefaultConfig();
    }
}
