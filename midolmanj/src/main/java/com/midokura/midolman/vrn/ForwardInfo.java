/*
 * Copyright 2012 Midokura Pte. Ltd.
 */

package com.midokura.midolman.vrn;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.midokura.packets.Ethernet;
import com.midokura.packets.IPv4;
import com.midokura.packets.TCP;
import com.midokura.packets.UDP;
import com.midokura.midolman.rules.ChainPacketContext;
import com.midokura.cache.Cache;
import com.midokura.midolman.util.Net;
import com.midokura.sdn.flows.WildcardMatch;
import com.midokura.util.functors.Callback0;


/* VRNController creates and partially populate an instance of
 * ForwardInfo to call ForwardingElement.process(fInfo).  The
 * ForwardingElement populates a number of fields to indicate various
 * decisions:  the next action for the packet, the egress port,
 * the packet at egress (i.e. after possible modifications).
 */
public class ForwardInfo implements ChainPacketContext {

    // These fields are filled by the caller of ForwardingElement.process():
    public UUID inPortId;
    public Ethernet pktIn;
    public WildcardMatch flowMatch; // (original) match of any eventual flows
    public WildcardMatch matchIn; // the match as it enters the ForwardingElement
    public Set<UUID> portGroups = new HashSet<UUID>();
    public boolean internallyGenerated = false;

    public enum Action {
        DROP,
        NOT_IPV4,
        FORWARD,
        CONSUMED,
        PAUSED,
    }

    // These fields are filled by ForwardingElement.process():
    public Action action;
    public UUID outPortId;
    public WildcardMatch matchOut; // the match as it exits the ForwardingElement

    // Used by FEs that want notification when the flow is removed.
    private Collection<UUID> notifyFEs = new HashSet<UUID>();
    // Used by the VRNCoordinator to detect loops.
    private Map<UUID, Integer> traversedFEs = new HashMap<UUID, Integer>();
    // Used for coarse invalidation. If any element in this set changes
    // there's a chance the flow is no longer correct. Elements can be
    // Routers, Bridges, Ports and Chains.
    private Set<Object> flowTags = new HashSet<Object>();
    public int depth = 0;  // depth in the VRN simulation

    // Used for connection tracking.
    private boolean connectionTracked = false;
    private boolean forwardFlow;
    private Cache connectionCache;
    private UUID ingressFE;

    public ForwardInfo(boolean internallyGenerated, Cache c, UUID ingressFE) {
        connectionCache = c;
        this.ingressFE = ingressFE;
        this.internallyGenerated = internallyGenerated;
    }

    @Override
    public String toString() {
        return "ForwardInfo [inPortId=" + inPortId +
               ", pktIn=" + pktIn + ", matchIn=" + matchIn +
               ", action=" + action + ", outPortId=" + outPortId +
               ", matchOut=" + matchOut + ", depth=" + depth + "]";
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    @Override
    public boolean isConnTracked() {
        return connectionTracked;
    }

    @Override
    public boolean isForwardFlow() {
        // Connection tracking:  isConnTracked starts out as false.
        // If isForwardFlow is called, isConnTracked becomes true and
        // a lookup into Cassandra determines which direction this packet
        // is considered to be going.
        if (connectionTracked)
            return forwardFlow;

        // Generated packets are always return & not connection tracked.
        if (internallyGenerated)
            return false;

        // TODO(jlm): Currently ICMP Unreachables from the external network
        // won't get through a return-flow-only filter.  How should we
        // handle these?  Manually simulate every external ICMP, and have
        // ICMPs shunted to their own queue to mitigate ICMP floods?

        // Non-IPv4 and and non-(TCP or UDP) packets aren't connection
        // tracked, always treated as forward flows.
        if (flowMatch.getDataLayerType() != IPv4.ETHERTYPE ||
                (flowMatch.getNetworkProtocol() != TCP.PROTOCOL_NUMBER &&
                 flowMatch.getNetworkProtocol() != UDP.PROTOCOL_NUMBER)) {
            return true;
        }

        connectionTracked = true;
        // Query connectionCache.
        String key = connectionKey(flowMatch.getNetworkSource(),
                                   flowMatch.getTransportSource(),
                                   flowMatch.getNetworkDestination(),
                                   flowMatch.getTransportDestination(),
                                   flowMatch.getNetworkProtocol(),
                                   ingressFE);
        String value = connectionCache.get(key);
        forwardFlow = !("r".equals(value));
        return forwardFlow;
    }

    @Override
    public Object getFlowCookie() {
        return flowMatch;
    }

    public static String connectionKey(int ip1, int port1, int ip2,
                                       int port2, short proto, UUID fe) {
        return new StringBuilder(Net.convertIntAddressToString(ip1))
                             .append('|').append(port1).append('|')
                             .append(Net.convertIntAddressToString(ip2))
                             .append('|').append(port2).append('|')
                             .append(proto).append('|')
                             .append(fe.toString()).toString();
    }

    @Override
    public Set<UUID> getPortGroups() {
        return portGroups;
    }

    public void addTraversedFE(UUID deviceId) {
        Integer numTraversed = traversedFEs.get(deviceId);
        if (null == numTraversed)
            traversedFEs.put(deviceId, 1);
        else
            traversedFEs.put(deviceId, numTraversed+1);
    }

    public int getTimesTraversed(UUID deviceId) {
        Integer numTraversed = traversedFEs.get(deviceId);
        return null == numTraversed ? 0 : numTraversed;
    }

    public int getNumFEsTraversed() {
        return traversedFEs.size();
    }

    public Collection<UUID> getNotifiedFEs() {
        return notifyFEs;
    }

    public void addRemovalNotification(UUID deviceId) {
        notifyFEs.add(deviceId);
    }

    public void addTraversedElementID(UUID id) {
        flowTags.add(id);
    }

    public boolean isGeneratedPacket() {
        return internallyGenerated;
    }

    public UUID getInPortId() {
        return inPortId;
    }

    public void setInPortId(UUID inPortId) {
        this.inPortId = inPortId;
    }

    public WildcardMatch getMatchIn() {
        return matchIn;
    }

    public void setMatchIn(WildcardMatch matchIn) {
        this.matchIn = matchIn;
    }

    public WildcardMatch getMatchOut() {
        return matchOut;
    }

    public void setMatchOut(WildcardMatch matchOut) {
        this.matchOut = matchOut;
    }

    @Override
    public UUID getOutPortId() {
        return outPortId;
    }

    public void setOutPortId(UUID outPortId) {
        this.outPortId = outPortId;
    }

    public Ethernet getPktIn() {
        return pktIn;
    }

    @Override
    public void addFlowTag(Object tag) {
        flowTags.add(tag);
    }

    @Override
    public void addFlowRemovedCallback(Callback0 cb) {
        // XXX(guillermo) do nothing, this class is unused outside of tests
        // and going away. Right?
    }
}
