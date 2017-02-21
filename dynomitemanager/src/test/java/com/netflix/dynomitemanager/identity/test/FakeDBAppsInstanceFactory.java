package com.netflix.dynomitemanager.identity.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.netflix.dynomitemanager.identity.AppsInstance;
import com.netflix.dynomitemanager.identity.IAppsInstanceFactory;

public class FakeDBAppsInstanceFactory implements IAppsInstanceFactory{

	@Override
	public List<AppsInstance> getAllIds(String appName) {
		return new ArrayList<>();
	}

	@Override
	public List<AppsInstance> getLocalDCIds(String appName, String region) {
		return new ArrayList<>();
	}

	@Override
	public AppsInstance getInstance(String appName, String dc, int id) {
		return new AppsInstance();
	}

	@Override
	public AppsInstance create(String app, int id, String instanceID, String hostname, String ip, String rac, Map<String, Object> volumes, String token, String datacenter) {
		AppsInstance ret = new AppsInstance();
		ret.setApp(app);
		ret.setId(id);
		ret.setInstanceId(instanceID);
		ret.setHost(hostname);
		ret.setHostIP(ip);
		ret.setRack(rac);
		ret.setVolumes(volumes);
		ret.setToken(token);
		ret.setDatacenter(datacenter);
		
		System.out.println("create called with :" + ret);
		return ret;
	}

	@Override
	public void delete(AppsInstance inst) {
		//System.out.println("delete called with: " + inst);
	}

	@Override
	public void update(AppsInstance inst) {
		//System.out.println("update called with: " + inst);
	}

	@Override
	public void sort(List<AppsInstance> return_) {
		//System.out.println("sort called with: " + return_);
	}

	@Override
	public void attachVolumes(AppsInstance instance, String mountPath, String device) {
		//System.out.println("attachVolumes called with: " + instance + " - " + mountPath + " - " + device);
	}
	
	
	
}
