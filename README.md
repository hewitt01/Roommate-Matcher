# RoomMatch AI

An AI roommate-compatibility agent (EECS3311 course project, Stage 2). It builds a lifestyle
profile through a conversation, ranks compatible roommates from a housing platform, and explains
each match. It has a graphical interface (GUI) and a command-line interface (CLI) over the same
backend.

## What it does

| Feature | What you can do |
|---|---|
| Profile conversation | The AI asks about your lifestyle and builds your profile from your answers; you can correct anything it got wrong |
| Edit profile | Change budget, location and move-in date (validated; nothing is saved if any value is invalid) |
| Ranked matches | Candidates ranked by compatibility. Each pair gets a score in both directions; the ranking uses the lower one |
| Hard filters | Limit candidates by city, rent range and move-in window |
| Match explanation | Plain-language reasons why a candidate matched |
| Friction map | Likely friction points and suggested house rules for a pair |
| Ask about a match | Free-form questions, answered only from the candidate's stored data |
| Shortlist | Bookmark and remove candidates; saved between runs |
| Out-of-date results | If you change your profile after ranking, results are flagged and re-scored when used |

Candidates with missing profile data are never guessed about: those dimensions are reported as
"not assessed", and candidates with no lifestyle data are skipped with the reason shown.

## Prerequisites

- Java 17 or newer (JDK, not only a JRE)
- Maven 3.8 or newer
- An Anthropic API key (the AI features call Claude)

Check with `java -version` and `mvn -v`.

## Setup

1. Clone the repository and open a terminal in its folder (the one containing `pom.xml`).
2. Create your settings file from the template:
   - Windows PowerShell: `copy .env.example .env`
   - macOS / Linux: `cp .env.example .env`
3. Open `.env` in a text editor and replace `YOUR_LOCAL_API_KEY_HERE` with your own key:
   ```
   LLM_API_KEY=your-key-here
   LLM_MODEL=claude-haiku-5-5
   ```
   Do not put quotes or spaces around the key. `.env` is git-ignored: never commit it.

## Build and test

```
mvn test
```

The tests use a fake language model and the local sample data. They need no API key and make no
network calls, so they cost nothing to run.

## Run

All commands are run from the repository root. On Windows PowerShell keep the quotes around
`-Dexec.args=...`.

**GUI**
```
mvn -q exec:java "-Dexec.args=gui"
```
Log in with a user id (try `p001`), then use the Profile, Matches and Shortlist tabs. Press
**Find matches** to rank candidates; select a row to explain it, view its friction map, ask a
question or bookmark it.

**CLI, interactive mode**
```
mvn -q exec:java "-Dexec.args=shell"
```
Type one command per line (`help` lists them):
```
login p001
match --top 3
explain 1
friction 1
ask 1 Do they have guests over?
shortlist add 1
shortlist
quit
```

**CLI, one-shot**
```
mvn -q exec:java "-Dexec.args=match --as p001 --city Toronto --top 3 --rationale"
```

Options for `match`: `--as <id>` (required in one-shot mode), `--city`, `--min-budget`,
`--max-budget`, `--from <yyyy-MM-dd>`, `--to <yyyy-MM-dd>`, `--top <n>`, `--rationale`.

## Sample data

`data/platform_users.json` stands in for the housing platform. All people in it are made up.

| Id | Notes |
|---|---|
| p001, p002, p004, p007, p008 | Complete lifestyle data |
| p003 | Partial data (two dimensions missing), shown as "not assessed" |
| p005 | Deactivated account (cannot log in, never ranked) |
| p006 | No lifestyle data: log in as p006 to try the profile conversation |

## Cost and privacy

- Every ranking, explanation, friction map, question and profile conversation sends text to the
  Claude API using your own key, and you pay for that usage. The default model is a small,
  low-cost Claude model. Ranking makes one call per candidate.
- Only the profile data needed for each task is sent to the model. It is wrapped in tags, and the
  prompts tell the model to treat it as data, never as instructions.
- Saved shortlists are written to `data/shortlists.json`, which is git-ignored.

## Design

Package `com.roommatch`:

| Package | Role |
|---|---|
| `model` | Users, lifestyle profile, compatibility results, friction map, shortlist, hard filters |
| `platform`, `repository` | Housing-platform data source and the adapter RoomMatch uses to read it |
| `agent` | The AI agent: planning, tools, profile conversation, scoring, explanations, friction maps, Q&A |
| `llm`, `config` | Claude client behind an interface; `.env` settings |
| `service` | Staleness tracking and shortlist persistence |
| `controller` | `AgentController`, the single entry point both interfaces use |
| `ui` | GUI (Swing) and CLI (one-shot and interactive) |

Design patterns used:

| Pattern | Where |
|---|---|
| Observer | `ProfileSubject` / `ProfileObserver`: `User` notifies `RankingService`, which flags results as out of date |
| Adapter | `PlatformProfileAdapter` makes the platform's data fit the `CandidateRepository` interface |
| Strategy | `CompatibilityScorer` with `LLMCompatibilityScorer`; scoring can be swapped without touching the agent |
| State | `ElicitationAgent` with `AskingDimensionState`, `ClarifyingState`, `CompleteState` for the profile conversation |
| Facade | `AgentController` hides the agent, services and generators from the GUI and CLI |
| Builder | `AgentController.Builder` wires the optional features |

Agent behaviour: the agent plans a multi-step ranking run, fetches candidate data through a tool
registry (`ToolManager`, `ProfileAccessTool`), remembers the profile conversation and the stored
results, validates the model's JSON (retrying when it is unusable), and re-scores a pair when your
profile changed since it was scored.

## Troubleshooting

- **"Setup problem: LLM_API_KEY is not set"**: `.env` is missing or the key line is empty. It must
  be in the folder you run the command from.
- **"The AI service failed: ..."**: the Claude API returned an error (wrong or revoked key, no
  credit, network problem). Fix the cause and try again; nothing is lost.
- **`mvn` is not recognized**: Maven's `bin` folder is not on your PATH. Run `mvn -v` in a new
  terminal after adding it.
- **Tests fail to start**: check that `java -version` shows 17 or newer.
