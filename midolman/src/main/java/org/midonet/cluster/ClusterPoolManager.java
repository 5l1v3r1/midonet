/*
 * Copyright 2014 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.cluster;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import scala.runtime.AbstractFunction0;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import org.midonet.cluster.data.Converter;
import org.midonet.cluster.data.l4lb.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.midonet.cluster.client.PoolBuilder;
import org.midonet.cluster.data.l4lb.PoolMember;
import org.midonet.midolman.state.zkManagers.PoolZkManager;
import org.midonet.midolman.state.zkManagers.PoolZkManager.PoolConfig;
import org.midonet.midolman.state.zkManagers.PoolMemberZkManager;
import org.midonet.midolman.state.zkManagers.PoolMemberZkManager.PoolMemberConfig;
import org.midonet.midolman.topology.VirtualTopologyMetrics;

public class ClusterPoolManager extends ClusterManager<PoolBuilder> {
    private static final Logger log = LoggerFactory
            .getLogger(ClusterPoolManager.class);

    PoolZkManager poolZkMgr;

    PoolMemberZkManager poolMemberZkMgr;

    private Map<UUID, Map<UUID, PoolMember>> poolIdToPoolMemberMap =
            new HashMap<>();
    private Map<UUID, Set<UUID>> poolToPoolMemberIds =
            new HashMap<>();
    private Multimap<UUID, UUID> poolToMissingPoolMemberIds =
            HashMultimap.create();

    @Inject
    public ClusterPoolManager(PoolZkManager poolZkMgr,
                              PoolMemberZkManager poolMemberZkMgr,
                              VirtualTopologyMetrics metrics) {
        this.poolZkMgr = poolZkMgr;
        this.poolMemberZkMgr = poolMemberZkMgr;
        metrics.setPoolMemberMaps(new AbstractFunction0<Object>() {
            @Override public Object apply() {
                return poolIdToPoolMemberMap.size();
            }
        });
        metrics.setPoolMemberIds(new AbstractFunction0<Object>() {
            @Override public Object apply() {
                return poolToPoolMemberIds.size();
            }
        });
        metrics.setPoolMissingMemberIds(new AbstractFunction0<Object>() {
            @Override public Object apply() {
                return poolToMissingPoolMemberIds.size();
            }
        });
    }

    @Override
    protected void getConfig(UUID poolId) {
        if (poolIdToPoolMemberMap.containsKey(poolId)) {
            log.error("Trying to request the same Pool {}.", poolId);
            return;
        }
        poolIdToPoolMemberMap.put(poolId, new HashMap<UUID, PoolMember>());
        PoolMemberListCallback poolMemberListCB = new PoolMemberListCallback(poolId);
        poolZkMgr.getPoolMemberIdListAsync(poolId, poolMemberListCB, poolMemberListCB);

        PoolConfigCallback poolConfigCB = new PoolConfigCallback(poolId);
        poolZkMgr.getAsync(poolId, poolConfigCB, poolConfigCB);
    }

    private void requestPoolMember(UUID poolMemberID) {
        PoolMemberCallback poolMemberCallback = new PoolMemberCallback(poolMemberID);
        poolMemberZkMgr.getAsync(poolMemberID, poolMemberCallback, poolMemberCallback);
    }

    private class PoolConfigCallback extends CallbackWithWatcher<PoolConfig> {
        private UUID poolId;

        private PoolConfigCallback(UUID poolId) {
            this.poolId = poolId;
        }

        @Override
        protected String describe() {
            return "PoolConfig:" + poolId;
        }

        @Override
        public void onSuccess(PoolConfig conf) {
            Pool pool = Converter.fromPoolConfig(conf);
            pool.setId(poolId);
            PoolBuilder builder = getBuilder(poolId);
            if (builder != null) {
                builder.setPoolConfig(pool);
            }
        }

        @Override
        public void pathDataChanged(String path) {
            poolZkMgr.getAsync(poolId, this, this);
        }

        @Override
        public void pathDeleted(String path) {
            log.debug("Pool {} has been deleted", poolId);
            PoolBuilder builder = unregisterBuilder(poolId);
            if (builder != null) {
                poolIdToPoolMemberMap.remove(poolId);
                poolToPoolMemberIds.remove(poolId);
                poolToMissingPoolMemberIds.removeAll(poolId);
                builder.deleted();
            }
        }

        @Override
        protected Runnable makeRetry() {
            return new Runnable() {
                @Override
                public void run() {
                    poolZkMgr.getAsync(poolId,
                            PoolConfigCallback.this, PoolConfigCallback.this);
                }
            };
        }
    }

    private class PoolMemberListCallback extends CallbackWithWatcher<Set<UUID>> {
        private UUID poolId;

        private PoolMemberListCallback(UUID PoolId) {
            this.poolId = PoolId;
        }

        @Override
        protected String describe() {
            return "PoolMemberList:" + poolId;
        }

        @Override
        public void onSuccess(Set<UUID> curPoolMemberIds) {
            // curPoolMemberIds is a set of the UUIDs of current PoolMembers

            // UUID to actual PoolMember for each PoolMember in Pool
            Map<UUID, PoolMember> poolMemberMap = poolIdToPoolMemberMap.get(poolId);
            if (poolMemberMap == null) return;

            poolToPoolMemberIds.put(poolId, curPoolMemberIds);

            // Set of old PoolMember IDs from Pool
            Set<UUID> oldPoolMemberIds = poolMemberMap.keySet();

            // Copy current PoolMembers
            Set<UUID> poolMembersToRequest = new HashSet<UUID>(curPoolMemberIds);

            // If the new set tells us a PoolMember disappeared,
            // remove it from the Pool's PoolMember id -> PoolMember info map
            // Also remove from poolMembersToRequest so that only the vips we
            // need are left
            Iterator<UUID> poolMemberIter = oldPoolMemberIds.iterator();
            while (poolMemberIter.hasNext()) {
                if (!poolMembersToRequest.remove(poolMemberIter.next()))
                    poolMemberIter.remove();
            }

            // If we have all the PoolMembers in the new set, we're
            // ready to call the PoolBuilder
            if (poolMembersToRequest.isEmpty()) {
                PoolBuilder builder = getBuilder(poolId);
                if (builder != null) {
                    builder.setPoolMembers(poolMemberMap);
                }
                return;
            }

            // Otherwise, we have to fetch some PoolMembers.
            for(UUID poolMemberId : poolMembersToRequest) {
                poolToMissingPoolMemberIds.put(poolId, poolMemberId);
            }

            // We do this in two passes (mark all missing, request all
            // missing) to avoid race condition where a pool member request
            // returns before we've marked all missing Vpool members
            for(UUID poolMemberId : poolMembersToRequest) {
                requestPoolMember(poolMemberId);
            }
        }

        @Override
        public void pathChildrenUpdated(String path) {
            poolZkMgr.getPoolMemberIdListAsync(poolId, this, this);
        }

        @Override
        protected Runnable makeRetry() {
            return new Runnable() {
                @Override
                public void run() {
                    poolZkMgr.getPoolMemberIdListAsync(poolId,
                            PoolMemberListCallback.this, PoolMemberListCallback.this);
                }
            };
        }

    }


    private class PoolMemberCallback
            extends CallbackWithWatcher<PoolMemberConfig> {
        private UUID poolMemberId;

        private PoolMemberCallback(UUID PoolMemberId) {
            this.poolMemberId = PoolMemberId;
        }

        @Override
        protected String describe() {
            return "PoolMember:" + poolMemberId;
        }

        @Override
        public void onSuccess(PoolMemberConfig memberConf) {
            PoolMember poolMember = Converter.fromPoolMemberConfig(memberConf);
            poolMember.setId(poolMemberId);

            Collection<UUID> missingPoolMemberIds =
                    poolToMissingPoolMemberIds.get(poolMember.getPoolId());
            Set<UUID> poolMemberIds = poolToPoolMemberIds.get(poolMember.getPoolId());
            // Does the Pool still care about this poolMember?
            if (poolMemberIds == null || missingPoolMemberIds == null ||
                !poolMemberIds.contains(poolMemberId))
                return;
            missingPoolMemberIds.remove(poolMemberId);
            Map<UUID, PoolMember> poolMemberMap = poolIdToPoolMemberMap.get(
                    poolMember.getPoolId());
            if (poolMemberMap == null)
                return;

            poolMemberMap.put(poolMemberId, poolMember);

            if ((missingPoolMemberIds.size() == 0)) {
                PoolBuilder builder = getBuilder(poolMember.getPoolId());
                if (builder != null) {
                    builder.setPoolMembers(poolMemberMap);
                }
            }
        }

        @Override
        public void pathDataChanged(String path) {
            poolMemberZkMgr.getAsync(poolMemberId, this, this);
        }

        @Override
        protected Runnable makeRetry() {
            return new Runnable() {
                @Override
                public void run() {
                    poolMemberZkMgr.getAsync(poolMemberId,
                            PoolMemberCallback.this, PoolMemberCallback.this);
                }
            };
        }
    }

}
