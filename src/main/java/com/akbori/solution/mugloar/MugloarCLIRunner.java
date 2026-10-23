package com.akbori.solution.mugloar;

import com.akbori.solution.mugloar.dto.*;
import com.akbori.solution.mugloar.model.*;
import com.akbori.solution.mugloar.repository.AppConfigRepository;
import com.akbori.solution.mugloar.repository.CachedAdRepository;
import com.akbori.solution.mugloar.repository.MissionLogRepository;
import com.akbori.solution.mugloar.repository.PlayerStatusRepository;
import com.akbori.solution.mugloar.service.MugloarService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MugloarCLIRunner implements CommandLineRunner {

    private final MugloarService mugloarService;
    private final PlayerStatusRepository playerRepo;
    private final MissionLogRepository logRepo;
    private final AppConfigRepository configRepo;
    private final CachedAdRepository adRepo;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private String currentGameId = null;
    private List<CachedAd> cachedAds = new ArrayList<>();

    /**
     * Wires the API service and the SQLite repositories used by the terminal menu.
     */
    public MugloarCLIRunner(MugloarService mugloarService, PlayerStatusRepository playerRepo,
                            MissionLogRepository logRepo, AppConfigRepository configRepo,
                            CachedAdRepository adRepo) {
        this.mugloarService = mugloarService;
        this.playerRepo = playerRepo;
        this.logRepo = logRepo;
        this.configRepo = configRepo;
        this.adRepo = adRepo;
    }

    /**
     * Entry point of the CLI.
     * Auto-resumes the last saved game (LAST_GAME_ID in app_config) if there is one,
     * then loops on the action menu until the user chooses to exit.
     */
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
            System.out.print("\nSelect an action [1-8]: ");
            String command = scanner.nextLine().trim();

            switch (command) {
                case "1" -> handleStart();
                case "2" -> handleBoard();
                case "3" -> handleSolve(scanner);
                case "4" -> handleStatus();
                case "5" -> handleHistory();
                case "6" -> handleReputation();
                case "7" -> handleDownloadAds();
                case "8" -> {
                    System.out.println("\nSaving application state configuration... Goodbye!");
                    System.exit(0);
                }
                default -> System.out.println("❌ Invalid option. Please input a number from 1 to 8.");
            }
        }
    }

    /**
     * Prints the action dashboard with the available menu options [1-8].
     */
    private void printGridMenu() {
        System.out.println("\n+---------------------------------------------------+");
        System.out.println("|                 ACTION DASHBOARD                  |");
        System.out.println("+----+------------------+---------------------------+");
        System.out.println("|  1 | Start Game       | Initialize a fresh session|");
        System.out.println("|  2 | View Board       | Show stored jobs          |");
        System.out.println("|  3 | Solve Ad         | Accept job by index num   |");
        System.out.println("|  4 | Check Status     | Read local character data |");
        System.out.println("|  5 | View Logs        | Review attempt statistics |");
        System.out.println("|  6 | Reputation       | Investigate standing      |");
        System.out.println("|  7 | Download Ads     | Fetch new jobs from server|");
        System.out.println("|  8 | Exit Terminal    | Safely shut down framework|");
        System.out.println("+----+------------------+---------------------------+");
    }

    /**
     * Starts a new game via the API, stores the initial player status,
     * and saves the game ID as LAST_GAME_ID so the session can be resumed later.
     */
    private void handleStart() {
        System.out.println("\nConnecting to game server for a new instance...");
        GameStartResponse res = mugloarService.startNewGame();
        currentGameId = res.gameId();

        // Save Player status
        PlayerStatus status = new PlayerStatus(res.gameId(), res.lives(), res.gold(), res.score(), res.highScore(), res.turn());
        playerRepo.save(status);

        // Save session link to config table
        configRepo.save(new AppConfig("LAST_GAME_ID", currentGameId));

        System.out.println("✅ New run established and recorded!");
        handleStatus();


    }


    /**
     * Downloads a fresh list of ads from the API and replaces this game's ads in cached_ad.
     * Each ad's board position becomes its display index and the current turn is stored as
     * fetchedTurn, so the EXPIRES countdown starts from now. Ads that were already used keep
     * their used flag if the server lists them again. If the download fails or returns nothing,
     * the stored ads are left untouched. Shows the new board afterwards.
     */
    private void handleDownloadAds() {
        if (checkSessionMissing()) return;

        System.out.println("\nDownloading active contracts...");
        List<AdItem> downloaded = mugloarService.getMessageBoard(currentGameId);
        if (downloaded.isEmpty()) {
            System.out.println("⚠️ No contracts downloaded; the stored board was kept.");
            return;
        }

        int currentTurn = playerRepo.findById(currentGameId).map(PlayerStatus::getTurn).orElse(0);

        // Remember which ads were already used before the old rows are replaced
        Set<String> usedAdIds = adRepo.findByGameIdOrderByDisplayIndexAsc(currentGameId).stream()
                .filter(CachedAd::isUsed)
                .map(CachedAd::getAdId)
                .collect(Collectors.toSet());

        List<CachedAd> rows = new ArrayList<>();
        for (int i = 0; i < downloaded.size(); i++) {
            AdItem ad = downloaded.get(i);
            CachedAd row = new CachedAd(currentGameId, i, ad, mapRiskCategory(ad.probability()), currentTurn);
            row.setUsed(usedAdIds.contains(ad.adId()));
            rows.add(row);
        }

        adRepo.deleteByGameId(currentGameId);
        adRepo.saveAll(rows);
        System.out.println("✅ Stored " + rows.size() + " contracts.");

        handleBoard();
    }

    /**
     * Shows the message board for the current game from the cached_ad table.
     * Ads are only read from the database; use action [7] to download a new list from the API.
     * Ads flagged as used in the DB are marked USED and listed as "IDX (AdId)" at the end.
     * The EXPIRES column is expiresIn minus the turns played since the board was downloaded
     * (current turn from player_status), shown as EXPIRED once it reaches 0.
     */
    private void handleBoard() {
        if (checkSessionMissing()) return;

        // Current turn from player_status; expiresIn is counted down from the turn the board was downloaded
        int currentTurn = playerRepo.findById(currentGameId).map(PlayerStatus::getTurn).orElse(0);

        cachedAds = adRepo.findByGameIdOrderByDisplayIndexAsc(currentGameId);

        if (cachedAds.isEmpty()) {
            System.out.println("⚠️ No contracts stored for this game. Run action [7] to download them.");
            return;
        }

        System.out.println("\n+-----+-------+--------+---------+---------------------------------------+---------------------------------------------+");
        System.out.printf("| %-3s | %-5s | %-6s | %-7s | %-37s | %-43s |%n", "IDX", "USED?", "GOLD", "EXPIRES", "RISK METRIC", "ADMISSION SPECIFICATION MESSAGE");
        System.out.println("+-----+-------+--------+---------+---------------------------------------+---------------------------------------------+");

        // "IDX (AdId)" labels for the used-ads summary line
        List<String> usedLabels = new ArrayList<>();

        for (int i = 0; i < cachedAds.size(); i++) {
            CachedAd ad = cachedAds.get(i);
            String riskExplain = mapRiskCategory(ad.getProbability());
            if (ad.isUsed()) usedLabels.add(i + " (" + ad.getAdId() + ")");
            String usedFlag = ad.isUsed() ? "USED" : "----";
            int remaining = ad.remainingExpiresIn(currentTurn);
            String expires = remaining > 0 ? String.valueOf(remaining) : "EXPIRED";
            String truncatedMsg = ad.getMessage().length() > 40 ? ad.getMessage().substring(0, 37) + "..." : ad.getMessage();

            System.out.printf("| %-3d | %-5s | %-6d | %-7s | %-37s | %-43s |%n", i, usedFlag, ad.getReward(), expires, riskExplain, truncatedMsg);
        }
        System.out.println("+-----+-------+--------+---------+---------------------------------------+---------------------------------------------+");
        System.out.printf("🚫 Used ads in this game: %d%s%n", usedLabels.size(),
                usedLabels.isEmpty() ? "" : " -> " + String.join(", ", usedLabels));
        System.out.println("💡 Strategy: Note down the 'IDX' number, select menu, and enter it to launch your dragon.");
    }

    /**
     * Asks for a board IDX and sends the dragon to solve that ad.
     * Refuses ads already flagged as used. On a solve, logs the result to mission_log,
     * marks the ad as used in cached_ad and updates the stored player status.
     *
     * @param scanner Console input used to read the ad index
     */
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

        CachedAd targetedAd = cachedAds.get(index);

        if (targetedAd.isUsed()) {
            System.out.printf("⚠️ Warning: Ad ID %s has already been used in this game and should not be used a second time.%n", targetedAd.getAdId());
            System.out.println("   Pick a different ad, or run action [7] to download a new board.");
            return;
        }

        System.out.printf("🚀 Dispatching dragon to resolve Ad ID: %s...%n", targetedAd.getAdId());

        SolutionResponse res = mugloarService.solveAd(currentGameId, targetedAd.getAdId());
        // HTTP errors are reported by the service; nothing is logged or marked as used
        if (res == null) return;

        // Log results to DB
        logRepo.save(new MissionLog(currentGameId, targetedAd.getAdId(), targetedAd.getReward(),
                mapRiskCategory(targetedAd.getProbability()), res.success(), res.message()));

        // Flag the ad as used in the cached board
        targetedAd.setUsed(true);
        adRepo.save(targetedAd);

        // Update local profile copy
        PlayerStatus status = playerRepo.findById(currentGameId).orElse(new PlayerStatus());
        status.setLives(res.lives());
        status.setGold(res.gold());
        status.setScore(res.score());
        status.setHighScore(res.highScore());
        status.setTurn(res.turn());
        playerRepo.save(status);

        System.out.printf("⚠️ Ad ID %s is now marked as used and should not be used a second time.%n", targetedAd.getAdId());

        System.out.println("\n+--------------------------------------------------------------------------------------------+");
        System.out.printf("| OUTCOME: %-81s |%n", res.success() ? "🏆 SUCCESSFUL EXTRACTION" : "💀 CRITICAL FAILURE");
        System.out.println("+--------------------------------------------------------------------------------------------+");
        System.out.printf("| Details: %-81s |%n", res.message());
        System.out.printf("| Wallet Dynamics: %-25s | Health Remainder: %-25s |%n", res.gold() + " Gold coins", res.lives() + " Lives left");
        System.out.println("+--------------------------------------------------------------------------------------------+");
    }

    /**
     * Prints the current game's player status (turn, lives, gold, score, high score) from SQLite.
     */
    private void handleStatus() {
        if (checkSessionMissing()) return;

        playerRepo.findById(currentGameId).ifPresentOrElse(status -> {
            System.out.println("\n+---------------------------------------------------+");
            System.out.println("|        LOCAL METRIC VENEER (PULLED FROM SQLITE)   |");
            System.out.println("+-------------------+-------------------------------+");
            System.out.printf("| Turn in the game  | %29s  |%n", status.getTurn());
            System.out.printf("| Active Game ID    | %-29s |%n", status.getGameId());
            System.out.printf("| Remaining Vitality| %-29s |%n", status.getLives() + " ❤️ " + (status.getLives() == 0 ? "(DEAD)" : "(ACTIVE)"));
            System.out.printf("| Treasure Reserve  | %-29s |%n", status.getGold() + " 🪙 coins");
            System.out.printf("| Dynamic Score     | %-29d |%n", status.getScore());
            System.out.printf("| Top Record Tier   | %-29d |%n", status.getHighScore());
            System.out.println("+-------------------+-------------------------------+");
        }, () -> System.out.println("⚠️ Warning: Data record entry matching this ID was not found in SQLite."));
    }

    /**
     * Prints the mission log of the current game, newest attempt first.
     */
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
                    TIME_FORMAT.format(Instant.ofEpochMilli(log.getTimestamp())),
                    log.getAdId(), log.getReward(), outcome, truncMsg);
        }
        System.out.println("+----------+----------------------+--------+-----------+---------------------------------------------+");
    }

    /**
     * Investigates the dragon's reputation via the API and prints the standing
     * with the people, the state and the underworld.
     * On success the stored player status turn count is increased by 1.
     * HTTP errors are reported by the service, so nothing is printed here when the call fails.
     */
    private void handleReputation() {
        if (checkSessionMissing()) return;

        System.out.println("\nInvestigating reputation...");
        ReputationResponse rep = mugloarService.investigateReputation(currentGameId);
        if (rep == null) return;

        // Investigating costs a turn, but the response has no turn field, so bump it locally
        playerRepo.findById(currentGameId).ifPresent(status -> {
            status.setTurn(status.getTurn() + 1);
            playerRepo.save(status);
        });

        System.out.println("\n+---------------------------------------------------+");
        System.out.println("|                REPUTATION REPORT                 |");
        System.out.println("+-------------------+-------------------------------+");
        System.out.printf("| People            | %-29s |%n", rep.people());
        System.out.printf("| State             | %-29s |%n", rep.state());
        System.out.printf("| Underworld        | %-29s |%n", rep.underworld());
        System.out.println("+-------------------+-------------------------------+");
    }

    /**
     * Maps the API's probability text to a colored risk label.
     *
     * @param probability Probability text from the ad, e.g. "Piece of cake"
     * @return Risk label such as "🟢 SAFE", or the raw text in upper case if unknown
     */
    private String mapRiskCategory(String probability) {
        if (probability == null) return "UNKNOWN RISK";
        // Locale.ROOT so a Turkish default locale doesn't turn "i" into "İ" / "I" into "ı"
        return switch (probability.toLowerCase(Locale.ROOT)) {
            case "sure thing", "piece of cake", "walk in the park" -> "🟢 SAFE";
            case "quite likely", "hmmm", "hmmm...." -> "🟡 MODERATE";
            case "gamble" -> "🎲 GAMBLE";
            case "risky", "rather detrimental" -> "🟠 DANGEROUS";
            case "suicide mission" -> "🔴 FATAL THREAT";
            default -> "⚪ " + probability.toUpperCase(Locale.ROOT);
        };
    }

    /**
     * Checks whether a game session is active and prints a warning if not.
     *
     * @return true if there is no current game ID
     */
    private boolean checkSessionMissing() {
        if (currentGameId == null) {
            System.out.println("❌ Access Denied: Initialize or restore an active session via item [1] first.");
            return true;
        }
        return false;
    }
}

