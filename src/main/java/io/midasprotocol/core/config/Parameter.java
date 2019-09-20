package io.midasprotocol.core.config;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface Parameter {

	enum ChainParameters {
		MAINTENANCE_TIME_INTERVAL, //ms  ,0
		ACCOUNT_UPGRADE_COST, //drop ,1
		CREATE_ACCOUNT_FEE, //drop ,2
		TRANSACTION_FEE, //drop ,3
		ASSET_ISSUE_FEE, //drop ,4
		WITNESS_PAY_PER_BLOCK, //drop ,5
		CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT, //drop ,6
		CREATE_NEW_ACCOUNT_BANDWIDTH_RATE, // 1 ~ ,7
		ALLOW_CREATION_OF_CONTRACTS, // 0 / >0 ,8
		ENERGY_FEE, // drop, 9
		EXCHANGE_CREATE_FEE, // drop, 10
		MAX_CPU_TIME_OF_ONE_TX, // ms, 11
		ALLOW_UPDATE_ACCOUNT_NAME, // 1, 12
		ALLOW_DELEGATE_RESOURCE, // 0, 13
		TOTAL_ENERGY_LIMIT, // 50,000,000,000, 14
		ALLOW_TVM_TRANSFER_M1, // 1, 15
		TOTAL_CURRENT_ENERGY_LIMIT, // 50,000,000,000, 16
		ALLOW_MULTI_SIGN, // 1, 17
		ALLOW_ADAPTIVE_ENERGY, // 1, 18
		UPDATE_ACCOUNT_PERMISSION_FEE, // 100, 19
		MULTI_SIGN_FEE, // 1, 20
		ALLOW_PROTO_FILTER,		// 21
		ALLOW_VM_CONSTANTINOPLE, // 22
	}

	enum ForkBlockVersionEnum {
		GENESIS(1),
		VERSION_0_1_1(2),
		VERSION_0_2_0(3);

		@Getter
		private int value;

		ForkBlockVersionEnum(int value) {
			this.value = value;
		}
	}

	interface TimeConstant {
		long MS_PER_DAY = 24 * 3600 * 1000L;
		long MS_PER_YEAR = 365 * 24 * 3600 * 1000L;
	}

	interface ChainConstant {

		long TRANSFER_FEE = 0; // free
		int SOLIDIFIED_THRESHOLD = 60;
		int PRIVATE_KEY_LENGTH = 64;
		int MAX_ACTIVE_WITNESS_NUM = 64;
		int BLOCK_SIZE = 2_000_000;
		int BLOCK_PRODUCED_INTERVAL = 3000; //ms,produce block period, must be divisible by 60. millisecond
		long CLOCK_MAX_DELAY = 3600000; // 3600 * 1000 ms
		int BLOCK_PRODUCED_TIME_OUT = 50; // 50%
		long PRECISION = 100_000_000;
		long WINDOW_SIZE_MS = 24 * 3600 * 1000L;

		long MAINTENANCE_SKIP_SLOTS = 2;
		int SINGLE_REPEAT = 1;
		int BLOCK_FILLED_SLOTS_NUMBER = 128;
		int MAX_FROZEN_NUMBER = 1;
		int BLOCK_VERSION = 3;

		int DECIMALS = 8;
		long TEN_POW_DECIMALS = 100000000L;
		long BLOCKS_PER_YEAR = 10512000L;

		int MEMO_MAX_LENGTH = 64;
	}

	interface NodeConstant {

		long SYNC_RETURN_BATCH_NUM = 1000;
		long SYNC_FETCH_BATCH_NUM = 2000;
		long MAX_BLOCKS_IN_PROCESS = 400;
		long MAX_BLOCKS_ALREADY_FETCHED = 800;
		long MAX_BLOCKS_SYNC_FROM_ONE_PEER = 1000;
		long SYNC_CHAIN_LIMIT_NUM = 500;
		int MAX_TRANSACTION_PENDING = 2000;

		float PENALTY_RATE = 0.5f;
		long MINOR_PENALTY_EPOCH = 6;
		long MAJOR_PENALTY_EPOCH = 24;

		List<NodeTier> NODE_TIERS = new ArrayList<>(Arrays.asList(
			new NodeTier("Master Node", 5000000L * ChainConstant.TEN_POW_DECIMALS, 20000, 20),
			new NodeTier("Jedi Node", 500000L * ChainConstant.TEN_POW_DECIMALS, 1800, 15),
			new NodeTier("Guardian Node", 50000L * ChainConstant.TEN_POW_DECIMALS, 150, 10),
			new NodeTier("Warrior Node", 10000L * ChainConstant.TEN_POW_DECIMALS, 25, 5),
			new NodeTier("Apprentice Node", 5000L * ChainConstant.TEN_POW_DECIMALS, 10, 0)
		));
		long SUPER_NODE_STAKE_AMOUNT = 5000000L * ChainConstant.TEN_POW_DECIMALS;
	}

	interface NetConstants {

		long GRPC_IDLE_TIME_OUT = 60000L;
		long ADV_TIME_OUT = 20000L;
		long SYNC_TIME_OUT = 20000L;
		long HEAD_NUM_MAX_DELTA = 1000L;
		long HEAD_NUM_CHECK_TIME = 60000L;
		int MAX_INVENTORY_SIZE_IN_MINUTES = 2;
		long NET_MAX_TRX_PER_SECOND = 700L;
		int MAX_BLOCK_FETCH_PER_PEER = 100;
		int MAX_TRX_FETCH_PER_PEER = 1000;
		int NET_MAX_INV_SIZE_IN_MINUTES = 2;
		int MSG_CACHE_DURATION_IN_BLOCKS = 5;
	}

	interface DatabaseConstants {

		int TRANSACTIONS_COUNT_LIMIT_MAX = 1000;
		int ASSET_ISSUE_COUNT_LIMIT_MAX = 1000;
		int PROPOSAL_COUNT_LIMIT_MAX = 1000;
		int EXCHANGE_COUNT_LIMIT_MAX = 1000;
	}

	interface AdaptiveResourceLimitConstants {

		int CONTRACT_RATE_NUMERATOR = 99;
		int CONTRACT_RATE_DENOMINATOR = 100;
		int EXPAND_RATE_NUMERATOR = 1000;
		int EXPAND_RATE_DENOMINATOR = 999;
		int PERIODS_MS = 60_000;
		int LIMIT_MULTIPLIER = 1000; //s
	}

}
