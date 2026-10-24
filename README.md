# Dragon Trainer

A terminal client for [Dragons of Mugloar](https://dragonsofmugloar.com), a game where you send a dragon on
missions from a message board, earn gold and score, and buy items from a shop to stay alive.

The client talks to the public Mugloar REST API and keeps a local copy of every game in SQLite.
That lets you close the terminal and resume the same game later.

## Requirements
- Java 21
- Network access to `https://dragonsofmugloar.com`
- Optional: the `sqlite3` command-line tool, if you want to inspect or rebuild the database

Maven does not need to be installed. The project ships with the Maven wrapper (`./mvnw`).

## Setup
The SQLite database is `src/main/resources/data/mugloar.db`. Its path is set in
`src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:sqlite:/Users/fahrettin/development/dragon-trainer/src/main/resources/data/mugloar.db
```

The path is absolute. On another machine, change it to where the project is checked out.

To start from an empty database, rebuild it from the DDL script:

```bash
sqlite3 src/main/resources/data/mugloar.db < src/main/resources/data/mugloar_schema.sql
```

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
| 4 | Check Status | Shows turn, lives, gold, level, score and high score |
| 5 | View Logs | Shows the mission and purchase history of the current game |
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

## Project layout
```
src/main/java/com/akbori/solution/mugloar/
  MugloarCLIRunner.java   terminal menu and all menu actions
  service/                MugloarService: calls to the Mugloar REST API
  dto/                    records for API requests and responses
  model/                  JPA entities stored in SQLite
  repository/             Spring Data repositories
src/main/resources/data/
  mugloar.db              the SQLite database
  mugloar_schema.sql      DDL to rebuild it
```

### Database tables
| Table | Holds |
|---|---|
| `player_status` | Latest lives, gold, level, score, high score and turn per game |
| `cached_ad` | The downloaded message board per game, with a `used` flag for ads already attempted |
| `cached_item` | The shop items per game |
| `mission_log` | Every mission attempt and purchase, with its result |
| `app_config` | Settings, such as `LAST_GAME_ID` for resuming |

## Tests
```bash
./mvnw test
```

| Test class | What it covers |
|---|---|
| `MugloarServiceTest` | API calls against a mocked server, including error responses and ad decoding |
| `MugloarServiceLiveTest` | The same endpoints against the real Mugloar server (needs network; tagged `live`) |
| `MugloarCLIRunnerBuyingTest` | The Buy Item action, with all collaborators mocked |
| `MugloarCLIRunnerGameOverTest` | Actions blocked at 0 lives, and the low-health and game-over warnings |
| `MugloarCLIRunnerSolveTest` | Solve Ad uses the current game's stored board (also after a new game or a resume) and refuses expired ads |
| `MugloarCLIRunnerErrorHandlingTest` | Network, response and database errors are reported without ending the terminal |
| `*RepositoryTest` | Each repository, against a throwaway in-memory SQLite database |
| `DragonTrainerApplicationTests` | The Spring context starts. It sets `mugloar.cli.enabled=false` so the menu doesn't wait for input |

To run one test class:

```bash
./mvnw test -Dtest=MugloarServiceTest
```