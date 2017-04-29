package com.netflix.dynomitemanager.supplier;

import static com.netflix.dynomitemanager.defaultimpl.DynomiteManagerConfiguration.LOCAL_ADDRESS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;
import com.google.inject.Inject;
import com.netflix.astyanax.connectionpool.Host;
import com.netflix.dynomitemanager.sidecore.IConfiguration;

/***
 * CassandraLocalHostsSupplier gets cassandra seeds
 * 
 * @author diegopacheco
 *
 */
public class CassandraLocalHostsSupplier implements HostSupplier{
	
	   private static final Logger logger = LoggerFactory.getLogger(CassandraLocalHostsSupplier.class);
	    private static final String errMsg = "No Cassandra hosts were provided. Use DM_CASSANDRA_CLUSTER_SEEDS or configuration property getCassandraSeeds().";
		private IConfiguration config;

		@Inject
		public CassandraLocalHostsSupplier(IConfiguration config) {
			this.config = config;
		}

		@Override
		public Supplier<List<Host>> getSupplier(String clusterName) {
			final List<Host> hosts = new ArrayList<Host>();

			String bootCluster = config.getCassandraClusterName();

			if (bootCluster == null)
				bootCluster = "";

	        // This condition will always be true at runtime. The else condition is only used by a unit test.
			if (bootCluster.equals(clusterName)) {
	            String seeds = config.getCassandraSeeds();
	            logger.info("Cassandra hosts (seed servers) for cluster topology: " + seeds);

				if (seeds == null || "".equals(seeds))
					throw new RuntimeException(errMsg);

				List<String> cassHostnames = new ArrayList<String>(Arrays.asList(StringUtils.split(seeds, ",")));

				if (cassHostnames.size() == 0)
					throw new RuntimeException(errMsg);

				for (String cassHost : cassHostnames) {
					hosts.add(new Host(cassHost, config.getCassandraThriftPort()));
				}
			} else {
	            // This branch will never be reached in production. It is only used by CassandraHostsSupplierTest.
	            // TODO: Remove this condition and rewrite the test.
				hosts.add(new Host(LOCAL_ADDRESS, config.getCassandraThriftPort()).setRack("localdc"));
			}

			return new Supplier<List<Host>>() {
				@Override
				public List<Host> get() {
					return hosts;
				}
			};

		}

	
}
