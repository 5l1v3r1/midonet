/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midolman.host.guice;

import com.google.inject.PrivateModule;
import com.google.inject.Scopes;
import com.midokura.config.ConfigProvider;
import com.midokura.midolman.host.HostInterfaceWatcher;
import com.midokura.midolman.host.commands.executors.CommandInterpreter;
import com.midokura.midolman.host.commands.executors.HostCommandWatcher;
import com.midokura.midolman.host.config.HostConfig;
import com.midokura.midolman.host.sensor.DmesgInterfaceSensor;
import com.midokura.midolman.host.sensor.IpAddrInterfaceSensor;
import com.midokura.midolman.host.sensor.IpTuntapInterfaceSensor;
import com.midokura.midolman.host.sensor.NetlinkInterfaceSensor;
import com.midokura.midolman.host.services.HostService;
import com.midokura.midolman.host.state.HostZkManager;
import com.midokura.midolman.host.updater.DefaultInterfaceDataUpdater;
import com.midokura.midolman.host.updater.InterfaceDataUpdater;
import com.midokura.midolman.services.HostIdProviderService;

import javax.inject.Singleton;

/**
 * Module to configure dependencies for the host.
 */
public class HostModule extends PrivateModule {

    @Override
    protected void configure() {
        binder().requireExplicitBindings();

        //bind(InterfaceScanner.class).to(DefaultInterfaceScanner.class);
        bind(InterfaceDataUpdater.class).to(DefaultInterfaceDataUpdater.class);
        bind(CommandInterpreter.class).in(Scopes.SINGLETON);

        //expose(InterfaceScanner.class);
        expose(InterfaceDataUpdater.class);
        expose(CommandInterpreter.class);

        requireBinding(ConfigProvider.class);
        bind(HostConfig.class)
                .toProvider(HostConfigProvider.class)
                .asEagerSingleton();

        expose(HostIdProviderService.class);

        // TODO: uncomment this when the direct dependency on HostZKManager has been removed
        // requireBinding(Client.class);
        requireBinding(HostZkManager.class);
        bind(HostCommandWatcher.class);
        bind(HostInterfaceWatcher.class);

        bind(HostIdProviderService.class)
            .to(HostService.class)
            .in(Singleton.class);
        expose(HostIdProviderService.class);

        bind(HostService.class)
            .in(Singleton.class);

        expose(HostService.class);

        bind(IpAddrInterfaceSensor.class);
        expose(IpAddrInterfaceSensor.class);
        bind(IpTuntapInterfaceSensor.class);
        expose(IpTuntapInterfaceSensor.class);
        bind(DmesgInterfaceSensor.class);
        expose(DmesgInterfaceSensor.class);
        bind(NetlinkInterfaceSensor.class);
        expose(NetlinkInterfaceSensor.class);
    }
}
