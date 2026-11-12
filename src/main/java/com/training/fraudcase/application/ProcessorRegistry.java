package com.training.fraudcase.application;

import com.training.fraudcase.agent.common.FraudCaseProcessor;
import com.training.fraudcase.config.CapstoneProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Selects the active FraudCaseProcessor from capstone.processor-mode.
 * If the configured mode's adapter is absent (e.g. its conditional beans
 * were not created), fails safely to the deterministic rules adapter.
 */
@Component
public class ProcessorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProcessorRegistry.class);

    private final Map<String, FraudCaseProcessor> byMode;
    private final CapstoneProperties props;

    public ProcessorRegistry(List<FraudCaseProcessor> processors, CapstoneProperties props) {
        this.byMode = processors.stream()
                .collect(Collectors.toMap(FraudCaseProcessor::mode, Function.identity()));
        this.props = props;
    }

    public FraudCaseProcessor active() {
        String mode = props.getProcessorMode().trim().toLowerCase();
        FraudCaseProcessor selected = byMode.get(mode);
        if (selected == null) {
            log.warn("processor-mode '{}' unavailable (registered: {}); failing safely to rules",
                    mode, byMode.keySet());
            return byMode.get("rules");
        }
        return selected;
    }

    public String activeMode() {
        return active().mode();
    }
}
