package com.netflix.dynomitemanager.identity.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.netflix.dynomitemanager.identity.TokenGroup;


public class TokenGroupTest {
	
	@Test
	public void testGroupping(){
		
		Map<String, String> instanceIDs = new HashMap<String, String>();
		instanceIDs.put("i-wef43fv01", "us-west-2a");
		instanceIDs.put("i-wef43fv02", "us-west-2b");
		instanceIDs.put("i-wef43fv03", "us-west-2c");
		instanceIDs.put("i-wef43fv04", "us-west-2a");
		instanceIDs.put("i-wef43fv05", "us-west-2b");
		instanceIDs.put("i-wef43fv06", "us-west-2c");
		
		TokenGroup tg = new TokenGroup(instanceIDs);
		List<String> az2a = tg.getAz_2a();
		List<String> az2b = tg.getAz_2b();
		List<String> az2c = tg.getAz_2c();
		
		Assert.assertNotNull(az2a);
		Assert.assertNotNull(az2b);
		Assert.assertNotNull(az2c);
		
		System.out.println("2A");
		System.out.println(az2a);
		System.out.println("");
		System.out.println("2B");
		System.out.println(az2b);
		System.out.println("");
		System.out.println("2C");
		System.out.println(az2c);
		
	}
	
}
