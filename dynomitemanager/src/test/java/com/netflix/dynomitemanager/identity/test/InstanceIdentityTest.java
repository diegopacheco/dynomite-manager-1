package com.netflix.dynomitemanager.identity.test;

import java.util.Map;

import org.apache.cassandra.thrift.Cassandra.system_add_column_family_args;
import org.apache.commons.collections.map.HashedMap;
import org.junit.Assert;
import org.junit.Test;

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
		
		Map<String,String> resultSummary = new HashedMap();
		
		IAppsInstanceFactory factory = new FakeDBAppsInstanceFactory();
		IMembership membership = new FakeAWSMembership(); 
		BlankConfiguration config = new BlankConfiguration();
		Sleeper sleeper = new ThreadSleeper();
		ITokenManager tokenManager = new TokenManager();
		InstanceEnvIdentity insEnvIdentity = new LocalInstanceEnvIdentity();
		
		GetNewToken gnt = new InstanceIdentity(factory, membership, config, sleeper, tokenManager, insEnvIdentity).new GetNewToken();
		AppsInstance result = gnt.call();
		Assert.assertNotNull(result);
		System.out.println(result);
		resultSummary.put("i-wef43fv01", result.getToken());
		
		config.setInstanceName("i-wef43fv02");
		gnt = new InstanceIdentity(factory, membership, config, sleeper, tokenManager, insEnvIdentity).new GetNewToken();
		result = gnt.call();
		Assert.assertNotNull(result);
		System.out.println(result.getToken());
		resultSummary.put("i-wef43fv02", result.getToken());
		
		config.setInstanceName("i-wef43fv03");
		gnt = new InstanceIdentity(factory, membership, config, sleeper, tokenManager, insEnvIdentity).new GetNewToken();
		result = gnt.call();
		Assert.assertNotNull(result);
		System.out.println(result.getToken());
		resultSummary.put("i-wef43fv03", result.getToken());
		
		config.setInstanceName("i-wef43fv04");
		gnt = new InstanceIdentity(factory, membership, config, sleeper, tokenManager, insEnvIdentity).new GetNewToken();
		result = gnt.call();
		Assert.assertNotNull(result);
		System.out.println(result.getToken());
		resultSummary.put("i-wef43fv04", result.getToken());
		
		config.setInstanceName("i-wef43fv05");
		gnt = new InstanceIdentity(factory, membership, config, sleeper, tokenManager, insEnvIdentity).new GetNewToken();
		result = gnt.call();
		Assert.assertNotNull(result);
		System.out.println(result.getToken());
		resultSummary.put("i-wef43fv05", result.getToken());
		
		config.setInstanceName("i-wef43fv06");
		gnt = new InstanceIdentity(factory, membership, config, sleeper, tokenManager, insEnvIdentity).new GetNewToken();
		result = gnt.call();
		Assert.assertNotNull(result);
		System.out.println(result.getToken());
		resultSummary.put("i-wef43fv06", result.getToken());
		
		System.out.println("----");
		System.out.println("----");
		System.out.println("Summary");
		System.out.println("----");
		System.out.println("----");
		
		for(String k : resultSummary.keySet()){
			System.out.println("Key: " + k + " token: " + resultSummary.get(k));
		}
	
		System.out.println("----");
		System.out.println(" AZ Check: ");
		System.out.println("----");
		System.out.println("---- i-wef43fv01 IS on :: us-west-2a");
		System.out.println("---- i-wef43fv02 IS on :: us-west-2b");
		System.out.println("---- i-wef43fv03 IS on :: us-west-2c");
		System.out.println("---- i-wef43fv04 IS on :: us-west-2a");
		System.out.println("---- i-wef43fv05 IS on :: us-west-2b");
		System.out.println("---- i-wef43fv06 IS on :: us-west-2c");
		
	}
	
}
