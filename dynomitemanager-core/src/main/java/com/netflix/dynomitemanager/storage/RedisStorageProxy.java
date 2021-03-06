package com.netflix.dynomitemanager.storage;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.dynomitemanager.config.FloridaConfig;
import com.netflix.nfsidecar.scheduler.SimpleTimer;
import com.netflix.nfsidecar.scheduler.Task;
import com.netflix.nfsidecar.scheduler.TaskTimer;
import com.netflix.nfsidecar.utils.Sleeper;
import com.netflix.runtime.health.api.Health;
import com.netflix.runtime.health.api.HealthIndicator;
import com.netflix.runtime.health.api.HealthIndicatorCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TODOs: we should talk to admin port (22222) instead of 8102 for both local
//and peer
@Singleton
public class RedisStorageProxy extends Task implements StorageProxy, HealthIndicator {

    private static final String DYNO_REDIS = "redis";
    private static final String DYNO_REDIS_CONF_PATH = "/apps/nfredis/conf/redis.conf";
    private static final String REDIS_ADDRESS = "127.0.0.1";
    private static final int REDIS_PORT = 22122;
    private static final long GB_2_IN_KB = 2L * 1024L * 1024L;
    private static final String PROC_MEMINFO_PATH = "/proc/meminfo";
    private static final Pattern MEMINFO_PATTERN = Pattern.compile("MemTotal:\\s*([0-9]*)");

    private final String REDIS_START_SCRIPT = "/apps/nfredis/bin/launch_nfredis.sh";
    private final String REDIS_STOP_SCRIPT = "/apps/nfredis/bin/kill_redis.sh";

    private static final String REDIS_CONF_MAXMEMORY_PATTERN = "^maxmemory\\s*[0-9][0-9]*[a-zA-Z]*";
    private static final String REDIS_CONF_APPENDONLY = "^appendonly\\s*[a-zA-Z]*";
    private static final String REDIS_CONF_APPENDFSYNC = "^ appendfsync\\s*[a-zA-Z]*";
    private static final String REDIS_CONF_AUTOAOFREWRITEPERCENTAGE = "^auto-aof-rewrite-percentage\\s*[0-9][0-9]*[a-zA-Z]*";
    private static final String REDIS_CONF_STOP_WRITES_BGSAVE_ERROR = "^stop-writes-on-bgsave-error\\s*[a-zA-Z]*";
    private static final String REDIS_CONF_SAVE_SCHEDULE = "^\\ssave\\s[0-9]*\\s[0-9]*";
    private static final String REDIS_CONF_UNIXSOCKET = "^\\s*unixsocket\\s*[/0-9a-zA-Z._]*";
    private static final String REDIS_CONF_UNIXSOCKETPERM = "^\\s*unixsocketperm\\s*[0-9]*";
    private static final String REDIS_CONF_PUBSUB = "notify-keyspace-events";
    private static final String REDIS_CONF_DAEMONIZE = "^daemonize\\s*[a-zA-Z]*";

    private static final Logger logger = LoggerFactory.getLogger(RedisStorageProxy.class);
    public static final String JOB_TASK_NAME = "REDIS HEALTH TRACKER";

    private Jedis localJedis;
    private boolean redisHealth = false;

    private final FloridaConfig config;

    @Inject
    private Sleeper sleeper;

    @Inject
    public RedisStorageProxy(FloridaConfig config) {
        this.config = config;
        // connect();
    }

    public static TaskTimer getTimer() {
        return new SimpleTimer(JOB_TASK_NAME, 15L * 1000);
    }

    @Override
    public String getName() {
        return JOB_TASK_NAME;
    }

    @Override
    public void execute() throws Exception {
        redisHealth = isAlive();
    }

	private void foceRedisReconnect() {
		// After backup/restore connection need to be re-stablished otherwise does not work.
		logger.info("Connecting to Redis.");
		this.localJedis = JedisUtils.connect(REDIS_ADDRESS, REDIS_PORT);
	}
    
    /**
     * A wrapper function around JedisUtils to connect to Redis
     */
    private void localRedisConnect() {
        if (this.localJedis == null) {
            logger.info("Connecting to Redis.");
            this.localJedis = JedisUtils.connect(REDIS_ADDRESS, REDIS_PORT);
        }
    }

    private void localRedisDisconnect() {
        if (this.localJedis != null) {
            logger.info("Disconnecting from Redis.");
            this.localJedis.disconnect();
            this.localJedis = null;
        }
    }

    /**
     * Connect to the peer with the same token, in order to start the warm up
     * process
     * 
     * @param peer
     *            address
     * @param peer
     *            port
     */
    private void startPeerSync(String peer, int port) {
        boolean isDone = false;

        localRedisConnect();

        /*
         * Iterate until we succeed the SLAVEOF command with some sleep time in
         * between. We disconnect and reconnect if the jedis connection fails.
         */
        while (!isDone) {
            try {
                // only sync from one peer for now
                isDone = (this.localJedis.slaveof(peer, port) != null);
                sleeper.sleepQuietly(1000);
            } catch (JedisConnectionException e) {
                logger.warn("JedisConnection Exception in SLAVEOF peer " + peer + " port " + port + " Exception: "
                        + e.getMessage());
                logger.warn("Trying to reconnect...");
                localRedisDisconnect();
                localRedisConnect();
            } catch (Exception e) {
                logger.error("Error: " + e.getMessage());

            }
        }

        // clean up the Redis connection.
        localRedisDisconnect();
    }

    /**
     * Turn off Redis' slave replication and switch from slave to master.
     */
    @Override
    public void stopPeerSync() {
        boolean isDone = false;
        localRedisConnect();

        /*
         * Iterate until we succeed the SLAVE NO ONE command with some sleep
         * time in between. We disconnect and reconnect if the jedis connection
         * fails.
         */
        while (!isDone) {
            logger.info("calling SLAVEOF NO ONE");
            try {
                isDone = (this.localJedis.slaveofNoOne() != null);
                sleeper.sleepQuietly(1000);
            } catch (JedisConnectionException e) {
                logger.warn("JedisConnection Exception in SLAVEOF NO ONE: " + e.getMessage());
                logger.warn("Trying to reconnect...");
                localRedisDisconnect();
                localRedisConnect();
            } catch (Exception e) {
                logger.error("Error: " + e.getMessage());
            }
        }

        // clean up the Redis connection.
        localRedisDisconnect();
    }

    @Override
    public String getEngine() {
        return DYNO_REDIS;
    }

    @Override
    public int getEngineNumber() {
        return 0;
    }

    @Override
    public boolean takeSnapshot() {
        localRedisConnect();
        try {
            if (config.persistenceType().equals("aof")) {
                logger.info("starting Redis BGREWRITEAOF");
                this.localJedis.bgrewriteaof();
            } else {
                logger.info("starting Redis BGSAVE");
                this.localJedis.bgsave();

            }
            /*
             * We want to check if a bgrewriteaof was already scheduled or it
             * has started. If a bgrewriteaof was already scheduled then we
             * should get an error from Redis but should continue. If a
             * bgrewriteaof has started, we should also continue. Otherwise we
             * may be having old data in the disk.
             */
        } catch (JedisDataException e) {
            String scheduled = null;
            if (!config.persistenceType().equals("aof")) {
                scheduled = "ERR Background save already in progress";
            } else {
                scheduled = "ERR Background append only file rewriting already in progress";
            }

            if (!e.getMessage().equals(scheduled)) {
                throw e;
            }
            logger.warn("Redis: There is already a pending BGREWRITEAOF/BGSAVE.");
        } catch (JedisConnectionException e) {
            logger.error("Redis: BGREWRITEAOF/BGSAVE cannot be completed");
            localRedisDisconnect();
            return false;
        }

        String peerRedisInfo = null;
        int retry = 0;

        try {
            while (true) {
                peerRedisInfo = this.localJedis.info();
                Iterable<String> result = Splitter.on('\n').split(peerRedisInfo);
                String pendingPersistence = null;

                for (String line : result) {
                    if ((line.startsWith("aof_rewrite_in_progress") && config.persistenceType().equals("aof"))
                            || (line.startsWith("rdb_bgsave_in_progress") && !config.persistenceType().equals("aof"))) {
                        String[] items = line.split(":");
                        pendingPersistence = items[1].trim();
                        if (pendingPersistence.equals("0")) {
                            logger.info("Redis: BGREWRITEAOF/BGSAVE completed.");
                            return true;
                        } else {
                            retry++;
                            logger.warn("Redis: BGREWRITEAOF/BGSAVE pending. Sleeping 30 secs...");
                            sleeper.sleepQuietly(30000);
                            if (retry > 20) {
                                return false;
                            }
                        }
                    }
                }
            }

        } catch (JedisConnectionException e) {
            logger.error("Cannot connect to Redis to INFO to determine if BGREWRITEAOF/BGSAVE completed ");
        } finally {
            localRedisDisconnect();
        }

        logger.error("Redis BGREWRITEAOF/BGSAVE was not successful.");
        return false;

    }

    @Override
    public boolean loadingData() {
		foceRedisReconnect();
		//localRedisConnect();
        logger.info("loading AOF from the drive");
        String peerRedisInfo = null;
        int retry = 0;

        try {
            peerRedisInfo = localJedis.info();
            Iterable<String> result = Splitter.on('\n').split(peerRedisInfo);
            String pendingAOF = null;

            for (String line : result) {
                if (line.startsWith("loading")) {
                    String[] items = line.split(":");
                    pendingAOF = items[1].trim();
                    if (pendingAOF.equals("0")) {
                        logger.info("Redis: memory loading completed.");
                        return true;
                    } else {
                        retry++;
                        logger.warn("Redis: memory pending. Sleeping 30 secs...");
                        sleeper.sleepQuietly(30000);

                        if (retry > 20) {
                            return false;
                        }
                    }
                }
            }
        } catch (JedisConnectionException e) {
            logger.error("Cannot connect to Redis to INFO to checking loading AOF");
        } finally {
            localRedisDisconnect();
        }

        return false;

    }

    @Override
    public boolean isAlive() {
        // Not using localJedis variable as it can be used by
        // ProcessMonitorTask as well.
        return JedisUtils.isAliveWithRetry(REDIS_ADDRESS, REDIS_PORT);
    }

    public long getUptime() {
        return 0;
    }

    private class AlivePeer {
        String selectedPeer;
        Jedis selectedJedis;
        Long upTime;
    }

    private AlivePeer peerNodeSelection(String peer, Jedis peerJedis) {
        AlivePeer currentAlivePeer = new AlivePeer();
        currentAlivePeer.selectedPeer = peer;
        currentAlivePeer.selectedJedis = peerJedis;
        String s = peerJedis.info(); // Parsing the info command on the peer
                                     // node
        RedisInfoParser infoParser = new RedisInfoParser();
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(s.getBytes()));
        try {
            Map<String, Long> allInfo = infoParser.parse(reader);
            Iterator iter = allInfo.keySet().iterator();
            String key = null;
            boolean found = false;
            while (iter.hasNext()) {
                key = (String) iter.next();
                if (key.equals("Redis_Server_uptime_in_seconds")) {
                    currentAlivePeer.upTime = allInfo.get(key);
                    found = true;
                    break;
                }
            }
            if (!found) {
                logger.warn("uptime_in_seconds was not found in Redis info");
                return null;
            }
            logger.info("Alive Peer node [" + peer + "] is up for " + currentAlivePeer.upTime + " seconds");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return currentAlivePeer;
    }

    // probably use our Retries Util here
    @Override
    public Bootstrap warmUpStorage(String[] peers) {
        AlivePeer longestAlivePeer = new AlivePeer();
        Jedis peerJedis = null;

        for (String peer : peers) { // Looking into the peers with the same
                                    // token
            logger.info("Peer node [" + peer + "] has the same token!");
            peerJedis = JedisUtils.connect(peer, REDIS_PORT);
            if (peerJedis != null && isAlive()) { // Checking if there are
                                                  // peers, and if so if they
                                                  // are alive

                AlivePeer currentAlivePeer = peerNodeSelection(peer, peerJedis);

                // Checking the one with the longest up time. Disconnect the one
                // that is not the longest.
                if (currentAlivePeer.selectedJedis == null) {
                    logger.error("Cannot find uptime_in_seconds in peer " + peer);
                    return Bootstrap.CANNOT_CONNECT_FAIL;
                } else if (longestAlivePeer.selectedJedis == null) {
                    longestAlivePeer = currentAlivePeer;
                } else if (currentAlivePeer.upTime > longestAlivePeer.upTime) {
                    longestAlivePeer.selectedJedis.disconnect();
                    longestAlivePeer = currentAlivePeer;
                }
            }
        }

        // We check if the select peer is alive and we connect to it.
        if (longestAlivePeer.selectedJedis == null) {
            logger.error("Cannot connect to peer node to bootstrap");
            return Bootstrap.CANNOT_CONNECT_FAIL;
        } else {
            String alivePeer = longestAlivePeer.selectedPeer;
            peerJedis = longestAlivePeer.selectedJedis;

            logger.info("Issue slaveof command on peer [" + alivePeer + "] and port [" + REDIS_PORT + "]");
            startPeerSync(alivePeer, REDIS_PORT);

            long diff = 0;
            long previousDiff = 0;
            short retry = 0;
            short numErrors = 0;
            long startTime = System.currentTimeMillis();

            // Conditions under which warmp up will end
            // 1. number of Jedis errors are 5.
            // 2. number of consecutive increases of offset differences (caused
            // when client produces high load).
            // 3. the difference between offsets is very small or zero
            // (success).
            // 4. warmp up takes more than FP defined minutes (default 20 min).
            // 5. Dynomite has started and is healthy.
            while (numErrors < 5) {
                // sleep 10 seconds in between checks
                sleeper.sleepQuietly(10000);
                try {
                    diff = canPeerSyncStop(peerJedis, startTime);
                } catch (Exception e) {
                    numErrors++;
                }

                // Diff meaning:
                // a. diff == 0 --> we are either in sync or close to sync.
                // b. diff == -1 --> there was an error in sync process.
                // c. diff == -2 --> offset is still zero, peer syncing has not
                // started.
                // d. diff == -3 --> warm up lasted more than bootstrapTime
                if (diff == 0) {
                    break;
                } else if (diff == -1) {
                    logger.error("There was an error in the warm up process - do NOT start Dynomite");
                    peerJedis.disconnect();
                    return Bootstrap.WARMUP_ERROR_FAIL;
                } else if (diff == -2) {
                    startTime = System.currentTimeMillis();
                } else if (diff == -3) {
                    peerJedis.disconnect();
                    return Bootstrap.EXPIRED_BOOTSTRAPTIME_FAIL;
                }

                // Exit conditions:
                // a. retry more than 5 times continuously and if the diff is
                // larger than the previous diff.
                if (previousDiff < diff) {
                    logger.info("Previous diff (" + previousDiff + ") was smaller than current diff (" + diff
                            + ") ---> Retry effort: " + retry);
                    retry++;
                    if (retry == 10) {
                        logger.error("Reached 10 consecutive retries, peer syncing cannot complete");
                        peerJedis.disconnect();
                        return Bootstrap.RETRIES_FAIL;
                    }
                } else {
                    retry = 0;
                }
                previousDiff = diff;
            }

            peerJedis.disconnect();

            if (diff > 0) {
                logger.info("Stopping peer syncing with difference: " + diff);
            }
        }

        return Bootstrap.IN_SYNC_SUCCESS;
    }

    /**
     * Resets Storage to master if it was a slave due to warm up failure.
     */
    @Override
    public boolean resetStorage() {
        logger.info("Checking if Storage needs to be reset to master");
        localRedisConnect();
        String localRedisInfo = null;
        try {
            localRedisInfo = localJedis.info();
        } catch (JedisConnectionException e) {
            // Try to reconnect
            try {
                localRedisConnect();
                localRedisInfo = localJedis.info();
            } catch (JedisConnectionException ex) {
                logger.error("Cannot connect to Redis");
                return false;
            }
        }
        Iterable<String> result = Splitter.on('\n').split(localRedisInfo);

        String role = null;

        for (String line : result) {
            if (line.startsWith("role")) {
                String[] items = line.split(":");
                // logger.info(items[0] + ": " + items[1]);
                role = items[1].trim();
                if (role.equals("slave")) {
                    logger.info("Redis: Stop replication. Switch from slave to master");
                    stopPeerSync();
                }
                return true;
            }
        }

        return false;

    }

    /**
     * Determining if the warm up process can stop
     * 
     * @param peerJedis
     *            Jedis connection with the peer node
     * @param startTime
     * @return Long status code
     * @throws RedisSyncException
     */
    private Long canPeerSyncStop(Jedis peerJedis, long startTime) throws RedisSyncException {

        if (System.currentTimeMillis() - startTime > config.getMaxTimeToBootstrap()) {
            logger.warn("Warm up takes more than " + config.getMaxTimeToBootstrap() / 60000 + " minutes --> moving on");
            return (long) -3;
        }

        logger.info("Checking for peer syncing");
        String peerRedisInfo = peerJedis.info();

        Long masterOffset = -1L;
        Long slaveOffset = -1L;

        // get peer's repl offset
        Iterable<String> result = Splitter.on('\n').split(peerRedisInfo);

        for (String line : result) {
            if (line.startsWith("master_repl_offset")) {
                String[] items = line.split(":");
                logger.info(items[0] + ": " + items[1]);
                masterOffset = Long.parseLong(items[1].trim());

            }

            // slave0:ip=10.99.160.121,port=22122,state=online,offset=17279,lag=0
            if (line.startsWith("slave0")) {
                String[] items = line.split(",");
                for (String item : items) {
                    if (item.startsWith("offset")) {
                        String[] offset = item.split("=");
                        logger.info(offset[0] + ": " + offset[1]);
                        slaveOffset = Long.parseLong(offset[1].trim());
                    }
                }
            }
        }

        if (slaveOffset == -1) {
            logger.error("Slave offset could not be parsed --> check memory overcommit configuration");
            return (long) -1;
        } else if (slaveOffset == 0) {
            logger.info("Slave offset is zero ---> Redis master node still dumps data to the disk");
            return (long) -2;
        }
        Long diff = Math.abs(masterOffset - slaveOffset);

        logger.info("masterOffset: " + masterOffset + " slaveOffset: " + slaveOffset + " current Diff: " + diff
                + " allowable diff: " + config.getAllowableBytesSyncDiff());

        // Allowable bytes sync diff can be configured by a Fast Property.
        // If the difference is very small, then we return zero.
        if (diff < config.getAllowableBytesSyncDiff()) {
            logger.info("master and slave are in sync!");
            return (long) 0;
        } else if (slaveOffset == 0) {
            logger.info("slave has not started syncing");
        }
        return diff;
    }

    private class RedisSyncException extends Exception {
        /**
         * Exception during peer syncing
         */
        private static final long serialVersionUID = -7736577871204223637L;
    }

    /**
     * Generate redis.conf.
     * 
     * @throws IOException
     */
    public void updateConfiguration() throws IOException {

        long storeMaxMem = getStoreMaxMem();

        if (config.getRedisCompatibleEngine().equals(ArdbRocksDbRedisCompatible.DYNO_ARDB)) {
            ArdbRocksDbRedisCompatible rocksDb = new ArdbRocksDbRedisCompatible(storeMaxMem,
                    config.getWriteBufferSize(), config.getArdbRocksDBMaxWriteBufferNumber(),
                    config.getArdbRocksDBMinWriteBuffersToMerge());
            rocksDb.updateConfiguration(ArdbRocksDbRedisCompatible.DYNO_ARDB_CONF_PATH);
        } else {

            // Updating the file.
            logger.info("Updating Redis conf: " + DYNO_REDIS_CONF_PATH);
            Path confPath = Paths.get(DYNO_REDIS_CONF_PATH);
            Path backupPath = Paths.get(DYNO_REDIS_CONF_PATH + ".bkp");

            // backup the original baked in conf only and not subsequent updates
            if (!Files.exists(backupPath)) {
                logger.info("Backing up baked in Redis config at: " + backupPath);
                Files.copy(confPath, backupPath, COPY_ATTRIBUTES);
            }

            if (config.isPersistenceEnabled() && config.persistenceType().equals("aof")) {
                logger.info("Persistence with AOF is enabled");
            } else if (config.isPersistenceEnabled() && !config.persistenceType().equals("aof")) {
                logger.info("Persistence with RDB is enabled");
            }

            // Not using Properties file to load as we want to retain all
            // comments,
            // and for easy diffing with the ami baked version of the conf file.
            List<String> lines = Files.readAllLines(confPath, Charsets.UTF_8);
            boolean saveReplaced = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                boolean isComment = false;
                if (line.startsWith("#")) {
                    isComment = true;
                    String withoutHash = line.substring(1);
                    if (!withoutHash.matches(REDIS_CONF_SAVE_SCHEDULE) && !withoutHash.matches(REDIS_CONF_UNIXSOCKET)
                            && !withoutHash.matches(REDIS_CONF_UNIXSOCKETPERM)) {
                        continue;
                    }
                    line = withoutHash;
                }
                if (line.matches(REDIS_CONF_UNIXSOCKET)) {
                    String unixSocket;
                    // This empty check is to make sure we disable unixsocket
                    // when the FP is deleted.
                    // Mostly this use case will not arise but the code is more
                    // complete now.
                    if (config.getRedisUnixPath().isEmpty()) {
                        unixSocket = "# unixsocket /tmp/redis.sock";
                        logger.info("Resetting Redis property: " + unixSocket);
                    } else {
                        unixSocket = "unixsocket " + config.getRedisUnixPath();
                        logger.info("Updating Redis property: " + unixSocket);
                    }
                    lines.set(i, unixSocket);
                }
                if (line.matches(REDIS_CONF_UNIXSOCKETPERM)) {
                    String unixSocketPerm;
                    if (config.getRedisUnixPath().isEmpty()) {
                        unixSocketPerm = "# unixsocketperm 700";
                        logger.info("Resetting Redis property: " + unixSocketPerm);
                    } else {
                        unixSocketPerm = "unixsocketperm 755";
                        logger.info("Updating Redis property: " + unixSocketPerm);
                    }
                    lines.set(i, unixSocketPerm);
                }

                if (line.matches(REDIS_CONF_MAXMEMORY_PATTERN)) {
                    String maxMemConf = "maxmemory " + storeMaxMem + "kb";
                    logger.info("Updating Redis property: " + maxMemConf);
                    lines.set(i, maxMemConf);
                }
                if (line.matches(REDIS_CONF_DAEMONIZE)) {
                    String daemonize = "daemonize yes";
                    logger.info("Updating Redis property: " + daemonize);
                    lines.set(i, daemonize);
                }
                // Pub/sub
                if (line.contains(REDIS_CONF_PUBSUB)) {
                    String notifyKeySpaceEvents = REDIS_CONF_PUBSUB + " \"" + config.getKeySpaceEvents() + "\"";
                    logger.info("Updating Redis Property: " + notifyKeySpaceEvents);
                    lines.set(i, notifyKeySpaceEvents);
                }

                // Persistence configuration
                if (config.isPersistenceEnabled() && config.persistenceType().equals("aof")) {
                    if (line.matches(REDIS_CONF_APPENDONLY)) {
                        String appendOnly = "appendonly yes";
                        logger.info("Updating Redis property: " + appendOnly);
                        lines.set(i, appendOnly);
                    } else if (line.matches(REDIS_CONF_APPENDFSYNC)) {
                        String appendfsync = "appendfsync no";
                        logger.info("Updating Redis property: " + appendfsync);
                        lines.set(i, appendfsync);
                    } else if (line.matches(REDIS_CONF_AUTOAOFREWRITEPERCENTAGE)) {
                        String autoAofRewritePercentage = "auto-aof-rewrite-percentage 100";
                        logger.info("Updating Redis property: " + autoAofRewritePercentage);
                        lines.set(i, autoAofRewritePercentage);
                    } else if (line.matches(REDIS_CONF_SAVE_SCHEDULE)) {
                        String saveSchedule = "# save 60 10000"; // if we select
                                                                 // AOF, it is
                                                                 // better to
                                                                 // stop
                                                                 // RDB
                        logger.info("Updating Redis property: " + saveSchedule);
                        lines.set(i, saveSchedule);
                    }
                } else if (config.isPersistenceEnabled() && !config.persistenceType().equals("aof")) {
                    if (line.matches(REDIS_CONF_STOP_WRITES_BGSAVE_ERROR)) {
                        String bgsaveerror = "stop-writes-on-bgsave-error no";
                        logger.info("Updating Redis property: " + bgsaveerror);
                        lines.set(i, bgsaveerror);
                    } else if (line.matches(REDIS_CONF_SAVE_SCHEDULE) && !saveReplaced) {
                        saveReplaced = true;
                        String saveSchedule = "save 60 10000"; // after 60 sec
                                                               // if at
                                                               // least 10000
                                                               // keys
                                                               // changed
                        logger.info("Updating Redis property: " + saveSchedule);
                        lines.set(i, saveSchedule);
                    } else if (line.matches(REDIS_CONF_APPENDONLY)) { // if we
                                                                      // select
                                                                      // RDB,
                                                                      // it is
                                                                      // better
                                                                      // to
                                                                      // stop
                                                                      // AOF
                        String appendOnly = "appendonly no";
                        logger.info("Updating Redis property: " + appendOnly);
                        lines.set(i, appendOnly);
                    }
                }
            }

            Files.write(confPath, lines, Charsets.UTF_8, WRITE, TRUNCATE_EXISTING);
        }
    }

    /**
     * Get the maximum amount of memory available for Redis or Memcached.
     * 
     * @return the maximum amount of storage available for Redis or Memcached in
     *         KB
     */
    public long getStoreMaxMem() {
        int memPct = config.getStorageMaxMemoryPercent();
        // Long is big enough for the amount of ram is all practical systems
        // that we deal with.
        long totalMem = getTotalAvailableSystemMemory();
        long storeMaxMem = (totalMem * memPct) / 100;
        storeMaxMem = ((totalMem - storeMaxMem) > GB_2_IN_KB) ? storeMaxMem : (totalMem - GB_2_IN_KB);

        logger.info(String.format("totalMem: %s setting storage max mem to %s", totalMem, storeMaxMem));
        return storeMaxMem;
    }

    /**
     * Get the amount of memory available on this instance.
     * 
     * @return total available memory (RAM) on instance in KB
     */
    public long getTotalAvailableSystemMemory() {
        String memInfo;
        try {
            memInfo = new Scanner(new File(PROC_MEMINFO_PATH)).useDelimiter("\\Z").next();
        } catch (FileNotFoundException e) {
            String errMsg = String.format("Unable to find %s file for retrieving memory info.", PROC_MEMINFO_PATH);
            logger.error(errMsg);
            throw new RuntimeException(errMsg);
        }

        Matcher matcher = MEMINFO_PATTERN.matcher(memInfo);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                logger.info("Failed to parse long", e);
            }
        }

        String errMsg = String.format("Could not extract total mem using pattern %s from:\n%s ", MEMINFO_PATTERN,
                memInfo);
        logger.error(errMsg);
        throw new RuntimeException(errMsg);
    }

    @Override
    public String getStartupScript() {
        if (config.getRedisCompatibleEngine().equals(ArdbRocksDbRedisCompatible.DYNO_ARDB)) {
            return ArdbRocksDbRedisCompatible.ARDB_ROCKSDB_START_SCRIPT;
        }
        return REDIS_START_SCRIPT;
    }

    @Override
    public String getStopScript() {
        if (config.getRedisCompatibleEngine().equals(ArdbRocksDbRedisCompatible.DYNO_ARDB)) {
            return ArdbRocksDbRedisCompatible.ARDB_ROCKSDB_STOP_SCRIPT;
        }
        return REDIS_STOP_SCRIPT;
    }

    @Override
    public String getIpAddress() {
        return REDIS_ADDRESS;
    }

    @Override
    public int getPort() {
        return REDIS_PORT;
    }

    @Override
    public void check(HealthIndicatorCallback healthCallback) {
        if (redisHealth) {
            healthCallback.inform(Health.healthy().withDetail("Redis", "All good!").build());
        } else {
            logger.info("Reporting Redis is down to Health check callback");
            healthCallback.inform(Health.unhealthy().withDetail("Redis", "Down!").build());

        }
    }

    public String getUnixPath() {
        return config.getRedisUnixPath();
    }
}
