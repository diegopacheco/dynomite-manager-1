package com.netflix.nfsidecar.archaius;

import javax.inject.Inject;

import com.netflix.archaius.api.Config;

/**
 * DefaultPropertyFactory was created in order to make injection possible since 
 * com.netflix.archaius.DefaultPropertyFactory does not have @Inject on constructor.
 * 
 * @author diegopacheco
 *
 */
public class DefaultPropertyFactory extends com.netflix.archaius.DefaultPropertyFactory {
	
	@Inject
    public DefaultPropertyFactory(Config config) {
       super(config);
    }
	
}
