package com.netflix.nfsidecar.configSource.test;

import org.junit.Assert;
import org.junit.Test;

import com.netflix.nfsidecar.configSource.OSPropertiesConfigSource;

/**
 * Unit Tests for OSPropertiesConfigSource
 * 
 * @author diegopacheco
 *
 */
public class OSPropertiesConfigSourceTest {

	@Test
	public void intializeTest() {
		OSPropertiesConfigSource osPropsSource = new OSPropertiesConfigSource();
		osPropsSource.intialize("x-microservice1-us-west-2a", "us-west-2");
	}

	@Test
	public void okVarTest() {
		OSPropertiesConfigSource osPropsSource = new OSPropertiesConfigSource();
		osPropsSource.intialize("x-microservice1-us-west-2a", "us-west-2");

		Assert.assertNotNull(osPropsSource.get("PATH"));
	}
	
	@Test
	public void wrongVarTest() {
		OSPropertiesConfigSource osPropsSource = new OSPropertiesConfigSource();
		osPropsSource.intialize("x-microservice1-us-west-2a", "us-west-2");

		Assert.assertNull(osPropsSource.get("JAVA.HOME2_"));
	}
	
	@Test
	public void okVarSetTest() {
		OSPropertiesConfigSource osPropsSource = new OSPropertiesConfigSource();
		osPropsSource.intialize("x-microservice1-us-west-2a", "us-west-2");
		osPropsSource.set("my.var.value", "works");
		
		Assert.assertNotNull(osPropsSource.get("my.var.value"));
	}
	
	@Test
	public void okVarSizingTest() {
		OSPropertiesConfigSource osPropsSource = new OSPropertiesConfigSource();
		osPropsSource.intialize("x-microservice1-us-west-2a", "us-west-2");
		Assert.assertNull(osPropsSource.get("JAVA.HOME2_"));
		Assert.assertTrue(osPropsSource.size() >= 2);
	}
	
	@Test
	public void okVarContainsTest() {
		OSPropertiesConfigSource osPropsSource = new OSPropertiesConfigSource();
		osPropsSource.intialize("x-microservice1-us-west-2a", "us-west-2");
		Assert.assertNotNull(osPropsSource.contains("JAVA.HOME2_"));
	}

}
