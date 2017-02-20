package com.netflix.dynomitemanager.sidecore.aws.test;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;

public class AWSMembershipTest {
	
	@Test
	public void testJoin(){
		
		Map<String,String> instanceIds = new HashMap<>();
		instanceIds.put("i-asqasdsdvc23", "us-west-2a");
		instanceIds.put("i-asqasdsdvc24", "us-west-2b");
		instanceIds.put("i-asqasdsdvc25", "us-west-2c");
		
		String result = StringUtils.join(instanceIds.keySet(), ",");
		System.out.println(result);
		Assert.assertNotNull(result);
		
	}
	
}
