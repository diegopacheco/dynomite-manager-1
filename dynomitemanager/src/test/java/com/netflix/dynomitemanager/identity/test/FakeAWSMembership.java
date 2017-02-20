package com.netflix.dynomitemanager.identity.test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netflix.dynomitemanager.identity.IMembership;

public class FakeAWSMembership implements IMembership {

	@Override
	public Map<String, String> getRacMembership() {
		Map<String, String> instances = new HashMap<String, String>();
		instances.put("i-wef43fv01", "us-west-2a");
		instances.put("i-wef43fv02", "us-west-2b");
		instances.put("i-wef43fv03", "us-west-2c");
		instances.put("i-wef43fv04", "us-west-2a");
		instances.put("i-wef43fv05", "us-west-2b");
		instances.put("i-wef43fv06", "us-west-2c");
		return instances;
	}

	@Override
	public List<String> getCrossAccountRacMembership() {
		return null;
	}

	@Override
	public int getRacMembershipSize() {
		return 6; // because there are 6 instances on the ASG
	}

	@Override
	public int getCrossAccountRacMembershipSize() {
		return 0;
	}

	@Override
	public int getRacCount() {
		return 0;
	}

	@Override
	public List<String> listACL(int from, int to) {
		return null;
	}

	@Override
	public void addACL(Collection<String> listIPs, int from, int to) {
		System.out.println("AddACL Called with: " + listIPs + " - " + from + " - " + to);
	}

	@Override
	public void removeACL(Collection<String> listIPs, int from, int to) {
		System.out.println("removeACL Called with: " + listIPs + " - " + from + " - " + to);
	}

	@Override
	public void expandRacMembership(int count) {
		System.out.println("expandRacMembership Called with: " + count);
	}

}
