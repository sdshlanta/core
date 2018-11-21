package com.core.client.rest;

import com.core.client.ICoreClient;
import com.core.data.*;
import com.core.utils.WebUtils;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class CoreRestClient implements ICoreClient {
    private static final Logger logger = LogManager.getLogger();
    private String baseUrl;
    private Integer sessionId;
    private SessionState sessionState;

    @Override
    public void setUrl(String url) {
        this.baseUrl = url;
    }

    @Override
    public void updateState(SessionState state) {
        sessionState = state;
    }

    @Override
    public void updateSession(Integer sessionId) {
        this.sessionId = sessionId;
    }

    private String getUrl(String path) {
        return String.format("%s/%s", baseUrl, path);
    }

    @Override
    public CreatedSession createSession() throws IOException {
        String url = getUrl("sessions");
        return WebUtils.post(url, CreatedSession.class);
    }

    public GetServices getServices() throws IOException {
        String url = getUrl("services");
        return WebUtils.getJson(url, GetServices.class);
    }

    @Override
    public GetSession getSession(Integer sessionId) throws IOException {
        String path = String.format("sessions/%s", sessionId);
        String url = getUrl(path);
        return WebUtils.getJson(url, GetSession.class);
    }

    @Override
    public GetSessions getSessions() throws IOException {
        String url = getUrl("sessions");
        return WebUtils.getJson(url, GetSessions.class);
    }

    @Override
    public boolean start(Collection<CoreNode> nodes, Collection<CoreLink> links, List<Hook> hooks) throws IOException {
        boolean result = setState(SessionState.DEFINITION);
        if (!result) {
            return false;
        }

        result = setState(SessionState.CONFIGURATION);
        if (!result) {
            return false;
        }

        for (Hook hook : hooks) {
            if (!createHook(hook)) {
                return false;
            }
        }

        for (CoreNode node : nodes) {
            // must pre-configure wlan nodes, if not already
            if (node.getNodeType().getValue() == NodeType.WLAN) {
                WlanConfig config = getWlanConfig(node);
                setWlanConfig(node, config);
            }

            if (!createNode(node)) {
                return false;
            }
        }

        for (CoreLink link : links) {
            if (!createLink(link)) {
                return false;
            }
        }

        return setState(SessionState.INSTANTIATION);
    }

    @Override
    public boolean stop() throws IOException {
        return setState(SessionState.SHUTDOWN);
    }

    @Override
    public boolean setState(SessionState state) throws IOException {
        String url = getUrl(String.format("sessions/%s/state", sessionId));
        Map<String, Integer> data = new HashMap<>();
        data.put("state", state.getValue());
        boolean result = WebUtils.putJson(url, data);

        if (result) {
            sessionState = state;
        }
        return result;
    }

    private boolean uploadFile(File file) throws IOException {
        String url = getUrl("upload");
        return WebUtils.postFile(url, file);
    }

    @Override
    public CoreService getService(CoreNode node, String serviceName) throws IOException {
        String url = getUrl(String.format("sessions/%s/nodes/%s/services/%s", sessionId, node.getId(), serviceName));
        return WebUtils.getJson(url, CoreService.class);
    }

    @Override
    public boolean setService(CoreNode node, String serviceName, CoreService service) throws IOException {
        String url = getUrl(String.format("sessions/%s/nodes/%s/services/%s", sessionId, node.getId(), serviceName));
        return WebUtils.putJson(url, service);
    }

    @Override
    public String getServiceFile(CoreNode node, String serviceName, String fileName) throws IOException {
        String url = getUrl(String.format("sessions/%s/nodes/%s/services/%s/file", sessionId, node.getId(),
                serviceName));
        Map<String, String> args = new HashMap<>();
        args.put("file", fileName);
        return WebUtils.getJson(url, String.class, args);
    }

    @Override
    public boolean setServiceFile(CoreNode node, String serviceName, ServiceFile serviceFile) throws IOException {
        String url = getUrl(String.format("sessions/%s/nodes/%s/services/%s/file", sessionId, node.getId(),
                serviceName));
        return WebUtils.putJson(url, serviceFile);
    }

    @Override
    public GetEmaneModels getEmaneModels() throws IOException {
        String url = getUrl(String.format("sessions/%s/emane/models", sessionId));
        return WebUtils.getJson(url, GetEmaneModels.class);
    }

    @Override
    public GetConfig getEmaneModelConfig(Integer id, String model) throws IOException {
        String url = getUrl(String.format("sessions/%s/emane/model/config", sessionId));
        Map<String, String> args = new HashMap<>();
        args.put("node", id.toString());
        args.put("name", model);
        return WebUtils.getJson(url, GetConfig.class, args);
    }

    @Override
    public GetConfig getEmaneConfig(CoreNode node) throws IOException {
        String url = getUrl(String.format("sessions/%s/emane/config", sessionId));
        Map<String, String> args = new HashMap<>();
        args.put("node", node.getId().toString());
        return WebUtils.getJson(url, GetConfig.class, args);
    }

    @Override
    public boolean setEmaneConfig(CoreNode node, List<ConfigOption> options) throws IOException {
        String url = getUrl(String.format("sessions/%s/emane/config", sessionId));
        SetEmaneConfig setEmaneConfig = new SetEmaneConfig();
        setEmaneConfig.setNode(node.getId());
        setEmaneConfig.setValues(options);
        return WebUtils.putJson(url, setEmaneConfig);
    }

    @Override
    public boolean setEmaneModelConfig(Integer id, String model, List<ConfigOption> options) throws IOException {
        String url = getUrl(String.format("sessions/%s/emane/model/config", sessionId));
        SetEmaneModelConfig setEmaneModelConfig = new SetEmaneModelConfig();
        setEmaneModelConfig.setNode(id);
        setEmaneModelConfig.setName(model);
        setEmaneModelConfig.setValues(options);
        return WebUtils.putJson(url, setEmaneModelConfig);
    }

    @Override
    public boolean isRunning() {
        return sessionState == SessionState.RUNTIME;
    }

    @Override
    public void saveSession(File file) throws IOException {
        String path = String.format("sessions/%s/xml", sessionId);
        String url = getUrl(path);
        WebUtils.getFile(url, file);
    }

    @Override
    public CreatedSession openSession(File file) throws IOException {
        String url = getUrl("sessions/xml");
        return WebUtils.postFile(url, file, CreatedSession.class);
    }

    @Override
    public GetConfig getSessionConfig() throws IOException {
        String url = getUrl(String.format("sessions/%s/options", sessionId));
        return WebUtils.getJson(url, GetConfig.class);
    }

    @Override
    public boolean setSessionConfig(SetConfig config) throws IOException {
        String url = getUrl(String.format("sessions/%s/options", sessionId));
        return WebUtils.putJson(url, config);
    }

    @Override
    public LocationConfig getLocationConfig() throws IOException {
        String url = getUrl(String.format("sessions/%s/location", sessionId));
        return WebUtils.getJson(url, LocationConfig.class);
    }

    @Override
    public boolean setLocationConfig(LocationConfig config) throws IOException {
        String url = getUrl(String.format("sessions/%s/location", sessionId));
        return WebUtils.putJson(url, config);
    }

    @Override
    public boolean createNode(CoreNode node) throws IOException {
        String url = getUrl(String.format("sessions/%s/nodes", sessionId));
        return WebUtils.postJson(url, node);
    }

    @Override
    public boolean editNode(CoreNode node) throws IOException {
        String url = getUrl(String.format("sessions/%s/nodes/%s", sessionId, node.getId()));
        return WebUtils.putJson(url, node);
    }

    @Override
    public boolean deleteNode(CoreNode node) throws IOException {
        String url = getUrl(String.format("/sessions/%s/nodes/%s", sessionId, node.getId()));
        return WebUtils.delete(url);
    }

    @Override
    public boolean createLink(CoreLink link) throws IOException {
        String url = getUrl(String.format("sessions/%s/links", sessionId));
        return WebUtils.postJson(url, link);
    }

    @Override
    public boolean editLink(CoreLink link) throws IOException {
        String url = getUrl(String.format("sessions/%s/links", sessionId));
        return WebUtils.putJson(url, link);
    }

    @Override
    public boolean createHook(Hook hook) throws IOException {
        String url = getUrl(String.format("sessions/%s/hooks", sessionId));
        return WebUtils.postJson(url, hook);
    }

    @Override
    public GetHooks getHooks() throws IOException {
        String url = getUrl(String.format("sessions/%s/hooks", sessionId));
        return WebUtils.getJson(url, GetHooks.class);
    }

    @Override
    public WlanConfig getWlanConfig(CoreNode node) throws IOException {
        String url = getUrl(String.format("sessions/%s/nodes/%s/wlan", sessionId, node.getId()));
        return WebUtils.getJson(url, WlanConfig.class);
    }

    @Override
    public boolean setWlanConfig(CoreNode node, WlanConfig config) throws IOException {
        String url = getUrl(String.format("sessions/%s/nodes/%s/wlan", sessionId, node.getId()));
        return WebUtils.putJson(url, config);
    }

    @Override
    public String getTerminalCommand(CoreNode node) throws IOException {
        String url = getUrl(String.format("sessions/%s/nodes/%s/terminal", sessionId, node.getId()));
        return WebUtils.getJson(url, String.class);
    }

    @Override
    public boolean setMobilityConfig(CoreNode node, MobilityConfig config) throws IOException {
        boolean uploaded = uploadFile(config.getScriptFile());
        if (!uploaded) {
            throw new IOException("failed to upload mobility script");
        }

        String url = getUrl(String.format("sessions/%s/nodes/%s/mobility", sessionId, node.getId()));
        config.setFile(config.getScriptFile().getName());
        return WebUtils.postJson(url, config);
    }

    @Override
    public GetMobilityConfigs getMobilityConfigs() throws IOException {
        String url = getUrl(String.format("sessions/%s/mobility/configs", sessionId));
        return WebUtils.getJson(url, GetMobilityConfigs.class);
    }

    @Override
    public MobilityConfig getMobilityConfig(CoreNode node) throws IOException {
        String url = getUrl(String.format("sessions/%s/nodes/%s/mobility", sessionId, node.getId()));
        return WebUtils.getJson(url, MobilityConfig.class);
    }

    @Override
    public boolean mobilityAction(CoreNode node, String action) throws IOException {
        String url = getUrl(String.format("sessions/%s/nodes/%s/mobility/%s", sessionId, node.getId(), action));
        return WebUtils.putJson(url);
    }
}
