/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.apache.ignite.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.transactions.*;
import org.gridgain.grid.cache.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.spi.swapspace.file.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.testframework.junits.common.*;

import static org.gridgain.grid.cache.GridCacheAtomicityMode.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCacheDistributionMode.*;
import static org.apache.ignite.transactions.GridCacheTxIsolation.*;
import static org.apache.ignite.transactions.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheWriteSynchronizationMode.*;

/**
 * Test ensuring that values are visible inside OPTIMISTIC transaction in co-located cache.
 */
public class GridCacheColocatedOptimisticTransactionSelfTest extends GridCommonAbstractTest {
    /** Grid count. */
    private static final int GRID_CNT = 3;

    /** Cache name. */
    private static final String CACHE = "cache";

    /** Key. */
    private static final Integer KEY = 1;

    /** Value. */
    private static final String VAL = "val";

    /** Shared IP finder. */
    private static final TcpDiscoveryIpFinder IP_FINDER = new TcpDiscoveryVmIpFinder(true);

    /** Grids. */
    private static Ignite[] ignites;

    /** Regular caches. */
    private static GridCache<Integer, String>[] caches;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration c = super.getConfiguration(gridName);

        c.getTransactionsConfiguration().setTxSerializableEnabled(true);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();

        disco.setIpFinder(IP_FINDER);

        GridCacheConfiguration cc = new GridCacheConfiguration();

        cc.setName(CACHE);
        cc.setCacheMode(PARTITIONED);
        cc.setAtomicityMode(TRANSACTIONAL);
        cc.setDistributionMode(PARTITIONED_ONLY);
        cc.setBackups(1);
        cc.setWriteSynchronizationMode(FULL_SYNC);
        cc.setSwapEnabled(true);
        cc.setEvictSynchronized(false);
        cc.setEvictNearSynchronized(false);

        c.setDiscoverySpi(disco);
        c.setCacheConfiguration(cc);
        c.setSwapSpaceSpi(new FileSwapSpaceSpi());

        return c;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override protected void beforeTest() throws Exception {
        ignites = new Ignite[GRID_CNT];
        caches = new GridCache[GRID_CNT];

        for (int i = 0; i < GRID_CNT; i++) {
            ignites[i] = startGrid(i);

            caches[i] = ignites[i].cache(CACHE);
        }
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();

        caches = null;
        ignites = null;
    }

    /**
     * Perform test.
     *
     * @throws Exception If failed.
     */
    public void testOptimisticTransaction() throws Exception {
        for (GridCache<Integer, String> cache : caches) {
            IgniteTx tx = cache.txStart(OPTIMISTIC, REPEATABLE_READ);

            try {
                cache.put(KEY, VAL);

                tx.commit();
            }
            finally {
                tx.close();
            }

            for (GridCache<Integer, String> cacheInner : caches) {
                tx = cacheInner.txStart(OPTIMISTIC, REPEATABLE_READ);

                try {
                    assert F.eq(VAL, cacheInner.get(KEY));

                    tx.commit();
                }
                finally {
                    tx.close();
                }
            }

            tx = cache.txStart(OPTIMISTIC, REPEATABLE_READ);

            try {
                cache.remove(KEY);

                tx.commit();
            }
            finally {
                tx.close();
            }
        }
    }
}
