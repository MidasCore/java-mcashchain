/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.midasprotocol.core.config.args;

import com.google.common.collect.Lists;
import io.grpc.internal.GrpcUtil;
import io.grpc.netty.NettyServerBuilder;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.core.Constant;

@Slf4j
public class ArgsTest {

	@After
	public void destroy() {
		Args.clearParam();
	}

	@Test
	public void get() {
		Args.setParam(new String[]{"-w"}, Constant.TEST_CONF);

		Args args = Args.getInstance();
		Assert.assertEquals("database", args.getStorage().getDbDirectory());

		Assert.assertEquals(0, args.getSeedNode().getIpList().size());

		GenesisBlock genesisBlock = args.getGenesisBlock();

		Assert.assertEquals(3, genesisBlock.getAssets().size());

		Assert.assertEquals(3, genesisBlock.getWitnesses().size());

		Assert.assertEquals("0", genesisBlock.getTimestamp());

		Assert.assertEquals("0x0000000000000000000000000000000000000000000000000000000000000000",
			genesisBlock.getParentHash());

		Assert.assertEquals(
			Lists.newArrayList("cf790eaa22cdda7cc04250f5f54043f8734080dedc502514ae16c2f000be66de"),
			args.getLocalWitnesses().getPrivateKeys());

		Assert.assertTrue(args.isNodeDiscoveryEnable());
		Assert.assertTrue(args.isNodeDiscoveryPersist());
		Assert.assertEquals("127.0.0.1", args.getNodeDiscoveryBindIp());
		Assert.assertEquals("46.168.1.1", args.getNodeExternalIp());
		Assert.assertEquals(18888, args.getNodeListenPort());
		Assert.assertEquals(2000, args.getNodeConnectionTimeout());
		Assert.assertEquals(0, args.getActiveNodes().size());
		Assert.assertEquals(30, args.getNodeMaxActiveNodes());
		Assert.assertEquals(43, args.getNodeP2pVersion());
		//Assert.assertEquals(30, args.getSyncNodeCount());

		// gRPC network configs checking
		Assert.assertEquals(50051, args.getRpcPort());
		Assert.assertEquals(Integer.MAX_VALUE, args.getMaxConcurrentCallsPerConnection());
		Assert.assertEquals(NettyServerBuilder.DEFAULT_FLOW_CONTROL_WINDOW, args.getFlowControlWindow());
		Assert.assertEquals(60000L, args.getMaxConnectionIdleInMillis());
		Assert.assertEquals(Long.MAX_VALUE, args.getMaxConnectionAgeInMillis());
		Assert.assertEquals(GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE, args.getMaxMessageSize());
		Assert.assertEquals(GrpcUtil.DEFAULT_MAX_HEADER_LIST_SIZE, args.getMaxHeaderListSize());
		Assert.assertEquals(1L, args.getAllowCreationOfContracts());

		Assert.assertEquals("cf790eaa22cdda7cc04250f5f54043f8734080dedc502514ae16c2f000be66de",
			args.getLocalWitnesses().getPrivateKey());
		Assert.assertEquals(Wallet.getAddressPreFixString() + "2b0c293ff59d17813b11161999b367e012173bd3",
			ByteArray.toHexString(args.getLocalWitnesses().getWitnessAccountAddress()));


	}
}
