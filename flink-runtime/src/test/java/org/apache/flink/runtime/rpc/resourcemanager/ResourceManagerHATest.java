/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.rpc.resourcemanager;

import akka.util.Timeout;
import org.apache.flink.runtime.highavailability.TestingHighAvailabilityServices;
import org.apache.flink.runtime.leaderelection.TestingLeaderElectionService;
import org.apache.flink.runtime.rpc.RpcService;
import org.apache.flink.runtime.rpc.TestingRpcService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.Future;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * resourceManager HA test, including grant leadership and revoke leadership
 */
public class ResourceManagerHATest {

	private RpcService rpcService;

	@Before
	public void setup() throws Exception {
		rpcService = new TestingRpcService();
	}

	@After
	public void teardown() throws Exception {
		rpcService.stopService();
	}

	@Test
	public void testGrantAndRevokeLeadership() throws Exception {
		TestingLeaderElectionService leaderElectionService = new TestingLeaderElectionService();
		TestingHighAvailabilityServices highAvailabilityServices = new TestingHighAvailabilityServices();
		highAvailabilityServices.setResourceManagerLeaderElectionService(leaderElectionService);

		final ResourceManager resourceManager = new ResourceManager(rpcService, highAvailabilityServices);
		resourceManager.start();
		// before grant leadership, resourceManager's leaderId is null
		Assert.assertNull(resourceManager.getLeaderSessionID());
		final UUID leaderId = UUID.randomUUID();
		leaderElectionService.isLeader(leaderId);
		// after grant leadership, resourceManager's leaderId has value
		Assert.assertEquals(getLatestLeaderId(resourceManager), leaderId);
		// then revoke leadership, resourceManager's leaderId is null again
		leaderElectionService.notLeader();
		Assert.assertNull(getLatestLeaderId(resourceManager));
	}


	private UUID getLatestLeaderId(final ResourceManager resourceManager) throws Exception {
		Timeout timeout = new Timeout(200, TimeUnit.MILLISECONDS);
		Future<UUID> actualLeaderIdFuture = resourceManager.callAsync(new Callable<UUID>() {
			@Override
			public UUID call() throws Exception {
				return resourceManager.getLeaderSessionID();
			}
		}, timeout);
		UUID actualValue = Await.result(actualLeaderIdFuture, timeout.duration());
		return actualValue;
	}

}
