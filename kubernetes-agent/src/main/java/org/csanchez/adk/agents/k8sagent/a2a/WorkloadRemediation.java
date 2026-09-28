package org.csanchez.adk.agents.k8sagent.a2a;

import java.util.Map;

public class WorkloadRemediation {
    private String affectedWorkload;
    private String namespace;
    private String failureType;
    private String rootCause;
    private String evidence;
    private String remediation;
    private int confidence;
    private Map<String, String> fileChanges;
    private boolean requiresGitHubPR;
    private String prLink;

    public String getAffectedWorkload() {
        return affectedWorkload;
    }

    public void setAffectedWorkload(String affectedWorkload) {
        this.affectedWorkload = affectedWorkload;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getRemediation() {
        return remediation;
    }

    public void setRemediation(String remediation) {
        this.remediation = remediation;
    }

    public int getConfidence() {
        return confidence;
    }

    public void setConfidence(int confidence) {
        this.confidence = confidence;
    }

    public Map<String, String> getFileChanges() {
        return fileChanges;
    }

    public void setFileChanges(Map<String, String> fileChanges) {
        this.fileChanges = fileChanges;
    }

    public boolean isRequiresGitHubPR() {
        return requiresGitHubPR;
    }

    public void setRequiresGitHubPR(boolean requiresGitHubPR) {
        this.requiresGitHubPR = requiresGitHubPR;
    }

    public String getPrLink() {
        return prLink;
    }

    public void setPrLink(String prLink) {
        this.prLink = prLink;
    }
}
