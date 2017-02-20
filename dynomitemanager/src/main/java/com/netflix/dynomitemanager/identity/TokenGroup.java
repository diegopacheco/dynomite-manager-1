package com.netflix.dynomitemanager.identity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 
 * This algorithm imply that the RACK size is always multiple of 3. So its 3, 6, 9, 12, 15. 18, 21, etc...
 * IF the Rack Size is not multiple of 3 this will not work and you will not have the correct behavior.
 * 
 * @author diego.pacheco
 *
 */
public class TokenGroup {
	
	private Map<String,String> originalInstanceIds = null;
	private Map<String,Integer> shuffledAZsInstanceIds = null;
	
	private List<String> az_2a = new ArrayList<>();
	private List<String> az_2b = new ArrayList<>();
	private List<String> az_2c = new ArrayList<>();
	
	/**
	 * Spread a simple MAP of instanceID mixed with us-west-2 a ,b and c from 1 bucket to 3 different buckets.  
	 * 
	 * @param instanceIds Map(String,String) 
	 */
	public TokenGroup(Map<String,String> instanceIds) {
		init(instanceIds);
	}
	
	private void init(Map<String,String> instanceIds){
		splitBucketPerAZ(instanceIds);
		shuffledAZsInstanceIds = shuffledAZs();
	}

	private void splitBucketPerAZ(Map<String, String> instanceIds) {
		for(String k: instanceIds.keySet()){
			switch(instanceIds.get(k)) {
				case ("us-west-2a"): az_2a.add(k); break;
				case ("us-west-2b"): az_2b.add(k); break;
				case ("us-west-2c"): az_2c.add(k); break;
				default: break;
			}
		}
		originalInstanceIds = instanceIds;
	}
	
	private Map<String,Integer> shuffledAZs(){
		
		// One per AZ. It will always be 3.
		int resiliency_copies = 3;
		int counter = 0;
		Integer current_token = 100;
		
		Iterator<String> iterator2a = az_2a.iterator();
		Iterator<String> iterator2b = az_2b.iterator();
		Iterator<String> iterator2c = az_2c.iterator();
		
		Map<String,Integer> shuffledAZs = new HashMap<>();
		for(String k : originalInstanceIds.keySet()){
			
			if (counter < resiliency_copies){
				counter++;
			}else{
				current_token = current_token + 100;
				counter = 1;
			}
			
			switch(counter){
				case (1): shuffledAZs.put( iterator2a.next() , current_token);  break;
				case (2): shuffledAZs.put( iterator2b.next() , current_token);  break;
				case (3): shuffledAZs.put( iterator2c.next() , current_token);  break;
			}

		}
		
		return shuffledAZs;
	}

	public Map<String, Integer> getShuffledAZsInstanceIds() {
		return shuffledAZsInstanceIds;
	}

	public List<String> getAz_2a() {
		return az_2a;
	}
	public List<String> getAz_2b() {
		return az_2b;
	}
	public List<String> getAz_2c() {
		return az_2c;
	}
	
}
