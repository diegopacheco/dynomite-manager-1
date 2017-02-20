package com.netflix.dynomitemanager.identity.test;

import org.junit.Assert;
import org.junit.Test;

import com.netflix.dynomitemanager.defaultimpl.IConfiguration;
import com.netflix.dynomitemanager.defaultimpl.test.BlankConfiguration;
import com.netflix.dynomitemanager.identity.AppsInstance;
import com.netflix.dynomitemanager.identity.IAppsInstanceFactory;
import com.netflix.dynomitemanager.identity.IMembership;
import com.netflix.dynomitemanager.identity.InstanceEnvIdentity;
import com.netflix.dynomitemanager.identity.InstanceIdentity;
import com.netflix.dynomitemanager.identity.InstanceIdentity.GetNewToken;
import com.netflix.dynomitemanager.identity.LocalInstanceEnvIdentity;
import com.netflix.dynomitemanager.sidecore.utils.ITokenManager;
import com.netflix.dynomitemanager.sidecore.utils.Sleeper;
import com.netflix.dynomitemanager.sidecore.utils.ThreadSleeper;
import com.netflix.dynomitemanager.sidecore.utils.TokenManager;

public class InstanceIdentityTest {
	
	@Test
	public void testGetNewTokenLogic() throws Throwable{
		
		IAppsInstanceFactory factory = new FakeDBAppsInstanceFactory();
		IMembership membership = new FakeAWSMembership(); 
		IConfiguration config = new BlankConfiguration();
		Sleeper sleeper = new ThreadSleeper();
		ITokenManager tokenManager = new TokenManager();
		InstanceEnvIdentity insEnvIdentity = new LocalInstanceEnvIdentity();
		
		GetNewToken gnt = new InstanceIdentity(factory, membership, config, sleeper, tokenManager, insEnvIdentity).new GetNewToken();
		AppsInstance result = gnt.call();
		
		Assert.assertNotNull(result);
		System.out.println(result);
		
		//
		// TODO: Test new token algo logic here
		//
		
		
		
	}
	
}
