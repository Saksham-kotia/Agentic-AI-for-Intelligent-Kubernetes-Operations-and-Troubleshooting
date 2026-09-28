package org.csanchez.adk.agents.k8sagent.a2a;

import java.util.List;
import java.util.Map;

/**
 * A2A response to rollouts-plugin-metric-ai
 * Supports both single-model and multi-model analysis
 */
public class A2AResponse {
	/**
	 * @deprecated The rollouts-plugin-metric-ai uses this to determine canary promotion.
	 */
	@Deprecated
	private boolean promote;
	
	// New multi-workload API fields
	private boolean success;
	private int workloadCount;
	private int pullRequestCount;
	private List<WorkloadRemediation> workloads;
	
	// Multi-model fields
	private List<ModelAnalysisResult> modelResults;
	private String votingRationale;
	
	public boolean isPromote() {
		return promote;
	}
	
	public void setPromote(boolean promote) {
		this.promote = promote;
	}
	
	public List<ModelAnalysisResult> getModelResults() {
		return modelResults;
	}
	
	public void setModelResults(List<ModelAnalysisResult> modelResults) {
		this.modelResults = modelResults;
	}
	
	public String getVotingRationale() {
		return votingRationale;
	}
	
	public void setVotingRationale(String votingRationale) {
		this.votingRationale = votingRationale;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public int getWorkloadCount() {
		return workloadCount;
	}

	public void setWorkloadCount(int workloadCount) {
		this.workloadCount = workloadCount;
	}

	public int getPullRequestCount() {
		return pullRequestCount;
	}

	public void setPullRequestCount(int pullRequestCount) {
		this.pullRequestCount = pullRequestCount;
	}

	public List<WorkloadRemediation> getWorkloads() {
		return workloads;
	}

	public void setWorkloads(List<WorkloadRemediation> workloads) {
		this.workloads = workloads;
	}
}


