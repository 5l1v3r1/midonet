/*
 * Copyright 2012 Midokura Europe SARL
 */
package com.midokura.midolman.host.state;

import com.midokura.midolman.state.*;
import com.midokura.midolman.state.zkManagers.PortZkManager;
import com.midokura.util.functors.CollectionFunctors;
import com.midokura.util.functors.Functor;
import org.apache.zookeeper.Op;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static com.midokura.midolman.host.state.HostDirectory.Command;

/**
 * Wrapper class over a Directory that handled setting and reading data related
 * to hosts and interfaces associated with the hosts.
 */
public class HostZkManager extends ZkManager {

    private final static Logger log =
        LoggerFactory.getLogger(HostZkManager.class);

    private final PortZkManager portZkManager;

    public HostZkManager(Directory zk, String basePath) {
        super(zk, basePath);
        this.portZkManager = new PortZkManager(zk, basePath);
    }

    public HostDirectory.Metadata getHostMetadata(UUID id)
        throws StateAccessException {
        return getHostMetadata(id, null);
    }

    public HostDirectory.Metadata getHostMetadata(UUID id, Directory.TypedWatcher watcher)
        throws StateAccessException {
        byte[] data = get(paths.getHostPath(id), watcher);
        HostDirectory.Metadata metadata;
        try {
            metadata = serializer.deserialize(data,
                                              HostDirectory.Metadata.class);
        } catch (ZkStateSerializationException e) {
            String dataAsString = new String(data);
            throw new ZkStateSerializationException(
                "Could not deserialize host metadata for id: " + id +
                    " [" + dataAsString + "]",
                e,
                HostDirectory.Metadata.class);
        }
        return metadata;
    }

    public void createHost(UUID hostId, HostDirectory.Metadata metadata)
        throws StateAccessException {
        log.debug("Creating host folders for hostId {}", hostId);

        try {
            List<Op> ops = new ArrayList<Op>();
            ops.add(
                getPersistentCreateOp(paths.getHostPath(hostId),
                                      serializer.serialize(metadata)));
            ops.add(
                getPersistentCreateOp(paths.getHostInterfacesPath(hostId),
                                      null));
            ops.add(
                getPersistentCreateOp(paths.getHostCommandsPath(hostId),
                                      null));
            ops.add(
                getPersistentCreateOp(
                    paths.getHostCommandErrorLogsPath(hostId), null));

            ops.add(
                getPersistentCreateOp(
                    paths.getHostVrnMappingsPath(hostId), null));

            ops.add(
                getPersistentCreateOp(
                    paths.getHostVrnPortMappingsPath(hostId), null));

            ops.add(
                getPersistentCreateOp(
                    paths.getHostTunnelZonesPath(hostId), null));

            for (UUID uuid : metadata.getTunnelZones()) {
                ops.add(
                    getPersistentCreateOp(
                        paths.getHostTunnelZonePath(hostId, uuid), null
                    ));
            }

            multi(ops);
        } catch (ZkStateSerializationException e) {
            throw new ZkStateSerializationException(
                "Could not serialize host metadata for id: " + hostId,
                e, HostDirectory.Metadata.class);
        }
    }

    public void makeAlive(UUID hostId) throws StateAccessException {
        makeAlive(hostId, null);
    }

    public void makeAlive(UUID hostId, HostDirectory.Metadata metadata)
        throws StateAccessException {
        addEphemeral(paths.getHostPath(hostId) + "/alive",
                     new byte[0]);
        updateMetadata(hostId, metadata);
    }

    public void updateMetadata(UUID hostId, HostDirectory.Metadata metadata)
        throws StateAccessException {
        if (metadata != null) {
            try {
                update(paths.getHostPath(hostId),
                       serializer.serialize(metadata));
            } catch (ZkStateSerializationException e) {
                throw new ZkStateSerializationException(
                    "Could not serialize host metadata for id: " + hostId,
                    e, HostDirectory.Metadata.class);
            }
        }
    }

    public boolean isAlive(UUID id) throws StateAccessException {
        return exists(paths.getHostPath(id) + "/alive");
    }

    public boolean hostExists(UUID id) throws StateAccessException {
        return exists(paths.getHostPath(id));
    }

    public void deleteHost(UUID id) throws StateAccessException {
        String hostEntryPath = paths.getHostPath(id);

        List<Op> delMulti = new ArrayList<Op>();

        if (exists(paths.getHostCommandsPath(id))) {
            delMulti.addAll(
                getRecursiveDeleteOps(paths.getHostCommandsPath(id)));
        }

        if (exists(paths.getHostCommandErrorLogsPath(id))) {
            delMulti.addAll(
                getRecursiveDeleteOps(
                    paths.getHostCommandErrorLogsPath(id)));
        }

        if (exists(paths.getHostInterfacesPath(id))) {
            delMulti.add(getDeleteOp(paths.getHostInterfacesPath(id)));
        }

        if (exists(paths.getHostVrnMappingsPath(id))) {
            delMulti.addAll(getRecursiveDeleteOps(
                    paths.getHostVrnMappingsPath(id)));
        }

        Set<UUID> tunnelZones = getTunnelZoneIds(id, null);
        for (UUID zoneId : tunnelZones) {
            delMulti.add(
                getDeleteOp(
                    paths.getHostTunnelZonePath(id, zoneId)));

            delMulti.add(
                getDeleteOp(
                    paths.getTunnelZoneMembershipPath(zoneId, id)));
        }

        if (exists(paths.getHostTunnelZonesPath(id))) {
            delMulti.add(
                getDeleteOp(paths.getHostTunnelZonesPath(id)));
        }

        delMulti.add(getDeleteOp(hostEntryPath));

        multi(delMulti);
    }

    public Set<UUID> getHostIds() throws StateAccessException {
        return getHostIds(null);
    }

    public Set<UUID> getHostIds(Directory.TypedWatcher watcher)
        throws StateAccessException {
        String path = paths.getHostsPath();
        Set<String> ids = getChildren(path, watcher);
        Set<UUID> uuids = new HashSet<UUID>();

        for (String id : ids) {
            uuids.add(UUID.fromString(id));
        }

        return uuids;
    }

    public Integer createHostCommandId(UUID hostId, Command command)
        throws StateAccessException {

        try {
            String path = addPersistentSequential(
                paths.getHostCommandsPath(hostId),
                serializer.serialize(command));

            int idx = path.lastIndexOf('/');
            return Integer.parseInt(path.substring(idx + 1));

        } catch (ZkStateSerializationException e) {
            throw new ZkStateSerializationException(
                "Could not serialize host command for id: " + hostId, e,
                Command.class);
        }
    }

    public boolean existsInterface(UUID hostId, String interfaceName)
        throws StateAccessException {
        return exists(paths.getHostInterfacePath(hostId,
                                                       interfaceName));
    }

    public void createInterface(UUID hostId,
                                HostDirectory.Interface anInterface)
        throws StateAccessException, IOException {

        List<Op> ops = new ArrayList<Op>();

        if (!exists(paths.getHostInterfacesPath(hostId))) {
            ops.add(getPersistentCreateOp(
                paths.getHostInterfacesPath(hostId), null));
        }
        ops.add(getEphemeralCreateOp(
            paths.getHostInterfacePath(hostId, anInterface.getName()),
            serializer.serialize(anInterface)));

        multi(ops);
    }

    public HostDirectory.Interface getInterfaceData(UUID hostId,
                                                    String name)
        throws StateAccessException {

        try {
            byte[] data = get(
                paths.getHostInterfacePath(hostId, name));

            return serializer.deserialize(data, HostDirectory.Interface.class);
        } catch (ZkStateSerializationException e) {
            throw new ZkStateSerializationException(
                "Could not deserialize host interface metadata for id: " +
                    hostId + " / " + name,
                e, HostDirectory.Metadata.class);
        }
    }

    public List<Integer> listCommandIds(UUID hostId, Runnable watcher)
        throws StateAccessException {
        List<Integer> result = new ArrayList<Integer>();

        String hostCommandsPath = paths.getHostCommandsPath(hostId);
        Set<String> commands = getChildren(hostCommandsPath, watcher);
        for (String commandId : commands) {
            try {
                result.add(Integer.parseInt(commandId));
            } catch (NumberFormatException e) {
                log.warn("HostCommand id is not a number: {} (for host: {}",
                         commandId, hostId);
            }
        }

        return result;
    }

    /**
     * Will return the collection of names for all the interfaces presently
     * described under the provided host entry.
     *
     * @param hostId the host id for which we want the interfaces
     * @return the collection of interface names
     * @throws StateAccessException if we weren't able to properly communicate
     *                              with the datastore.
     */
    public Set<String> getInterfaces(UUID hostId)
        throws StateAccessException {

        String path = paths.getHostInterfacesPath(hostId);
        if (!exists(path)) {
            return Collections.emptySet();
        }

        return getChildren(path);
    }

    public void updateHostInterfaces(UUID hostId,
                                     List<HostDirectory.Interface>
                                         currentInterfaces,
                                     Set<String> obsoleteInterfaces)
        throws StateAccessException {

        List<Op> updateInterfacesOperation = new ArrayList<Op>();

        for (String obsoleteInterface : obsoleteInterfaces) {
            updateInterfacesOperation.add(
                getDeleteOp(
                    paths.getHostInterfacePath(hostId,
                                                     obsoleteInterface)));
        }

        for (HostDirectory.Interface hostInterface : currentInterfaces) {
            try {

                String hostInterfacePath =
                    paths.getHostInterfacePath(hostId,
                                                     hostInterface.getName());
                byte[] serializedData = serializer.serialize(hostInterface);

                Op hostInterfaceOp;
                if (exists(hostInterfacePath)) {
                    hostInterfaceOp =
                        getSetDataOp(hostInterfacePath, serializedData);
                } else {
                    hostInterfaceOp =
                        getEphemeralCreateOp(hostInterfacePath, serializedData);
                }

                updateInterfacesOperation.add(hostInterfaceOp);

            } catch (ZkStateSerializationException ex) {
                log.warn("Could not serialize interface data {}.",
                         hostInterface, ex);
            }
        }

        if (updateInterfacesOperation.size() > 0) {
            multi(updateInterfacesOperation);
        }
    }

    public List<Integer> getCommandIds(UUID hostId)
        throws StateAccessException {

        Set<String> commandIdKeys = getChildren(
            paths.getHostCommandsPath(hostId));

        List<Integer> commandIds = new ArrayList<Integer>();
        for (String commandIdKey : commandIdKeys) {
            try {
                commandIds.add(Integer.parseInt(commandIdKey));
            } catch (NumberFormatException e) {
                log.warn("Command key could not be converted to a number: " +
                             "host {}, command {}", hostId, commandIdKey);
            }
        }

        Collections.sort(commandIds);

        return commandIds;
    }

    public Command getCommandData(UUID hostId, Integer commandId)
        throws StateAccessException {

        try {
            byte[] data = get(
                paths.getHostCommandPath(hostId, commandId));

            return serializer.deserialize(data, Command.class);
        } catch (ZkStateSerializationException e) {
            throw new ZkStateSerializationException(
                "Could not deserialize host command data id: " +
                    hostId + " / " + commandId, e, Command.class);
        }
    }

    public void deleteHostCommand(UUID hostId, Integer id)
        throws StateAccessException {

        String commandPath = paths.getHostCommandPath(hostId, id);

        List<Op> delete = getRecursiveDeleteOps(commandPath);

        multi(delete);
    }

    public void setCommandErrorLogEntry(UUID hostId,
                                        HostDirectory.ErrorLogItem errorLog)
        throws StateAccessException {
        String path = paths.getHostCommandErrorLogsPath(hostId) + "/"
            + String.format("%010d", errorLog.getCommandId());
        if (!(exists(path))) {
            addPersistent(path, null);
        }
        try {
            // Assign to the error log the same id of the command that generated it
            update(path, serializer.serialize(errorLog));

        } catch (ZkStateSerializationException e) {
            throw new ZkStateSerializationException(
                "Could not serialize host metadata for id: " + hostId,
                e, HostDirectory.Metadata.class);
        }
    }

    public HostDirectory.ErrorLogItem getErrorLogData(UUID hostId, Integer logId)
        throws StateAccessException {
        try {
            String errorItemPath =
                paths.getHostCommandErrorLogPath(hostId, logId);

            if (exists(errorItemPath)) {
                byte[] data = get(errorItemPath);
                return serializer.deserialize(data,
                                              HostDirectory.ErrorLogItem.class);
            }

            return null;
        } catch (ZkStateSerializationException e) {
            throw new ZkStateSerializationException(
                "Could not deserialize host error log data id: " +
                    hostId + " / " + logId, e,
                HostDirectory.ErrorLogItem.class);
        }
    }

    public List<Integer> listErrorLogIds(UUID hostId)
        throws StateAccessException {
        List<Integer> result = new ArrayList<Integer>();
        String hostLogsPath = paths.getHostCommandErrorLogsPath(hostId);
        Set<String> logs = getChildren(hostLogsPath, null);
        for (String logId : logs) {
            // For now, get each one.
            try {
                result.add(Integer.parseInt(logId));
            } catch (NumberFormatException e) {
                log.warn("ErrorLog id is not a number: {} (for host: {}",
                         logId, hostId);
            }
        }

        return result;
    }

    public String getVirtualDatapathMapping(UUID hostIdentifier, Runnable watcher)
        throws StateAccessException {

        String hostVrnDatapathMappingPath =
            paths.getHostVrnDatapathMappingPath(hostIdentifier);

        if (!exists(hostVrnDatapathMappingPath)) {
            addVirtualDatapathMapping(hostIdentifier, "midonet");
            return "midonet";
        }

        return serializer.deserialize(
            get(hostVrnDatapathMappingPath, watcher),
            String.class);
    }

    public boolean virtualPortMappingExists(UUID hostId, UUID portId)
            throws StateAccessException {
        return exists(paths.getHostVrnPortMappingPath(hostId, portId));
    }

    public HostDirectory.VirtualPortMapping getVirtualPortMapping(
            UUID hostId, UUID portId) throws StateAccessException {
        String path = paths.getHostVrnPortMappingPath(hostId, portId);
        if (!exists(path)) {
            return null;
        }

        return serializer.deserialize(get(path),
                HostDirectory.VirtualPortMapping.class);
    }

    public Set<HostDirectory.VirtualPortMapping> getVirtualPortMappings(
            UUID hostIdentifier, Directory.TypedWatcher watcher)
        throws StateAccessException {

        String virtualPortMappingPath =
            paths.getHostVrnPortMappingsPath(hostIdentifier);

        Set<HostDirectory.VirtualPortMapping> portMappings =
            new HashSet<HostDirectory.VirtualPortMapping>();

        if (exists(virtualPortMappingPath)) {
            Set<String> children = getChildren(virtualPortMappingPath, watcher);
            for (String child : children) {
                portMappings.add(
                    serializer.deserialize(
                        get(paths.getHostVrnPortMappingPath(
                            hostIdentifier,
                            UUID.fromString(child))),
                        HostDirectory.VirtualPortMapping.class
                    )
                );
            }
        }

        return portMappings;
    }

    public void addVirtualDatapathMapping(UUID hostIdentifier,
                                          String datapathMapping)
        throws StateAccessException {

        List<Op> operations = new ArrayList<Op>();

        if (!exists(paths.getHostPath(hostIdentifier)))
            operations.add(
                getPersistentCreateOp(paths.getHostPath(hostIdentifier),
                                      null));

        String virtualMappingPath =
            paths.getHostVrnMappingsPath(hostIdentifier);

        if (!exists(virtualMappingPath)) {
            operations.add(getPersistentCreateOp(virtualMappingPath, null));
        }

        String hostVrnDatapathMappingPath =
            paths.getHostVrnDatapathMappingPath(hostIdentifier);

        if (exists(hostVrnDatapathMappingPath)) {
            operations.add(getDeleteOp(hostVrnDatapathMappingPath));
        }

        operations.add(
            getPersistentCreateOp(
                hostVrnDatapathMappingPath,
                serializer.serialize(datapathMapping)));

        multi(operations);
    }

    public void addVirtualPortMapping(
            UUID hostIdentifier, HostDirectory.VirtualPortMapping portMapping)
        throws StateAccessException {

        // Make sure that the portID is valid
        UUID portId = portMapping.getVirtualPortId();
        if (!portZkManager.exists(portId)) {
            throw new IllegalArgumentException(
                "Port with ID " + portId + " does not exist");
        }

        List<Op> operations = new ArrayList<Op>();

        String hostPath = paths.getHostPath(hostIdentifier);
        if (!exists(hostPath)) {
            operations.add(getPersistentCreateOp(hostPath, null));
        }

        String virtualMappingPath =
            paths.getHostVrnMappingsPath(hostIdentifier);


        if (!exists(virtualMappingPath)) {
            operations.add(getPersistentCreateOp(virtualMappingPath, null));
        }

        String hostVrnPortMappingsPath =
            paths.getHostVrnPortMappingsPath(hostIdentifier);

        if (!exists(hostVrnPortMappingsPath)) {
            operations.add(
                getPersistentCreateOp(hostVrnPortMappingsPath, null));
        }

        String hostVrnPortMappingPath =
            paths.getHostVrnPortMappingPath(hostIdentifier, portId);

        if (exists(hostVrnPortMappingPath)) {
            operations.add(getDeleteOp(hostVrnPortMappingPath));
        }

        operations.add(
            getPersistentCreateOp(
                hostVrnPortMappingPath,
                serializer.serialize(portMapping)));

        // Update the port config with host/interface
        operations.add(getMapUpdatePortOp(portId, hostIdentifier,
                                          portMapping.getLocalDeviceName()));

        multi(operations);
    }

    public void delVirtualPortMapping(UUID hostIdentifier, UUID portId)
        throws StateAccessException {

        String virtualMappingPath =
            paths.getHostVrnPortMappingPath(hostIdentifier, portId);

        List<Op> operations = new ArrayList<Op>();
        if (exists(virtualMappingPath)) {

            operations.add(getDeleteOp(virtualMappingPath));

            // Update the port config
            operations.add(getMapUpdatePortOp(portId, null, null));

            multi(operations);
        }

    }

    private Op getMapUpdatePortOp(UUID portId, UUID hostIdentifier,
                                  String localDeviceName)
        throws StateAccessException {

        PortConfig port = portZkManager.get(portId);
        if (port instanceof PortDirectory.MaterializedBridgePortConfig) {
            ((PortDirectory.MaterializedBridgePortConfig) port).setHostId(
                hostIdentifier);
            ((PortDirectory.MaterializedBridgePortConfig) port)
                .setInterfaceName(localDeviceName);
        } else if (port instanceof PortDirectory.MaterializedRouterPortConfig) {
            ((PortDirectory.MaterializedRouterPortConfig) port).setHostId(
                hostIdentifier);
            ((PortDirectory.MaterializedRouterPortConfig) port)
                .setInterfaceName(localDeviceName);
        } else {
            throw new UnsupportedOperationException(
                "Cannot bind interface to a logical port");
        }

        String portPath = paths.getPortPath(portId);
        return getSetDataOp(portPath, serializer.serialize(port));

    }

    public Set<UUID> getTunnelZoneIds(UUID hostId,
                                      Directory.TypedWatcher watcher)
        throws StateAccessException {
        String zonesPath = paths.getHostTunnelZonesPath(hostId);

        if (exists(zonesPath)) {
            return CollectionFunctors.map(
                getChildren(zonesPath, watcher),
                new Functor<String, UUID>() {
                    @Override
                    public UUID apply(String arg0) {
                        return UUID.fromString(arg0);
                    }
                }, new HashSet<UUID>());
        }

        return Collections.emptySet();
    }
}
