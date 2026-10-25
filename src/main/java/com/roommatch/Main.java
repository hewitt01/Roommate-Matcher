package com.roommatch;

import com.roommatch.agent.CompatibilityAgent;
import com.roommatch.agent.LLMCompatibilityScorer;
import com.roommatch.agent.ProfileAccessTool;
import com.roommatch.agent.ToolManager;
import com.roommatch.config.AppConfig;
import com.roommatch.controller.AgentController;
import com.roommatch.llm.ClaudeLLMClient;
import com.roommatch.llm.LLMClient;
import com.roommatch.platform.HousingPlatformClient;
import com.roommatch.repository.CandidateRepository;
import com.roommatch.repository.PlatformProfileAdapter;
import com.roommatch.service.RankingService;
import com.roommatch.ui.RoomMatchCLI;
import java.nio.file.Path;

/** Entry point: wires the real components together and starts the CLI. */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        int exitCode = new RoomMatchCLI(System.out, System.err, Main::buildServices).run(args);
        System.exit(exitCode);
    }

    static RoomMatchCLI.Services buildServices() {
        AppConfig config = AppConfig.load(Path.of(".env"));
        LLMClient llm = ClaudeLLMClient.fromConfig(config);
        CandidateRepository repository = new PlatformProfileAdapter(
                HousingPlatformClient.fromJsonFile(Path.of("data", "platform_users.json")));

        ToolManager tools = new ToolManager();
        tools.register(new ProfileAccessTool(repository));
        CompatibilityAgent agent = new CompatibilityAgent(tools, new LLMCompatibilityScorer(llm));
        return new RoomMatchCLI.Services(new AgentController(agent, new RankingService()), repository);
    }
}
