package com.akbori.solution.mugloar;

import com.akbori.solution.mugloar.dto.*;
import com.akbori.solution.mugloar.model.*;
import com.akbori.solution.mugloar.repository.AppConfigRepository;
import com.akbori.solution.mugloar.repository.MissionLogRepository;
import com.akbori.solution.mugloar.repository.PlayerStatusRepository;
import com.akbori.solution.mugloar.service.MugloarService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@Component
public class MugloarCLIRunner implements CommandLineRunner {

    private final MugloarService mugloarService;
    private final PlayerStatusRepository playerRepo;
    private final MissionLogRepository logRepo;
    private final AppConfigRepository configRepo;

    private String currentGameId = null;
    private List<TaskResponse> cachedAds = new ArrayList<>();

    public MugloarCLIRunner(MugloarService mugloarService, PlayerStatusRepository playerRepo,
                            MissionLogRepository logRepo, AppConfigRepository configRepo) {
        this.mugloarService = mugloarService;
        this.playerRepo = playerRepo;
        this.logRepo = logRepo;
        this.configRepo = configRepo;
    }

    @Override
    public void run(String... args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=====================================================");
        System.out.println("      DRAGONS OF MUGLOAR PERSISTENT TERMINAL        ");
        System.out.println("=====================================================");

        // --- AUTO RESUME SESSION LOGIC ---
        configRepo.findById("LAST_GAME_ID").ifPresent(config -> {
            currentGameId = config.getConfigValue();
            System.out.println("🔄 Found saved run! Auto-resumed Session ID: " + currentGameId);
            handleStatus();
        });

        if (currentGameId == null) {
            System.out.println("ℹ️ No previous session found. Choose option [1] to start a new adventure.");
        }

        while (true) {
            printGridMenu();
            System.out.print("\nSelect an action [1-6]: ");
            String command = scanner.nextLine().trim();

            switch (command) {
                case "1" -> handleStart();
                case "2" -> handleBoard();
                case "3" -> handleSolve(scanner);
                case "4" -> handleStatus();
                case "5" -> handleHistory();
                case "6" -> {
                    System.out.println("\nSaving application state configuration... Goodbye!");
                    System.exit(0);
                }
                default -> System.out.println("❌ Invalid option. Please input a number from 1 to 6.");
            }
        }
    }

    private void printGridMenu() {
        System.out.println("\n+---------------------------------------------------+");
        System.out.println("|                 ACTION DASHBOARD                  |");
        System.out.println("+----+------------------+---------------------------+");
        System.out.println("|  1 | Start Game       | Initialize a fresh session|");
        System.out.println("|  2 | View Board       | Refresh available jobs    |");
        System.out.println("|  3 | Solve Ad         | Accept job by index num   |");
        System.out.println("|  4 | Check Status     | Read local character data |");
        System.out.println("|  5 | View Logs        | Review attempt statistics |");
        System.out.println("|  6 | Exit Terminal    | Safely shut down framework|");
        System.out.println("+----+------------------+---------------------------+");
    }

    private void handleStart() {
        System.out.println("\nConnecting to game server for a new instance...");
        GameStartResponse res = mugloarService.startNewGame();
        currentGameId = res.gameId();

        // Save Player status
        PlayerStatus status = new PlayerStatus(res.gameId(), res.lives(), res.gold(), res.score(), res.highScore());
        playerRepo.save(status);

        // Save session link to config table
        configRepo.save(new AppConfig("LAST_GAME_ID", currentGameId));

        System.out.println("✅ New run established and recorded!");
        handleStatus();


    }

    private void handleBoard() {
        if (checkSessionMissing()) return;

        System.out.println("\nDownloading active contracts...");
        cachedAds = mugloarService.getMessageBoard(currentGameId);

        if (cachedAds.isEmpty()) {
            System.out.println("⚠️ The board has no current contracts.");
            return;
        }

        System.out.println("\n+-----+--------+---------------------------------------+---------------------------------------------+");
        System.out.printf("| %-3s | %-6s | %-37s | %-43s |%n", "IDX", "GOLD", "RISK METRIC", "ADMISSION SPECIFICATION MESSAGE");
        System.out.println("+-----+--------+---------------------------------------+---------------------------------------------+");

        for (int i = 0; i < cachedAds.size(); i++) {
            TaskResponse ad = cachedAds.get(i);
            String riskExplain = mapRiskCategory(ad.probability());
            String truncatedMsg = ad.message().length() > 40 ? ad.message().substring(0, 37) + "..." : ad.message();

            System.out.printf("| %-3d | %-6d | %-37s | %-43s |%n", i, ad.reward(), riskExplain, truncatedMsg);
        }
        System.out.println("+-----+--------+---------------------------------------+---------------------------------------------+");
        System.out.println("💡 Strategy: Note down the 'IDX' number, select menu, and enter it to launch your dragon.");
    }

    private void handleSolve(Scanner scanner) {
        if (checkSessionMissing()) return;
        if (cachedAds.isEmpty()) {
            System.out.println("❌ No cached assignments available. Run action [2] to scan the board first.");
            return;
        }

        System.out.printf("\nEnter target ad index number [0-%d]: ", cachedAds.size() - 1);
        String input = scanner.nextLine().trim();
        int index;
        try {
            index = Integer.parseInt(input);
            if (index < 0 || index >= cachedAds.size()) throw new IndexOutOfBoundsException();
        } catch (Exception e) {
            System.out.println("❌ Invalid number index entry.");
            return;
        }

        TaskResponse targetedAd = cachedAds.get(index);
        System.out.printf("🚀 Dispatching dragon to resolve Ad ID: %s...%n", targetedAd.adId());

        SolutionResponse res = mugloarService.solveAd(currentGameId, targetedAd.adId());

        // Log results to DB
        logRepo.save(new MissionLog(currentGameId, targetedAd.adId(), targetedAd.reward(),
                mapRiskCategory(targetedAd.probability()), res.success(), res.message()));

        // Update local profile copy
        PlayerStatus status = playerRepo.findById(currentGameId).orElse(new PlayerStatus());
        status.setLives(res.lives());
        status.setGold(res.gold());
        status.setScore(res.score());
        status.setHighscore(res.highScore());
        playerRepo.save(status);

        System.out.println("\n+--------------------------------------------------------------------------------------------+");
        System.out.printf("| OUTCOME: %-81s |%n", res.success() ? "🏆 SUCCESSFUL EXTRACTION" : "💀 CRITICAL FAILURE");
        System.out.println("+--------------------------------------------------------------------------------------------+");
        System.out.printf("| Details: %-81s |%n", res.message());
        System.out.printf("| Wallet Dynamics: %-25s | Health Remainder: %-25s |%n", res.gold() + " Gold coins", res.lives() + " Lives left");
        System.out.println("+--------------------------------------------------------------------------------------------+");
    }

    private void handleStatus() {
        if (checkSessionMissing()) return;

        playerRepo.findById(currentGameId).ifPresentOrElse(status -> {
            System.out.println("\n+---------------------------------------------------+");
            System.out.println("|        LOCAL METRIC VENEER (PULLED FROM SQLITE)   |");
            System.out.println("+-------------------+-------------------------------+");
            System.out.printf("| Active Game ID    | %-29s |%n", status.getGameId());
            System.out.printf("| Remaining Vitality| %-29s |%n", status.getLives() + " ❤️ " + (status.getLives() == 0 ? "(DEAD)" : "(ACTIVE)"));
            System.out.printf("| Treasure Reserve  | %-29s |%n", status.getGold() + " 🪙 coins");
            System.out.printf("| Dynamic Score     | %-29d |%n", status.getScore());
            System.out.printf("| Top Record Tier   | %-29d |%n", status.getHighscore());
            System.out.println("+-------------------+-------------------------------+");
        }, () -> System.out.println("⚠️ Warning: Data record entry matching this ID was not found in SQLite."));
    }

    private void handleHistory() {
        if (checkSessionMissing()) return;

        List<MissionLog> logs = logRepo.findByGameIdOrderByIdDesc(currentGameId);
        if (logs.isEmpty()) {
            System.out.println("ℹ️ No assignments have been solved yet in this session.");
            return;
        }

        System.out.println("\n+----------+----------------------+--------+-----------+---------------------------------------------+");
        System.out.printf("| %-8s | %-20s | %-6s | %-9s | %-43s |%n", "TIME", "MISSION AD ID", "GOLD", "RESULT", "SERVER VERDICT LOG");
        System.out.println("+----------+----------------------+--------+-----------+---------------------------------------------+");

        for (MissionLog log : logs) {
            String outcome = log.isSuccess() ? "SUCCESS" : "FAIL";
            String truncMsg = log.getServerMessage().length() > 40 ? log.getServerMessage().substring(0, 40) + "..." : log.getServerMessage();
            System.out.printf("| %-8s | %-20s | %-6d | %-9s | %-43s |%n",
                    log.getTimestamp().toLocalTime().toString().substring(0, 8),
                    log.getAdId(), log.getReward(), outcome, truncMsg);
        }
        System.out.println("+----------+----------------------+--------+-----------+---------------------------------------------+");
    }
    private String mapRiskCategory(String probability) {
        if (probability == null) return "UNKNOWN RISK";
        return switch (probability.toLowerCase()) {
            case "sure thing", "piece of cake", "walk in the park" -> "🟢 SAFE";
            case "quite likely", "hmmm" -> "🟡 MODERATE";
            case "risky", "rather detrimental" -> "🟠 DANGEROUS";
            case "suicide mission" -> "🔴 FATAL THREAT";
            default -> "⚪ " + probability.toUpperCase();
        };
    }
    private boolean checkSessionMissing() {
        if (currentGameId == null) {
            System.out.println("❌ Access Denied: Initialize or restore an active session via item [1] first.");
            return true;
        }
        return false;
    }
}

