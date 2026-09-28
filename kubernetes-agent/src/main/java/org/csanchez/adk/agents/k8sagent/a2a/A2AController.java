package org.csanchez.adk.agents.k8sagent.a2a;

import org.csanchez.adk.agents.k8sagent.KubernetesAgent;
import org.csanchez.adk.agents.k8sagent.remediation.GitHubPRTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Agent-to-Agent (A2A) communication
 */
@RestController
@RequestMapping("/a2a")
@SuppressWarnings("null")
public class A2AController {

	private static final Logger logger = LoggerFactory.getLogger(A2AController.class);

	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		return ResponseEntity.ok(Map.of(
				"status", "healthy",
				"agent", "KubernetesAgent",
				"version", "1.0.0"));
	}

	@PostMapping("/analyze")
	public ResponseEntity<Object> analyze(@RequestBody A2ARequest request) {
		logger.info("Received A2A analysis request from user: {}", request.getUserId());

		try {
			// Determine which model to use (use the first one configured)
			List<String> modelsToUse = determineModelsToUse(request);
			String modelName = modelsToUse.isEmpty() ? "gemini-3-flash-preview" : modelsToUse.get(0);
			logger.info("Using model for analysis: {}", modelName);

			// 1. Collect evidence deterministically
			String targetNamespace = null;
			if (request.getContext() != null && request.getContext().containsKey("namespace")) {
				targetNamespace = request.getContext().get("namespace").toString();
			}
			String evidenceJson = KubernetesEvidenceCollector.collectEvidence(targetNamespace);

			// 2. Perform Gemini analysis
			A2AResponse response = GeminiAnalyzer.analyze(modelName, request.getPrompt(), evidenceJson);

			// 3. Optional GitHub PR execution
			int prCount = 0;
			if (response.getWorkloads() != null) {
				for (WorkloadRemediation rem : response.getWorkloads()) {
					if (rem.isRequiresGitHubPR() && rem.getFileChanges() != null && !rem.getFileChanges().isEmpty()) {
						String repoUrl = null;
						if (request.getContext() != null && request.getContext().containsKey("repoUrl")) {
							repoUrl = request.getContext().get("repoUrl").toString();
						}
						if (repoUrl == null || repoUrl.trim().isEmpty()) {
							repoUrl = org.csanchez.adk.agents.k8sagent.config.Config.get("GITHUB_REPO_URL", "");
						}

						if (repoUrl != null && !repoUrl.trim().isEmpty()) {
							logger.info("Executing GitHub PR as requested by Gemini for workload: {} in repository: {}", rem.getAffectedWorkload(), repoUrl);
							try {
								GitHubPRTool prTool = new GitHubPRTool(new org.csanchez.adk.agents.k8sagent.remediation.GitOperations());
								Map<String, Object> prResult = prTool.create_github_pr(
										repoUrl,
										rem.getFileChanges(),
										rem.getRemediation(),
										rem.getRootCause(),
										rem.getNamespace(),
										rem.getAffectedWorkload()
								);
								if (Boolean.TRUE.equals(prResult.get("success"))) {
									logger.info("GitHub PR created successfully for {}.", rem.getAffectedWorkload());
									rem.setPrLink((String) prResult.get("prUrl"));
									prCount++;
								} else {
									logger.error("Failed to create GitHub PR for " + rem.getAffectedWorkload() + ": " + prResult.get("error"));
									rem.setPrLink("Failed to create PR: " + prResult.get("error"));
								}
							} catch (Exception e) {
								logger.error("Failed to create GitHub PR for " + rem.getAffectedWorkload(), e);
								rem.setPrLink("Failed to create PR: " + e.getMessage());
							}
						} else {
							logger.warn("Gemini requested a GitHub PR for {}, but no repository was configured.", rem.getAffectedWorkload());
							rem.setPrLink("Repository not configured.");
						}
					}
				}
			}
			
			java.util.Map<String, Object> finalResponse = new java.util.LinkedHashMap<>();
			finalResponse.put("success", true);
			finalResponse.put("workloadCount", response.getWorkloads() != null ? response.getWorkloads().size() : 0);
			finalResponse.put("pullRequestCount", prCount);
			finalResponse.put("workloads", response.getWorkloads() != null ? response.getWorkloads() : java.util.Collections.emptyList());
			
			return ResponseEntity.ok(finalResponse);

		} catch (Exception e) {
			logger.error("Error processing A2A request from user: {}", request.getUserId(), e);
			
			java.util.Map<String, Object> errorResponse = new java.util.LinkedHashMap<>();
			errorResponse.put("success", false);
			errorResponse.put("error", "Analysis failed: " + e.getMessage());
			
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
		}
	}
	
	private List<String> determineModelsToUse(A2ARequest request) {
		if (request.getModelsToUse() != null && !request.getModelsToUse().isEmpty()) {
			return request.getModelsToUse();
		}
		return KubernetesAgent.getModelsToUse();
	}
}
