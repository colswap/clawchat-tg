package com.clawchat.gateway;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks running/completed/errored subagents by listening to {@link GatewayClient} events
 * with prefix {@code subagent.*}.
 *
 * <p>Owned by Agent E (feat/subagent-panel).
 *
 * <h3>Assumed payload shapes</h3>
 * <ul>
 *   <li>{@code subagent.started}  &rarr; {@code {"agentId":"...","label":"..."}}</li>
 *   <li>{@code subagent.completed} &rarr; {@code {"agentId":"..."}}</li>
 *   <li>{@code subagent.error}    &rarr; {@code {"agentId":"...","message":"..."}}</li>
 * </ul>
 * Missing fields are tolerated; {@code agentId} is required (event is dropped if absent).
 * Unknown {@code subagent.*} subtypes are ignored.
 *
 * <p>All listener callbacks are dispatched on the Android main thread.
 */
public final class SubagentManager {

    public interface Listener {
        void onSubagentStatesChanged();
    }

    private final GatewayClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, SubagentState> states = new LinkedHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private final GatewayClient.EventListener eventListener = new GatewayClient.EventListener() {
        @Override
        public void onEvent(com.clawchat.gateway.model.GatewayEvent event) {
            if (event == null || event.event == null) return;
            if (!event.event.startsWith("subagent.")) return;
            handleEvent(event.event, event.payload);
        }
    };

    public SubagentManager(GatewayClient client) {
        this.client = client;
        if (client != null) {
            client.addEventListener(eventListener);
        }
    }

    /** Detach from the gateway. */
    public void dispose() {
        if (client != null) {
            client.removeEventListener(eventListener);
        }
    }

    private void handleEvent(String type, JSONObject payload) {
        if (payload == null) return;
        final String agentId = payload.optString("agentId", null);
        if (agentId == null || agentId.length() == 0) return;

        synchronized (states) {
            SubagentState s = states.get(agentId);
            if ("subagent.started".equals(type)) {
                if (s == null) {
                    s = new SubagentState();
                    s.agentId = agentId;
                    s.startedAtMs = System.currentTimeMillis();
                    states.put(agentId, s);
                }
                s.label = payload.optString("label", s.label != null ? s.label : agentId);
                s.status = SubagentState.Status.RUNNING;
                s.message = null;
            } else if ("subagent.completed".equals(type)) {
                if (s == null) {
                    s = new SubagentState();
                    s.agentId = agentId;
                    s.label = agentId;
                    s.startedAtMs = System.currentTimeMillis();
                    states.put(agentId, s);
                }
                s.status = SubagentState.Status.COMPLETED;
            } else if ("subagent.error".equals(type)) {
                if (s == null) {
                    s = new SubagentState();
                    s.agentId = agentId;
                    s.label = agentId;
                    s.startedAtMs = System.currentTimeMillis();
                    states.put(agentId, s);
                }
                s.status = SubagentState.Status.ERROR;
                s.message = payload.optString("message", null);
            } else {
                return; // unknown subtype
            }
        }
        dispatchChanged();
    }

    private void dispatchChanged() {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                for (Listener l : listeners) {
                    try { l.onSubagentStatesChanged(); } catch (Throwable ignored) {}
                }
            }
        });
    }

    /** Snapshot of all known subagent states (insertion order). */
    public List<SubagentState> getStates() {
        synchronized (states) {
            return Collections.unmodifiableList(new ArrayList<SubagentState>(states.values()));
        }
    }

    /** Count of currently-running subagents. */
    public int runningCount() {
        int n = 0;
        synchronized (states) {
            Collection<SubagentState> vs = states.values();
            for (SubagentState s : vs) {
                if (s.status == SubagentState.Status.RUNNING) n++;
            }
        }
        return n;
    }

    /** Clear all state (e.g. on session switch). */
    public void clear() {
        synchronized (states) { states.clear(); }
        dispatchChanged();
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }
}
