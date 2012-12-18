/*
 * Copyright 2011 Midokura Europe SARL
 */

package com.midokura.midonet.client.dto;

import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

@XmlRootElement
public class DtoApplication {
    private String version;
    private URI uri;
    private URI hosts;
    private URI tunnelZones;
    private URI bridges;
    private URI chains;
    private URI metricsFilter;
    private URI metricsQuery;
    private URI portGroups;
    private URI routers;
    private String adRouteTemplate;
    private String bgpTemplate;
    private String bridgeTemplate;
    private String chainTemplate;
    private String ruleTemplate;
    private String hostTemplate;
    private String portTemplate;
    private String portGroupTemplate;
    private String routeTemplate;
    private String routerTemplate;
    private String tunnelZoneTemplate;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public URI getBridges() {
        return bridges;
    }

    public void setBridges(URI bridges) {
        this.bridges = bridges;
    }

    public URI getChains() {
        return chains;
    }

    public void setChains(URI chains) {
        this.chains = chains;
    }

    public URI getMetricsFilter() {
        return metricsFilter;
    }

    public void setMetricsFilter(URI metricsFilter) {
        this.metricsFilter = metricsFilter;
    }

    public URI getMetricsQuery() {
        return metricsQuery;
    }

    public void setMetricsQuery(URI metricsQuery) {
        this.metricsQuery = metricsQuery;
    }

    public URI getPortGroups() {
        return portGroups;
    }

    public void setPortGroups(URI portGroups) {
        this.portGroups = portGroups;
    }

    public URI getRouters() {
        return routers;
    }

    public void setRouters(URI routers) {
        this.routers = routers;
    }

    public URI getHosts() {
        return hosts;
    }

    public void setHosts(URI hosts) {
        this.hosts = hosts;
    }

    public URI getTunnelZones() {
        return tunnelZones;
    }

    public void setTunnelZones(URI tunnelZones) {
        this.tunnelZones = tunnelZones;
    }

    public String getAdRouteTemplate() {
        return adRouteTemplate;
    }

    public void setAdRoute(String adRouteTemplate) {
        this.adRouteTemplate = adRouteTemplate;
    }

    public String getBgpTemplate() {
        return bgpTemplate;
    }

    public void setBgpTemplate(String bgpTemplate) {
        this.bgpTemplate = bgpTemplate;
    }

    public String getBridgeTemplate() {
        return bridgeTemplate;
    }

    public void setBridgeTemplate(String bridgeTemplate) {
        this.bridgeTemplate = bridgeTemplate;
    }

    public String getChainTemplate() {
        return chainTemplate;
    }

    public void setChainTemplate(String chainTemplate) {
        this.chainTemplate = chainTemplate;
    }

    public String getRuleTemplate() {
        return ruleTemplate;
    }

    public void setRuleTemplate(String ruleTemplate) {
        this.ruleTemplate = ruleTemplate;
    }

    public String getHostTemplate() {
        return hostTemplate;
    }

    public void setHostTemplate(String hostTemplate) {
        this.hostTemplate = hostTemplate;
    }

    public String getPortTemplate() {
        return portTemplate;
    }

    public void setPortTemplate(String portTemplate) {
        this.portTemplate = portTemplate;
    }

    public String getPortGroupTemplate() {
        return portGroupTemplate;
    }

    public void setPortGroupTemplate(String portGroupTemplate) {
        this.portGroupTemplate = portGroupTemplate;
    }

    public String getRouteTemplate() {
        return routeTemplate;
    }

    public void setRoute(String routeTemplate) {
        this.routeTemplate = routeTemplate;
    }

    public String getRouterTemplate() {
        return routerTemplate;
    }

    public void setRouterTemplate(String routerTemplate) {
        this.routerTemplate = routerTemplate;
    }

    public String getTunnelZoneTemplate() {
        return tunnelZoneTemplate;
    }

    public void setTunnelZoneTemplate(String tunnelZoneTemplate) {
        this.tunnelZoneTemplate = tunnelZoneTemplate;
    }
}
