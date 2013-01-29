/*
 * Copyright 2012 Midokura Pte. Ltd.
 */
package com.midokura.midolman.host.sensor;

import com.google.inject.Inject;
import com.midokura.midolman.host.interfaces.InterfaceDescription;
import com.midokura.netlink.protos.OvsDatapathConnection;
import com.midokura.sdn.dp.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Interface sensor using Netlink.
 */
public class NetlinkInterfaceSensor implements InterfaceSensor {

    private static final Logger log = LoggerFactory.getLogger(
            NetlinkInterfaceSensor.class);

    @Inject
    private OvsDatapathConnection datapathConnection;

    // Timeout value in milliseconds when accessing the datapath
    private static final long NETLINK_CONN_TIMEOUT =
            TimeUnit.SECONDS.toMillis(3);

    @Override
    public List<InterfaceDescription> updateInterfaceData
            (List<InterfaceDescription> interfaces) {

        for (InterfaceDescription interfaceDescription : interfaces) {

            Port<?, ?> port = null;

            try {

                port = getDatapathPort(interfaceDescription.getName());

            } catch (InterruptedException ex) {
                log.debug("Got interrupted. Interrupting the current thread");
                Thread.currentThread().interrupt();
            } catch (ExecutionException ex) {
                // This interface is not a datapath port
                log.debug("Exception thrown while retrieving the result for " +
                        "interface " + interfaceDescription.getName() + ": "
                        + ex.getCause());
                // Try the next one
                continue;
            } catch (TimeoutException ex) {
                log.warn("Timeout exception thrown with value: "
                        + NETLINK_CONN_TIMEOUT);
                // Try the next one
                continue;
            }

            // Existence of port implies this is port used in Midolman
            if (port != null) {

                // Set the port type
                interfaceDescription.setPortType(port.getType());

                // Set the endpoint if it's still unknown
                if (interfaceDescription.getEndpoint() ==
                        InterfaceDescription.Endpoint.UNKNOWN) {
                    // We have not got an endpoint classification from the
                    // previous sensor.
                    interfaceDescription.setEndpoint(
                            InterfaceDescription.Endpoint.DATAPATH);
                    interfaceDescription.setType(
                            InterfaceDescription.Type.VIRT);
                }
            }

        }

        return interfaces;
    }

    protected Port getDatapathPort(String portName)
            throws ExecutionException, TimeoutException, InterruptedException {

        // Query datapath to see if there is a port with this name.
        Future<Port<?, ?>> result =
                datapathConnection.portsGet(portName, null);

        return result.get(NETLINK_CONN_TIMEOUT, TimeUnit.MILLISECONDS);
    }
}
