package com.roommatch.ui;

import com.roommatch.agent.CompatibilityAgent;
import com.roommatch.agent.ElicitationAgent;
import com.roommatch.agent.ExplanationGenerator;
import com.roommatch.agent.FrictionMapGenerator;
import com.roommatch.agent.LLMCompatibilityScorer;
import com.roommatch.agent.MatchQuestionAnswerer;
import com.roommatch.agent.ProfileAccessTool;
import com.roommatch.agent.ToolManager;
import com.roommatch.controller.AgentController;
import com.roommatch.llm.LLMClient;
import com.roommatch.llm.LLMException;
import com.roommatch.platform.HousingPlatformClient;
import com.roommatch.repository.CandidateRepository;
import com.roommatch.repository.PlatformProfileAdapter;
import com.roommatch.service.RankingService;
import com.roommatch.service.ShortlistService;
import com.roommatch.service.ShortlistStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Real controller, agents, adapter and sample data for GUI tests; only the language model is faked. */
final class GuiTestServices {
    static final String ALL_FOUR = "{\"dimensions\": {\"cleanliness\": \"spotless\", \"noiseTolerance\": \"quiet\","
            + " \"guestFrequency\": \"rarely\", \"sleepSchedule\": \"early riser\"}, \"followUp\": \"\"}";

    final AtomicInteger scoringCalls = new AtomicInteger();
    final Map<String, List<String>> shortlistData = new HashMap<>();
    volatile String elicitationReply = ALL_FOUR;
    volatile boolean aiDown;
    final RoomMatchCLI.Services services;

    GuiTestServices() {
        LLMClient llm = prompt -> {
            if (aiDown) {
                throw new LLMException("service down");
            }
            if (prompt.contains("\"answerable\"")) {
                return prompt.contains("pets")
                        ? "{\"answerable\": false, \"answer\": \"Their profile says nothing about pets.\"}"
                        : "{\"answerable\": true, \"answer\": \"They are quiet and tidy.\"}";
            }
            if (prompt.contains("\"frictionPoints\"")) {
                return "{\"frictionPoints\": [\"Tidiness differs.\", \"Noise in the evening.\"],"
                        + " \"suggestedRules\": [\"Quiet hours after ten.\"]}";
            }
            if (prompt.contains("Reply with the explanation text only.")) {
                return "You two match on sleep and noise.";
            }
            if (prompt.contains("<answer>")) {
                return elicitationReply;
            }
            int n = scoringCalls.incrementAndGet();
            double high = Math.max(0, 100 - 5 * n);
            return "{\"scoreAtoB\": " + high + ", \"scoreBtoA\": " + high + ", \"rationale\": \"fits\"}";
        };
        CandidateRepository repo = new PlatformProfileAdapter(
                HousingPlatformClient.fromJsonFile(Path.of("data", "platform_users.json")));
        ToolManager tools = new ToolManager();
        tools.register(new ProfileAccessTool(repo));
        CompatibilityAgent agent = new CompatibilityAgent(tools, new LLMCompatibilityScorer(llm));
        ShortlistStore store = new ShortlistStore() {
            @Override public List<String> load(String userId) { return shortlistData.getOrDefault(userId, List.of()); }
            @Override public void save(String userId, List<String> ids) { shortlistData.put(userId, new ArrayList<>(ids)); }
        };
        AgentController controller = AgentController.builder(agent, new RankingService())
                .elicitation(() -> new ElicitationAgent(llm))
                .explanations(new ExplanationGenerator(llm))
                .frictionMaps(new FrictionMapGenerator(llm))
                .questions(new MatchQuestionAnswerer(llm))
                .shortlists(new ShortlistService(repo, store))
                .build();
        this.services = new RoomMatchCLI.Services(controller, repo);
    }

    GuiSession session() {
        return new GuiSession(services.controller(), services.repository(), GuiTasks.immediate());
    }
}
