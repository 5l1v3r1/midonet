/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.servlet;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.midokura.midonet.api.rest_api.RestApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

/**
 * Guice servlet listener.
 */
public class JerseyGuiceServletContextListener extends
        GuiceServletContextListener {

    private final static Logger log = LoggerFactory
            .getLogger(JerseyGuiceServletContextListener.class);

    protected ServletContext servletContext;
    protected Injector injector;

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        log.debug("contextInitialized: entered");

        servletContext = servletContextEvent.getServletContext();
        super.contextInitialized(servletContextEvent);

        log.debug("contextInitialized exiting");
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        log.debug("contextDestroyed: entered");

        destroyApplication();
        super.contextDestroyed(servletContextEvent);

        log.debug("contextDestroyed exiting");
    }

    protected void initializeApplication() {
        log.debug("initializeApplication: entered");

        // TODO: Once the cluster work is completed, RestApiService may not be
        // needed since currently it only initializes the ZK root directories.
        RestApiService restApiService = injector.getInstance(
                RestApiService.class);
        restApiService.startAndWait();

        log.debug("initializeApplication: exiting.");
    }

    protected void destroyApplication() {
        log.debug("destroyApplication: entered");

        // TODO: Check if we need to do this after the cluster work is
        // completed.
        RestApiService restApiService = injector.getInstance(
                RestApiService.class);
        restApiService.stopAndWait();

        log.debug("destroyApplication: entered");
    }

    @Override
    protected Injector getInjector() {
        log.debug("getInjector: entered.");

        injector = Guice.createInjector(
                new RestApiJerseyServletModule(servletContext));

        // Initialize application
        initializeApplication();

        log.debug("getInjector: exiting.");
        return injector;
    }

}
