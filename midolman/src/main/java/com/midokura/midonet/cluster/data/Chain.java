/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.cluster.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Chain extends Entity.Base<UUID, Chain.Data, Chain> {

    public enum Property {
        tenant_id
    }

    public Chain() {
        this(null, new Data());
    }

    public Chain(UUID id){
        this(id, new Data());
    }

    public Chain(Data data){
        this(null, data);
    }

    public Chain(UUID uuid, Data data) {
        super(uuid, data);
    }

    @Override
    protected Chain self() {
        return this;
    }

    public String getName() {
        return getData().name;
    }

    public Chain setName(String name) {
        getData().name = name;
        return this;
    }

    public Chain setProperty(Property property, String value) {
        getData().properties.put(property.name(), value);
        return this;
    }

    public Chain setProperties(Map<String, String> properties) {
        getData().properties = properties;
        return this;
    }

    public String getProperty(Property property) {
        return getData().properties.get(property.name());
    }

    public Map<String, String> getProperties() {
        return getData().properties;
    }

    public static class Data {
        public String name;
        public Map<String, String> properties = new HashMap<String, String>();

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            Data that = (Data) o;
            if (name != null ? !name.equals(that.name) : that.name != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            return name != null ? name.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "Chain.Data{" + "name=" + name + '}';
        }
    }
}
