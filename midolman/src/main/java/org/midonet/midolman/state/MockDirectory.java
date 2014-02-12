/*
 * Copyright 2011 Midokura KK
 * Copyright 2013 Midokura PTE LTD.
 */

package org.midonet.midolman.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;
import java.util.Set;
import javax.annotation.Nonnull;

import org.apache.jute.Record;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NoChildrenForEphemeralsException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.KeeperException.NotEmptyException;
import org.apache.zookeeper.Op;
import org.apache.zookeeper.OpResult;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.proto.CreateRequest;
import org.apache.zookeeper.proto.DeleteRequest;
import org.apache.zookeeper.proto.SetDataRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.zookeeper.Watcher.Event.EventType;
import static org.apache.zookeeper.Watcher.Event.KeeperState;


/**
 * This is an in-memory, naive implementation of the Directory interface.
 * It is only meant to be used in tests. However, it is packaged here
 * so that it can be used by external projects (e.g. functional tests).
 */
public class MockDirectory implements Directory {

    private final static Logger log = LoggerFactory.getLogger(
        MockDirectory.class);

    private class Node {
        // The node's path from the root.
        private String path;
        private byte[] data;
        CreateMode mode;
        int sequence;
        Map<String, Node> children;
        Set<Watcher> watchers;

        // We currently have no need for Watchers on 'exists'.

        Node(String path, byte[] data, CreateMode mode) {
            super();
            this.path = path;
            this.data = data;
            this.mode = mode;
            this.sequence = 0;
            this.children = new HashMap<String, Node>();
            this.watchers = new HashSet<Watcher>();
        }

        synchronized Node getChild(String name) throws NoNodeException {
            Node child = children.get(name);
            if (null == child)
                throw new NoNodeException(path + '/' + name);
            return child;
        }

        // Document that this returns the absolute path of the child
        synchronized String addChild(String name, byte[] data, CreateMode mode,
                        boolean multi)
            throws NodeExistsException, NoChildrenForEphemeralsException {
            if (enableDebugLog)
                log.debug("addChild {} => {}", name, data);
            if (!mode.isSequential() && children.containsKey(name))
                throw new NodeExistsException(path + '/' + name);
            if (this.mode.isEphemeral())
                throw new NoChildrenForEphemeralsException(path + '/' + name);
            if (mode.isSequential()) {
                name = name + String.format("%010d", sequence++);
            }

            String childPath = path + "/" + name;
            Node child = new Node(childPath, data, mode);
            children.put(name, child);
            fireWatchers(multi, EventType.NodeChildrenChanged);
            return childPath;
        }

        synchronized void setData(byte[] data, boolean multi) {
            if (enableDebugLog)
                log.debug("[child]setData => {}", data);
            this.data = data;

            fireWatchers(multi, EventType.NodeDataChanged);
        }

        synchronized byte[] getData(Watcher watcher) {
            if (watcher != null)
                watchers.add(watcher);

            if (data == null) {
                return null;
            }
            return data.clone();
        }

        synchronized Set<String> getChildren(Runnable watcher) {
            if (watcher != null)
                watchers.add(wrapCallback(watcher));

            return new HashSet<String>(children.keySet());
        }

        synchronized void deleteChild(String name, boolean multi)
            throws NoNodeException, NotEmptyException {
            Node child = children.get(name);
            String childPath = path + "/" + name;
            if (null == child)
                throw new NoNodeException(childPath);
            if (!child.children.isEmpty())
                throw new NotEmptyException(childPath);
            children.remove(name);

            child.fireWatchers(multi, EventType.NodeDeleted);
            this.fireWatchers(multi, EventType.NodeChildrenChanged);
        }

        synchronized void fireWatchers(boolean isMulti, EventType eventType) {
            // Each Watcher is called back at most once for every time they
            // register.
            Set<Watcher> watchers = new HashSet<Watcher>(this.watchers);
            this.watchers.clear();

            for (Watcher watcher : watchers) {
                WatchedEvent watchedEvent = new WatchedEvent(
                    eventType, KeeperState.SyncConnected, path
                );

                if (isMulti) {
                    synchronized (multiDataWatchers) {
                        multiDataWatchers.put(watcher, watchedEvent);
                    }
                } else {
                    watcher.process(watchedEvent);
                }
            }
        }
    }

    private Node rootNode;
    private final Map<Watcher, WatchedEvent> multiDataWatchers;
    public boolean enableDebugLog = false;

    private MockDirectory(Node root, Map<Watcher, WatchedEvent> multiWatchers) {
        rootNode = root;
        // All the nodes will belong to another MockDirectory whose
        // multiDataWatchers set is initialized, and they will use it.
        multiDataWatchers = multiWatchers;
    }

    public MockDirectory() {
        rootNode = new Node("", null, CreateMode.PERSISTENT);
        multiDataWatchers = new HashMap<Watcher, WatchedEvent>();
    }

    private Node getNode(String path) throws NoNodeException {
        String[] path_elems = path.split("/");
        return getNode(path_elems, path_elems.length);
    }

    private Node getNode(String[] path, int depth) throws NoNodeException {
        Node curNode = rootNode;

        // TODO(pino): fix this hack - starts at 1 to skip empty string.
        for (int i = 1; i < depth; i++) {
            String path_elem = path[i];
            curNode = curNode.getChild(path_elem);
        }
        if (enableDebugLog)
            log.debug("get {} => {}", path, curNode);
        return curNode;
    }

    private String add(String relativePath, byte[] data, CreateMode mode,
                       boolean multi)
        throws NoNodeException, NodeExistsException,
               NoChildrenForEphemeralsException {
        if (enableDebugLog)
            log.debug("add {} => {}", relativePath, data);
        if (!relativePath.startsWith("/")) {
            throw new IllegalArgumentException("Path must start with '/'");
        }
        String[] path = relativePath.split("/", -1);
        if (path.length == 0)
            throw new IllegalArgumentException("Cannot add the root node");
        Node parent = getNode(path, path.length - 1);
        String childPath =
            parent.addChild(path[path.length - 1], data, mode, multi);

        return childPath.substring(rootNode.path.length());
    }

    @Override
    public String add(String relativePath, byte[] data, CreateMode mode)
        throws NoNodeException, NodeExistsException,
               NoChildrenForEphemeralsException {
        return add(relativePath, data, mode, false);
    }

    @Override
    public void ensureHas(String relativePath, byte[] data)
            throws NoNodeException, NoChildrenForEphemeralsException {
        try {
            add(relativePath, data, CreateMode.PERSISTENT, false);
        } catch (KeeperException.NodeExistsException e) { /* node was there */ }
    }

    @Override
    public void asyncAdd(String relativePath, byte[] data, CreateMode mode) {
        try {
            add(relativePath, data, mode);
        } catch (KeeperException e) {
            log.debug("asyncAdd Exception", e);
        }
    }

    @Override
    public void asyncAdd(String relativePath, byte[] data, CreateMode mode, DirectoryCallback.Add cb) {
        try {
            cb.onSuccess(
                new DirectoryCallback.Result<String>(
                    add(relativePath, data, mode),
                    null
                ));
        } catch (KeeperException e) {
            cb.onError(e);
        }
    }

    private void update(String path, byte[] data, boolean multi)
        throws NoNodeException {
        getNode(path).setData(data, multi);
    }

    @Override
    public void update(String path, byte[] data) throws NoNodeException {
        update(path, data, false);
    }

    @Override
    public byte[] get(String path, Runnable watcher) throws NoNodeException {
        return getNode(path).getData(wrapCallback(watcher));
    }

    @Override
    public Map.Entry<byte[], Integer> getWithVersion(String path,
            Runnable watcher) throws NoNodeException {
        int version = -1;

        byte[] data = getNode(path).getData(wrapCallback(watcher));

        return new AbstractMap.SimpleEntry<byte[], Integer>(data, version);
    }

    @Override
    public void asyncGetChildren(String relativePath, DirectoryCallback<Set<String>> childrenCallback, TypedWatcher watcher) {
        try {
            childrenCallback.onSuccess(
                new DirectoryCallback.Result<Set<String>>(
                    getNode(relativePath).getChildren(watcher),
                    new Stat()));
        } catch (NoNodeException e) {
            childrenCallback.onError(e);
        }
    }

    @Override
    public void asyncGet(String relativePath, DirectoryCallback<byte[]> dataCb, TypedWatcher watcher) {
        try {
            byte[] data = getNode(relativePath).getData(wrapCallback(watcher));
            dataCb.onSuccess(new DirectoryCallback.Result<byte[]>(data, new Stat()));
        } catch (NoNodeException e) {
            dataCb.onError(e);
        }
    }

    @Override
    public Set<String> getChildren(String path, Runnable watcher)
        throws NoNodeException {
        return getNode(path).getChildren(watcher);
    }

    @Override
    public boolean has(String path) {
        try {
            getNode(path);
            return true;
        } catch (NoNodeException e) {
            return false;
        }
    }

    private void delete(String relativePath, boolean multi)
        throws NoNodeException, NotEmptyException {
        String[] path = relativePath.split("/");
        if (path.length == 0)
            throw new IllegalArgumentException("Cannot delete the root node");
        Node parent = getNode(path, path.length - 1);
        parent.deleteChild(path[path.length - 1], multi);
    }

    @Override
    public void delete(String relativePath)
        throws NoNodeException, NotEmptyException {
        delete(relativePath, false);
    }

    @Override
    public void asyncDelete(String relativePath, DirectoryCallback.Void callback) {
         try {
             delete(relativePath, false);
             callback.onSuccess(new DirectoryCallback.Result<Void>(null, null));
         } catch (KeeperException ex) {
             callback.onError(ex);
         }
    }

    @Override
    public void asyncDelete(String relativePath) {
        try {
            delete(relativePath, false);
        } catch (KeeperException e) {
            log.debug("asyncDelete got exception", e);
        }

    }

    @Override
    public Directory getSubDirectory(String path) throws NoNodeException {
        return new MockDirectory(getNode(path), multiDataWatchers);
    }

    @Override
    public List<OpResult> multi(List<Op> ops) throws InterruptedException,
                                                     KeeperException {
        List<OpResult> results = new ArrayList<OpResult>();
        // Fire watchers after finishing multi operation.
        // Copy to the local Set to avoid concurrent access.
        Map<Watcher, WatchedEvent> watchers = new HashMap<Watcher, WatchedEvent>();
        try {
            for (Op op : ops) {
                Record record = op.toRequestRecord();
                if (record instanceof CreateRequest) {
                    // TODO(pino, ryu): should we use the try/catch and create
                    // new ErrorResult? Don't for now, but this means that the
                    // unit tests can't purposely make a bad Op.
                    // try {
                    CreateRequest req = CreateRequest.class.cast(record);
                    String path = this.add(req.getPath(), req.getData(),
                                           CreateMode.fromFlag(req.getFlags()),
                                           true);
                    results.add(new OpResult.CreateResult(path));
                    // } catch (KeeperException e) {
                    // e.printStackTrace();
                    // results.add(
                    // new OpResult.ErrorResult(e.code().intValue()));
                    // }
                } else if (record instanceof SetDataRequest) {
                    SetDataRequest req = SetDataRequest.class.cast(record);
                    this.update(req.getPath(), req.getData(), true);
                    // We create the SetDataResult with Stat=null. The
                    // Directory interface doesn't provide the Stat object.
                    results.add(new OpResult.SetDataResult(null));
                } else if (record instanceof DeleteRequest) {
                    DeleteRequest req = DeleteRequest.class.cast(record);
                    this.delete(req.getPath(), true);
                    results.add(new OpResult.DeleteResult());
                } else {
                    // might be CheckVersionRequest or some new type we miss.
                    throw new IllegalArgumentException(
                        "This mock implementation only supports Create, " +
                            "SetData and Delete operations");
                }
            }
        } finally {
            synchronized (multiDataWatchers) {
                watchers.putAll(multiDataWatchers);
                multiDataWatchers.clear();
            }
        }

        for (Watcher watcher : watchers.keySet()) {
            watcher.process(watchers.get(watcher));
        }

        return results;
    }

    @Override
    public long getSessionId() {
        return 0;
    }

    @Override
    public void closeConnection() {
        // Do nothing here.
    }

    @Override
    public String toString() {
        return "MockDirectory{" +
            "node.path=" + rootNode.path +
            '}';
    }

    private Watcher wrapCallback(Runnable runnable) {
        if (runnable == null)
            return null;
        if (runnable instanceof TypedWatcher)
            return new MyTypedWatcher(TypedWatcher.class.cast(runnable));
        else
            return new MyWatcher(runnable);
    }

    private static class MyWatcher implements Watcher {
        Runnable watcher;

        MyWatcher(Runnable watch) {
            watcher = watch;
        }

        @Override
        public void process(WatchedEvent arg0) {
            watcher.run();
        }
    }

    @Override
    public void asyncMultiPathGet(@Nonnull final Set<String> relativePaths,
                                  final DirectoryCallback<Set<byte[]>> cb) {
        if(relativePaths.isEmpty()){
            log.debug("Empty set of paths, is that OK?");
            cb.onSuccess(new DirectoryCallback.Result<Set<byte[]>>(
            Collections.<byte[]>emptySet(), null));
        }
        // Map to keep track of the callbacks that returned
        final Map<String, byte[]> callbackResults =
            new HashMap<String, byte[]>();
        for(final String path: relativePaths){
            asyncGet(path, new DirectoryCallback<byte[]>(){

                @Override
                public void onTimeout(){
                    synchronized (callbackResults){
                        callbackResults.put(path, null);
                    }
                    log.error("asyncMultiPathGet - Timeout {}", path);
                }

                @Override
                public void onError(KeeperException e) {
                    synchronized (callbackResults){
                        callbackResults.put(path, null);
                    }
                    log.error("asyncMultiPathGet - Exception {}", path, e);
                }

                @Override
                public void onSuccess(Result<byte[]> data) {
                    synchronized (callbackResults){
                        callbackResults.put(path, data.getData());
                            if(callbackResults.size() == relativePaths.size()){
                                Set<byte[]> results = new HashSet<byte[]>();
                                for(Map.Entry entry : callbackResults.entrySet()){
                                    if(entry != null)
                                        results.add((byte[])entry.getValue());
                                }
                                cb.onSuccess(
                                    new Result<Set<byte[]>>(results, null));
                            }
                    }
                }
            }, null);
        }
    }

    private static class MyTypedWatcher implements Watcher, Runnable {
        TypedWatcher watcher;
        WatchedEvent watchedEvent;

        private MyTypedWatcher(TypedWatcher watcher) {
            this.watcher = watcher;
        }

        @Override
        public void process(WatchedEvent event) {
            dispatchEvent(event, watcher);
        }

        @Override
        public void run() {
            dispatchEvent(watchedEvent, watcher);
        }

        private void dispatchEvent(WatchedEvent event, TypedWatcher typedWatcher) {
            switch (event.getType()) {
                case NodeDeleted:
                    typedWatcher.pathDeleted(event.getPath());
                    break;

                case NodeCreated:
                    typedWatcher.pathCreated(event.getPath());
                    break;

                case NodeChildrenChanged:
                    typedWatcher.pathChildrenUpdated(event.getPath());
                    break;

                case NodeDataChanged:
                    typedWatcher.pathDataChanged(event.getPath());
                    break;

                case None:
                    typedWatcher.connectionStateChanged(event.getState());
                    break;
            }
        }
    }
}
