/**
 * Copyright 2016 Netflix, Inc. <p/> Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at <p/>
 * http://www.apache.org/licenses/LICENSE-2.0 <p/> Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package com.netflix.dynomitemanager.sidecore.utils.test;

import org.junit.Assert;
import org.junit.Test;

import com.netflix.dynomitemanager.sidecore.utils.TokenManager;

/**
 * Unit tests for TokenManager
 *
 * @author diegopacheco
 *
 */
public class TokenManagerTest {

	@Test
	public void createTokenTest() {
		TokenManager tm = new TokenManager();
		String token = tm.createToken(0, 1, "us-west-2");
		Assert.assertNotNull(token);
		Assert.assertTrue(!"".equals(token));
		Assert.assertEquals("1383429731", token);
	}

	@Test
	public void createToken2Test() {
		TokenManager tm = new TokenManager();
		String token = tm.createToken(1, 2, "us-west-2");
		Assert.assertNotNull(token);
		Assert.assertTrue(!"".equals(token));
		Assert.assertEquals("3530913378", token);
	}

	@Test
	public void createTokenRackAndSizeTest() {
		TokenManager tm = new TokenManager();
		String token = tm.createToken(1, 2, 2, "us-west-2");
		Assert.assertNotNull(token);
		Assert.assertTrue(!"".equals(token));
		Assert.assertEquals("2457171554", token);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createTokenWorngCountTest() {
		TokenManager tm = new TokenManager();
		tm.createToken(0, -1, "us-west-2");
	}

	@Test(expected = IllegalArgumentException.class)
	public void createTokenWorngSlotTest() {
		TokenManager tm = new TokenManager();
		tm.createToken(-1, 0, "us-west-2");
	}
	
	@Test
	public void createTokenWorngDCTest() {
		TokenManager tm = new TokenManager();
		String t = tm.createToken(100, 6, null);
		Assert.assertEquals("4246741211", t);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createTokenWorngRackCountTest() {
		TokenManager tm = new TokenManager();
		tm.createToken(1, -1, 2, "us-west-2");
	}
	
	@Test
	public void createTokenWorngSlot() {
		TokenManager tm = new TokenManager();
		String t = tm.createToken(0, 6, 2, "us-west-2");
		Assert.assertEquals("1383429731", t);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createTokenWorngSizeTest() {
		TokenManager tm = new TokenManager();
		tm.createToken(1, 1, -1, "us-west-2");
	}

	@Test
	public void createRegionOffSet() {
		TokenManager tm = new TokenManager();
		tm.createToken(0, 2, "us-west-2");
		int offSet = tm.regionOffset("us-west-2");
		Assert.assertTrue(offSet >= 1);
		Assert.assertEquals(1383429731, offSet);
	}
	
	@Test
	public void testCreateTokenSimple(){
		TokenManager tm = new TokenManager();
		String token = tm.createToken(100, 6, "us-west-2");
		Assert.assertEquals("4246741211", token);
		
		token = tm.createToken(200, 6, "us-west-2");
		Assert.assertEquals("2815085396", token);
		
		token = tm.createToken(300, 6, "us-west-2");
		Assert.assertEquals("1383429581", token);
		
		token = tm.createToken(100, 6, "us-west-2a");
		Assert.assertEquals("4246741211", token);
		
		token = tm.createToken(1, 6, "us-west-2a");
		Assert.assertEquals("2099257613", token);
		
		token = tm.createToken(2, 6, "us-west-2a");
		Assert.assertEquals("2815085495", token);
		
		token = tm.createToken(100, 6, "us-west-2b");
		Assert.assertEquals("4246741211", token);
		
		token = tm.createToken(100, 6, "us-west-2c");
		Assert.assertEquals("4246741211", token);
		
		token = tm.createToken(100, 1, null);
		Assert.assertEquals("1383429731", token);
		
		token = tm.createToken(1, 1, null);
		Assert.assertEquals("1383429731", token);
		
		token = tm.createToken(0, 1, "us-west-1");
		Assert.assertEquals("1383429731", token);
		
		token = tm.createToken(1, 1, "");
		Assert.assertEquals("1383429731", token);
		
		token = tm.createToken(100, 6, "It_does_not_matter_this_will_be_ok");
		Assert.assertEquals("4246741211", token);
		
		token = tm.createToken(100, 7, "");
		Assert.assertEquals("2610563201", token);
		
	}
	
	@Test
	public void testCreateTokenSimulation(){
		
		TokenManager tm = new TokenManager();
		String token = tm.createToken(100, 6, "asg-name1-us-west-2a");
		Assert.assertEquals("4246741211", token);
		
		token = tm.createToken(200, 6, "asg-name1-us-west-2a");
		Assert.assertEquals("2815085396", token);
		
		token = tm.createToken(300, 6, "asg-name1-us-west-2a");
		Assert.assertEquals("1383429581", token);
		
	}


}
