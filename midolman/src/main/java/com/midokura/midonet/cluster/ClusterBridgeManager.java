/*
 * Copyright 2012 Midokura Pte. Ltd.
 */

package com.midokura.midonet.cluster;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;

import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.midokura.midolman.config.ZookeeperConfig;
import com.midokura.midolman.state.Directory;
import com.midokura.midolman.state.MacPortMap;
import com.midokura.midolman.state.PortDirectory;
import com.midokura.midolman.state.ReplicatedMap;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.midolman.state.ZkPathManager;
import com.midokura.midolman.state.zkManagers.BridgeZkManager;
import com.midokura.midolman.state.zkManagers.PortZkManager;
import com.midokura.midonet.cluster.client.BridgeBuilder;
import com.midokura.midonet.cluster.client.MacLearningTable;
import com.midokura.packets.IntIPv4;
import com.midokura.packets.MAC;
import com.midokura.util.functors.Callback1;
import com.midokura.util.functors.Callback3;

public class ClusterBridgeManager extends ClusterManager<BridgeBuilder>{

    @Inject
    BridgeZkManager bridgeMgr;

    @Inject
    ZookeeperConfig zkConfig;

    @Inject
    Directory dir;

    @Inject
    PortZkManager portMgr;

    @Inject
    ClusterPortsManager portsManager;

    private static final Logger log = LoggerFactory
        .getLogger(ClusterBridgeManager.class);

    @Override
    protected void getConfig(UUID id) {
        getBridgeConf(id, false);
    }

    void getBridgeConf(final UUID id, final boolean isUpdate) {
        log.info("Updating configuration for bridge {}", id);
        BridgeBuilder builder = getBuilder(id);
        if(builder == null){
            log.error("Null builder for bridge {}", id.toString());
            return;
        }

        BridgeZkManager.BridgeConfig config = null;

        MacPortMap macPortMap = null;
        try {
            // we don't need to get the macPortMap again if it's an
            // update nor to create the logical port table.
            // For detecting changes to these maps we did set watchers.
            if (!isUpdate) {
                ZkPathManager pathManager = new ZkPathManager(
                        zkConfig.getMidolmanRootKey());
                macPortMap = new MacPortMap(dir.getSubDirectory(
                        pathManager.getBridgeMacPortsPath(id)));
                macPortMap.setConnectionWatcher(connectionWatcher);

                macPortMap.start();
                builder.setMacLearningTable(
                        new MacLearningTableImpl(id, macPortMap));
                updateLogicalPorts(builder, id, false);

            }

            /* NOTE(guillermo) this the last zk-related call in this block
             * so that the watcher is not added in an undefined state.
             * We would not want to add the watcher (with update=true) and
             * then find that the ZK calls for the rest of the data fail. */
            config = bridgeMgr.get(id, watchBridge(id, true));
        } catch (StateAccessException e) {
            log.warn("Cannot retrieve the configuration for bridge {}", id, e);
            connectionWatcher.handleError(
                    id.toString(), watchBridge(id, isUpdate), e);
            return;
        } catch (KeeperException e) {
            log.warn("Cannot retrieve the configuration for bridge {}", id, e);
            connectionWatcher.handleError(
                    id.toString(), watchBridge(id, isUpdate), e);
            return;
        }

        if (config == null) {
            log.warn("Received null bridge config for {}", id);
            return;
        }

        log.debug("Populating builder for bridge {}", id);
        builder.setInFilter(config.inboundFilter)
                .setOutFilter(config.outboundFilter);
        builder.setTunnelKey(config.tunnelKey);
        builder.build();
        log.info("Added watcher for bridge {}", id);
    }

    Runnable watchBridge(final UUID id, final boolean isUpdate) {
        return new Runnable() {
            @Override
            public void run() {
                // return fast and update later
                getBridgeConf(id, isUpdate);
            }
        };
    }

    void updateLogicalPorts(BridgeBuilder builder, UUID bridgeId, boolean isUpdate)
            throws StateAccessException {

        // This implementation won't keep the old tables and compute a diff
        // on the contrary it will create new tables every time
        // and pass them to the builder
        Map<MAC, UUID> rtrMacToLogicalPortId = new HashMap<MAC, UUID>();
        Map<IntIPv4, MAC> rtrIpToMac =  new HashMap<IntIPv4, MAC>();
        Set<UUID> logicalPortIDs;
        LogicalPortWatcher watcher = new LogicalPortWatcher(bridgeId, builder);
        try {
            logicalPortIDs = portMgr.getBridgeLogicalPortIDs(bridgeId, watcher);
        } catch (StateAccessException e) {
            log.error("Failed to retrieve the logical port IDs for bridge {}",
                      bridgeId);
            throw e;
        }

        for (UUID id : logicalPortIDs) {
            log.debug("Found logical port {}", id);
            // TODO(rossella) consider keeping in memory the old ports list so that
            // the watcher can consider just the port whose configuration changed,
            // not the whole list.

            // Find the peer of the new logical port.
            PortDirectory.LogicalBridgePortConfig bridgePort =
                portsManager.getPortConfigAndRegisterWatcher(
                    id, PortDirectory.LogicalBridgePortConfig.class, watcher);
            if (null == bridgePort) {
                log.error("Failed to find the logical bridge port's config {}",
                          id);
                continue;
            }
            // Ignore dangling ports.
            if (null == bridgePort.peerId()) {
                continue;
            }
            PortDirectory.LogicalRouterPortConfig routerPort =
                portsManager.getPortConfigAndRegisterWatcher(
                    bridgePort.peerId(),
                    PortDirectory.LogicalRouterPortConfig.class,
                    watcher);

            if (null == routerPort) {
                log.error("Failed to get the config for the bridge's peer {}",
                          bridgePort);
                continue;
            }
            // 'Learn' that the router's mac is reachable via the bridge port.
            rtrMacToLogicalPortId.put(routerPort.getHwAddr(), id);
            // Add the router port's IP and MAC to the permanent ARP map.
            IntIPv4 rtrPortIp = new IntIPv4(routerPort.portAddr);
            rtrIpToMac.put(rtrPortIp, routerPort.getHwAddr());

            log.debug("added bridge port {} " +
                          "connected to router port with MAC:{} and IP:{}",
                      new Object[]{id, routerPort.getHwAddr(), rtrPortIp});
        }
        builder.setLogicalPortsMap(rtrMacToLogicalPortId, rtrIpToMac);
        // Trigger the update, this method was called by a watcher, because
        // something changed in the LogicalPortMap, so deliver the new maps.
        if(isUpdate)
            builder.build();

    }

    class MacLearningTableImpl implements MacLearningTable {

        MacPortMap map;
        UUID bridgeID;

        MacLearningTableImpl(UUID bridgeID, MacPortMap map) {
            this.bridgeID = bridgeID;
            this.map = map;
        }

        @Override
        public void get(final MAC mac, final Callback1<UUID> cb,
                        final Long expirationTime) {
            // It's ok to do a synchronous get on the map because it only
            // queries local state (doesn't go remote like the other calls.
            cb.call(map.get(mac));
        }

        @Override
        public void add(final MAC mac, final UUID portID) {

            reactorLoop.submit(new Runnable() {

                @Override
                public void run() {
                    try {
                        map.put(mac, portID);
                    } catch (Exception e) {
                        log.error("Failed adding mac {} to port {}",
                                  new Object[]{mac, portID, e});
                    }
                    log.info("Added mac {} to port {} for bridge {}",
                             new Object[]{mac, portID, bridgeID});
                }
            });
        }

        @Override
        public void remove(final MAC mac, final UUID portID) {
            reactorLoop.submit(new Runnable() {

                @Override
                public void run() {
                    try {
                        map.removeIfOwner(mac);
                    } catch (Exception e) {
                        log.error("Failed removing mac {} from port {}",
                                  new Object[]{mac, portID, e});
                    }
                }
            });

        }

        // This notify() registers its callback directly with the underlying
        // MacPortMap map, so the callbacks are called from MacPortMap context
        // and should perform ActorRef::tell or such to switch to the context
        // appropriate for the callback's work.
        @Override
        public void notify(final Callback3<MAC, UUID, UUID> cb) {
            reactorLoop.submit(new Runnable() {

                @Override
                public void run() {
                    map.addWatcher(new ReplicatedMap.Watcher<MAC, UUID>() {
                        @Override
                        public void processChange(MAC key, UUID oldValue,
                                                  UUID newValue) {
                            cb.call(key, oldValue, newValue);
                        }
                    });
                }
            });

        }
    }

    class LogicalPortWatcher implements Runnable {
        UUID bridgeID;
        BridgeBuilder builder;

        LogicalPortWatcher(UUID bridgeID, BridgeBuilder builder) {
            this.bridgeID = bridgeID;
            this.builder = builder;
        }

        public String describe() {
            return "BridgeLogicalPorts:" + bridgeID;
        }

        public void run() {
            try {
                updateLogicalPorts(builder, bridgeID, true);
            } catch (StateAccessException e) {
                connectionWatcher.handleError(describe(), this, e);
            }
        }
    }

}
