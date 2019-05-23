/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package io.midasprotocol.common.runtime.config;

import lombok.Setter;
//import io.midasprotocol.common.utils.ForkController;
//import io.midasprotocol.core.config.Parameter.ForkBlockVersionConsts;
import io.midasprotocol.core.config.args.Args;

/**
 * For developer only
 */
public class VMConfig {

	public static final int MAX_CODE_LENGTH = 1024 * 1024;

	public static final long MAX_FEE_LIMIT = 100_000_000_000L; //1000 trx
	//Odyssey3.2 hard fork -- ForkBlockVersionConsts.ENERGY_LIMIT
//	@Setter
//	private static boolean ENERGY_LIMIT_HARD_FORK = false;
	@Setter
	private static boolean ALLOW_TVM_TRANSFER_TRC10 = false;
	@Setter
	private static boolean ALLOW_MULTI_SIGN = false;

//  @Getter
//  @Setter
//  private static boolean VERSION_3_5_HARD_FORK = false;
	private boolean vmTraceCompressed = false;
	private boolean vmTrace = Args.getInstance().isVmTrace();

	private VMConfig() {
	}

	public static VMConfig getInstance() {
		return SystemPropertiesInstance.INSTANCE;
	}

	public static void initVmHardFork() {
//		ENERGY_LIMIT_HARD_FORK = ForkController.instance().pass(ForkBlockVersionConsts.ENERGY_LIMIT);
		//VERSION_3_5_HARD_FORK = ForkController.instance().pass(ForkBlockVersionEnum.VERSION_3_5);
	}

	public static void initAllowMultiSign(long allow) {
		ALLOW_MULTI_SIGN = allow == 1;
	}

	public static void initAllowTvmTransferTrc10(long allow) {
		ALLOW_TVM_TRANSFER_TRC10 = allow == 1;
	}

//	public static boolean getEnergyLimitHardFork() {
//		return ENERGY_LIMIT_HARD_FORK;
//	}

	public static boolean allowTvmTransferTrc10() {
		return ALLOW_TVM_TRANSFER_TRC10;
	}

	public static boolean allowMultiSign() {
		return ALLOW_MULTI_SIGN;
	}

	public boolean vmTrace() {
		return vmTrace;
	}

	public boolean vmTraceCompressed() {
		return vmTraceCompressed;
	}

	private static class SystemPropertiesInstance {

		private static final VMConfig INSTANCE = new VMConfig();
	}

}