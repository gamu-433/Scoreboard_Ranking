package petoma.me.sc_ranking;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.*;

public class Sc_ranking extends JavaPlugin {
    private String baseScoreboard;
    private String pasteScoreboard;
    private int taskId;
    private boolean baseScoreboardError = false;
    private boolean pasteScoreboardError = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        // 10秒ごとにランキング更新タスクを開始
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::updateRanking, 0L, 200L); // 20ticks = 1秒

        // コマンド登録
        getCommand("psc").setExecutor(this);

        getLogger().info("sc_ranking has been enabled!");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTask(taskId);
        getLogger().info("sc_ranking has been disabled!");
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();

        // デフォルト値を設定
        config.addDefault("base", "points");
        config.addDefault("paste", "ranking");
        config.options().copyDefaults(true);
        saveConfig();

        // 設定を読み込み
        baseScoreboard = config.getString("base");
        pasteScoreboard = config.getString("paste");

        // エラーフラグをリセット
        baseScoreboardError = false;
        pasteScoreboardError = false;

        // スコアボードの存在チェック
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            Scoreboard scoreboard = manager.getMainScoreboard();

            // ベーススコアボードのチェック
            if (scoreboard.getObjective(baseScoreboard) == null) {
                getLogger().severe("Base scoreboard '" + baseScoreboard + "' does not exist! Plugin will not function properly until it is created.");
                baseScoreboardError = true;
            }

            // ペースト先スコアボードのチェック
            if (scoreboard.getObjective(pasteScoreboard) == null) {
                getLogger().severe("Paste scoreboard '" + pasteScoreboard + "' does not exist! Plugin will not function properly until it is created.");
                pasteScoreboardError = true;
            }
        }
    }

    public void updateRanking() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard scoreboard = manager.getMainScoreboard();

        // ベーススコアボードの取得
        Objective baseObjective = scoreboard.getObjective(baseScoreboard);
        if (baseObjective == null) {
            if (!baseScoreboardError) {
                getLogger().warning("Base scoreboard '" + baseScoreboard + "' not found! Skipping ranking update.");
                baseScoreboardError = true;
            }
            return;
        }

        // ペースト先スコアボードの取得
        Objective pasteObjective = scoreboard.getObjective(pasteScoreboard);
        if (pasteObjective == null) {
            if (!pasteScoreboardError) {
                getLogger().warning("Paste scoreboard '" + pasteScoreboard + "' not found! Skipping ranking update.");
                pasteScoreboardError = true;
            }
            return;
        }

        // baseスコアボードのエントリーを収集
        Set<String> baseEntries = new HashSet<>();
        Map<String, Integer> scores = new HashMap<>();

        for (String entry : scoreboard.getEntries()) {
            Score score = baseObjective.getScore(entry);
            if (score.isScoreSet()) {
                baseEntries.add(entry);
                scores.put(entry, score.getScore());
            }
        }

        // pasteスコアボードのエントリーを収集し、baseにないエントリーをクリーンアップ
        for (String entry : scoreboard.getEntries()) {
            Score pasteScore = pasteObjective.getScore(entry);
            if (pasteScore.isScoreSet() && !baseEntries.contains(entry)) {
                // baseにないがpasteに残っているエントリーをリセット
                scoreboard.resetScores(entry);
                getLogger().info("Cleaned up player " + entry + " from ranking as they no longer exist in base scoreboard.");
            }
        }

        // スコアでソート
        List<Map.Entry<String, Integer>> sortedScores = new ArrayList<>(scores.entrySet());
        sortedScores.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        // ランキングを直接更新
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
                    // タスクを一旦キャンセル
                    Bukkit.getScheduler().cancelTask(taskId);

                    // コンフィグを再読み込み
                    reloadConfig();
                    loadConfig();

                    // タスクを再開
                    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::updateRanking, 0L, 200L);

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