package com.netflix.nfsidecar.configSource;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

/**
 * OSPropertiesConfigSource gets all env vars from OS and converts "_" into ".". 
 * 
 * @author diegopacheco 
 * @since 10-20-2017
 *
 */
public class OSPropertiesConfigSource extends AbstractConfigSource {

	private static final Logger logger = LoggerFactory.getLogger(OSPropertiesConfigSource.class.getName());

	private final Map<String, String> data = Maps.newConcurrentMap();

	public OSPropertiesConfigSource() {}

	public OSPropertiesConfigSource(final Properties properties) {
		checkNotNull(properties);
		clone(properties);
	}

	@Override
	public void intialize(final String asgName, final String region) {
		super.intialize(asgName, region);
		
		Map<String, String> env = System.getenv();
		
		logger.info("Getting all System Envs Vars... ");
		logger.debug("Using Envs: { " + env.keySet() + "}");
		
		for (String key : env.keySet()) {
			String newKey = key.replaceAll("_", ".");
			data.put(newKey, env.get(key));
		}
		
	}

	@Override
	public String get(final String prop) {
		return data.get(prop);
	}

	@Override
	public void set(final String key, final String value) {
		Preconditions.checkNotNull(value, "Value can not be null for configurations.");
		data.put(key, value);
	}

	@Override
	public int size() {
		return data.size();
	}

	@Override
	public boolean contains(final String prop) {
		return data.containsKey(prop);
	}

	/**
     * Clones all the values from the properties.  If the value is null, it will be ignored.
     *
     * @param properties to clone
     */
    private void clone(final Properties properties) 
    {
        if (properties.isEmpty()) return;

        synchronized (properties) 
        {
            for (final String key : properties.stringPropertyNames()) 
            {
                final String value = properties.getProperty(key);
                if (!Strings.isNullOrEmpty(value)) 
                {
                    data.put(key, value);
                }
            }
        }
    }
    
}
