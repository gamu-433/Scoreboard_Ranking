package petoma.me.sc_ranking;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.*;

public class Sc_ranking extends JavaPlugin {
    private List<RankingPair> rankingPairs;
    private int taskId;

    private class RankingPair {
        String baseScoreboard;
        String pasteScoreboard;
        boolean baseScoreboardError = false;
        boolean pasteScoreboardError = false;

        RankingPair(String baseScoreboard, String pasteScoreboard) {
            this.baseScoreboard = baseScoreboard;
            this.pasteScoreboard = pasteScoreboard;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        rankingPairs = new ArrayList<>();
        loadConfig();

        // 10秒ごとにランキング更新
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::updateAllRankings, 0L, 200L);

        getCommand("psc").setExecutor(this);
        getCommand("psc").setTabCompleter(new PscTabCompleter());
        getLogger().info("sc_ranking has been enabled!");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTask(taskId);
        getLogger().info("sc_ranking has been disabled!");
    }

    private void loadConfig() {
        rankingPairs.clear();
        FileConfiguration config = getConfig();

        if (config.contains("base") && config.contains("paste")) {
            String baseScoreboard = config.getString("base");
            String pasteScoreboard = config.getString("paste");
            rankingPairs.add(new RankingPair(baseScoreboard, pasteScoreboard));
        }

        // 複数ペアの設定
        ConfigurationSection pairsSection = config.getConfigurationSection("pairs");
        if (pairsSection != null) {
            for (String key : pairsSection.getKeys(false)) {
                ConfigurationSection pairSection = pairsSection.getConfigurationSection(key);
                if (pairSection != null && pairSection.contains("base") && pairSection.contains("paste")) {
                    String baseScoreboard = pairSection.getString("base");
                    String pasteScoreboard = pairSection.getString("paste");
                    rankingPairs.add(new RankingPair(baseScoreboard, pasteScoreboard));
                }
            }
        }

        // 設定が見つからない場合
        if (rankingPairs.isEmpty()) {
            if (!config.contains("pairs")) {
                config.createSection("pairs");
            }
            ConfigurationSection pairsSection2 = config.getConfigurationSection("pairs");

            ConfigurationSection defaultPair = pairsSection2.createSection("default");
            defaultPair.set("base", "points");
            defaultPair.set("paste", "ranking");

            saveConfig();

            rankingPairs.add(new RankingPair("points", "ranking"));
        }

        // スコアボードの存在チェック
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            Scoreboard scoreboard = manager.getMainScoreboard();

            for (RankingPair pair : rankingPairs) {
                if (scoreboard.getObjective(pair.baseScoreboard) == null) {
                    getLogger().severe("Base scoreboard '" + pair.baseScoreboard + "' does not exist! This pair will not function properly until it is created.");
                    pair.baseScoreboardError = true;
                }

                if (scoreboard.getObjective(pair.pasteScoreboard) == null) {
                    getLogger().severe("Paste scoreboard '" + pair.pasteScoreboard + "' does not exist! This pair will not function properly until it is created.");
                    pair.pasteScoreboardError = true;
                }
            }
        }
    }

    public void updateAllRankings() {
        for (RankingPair pair : rankingPairs) {
            updateRanking(pair);
        }
    }

    public void updateRanking(RankingPair pair) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard scoreboard = manager.getMainScoreboard();

        // ベーススコアボードの取得
        Objective baseObjective = scoreboard.getObjective(pair.baseScoreboard);
        if (baseObjective == null) {
            if (!pair.baseScoreboardError) {
                getLogger().warning("Base scoreboard '" + pair.baseScoreboard + "' not found! Skipping ranking update.");
                pair.baseScoreboardError = true;
            }
            return;
        }

        // ペースト先スコアボードの取得
        Objective pasteObjective = scoreboard.getObjective(pair.pasteScoreboard);
        if (pasteObjective == null) {
            if (!pair.pasteScoreboardError) {
                getLogger().warning("Paste scoreboard '" + pair.pasteScoreboard + "' not found! Skipping ranking update.");
                pair.pasteScoreboardError = true;
            }
            return;
        }

        // 前回のエラーが解決した場合にエラーフラグをリセット
        if (pair.baseScoreboardError) {
            pair.baseScoreboardError = false;
        }
        if (pair.pasteScoreboardError) {
            pair.pasteScoreboardError = false;
        }

        Set<String> baseEntries = new HashSet<>();
        Map<String, Integer> scores = new HashMap<>();

        for (String entry : scoreboard.getEntries()) {
            Score score = baseObjective.getScore(entry);
            if (score.isScoreSet()) {
                baseEntries.add(entry);
                scores.put(entry, score.getScore());
            }
        }

        for (String entry : scoreboard.getEntries()) {
            Score pasteScore = pasteObjective.getScore(entry);
            if (pasteScore.isScoreSet() && !baseEntries.contains(entry)) {
                // baseにないがpasteに残っているプレイヤーをリセット
                scoreboard.resetScores(entry);
                getLogger().info("Cleaned up player " + entry + " from ranking " + pair.pasteScoreboard + " as they no longer exist in base scoreboard " + pair.baseScoreboard);
            }
        }

        List<Map.Entry<String, Integer>> sortedScores = new ArrayList<>(scores.entrySet());
        sortedScores.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        // ランキング更新
        int rank = 1;
        for (Map.Entry<String, Integer> entry : sortedScores) {
            pasteObjective.getScore(entry.getKey()).setScore(rank);
            rank++;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("psc")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("sc_ranking.reload")) {
                    Bukkit.getScheduler().cancelTask(taskId);

                    reloadConfig();
                    loadConfig();

                    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::updateAllRankings, 0L, 200L);

                    sender.sendMessage("§a[sc_ranking] Configuration reloaded successfully.");
                    return true;
                } else {
                    sender.sendMessage("§c[sc_ranking] You don't have permission to use this command.");
                    return true;
                }
            }
            return false;
        }
        return false;
    }

}