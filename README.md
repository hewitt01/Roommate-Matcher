# RoomMatch AI

An AI-powered roommate compatibility agent, built for EECS3311.

Most roommate-matching platforms rely on static filters (budget, location, a one-line bio) that miss the daily habits, including cleanliness, noise, guests, and sleep schedule, that actually cause conflict after moving in. RoomMatch AI elicits a user's real lifestyle tolerances through adaptive conversation, ranks them against many candidate profiles using asymmetric compatibility reasoning, and explains why two people are or aren't a good match. It also generates a post-match "friction map" of likely conflict points and suggested house rules.

📄 **[Full Stage 1 design report](docs/stage1-report.md)**: project overview, feature specifications, UML class/use-case/sequence diagrams, design pattern justifications, and feature-to-design traceability.

## Status
- **Stage 1:** Design complete
- **Stage 2:** Coming soon
- **Stage 3:** Coming soon

## Tech
- AI/LLM: Claude (Anthropic)
- Architecture: GUI/CLI feeding into a Controller (Facade), which drives the agent subsystem (Strategy, State, Observer, and Adapter patterns)
