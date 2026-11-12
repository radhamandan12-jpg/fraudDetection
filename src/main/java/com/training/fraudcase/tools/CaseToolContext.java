package com.training.fraudcase.tools;

/**
 * Carries the active caseId while a processor (model-driven or rules) is
 * running so that every tool invocation is traced against the right case.
 */
public final class CaseToolContext {

    private static final ThreadLocal<String> CURRENT_CASE = new ThreadLocal<>();

    private CaseToolContext() {}

    public static void set(String caseId) {
        CURRENT_CASE.set(caseId);
    }

    public static String currentCaseId() {
        return CURRENT_CASE.get();
    }

    public static void clear() {
        CURRENT_CASE.remove();
    }
}
