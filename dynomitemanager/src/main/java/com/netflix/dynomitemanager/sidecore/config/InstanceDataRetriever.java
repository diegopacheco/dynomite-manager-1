/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.netflix.dynomitemanager.sidecore.config;

public interface InstanceDataRetriever {

    /**
     * Get the data center (AWS region) for the current instance.
     * @return the instance's data center (AWS region)
     */
    String getDataCenter();

    /**
     * Get the rack (AWS AZ) for the current instance.
     * @return the instance's rack (AWS AZ)
     */
    String getRac();

	String getPublicHostname();

	String getPublicIP();

	String getInstanceId();

	String getInstanceType();

	String getMac(); //fetch id of the network interface for running instance

	String getVpcId(); //the id of the vpc for running instance

	String getSecurityGroupName();
}
