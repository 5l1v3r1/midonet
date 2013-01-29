/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.cluster.data;

import org.codehaus.jackson.annotate.JsonTypeInfo;

import javax.annotation.Nonnull;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
              include = JsonTypeInfo.As.PROPERTY, property = "type")
public class ChainName extends Entity.Base<String, ChainName.Data, ChainName>{

    public ChainName(String key, Data data) {
        super(key, data);
    }

    public ChainName(@Nonnull Chain chain) {
        super(chain.getData().name, new Data());

        setChainId(chain.getId());
    }

    public ChainName setChainId(UUID chainId) {
        getData().id = chainId;
        return self();
    }

    public UUID getChainId() {
        return getData().id;
    }

    @Override
    protected ChainName self() {
        return this;
    }

    public static class Data {
        public UUID id;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Data data = (Data) o;

            if (id != null ? !id.equals(data.id) : data.id != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }
}
