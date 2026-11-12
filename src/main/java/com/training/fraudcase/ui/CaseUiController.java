package com.training.fraudcase.ui;

import com.training.fraudcase.api.dto.CreateCaseRequest;
import com.training.fraudcase.api.dto.MessageRequest;
import com.training.fraudcase.api.dto.ReviewRequest;
import com.training.fraudcase.application.FraudCaseService;
import com.training.fraudcase.application.InvestigationOrchestrator;
import com.training.fraudcase.domain.enums.CardPossession;
import com.training.fraudcase.domain.enums.CaseChannel;
import com.training.fraudcase.domain.enums.Disposition;
import com.training.fraudcase.domain.enums.IssueType;
import com.training.fraudcase.domain.enums.Route;
import com.training.fraudcase.domain.enums.TransactionChannel;
import com.training.fraudcase.domain.enums.TransactionStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;

@Controller
public class CaseUiController {

    private final FraudCaseService caseService;
    private final InvestigationOrchestrator orchestrator;

    public CaseUiController(FraudCaseService caseService, InvestigationOrchestrator orchestrator) {
        this.caseService = caseService;
        this.orchestrator = orchestrator;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("cases", caseService.listCases());
        return "index";
    }

    @GetMapping("/cases/new")
    public String newCase(Model model) {
        model.addAttribute("channels", CaseChannel.values());
        model.addAttribute("txnChannels", TransactionChannel.values());
        model.addAttribute("txnStatuses", TransactionStatus.values());
        model.addAttribute("issueTypes", IssueType.values());
        model.addAttribute("possessions", CardPossession.values());
        return "case-new";
    }

    @PostMapping("/cases")
    public String create(@RequestParam CaseChannel channel,
                         @RequestParam String maskedCustomerRef,
                         @RequestParam(required = false) String homeRegion,
                         @RequestParam String transactionRef,
                         @RequestParam BigDecimal amount,
                         @RequestParam String currency,
                         @RequestParam TransactionStatus transactionStatus,
                         @RequestParam TransactionChannel transactionChannel,
                         @RequestParam String maskedMerchant,
                         @RequestParam IssueType issueType,
                         @RequestParam String customerStatement,
                         @RequestParam CardPossession cardPossession,
                         @RequestParam Boolean merchantRecognised,
                         @RequestParam(defaultValue = "false") boolean syntheticDataConfirmed,
                         @RequestParam(defaultValue = "false") boolean reviewRequired,
                         @RequestHeader(value = "X-Analyst-Id", defaultValue = "ui-analyst") String analystId,
                         RedirectAttributes redirect) {
        try {
            var req = new CreateCaseRequest(Instant.now(), channel, maskedCustomerRef, homeRegion,
                    transactionRef, amount, currency, Instant.now(), transactionStatus,
                    transactionChannel, maskedMerchant, issueType, customerStatement,
                    cardPossession, merchantRecognised, syntheticDataConfirmed, reviewRequired);
            var created = caseService.createCase(req, analystId);
            return "redirect:/cases/" + created.caseId();
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/cases/new";
        }
    }

    @GetMapping("/cases/{caseId}")
    public String detail(@PathVariable String caseId, Model model) {
        model.addAttribute("summary", caseService.getSummary(caseId));
        model.addAttribute("claim", caseService.loadClaim(caseId));
        model.addAttribute("kase", caseService.loadCase(caseId));
        model.addAttribute("evidence", caseService.evidenceViews(caseId));
        model.addAttribute("traces", caseService.traceViews(caseId));
        model.addAttribute("recommendation", caseService.latestRecommendation(caseId));
        model.addAttribute("citations", caseService.citations(caseId));
        model.addAttribute("messages", caseService.messages(caseId));
        model.addAttribute("reviews", caseService.reviews(caseId));
        model.addAttribute("dispositions", Disposition.values());
        model.addAttribute("routes", Route.values());
        return "case-detail";
    }

    @PostMapping("/cases/{caseId}/investigate")
    public String investigate(@PathVariable String caseId,
                              @RequestHeader(value = "X-Analyst-Id", defaultValue = "ui-analyst") String analystId,
                              RedirectAttributes redirect) {
        try {
            orchestrator.investigate(caseId, analystId);
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + caseId;
    }

    @PostMapping("/cases/{caseId}/messages")
    public String message(@PathVariable String caseId,
                          @RequestParam String question,
                          @RequestHeader(value = "X-Analyst-Id", defaultValue = "ui-analyst") String analystId,
                          RedirectAttributes redirect) {
        try {
            caseService.postMessage(caseId, question, analystId);
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + caseId;
    }

    @PostMapping("/cases/{caseId}/reviews")
    public String review(@PathVariable String caseId,
                         @RequestParam Disposition disposition,
                         @RequestParam String reason,
                         @RequestParam(required = false) Route selectedRoute,
                         @RequestHeader(value = "X-Reviewer-Id", defaultValue = "ui-reviewer") String reviewerId,
                         RedirectAttributes redirect) {
        try {
            caseService.recordReview(caseId, new ReviewRequest(disposition, reason, selectedRoute), reviewerId);
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + caseId;
    }
}
