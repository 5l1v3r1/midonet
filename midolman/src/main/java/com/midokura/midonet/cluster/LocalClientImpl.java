/*
* Copyright 2012 Midokura Europe SARL
*/
package com.midokura.midonet.cluster;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Named;

import com.midokura.midolman.state.*;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.midokura.midolman.guice.zookeeper.ZKConnectionProvider;
import com.midokura.midolman.host.state.HostDirectory;
import com.midokura.midolman.host.state.HostZkManager;
import com.midokura.midolman.state.zkManagers.PortSetZkManager;
import com.midokura.midolman.state.zkManagers.TunnelZoneZkManager;
import com.midokura.midonet.cluster.client.BGPListBuilder;
import com.midokura.midonet.cluster.client.BridgeBuilder;
import com.midokura.midonet.cluster.client.ChainBuilder;
import com.midokura.midonet.cluster.client.HostBuilder;
import com.midokura.midonet.cluster.client.PortBuilder;
import com.midokura.midonet.cluster.client.PortSetBuilder;
import com.midokura.midonet.cluster.client.RouterBuilder;
import com.midokura.midonet.cluster.client.TunnelZones;
import com.midokura.midonet.cluster.data.TunnelZone;
import com.midokura.midonet.cluster.data.zones.GreTunnelZone;
import com.midokura.midonet.cluster.data.zones.GreTunnelZoneHost;
import com.midokura.midonet.cluster.data.zones.CapwapTunnelZone;
import com.midokura.midonet.cluster.data.zones.CapwapTunnelZoneHost;
import com.midokura.util.eventloop.Reactor;

import static com.midokura.midonet.cluster.client.TunnelZones.GreBuilder;
import static com.midokura.midonet.cluster.client.TunnelZones.CapwapBuilder;

/**
 * Implementation of the Cluster.Client using ZooKeeper
 * Assumption:
 * - No cache, the caller of this class will have to implement its own cache
 * - Only one builder for UUID is allowed
 * - Right now it's single-threaded, we don't assure a thread-safe behaviour
 */
public class LocalClientImpl implements Client {

    private static final Logger log = LoggerFactory
            .getLogger(LocalClientImpl.class);

    @Inject
    HostZkManager hostManager;

    @Inject
    ClusterBgpManager bgpManager;

    @Inject
    ClusterChainManager chainManager;

    @Inject
    TunnelZoneZkManager tunnelZoneZkManager;

    @Inject
    PortSetZkManager portSetZkManager;

    @Inject
    ClusterRouterManager routerManager;

    @Inject
    ClusterBridgeManager bridgeManager;

    @Inject
    ClusterPortsManager portsManager;

    @Inject
    ZkConnectionAwareWatcher connectionWatcher;

    /**
     * We inject it because we want to use the same {@link Reactor} as {@link ZkDirectory}
     */
    @Inject
    @Named(ZKConnectionProvider.DIRECTORY_REACTOR_TAG)
    Reactor reactorLoop;

    @Override
    public void getBridge(UUID bridgeID, BridgeBuilder builder) {
        bridgeManager.registerNewBuilder(bridgeID, builder);
        log.debug("getBridge {}", bridgeID);
    }

    @Override
    public void getRouter(UUID routerID, RouterBuilder builder) {
        routerManager.registerNewBuilder(routerID, builder);
        log.debug("getRouter {}", routerID);
    }

    @Override
    public void getChain(UUID chainID, ChainBuilder builder) {
        chainManager.registerNewBuilder(chainID, builder);
        log.debug("getChain {}", chainID);
    }

    @Override
    public void getPort(UUID portID, PortBuilder builder) {
        portsManager.registerNewBuilder(portID, builder);
        log.debug("getPort {}", portID);
    }

    @Override
    public void getHost(final UUID hostID, final HostBuilder builder) {
        reactorLoop.submit(new Runnable() {
            @Override
            public void run() {
                getHostConfig(hostID, builder, false);
            }
        });
    }

    @Override
    public void getTunnelZones(final UUID zoneID, final TunnelZones.BuildersProvider builders) {
        reactorLoop.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    TunnelZone<?, ?> zone = readTunnelZone(zoneID, builders);
                    readHosts(zone,
                            new HashMap<UUID, TunnelZone.HostConfig<?, ?>>(),
                            builders);

                } catch (StateAccessException e) {
                    connectionWatcher.handleError(
                            "TunnelZone:" + zoneID.toString(), this, e);
                }
            }
        });
    }

    @Override
    public void getPortSet(final UUID uuid, final PortSetBuilder builder) {
        portSetZkManager.getPortSetAsync(
                uuid,
                new DirectoryCallback<Set<UUID>>() {
                    @Override
                    public void onSuccess(Result<Set<UUID>> result) {
                        builder.setHosts(result.getData()).build();
                    }

                    @Override
                    public void onTimeout() {
                        connectionWatcher.handleTimeout(makeRetry());
                    }

                    @Override
                    public void onError(KeeperException e) {
                        connectionWatcher.handleError(
                                "PortSet:" + uuid, makeRetry(), e);
                    }

                    private Runnable makeRetry() {
                        return new Runnable() {
                            @Override
                            public void run() {
                                getPortSet(uuid, builder);
                            }
                        };
                    }
                },
                new Directory.DefaultTypedWatcher() {
                    @Override
                    public void pathChildrenUpdated(String path) {
                        getPortSet(uuid, builder);
                    }
                }
        );
    }

    @Override
    public void subscribeBgp(UUID portID, BGPListBuilder builder) {
        log.debug("subscribing port {} for BGP updates", portID);
        bgpManager.registerNewBuilder(portID, builder);
    }

    private void readHosts(final TunnelZone<?, ?> zone,
                           final Map<UUID, TunnelZone.HostConfig<?, ?>> zoneHosts,
                           final TunnelZones.BuildersProvider builders) {

        Set<UUID> currentList = null;

        try {
            currentList =
                    tunnelZoneZkManager.getZoneMemberships(zone.getId(),
                            new Directory.DefaultTypedWatcher() {
                                @Override
                                public void pathChildrenUpdated(String path) {
                                    readHosts(
                                            zone,
                                            zoneHosts,
                                            builders);
                                }
                            });
        } catch (StateAccessException e) {
            log.error("Exception while reading hosts", e);
            Runnable retry = new Runnable() {
                @Override
                public void run() {
                    readHosts(zone, zoneHosts, builders);
                }
            };
            connectionWatcher.handleError("HostsForZone:" + zone.getId(), retry, e);
            return;
        }

        try {
            Set<TunnelZone.HostConfig<?, ?>> newMemberships =
                    new HashSet<TunnelZone.HostConfig<?, ?>>();

            for (UUID uuid : currentList) {
                if (!zoneHosts.containsKey(uuid)) {
                    newMemberships.add(
                            tunnelZoneZkManager.getZoneMembership(
                                    zone.getId(), uuid, null)
                    );
                }
            }

            Set<UUID> removedMemberships = new HashSet<UUID>();
            for (UUID uuid : zoneHosts.keySet()) {
                if (!currentList.contains(uuid)) {
                    removedMemberships.add(uuid);
                }
            }

            for (TunnelZone.HostConfig<?, ?> newHost : newMemberships) {
                triggerZoneMembershipChange(zone, newHost, builders, true);
                zoneHosts.put(newHost.getId(), newHost);
            }

            for (UUID removedHost : removedMemberships) {
                triggerZoneMembershipChange(zone, zoneHosts.get(removedHost),
                        builders, false);
                zoneHosts.remove(removedHost);
            }

        } catch (StateAccessException e) {
            // XXX(guillermo) I don't see how this block could be retried
            // without racing with the watcher installed by the first try{}
            // block or blocking the reactor by retrying in a loop right here.
            // The latter solution would only apply for timeout errors, not
            // disconnections.
            //
            // For now, this error is left unhandled.
            log.error("Exception while reading hosts", e);
        }
    }

    private void triggerZoneMembershipChange(TunnelZone<?, ?> zone,
                                             TunnelZone.HostConfig<?, ?> hostConfig,
                                             TunnelZones.BuildersProvider buildersProvider,
                                             boolean added) {
        switch (zone.getType()) {
            case Gre:
                if (hostConfig instanceof GreTunnelZoneHost) {
                    GreTunnelZoneHost greConfig = (GreTunnelZoneHost) hostConfig;

                    if (added) {
                        buildersProvider
                                .getGreZoneBuilder()
                                .addHost(greConfig.getId(), greConfig);
                    } else {
                        buildersProvider
                                .getGreZoneBuilder()
                                .removeHost(greConfig.getId(), greConfig);
                    }
                }
                break;

            case Capwap:
                if (hostConfig instanceof CapwapTunnelZoneHost) {
                    CapwapTunnelZoneHost config = (CapwapTunnelZoneHost) hostConfig;

                    if (added) {
                        buildersProvider
                                .getCapwapZoneBuilder()
                                .addHost(config.getId(), config);
                    } else {
                        buildersProvider
                                .getCapwapZoneBuilder()
                                .removeHost(config.getId(), config);
                    }
                }
                break;

            case Ipsec:
        }
    }

    private TunnelZone<?, ?> readTunnelZone(final UUID zoneID,
            final TunnelZones.BuildersProvider builders) throws StateAccessException {

        TunnelZone<?, ?> zone;
        try {
            zone = tunnelZoneZkManager.getZone(
                    zoneID,
                    new Directory.DefaultPersistentWatcher(connectionWatcher) {
                        @Override
                        public void pathDataChanged(String path) {
                            run();
                        }

                        @Override
                        public String describe() {
                            return "TunnelZone:" + zoneID.toString();
                        }

                        @Override
                        public void _run() throws StateAccessException {
                            readTunnelZone(zoneID, builders);
                        }
                    });
        } catch (StateAccessException e) {
            log.warn("Exception retrieving availability zone with id: {}",
                    zoneID, e);
            throw e;
        }

        if (zone instanceof GreTunnelZone) {
            final GreTunnelZone greZone = (GreTunnelZone) zone;
            builders.getGreZoneBuilder()
                    .setConfiguration(
                            new GreBuilder.ZoneConfig() {
                        @Override
                        public GreTunnelZone getTunnelZoneConfig() {
                                    return greZone;
                            }
                        });
        } else if (zone instanceof CapwapTunnelZone) {
            final CapwapTunnelZone capwapZone = (CapwapTunnelZone) zone;
            builders.getCapwapZoneBuilder()
                    .setConfiguration(
                            new CapwapBuilder.ZoneConfig() {
                        @Override
                        public CapwapTunnelZone getTunnelZoneConfig() {
                                    return capwapZone;
                            }
                        });
        }

        return zone;
    }

    private HostDirectory.Metadata retrieveHostMetadata(final UUID hostId,
            final HostBuilder builder, final boolean isUpdate) throws StateAccessException {
        try {
            HostDirectory.Metadata metadata = hostManager.getHostMetadata(hostId,
                    new Directory.DefaultPersistentWatcher(connectionWatcher) {
                        @Override
                        public void pathDataChanged(String path) {
                            run();
                        }

                        @Override
                        public void _run() throws StateAccessException {
                            retrieveHostMetadata(hostId, builder, true);
                        }

                        @Override
                        public String describe() {
                            return "HostMetadata:" + hostId.toString();
                        }

                        @Override
                        public void pathDeleted(String path) {
                            //builder.deleted();
                        }
                });

            if (isUpdate)
                builder.build();

            return metadata;
        } catch (StateAccessException e) {
            log.error("Exception: ", e);
            // trigger delete if that's the case.
            throw e;
        }
    }

    private String retrieveHostDatapathName(final UUID hostId,
                                            final HostBuilder builder,
                                            final boolean isUpdate) {
        try {
            String datapath =
                    hostManager.getVirtualDatapathMapping(
                            hostId, new Directory.DefaultTypedWatcher() {
                        @Override
                        public void pathDataChanged(String path) {
                            retrieveHostDatapathName(hostId, builder, true);
                        }
                    });

            builder.setDatapathName(datapath);

            if (isUpdate)
                builder.build();

            return datapath;
        } catch (StateAccessException e) {
            log.error("Exception: ", e);
            // TODO trigger delete if that's the case.
            Runnable retry = new Runnable() {
                @Override
                public void run() {
                    retrieveHostDatapathName(hostId, builder, isUpdate);
                }
            };

            connectionWatcher.handleError(
                    "HostDatapathName" + hostId.toString(), retry, e);
            return null;
        }
    }

    private Set<HostDirectory.VirtualPortMapping>
    retrieveHostVirtualPortMappings(final UUID hostId,
                                    final HostBuilder builder,
                                    final Set<HostDirectory.VirtualPortMapping> oldMappings,
                                    boolean isUpdate) {
        Set<HostDirectory.VirtualPortMapping> newMappings = null;
        try {
            // We are running in the same thread as the one which is going to
            // run the watcher below. This implies the following set of
            // constraints (and assumptions) on the execution flow:
            //   - we want to store the old mappings and pass (via the Watcher)
            //      to the next call so it can do the diff
            //   - the processing of this method is running on the same thread
            //      as the one that will call the watcher (which means the calls
            //      to this method triggered by a change in the list of port
            //      mappings will effectively be serialized)
            //   - the entry point of this method needs the old known state of
            //      the port mappings
            //   - the hostManager.getVirtualPortMappings() method can fail after
            //      installing the watcher and said watcher gets the newMappings
            //      reference which should contain the list of old mappings.
            //
            //  So make sure we are consistent in the face of intermittent
            //     failures to load a port mapping we will first install the
            //     oldMappings into the newMappings and only after we return
            //     successfully from the getVirtualPortMappings we have sent the
            //     updates to the callers
            newMappings =
                    hostManager
                            .getVirtualPortMappings(
                                    hostId, new Directory.DefaultTypedWatcher() {
                                @Override
                                public void pathChildrenUpdated(String path) {
                                    retrieveHostVirtualPortMappings(hostId, builder,
                                            oldMappings, true);
                                }
                            });
        } catch (StateAccessException e) {
            // TODO trigger delete if that's the case.

            Runnable retry = new Runnable() {
                @Override
                public void run() {
                    retrieveHostVirtualPortMappings(hostId, builder, oldMappings, true);
                }
            };
            connectionWatcher.handleError(
                    "HostVirtualPortMappings:" + hostId.toString(), retry, e);
            return null;
        }

        for (HostDirectory.VirtualPortMapping mapping : oldMappings) {
            if (!newMappings.contains(mapping)) {
                builder.delMaterializedPortMapping(
                        mapping.getVirtualPortId(),
                        mapping.getLocalDeviceName());
            }
        }
        for (HostDirectory.VirtualPortMapping mapping : newMappings) {
            if (!oldMappings.contains(mapping)) {
                builder.addMaterializedPortMapping(
                        mapping.getVirtualPortId(),
                        mapping.getLocalDeviceName()
                );
            }
        }

        if (isUpdate)
            builder.build();

        oldMappings.clear();
        oldMappings.addAll(newMappings);

        return newMappings;
    }

    private void getHostConfig(final UUID hostId,
                               final HostBuilder builder,
                               final boolean isUpdate) {

        log.info("Updating host information for host {}", hostId);

        HostDirectory.Metadata metadata;
        try {
            metadata = retrieveHostMetadata(hostId, builder, isUpdate);
        } catch (StateAccessException e) {
            Runnable retry = new Runnable() {
                @Override
                public void run() {
                    getHostConfig(hostId, builder, isUpdate);
                }
            };
            connectionWatcher.handleError(
                    "HostConfig:" + hostId.toString(), retry, e);
            return;
        }

        if (metadata != null) {
            retrieveTunnelZoneConfigs(hostId, new HashSet<UUID>(), builder);

            retrieveHostDatapathName(hostId, builder, isUpdate);

            retrieveHostVirtualPortMappings(
                    hostId, builder,
                    new HashSet<HostDirectory.VirtualPortMapping>(),
                    isUpdate);

            builder.build();
        }
    }

    private void retrieveTunnelZoneConfigs(final UUID hostId,
                                           final Set<UUID> oldZones,
                                           final HostBuilder builder) {
        Set<UUID> newZones = null;

        try {
            newZones =
                hostManager.getTunnelZoneIds(
                    hostId,
                    new Directory.DefaultTypedWatcher() {
                        @Override
                        public void pathChildrenUpdated(String path) {
                            retrieveTunnelZoneConfigs(hostId, oldZones,
                                                      builder);
                            builder.build();
                        }
                    });
        } catch (StateAccessException e) {
            log.error("Exception", e);
            Runnable retry = new Runnable() {
                @Override
                public void run() {
                    retrieveTunnelZoneConfigs(hostId, oldZones, builder);
                }
            };
            connectionWatcher.handleError(
                    "TunnelZoneConfigs:" + hostId.toString(), retry, e);
            return;
        }

        try {
            Map<UUID, TunnelZone.HostConfig<?, ?>> hostTunnelZones =
                    new HashMap<UUID, TunnelZone.HostConfig<?, ?>>();
            for (UUID uuid : newZones) {
                hostTunnelZones.put(
                        uuid,
                        tunnelZoneZkManager.getZoneMembership(uuid, hostId, null));
            }

            builder.setTunnelZones(hostTunnelZones);

            oldZones.clear();
            oldZones.addAll(newZones);

        } catch (StateAccessException e) {
            // XXX(guillermo) I don't see how this block could be retried
            // without racing with the watcher installed by the first try{}
            // block or blocking the reactor by retrying in a loop right here.
            // The latter solution would only apply for timeout errors, not
            // disconnections.
            //
            // For now, this error is left unhandled.
            log.error("Exception", e);
        }
    }
}
