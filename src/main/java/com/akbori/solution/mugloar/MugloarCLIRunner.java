package com.akbori.solution.mugloar;

import com.akbori.solution.mugloar.dto.*;
import com.akbori.solution.mugloar.model.*;
import com.akbori.solution.mugloar.repository.*;
import com.akbori.solution.mugloar.service.MugloarService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

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
// On by default; tests set mugloar.cli.enabled=false so the menu loop doesn't block waiting for input
@ConditionalOnProperty(name = "mugloar.cli.enabled", havingValue = "true", matchIfMissing = true)
public class MugloarCLIRunner implements CommandLineRunner {

    private final MugloarService mugloarService;
    private final PlayerStatusRepository playerRepo;
    private final MissionLogRepository logRepo;
    private final AppConfigRepository configRepo;
    private final CachedAdRepository adRepo;
    private final CachedItemRepository marketRepo;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private String currentGameId = null;
    private List<CachedItem> cachedItems = new ArrayList<>();

    /**
     * Wires the API service and the SQLite repositories used by the terminal menu.
     */
    public MugloarCLIRunner(MugloarService mugloarService, PlayerStatusRepository playerRepo,
                            MissionLogRepository logRepo, AppConfigRepository configRepo,
                            CachedAdRepository adRepo, CachedItemRepository marketRepo) {
        this.mugloarService = mugloarService;
        this.playerRepo = playerRepo;
        this.logRepo = logRepo;
        this.configRepo = configRepo;
        this.adRepo = adRepo;
        this.marketRepo = marketRepo;
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
            System.out.print("\nSelect an action [1-10]: ");
            String command = scanner.nextLine().trim();

            if (command.equals("10")) {
                System.out.println("\nSaving application state configuration... Goodbye!");
                System.exit(0);
            }
            handleCommand(command, scanner);
        }
    }

    /**
     * Runs one menu action. Any exception it throws is reported and swallowed,
     * so a network outage or a database error ends only that action, not the whole terminal.
     *
     * @param command The menu number the user typed
     * @param scanner Console input, for actions that ask for an index
     */
    private void handleCommand(String command, Scanner scanner) {
        try {
            switch (command) {
                case "1" -> handleStart();
                case "2" -> handleBoard();
                case "3" -> handleSolve(scanner);
                case "4" -> handleStatus();
                case "5" -> handleHistory();
                case "6" -> handleReputation();
                case "7" -> handleDownloadAds();
                case "8" -> handleMarketBoard();
                case "9" -> handleBuying(scanner);
                default -> System.out.println("❌ Invalid option. Please input a number from 1 to 10.");
            }
        } catch (ResourceAccessException e) {
            System.out.println("🌐 Could not reach the Mugloar server. Check your internet connection and try again.");
        } catch (RestClientException e) {
            System.out.println("❌ The Mugloar server sent a response that could not be read: " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("❌ The action failed: " + e.getMessage());
        }
    }

    /**
     * Prints the action dashboard with the available menu options [1-10].
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
        System.out.println("|  8 | Market           | Show items for sale       |");
        System.out.println("|  9 | Buy Item         | Purchase item by index num|");
        System.out.println("| 10 | Exit Terminal    | Safely shut down framework|");
        System.out.println("+----+------------------+---------------------------+");
    }

    /**
     * Starts a new game via the API, stores the initial player status,
     * and saves the game ID as LAST_GAME_ID so the session can be resumed later.
     */
    private void handleStart() {
        System.out.println("\nConnecting to game server for a new instance...");
        GameStartResponse res = mugloarService.startNewGame();
        // HTTP errors are reported by the service; the current game stays as it was
        if (res == null) return;
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
        if (checkSessionMissing() || checkGameOver()) return;

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

        List<CachedAd> ads = adRepo.findByGameIdOrderByDisplayIndexAsc(currentGameId);

        if (ads.isEmpty()) {
            System.out.println("⚠️ No contracts stored for this game. Run action [7] to download them.");
            return;
        }

        System.out.println("\n+-----+------------+-------+--------+---------+---------------------------------------+---------------------------------------------+");
        System.out.printf("| %-3s | %-10s | %-5s | %-6s | %-7s | %-37s | %-43s |%n", "IDX", "AD ID", "USED?", "GOLD", "EXPIRES", "RISK METRIC", "ADMISSION SPECIFICATION MESSAGE");
        System.out.println("+-----+------------+-------+--------+---------+---------------------------------------+---------------------------------------------+");

        // "IDX (AdId)" labels for the used-ads summary line
        List<String> usedLabels = new ArrayList<>();

        for (int i = 0; i < ads.size(); i++) {
            CachedAd ad = ads.get(i);
            int idx = i + 1; // IDX shown to the user starts from 1
            String riskExplain = mapRiskCategory(ad.getProbability());
            if (ad.isUsed()) usedLabels.add(idx + " (" + ad.getAdId() + ")");
            String usedFlag = ad.isUsed() ? "USED" : "----";
            int remaining = ad.remainingExpiresIn(currentTurn);
            String expires = remaining > 0 ? String.valueOf(remaining) : "EXPIRED";
            String truncatedMsg = ad.getMessage().length() > 40 ? ad.getMessage().substring(0, 37) + "..." : ad.getMessage();

            System.out.printf("| %-3d | %-10s | %-5s | %-6d | %-7s | %-37s | %-43s |%n", idx, ad.getAdId(), usedFlag, ad.getReward(), expires, riskExplain, truncatedMsg);
        }
        System.out.println("+-----+------------+-------+--------+---------+---------------------------------------+---------------------------------------------+");
        System.out.printf("🚫 Used ads in this game: %d%s%n", usedLabels.size(),
                usedLabels.isEmpty() ? "" : " -> " + String.join(", ", usedLabels));
        System.out.println("💡 Strategy: Note down the 'IDX' number, select menu, and enter it to launch your dragon.");
    }

    /**
     * Asks for a board IDX and sends the dragon to solve that ad.
     * Refuses ads already flagged as used or already expired. On a solve, logs the result to mission_log,
     * marks the ad as used in cached_ad and updates the stored player status.
     *
     * @param scanner Console input used to read the ad index
     */
    private void handleSolve(Scanner scanner) {
        if (checkSessionMissing() || checkGameOver()) return;
        // Read the board from the DB for the current game, in the same order View Board shows it,
        // so ads from a previous game are never used and the board works right after a resume
        List<CachedAd> ads = adRepo.findByGameIdOrderByDisplayIndexAsc(currentGameId);
        if (ads.isEmpty()) {
            System.out.println("❌ No contracts stored for this game. Run action [7] to download them.");
            return;
        }

        System.out.printf("\nEnter target ad index number [1-%d]: ", ads.size());
        String input = scanner.nextLine().trim();
        int index;
        try {
            // IDX on the board starts from 1, the list is 0-based
            index = Integer.parseInt(input) - 1;
            if (index < 0 || index >= ads.size()) throw new IndexOutOfBoundsException();
        } catch (Exception e) {
            System.out.println("❌ Invalid number index entry.");
            return;
        }

        CachedAd targetedAd = ads.get(index);

        if (targetedAd.isUsed()) {
            System.out.printf("⚠️ Warning: Ad ID %s has already been used in this game and should not be used a second time.%n", targetedAd.getAdId());
            System.out.println("   Pick a different ad, or run action [7] to download a new board.");
            return;
        }

        // Same countdown View Board shows; the server stops accepting an ad once it reaches 0
        int currentTurn = playerRepo.findById(currentGameId).map(PlayerStatus::getTurn).orElse(0);
        if (targetedAd.remainingExpiresIn(currentTurn) <= 0) {
            System.out.printf("⚠️ Warning: Ad ID %s has expired and can no longer be solved.%n", targetedAd.getAdId());
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

        // Update local profile copy; if no row is stored, create it for this game so the save can't fail on a null ID
        PlayerStatus status = playerRepo.findById(currentGameId).orElseGet(() -> {
            PlayerStatus created = new PlayerStatus();
            created.setGameId(currentGameId);
            return created;
        });
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
        printLivesWarning(res.lives());
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
            System.out.printf("| Dragon Level      | %-29d |%n", status.getLevel());
            System.out.printf("| Dynamic Score     | %-29d |%n", status.getScore());
            System.out.printf("| Top Record Tier   | %-29d |%n", status.getHighScore());
            System.out.println("+-------------------+-------------------------------+");
            printLivesWarning(status.getLives());
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
        if (checkSessionMissing() || checkGameOver()) return;

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
     * Categories run from safest to deadliest. Encrypted ads are decoded by the service,
     * so the probability text is always plain here.
     *
     * @param probability Probability text from the ad, e.g. "Piece of cake"
     * @return Risk label such as "🟢 SAFE", or the raw text in upper case if unknown
     */
    private String mapRiskCategory(String probability) {
        if (probability == null) return "UNKNOWN RISK";
        // Locale.ROOT so a Turkish default locale doesn't turn "i" into "İ" / "I" into "ı"
        return switch (probability.toLowerCase(Locale.ROOT)) {
            case "piece of cake", "sure thing", "walk in the park" -> "🟢 SAFE";
            case "quite likely", "hmmm...." -> "🟡 LIKELY";
            case "gamble" -> "🎲 GAMBLE";
            case "risky", "playing with fire" -> "🟠 RISKY";
            case "rather detrimental" -> "🔴 DANGEROUS";
            case "suicide mission", "impossible" -> "☠️ FATAL";
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

    /**
     * Checks whether the dragon of the current game has no lives left and prints a warning if so.
     * Used by actions that call the server, which rejects every request for a finished game.
     * Read-only actions (board, status, logs, market) stay available.
     *
     * @return true if the stored status shows 0 lives
     */
    private boolean checkGameOver() {
        boolean dead = playerRepo.findById(currentGameId).map(s -> s.getLives() <= 0).orElse(false);
        if (dead) {
            System.out.println("💀 GAME OVER: Your dragon has no lives left. Start a new game via item [1].");
        }
        return dead;
    }

    /**
     * Prints a game-over notice at 0 lives, or a low-health warning at 1 life.
     *
     * @param lives Lives left after the last action
     */
    private void printLivesWarning(int lives) {
        if (lives <= 0) {
            System.out.println("💀 GAME OVER: Your dragon has no lives left. Start a new game via item [1].");
        } else if (lives == 1) {
            System.out.println("🚨 Only 1 life left! Buy a Healing potion via items [8] and [9] before taking another risk.");
        }
    }



    /**
     * Shows the market board for the current game from the cached_item table.
     * The shop list does not change during a game, so it is downloaded from the API
     * and stored only when this game has no items stored yet.
     */
    private void handleMarketBoard() {
        if (checkSessionMissing()) return;

        cachedItems = marketRepo.findByGameIdOrderByDisplayIndexAsc(currentGameId);

        if (cachedItems.isEmpty()) {
            System.out.println("\nDownloading market items...");
            List<ShopItem> downloaded = mugloarService.getMarketItems(currentGameId);
            if (downloaded.isEmpty()) {
                System.out.println("⚠️ No market items available for this game.");
                return;
            }

            List<CachedItem> rows = new ArrayList<>();
            for (int i = 0; i < downloaded.size(); i++) {
                rows.add(new CachedItem(currentGameId, i, downloaded.get(i)));
            }
            marketRepo.saveAll(rows);
            cachedItems = rows;
            System.out.println("✅ Stored " + rows.size() + " market items.");
        }

        System.out.println("\n+-----+------------+---------------------------+--------+");
        System.out.printf("| %-3s | %-10s | %-25s | %-6s |%n", "IDX", "ITEM ID", "NAME", "COST");
        System.out.println("+-----+------------+---------------------------+--------+");

        for (int i = 0; i < cachedItems.size(); i++) {
            CachedItem item = cachedItems.get(i);
            int idx = i + 1; // IDX shown to the user starts from 1

            System.out.printf("| %-3d | %-10s | %-25s | %-6d |%n", idx, item.getItemId(), item.getName(), item.getCost());
        }
        System.out.println("+-----+------------+---------------------------+--------+");

        System.out.println("💡 Strategy: Note down the 'IDX' number, for the shppable item for your dragon");
    }





    /**
     * Asks for a market IDX and buys that item.
     * Refuses items the player cannot afford. On a purchase, logs the result to mission_log
     * and updates the stored player status.
     *
     * @param scanner Console input used to read the item index
     */
    private void handleBuying(Scanner scanner) {
        if (checkSessionMissing() || checkGameOver()) return;
        // Reload so items left over from a previous game are never used
        cachedItems = marketRepo.findByGameIdOrderByDisplayIndexAsc(currentGameId);
        if (cachedItems.isEmpty()) {
            System.out.println("❌ No cached market items available. Run action [8] to open the market first.");
            return;
        }

        System.out.printf("\nEnter target market item index number [1-%d]: ", cachedItems.size());
        String input = scanner.nextLine().trim();
        int index;
        try {
            // IDX on the board starts from 1, the list is 0-based
            index = Integer.parseInt(input) - 1;
            if (index < 0 || index >= cachedItems.size()) throw new IndexOutOfBoundsException();
        } catch (Exception e) {
            System.out.println("❌ Invalid number index entry.");
            return;
        }

        CachedItem targetedItem = cachedItems.get(index);

        // Update local profile copy
        PlayerStatus status = playerRepo.findById(currentGameId).orElse(null);

        if (status == null){
            System.out.println("Your dragon should have some assignment , there is no status for your dragon");
            return;
        }

        if (status.getGold() < targetedItem.getCost()) {
            System.out.printf("⚠️ Warning: Market Item ID %s is so expensive for your budget. You have only %s Gold at the moment%n", targetedItem.getItemId(), status.getGold());
            System.out.println("   Pick a different market item, or wait until you get enough gold.");
            return;
        }

        System.out.printf("🚀 Dealing with the purchase of the Market Item ID: %s...%n", targetedItem.getItemId());

    TradeResponse  res = mugloarService.buyItem(currentGameId, targetedItem.getItemId()) ;
        // HTTP errors are reported by the service; nothing is logged or marked as used
        if (res == null) return;

        String serverMessage ="User Buyed the item " + targetedItem.getName() + " with " + targetedItem.getCost() + " Gold";
        // Log results to DB
        logRepo.save(new MissionLog(currentGameId, targetedItem.getItemId(), targetedItem.getCost(),
                null, res.shoppingSuccess(), serverMessage));



        status.setLives(res.lives());
        status.setGold(res.gold());
        status.setTurn(res.turn());
        status.setLevel(res.level());
        playerRepo.save(status);


        System.out.println("\n+--------------------------------------------------------------------------------------------+");
        System.out.printf("| OUTCOME: %-81s |%n", res.shoppingSuccess() ? "🏆 SUCCESSFUL SHOPPING" : "💀  FAILURE");
        System.out.println("+--------------------------------------------------------------------------------------------+");
        System.out.printf("| Details: %-81s |%n", serverMessage);
        System.out.printf("| Wallet Dynamics: %-25s | Health Remainder: %-25s |%n", res.gold() + " Gold coins", res.lives() + " Lives left");
        System.out.printf("| Dragon Level: %-76s |%n", res.level());
        System.out.println("+--------------------------------------------------------------------------------------------+");
        printLivesWarning(res.lives());
    }

}

