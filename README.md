# Dragon Trainer

A terminal client for [Dragons of Mugloar](https://dragonsofmugloar.com), a game where you send a dragon on
missions from a message board, earn gold and score, and buy items from a shop to stay alive.

The client talks to the public Mugloar REST API and keeps a local copy of every game in SQLite.
That lets you close the terminal and resume the same game later.

## Requirements
- Java 21
- Network access to `https://dragonsofmugloar.com`
- The `sqlite3` command-line tool, to create the database the first time

Maven does not need to be installed. The project ships with the Maven wrapper (`./mvnw`).

## Setup
Your games are stored in a local SQLite database, `data/mugloar.db`. It is not part of the repository
(`data/*.db` is in `.gitignore`), so each checkout creates its own from the DDL script:

```bash
sqlite3 data/mugloar.db < src/main/resources/data/mugloar_schema.sql
```

The path is set in `src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:sqlite:${MUGLOAR_DB_PATH:data/mugloar.db}
```

`data/mugloar.db` is relative to the folder the app is started from, which is the project root when you
use `./mvnw spring-boot:run`. To use a database somewhere else, for example when running a built jar
from another folder, set `MUGLOAR_DB_PATH`:

```bash
MUGLOAR_DB_PATH=/path/to/mugloar.db ./mvnw spring-boot:run
```

If the database file is missing, SQLite creates an empty one without tables, and the first action fails
with "no such table". Run the `sqlite3` command above to fix it.

## Running
```bash
./mvnw spring-boot:run
```

On start, the client resumes the last game it played, if there is one (`LAST_GAME_ID` in `app_config`).
Otherwise, choose option `1` to start a new game.

## Menu
| # | Action | What it does |
|---|---|---|
| 1 | Start Game | Starts a new game on the server and makes it the current game |
| 2 | View Board | Shows the stored ads, their reward, turns left and risk category |
| 3 | Solve Ad | Sends the dragon on the ad you pick by its `IDX` |
| 4 | Check Status | Shows turn, lives, gold, level, score and high score. The server always reports a high score of 0, so the high score shown is your best score across all stored games |
| 5 | View Logs | Shows the history of the current game, with date and time. The TYPE column tells missions from purchases, and GOLD shows gold earned (`+73`) or spent (`-50`); a failed mission or purchase shows `0` |
| 6 | Reputation | Shows your standing with the people, the state and the underworld (costs a turn) |
| 7 | Download Ads | Downloads a new message board from the server |
| 8 | Market | Shows the shop items, and downloads them the first time for each game |
| 9 | Buy Item | Buys the shop item you pick by its `IDX` |
| 10 | Exit Terminal | Quits the client |

### A typical turn
1. **7, Download Ads**, to get a new board.
2. **3, Solve Ad**, and pick a 🟢 SAFE ad with a good reward.
3. **4, Check Status**. When lives get low, use **8, Market** and **9, Buy Item** to buy a Healing potion.

### Risk categories
Each ad's probability text is shown as a risk category, from safest to deadliest:

| Category | Probability text from the server |
|---|---|
| 🟢 SAFE | Piece of cake, Sure thing, Walk in the park |
| 🟡 LIKELY | Quite likely, Hmmm.... |
| 🎲 GAMBLE | Gamble |
| 🟠 RISKY | Risky, Playing with fire |
| 🔴 DANGEROUS | Rather detrimental |
| ☠️ FATAL | Suicide mission, Impossible |

Some ads arrive encrypted, in Base64 or ROT13. The client decodes them when it downloads the board,
so they show and solve like any other ad.

### Level colors
Check Status and the purchase result show the dragon's level as `🏅 Level N`, colored from yellow to green:

| Level | Color |
|---|---|
| 0–1 | yellow |
| 2 | yellow-lime |
| 3 | lime |
| 4 | light green |
| 5 and up | green |

The colors are ANSI 256-color codes. If your terminal shows them as raw text, turn them off with the
standard `NO_COLOR` variable: `NO_COLOR=1 ./mvnw spring-boot:run`.

### Game over and warnings
- At **1 life**, a warning suggests buying a Healing potion. It appears after solving, after buying and in Check Status.
- At **0 lives**, the game is over. Solve Ad, Reputation, Download Ads and Buy Item are blocked, because the server
  rejects every request for a finished game. Board, Status, Logs and Market still show the stored data.
  Start a new game with option `1`.

### When something goes wrong
A failed action prints a message and returns you to the menu. The terminal never quits on an error.

| Problem | What you see |
|---|---|
| No internet, a timeout or DNS failure | 🌐 Could not reach the Mugloar server. Check your internet connection and try again. |
| The server answers with an error status (400, 404, 410, 5xx) | A message naming the action and the status, e.g. ❌ Mugloar server error (503) while starting a new game. |
| The server's response can't be read | ❌ The Mugloar server sent a response that could not be read: … |
| Any other failure, such as a database error | ❌ The action failed: … |

If starting a new game fails, the current game stays selected.

Option `10` quits, and so does closing the input (Ctrl+D, or the end of a piped script); both exit cleanly.
The client doesn't start a web server, so it never needs port 8080. Two copies can run at once, but they share
`data/mugloar.db`, so don't play the same game in both.

## Project layout
```
src/main/java/com/akbori/solution/mugloar/
  MugloarCLIRunner.java   terminal menu and all menu actions
  service/                MugloarService: calls to the Mugloar REST API
  dto/                    records for API requests and responses
  model/                  JPA entities stored in SQLite
  repository/             Spring Data repositories
src/main/resources/data/
  mugloar_schema.sql      DDL to create the database
data/
  mugloar.db              your local SQLite database (not in git)
```

### Database tables
| Table | Holds |
|---|---|
| `player_status` | Latest lives, gold, level, score, high score and turn per game |
| `cached_ad` | The downloaded message board per game, with a `used` flag for ads already attempted |
| `cached_item` | The shop items per game |
| `mission_log` | Every mission attempt and purchase, with its result. `entry_type` is `MISSION` or `PURCHASE`; for a purchase, `ad_id` holds the item ID and `reward` the gold spent |
| `app_config` | Settings, such as `LAST_GAME_ID` for resuming |

## Branch flow
```
feature/<name>  ->  develop  ->  release/BGBNK_<yyyyMMdd>_<NNN>     e.g. release/BGBNK_20261024_001
```
Feature branches are merged only into `develop`, and `develop` only into a release branch. A release
branch name has a real date and a counter from `001` to `999`.

- **Merge from the web:** Actions → **Merge branch** → **Run workflow**, then enter the source and target
  branch. A release branch that doesn't exist yet is created from `main`. The merged code is tested
  (without the live tests) before it's pushed, so a conflict or a failing test changes nothing.
- **Pull requests:** the **Branch rules** check fails a pull request that breaks the flow. Make it a
  required status check for `develop` and `release/*` to block such merges.

The rules live in `.github/scripts/check-merge-rule.sh`, used by both workflows.

## Tests
```bash
./mvnw test
```

| Test class | What it covers |
|---|---|
| `MugloarServiceTest` | API calls against a mocked server, including error responses and ad decoding |
| `MugloarServiceLiveTest` | The same endpoints against the real Mugloar server (needs network; tagged `live`) |
| `MugloarCLIRunnerStartAndReputationTest` | Start Game saves the new game and `LAST_GAME_ID`; Reputation prints the report and counts a turn |
| `MugloarCLIRunnerDownloadAdsTest` | Download Ads replaces the stored board, keeps used flags and stamps the download turn |
| `MugloarCLIRunnerBoardTest` | View Board shows USED and EXPIRED ads, risk labels and shortened messages in an aligned table |
| `MugloarCLIRunnerRiskCategoryTest` | Every probability text maps to the right risk category |
| `MugloarCLIRunnerMarketTest` | Market downloads the shop once per game, then reads it from the database |
| `MugloarCLIRunnerBuyingTest` | The Buy Item action, with all collaborators mocked |
| `MugloarCLIRunnerGameOverTest` | Actions blocked at 0 lives, and the low-health and game-over warnings |
| `MugloarCLIRunnerLevelColorTest` | The level color for each level, from yellow at 1 to green at 5 and above |
| `MugloarCLIRunnerHistoryTest` | View Logs shows missions and purchases apart, with gold earned or spent |
| `MugloarCLIRunnerSolveTest` | Solve Ad uses the current game's stored board (also after a new game or a resume), refuses expired ads, and recreates a missing player status |
| `MugloarCLIRunnerErrorHandlingTest` | Network, response and database errors are reported without ending the terminal |
| `*RepositoryTest` | Each repository, against a throwaway in-memory SQLite database |
| `DragonTrainerApplicationTests` | The Spring context starts. It sets `mugloar.cli.enabled=false` so the menu doesn't wait for input, and uses an in-memory database |

To run one test class:

```bash
./mvnw test -Dtest=MugloarServiceTest
```