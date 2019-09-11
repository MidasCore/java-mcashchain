package io.midasprotocol.common.logsfilter;

import org.junit.Assert;
import org.junit.Test;
import io.midasprotocol.common.logsfilter.capsule.ContractEventTriggerCapsule;
import io.midasprotocol.common.runtime.vm.LogEventWrapper;
import io.midasprotocol.protos.Protocol.SmartContract.ABI.Entry;

import java.util.*;

import static io.midasprotocol.common.logsfilter.FilterQuery.*;

public class FilterQueryTest {

	@Test
	public synchronized void testParseFilterQueryBlockNumber() {
		{
			String blockNum = "";
			Assert.assertEquals(FilterQuery.LATEST_BLOCK_NUM, parseToBlockNumber(blockNum));
		}

		{
			String blockNum = "earliest";
			Assert.assertEquals(FilterQuery.EARLIEST_BLOCK_NUM, parseFromBlockNumber(blockNum));
		}

		{
			String blockNum = "13245";
			Assert.assertEquals(13245, parseToBlockNumber(blockNum));
		}
	}

	@Test
	public synchronized void testMatchFilter() {
		String[] addrList = {"address1", "address2"};
		String[] topList = {"top1", "top2"};
		Map<String, String> topMap = new HashMap<>();
		List<byte[]> addressList = new ArrayList<>();
		addressList.add(addrList[0].getBytes());
		addressList.add(addrList[1].getBytes());
		topMap.put("1", topList[0]);
		topMap.put("2", topList[1]);
		LogEventWrapper event = new LogEventWrapper();
		event.setTopicList(addressList);
		event.setData(new byte[]{});
		event.setEventSignature("");
		event.setAbiEntry(Entry.newBuilder().setName("testABI").build());
		event.setBlockNumber(123L);
		ContractEventTriggerCapsule capsule = new ContractEventTriggerCapsule(event);
		capsule.getContractEventTrigger().setContractAddress("address1");
		capsule.getContractEventTrigger().setTopicMap(topMap);

		{
			Assert.assertTrue(matchFilter(capsule.getContractEventTrigger()));
		}

		{
			FilterQuery filterQuery = new FilterQuery();
			filterQuery.setFromBlock(1);
			filterQuery.setToBlock(100);
			EventPluginLoader.getInstance().setFilterQuery(filterQuery);
			Assert.assertFalse(matchFilter(capsule.getContractEventTrigger()));
		}

		{
			FilterQuery filterQuery = new FilterQuery();
			filterQuery.setFromBlock(133);
			filterQuery.setToBlock(190);
			EventPluginLoader.getInstance().setFilterQuery(filterQuery);
			Assert.assertFalse(matchFilter(capsule.getContractEventTrigger()));
		}

		{
			FilterQuery filterQuery = new FilterQuery();
			filterQuery.setFromBlock(100);
			filterQuery.setToBlock(190);
			filterQuery.setContractAddressList(Arrays.asList(addrList));
			filterQuery.setContractTopicList(Arrays.asList(topList));
			EventPluginLoader.getInstance().setFilterQuery(filterQuery);
			Assert.assertTrue(matchFilter(capsule.getContractEventTrigger()));
		}
	}
}
