package org.csanchez.adk.agents.k8sagent.a2a;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class KubernetesEvidenceCollector {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesEvidenceCollector.class);
    private static final KubernetesClient k8sClient = new KubernetesClientBuilder().build();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String collectEvidence(String targetNamespace) {
        logger.info("Starting deterministic Kubernetes evidence collection...");
        Map<String, Object> evidenceBundle = new HashMap<>();
        List<Map<String, Object>> unhealthyWorkloads = new ArrayList<>();

        try {
            List<Pod> pods;
            if (targetNamespace != null && !targetNamespace.isEmpty()) {
                pods = k8sClient.pods().inNamespace(targetNamespace).list().getItems();
            } else {
                pods = k8sClient.pods().inAnyNamespace().list().getItems();
            }

            for (Pod pod : pods) {
                if (isPodUnhealthy(pod)) {
                    Map<String, Object> podData = collectPodData(pod);
                    unhealthyWorkloads.add(podData);
                }
            }

            evidenceBundle.put("unhealthyWorkloads", unhealthyWorkloads);
            evidenceBundle.put("totalUnhealthyPods", unhealthyWorkloads.size());
            
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(evidenceBundle);
            logger.info("Evidence collection complete. Found {} unhealthy pods.", unhealthyWorkloads.size());
            return json;

        } catch (Exception e) {
            logger.error("Failed to collect Kubernetes evidence", e);
            return "{\"error\": \"Failed to collect evidence: " + e.getMessage() + "\"}";
        }
    }

    private static boolean isPodUnhealthy(Pod pod) {
        PodStatus status = pod.getStatus();
        if (status == null) return false;

        String phase = status.getPhase();
        if ("Failed".equalsIgnoreCase(phase) || "Unknown".equalsIgnoreCase(phase)) {
            return true;
        }

        if (status.getContainerStatuses() != null) {
            for (ContainerStatus cs : status.getContainerStatuses()) {
                if (cs.getRestartCount() > 0) {
                    return true;
                }
                ContainerState state = cs.getState();
                if (state != null) {
                    if (state.getWaiting() != null) return true;
                    if (state.getTerminated() != null && state.getTerminated().getExitCode() != 0) return true;
                }
                ContainerState lastState = cs.getLastState();
                if (lastState != null) {
                    if (lastState.getTerminated() != null && lastState.getTerminated().getExitCode() != 0) return true;
                }
            }
        }
        return false;
    }

    private static Map<String, Object> collectPodData(Pod pod) {
        Map<String, Object> podData = new HashMap<>();
        String name = pod.getMetadata().getName();
        String namespace = pod.getMetadata().getNamespace();

        podData.put("name", name);
        podData.put("namespace", namespace);
        podData.put("phase", pod.getStatus().getPhase());

        // Container info
        List<Map<String, Object>> containers = new ArrayList<>();
        if (pod.getStatus().getContainerStatuses() != null) {
            for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                Map<String, Object> cData = new HashMap<>();
                cData.put("name", cs.getName());
                cData.put("image", cs.getImage());
                cData.put("restartCount", cs.getRestartCount());
                
                ContainerState state = cs.getState();
                if (state != null) {
                    cData.put("state", parseContainerState(state));
                }
                ContainerState lastState = cs.getLastState();
                if (lastState != null) {
                    cData.put("lastState", parseContainerState(lastState));
                }
                containers.add(cData);
            }
        }
        podData.put("containers", containers);

        // Owners
        List<OwnerReference> owners = pod.getMetadata().getOwnerReferences();
        if (owners != null && !owners.isEmpty()) {
            podData.put("owners", owners.stream()
                .map(o -> Map.of("kind", o.getKind(), "name", o.getName()))
                .collect(Collectors.toList()));
        }

        // Recent Events
        try {
            List<Event> events = k8sClient.v1().events().inNamespace(namespace)
                .withField("involvedObject.name", name)
                .list().getItems();
                
            List<Map<String, Object>> eventData = events.stream()
                .sorted((e1, e2) -> {
                    if (e2.getLastTimestamp() != null && e1.getLastTimestamp() != null) {
                        return e2.getLastTimestamp().compareTo(e1.getLastTimestamp());
                    }
                    return 0;
                })
                .limit(5)
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", e.getType() != null ? e.getType() : "");
                    map.put("reason", e.getReason() != null ? e.getReason() : "");
                    map.put("message", e.getMessage() != null ? e.getMessage() : "");
                    return map;
                })
                .collect(Collectors.toList());
            podData.put("recentEvents", eventData);
        } catch (Exception e) {
            logger.warn("Could not fetch events for pod {}", name);
        }

        // Logs
        try {
            String logs = k8sClient.pods().inNamespace(namespace).withName(name)
                .tailingLines(50)
                .getLog();
            podData.put("logs", logs);
        } catch (Exception e) {
            podData.put("logs", "Could not fetch logs: " + e.getMessage());
        }

        // Previous logs if restarted
        boolean hasRestarts = containers.stream().anyMatch(c -> c.containsKey("restartCount") && (int) c.get("restartCount") > 0);
        if (hasRestarts) {
            try {
                String previousLogs = k8sClient.pods().inNamespace(namespace).withName(name)
                    .terminated()
                    .tailingLines(50)
                    .getLog();
                if (previousLogs != null && !previousLogs.isEmpty()) {
                    podData.put("previousLogs", previousLogs);
                } else {
                    podData.put("previousLogs", "Previous logs are unavailable.");
                }
            } catch (Exception e) {
                podData.put("previousLogs", "Previous logs are unavailable: " + e.getMessage());
            }
        }

        return podData;
    }

    private static Map<String, Object> parseContainerState(ContainerState state) {
        Map<String, Object> stateData = new HashMap<>();
        if (state.getWaiting() != null) {
            stateData.put("status", "Waiting");
            stateData.put("reason", state.getWaiting().getReason());
            stateData.put("message", state.getWaiting().getMessage());
        } else if (state.getTerminated() != null) {
            stateData.put("status", "Terminated");
            stateData.put("reason", state.getTerminated().getReason());
            stateData.put("message", state.getTerminated().getMessage());
            stateData.put("exitCode", state.getTerminated().getExitCode());
            stateData.put("signal", state.getTerminated().getSignal());
            stateData.put("startedAt", state.getTerminated().getStartedAt());
            stateData.put("finishedAt", state.getTerminated().getFinishedAt());
        } else if (state.getRunning() != null) {
            stateData.put("status", "Running");
            stateData.put("startedAt", state.getRunning().getStartedAt());
        }
        return stateData;
    }
}
