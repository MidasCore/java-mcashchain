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

package io.midasprotocol.core.capsule.utils;

import com.google.protobuf.ByteString;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.protos.Protocol.TXOutput;

public class TxOutputUtil {

	/**
	 * new transaction output.
	 *
	 * @param value   int value
	 * @param address String address
	 * @return {@link TXOutput}
	 */
	public static TXOutput newTxOutput(long value, String address) {
		return TXOutput.newBuilder()
			.setValue(value)
			.setPubKeyHash(ByteString.copyFrom(ByteArray.fromHexString(address)))
			.build();
	}
}
