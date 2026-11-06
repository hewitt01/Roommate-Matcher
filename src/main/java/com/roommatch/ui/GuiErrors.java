package com.roommatch.ui;

import com.roommatch.agent.ElicitationException;
import com.roommatch.agent.ProfileIncompleteException;
import com.roommatch.config.ConfigException;
import com.roommatch.llm.LLMException;
import com.roommatch.platform.PlatformException;

/** Turns exceptions into one plain sentence a user can act on (never a stack trace). */
final class GuiErrors {
    private GuiErrors() { }

    static String describe(Throwable t) {
        String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        if (t instanceof ProfileIncompleteException) {
            return message + " Ranking needs a complete lifestyle profile: use the Profile tab to build it.";
        }
        if (t instanceof LLMException) {
            return "The AI service failed: " + message;
        }
        if (t instanceof ConfigException || t instanceof PlatformException) {
            return "Setup problem: " + message;
        }
        if (t instanceof ElicitationException) {
            return "The profile conversation hit a problem: " + message;
        }
        return message;
    }
}
